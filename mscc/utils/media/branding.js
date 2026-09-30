const escapeRegExp = value => String(value).replace(/[.*+?^$()|[\]\\{}]/g, '\\$&')

export function replaceBrandAliases(value, brand, aliases = []) {
  let output = String(value || '').trim()
  const replacement = String(brand || '').trim()
  if (!output || !replacement) return output

  const names = aliases
    .map(name => String(name || '').trim())
    .filter(Boolean)
    .sort((left, right) => right.length - left.length)

  for (const name of names) {
    output = output.replace(new RegExp(escapeRegExp(name), 'gi'), replacement)
  }

  return output
}

export function brandedTitle(title, {
  botName,
  sourceName = '',
  aliases = [],
} = {}) {
  return replaceBrandAliases(title, botName, [sourceName, ...(aliases || [])])
}
