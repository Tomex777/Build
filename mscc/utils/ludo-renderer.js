import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import { existsSync } from 'node:fs'
import {
  LUDO_COLORS,
  LUDO_FINISH_PROGRESS,
  LUDO_HOME_LANES,
  LUDO_LOOP,
  LUDO_SAFE_GLOBALS,
  LUDO_START_OFFSETS,
  ludoCoordinate,
  ludoGlobalIndex,
} from './ludo-game.js'
import { normalizeLudoTheme } from './game-themes.js'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'DejaVu Sans Bold'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'DejaVu Sans'],
]) {
  try {
    if (existsSync(file)) GlobalFonts.registerFromPath(file, family)
  } catch {}
}

const CELL = 44
const MARGIN = 30
const BOARD = CELL * 15
const SIZE = BOARD + MARGIN * 2

const COLOR_LABELS = Object.freeze({
  red:'R',
  green:'G',
  yellow:'Y',
  blue:'B',
})

const YARD_POINTS = Object.freeze({
  red:Object.freeze([[1.7,1.7],[4.3,1.7],[1.7,4.3],[4.3,4.3]]),
  green:Object.freeze([[10.7,1.7],[13.3,1.7],[10.7,4.3],[13.3,4.3]]),
  yellow:Object.freeze([[10.7,10.7],[13.3,10.7],[10.7,13.3],[13.3,13.3]]),
  blue:Object.freeze([[1.7,10.7],[4.3,10.7],[1.7,13.3],[4.3,13.3]]),
})

const FINISH_POINTS = Object.freeze({
  red:[6.55,7.5],
  green:[7.5,6.55],
  yellow:[8.45,7.5],
  blue:[7.5,8.45],
})

function alpha(hex, amount) {
  const value = String(hex || '#000000').replace('#','')
  const n = Number.parseInt(value, 16)
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return `rgba(${r},${g},${b},${amount})`
}

function themeColor(theme, color) {
  return theme[color] || '#888888'
}

function darkDim(hex, factor = 0.34) {
  const value = String(hex || '#000000').replace('#','')
  const n = Number.parseInt(value, 16)
  if (!Number.isFinite(n)) return '#222222'
  const r = Math.max(0, Math.min(255, Math.round(((n >> 16) & 255) * factor)))
  const g = Math.max(0, Math.min(255, Math.round(((n >> 8) & 255) * factor)))
  const b = Math.max(0, Math.min(255, Math.round((n & 255) * factor)))
  return '#' + [r,g,b].map(channel => channel.toString(16).padStart(2,'0')).join('')
}

function colorState(game, color) {
  const player = game.playerByColor?.(color) || game.players?.find?.(item => item.color === color) || null
  if (!player) return { active:false, left:false, player:null }
  return { active:!player.eliminated, left:Boolean(player.eliminated), player }
}

function displayedColor(game, theme, color) {
  const base = themeColor(theme, color)
  return colorState(game, color).active ? base : darkDim(base)
}

function cellXY(row, col) {
  return {
    x:MARGIN + col * CELL,
    y:MARGIN + row * CELL,
  }
}

function centerOf(row, col) {
  const { x, y } = cellXY(row, col)
  return { x:x + CELL / 2, y:y + CELL / 2 }
}

function drawCell(ctx, row, col, fill, stroke) {
  const { x, y } = cellXY(row, col)
  ctx.fillStyle = fill
  ctx.fillRect(x, y, CELL, CELL)
  ctx.strokeStyle = stroke
  ctx.lineWidth = 1
  ctx.strokeRect(x, y, CELL, CELL)
}

