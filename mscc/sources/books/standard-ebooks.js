import { clean, decodeHtml, fetchText, safeFileName, sendDocument, textFromHtml } from './_shared.js'

const ORIGIN = 'https://standardebooks.org'

function absolute(path) {
  try { return new URL(String(path || ''), ORIGIN).href } catch { return '' }
}

function authorFromPath(pathname = '') {
  const parts = String(pathname).split('/').filter(Boolean)
  const slug = parts[1] || ''
  return slug
    .split(/[-_]/)
    .filter(Boolean)
    .map(word => word.length <= 2 ? word.toUpperCase() : word[0]?.toUpperCase() + word.slice(1))
    .join(' ')
}

function parseSearch(html) {
  const out = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of String(html || '').matchAll(re)) {
    const href = String(match[1] || '')
    let url
    try { url = new URL(href, ORIGIN) } catch { continue }
    if (url.origin !== ORIGIN) continue
    const parts = url.pathname.split('/').filter(Boolean)
    if (parts[0] !== 'ebooks' || parts.length < 3 || parts.length > 4) continue
    if (['downloads','text','feeds'].includes(parts.at(-1))) continue
    const title = textFromHtml(match[2])
    if (!title || title.length > 220 || seen.has(url.pathname)) continue
    if (/^(read|download|details|source|github|wikipedia)$/i.test(title)) continue
    seen.add(url.pathname)
    out.push({
      id:url.pathname,
      title:clean(title, 180),
      author:authorFromPath(url.pathname),
      url:url.href,
    })
    if (out.length >= 25) break
  }
  return out
}

function identifierFromPath(pathname = '') {
  const parts = String(pathname).split('/').filter(Boolean)
  if (parts[0] !== 'ebooks' || parts.length < 3) return ''
  return parts.slice(1).join('_')
}

function editionsFor(item = {}) {
  let pathname = String(item?.id || '')
  try { pathname = new URL(item?.url || pathname, ORIGIN).pathname } catch {}
  const identifier = identifierFromPath(pathname)
  if (!identifier) return []
  const base = ORIGIN + pathname.replace(/\/$/, '') + '/downloads/' + identifier
  return [
    { id:'epub', title:'EPUB', format:'EPUB', language:'English', url:base + '.epub', mimetype:'application/epub+zip', extension:'epub' },
    { id:'azw3', title:'AZW3', format:'AZW3', language:'English', url:base + '.azw3', mimetype:'application/vnd.amazon.ebook', extension:'azw3' },
    { id:'kepub', title:'KEPUB', format:'KEPUB', language:'English', url:base + '.kepub.epub', mimetype:'application/epub+zip', extension:'kepub.epub' },
    { id:'advanced-epub', title:'Advanced EPUB', format:'EPUB', language:'English', url:base + '.advanced.epub', mimetype:'application/epub+zip', extension:'advanced.epub' },
  ]
}

async function search(query) {
  const url = ORIGIN + '/ebooks?' + new URLSearchParams({
    query,
    sort:'relevance',
    view:'list',
    'per-page':'48',
  })
  const { text } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml', 'accept-language':'en-US,en;q=0.9' },
  })
  const items = parseSearch(text)
  if (!items.length) throw new Error('Standard Ebooks returned no books.')
  return items
}

export default {
  id:'standard-ebooks',
  name:'Standard Ebooks',
  description:'Polished public-domain editions in EPUB, AZW3 and KEPUB formats.',

  async run({ action, query, item, edition, context }) {
    if (action === 'search') return { items:await search(clean(query, 180)) }

    if (action === 'editions') {
      const editions = editionsFor(item)
      if (!editions.length) throw new Error('Standard Ebooks could not resolve download formats.')
      return { editions }
    }

    if (action === 'download') {
      const chosen = edition || {}
      const url = String(chosen.url || '').trim()
      if (!url) throw new Error('Standard Ebooks edition URL is missing.')
      const title = clean(item?.title || 'Standard Ebook', 160)
      const author = clean(item?.author || '', 100)
      return sendDocument(context, {
        url,
        mimetype:chosen.mimetype || 'application/epub+zip',
        fileName:safeFileName([title, author].filter(Boolean).join(' - '), chosen.extension || 'epub'),
      })
    }

    throw new Error('Unsupported Standard Ebooks action: ' + action)
  },

  _test:{ parseSearch, editionsFor, identifierFromPath },
}
