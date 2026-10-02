import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import { existsSync } from 'node:fs'
import {
  LUDO_COLORS,
  LUDO_FINISH_PROGRESS,
  LUDO_HOME_LANES,
  LUDO_LOOP,
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

// Compact original MSCC character cards replace the generic footballer portraits.
// The fourth card is a NIGHT brand avatar until a fourth named personality is locked.
const CHARACTER_DECOR = Object.freeze({
  red:Object.freeze({
    name:'JOSIA',
    skin:'#6f4835',
    hair:'#17131c',
    accent:'#9b7ad6',
    glasses:true,
    headphones:false,
    hairStyle:'long',
  }),
  green:Object.freeze({
    name:'NAMI',
    skin:'#694431',
    hair:'#141820',
    accent:'#3f8fe5',
    glasses:true,
    headphones:true,
    hairStyle:'messy',
  }),
  yellow:Object.freeze({
    name:'MIMI',
    skin:'#704936',
    hair:'#1b1420',
    accent:'#c94f8d',
    glasses:false,
    headphones:true,
    hairStyle:'highlight',
  }),
  blue:Object.freeze({
    name:'NIGHT',
    skin:'#654333',
    hair:'#11151d',
    accent:'#58a9d8',
    glasses:false,
    headphones:false,
    hairStyle:'short',
  }),
})

// These arrows reproduce the familiar Nigerian-market board flow:
// clockwise around the outer loop, then inward along each coloured home lane.
const TRACK_ARROWS = Object.freeze([
  // Left arm: clockwise track across the top, back across the bottom,
  // with the turnaround at the far-left middle square.
  [6,1, 1,0],[6,3, 1,0],[6,5, 1,0],
  [7,0, 0,-1],
  [8,1, -1,0],[8,3, -1,0],[8,5, -1,0],

  // Top arm: up the left side, across the top, then down the right side.
  [1,6, 0,-1],[3,6, 0,-1],[5,6, 0,-1],
  [0,7, 1,0],
  [1,8, 0,1],[3,8, 0,1],[5,8, 0,1],

  // Right arm.
  [6,9, 1,0],[6,11, 1,0],[6,13, 1,0],
  [7,14, 0,1],
  [8,9, -1,0],[8,11, -1,0],[8,13, -1,0],

  // Bottom arm.
  [9,8, 0,1],[11,8, 0,1],[13,8, 0,1],
  [14,7, -1,0],
  [9,6, 0,-1],[11,6, 0,-1],[13,6, 0,-1],
])

const HOME_ARROWS = Object.freeze({
  // Three equally spaced arrows in each coloured home lane, all pointing inward.
  red:Object.freeze([[7,1,1,0],[7,3,1,0],[7,5,1,0]]),
  green:Object.freeze([[1,7,0,1],[3,7,0,1],[5,7,0,1]]),
  yellow:Object.freeze([[7,13,-1,0],[7,11,-1,0],[7,9,-1,0]]),
  blue:Object.freeze([[13,7,0,-1],[11,7,0,-1],[9,7,0,-1]]),
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

function shadeHex(hex, amount = 0) {
  const value = String(hex || '#000000').replace('#','')
  const n = Number.parseInt(value, 16)
  if (!Number.isFinite(n)) return '#000000'
  const channels = [n >> 16, (n >> 8) & 255, n & 255]
  const next = channels.map(channel => Math.max(0, Math.min(255, Math.round(channel + (amount >= 0 ? (255 - channel) * amount : channel * amount)))))
  return '#' + next.map(channel => channel.toString(16).padStart(2,'0')).join('')
}

function drawCharacterPortrait(ctx, cx, cy, art) {
  const headR = CELL * 0.23

  // shoulders / clothing
  ctx.fillStyle = art.accent
  ctx.beginPath()
  ctx.ellipse(cx, cy + CELL * 0.23, CELL * 0.50, CELL * 0.34, 0, Math.PI, 0)
  ctx.fill()

  // hair behind face for the women
  if (art.hairStyle === 'long' || art.hairStyle === 'highlight') {
    ctx.fillStyle = art.hair
    ctx.beginPath()
    ctx.ellipse(cx, cy - CELL * 0.05, CELL * 0.32, CELL * 0.38, 0, 0, Math.PI * 2)
    ctx.fill()
  }

  // face
  ctx.fillStyle = art.skin
  ctx.beginPath()
  ctx.arc(cx, cy - CELL * 0.15, headR, 0, Math.PI * 2)
  ctx.fill()

  // canonical hair cues
  ctx.fillStyle = art.hair
  ctx.beginPath()
  if (art.hairStyle === 'messy') {
    ctx.moveTo(cx - headR, cy - CELL * 0.20)
    ctx.lineTo(cx - CELL * 0.12, cy - CELL * 0.43)
    ctx.lineTo(cx - CELL * 0.02, cy - CELL * 0.31)
    ctx.lineTo(cx + CELL * 0.09, cy - CELL * 0.45)
    ctx.lineTo(cx + headR, cy - CELL * 0.19)
    ctx.lineTo(cx + headR * 0.82, cy - CELL * 0.07)
    ctx.lineTo(cx - headR * 0.88, cy - CELL * 0.07)
    ctx.closePath()
  } else {
    ctx.arc(cx, cy - CELL * 0.23, headR * 0.98, Math.PI, Math.PI * 2)
    ctx.lineTo(cx + headR * 0.88, cy - CELL * 0.12)
    ctx.lineTo(cx - headR * 0.88, cy - CELL * 0.12)
    ctx.closePath()
  }
  ctx.fill()

  if (art.hairStyle === 'highlight') {
    ctx.strokeStyle = '#d85b9c'
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.arc(cx + CELL * 0.05, cy - CELL * 0.19, headR * 0.92, Math.PI * 1.12, Math.PI * 1.82)
    ctx.stroke()
  }

  if (art.headphones) {
    ctx.strokeStyle = art.accent
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.arc(cx, cy - CELL * 0.15, headR * 1.25, Math.PI * 1.08, Math.PI * 1.92)
    ctx.stroke()
    ctx.fillStyle = art.accent
    ctx.fillRect(cx - headR * 1.32, cy - CELL * 0.18, 5, 13)
    ctx.fillRect(cx + headR * 1.32 - 5, cy - CELL * 0.18, 5, 13)
  }

  if (art.glasses) {
    ctx.strokeStyle = '#232323'
    ctx.lineWidth = 1.7
    const gy = cy - CELL * 0.14
    ctx.strokeRect(cx - CELL * 0.18, gy - 4, CELL * 0.14, 8)
    ctx.strokeRect(cx + CELL * 0.04, gy - 4, CELL * 0.14, 8)
    ctx.beginPath()
    ctx.moveTo(cx - CELL * 0.04, gy)
    ctx.lineTo(cx + CELL * 0.04, gy)
    ctx.stroke()
  }
}

function drawCharacterCard(ctx, cx, cy, color, theme, state) {
  const art = CHARACTER_DECOR[color]
  if (!art) return

  ctx.save()
  ctx.globalAlpha = state.active ? 1 : 0.28

  const panelW = CELL * 2.18
  const panelH = CELL * 2.32
  ctx.shadowColor = state.active ? 'rgba(0,0,0,0.18)' : 'rgba(0,0,0,0.08)'
  ctx.shadowBlur = state.active ? 5 : 1
  ctx.shadowOffsetY = state.active ? 2 : 0
  ctx.fillStyle = state.active ? '#fffdf7' : darkDim('#fffdf7', 0.58)
  ctx.beginPath()
  ctx.roundRect(cx - panelW / 2, cy - panelH / 2, panelW, panelH, 7)
  ctx.fill()
  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0

  ctx.strokeStyle = alpha(displayedColor({ playerByColor:()=>state.player, players:state.player ? [state.player] : [] }, theme, color), state.active ? 0.92 : 0.38)
  ctx.lineWidth = 1
  ctx.stroke()

  drawCharacterPortrait(ctx, cx, cy - CELL * 0.08, art)

  ctx.fillStyle = state.active ? '#202020' : alpha(theme.text, 0.42)
  ctx.font = 'bold 8px "DejaVu Sans Bold", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(art.name, cx, cy + CELL * 0.82)
  ctx.restore()
}

function drawRaisedYardSpot(ctx, cx, cy, fill, active) {
  const r = CELL * 0.38
  ctx.save()
  ctx.globalAlpha = active ? 1 : 0.35
  ctx.shadowColor = 'rgba(0,0,0,0.28)'
  ctx.shadowBlur = 6
  ctx.shadowOffsetY = 3
  ctx.fillStyle = shadeHex(fill, -0.18)
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fill()

  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0
  const gradient = ctx.createRadialGradient(cx - r * 0.30, cy - r * 0.34, r * 0.05, cx, cy, r)
  gradient.addColorStop(0, '#ffffff')
  gradient.addColorStop(0.38, '#fffdf7')
  gradient.addColorStop(1, shadeHex('#fffdf7', -0.10))
  ctx.fillStyle = gradient
  ctx.beginPath()
  ctx.arc(cx, cy - 1, r * 0.82, 0, Math.PI * 2)
  ctx.fill()

  ctx.strokeStyle = shadeHex(fill, 0.08)
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.arc(cx, cy - 1, r * 0.82, Math.PI * 1.08, Math.PI * 1.84)
  ctx.stroke()
  ctx.restore()
}

function drawHomeBlock(ctx, row, col, color, theme, game) {
  const { x, y } = cellXY(row, col)
  const w = CELL * 6
  const state = colorState(game, color)
  const fill = displayedColor(game, theme, color)

  // Bold printed-board colour with a very thin frame around the character field.
  ctx.fillStyle = state.active ? fill : darkDim(fill, 0.90)
  ctx.fillRect(x, y, w, w)
  ctx.strokeStyle = state.active ? shadeHex(fill, -0.24) : alpha(fill, 0.35)
  ctx.lineWidth = 1.5
  ctx.strokeRect(x + 0.75, y + 0.75, w - 1.5, w - 1.5)

  const inset = CELL * 0.18
  ctx.fillStyle = state.active ? '#fffdf7' : darkDim('#fffdf7', 0.56)
  ctx.fillRect(x + inset, y + inset, w - inset * 2, w - inset * 2)
  ctx.strokeStyle = alpha(fill, state.active ? 0.92 : 0.34)
  ctx.lineWidth = 1
  ctx.strokeRect(x + inset, y + inset, w - inset * 2, w - inset * 2)

  drawCharacterCard(ctx, x + w / 2, y + w / 2, color, theme, state)

  for (const [px,py] of YARD_POINTS[color]) {
    drawRaisedYardSpot(
      ctx,
      MARGIN + px * CELL,
      MARGIN + py * CELL,
      fill,
      state.active,
    )
  }
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
    ctx.fillStyle = state.active ? displayedColor(game, theme, item.color) : alpha(displayedColor(game, theme, item.color), 0.46)
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

function drawArrowVector(ctx, row, col, dx, dy, theme, fillOverride = '') {
  const { x:cx, y:cy } = centerOf(row, col)
  const len = CELL * 0.25
  const half = CELL * 0.12

  ctx.save()
  ctx.translate(cx, cy)
  ctx.rotate(Math.atan2(dy, dx))
  ctx.fillStyle = fillOverride || alpha(theme.text, 0.88)
  ctx.beginPath()
  ctx.moveTo(len, 0)
  ctx.lineTo(-half, -half)
  ctx.lineTo(-half * 0.22, 0)
  ctx.lineTo(-half, half)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

function drawDirectionArrows(ctx, theme, game) {
  for (const [row,col,dx,dy] of TRACK_ARROWS) {
    drawArrowVector(ctx, row, col, dx, dy, theme)
  }
  for (const color of LUDO_COLORS) {
    const state = colorState(game, color)
    const arrowFill = alpha(state.active ? '#171717' : darkDim(themeColor(theme, color), 0.32), state.active ? 0.88 : 0.34)
    for (const [row,col,dx,dy] of HOME_ARROWS[color]) {
      drawArrowVector(ctx, row, col, dx, dy, theme, arrowFill)
    }
  }
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
    drawCell(ctx, start[0], start[1], state.active ? visual : alpha(visual, 0.38), theme.grid)
    for (const [row,col] of LUDO_HOME_LANES[color]) {
      drawCell(ctx, row, col, state.active ? alpha(visual, 0.88) : alpha(visual, 0.30), theme.grid)
    }
  }

  // Nigerian market boards rely on directional arrows, not star icons, to
  // communicate route flow. Safe-square behaviour remains in the rules engine.
  drawDirectionArrows(ctx, theme, game)
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
  const base = faint ? darkDim(themeColor(theme, color), 0.42) : themeColor(theme, color)

  ctx.save()
  ctx.globalAlpha = faint ? 0.44 : 1

  // Physical Nigerian-board seed/counter: a flat circular chip with a raised rim,
  // not a pawn and not a spherical bubble.
  ctx.shadowColor = 'rgba(0,0,0,0.34)'
  ctx.shadowBlur = faint ? 2 : 5
  ctx.shadowOffsetY = faint ? 1 : 3
  ctx.fillStyle = shadeHex(base, -0.30)
  ctx.beginPath()
  ctx.arc(cx, cy + radius * 0.08, radius, 0, Math.PI * 2)
  ctx.fill()

  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0

  // Main coloured face.
  ctx.fillStyle = base
  ctx.beginPath()
  ctx.arc(cx, cy, radius * 0.92, 0, Math.PI * 2)
  ctx.fill()

  // Bevel ring.
  ctx.strokeStyle = shadeHex(base, -0.22)
  ctx.lineWidth = Math.max(2, radius * 0.12)
  ctx.beginPath()
  ctx.arc(cx, cy, radius * 0.78, 0, Math.PI * 2)
  ctx.stroke()

  // Small restrained highlight so it reads as plastic, while staying circular.
  ctx.strokeStyle = 'rgba(255,255,255,0.46)'
  ctx.lineWidth = Math.max(1.3, radius * 0.07)
  ctx.beginPath()
  ctx.arc(cx - radius * 0.05, cy - radius * 0.06, radius * 0.70, Math.PI * 1.08, Math.PI * 1.58)
  ctx.stroke()

  if (selected) {
    ctx.strokeStyle = theme.hint
    ctx.lineWidth = 4
    ctx.beginPath()
    ctx.arc(cx, cy, radius * 1.06, 0, Math.PI * 2)
    ctx.stroke()
  }

  ctx.fillStyle = color === 'yellow' && !faint ? '#171717' : '#ffffff'
  ctx.font = `bold ${Math.max(11, Math.floor(radius * 0.86))}px "DejaVu Sans Bold", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(String(label), cx, cy + 0.5)
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
        Boolean(item.player.eliminated),
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
        Boolean(item.player.eliminated),
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
    if (!player) continue

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
