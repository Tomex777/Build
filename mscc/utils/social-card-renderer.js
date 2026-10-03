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

function clean(value, max = 1000) {
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
  let consumed = 0

  for (const word of words) {
    const test = line ? line + ' ' + word : word
    if (ctx.measureText(test).width <= maxWidth || !line) {
      line = test
      consumed += 1
      continue
    }
    lines.push(line)
    line = word
    consumed += 1
    if (lines.length >= maxLines) break
  }

  if (line && lines.length < maxLines) lines.push(line)

  if (consumed < words.length && lines.length) {
    let last = lines.at(-1)
    while (last.length > 1 && ctx.measureText(last + '…').width > maxWidth) last = last.slice(0, -1)
    lines[lines.length - 1] = last.replace(/[\s.,;:!?-]+$/,'') + '…'
  }

  return lines
}

function drawWatermark(ctx, width, height, {
  light = false,
  fontSize = 13,
  inset = 16,
} = {}) {
  ctx.save()
  ctx.globalAlpha = 0.18
  ctx.fillStyle = light ? '#ffffff' : '#0f1419'
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
  fill = '#536471',
  text = '#ffffff',
  ring = '',
  ringWidth = 0,
} = {}) {
  if (ring && ringWidth > 0) {
    ctx.save()
    ctx.strokeStyle = ring
    ctx.lineWidth = ringWidth
    ctx.beginPath()
    ctx.arc(x + size / 2, y + size / 2, size / 2 + ringWidth + 1, 0, Math.PI * 2)
    ctx.stroke()
    ctx.restore()
  }

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
    ctx.font = `bold ${Math.floor(size * 0.42)}px ${BOLD}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const initial = clean(authorName, 2).charAt(0).toUpperCase() || 'N'
    ctx.fillText(initial, x + size / 2, y + size / 2 + 1)
  }
  ctx.restore()
}

function strokeIcon(ctx, path, {
  x,
  y,
  size = 24,
  color,
  lineWidth = 2,
  fill = false,
} = {}) {
  ctx.save()
  ctx.translate(x, y)
  ctx.scale(size / 24, size / 24)
  ctx.lineWidth = lineWidth * 24 / size
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  ctx.strokeStyle = color
  ctx.fillStyle = color
  path(ctx)
  if (fill) ctx.fill()
  else ctx.stroke()
  ctx.restore()
}

function iconReply(ctx) {
  ctx.beginPath()
  ctx.moveTo(20.5, 11.5)
  ctx.bezierCurveTo(20.5, 6.8, 16.8, 3.5, 12.2, 3.5)
  ctx.bezierCurveTo(7.4, 3.5, 3.5, 6.9, 3.5, 11.5)
  ctx.bezierCurveTo(3.5, 15.8, 7, 19, 11.4, 19)
  ctx.lineTo(13.2, 19)
  ctx.lineTo(17.8, 21.5)
  ctx.lineTo(17.8, 17.9)
  ctx.bezierCurveTo(19.6, 16.2, 20.5, 14, 20.5, 11.5)
}

function iconRepost(ctx) {
  ctx.beginPath()
  ctx.moveTo(7, 7)
  ctx.lineTo(18, 7)
  ctx.lineTo(15, 4)
  ctx.moveTo(18, 7)
  ctx.lineTo(15, 10)
  ctx.moveTo(17, 17)
  ctx.lineTo(6, 17)
  ctx.lineTo(9, 20)
  ctx.moveTo(6, 17)
  ctx.lineTo(9, 14)
}

function iconHeart(ctx) {
  ctx.beginPath()
  ctx.moveTo(12, 20)
  ctx.bezierCurveTo(10, 18.4, 4, 14.7, 4, 9.7)
  ctx.bezierCurveTo(4, 6.7, 6.1, 4.5, 9.1, 4.5)
  ctx.bezierCurveTo(10.8, 4.5, 11.8, 5.5, 12, 6)
  ctx.bezierCurveTo(12.2, 5.5, 13.2, 4.5, 14.9, 4.5)
  ctx.bezierCurveTo(17.9, 4.5, 20, 6.7, 20, 9.7)
  ctx.bezierCurveTo(20, 14.7, 14, 18.4, 12, 20)
}

function iconBookmark(ctx) {
  ctx.beginPath()
  ctx.moveTo(7, 4)
  ctx.lineTo(17, 4)
  ctx.bezierCurveTo(18.1, 4, 19, 4.9, 19, 6)
  ctx.lineTo(19, 20)
  ctx.lineTo(12, 15.8)
  ctx.lineTo(5, 20)
  ctx.lineTo(5, 6)
  ctx.bezierCurveTo(5, 4.9, 5.9, 4, 7, 4)
}

function iconShare(ctx) {
  ctx.beginPath()
  ctx.moveTo(12, 16)
  ctx.lineTo(12, 5)
  ctx.moveTo(8.5, 8.5)
  ctx.lineTo(12, 5)
  ctx.lineTo(15.5, 8.5)
  ctx.moveTo(6, 12)
  ctx.lineTo(6, 18)
  ctx.bezierCurveTo(6, 19.1, 6.9, 20, 8, 20)
  ctx.lineTo(16, 20)
  ctx.bezierCurveTo(17.1, 20, 18, 19.1, 18, 18)
  ctx.lineTo(18, 12)
}

function iconComment(ctx) {
  iconReply(ctx)
}

function iconSend(ctx) {
  ctx.beginPath()
  ctx.moveTo(4, 5)
  ctx.lineTo(20, 12)
  ctx.lineTo(4, 19)
  ctx.lineTo(7.5, 12)
  ctx.closePath()
}

function drawVerifiedBadge(ctx, x, y, size = 22) {
  ctx.save()
  ctx.translate(x, y)
  ctx.scale(size / 22, size / 22)
  ctx.fillStyle = '#1d9bf0'
  ctx.beginPath()
  ctx.arc(11, 11, 9.2, 0, Math.PI * 2)
  ctx.fill()

  ctx.strokeStyle = '#ffffff'
  ctx.lineWidth = 1.9
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.beginPath()
  ctx.moveTo(6.7, 11.3)
  ctx.lineTo(9.5, 14)
  ctx.lineTo(15.4, 7.9)
  ctx.stroke()
  ctx.restore()
}

function drawAccentLine(ctx, line, x, y, {
  color,
  accent = '#1d9bf0',
  font,
} = {}) {
  ctx.font = font
  const parts = String(line || '').split(/([@#][A-Za-z0-9_]+)/g)
  let offset = x
  for (const part of parts) {
    if (!part) continue
    ctx.fillStyle = /^[@#][A-Za-z0-9_]+$/.test(part) ? accent : color
    ctx.fillText(part, offset, y)
    offset += ctx.measureText(part).width
  }
}

function compactCount(value) {
  const n = Number(value || 0)
  if (!Number.isFinite(n) || n <= 0) return '0'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace('.0','') + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(n >= 10_000 ? 0 : 1).replace('.0','') + 'K'
  return String(Math.floor(n))
}

export async function renderTweetCard({
  text,
  theme = 'light',
  authorName = 'Night User',
  handle = '',
  avatarBuffer = null,
  timestamp = 'Just now',
  verified = false,
  replyCount = 0,
  repostCount = 0,
  likeCount = 0,
  bookmarkCount = 0,
  viewCount = 0,
} = {}) {
  const dark = String(theme).toLowerCase() === 'dark'
  const width = 1000
  const height = 650
  const canvas = createCanvas(width, height)
  const ctx = canvas.getContext('2d')

  const bg = dark ? '#000000' : '#ffffff'
  const fg = dark ? '#e7e9ea' : '#0f1419'
  const muted = dark ? '#71767b' : '#536471'
  const line = dark ? '#2f3336' : '#eff3f4'
  const accent = '#1d9bf0'

  ctx.fillStyle = bg
  ctx.fillRect(0, 0, width, height)

  await drawAvatar(ctx, 54, 42, 88, {
    avatarBuffer,
    authorName,
    fill:dark ? '#333639' : '#cfd9de',
    text:dark ? '#eff3f4' : '#0f1419',
  })

  ctx.textAlign = 'left'
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = fg
  ctx.font = `30px ${BOLD}`
  const name = clean(authorName, 48)
  ctx.fillText(name, 164, 76)
  let nameEnd = 164 + ctx.measureText(name).width
  if (verified && nameEnd < 840) drawVerifiedBadge(ctx, nameEnd + 10, 54, 21)

  ctx.fillStyle = muted
  ctx.font = `24px ${FONT}`
  ctx.fillText(clean(handle || handleOf(authorName), 34), 164, 111)

  ctx.fillStyle = muted
  ctx.font = `28px ${BOLD}`
  ctx.textAlign = 'right'
  ctx.fillText('•••', 946, 76)

  ctx.textAlign = 'left'
  const bodyFont = `35px ${FONT}`
  ctx.font = bodyFont
  const lines = wrapLines(ctx, text, 892, 6)
  let y = 188
  for (const row of lines) {
    drawAccentLine(ctx, row, 54, y, { color:fg, accent, font:bodyFont })
    y += 52
  }

  const timestampY = 440
  ctx.fillStyle = muted
  ctx.font = `22px ${FONT}`
  ctx.fillText(clean(timestamp, 80), 54, timestampY)
  if (Number(viewCount) > 0) {
    const stampWidth = ctx.measureText(clean(timestamp,80)).width
    ctx.fillText(' · ' + compactCount(viewCount) + ' Views', 54 + stampWidth, timestampY)
  }

  ctx.strokeStyle = line
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(54, 472)
  ctx.lineTo(946, 472)
  ctx.stroke()

  const stats = [
    [replyCount, 'Replies'],
    [repostCount, 'Reposts'],
    [likeCount, 'Likes'],
  ]
  let sx = 54
  for (const [count,label] of stats) {
    ctx.fillStyle = fg
    ctx.font = `22px ${BOLD}`
    const n = compactCount(count)
    ctx.fillText(n, sx, 510)
    const nw = ctx.measureText(n).width
    ctx.fillStyle = muted
    ctx.font = `22px ${FONT}`
    ctx.fillText(' ' + label, sx + nw, 510)
    sx += nw + ctx.measureText(' ' + label).width + 38
  }

  ctx.strokeStyle = line
  ctx.beginPath()
  ctx.moveTo(54, 535)
  ctx.lineTo(946, 535)
  ctx.stroke()

  const iconY = 568
  const iconColor = muted
  const iconXs = [80, 288, 496, 704, 912]
  strokeIcon(ctx, iconReply, { x:iconXs[0]-14, y:iconY-14, size:28, color:iconColor })
  strokeIcon(ctx, iconRepost, { x:iconXs[1]-14, y:iconY-14, size:28, color:iconColor })
  strokeIcon(ctx, iconHeart, { x:iconXs[2]-14, y:iconY-14, size:28, color:iconColor })
  strokeIcon(ctx, iconBookmark, { x:iconXs[3]-14, y:iconY-14, size:28, color:iconColor })
  strokeIcon(ctx, iconShare, { x:iconXs[4]-14, y:iconY-14, size:28, color:iconColor })

  const counts = [replyCount,repostCount,likeCount,bookmarkCount]
  for (let i=0;i<4;i+=1) {
    if (Number(counts[i]) <= 0) continue
    ctx.fillStyle = muted
    ctx.font = `18px ${FONT}`
    ctx.textAlign = 'left'
    ctx.fillText(compactCount(counts[i]), iconXs[i] + 21, iconY + 6)
  }

  drawWatermark(ctx, width, height, { light:dark, fontSize:13, inset:15 })
  return canvas.toBuffer('image/png')
}

function gradient(ctx, width, height, colors, x = 0, y = 0) {
  const g = ctx.createLinearGradient(x, y, x + width, y + height)
  colors.forEach(([stop, color]) => g.addColorStop(stop, color))
  ctx.fillStyle = g
  ctx.fillRect(x, y, width, height)
}

function drawInstagramActions(ctx, y, color) {
  strokeIcon(ctx, iconHeart, { x:48, y, size:34, color })
  strokeIcon(ctx, iconComment, { x:108, y, size:34, color })
  strokeIcon(ctx, iconSend, { x:168, y, size:34, color })
  strokeIcon(ctx, iconBookmark, { x:914, y, size:34, color })
}

function drawFacebookReactions(ctx, x, y) {
  const reactions = [
    ['#1877f2','f'],
    ['#f55368','♥'],
    ['#f7b125','☺'],
  ]
  let dx = 0
  for (const [color,label] of reactions) {
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(x + dx, y, 12, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = '#fff'
    ctx.font = `bold 14px ${BOLD}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(label, x + dx, y)
    dx += 19
  }
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

    gradient(ctx, width, height, [[0,'#1d1b3a'],[0.45,'#5d315e'],[1,'#d16f69']])

    const progressY = 30
    const gap = 8
    const barW = (width - 56 - gap * 3) / 4
    for (let i=0;i<4;i+=1) {
      ctx.fillStyle = i === 0 ? '#ffffff' : 'rgba(255,255,255,0.35)'
      roundedRect(ctx, 28 + i * (barW + gap), progressY, barW, 5, 3)
      ctx.fill()
    }

    await drawAvatar(ctx, 38, 65, 70, {
      avatarBuffer,
      authorName,
      fill:'#2d2d34',
      ring:'#ffffff',
      ringWidth:1.5,
    })
    ctx.fillStyle = '#ffffff'
    ctx.font = `27px ${BOLD}`
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
    ctx.fillText(clean(authorName, 42), 128, 108)
    ctx.font = `20px ${FONT}`
    ctx.fillStyle = 'rgba(255,255,255,0.78)'
    ctx.fillText('now', 128, 137)

    ctx.fillStyle = 'rgba(0,0,0,0.38)'
    roundedRect(ctx, 120, 690, 840, 360, 32)
    ctx.fill()

    ctx.fillStyle = '#ffffff'
    ctx.font = `45px ${BOLD}`
    ctx.textAlign = 'center'
    const lines = wrapLines(ctx, text, 710, 6)
    let y = 840 - ((lines.length - 1) * 31)
    for (const line of lines) {
      ctx.fillText(line, width / 2, y)
      y += 64
    }

    ctx.strokeStyle = 'rgba(255,255,255,0.9)'
    ctx.lineWidth = 2
    roundedRect(ctx, 58, 1765, 830, 92, 46)
    ctx.stroke()
    ctx.fillStyle = '#ffffff'
    ctx.font = `27px ${FONT}`
    ctx.textAlign = 'left'
    ctx.fillText('Send message…', 98, 1822)
    strokeIcon(ctx, iconHeart, { x:915, y:1790, size:36, color:'#ffffff' })
    strokeIcon(ctx, iconSend, { x:975, y:1790, size:36, color:'#ffffff' })

    drawWatermark(ctx, width, height, { light:true, fontSize:16, inset:22 })
    return canvas.toBuffer('image/png')
  }

  if (mode === 'instagram') {
    const width = 1000
    const height = 1120
    const canvas = createCanvas(width, height)
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, width, height)

    await drawAvatar(ctx, 34, 28, 70, {
      avatarBuffer,
      authorName,
      fill:'#7c3aed',
      ring:'#d62976',
      ringWidth:3,
    })
    ctx.fillStyle = '#101010'
    ctx.font = `26px ${BOLD}`
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
    ctx.fillText(clean(authorName, 38), 126, 66)
    ctx.font = `18px ${FONT}`
    ctx.fillStyle = '#737373'
    ctx.fillText('Original audio', 126, 93)
    ctx.fillStyle = '#111'
    ctx.font = `30px ${BOLD}`
    ctx.textAlign = 'right'
    ctx.fillText('•••', 957, 70)

    const mediaX = 0
    const mediaY = 124
    const mediaH = 650
    gradient(ctx, width, mediaH, [[0,'#261f44'],[0.48,'#7a4569'],[1,'#dc8f74']], mediaX, mediaY)

    ctx.fillStyle = 'rgba(0,0,0,0.25)'
    roundedRect(ctx, 100, 330, 800, 220, 30)
    ctx.fill()

    ctx.fillStyle = '#ffffff'
    ctx.font = `39px ${BOLD}`
    ctx.textAlign = 'center'
    const overlayLines = wrapLines(ctx, text, 670, 4)
    let oy = 410 - ((overlayLines.length - 1) * 27)
    for (const row of overlayLines) {
      ctx.fillText(row, width / 2, oy)
      oy += 56
    }

    drawInstagramActions(ctx, 806, '#111111')

    ctx.fillStyle = '#111111'
    ctx.font = `23px ${BOLD}`
    ctx.textAlign = 'left'
    ctx.fillText('0 likes', 44, 884)

    const username = clean(authorName, 30)
    ctx.font = `22px ${BOLD}`
    ctx.fillText(username, 44, 932)
    const nameW = ctx.measureText(username).width
    ctx.font = `22px ${FONT}`
    ctx.fillText(' ' + clean(text, 105), 44 + nameW, 932)

    ctx.fillStyle = '#737373'
    ctx.font = `20px ${FONT}`
    ctx.fillText('View all 0 comments', 44, 976)
    ctx.font = `17px ${FONT}`
    ctx.fillText('JUST NOW', 44, 1020)

    drawWatermark(ctx, width, height, { light:false, fontSize:13, inset:15 })
    return canvas.toBuffer('image/png')
  }

  if (mode === 'facebook') {
    const width = 1000
    const height = 760
    const canvas = createCanvas(width, height)
    const ctx = canvas.getContext('2d')

    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, width, height)

    await drawAvatar(ctx, 38, 34, 78, {
      avatarBuffer,
      authorName,
      fill:'#1877f2',
    })

    ctx.fillStyle = '#050505'
    ctx.font = `28px ${BOLD}`
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
    ctx.fillText(clean(authorName, 44), 136, 70)

    ctx.fillStyle = '#65676b'
    ctx.font = `20px ${FONT}`
    ctx.fillText('Just now · 🌐', 136, 100)

    ctx.fillStyle = '#65676b'
    ctx.font = `32px ${BOLD}`
    ctx.textAlign = 'right'
    ctx.fillText('•••', 948, 70)

    ctx.fillStyle = '#050505'
    ctx.font = `30px ${FONT}`
    ctx.textAlign = 'left'
    const lines = wrapLines(ctx, text, 900, 7)
    let y = 175
    for (const row of lines) {
      ctx.fillText(row, 44, y)
      y += 44
    }

    const statsY = 520
    drawFacebookReactions(ctx, 60, statsY)
    ctx.fillStyle = '#65676b'
    ctx.font = `20px ${FONT}`
    ctx.textAlign = 'left'
    ctx.fillText('0', 120, statsY + 7)
    ctx.textAlign = 'right'
    ctx.fillText('0 comments   0 shares', 948, statsY + 7)

    ctx.strokeStyle = '#ced0d4'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(40, 555)
    ctx.lineTo(960, 555)
    ctx.stroke()

    ctx.fillStyle = '#65676b'
    ctx.font = `23px ${BOLD}`
    ctx.textAlign = 'center'
    ctx.fillText('♡  Like', 205, 612)
    ctx.fillText('○  Comment', 500, 612)
    ctx.fillText('↗  Share', 795, 612)

    ctx.strokeStyle = '#ced0d4'
    ctx.beginPath()
    ctx.moveTo(40, 640)
    ctx.lineTo(960, 640)
    ctx.stroke()

    drawWatermark(ctx, width, height, { light:false, fontSize:13, inset:15 })
    return canvas.toBuffer('image/png')
  }

  const width = 1000
  const height = 760
  const canvas = createCanvas(width, height)
  const ctx = canvas.getContext('2d')
  gradient(ctx, width, height, [[0,'#11141d'],[1,'#28213e']])

  await drawAvatar(ctx, 46, 40, 76, {
    avatarBuffer,
    authorName,
    fill:'#6d5dfc',
  })
  ctx.fillStyle = '#ffffff'
  ctx.font = `29px ${BOLD}`
  ctx.textAlign = 'left'
  ctx.fillText(clean(authorName, 44), 140, 78)
  ctx.fillStyle = '#a8adba'
  ctx.font = `20px ${FONT}`
  ctx.fillText('now', 140, 108)

  ctx.fillStyle = '#ffffff'
  ctx.font = `43px ${BOLD}`
  ctx.textAlign = 'center'
  const lines = wrapLines(ctx, text, 780, 7)
  let y = 300 - ((lines.length - 1) * 28)
  for (const row of lines) {
    ctx.fillText(row, width / 2, y)
    y += 62
  }

  drawWatermark(ctx, width, height, { light:true, fontSize:13, inset:15 })
  return canvas.toBuffer('image/png')
}
