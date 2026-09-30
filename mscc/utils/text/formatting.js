export const clipText = (value, max = 4096) => {
  const text = String(value ?? '')
  const limit = Math.max(0, Number(max) || 0)
  if (!limit || text.length <= limit) return text
  if (limit <= 1) return text.slice(0, limit)
  return text.slice(0, limit - 1) + '…'
}

export const compactLines = values =>
  values
    .flat(Infinity)
    .map(value => String(value ?? '').trim())
    .filter(Boolean)
    .join('\n')

export function chunkText(value, max = 3800) {
  const text = String(value ?? '')
  const limit = Math.max(1, Number(max) || 3800)
  if (!text) return ['']
  if (text.length <= limit) return [text]

  const chunks = []
  let remaining = text

  while (remaining.length > limit) {
    let cut = remaining.lastIndexOf('\n', limit)
    if (cut < Math.floor(limit * 0.5)) cut = remaining.lastIndexOf(' ', limit)
    if (cut < Math.floor(limit * 0.5)) cut = limit

    chunks.push(remaining.slice(0, cut).trimEnd())
    remaining = remaining.slice(cut).trimStart()
  }

  if (remaining) chunks.push(remaining)
  return chunks
}

export function humanBytes(value) {
  const bytes = Math.max(0, Number(value) || 0)
  if (bytes < 1024) return `${Math.round(bytes)} B`

  const units = ['KB', 'MB', 'GB', 'TB']
  let current = bytes / 1024
  let unit = units[0]

  for (let i = 1; i < units.length && current >= 1024; i++) {
    current /= 1024
    unit = units[i]
  }

  const digits = current >= 100 ? 0 : current >= 10 ? 1 : 2
  return `${current.toFixed(digits)} ${unit}`
}

export function humanDuration(ms) {
  let seconds = Math.max(0, Math.floor((Number(ms) || 0) / 1000))
  const days = Math.floor(seconds / 86400)
  seconds %= 86400
  const hours = Math.floor(seconds / 3600)
  seconds %= 3600
  const minutes = Math.floor(seconds / 60)
  seconds %= 60

  return [
    days ? `${days}d` : '',
    hours ? `${hours}h` : '',
    minutes ? `${minutes}m` : '',
    seconds || (!days && !hours && !minutes) ? `${seconds}s` : '',
  ].filter(Boolean).join(' ')
}
