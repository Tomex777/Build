import { existsSync } from 'node:fs'
import { createCanvas, GlobalFonts, loadImage } from '@napi-rs/canvas'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'Night Sans'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'Night Sans Bold'],
]) {
  try {
    if (existsSync(file)) GlobalFonts.registerFromPath(file, family)
  } catch {}
}

const FONT = '"Night Sans", "DejaVu Sans", sans-serif'
const BOLD = '"Night Sans Bold", "DejaVu Sans", sans-serif'

function clean(value, max = 800) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function handleOf(name = '') {
  const stem = clean(name, 60).toLowerCase().replace(/[^a-z0-9]+/g, '').slice(0, 22)
  return '@' + (stem || 'nightuser')
}

function roundedRect(ctx, x, y, width, height, radius) {
  const r = Math.max(0, Math.min(radius, Math.min(width, height) / 2))
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + width, y, x + width, y + height, r)
  ctx.arcTo(x + width, y + height, x, y + height, r)
  ctx.arcTo(x, y + height, x, y, r)
  ctx.arcTo(x, y, x + width, y, r)
  ctx.closePath()
}

function wrapLines(ctx, text, maxWidth, maxLines = 12) {
  const words = clean(text).split(/\s+/).filter(Boolean)
  const lines = []
  let line = ''

  for (const word of words) {
    const test = line ? line + ' ' + word : word
    if (ctx.measureText(test).width <= maxWidth || !line) {
      line = test
      continue
    }
    lines.push(line)
    line = word
    if (lines.length >= maxLines) break
  }
  if (line && lines.length < maxLines) lines.push(line)
  if (words.length && lines.length === maxLines) {
    let last = lines.at(-1)
    while (last.length > 1 && ctx.measureText(last + '…').width > maxWidth) last = last.slice(0, -1)
    lines[lines.length - 1] = last.replace(/[\s.,;:!?-]+$/,'') + '…'
  }
  return lines
}

function drawWatermark(ctx, width, height, {
  light = false,
  fontSize = 14,
  inset = 22,
} = {}) {
  ctx.save()
  ctx.globalAlpha = 0.22
  ctx.fillStyle = light ? '#ffffff' : '#111111'
  ctx.font = `${fontSize}px ${FONT}`
  ctx.textAlign = 'right'
  ctx.textBaseline = 'bottom'
  ctx.fillText('Night', width - inset, height - inset)
  ctx.restore()
}

async function avatarImage(buffer) {
  if (!Buffer.isBuffer(buffer) || !buffer.length) return null
  try { return await loadImage(buffer) } catch { return null }
}

