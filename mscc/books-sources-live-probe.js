import { fetchJson, fetchText, textFromHtml } from './sources/books/_shared.js'
import standardEbooks from './sources/books/standard-ebooks.js'

async function firstBytes(url, headers = {}) {
  const response = await fetch(url, {
    headers:{ 'user-agent':'MSCC/2.3 books live probe', range:'bytes=0-4095', ...headers },
    redirect:'follow',
    signal:AbortSignal.timeout(25000),
  })
  if (!response.ok && response.status !== 206) throw new Error('Media HTTP ' + response.status + ' for ' + new URL(url).hostname)
  const bytes = new Uint8Array(await response.arrayBuffer())
  if (!bytes.length) throw new Error('No bytes from ' + new URL(url).hostname)
  return { bytes, response }
}

function zipMagic(bytes) {
  return bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b
}

async function probeGutenberg() {
  const { text } = await fetchText(
    'https://www.gutenberg.org/ebooks/search/?' + new URLSearchParams({ query:'Alice in Wonderland', submit_search:'Go!' }),
    { headers:{ accept:'text/html,application/xhtml+xml' }, timeoutMs:25000 },
  )
  if (!/Alice(?:'|&#39;|’)?s Adventures in Wonderland/i.test(text)) {
    throw new Error('Project Gutenberg search page did not return Alice in Wonderland')
  }
  const url = 'https://www.gutenberg.org/ebooks/11.epub3.images'
  const { bytes } = await firstBytes(url)
  if (!zipMagic(bytes)) throw new Error('Project Gutenberg EPUB did not have ZIP magic')
  console.log('PASS Gutenberg search + EPUB bytes:', bytes.length)
}

async function probeStandardEbooks() {
  const pageUrl = 'https://standardebooks.org/ebooks/charlotte-bronte/jane-eyre'
  const { text } = await fetchText(pageUrl, {
    headers:{ accept:'text/html,application/xhtml+xml' },
    timeoutMs:25000,
  })
  const editions = standardEbooks._test.parseEditions(text)
  const epub = editions.find(row => row.format === 'EPUB')
  if (!epub?.url) throw new Error('Standard Ebooks details page returned no EPUB link')
  const { bytes, response } = await firstBytes(epub.url)
  if (!zipMagic(bytes)) {
    console.log('Standard Ebooks diagnostic URL:', epub.url)
    console.log('Standard Ebooks final URL:', response.url)
    console.log('Standard Ebooks content type:', response.headers.get('content-type') || '')
    console.log('Standard Ebooks first bytes:', Buffer.from(bytes.slice(0, 48)).toString('hex'))
    console.log('Standard Ebooks first text:', Buffer.from(bytes.slice(0, 160)).toString('utf8').replace(/\s+/g, ' '))
    throw new Error('Standard Ebooks EPUB did not have ZIP magic')
  }
  console.log('PASS Standard Ebooks details + EPUB bytes:', bytes.length)
}

async function probeOpenLibrary() {
  const { data } = await fetchJson('https://openlibrary.org/search.json?' + new URLSearchParams({
    q:'Alice in Wonderland',
    fields:'key,title,ia,public_scan_b,ebook_access',
    limit:'12',
  }), { timeoutMs:25000 })
  const docs = Array.isArray(data?.docs) ? data.docs : []
  for (const doc of docs) {
    if (!(doc?.public_scan_b === true || String(doc?.ebook_access || '').toLowerCase() === 'public')) continue
    for (const identifier of (Array.isArray(doc?.ia) ? doc.ia : []).slice(0, 5)) {
      try {
        const { data:meta } = await fetchJson('https://archive.org/metadata/' + encodeURIComponent(identifier), { timeoutMs:25000 })
        if (String(meta?.metadata?.['access-restricted-item'] || '').toLowerCase() === 'true') continue
        const file = (Array.isArray(meta?.files) ? meta.files : []).find(row =>
          row?.private !== true && /\.epub$/i.test(String(row?.name || ''))
        )
        if (!file) continue
        const url = 'https://archive.org/download/' + encodeURIComponent(identifier) + '/' + String(file.name).split('/').map(encodeURIComponent).join('/')
        const { bytes } = await firstBytes(url)
        if (!zipMagic(bytes)) continue
        console.log('PASS Open Library/IA EPUB bytes:', doc.title, identifier, bytes.length)
        return
      } catch {}
    }
  }
  throw new Error('Open Library probe found no public EPUB bytes')
}

async function probeNovelBuddy() {
  const { data } = await fetchJson('https://api.novelbuddy.me/titles/search?' + new URLSearchParams({
    q:'Lord of Mysteries',
    limit:'5',
    page:'1',
  }), {
    headers:{ origin:'https://novelbuddy.me', referer:'https://novelbuddy.me/' },
    timeoutMs:25000,
  })
  const item = data?.data?.items?.find(row => row?.id)
  if (!item) throw new Error('NovelBuddy search returned no novel')
  const { data:chapterData } = await fetchJson(
    'https://api.novelbuddy.me/titles/' + encodeURIComponent(item.id) + '/chapters',
    {
      headers:{ origin:'https://novelbuddy.me', referer:'https://novelbuddy.me/' },
      timeoutMs:25000,
    },
  )
  const chapters = Array.isArray(chapterData?.data?.chapters) ? chapterData.data.chapters : []
  const chapter = chapters.find(row => row?.id)
  if (!chapter) throw new Error('NovelBuddy returned no chapters')
  const { data:contentData } = await fetchJson(
    'https://api.novelbuddy.me/titles/' + encodeURIComponent(item.id) + '/chapters/' + encodeURIComponent(chapter.id),
    {
      headers:{ origin:'https://novelbuddy.me', referer:'https://novelbuddy.me/' },
      timeoutMs:25000,
    },
  )
  const text = textFromHtml(contentData?.data?.chapter?.content || '')
  if (text.length < 100) throw new Error('NovelBuddy chapter content was too short')
  console.log('PASS NovelBuddy chapter content:', item.name, chapter.name, text.length, 'chars')
}

await probeGutenberg()
await probeStandardEbooks()
await probeOpenLibrary()
await probeNovelBuddy()
console.log('PASS books/novels live source qualification')
