const NUMBER = /^\d+(?:\.\d+)?$/
const RANGE = /^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$/

function numeric(value) {
  const n = Number(String(value || '').trim())
  return Number.isFinite(n) ? n : null
}

export function looksLikeNumberSelection(value) {
  const text = String(value || '').trim()
  if (!text || text.length > 240) return false
  return /^\d+(?:\.\d+)?(?:\s*-\s*\d+(?:\.\d+)?)?(?:\s*,\s*\d+(?:\.\d+)?(?:\s*-\s*\d+(?:\.\d+)?)?)*$/.test(text)
}

export function parseNumberSelection(value, entries = [], {
  numberOf = entry => entry?.number,
  maxSelected = 250,
} = {}) {
  const text = String(value || '').trim()
  if (!looksLikeNumberSelection(text)) {
    return { ok:false, error:'invalid', selected:[], requested:[] }
  }

  const normalized = entries
    .map((entry, index) => {
      const raw = String(numberOf(entry, index) ?? '').trim()
      const n = numeric(raw)
      return n === null ? null : { entry, raw, n, index }
    })
    .filter(Boolean)

  const selected = []
  const seen = new Set()
  const requested = []

  const add = row => {
    const key = row.index + '|' + row.raw
    if (seen.has(key)) return
    seen.add(key)
    selected.push(row.entry)
  }

  for (const rawPart of text.split(',')) {
    const part = rawPart.trim()
    if (NUMBER.test(part)) {
      const n = numeric(part)
      requested.push({ type:'number', start:n, end:n })
      const exact = normalized.find(row => row.n === n)
      if (exact) add(exact)
      continue
    }

    const match = part.match(RANGE)
    if (!match) return { ok:false, error:'invalid', selected:[], requested:[] }

    let start = numeric(match[1])
    let end = numeric(match[2])
    if (start === null || end === null) return { ok:false, error:'invalid', selected:[], requested:[] }
    if (start > end) [start, end] = [end, start]
    requested.push({ type:'range', start, end })

    for (const row of normalized) {
      if (row.n >= start && row.n <= end) add(row)
      if (selected.length > maxSelected) {
        return { ok:false, error:'too-many', selected:[], requested }
      }
    }
  }

  if (!selected.length) return { ok:false, error:'not-found', selected:[], requested }

  selected.sort((a, b) => {
    const an = numeric(numberOf(a))
    const bn = numeric(numberOf(b))
    return (an ?? 0) - (bn ?? 0)
  })

  return { ok:true, selected, requested }
}

export function selectionLabel(entries = [], {
  numberOf = entry => entry?.number,
  unit = 'episode',
} = {}) {
  const numbers = entries.map(entry => String(numberOf(entry) ?? '').trim()).filter(Boolean)
  if (!numbers.length) return ''
  if (numbers.length === 1) return `${unit} ${numbers[0]}`
  return `${unit}s ${numbers.join(', ')}`
}
