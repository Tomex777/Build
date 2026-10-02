import { existsSync } from 'node:fs'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import gifenc from 'gifenc'
import { runFfmpeg } from './media-conversion.js'
import { normalizeChessTheme } from './game-themes.js'

const { GIFEncoder, quantize, applyPalette } = gifenc

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'DejaVu Sans Bold'],
  ['/usr/share/fonts/truetype/freefont/FreeSerif.ttf', 'FreeSerif'],
]) {
  try {
    if (existsSync(file)) GlobalFonts.registerFromPath(file, family)
  } catch {}
}

const CELL = 65
const MARGIN = 35
const BOARD = CELL * 8
const SIZE = BOARD + MARGIN * 2
const FILES = 'abcdefgh'

const PIECES = {
  wk:'♔', wq:'♕', wr:'♖', wb:'♗', wn:'♘', wp:'♙',
  bk:'♚', bq:'♛', br:'♜', bb:'♝', bn:'♞', bp:'♟',
}

const templateCache = new Map()

function alpha(hex, amount) {
  const value = String(hex || '#000000').replace('#','')
  const n = Number.parseInt(value, 16)
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return `rgba(${r},${g},${b},${amount})`
}

function squareXY(file, rank) {
  return {
    x:MARGIN + file * CELL,
    y:MARGIN + rank * CELL,
  }
}

function algToXY(square) {
  const file = FILES.indexOf(String(square || '')[0])
  const rank = 8 - Number.parseInt(String(square || '')[1], 10)
  return squareXY(file, rank)
}

function themeKey(theme) {
  return [
    theme.background,theme.light,theme.dark,theme.labelColor,
    theme.whitePiece,theme.blackPiece,
  ].join('|')
}

function buildTemplate(theme) {
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = theme.background
  ctx.fillRect(0, 0, SIZE, SIZE)

  for (let rank = 0; rank < 8; rank += 1) {
    for (let file = 0; file < 8; file += 1) {
      const { x, y } = squareXY(file, rank)
      ctx.fillStyle = (rank + file) % 2 === 1 ? theme.light : theme.dark
      ctx.fillRect(x, y, CELL, CELL)
    }
  }

  ctx.fillStyle = theme.labelColor
  ctx.font = 'bold 18px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  for (let file = 0; file < 8; file += 1) {
    const x = MARGIN + file * CELL + CELL / 2
    ctx.fillText(FILES[file], x, MARGIN / 2)
    ctx.fillText(FILES[file], x, MARGIN + BOARD + MARGIN / 2)
  }

  for (let rank = 0; rank < 8; rank += 1) {
    const value = String(8 - rank)
    const y = MARGIN + rank * CELL + CELL / 2
    ctx.fillText(value, MARGIN / 2, y)
    ctx.fillText(value, MARGIN + BOARD + MARGIN / 2, y)
  }

  return canvas
}

function getTemplate(theme) {
  const key = themeKey(theme)
  if (!templateCache.has(key)) {
    if (templateCache.size > 16) templateCache.clear()
    templateCache.set(key, buildTemplate(theme))
  }
  return templateCache.get(key)
}

function drawHighlightSquares(ctx, squares, color) {
  for (const square of squares.filter(Boolean)) {
    const { x, y } = algToXY(square)
    ctx.fillStyle = color
    ctx.fillRect(x, y, CELL, CELL)
  }
}

