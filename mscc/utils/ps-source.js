import path from 'node:path'
import AdmZip from 'adm-zip'
import * as XLSX from 'xlsx'

const ZIP_IMAGE_EXTS = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp'])
const MAX_ZIP_ENTRIES = 600
const MAX_ZIP_IMAGE_BYTES = 12 * 1024 * 1024
const MAX_ZIP_TOTAL_BYTES = 96 * 1024 * 1024

function normalizeUrl(value) {
  let raw = String(value || '').trim()
  raw = raw.replace(/[),.;\]}>'"]+$/g, '')
  if (!/^https?:\/\//i.test(raw)) return ''
  try {
    const url = new URL(raw)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}

export function extractUrlsFromText(text) {
  const matches = String(text || '').match(/https?:\/\/[^\s<>"']+/gi) || []
  const seen = new Set()
  const urls = []
  for (const match of matches) {
    const url = normalizeUrl(match)
    if (!url || seen.has(url)) continue
    seen.add(url)
    urls.push(url)
  }
  return urls
}

function stringsFromJson(value, out = []) {
  if (typeof value === 'string') {
    out.push(value)
    return out
  }
  if (Array.isArray(value)) {
    for (const item of value) stringsFromJson(item, out)
    return out
  }
  if (value && typeof value === 'object') {
    for (const item of Object.values(value)) stringsFromJson(item, out)
  }
  return out
}

function unique(values) {
  return [...new Set(values.filter(Boolean))]
}

function parseJson(buffer) {
  try {
    const parsed = JSON.parse(buffer.toString('utf8'))
    return unique(stringsFromJson(parsed).flatMap(extractUrlsFromText))
  } catch {
    return extractUrlsFromText(buffer.toString('utf8'))
  }
}

function parseWorkbook(buffer) {
  const workbook = XLSX.read(buffer, { type:'buffer' })
  const urls = []
  for (const name of workbook.SheetNames) {
    const sheet = workbook.Sheets[name]
    const rows = XLSX.utils.sheet_to_json(sheet, { header:1, raw:false })
    for (const cell of rows.flat()) {
      if (typeof cell === 'string') urls.push(...extractUrlsFromText(cell))
    }
  }
  return unique(urls)
}

function parseZip(buffer) {
  const zip = new AdmZip(buffer)
  const entries = zip.getEntries()
  if (entries.length > MAX_ZIP_ENTRIES) throw new Error('ZIP contains too many files.')

  const images = []
  let total = 0

  for (const entry of entries) {
    if (entry.isDirectory) continue
    const name = String(entry.entryName || '')
    if (name.includes('__MACOSX') || path.basename(name).startsWith('._')) continue
    const ext = path.extname(name).toLowerCase()
    if (!ZIP_IMAGE_EXTS.has(ext)) continue

    const size = Number(entry.header?.size || 0)
    if (size > MAX_ZIP_IMAGE_BYTES) continue
    total += size
    if (total > MAX_ZIP_TOTAL_BYTES) throw new Error('ZIP expands beyond the safe image limit.')

    const data = entry.getData()
    if (!Buffer.isBuffer(data) || !data.length || data.length > MAX_ZIP_IMAGE_BYTES) continue
    images.push({
      buffer:data,
      name:path.basename(name),
      animated:ext === '.gif',
    })
  }

  return images
}

export function psPackNameFromFile(fileName) {
  const base = path.basename(String(fileName || 'urls.txt'), path.extname(String(fileName || 'urls.txt'))).trim()
  return (base || 'Stickers').slice(0, 128)
}

export function parsePsRangeArgs(args = [], itemCount = 0) {
  const parts = args.map(value => String(value || '').trim()).filter(Boolean)
  let start = 0
  let end = Math.max(0, Number(itemCount) - 1)
  let customPackName = ''

  if (parts.length) {
    const match = parts[parts.length - 1].match(/^(\d+)-(\d+)$/)
    if (match) {
      start = Math.max(0, Number(match[1]) - 1)
      end = Math.max(start, Number(match[2]) - 1)
      parts.pop()
    }
    customPackName = parts.join(' ').trim()
  }

  if (itemCount > 0) end = Math.min(end, itemCount - 1)

  return { start, end, customPackName }
}

export function parsePsFile(buffer, fileName = 'urls.txt') {
  const ext = path.extname(String(fileName || '')).toLowerCase()

  if (ext === '.zip') {
    return { urls:[], images:parseZip(buffer) }
  }

  if (ext === '.xlsx' || ext === '.xls') {
    return { urls:parseWorkbook(buffer), images:[] }
  }

  if (ext === '.json') {
    return { urls:parseJson(buffer), images:[] }
  }

  return { urls:extractUrlsFromText(buffer.toString('utf8')), images:[] }
}
