import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import { existsSync } from 'node:fs'
import { normalizeTicTacToeTheme } from './game-themes.js'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'DejaVu Sans Bold'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'DejaVu Sans'],
]) {
  try {
    if (existsSync(file)) GlobalFonts.registerFromPath(file, family)
  } catch {}
}

const SIZE = 600
const PAD = 54
const BOARD = SIZE - PAD * 2
const CELL = BOARD / 3

function cellRect(index) {
  const row = Math.floor(index / 3)
  const col = index % 3
  return {
    x:PAD + col * CELL,
    y:PAD + row * CELL,
    w:CELL,
    h:CELL,
  }
}

function drawX(ctx, rect, color) {
  const inset = rect.w * 0.26
  ctx.strokeStyle = color
  ctx.lineWidth = 18
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(rect.x + inset, rect.y + inset)
  ctx.lineTo(rect.x + rect.w - inset, rect.y + rect.h - inset)
  ctx.moveTo(rect.x + rect.w - inset, rect.y + inset)
  ctx.lineTo(rect.x + inset, rect.y + rect.h - inset)
  ctx.stroke()
}

function drawO(ctx, rect, color) {
  ctx.strokeStyle = color
  ctx.lineWidth = 18
  ctx.beginPath()
  ctx.arc(
    rect.x + rect.w / 2,
    rect.y + rect.h / 2,
    rect.w * 0.25,
    0,
    Math.PI * 2,
  )
  ctx.stroke()
}

function drawWinningLine(ctx, line, color) {
  if (!Array.isArray(line) || line.length !== 3) return
  const first = cellRect(line[0])
  const last = cellRect(line[2])
  ctx.strokeStyle = color
  ctx.lineWidth = 12
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(first.x + first.w / 2, first.y + first.h / 2)
  ctx.lineTo(last.x + last.w / 2, last.y + last.h / 2)
  ctx.stroke()
}

export function renderTicTacToeBoard(game, themeInput = {}) {
  const theme = normalizeTicTacToeTheme(themeInput)
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')

  ctx.fillStyle = theme.background
  ctx.fillRect(0, 0, SIZE, SIZE)

  ctx.fillStyle = theme.board
  ctx.beginPath()
  ctx.roundRect(PAD, PAD, BOARD, BOARD, 28)
  ctx.fill()

  if (Number.isInteger(game.lastMove) && game.lastMove >= 0 && game.lastMove < 9) {
    const rect = cellRect(game.lastMove)
    ctx.fillStyle = theme.last
    ctx.fillRect(rect.x + 4, rect.y + 4, rect.w - 8, rect.h - 8)
  }

  ctx.strokeStyle = theme.grid
  ctx.lineWidth = 8
  ctx.lineCap = 'round'
  for (let i = 1; i <= 2; i += 1) {
    const offset = PAD + CELL * i
    ctx.beginPath()
    ctx.moveTo(offset, PAD + 16)
    ctx.lineTo(offset, PAD + BOARD - 16)
    ctx.stroke()

    ctx.beginPath()
    ctx.moveTo(PAD + 16, offset)
    ctx.lineTo(PAD + BOARD - 16, offset)
    ctx.stroke()
  }

  ctx.font = 'bold 24px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  for (let index = 0; index < 9; index += 1) {
    const rect = cellRect(index)
    const mark = game.board[index]
    if (mark === 'X') drawX(ctx, rect, theme.x)
    else if (mark === 'O') drawO(ctx, rect, theme.o)
    else {
      ctx.fillStyle = theme.hint
      ctx.globalAlpha = 0.72
      ctx.fillText(String(index + 1), rect.x + rect.w / 2, rect.y + rect.h / 2)
      ctx.globalAlpha = 1
    }
  }

  if (game.winningLine?.length === 3) {
    drawWinningLine(ctx, game.winningLine, theme.win)
  }

  return canvas.toBuffer('image/png')
}

export const TICTACTOE_BOARD_SIZE = SIZE
