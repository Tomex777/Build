import { replaceBrandAliases } from './branding.js'

const RESERVED = /[<>:"/\\|?*\x00-\x1F]/g
const TRAILING = /[. ]+$/
const MULTISPACE = /\s+/g

export function splitExtension(value) {
  const name = String(value || '')
  const index = name.lastIndexOf('.')

  if (index <= 0 || index === name.length - 1) return { stem: name, extension: '' }

  return {
    stem: name.slice(0, index),
    extension: name.slice(index + 1),
  }
}

export function sanitizeFilename(value, {
  fallback = 'file',
  maxLength = 120,
} = {}) {
  const safeMax = Math.max(16, Number(maxLength) || 120)
  const { stem, extension } = splitExtension(String(value || fallback))

  let cleanStem = stem
    .replace(RESERVED, ' ')
    .replace(MULTISPACE, ' ')
    .replace(TRAILING, '')
    .trim()

  if (!cleanStem) cleanStem = String(fallback || 'file').replace(RESERVED, '').trim() || 'file'

  const cleanExtension = extension
    .replace(/[^a-zA-Z0-9]/g, '')
    .slice(0, 12)

  const suffix = cleanExtension ? `.${cleanExtension}` : ''
  const allowedStem = Math.max(1, safeMax - suffix.length)

  return cleanStem.slice(0, allowedStem).replace(TRAILING, '') + suffix
}

export function brandedFilename(value, {
  botName,
  sourceName = '',
  aliases = [],
  fallback = 'file',
  maxLength = 120,
} = {}) {
  const branded = replaceBrandAliases(value, botName, [sourceName, ...(aliases || [])])
  return sanitizeFilename(branded, { fallback, maxLength })
}