function drawHomeBlock(ctx, row, col, color, theme, game) {
  const { x, y } = cellXY(row, col)
  const w = CELL * 6
  const state = colorState(game, color)
  const fill = displayedColor(game, theme, color)

  ctx.fillStyle = alpha(fill, state.active ? 0.28 : 0.20)
  ctx.fillRect(x, y, w, w)
  ctx.strokeStyle = alpha(fill, state.active ? 0.78 : 0.48)
  ctx.lineWidth = 3
  ctx.strokeRect(x + 1.5, y + 1.5, w - 3, w - 3)

  ctx.fillStyle = state.active ? theme.yard : darkDim(theme.yard, 0.58)
  ctx.beginPath()
  ctx.roundRect(x + CELL * 0.85, y + CELL * 0.85, CELL * 4.3, CELL * 4.3, 24)
  ctx.fill()
  ctx.strokeStyle = alpha(fill, state.active ? 0.5 : 0.35)
  ctx.lineWidth = 2
  ctx.stroke()
}

function drawCenter(ctx, theme, game) {
  const x = MARGIN + CELL * 6
  const y = MARGIN + CELL * 6
  const size = CELL * 3
  const cx = x + size / 2
  const cy = y + size / 2

  const triangles = [
    { color:'red', points:[[x,y],[x, y + size],[cx,cy]] },
    { color:'green', points:[[x,y],[x + size,y],[cx,cy]] },
    { color:'yellow', points:[[x + size,y],[x + size,y + size],[cx,cy]] },
    { color:'blue', points:[[x,y + size],[x + size,y + size],[cx,cy]] },
  ]

  for (const item of triangles) {
    const state = colorState(game, item.color)
    ctx.fillStyle = alpha(displayedColor(game, theme, item.color), state.active ? 0.78 : 0.42)
    ctx.beginPath()
    ctx.moveTo(...item.points[0])
    ctx.lineTo(...item.points[1])
    ctx.lineTo(...item.points[2])
    ctx.closePath()
    ctx.fill()
  }
  ctx.strokeStyle = theme.grid
  ctx.lineWidth = 2
  ctx.strokeRect(x, y, size, size)
}

function drawSafeMarker(ctx, row, col, theme) {
  const { x, y } = centerOf(row, col)
  ctx.strokeStyle = alpha(theme.safe, 0.85)
  ctx.lineWidth = 3
  ctx.beginPath()
  ctx.arc(x, y, CELL * 0.22, 0, Math.PI * 2)
  ctx.stroke()
  ctx.fillStyle = alpha(theme.safe, 0.18)
  ctx.fill()
}

function drawBoard(ctx, theme, game) {
  ctx.fillStyle = theme.background
  ctx.fillRect(0, 0, SIZE, SIZE)

  ctx.fillStyle = theme.board
  ctx.fillRect(MARGIN, MARGIN, BOARD, BOARD)

  drawHomeBlock(ctx, 0, 0, 'red', theme, game)
  drawHomeBlock(ctx, 0, 9, 'green', theme, game)
  drawHomeBlock(ctx, 9, 9, 'yellow', theme, game)
  drawHomeBlock(ctx, 9, 0, 'blue', theme, game)

  for (const [row,col] of LUDO_LOOP) drawCell(ctx, row, col, theme.track, theme.grid)

  for (const color of LUDO_COLORS) {
    const state = colorState(game, color)
    const visual = displayedColor(game, theme, color)
    const start = LUDO_LOOP[LUDO_START_OFFSETS[color]]
    drawCell(ctx, start[0], start[1], alpha(visual, state.active ? 0.62 : 0.38), theme.grid)
    for (const [row,col] of LUDO_HOME_LANES[color]) {
      drawCell(ctx, row, col, alpha(visual, state.active ? 0.52 : 0.32), theme.grid)
    }
  }

  for (const global of LUDO_SAFE_GLOBALS) {
    const [row,col] = LUDO_LOOP[global]
    drawSafeMarker(ctx, row, col, theme)
  }

  drawCenter(ctx, theme, game)
}

function tokenRadius(count) {
  if (count >= 4) return CELL * 0.20
  if (count === 3) return CELL * 0.22
  if (count === 2) return CELL * 0.25
  return CELL * 0.31
}

function tokenOffsets(count) {
  if (count <= 1) return [[0,0]]
  if (count === 2) return [[-0.19,0],[0.19,0]]
  if (count === 3) return [[0,-0.20],[-0.20,0.17],[0.20,0.17]]
  return [[-0.18,-0.18],[0.18,-0.18],[-0.18,0.18],[0.18,0.18]]
}

