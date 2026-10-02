import { createCanvas } from '@napi-rs/canvas'

const CELL = 72
const MARGIN = 38
const BOARD = CELL * 8
const SIZE = BOARD + MARGIN * 2
const FILES = 'abcdefgh'

const PIECES = {
  wk:'♔', wq:'♕', wr:'♖', wb:'♗', wn:'♘', wp:'♙',
  bk:'♚', bq:'♛', br:'♜', bb:'♝', bn:'♞', bp:'♟',
}

const COLORS = {
  bg:'#0a0d14',
  border:'#222936',
  light:'#c6ccd8',
  dark:'#556070',
  label:'#9099a8',
  white:'#ffffff',
  black:'#111827',
  blackStroke:'#f4c95d',
  last:'rgba(255, 211, 77, 0.42)',
  check:'rgba(224, 63, 63, 0.52)',
  preview:'rgba(62, 191, 255, 0.9)',
  previewCapture:'rgba(255, 101, 101, 0.92)',
}

function squareXY(square) {
  const file = FILES.indexOf(square[0])
  const rank = 8 - Number(square[1])
  return {
    x:MARGIN + file * CELL,
    y:MARGIN + rank * CELL,
  }
}

function drawSquareHighlight(ctx, square, color) {
  const { x, y } = squareXY(square)
  ctx.fillStyle = color
  ctx.fillRect(x, y, CELL, CELL)
}

function drawPiece(ctx, square, piece) {
  const { x, y } = squareXY(square)
  const glyph = PIECES[piece.color + piece.type]
  const cx = x + CELL / 2
  const cy = y + CELL / 2 + 3

  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.font = '58px "DejaVu Sans", "Noto Sans Symbols 2", sans-serif'

  if (piece.color === 'w') {
    ctx.lineWidth = 2
    ctx.strokeStyle = '#111827'
    ctx.fillStyle = COLORS.white
    ctx.strokeText(glyph, cx, cy)
    ctx.fillText(glyph, cx, cy)
  } else {
    ctx.lineWidth = 2
    ctx.strokeStyle = COLORS.blackStroke
    ctx.fillStyle = COLORS.black
    ctx.strokeText(glyph, cx, cy)
    ctx.fillText(glyph, cx, cy)
  }
}

export function renderChessBoard(game, {
  previewTargets = [],
  previewOrigin = '',
} = {}) {
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = COLORS.bg
  ctx.fillRect(0, 0, SIZE, SIZE)

  ctx.fillStyle = COLORS.border
  ctx.fillRect(MARGIN - 4, MARGIN - 4, BOARD + 8, BOARD + 8)

  for (let rank = 0; rank < 8; rank += 1) {
    for (let file = 0; file < 8; file += 1) {
      const x = MARGIN + file * CELL
      const y = MARGIN + rank * CELL
      ctx.fillStyle = (rank + file) % 2 === 0 ? COLORS.light : COLORS.dark
      ctx.fillRect(x, y, CELL, CELL)
    }
  }

  if (game.lastMove?.from) drawSquareHighlight(ctx, game.lastMove.from, COLORS.last)
  if (game.lastMove?.to) drawSquareHighlight(ctx, game.lastMove.to, COLORS.last)

  const checked = game.checkedKingSquare?.()
  if (checked) drawSquareHighlight(ctx, checked, COLORS.check)

  const board = game.boardMap()
  for (const [square, piece] of Object.entries(board)) drawPiece(ctx, square, piece)

  if (previewOrigin) {
    const { x, y } = squareXY(previewOrigin)
    ctx.strokeStyle = COLORS.preview
    ctx.lineWidth = 4
    ctx.strokeRect(x + 3, y + 3, CELL - 6, CELL - 6)
  }

  for (const square of previewTargets || []) {
    const { x, y } = squareXY(square)
    const occupied = Boolean(board[square])
    const cx = x + CELL / 2
    const cy = y + CELL / 2

    if (occupied) {
      ctx.strokeStyle = COLORS.previewCapture
      ctx.lineWidth = 5
      ctx.beginPath()
      ctx.arc(cx, cy, CELL * 0.4, 0, Math.PI * 2)
      ctx.stroke()
    } else {
      ctx.fillStyle = COLORS.preview
      ctx.beginPath()
      ctx.arc(cx, cy, CELL * 0.11, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  ctx.fillStyle = COLORS.label
  ctx.font = 'bold 17px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  for (let file = 0; file < 8; file += 1) {
    const x = MARGIN + file * CELL + CELL / 2
    ctx.fillText(FILES[file], x, MARGIN / 2)
    ctx.fillText(FILES[file], x, MARGIN + BOARD + MARGIN / 2)
  }

  for (let rank = 0; rank < 8; rank += 1) {
    const label = String(8 - rank)
    const y = MARGIN + rank * CELL + CELL / 2
    ctx.fillText(label, MARGIN / 2, y)
    ctx.fillText(label, MARGIN + BOARD + MARGIN / 2, y)
  }

  return canvas.toBuffer('image/png')
}

export const CHESS_BOARD_SIZE = SIZE