async function drawAvatar(ctx, x, y, size, {
  avatarBuffer = null,
  authorName = 'Night User',
  fill = '#6d5dfc',
  text = '#ffffff',
} = {}) {
  const image = await avatarImage(avatarBuffer)
  ctx.save()
  ctx.beginPath()
  ctx.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2)
  ctx.clip()

  if (image) {
    const scale = Math.max(size / image.width, size / image.height)
    const w = image.width * scale
    const h = image.height * scale
    ctx.drawImage(image, x + (size - w) / 2, y + (size - h) / 2, w, h)
  } else {
    ctx.fillStyle = fill
    ctx.fillRect(x, y, size, size)
    ctx.fillStyle = text
    ctx.font = `bold ${Math.floor(size * 0.43)}px ${BOLD}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const initial = clean(authorName, 2).charAt(0).toUpperCase() || 'N'
    ctx.fillText(initial, x + size / 2, y + size / 2 + 1)
  }
  ctx.restore()
}

function drawActionRow(ctx, {
  x = 76,
  y,
  width,
  color,
} = {}) {
  const labels = ['○', '↻', '♡', '▢', '↗']
  ctx.fillStyle = color
  ctx.font = `30px ${FONT}`
  ctx.textAlign = 'center'
  for (let i = 0; i < labels.length; i += 1) {
    const cx = x + i * (width / (labels.length - 1))
    ctx.fillText(labels[i], cx, y)
  }
}

export async function renderTweetCard({
  text,
  theme = 'light',
  authorName = 'Night User',
  handle = '',
  avatarBuffer = null,
  timestamp = '',
} = {}) {
  const dark = String(theme).toLowerCase() === 'dark'
  const width = 1000
  const height = 610
  const canvas = createCanvas(width, height)
  const ctx = canvas.getContext('2d')

  const bg = dark ? '#07090b' : '#ffffff'
  const fg = dark ? '#f2f5f7' : '#111418'
  const muted = dark ? '#8b98a5' : '#687684'
  const line = dark ? '#232b32' : '#e5e9ec'

  ctx.fillStyle = bg
  ctx.fillRect(0, 0, width, height)

  await drawAvatar(ctx, 58, 50, 88, { avatarBuffer, authorName })
  ctx.fillStyle = fg
  ctx.font = `32px ${BOLD}`
  ctx.textAlign = 'left'
  ctx.fillText(clean(authorName, 48), 168, 85)
  ctx.fillStyle = muted
  ctx.font = `25px ${FONT}`
  ctx.fillText(clean(handle || handleOf(authorName), 34), 168, 121)

  ctx.fillStyle = muted
  ctx.font = `32px ${BOLD}`
  ctx.textAlign = 'right'
  ctx.fillText('•••', 942, 85)

  ctx.fillStyle = fg
  ctx.font = `34px ${FONT}`
  ctx.textAlign = 'left'
  const lines = wrapLines(ctx, text, 884, 6)
  let y = 190
  for (const row of lines) {
    ctx.fillText(row, 58, y)
    y += 50
  }

  ctx.fillStyle = muted
  ctx.font = `23px ${FONT}`
  ctx.fillText(clean(timestamp || 'Just now', 80), 58, 438)

  ctx.strokeStyle = line
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(58, 472)
  ctx.lineTo(942, 472)
  ctx.stroke()

  ctx.fillStyle = fg
  ctx.font = `23px ${BOLD}`
  ctx.fillText('0', 58, 514)
  ctx.fillStyle = muted
  ctx.font = `23px ${FONT}`
  ctx.fillText('replies', 76, 514)
  ctx.fillStyle = fg
  ctx.font = `23px ${BOLD}`
  ctx.fillText('0', 180, 514)
  ctx.fillStyle = muted
  ctx.font = `23px ${FONT}`
  ctx.fillText('reposts', 198, 514)

  ctx.strokeStyle = line
  ctx.beginPath()
  ctx.moveTo(58, 538)
  ctx.lineTo(942, 538)
  ctx.stroke()
  drawActionRow(ctx, { x:80, y:582, width:820, color:muted })

  drawWatermark(ctx, width, height, { light:dark, fontSize:14, inset:18 })
  return canvas.toBuffer('image/png')
}

function gradient(ctx, width, height, colors) {
  const g = ctx.createLinearGradient(0, 0, width, height)
  colors.forEach(([stop, color]) => g.addColorStop(stop, color))
  ctx.fillStyle = g
  ctx.fillRect(0, 0, width, height)
}

export async function renderPostCard({
  text,
  style = 'generic',
  authorName = 'Night User',
  avatarBuffer = null,
} = {}) {
  const mode = String(style || 'generic').toLowerCase()
  if (mode === 'story') {
    const width = 1080
    const height = 1920
    const canvas = createCanvas(width, height)
    const ctx = canvas.getContext('2d')
    gradient(ctx, width, height, [[0,'#24163f'],[0.52,'#704174'],[1,'#d27672']])

    ctx.fillStyle = 'rgba(255,255,255,0.9)'
    ctx.fillRect(40, 40, width - 80, 6)
    await drawAvatar(ctx, 48, 82, 72, { avatarBuffer, authorName, fill:'#17171b' })
    ctx.fillStyle = '#ffffff'
    ctx.font = `27px ${BOLD}`
    ctx.fillText(clean(authorName, 42), 140, 125)

    ctx.fillStyle = 'rgba(13,13,18,0.56)'
    roundedRect(ctx, 120, 690, 840, 390, 34)
    ctx.fill()

    ctx.fillStyle = '#ffffff'
    ctx.font = `46px ${BOLD}`
    ctx.textAlign = 'center'
    const lines = wrapLines(ctx, text, 720, 6)
    let y = 810 - ((lines.length - 1) * 32)
    for (const line of lines) {
      ctx.fillText(line, width / 2, y)
      y += 66
    }

    ctx.strokeStyle = 'rgba(255,255,255,0.7)'
    ctx.lineWidth = 2
    roundedRect(ctx, 80, 1760, 800, 92, 46)
    ctx.stroke()
    ctx.fillStyle = '#ffffff'
    ctx.font = `28px ${FONT}`
    ctx.textAlign = 'left'
    ctx.fillText('Send message…', 120, 1818)
    drawWatermark(ctx, width, height, { light:true, fontSize:18, inset:26 })
    return canvas.toBuffer('image/png')
  }

  const width = 1000
  const height = mode === 'instagram' ? 1040 : 760
  const canvas = createCanvas(width, height)
  const ctx = canvas.getContext('2d')
  const facebook = mode === 'facebook'
  const generic = !['instagram','facebook'].includes(mode)

  if (generic) gradient(ctx, width, height, [[0,'#11141d'],[1,'#28213e']])
  else {
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, width, height)
  }

  const fg = generic ? '#ffffff' : '#121212'
  const muted = generic ? '#a8adba' : '#6b7077'

  await drawAvatar(ctx, 46, 40, 76, {
    avatarBuffer,
    authorName,
    fill:facebook ? '#315fbd' : '#7b4ee8',
  })
  ctx.fillStyle = fg
  ctx.font = `29px ${BOLD}`
  ctx.textAlign = 'left'
  ctx.fillText(clean(authorName, 44), 140, 78)
  ctx.fillStyle = muted
  ctx.font = `20px ${FONT}`
  ctx.fillText('now', 140, 108)

  if (mode === 'instagram') {
    gradient(ctx, width - 80, 560, [[0,'#3b284f'],[0.48,'#965b6f'],[1,'#ec9c68']])
    ctx.fillStyle = 'rgba(0,0,0,0.28)'
    roundedRect(ctx, 115, 290, 770, 210, 28)
    ctx.fill()
    ctx.fillStyle = '#ffffff'
    ctx.font = `38px ${BOLD}`
    ctx.textAlign = 'center'
    const lines = wrapLines(ctx, text, 650, 4)
    let y = 365 - ((lines.length - 1) * 27)
    for (const row of lines) {
      ctx.fillText(row, width / 2, y)
      y += 55
    }
    ctx.fillStyle = '#171717'
    ctx.font = `35px ${FONT}`
    ctx.textAlign = 'left'
    ctx.fillText('♡   ○   ▷', 48, 910)
    ctx.textAlign = 'right'
    ctx.fillText('▢', 946, 910)
    ctx.fillStyle = '#151515'
    ctx.font = `22px ${BOLD}`
    ctx.textAlign = 'left'
    ctx.fillText(clean(authorName, 30), 48, 960)
    ctx.font = `22px ${FONT}`
    ctx.fillText('  ' + clean(text, 90), 48 + ctx.measureText(clean(authorName,30)).width, 960)
    drawWatermark(ctx, width, height, { light:false, fontSize:14, inset:18 })
    return canvas.toBuffer('image/png')
  }

  ctx.fillStyle = fg
  ctx.font = generic ? `43px ${BOLD}` : `32px ${FONT}`
  ctx.textAlign = generic ? 'center' : 'left'
  const maxWidth = generic ? 780 : 890
  const lines = wrapLines(ctx, text, maxWidth, generic ? 7 : 6)
  let y = generic ? 275 - ((lines.length - 1) * 28) : 185
  for (const row of lines) {
    ctx.fillText(row, generic ? width / 2 : 48, y)
    y += generic ? 62 : 48
  }

  if (facebook) {
    ctx.strokeStyle = '#e1e4e8'
    ctx.beginPath()
    ctx.moveTo(48, 590)
    ctx.lineTo(952, 590)
    ctx.stroke()
    ctx.fillStyle = muted
    ctx.font = `24px ${FONT}`
    ctx.fillText('♡ Like      ○ Comment      ↗ Share', 78, 650)
  } else {
    ctx.fillStyle = 'rgba(255,255,255,0.12)'
    roundedRect(ctx, 160, 520, 680, 1, 1)
    ctx.fill()
    ctx.fillStyle = muted
    ctx.font = `22px ${FONT}`
    ctx.textAlign = 'center'
    ctx.fillText('shared with Night', width / 2, 580)
  }

  drawWatermark(ctx, width, height, { light:generic, fontSize:14, inset:18 })
  return canvas.toBuffer('image/png')
}
