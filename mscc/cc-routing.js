export function selectCcDestination({ accounts }) {
  if (!accounts?.size) return ''

  const accountA = accounts.get('A')
  if (accountA?.enabled && accountA?.role === 'owner') return 'A'

  for (const [id, account] of accounts) {
    if (account?.enabled && account?.role === 'owner') return id
  }

  return ''
}
