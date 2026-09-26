export function selectCcDestination({
  sourceId,
  defaultDestination,
  overrides = {},
  accounts,
}) {
  const enabled = id => Boolean(id && accounts?.get?.(id)?.enabled)
  const override = sourceId ? overrides[sourceId] : ''
  if (enabled(override)) return override
  if (enabled(defaultDestination)) return defaultDestination
  for (const [id, account] of accounts || []) {
    if (account?.enabled) return id
  }
  return ''
}
