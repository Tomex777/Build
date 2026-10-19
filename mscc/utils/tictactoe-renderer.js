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


function strokeLine(ctx, x1, y1, x2, y2, width = 18) {
  ctx.lineWidth = width
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(x1, y1)
  ctx.lineTo(x2, y2)
  ctx.stroke()
}

function drawBrushX(ctx, cx, cy, size) {
  const d = size * 0.34
  strokeLine(ctx, cx - d, cy - d, cx + d, cy + d, Math.max(12, size * 0.11))
  strokeLine(ctx, cx + d, cy - d, cx - d, cy + d, Math.max(12, size * 0.11))
}

function drawBrushO(ctx, cx, cy, size, slashed = false) {
  ctx.lineWidth = Math.max(12, size * 0.11)
  ctx.beginPath()
  ctx.ellipse(cx, cy, size * 0.29, size * 0.23, -0.32, 0, Math.PI * 2)
  ctx.stroke()
  if (slashed) {
    strokeLine(
      ctx,
      cx - size * 0.36,
      cy - size * 0.35,
      cx + size * 0.36,
      cy + size * 0.35,
      Math.max(10, size * 0.085),
    )
  }
}

export function renderTicTacToeMenuArt() {
  const canvas = createCanvas(768, 768)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#000000'
  ctx.fillRect(0, 0, 768, 768)
  ctx.strokeStyle = '#ff1010'

  // Hand-drawn 3x3 grid, intentionally imperfect to match the chosen art.
  strokeLine(ctx, 258, 96, 258, 682, 20)
  strokeLine(ctx, 505, 101, 505, 682, 20)
  strokeLine(ctx, 86, 280, 682, 280, 20)
  strokeLine(ctx, 88, 496, 684, 496, 20)

  const s = 172
  drawBrushO(ctx, 151, 180, s, true)
  drawBrushX(ctx, 381, 183, s)
  drawBrushX(ctx, 615, 180, s)

  drawBrushO(ctx, 151, 387, s, false)
  drawBrushO(ctx, 383, 390, s, true)
  drawBrushX(ctx, 615, 387, s)

  drawBrushX(ctx, 151, 603, s)
  drawBrushO(ctx, 383, 603, s, false)
  drawBrushO(ctx, 615, 603, s, true)

  return canvas.toBuffer('image/png')
}

// Nexia picture-game visual parity: 592×592 JPEG, A–C / 1–3 coordinates,
// cached empty grid, gold X, blue O, last-move highlight and green win line.
// This is separate from NIGHT's customizable numbered board renderer.
const NEXIA_PX=592, NEXIA_MARGIN=56, NEXIA_CELL=160
let nexiaTemplate
function nexiaCenter(index){
  return { x:NEXIA_MARGIN+(index%3)*NEXIA_CELL+NEXIA_CELL/2,
    y:NEXIA_MARGIN+Math.floor(index/3)*NEXIA_CELL+NEXIA_CELL/2 }
}
function buildNexiaTemplate(){
  const canvas=createCanvas(NEXIA_PX,NEXIA_PX),ctx=canvas.getContext('2d')
  ctx.fillStyle='#0e0e0e';ctx.fillRect(0,0,NEXIA_PX,NEXIA_PX)
  ctx.fillStyle='#888888';ctx.font='bold 22px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign='center';ctx.textBaseline='middle'
  for(let i=0;i<3;i++){
    ctx.fillText('ABC'[i],NEXIA_MARGIN+(i+.5)*NEXIA_CELL,NEXIA_MARGIN/2)
    ctx.fillText(String(i+1),NEXIA_MARGIN/2,NEXIA_MARGIN+(i+.5)*NEXIA_CELL)
  }
  ctx.strokeStyle='#333333';ctx.lineWidth=3
  for(let i=0;i<=3;i++){
    const at=NEXIA_MARGIN+i*NEXIA_CELL
    ctx.beginPath();ctx.moveTo(at,NEXIA_MARGIN);ctx.lineTo(at,NEXIA_MARGIN+480);ctx.stroke()
    ctx.beginPath();ctx.moveTo(NEXIA_MARGIN,at);ctx.lineTo(NEXIA_MARGIN+480,at);ctx.stroke()
  }
  return canvas
}
export function renderNexiaTicTacToePicture(game) {
  const canvas=createCanvas(NEXIA_PX,NEXIA_PX),ctx=canvas.getContext('2d')
  nexiaTemplate ||= buildNexiaTemplate()
  ctx.drawImage(nexiaTemplate,0,0)
  if(Number.isInteger(game.lastMove)&&game.lastMove>=0&&game.lastMove<=8) {
    const index=game.lastMove
    ctx.fillStyle='rgba(255,255,255,0.06)'
    ctx.fillRect(NEXIA_MARGIN+(index%3)*NEXIA_CELL,
      NEXIA_MARGIN+Math.floor(index/3)*NEXIA_CELL,NEXIA_CELL,NEXIA_CELL)
  }
  for(let i=0;i<9;i++){
    const mark=game.board[i]
    if(!mark)continue
    const {x,y}=nexiaCenter(i)
    ctx.strokeStyle=mark==='X'?'#c9a84c':'#4ca9c9'
    ctx.lineWidth=10;ctx.lineCap='round';ctx.beginPath()
    if(mark==='X'){
      const radius=NEXIA_CELL*.28
      ctx.moveTo(x-radius,y-radius);ctx.lineTo(x+radius,y+radius)
      ctx.moveTo(x+radius,y-radius);ctx.lineTo(x-radius,y+radius)
    } else ctx.arc(x,y,NEXIA_CELL*.30,0,Math.PI*2)
    ctx.stroke()
  }
  if(game.winningLine?.length===3){
    const a=nexiaCenter(game.winningLine[0]),c=nexiaCenter(game.winningLine[2])
    ctx.strokeStyle='#4ccf6e';ctx.lineWidth=8;ctx.lineCap='round'
    ctx.globalAlpha=.85;ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(c.x,c.y);ctx.stroke()
    ctx.globalAlpha=1
  }
  return canvas.toBuffer('image/jpeg',{quality:.92})
}
