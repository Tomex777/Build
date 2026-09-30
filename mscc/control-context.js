export function isPrivateOwnerDm({
  account,
  mainAccountId,
  chat,
  group = false,
  senderNumber,
  peerNumber,
  controlNumbers,
}) {
  if (!account?.id || account.role !== 'owner') return false
  if (!mainAccountId || account.id !== mainAccountId) return false
  if (!chat || group) return false

  const controls = controlNumbers instanceof Set
    ? controlNumbers
    : new Set(controlNumbers || [])

  return controls.has(String(senderNumber || '')) &&
    controls.has(String(peerNumber || ''))
}
