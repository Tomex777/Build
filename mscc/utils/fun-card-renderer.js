import { existsSync } from 'node:fs'
import { createCanvas, GlobalFonts, loadImage } from '@napi-rs/canvas'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'Night Sans'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'Night Sans Bold'],
]) {
  try { if (existsSync(file)) GlobalFonts.registerFromPath(file, family) } catch {}
}

const FONT = '"Night Sans", "DejaVu Sans", sans-serif'
const BOLD = '"Night Sans Bold", "DejaVu Sans", sans-serif'

function clean(value, max = 1000) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function rounded(ctx, x, y, w, h, r) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

function wrap(ctx, text, width, maxLines = 10) {
  const words = clean(text).split(/\s+/).filter(Boolean)
  const lines = []
  let line = ''
  for (const word of words) {
    const test = line ? line + ' ' + word : word
    if (!line || ctx.measureText(test).width <= width) {
      line = test
    } else {
      lines.push(line)
      line = word
      if (lines.length >= maxLines) break
    }
  }
  if (line && lines.length < maxLines) lines.push(line)
  if (lines.length === maxLines && words.join(' ').length > lines.join(' ').length) {
    let last = lines.at(-1)
    while (last.length > 1 && ctx.measureText(last + '…').width > width) last = last.slice(0, -1)
    lines[lines.length - 1] = last.replace(/[\s.,;:!?-]+$/,'') + '…'
  }
  return lines
}

function watermark(ctx, w, h, light = true) {
  ctx.save()
  ctx.globalAlpha = 0.18
  ctx.fillStyle = light ? '#ffffff' : '#151515'
  ctx.font = '13px ' + FONT
  ctx.textAlign = 'right'
  ctx.textBaseline = 'bottom'
  ctx.fillText('Night', w - 16, h - 14)
  ctx.restore()
}

async function loadAvatar(buffer) {
  if (!Buffer.isBuffer(buffer) || !buffer.length) return null
  try { return await loadImage(buffer) } catch { return null }
}

async function circleAvatar(ctx, x, y, size, buffer, name) {
  const image = await loadAvatar(buffer)
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
    ctx.fillStyle = '#5e6572'
    ctx.fillRect(x, y, size, size)
    ctx.fillStyle = '#fff'
    ctx.font = Math.floor(size * 0.42) + 'px ' + BOLD
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(clean(name, 1).toUpperCase() || 'N', x + size / 2, y + size / 2)
  }
  ctx.restore()
}

async function coverImage(ctx, buffer, x, y, w, h) {
  const image = await loadAvatar(buffer)
  if (!image) return false
  const scale = Math.max(w / image.width, h / image.height)
  const dw = image.width * scale
  const dh = image.height * scale
  ctx.drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh)
  return true
}

export async function renderQuoteCard({ text, authorName = 'Night User', avatarBuffer = null } = {}) {
  const w = 1080, h = 1080
  const canvas = createCanvas(w, h)
  const ctx = canvas.getContext('2d')
  const g = ctx.createLinearGradient(0, 0, w, h)
  g.addColorStop(0, '#0d1117')
  g.addColorStop(1, '#242a35')
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)

  ctx.fillStyle = 'rgba(255,255,255,0.08)'
  ctx.font = '260px ' + BOLD
  ctx.fillText('“', 70, 285)

  ctx.fillStyle = '#f3f5f7'
  ctx.font = '48px ' + BOLD
  ctx.textAlign = 'left'
  const lines = wrap(ctx, text, 850, 8)
  let y = 360 - Math.max(0, lines.length - 4) * 24
  for (const line of lines) {
    ctx.fillText(line, 115, y)
    y += 68
  }

  await circleAvatar(ctx, 115, 845, 88, avatarBuffer, authorName)
  ctx.fillStyle = '#ffffff'
  ctx.font = '28px ' + BOLD
  ctx.fillText(clean(authorName, 48), 228, 890)
  ctx.fillStyle = '#8b949e'
  ctx.font = '21px ' + FONT
  ctx.fillText('quoted on Night', 228, 924)
  watermark(ctx, w, h, true)
  return canvas.toBuffer('image/png')
}

export async function renderCaptionCard({ imageBuffer, text } = {}) {
  const w = 1080, h = 1080
  const canvas = createCanvas(w, h)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#090909'
  ctx.fillRect(0, 0, w, h)
  const ok = await coverImage(ctx, imageBuffer, 0, 0, w, h)
  if (!ok) throw new Error('Reply to an image with .caption <text>.')

  const g = ctx.createLinearGradient(0, 620, 0, 1080)
  g.addColorStop(0, 'rgba(0,0,0,0)')
  g.addColorStop(1, 'rgba(0,0,0,0.88)')
  ctx.fillStyle = g
  ctx.fillRect(0, 590, w, 490)

  ctx.fillStyle = '#ffffff'
  ctx.font = '42px ' + BOLD
  ctx.textAlign = 'center'
  const lines = wrap(ctx, text, 870, 5)
  let y = 860 - Math.max(0, lines.length - 1) * 28
  for (const line of lines) {
    ctx.fillText(line, w / 2, y)
    y += 58
  }
  watermark(ctx, w, h, true)
  return canvas.toBuffer('image/png')
}

