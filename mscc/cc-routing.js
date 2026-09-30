export function selectCcDestination({ accounts }) {
  const accountA = accounts?.get?.('A')
  return accountA?.enabled && accountA?.role === 'owner' ? 'A' : ''
}
