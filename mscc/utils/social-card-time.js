import { randomInt } from 'node:crypto'

/**
 * Format fictional card timestamps in each platform's usual visual language.
 * The elapsed time is relative to the image generation date (UTC).
 * This never reads or claims real X, Instagram or Facebook activity.
 */
export function formatSocialCardTime(style = 'tweet', ageMinutes = 180, now = new Date()) {
  const mode = String(style || 'tweet').toLowerCase()
  const minutes = Math.max(1, Math.floor(Number(ageMinutes) || 1))
  const hours = Math.max(1, Math.floor(minutes / 60))
  const days = Math.max(1, Math.floor(minutes / 1440))

  if (mode === 'tweet') {
    const date = new Date(new Date(now).getTime() - minutes * 60_000)
    const clock = date.toLocaleTimeString('en-US', {
      timeZone:'UTC', hour:'numeric', minute:'2-digit',
    })
    const calendar = date.toLocaleDateString('en-US', {
      timeZone:'UTC', month:'short', day:'numeric', year:'numeric',
    })
    return clock + ' · ' + calendar
  }
  if (mode === 'story') return hours + 'h'
  if (mode === 'facebook') {
    return minutes < 60 ? minutes + 'm' : minutes < 1440
      ? hours + 'h' : days < 7 ? days + 'd' : Math.floor(days / 7) + 'w'
  }
  if (mode === 'instagram') {
    if (minutes < 60) return minutes + ' MINUTES AGO'
    if (minutes < 1440) return hours + (hours === 1 ? ' HOUR AGO' : ' HOURS AGO')
    if (days < 7) return days + (days === 1 ? ' DAY AGO' : ' DAYS AGO')
    const date = new Date(new Date(now).getTime() - minutes * 60_000)
    return date.toLocaleDateString('en-US', {timeZone:'UTC', month:'long', day:'numeric'}).toUpperCase()
  }
  if (minutes < 60) return minutes + 'm ago'
  if (minutes < 1440) return hours + 'h ago'
  return days + (days === 1 ? ' day ago' : ' days ago')
}

// Favor hours/days over a repeated "Just now" label. Stories are always
// within their typical 24-hour lifespan. Call once per generated card so
// header/footer timestamps never contradict one another.
export function randomSocialCardTime(style = 'tweet', { now = new Date(), random = randomInt } = {}) {
  const mode = String(style || 'tweet').toLowerCase()
  let minutes
  if (mode === 'story') {
    minutes = random(60, 23 * 60 + 1)
  } else {
    const tier = random(100)
    minutes = tier < 45
      ? random(2 * 60, 23 * 60)
      : tier < 88
        ? random(24 * 60, 14 * 24 * 60)
        : random(14 * 24 * 60, 90 * 24 * 60)
  }
  return formatSocialCardTime(mode, minutes, now)
}