export async function renderWantedCard({ authorName = 'Unknown', avatarBuffer = null } = {}) {
  const w = 900, h = 1200
  const canvas = createCanvas(w, h)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#d8c391'
  ctx.fillRect(0, 0, w, h)
  ctx.fillStyle = 'rgba(74,48,19,0.08)'
  for (let y=0;y<h;y+=22) ctx.fillRect(0, y, w, 1)

  ctx.strokeStyle = '#4a3013'
  ctx.lineWidth = 8
  ctx.strokeRect(34, 34, w - 68, h - 68)

  ctx.fillStyle = '#3b2410'
  ctx.textAlign = 'center'
  ctx.font = '112px ' + BOLD
  ctx.fillText('WANTED', w / 2, 165)
  ctx.font = '31px ' + BOLD
  ctx.fillText('FOR HIGHLY QUESTIONABLE BEHAVIOR', w / 2, 215)

  ctx.fillStyle = '#5b4024'
  ctx.fillRect(115, 270, 670, 650)
  const avatar = await loadAvatar(avatarBuffer)
  if (avatar) {
    const scale = Math.max(650 / avatar.width, 650 / avatar.height)
    const dw = avatar.width * scale, dh = avatar.height * scale
    ctx.save()
    ctx.beginPath()
    ctx.rect(125, 280, 650, 630)
    ctx.clip()
    ctx.drawImage(avatar, 125 + (650-dw)/2, 280 + (630-dh)/2, dw, dh)
    ctx.restore()
  } else {
    ctx.fillStyle = '#725332'
    ctx.fillRect(125, 280, 650, 630)
    ctx.fillStyle = '#ead8ad'
    ctx.font = '220px ' + BOLD
    ctx.fillText(clean(authorName,1).toUpperCase() || '?', w / 2, 665)
  }

  ctx.fillStyle = '#3b2410'
  ctx.font = '49px ' + BOLD
  ctx.fillText(clean(authorName, 34).toUpperCase(), w / 2, 1000)
  ctx.font = '27px ' + FONT
  ctx.fillText('REWARD: one suspiciously good meme', w / 2, 1055)
  watermark(ctx, w, h, false)
  return canvas.toBuffer('image/png')
}

export async function renderJailCard({ authorName = 'Night User', avatarBuffer = null } = {}) {
  const w = 900, h = 1000
  const canvas = createCanvas(w, h)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#d9dde0'
  ctx.fillRect(0,0,w,h)

  ctx.strokeStyle = '#8a9298'
  ctx.lineWidth = 3
  ctx.textAlign = 'left'
  ctx.font = '18px ' + BOLD
  for (let y=180, n=190; y<900; y+=120, n-=10) {
    ctx.beginPath(); ctx.moveTo(0,y); ctx.lineTo(w,y); ctx.stroke()
    ctx.fillStyle = '#6e767d'; ctx.fillText(String(n) + ' cm', 18, y - 8)
  }

  await circleAvatar(ctx, 200, 190, 500, avatarBuffer, authorName)

  ctx.fillStyle = '#202428'
  rounded(ctx, 175, 715, 550, 110, 12)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.textAlign = 'center'
  ctx.font = '38px ' + BOLD
  ctx.fillText(clean(authorName, 32), w/2, 765)
  ctx.font = '22px ' + FONT
  ctx.fillText('NIGHT COUNTY · CASE 0001', w/2, 802)

  ctx.strokeStyle = '#1e2327'
  ctx.lineWidth = 18
  for (let x=55;x<w;x+=125) {
    ctx.beginPath(); ctx.moveTo(x,0); ctx.lineTo(x,h); ctx.stroke()
  }
  ctx.lineWidth = 22
  ctx.beginPath(); ctx.moveTo(0,110); ctx.lineTo(w,110); ctx.stroke()
  ctx.beginPath(); ctx.moveTo(0,895); ctx.lineTo(w,895); ctx.stroke()

  watermark(ctx,w,h,false)
  return canvas.toBuffer('image/png')
}

export async function renderWastedCard({ authorName = 'Night User', avatarBuffer = null } = {}) {
  const w = 1000, h = 1000
  const canvas = createCanvas(w,h)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#131313'
  ctx.fillRect(0,0,w,h)
  const ok = await coverImage(ctx, avatarBuffer, 0, 0, w, h)
  if (!ok) {
    ctx.fillStyle = '#353535'
    ctx.fillRect(0,0,w,h)
    ctx.fillStyle = '#777'
    ctx.font = '300px ' + BOLD
    ctx.textAlign = 'center'
    ctx.fillText(clean(authorName,1).toUpperCase() || 'N', w/2, 600)
  }
  ctx.fillStyle = 'rgba(0,0,0,0.58)'
  ctx.fillRect(0,0,w,h)
  ctx.fillStyle = '#d34242'
  ctx.font = '118px ' + BOLD
  ctx.textAlign = 'center'
  ctx.fillText('WASTED', w/2, 550)
  ctx.fillStyle = '#e2e2e2'
  ctx.font = '27px ' + FONT
  ctx.fillText(clean(authorName, 46), w/2, 605)
  watermark(ctx,w,h,true)
  return canvas.toBuffer('image/png')
}

export async function renderAchievementCard({ text } = {}) {
  const w = 1100, h = 420
  const canvas = createCanvas(w,h)
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#11151b'
  ctx.fillRect(0,0,w,h)
  ctx.fillStyle = '#1d242d'
  rounded(ctx, 35, 35, 1030, 350, 28)
  ctx.fill()

  ctx.fillStyle = '#f1b83b'
  ctx.beginPath()
  ctx.arc(180, 210, 105, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#11151b'
  ctx.font = '120px ' + BOLD
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('★', 180, 205)

  ctx.textAlign = 'left'
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = '#f1b83b'
  ctx.font = '28px ' + BOLD
  ctx.fillText('ACHIEVEMENT UNLOCKED', 330, 145)
  ctx.fillStyle = '#ffffff'
  ctx.font = '45px ' + BOLD
  const lines = wrap(ctx, text, 660, 3)
  let y = 215
  for (const line of lines) {
    ctx.fillText(line, 330, y)
    y += 58
  }
  watermark(ctx,w,h,true)
  return canvas.toBuffer('image/png')
}
