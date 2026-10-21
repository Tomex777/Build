import { createHash } from 'node:crypto'

const normalizeCapability = value => String(value || 'general').trim().toLowerCase() || 'general'

function deterministicPick(groupJid, capability, accountIds) {
  const ids = [...accountIds].sort()
  if (!ids.length) return ''
  const digest = createHash('sha256').update(`${groupJid}\0${capability}\0${ids.join(',')}`).digest()
  const number = digest.readUInt32BE(0)
  return ids[number % ids.length]
}

export async function chooseGroupExecutor({
  groupJid,
  capability = 'general',
  accounts,
  isMember,
  scoreFor,
  getSticky,
  setSticky,
}) {
  const cap = normalizeCapability(capability)
  const candidates = []

  for (const account of [...accounts].sort((a, b) => String(a.id).localeCompare(String(b.id)))) {
    if (!account?.enabled || !account?.connected || !account?.sock) continue
    // Most linked bots aren't eligible for this capability. Avoid requesting
    // their WhatsApp group metadata (remote network I/O) just to discard them.
    const score = scoreFor(account.id, cap)
    // Invalid/disabled capabilities must not poison the best-score choice.
    const numericScore = Number(score)
    if (score == null || score === '' || typeof score === 'boolean' || !Number.isFinite(numericScore)) continue
    // A single account with a broken group-metadata call must not block others.
    try {
      if (!(await isMember(account, groupJid))) continue
    } catch {
      continue
    }
    candidates.push({ id: account.id, score: numericScore })
  }

  if (!candidates.length) return ''

  const bestScore = Math.max(...candidates.map(item => item.score))
  const best = candidates.filter(item => item.score === bestScore).map(item => item.id).sort()
  const sticky = getSticky(groupJid, cap)
  const selected = best.includes(sticky)
    ? sticky
    : deterministicPick(groupJid, cap, best)

  if (selected && selected !== sticky) setSticky(groupJid, cap, selected)
  return selected
}

export function canExecuteDirect({ accountId, capability = 'general', scoreFor }) {
  const score = scoreFor(accountId, normalizeCapability(capability))
  return score != null && score !== '' && typeof score !== 'boolean' && Number.isFinite(Number(score))
}
