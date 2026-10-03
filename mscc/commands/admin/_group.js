export function toggleValue(raw) {
  const value = String(raw || '').trim().toLowerCase()
  if (['on','enable','enabled','yes','1'].includes(value)) return true
  if (['off','disable','disabled','no','0'].includes(value)) return false
  return null
}

export function targetAndReason(args = []) {
  const parts = args.map(value => String(value || '').trim()).filter(Boolean)
  const first = parts[0] || ''
  const explicitTarget = first.startsWith('@') || /^[+\d(). -]{7,}$/.test(first)
  return {
    target:explicitTarget ? first : '',
    reason:(explicitTarget ? parts.slice(1) : parts).join(' ').trim(),
  }
}

export function targetArg(args = []) {
  const first = String(args?.[0] || '').trim()
  return first.startsWith('@') || /^[+\d(). -]{7,}$/.test(first) ? first : ''
}
