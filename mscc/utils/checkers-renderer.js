import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import { existsSync } from 'node:fs'
import { coordFromIndex } from './checkers-game.js'
import { normalizeCheckersTheme } from './game-themes.js'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'DejaVu Sans Bold'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'DejaVu Sans'],
]) {
  try {
    if (existsSync(file)) GlobalFonts.registerFromPath(file, family)
  } catch {}
}

const SIZE = 600
const MARGIN = 40
const CELL = 65
const BOARD = CELL * 8

function alpha(hex, amount) {
  const value = String(hex || '#000000').replace('#','')
  const n = Number.parseInt(value, 16)
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return `rgba(${r},${g},${b},${amount})`
}

function squareXY(index) {
  const coord = coordFromIndex(index)
  if (!coord) return null
  return {
    x:MARGIN + coord.col * CELL,
    y:MARGIN + coord.row * CELL,
    row:coord.row,
    col:coord.col,
  }
}

function drawCrown(ctx, cx, cy, radius, color) {
  ctx.strokeStyle = color
  ctx.fillStyle = alpha(color, 0.16)
  ctx.lineWidth = Math.max(3, radius * 0.09)
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(cx - radius * 0.52, cy + radius * 0.28)
  ctx.lineTo(cx - radius * 0.42, cy - radius * 0.34)
  ctx.lineTo(cx - radius * 0.08, cy - radius * 0.02)
  ctx.lineTo(cx, cy - radius * 0.46)
  ctx.lineTo(cx + radius * 0.10, cy - radius * 0.02)
  ctx.lineTo(cx + radius * 0.44, cy - radius * 0.34)
  ctx.lineTo(cx + radius * 0.52, cy + radius * 0.28)
  ctx.closePath()
  ctx.fill()
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(cx - radius * 0.52, cy + radius * 0.36)
  ctx.lineTo(cx + radius * 0.52, cy + radius * 0.36)
  ctx.stroke()
}

function drawPiece(ctx, index, piece, theme) {
  const pos = squareXY(index)
  if (!pos) return
  const cx = pos.x + CELL / 2
  const cy = pos.y + CELL / 2
  const radius = CELL * 0.36
  const fill = piece.color === 'b' ? theme.blackPiece : theme.redPiece

  ctx.shadowColor = 'rgba(0,0,0,0.45)'
  ctx.shadowBlur = 8
  ctx.shadowOffsetY = 4
  ctx.fillStyle = fill
  ctx.beginPath()
  ctx.arc(cx, cy, radius, 0, Math.PI * 2)
  ctx.fill()
  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0

  ctx.strokeStyle = piece.color === 'b' ? '#777777' : '#e67a68'
  ctx.lineWidth = 3
  ctx.beginPath()
  ctx.arc(cx, cy, radius * 0.86, 0, Math.PI * 2)
  ctx.stroke()

  if (piece.rank === 'k') drawCrown(ctx, cx, cy, radius * 0.78, theme.crown)
}

function drawBoardBase(ctx, theme) {
  ctx.fillStyle = theme.background
  ctx.fillRect(0, 0, SIZE, SIZE)

  for (let row = 0; row < 8; row += 1) {
    for (let col = 0; col < 8; col += 1) {
      const x = MARGIN + col * CELL
      const y = MARGIN + row * CELL
      ctx.fillStyle = (row + col) % 2 === 0 ? theme.light : theme.dark
      ctx.fillRect(x, y, CELL, CELL)
    }
  }

  ctx.strokeStyle = '#000000'
  ctx.globalAlpha = 0.3
  ctx.lineWidth = 2
  ctx.strokeRect(MARGIN, MARGIN, BOARD, BOARD)
  ctx.globalAlpha = 1
}

function drawNumbers(ctx, theme) {
  ctx.font = 'bold 12px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'top'
  for (let index = 0; index < 32; index += 1) {
    const pos = squareXY(index)
    ctx.fillStyle = alpha(theme.hint, 0.55)
    ctx.fillText(String(index + 1), pos.x + 5, pos.y + 4)
  }
}

export function renderCheckersBoard(game, {
  previewOrigin = -1,
  previewTargets = [],
  theme:themeInput = {},
} = {}) {
  const theme = normalizeCheckersTheme(themeInput)
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')

  drawBoardBase(ctx, theme)

  if (game.lastMove?.path?.length) {
    for (const index of [game.lastMove.path[0], game.lastMove.path.at(-1)]) {
      const pos = squareXY(index)
      if (!pos) continue
      ctx.fillStyle = alpha(theme.last, 0.18)
      ctx.fillRect(pos.x, pos.y, CELL, CELL)
    }
  }

  if (Number.isInteger(previewOrigin) && previewOrigin >= 0) {
    const pos = squareXY(previewOrigin)
    if (pos) {
      ctx.strokeStyle = alpha(theme.hint, 0.72)
      ctx.lineWidth = 3
      ctx.strokeRect(pos.x + 3, pos.y + 3, CELL - 6, CELL - 6)
    }
  }

  for (const target of previewTargets) {
    const pos = squareXY(target)
    if (!pos) continue
    ctx.fillStyle = alpha(theme.hint, 0.52)
    ctx.beginPath()
    ctx.arc(pos.x + CELL / 2, pos.y + CELL / 2, CELL * 0.105, 0, Math.PI * 2)
    ctx.fill()
  }

  drawNumbers(ctx, theme)

  for (let index = 0; index < 32; index += 1) {
    const piece = game.board[index]
    if (piece) drawPiece(ctx, index, piece, theme)
  }

  return canvas.toBuffer('image/png')
}

export const CHECKERS_BOARD_SIZE = SIZE
export const CHECKERS_CELL_SIZE = CELL
