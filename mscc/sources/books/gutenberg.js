import { clean, fetchJson, safeFileName, sendDocument, sizeText } from './_shared.js'

const API = 'https://gutendex.com/books'

const FORMAT_MAP = [
  ['application/epub+zip', 'EPUB', 'application/epub+zip', 'epub'],
  ['text/plain; charset=utf-8', 'TXT', 'text/plain', 'txt'],
  ['text/plain', 'TXT', 'text/plain', 'txt'],
  ['application/pdf', 'PDF', 'application/pdf', 'pdf'],
]

function authorNames(book = {}) {
  return (Array.isArray(book.authors) ? book.authors : [])
    .map(author => clean(author?.name || '', 120))
    .filter(Boolean)
    .join(', ')
}

function editionRows(book = {}) {
  const formats = book?.formats || {}
  const rows = []
  const seen = new Set()
  for (const [key, title, mimetype, extension] of FORMAT_MAP) {
    const url = String(formats[key] || '').trim()
    if (!/^https?:\/\//i.test(url) || seen.has(url)) continue
    seen.add(url)
    rows.push({
      id:key,
      title,
      format:title,
      language:Array.isArray(book.languages) ? book.languages.join(', ').toUpperCase() : '',
      url,
      mimetype,
      extension,
    })
  }
  return rows
}

async function search(query) {
  const { data } = await fetchJson(API + '?' + new URLSearchParams({ search:query }))
  const rows = Array.isArray(data?.results) ? data.results : []
  const items = rows.slice(0, 25).map(book => ({
    id:String(book.id),
    title:clean(book.title || '', 180),
    author:authorNames(book),
    year:'',
    languages:Array.isArray(book.languages) ? book.languages : [],
    formats:book.formats || {},
    downloadCount:Number(book.download_count || 0) || 0,
  })).filter(item => item.title)
  if (!items.length) throw new Error('Project Gutenberg returned no books.')
  return items
}

async function hydrate(item) {
  const id = String(item?.id || '').trim()
  if (!/^\d+$/.test(id)) return item || {}
  try {
    const { data } = await fetchJson(API + '/' + id)
    return data || item || {}
  } catch {
    return item || {}
  }
}

export default {
  id:'gutenberg',
  name:'Project Gutenberg',
  description:'Public-domain ebooks with EPUB and plain-text downloads.',

  async run({ action, query, item, edition, context }) {
    if (action === 'search') return { items:await search(clean(query, 180)) }

    if (action === 'editions') {
      const book = await hydrate(item)
      const editions = editionRows(book)
      if (!editions.length) throw new Error('Project Gutenberg has no supported downloadable format for this book.')
      return { editions }
    }

    if (action === 'download') {
      const chosen = edition || {}
      const url = String(chosen.url || '').trim()
      if (!url) throw new Error('Project Gutenberg edition URL is missing.')
      const title = clean(item?.title || 'Project Gutenberg book', 160)
      const artist = clean(item?.author || authorNames(item), 100)
      const fileName = safeFileName([title, artist].filter(Boolean).join(' - '), chosen.extension || 'epub')
      return sendDocument(context, {
        url,
        mimetype:chosen.mimetype || 'application/octet-stream',
        fileName,
      })
    }

    throw new Error('Unsupported Project Gutenberg action: ' + action)
  },

  _test:{ editionRows, authorNames },
}