function drawToken(ctx, cx, cy, radius, color, theme, label, selected = false, faint = false) {
  const fill = faint ? darkDim(themeColor(theme, color), 0.42) : themeColor(theme, color)

  ctx.save()
  ctx.globalAlpha = faint ? 0.46 : 1
  ctx.shadowColor = 'rgba(0,0,0,0.42)'
  ctx.shadowBlur = faint ? 2 : 7
  ctx.shadowOffsetY = faint ? 1 : 3
  ctx.fillStyle = fill
  ctx.beginPath()
  ctx.arc(cx, cy, radius, 0, Math.PI * 2)
  ctx.fill()
  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0

  ctx.strokeStyle = selected ? theme.hint : alpha(theme.tokenRing, 0.88)
  ctx.lineWidth = selected ? 4 : 2
  ctx.beginPath()
  ctx.arc(cx, cy, radius, 0, Math.PI * 2)
  ctx.stroke()

  ctx.fillStyle = color === 'yellow' && !faint ? '#171717' : '#ffffff'
  ctx.font = `bold ${Math.max(11, Math.floor(radius * 0.9))}px "DejaVu Sans Bold", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(String(label), cx, cy + 1)
  ctx.restore()
}

function placementKey(row, col) {
  return `${row},${col}`
}

function collectPlacedTokens(game) {
  const cells = new Map()
  const yards = []
  const finished = []

  for (const player of game.players) {
    const values = game.tokens[player.color] || []
    for (let tokenIndex = 0; tokenIndex < values.length; tokenIndex += 1) {
      const progress = values[tokenIndex]
      const item = { player, color:player.color, tokenIndex, progress }

      if (progress < 0) {
        yards.push(item)
        continue
      }
      if (progress === LUDO_FINISH_PROGRESS) {
        finished.push(item)
        continue
      }

      const coord = ludoCoordinate(player.color, progress)
      if (!coord) continue
      const key = placementKey(coord[0], coord[1])
      if (!cells.has(key)) cells.set(key, [])
      cells.get(key).push(item)
    }
  }

  return { cells, yards, finished }
}

function drawLastMove(ctx, game, theme) {
  const move = game.lastMove
  if (!move || move.to < 0 || move.to === LUDO_FINISH_PROGRESS) return
  const coord = ludoCoordinate(move.color, move.to)
  if (!coord) return
  const { x, y } = cellXY(coord[0], coord[1])
  ctx.fillStyle = alpha(theme.last, 0.24)
  ctx.fillRect(x + 2, y + 2, CELL - 4, CELL - 4)
}

function drawYardTokens(ctx, yards, theme, selectable) {
  for (const item of yards) {
    const point = YARD_POINTS[item.color][item.tokenIndex]
    const cx = MARGIN + point[0] * CELL
    const cy = MARGIN + point[1] * CELL
    drawToken(
      ctx,
      cx,
      cy,
      CELL * 0.31,
      item.color,
      theme,
      item.tokenIndex + 1,
      selectable.has(item.tokenIndex) && item.player.id === selectable.playerId,
      Boolean(item.player.eliminated),
    )
  }
}

function drawTrackTokens(ctx, cells, theme, selectable) {
  for (const [key, items] of cells) {
    const [row,col] = key.split(',').map(Number)
    const { x:cx, y:cy } = centerOf(row, col)
    const offsets = tokenOffsets(items.length)
    const radius = tokenRadius(items.length)
    items.forEach((item,index) => {
      const [ox,oy] = offsets[index] || [0,0]
      drawToken(
        ctx,
        cx + ox * CELL,
        cy + oy * CELL,
        radius,
        item.color,
        theme,
        item.tokenIndex + 1,
        selectable.has(item.tokenIndex) && item.player.id === selectable.playerId,
      )
    })
  }
}

function drawFinishedTokens(ctx, finished, theme) {
  const groups = new Map()
  for (const item of finished) {
    if (!groups.has(item.color)) groups.set(item.color, [])
    groups.get(item.color).push(item)
  }

  for (const [color,items] of groups) {
    const base = FINISH_POINTS[color]
    const offsets = tokenOffsets(items.length)
    items.forEach((item,index) => {
      const [ox,oy] = offsets[index] || [0,0]
      drawToken(
        ctx,
        MARGIN + (base[0] + ox * 0.85) * CELL,
        MARGIN + (base[1] + oy * 0.85) * CELL,
        CELL * 0.17,
        color,
        theme,
        item.tokenIndex + 1,
        false,
      )
    })
  }
}

function drawDice(ctx, roll, theme) {
  if (!Number.isInteger(roll) || roll < 1 || roll > 6) return
  const size = CELL * 1.28
  const cx = MARGIN + BOARD / 2
  const cy = MARGIN + BOARD / 2
  const x = cx - size / 2
  const y = cy - size / 2

  ctx.fillStyle = theme.dice
  ctx.beginPath()
  ctx.roundRect(x, y, size, size, 10)
  ctx.fill()
  ctx.strokeStyle = alpha(theme.grid, 0.9)
  ctx.lineWidth = 2
  ctx.stroke()

  const positions = {
    tl:[-0.24,-0.24], tc:[0,-0.24], tr:[0.24,-0.24],
    ml:[-0.24,0], mc:[0,0], mr:[0.24,0],
    bl:[-0.24,0.24], bc:[0,0.24], br:[0.24,0.24],
  }
  const pips = {
    1:['mc'],
    2:['tl','br'],
    3:['tl','mc','br'],
    4:['tl','tr','bl','br'],
    5:['tl','tr','mc','bl','br'],
    6:['tl','ml','bl','tr','mr','br'],
  }

  ctx.fillStyle = theme.dicePip
  for (const key of pips[roll]) {
    const [ox,oy] = positions[key]
    ctx.beginPath()
    ctx.arc(cx + ox * size, cy + oy * size, size * 0.065, 0, Math.PI * 2)
    ctx.fill()
  }
}

function drawLabels(ctx, game, theme) {
  ctx.font = 'bold 18px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  const labels = {
    red:[3,0.45],
    green:[12,0.45],
    yellow:[12,14.55],
    blue:[3,14.55],
  }

  for (const color of LUDO_COLORS) {
    const point = labels[color]
    const state = colorState(game, color)
    const player = state.player
    if (!player) {
      ctx.fillStyle = alpha(displayedColor(game, theme, color), 0.72)
      ctx.fillText(`${COLOR_LABELS[color]} · Inactive`, MARGIN + point[0] * CELL, MARGIN + point[1] * CELL)
      continue
    }

    ctx.fillStyle = state.left ? alpha(theme.text, 0.48) : theme.text
    const name = player.name.length > 11 ? player.name.slice(0,10) + '…' : player.name
    const suffix = state.left ? ' · Left' : ''
    ctx.fillText(`${COLOR_LABELS[color]} · ${name}${suffix}`, MARGIN + point[0] * CELL, MARGIN + point[1] * CELL)
  }
}

export function renderLudoBoard(game, {
  theme:themeInput = {},
  selectablePlayerId = '',
  selectableTokens = [],
  roll = game.pendingRoll,
} = {}) {
  const theme = normalizeLudoTheme(themeInput)
  const canvas = createCanvas(SIZE, SIZE)
  const ctx = canvas.getContext('2d')

  drawBoard(ctx, theme, game)
  drawLastMove(ctx, game, theme)

  const selectable = new Set((Array.isArray(selectableTokens) ? selectableTokens : []).map(Number))
  selectable.playerId = String(selectablePlayerId || '')

  const placed = collectPlacedTokens(game)
  drawYardTokens(ctx, placed.yards, theme, selectable)
  drawTrackTokens(ctx, placed.cells, theme, selectable)
  drawFinishedTokens(ctx, placed.finished, theme)
  drawDice(ctx, roll, theme)
  drawLabels(ctx, game, theme)

  return canvas.toBuffer('image/png')
}

export const LUDO_BOARD_SIZE = SIZE
export const LUDO_CELL_SIZE = CELL