function drawPieceAt(ctx, cx, cy, piece, theme) {
  const glyph = PIECES[piece.color + piece.type]
  ctx.font = `${Math.floor(CELL * 0.78)}px FreeSerif, serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = piece.color === 'w' ? theme.whitePiece : theme.blackPiece

  if (piece.color === 'w') {
    ctx.lineWidth = 2
    ctx.strokeStyle = '#000000'
    ctx.strokeText(glyph, cx, cy)
  }

  ctx.fillText(glyph, cx, cy)
}

function drawPiece(ctx, square, piece, theme) {
  const { x, y } = algToXY(square)
  drawPieceAt(ctx, x + CELL / 2, y + CELL / 2 + 2, piece, theme)
}

function drawPreviewMarkers(ctx, boardMap, targets, theme) {
  for (const square of targets) {
    const { x, y } = algToXY(square)
    const cx = x + CELL / 2
    const cy = y + CELL / 2
    if (boardMap[square]) {
      ctx.strokeStyle = alpha(theme.capture, 0.58)
      ctx.lineWidth = 3
      ctx.beginPath()
      ctx.arc(cx, cy, CELL * 0.40, 0, Math.PI * 2)
      ctx.stroke()
    } else {
      ctx.fillStyle = alpha(theme.preview, 0.52)
      ctx.beginPath()
      ctx.arc(cx, cy, CELL * 0.105, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

export function renderChessBoard(game, {
  previewTargets = [],
  previewOrigin = '',
  theme:themeInput = {},
} = {}) {
  const theme = normalizeChessTheme(themeInput)
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')
  ctx.drawImage(getTemplate(theme), 0, 0)

  if (game.lastMove) {
    drawHighlightSquares(ctx, [game.lastMove.from, game.lastMove.to], alpha(theme.lastMove, 0.12))
  }

  const checked = game.checkedKingSquare?.()
  if (checked) drawHighlightSquares(ctx, [checked], alpha(theme.check, 0.24))

  const boardMap = game.boardMap()
  for (const [square, piece] of Object.entries(boardMap)) drawPiece(ctx, square, piece, theme)

  if (previewTargets.length) {
    drawPreviewMarkers(ctx, boardMap, previewTargets, theme)
    if (previewOrigin) {
      const { x, y } = algToXY(previewOrigin)
      ctx.strokeStyle = alpha(theme.preview, 0.58)
      ctx.lineWidth = 2
      ctx.strokeRect(x + 3, y + 3, CELL - 6, CELL - 6)
    }
  }

  return canvas.toBuffer('image/png')
}

function buildMoveGif(game, {
  frames = 7,
  holdMs = 700,
  frameMs = 45,
  theme:themeInput = {},
} = {}) {
  if (!game.lastMove) return null

  const theme = normalizeChessTheme(themeInput)
  const { from, to } = game.lastMove
  const boardMap = game.boardMap()
  const movedPiece = boardMap[to]
  if (!movedPiece) return null

  const checked = game.checkedKingSquare?.()
  const fromXY = algToXY(from)
  const toXY = algToXY(to)
  const frameData = []

  for (let index = 0; index <= frames; index += 1) {
    const t = index / frames
    const canvas = createCanvas(SIZE, SIZE)
    const ctx = canvas.getContext('2d')
    ctx.drawImage(getTemplate(theme), 0, 0)

    drawHighlightSquares(ctx, [from, to], alpha(theme.lastMove, 0.12))
    if (checked) drawHighlightSquares(ctx, [checked], alpha(theme.check, 0.24))

    for (const [square, piece] of Object.entries(boardMap)) {
      if (square === to) continue
      drawPiece(ctx, square, piece, theme)
    }

    drawPieceAt(
      ctx,
      fromXY.x + CELL / 2 + (toXY.x - fromXY.x) * t,
      fromXY.y + CELL / 2 + (toXY.y - fromXY.y) * t + 2,
      movedPiece,
      theme,
    )

    frameData.push(ctx.getImageData(0, 0, SIZE, SIZE).data)
  }

  const palette = quantize(frameData.at(-1), 64)
  const encoder = GIFEncoder()

  for (let index = 0; index < frameData.length; index += 1) {
    const indexed = applyPalette(frameData[index], palette)
    encoder.writeFrame(indexed, SIZE, SIZE, {
      palette,
      delay:index === frameData.length - 1 ? holdMs : frameMs,
    })
  }

  encoder.finish()
  return Buffer.from(encoder.bytes())
}

async function withTempDir(work) {
  const directory = await mkdtemp(join(tmpdir(), 'mscc-chess-'))
  try {
    return await work(directory)
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
}

export async function renderChessMoveVideo(game, options = {}) {
  const gif = buildMoveGif(game, options)
  if (!gif) return null

  return withTempDir(async directory => {
    const input = join(directory, 'move.gif')
    const output = join(directory, 'move.mp4')
    await writeFile(input, gif)

    await runFfmpeg([
      '-i', input,
      '-vf',
      'fps=15,scale=512:512:flags=lanczos:force_original_aspect_ratio=decrease,' +
      'pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black',
      '-c:v', 'libx264',
      '-pix_fmt', 'yuv420p',
      '-movflags', '+faststart',
      '-fps_mode', 'vfr',
      output,
    ], { timeoutMs:45_000 })

    return readFile(output)
  })
}

export const CHESS_BOARD_SIZE = SIZE
export const CHESS_CELL_SIZE = CELL
