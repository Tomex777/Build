import mit from './sources/courses/mit-ocw.js'
import wikiversity from './sources/courses/wikiversity.js'

const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 MSCC-course-probe'

async function fetchLive(url, { headers = {}, timeoutMs = 30000 } = {}) {
  const response = await fetch(url, {
    headers:{ 'user-agent':UA, accept:'*/*', ...headers },
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status} for ${new URL(url).hostname}`)
    error.bodyPreview = text.slice(0, 240)
    throw error
  }
  return { response, text }
}

async function probeZip(url, label) {
  const response = await fetch(url, {
    headers:{ range:'bytes=0-4095', 'user-agent':'MSCC course live probe' },
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  try {
    if (!response.ok && response.status !== 206) throw new Error(`${label} HTTP ${response.status}`)
    const bytes = new Uint8Array(await response.arrayBuffer())
    if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
      throw new Error(`${label} did not return ZIP bytes`)
    }
    return bytes.length
  } finally {
    await response.body?.cancel?.().catch?.(() => {})
  }
}

function decode(value = '') {
  return String(value)
    .replace(/&amp;/gi, '&')
    .replace(/&#38;/gi, '&')
    .replace(/&#x2f;/gi, '/')
    .replace(/\\u003d/gi, '=')
    .replace(/\\u0026/gi, '&')
    .replace(/\\u002f/gi, '/')
}

function titleText(value = '') {
  return decode(String(value).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim())
}

function parseDownloadlySearch(html = '', base = 'https://thedownloadly.com/') {
  const out = []
  const seen = new Set()
  for (const match of String(html).matchAll(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    let url = ''
    try { url = new URL(decode(match[1]), base).href } catch { continue }
    const parsed = new URL(url)
    if (parsed.hostname !== 'thedownloadly.com') continue
    if (/\/(?:category|tag|author|page)\//i.test(parsed.pathname)) continue
    if (parsed.pathname === '/' || !/^\/[a-z0-9][a-z0-9-]+\/?$/i.test(parsed.pathname)) continue
    const title = titleText(match[2])
    if (!title || title.length < 8 || /^(read more|downloadly)$/i.test(title)) continue
    if (seen.has(url)) continue
    seen.add(url)
    out.push({ title, url })
  }
  return out
}

function googleDriveFolders(html = '') {
  const out = []
  const seen = new Set()
  for (const match of String(html).matchAll(/https:\/\/drive\.google\.com\/drive\/folders\/([a-zA-Z0-9_-]{10,})[^"'<>\s]*/g)) {
    const id = match[1]
    if (seen.has(id)) continue
    seen.add(id)
    out.push({ id, url:decode(match[0]) })
  }
  return out
}

function driveFileIds(html = '') {
  const source = decode(String(html))
  const ids = new Set()
  const patterns = [
    /\/file\/d\/([a-zA-Z0-9_-]{10,})/g,
    /["']([a-zA-Z0-9_-]{20,})["'][^\n]{0,180}application\//g,
    /application\/[^"'\\]{2,80}[^\n]{0,180}["']([a-zA-Z0-9_-]{20,})["']/g,
  ]
  for (const re of patterns) {
    for (const match of source.matchAll(re)) ids.add(match[1])
  }
  return [...ids]
}

const mitSearch = await mit.run({ action:'search', query:'python', context:{} })
const mitCourse = (mitSearch.items || []).find(item => /python/i.test(item.title)) || mitSearch.items?.[0]
if (!mitCourse) throw new Error('MIT OCW live search returned no course')
let mitSent = null
const mitDelivery = await mit.run({
  action:'downloadCourse',
  item:mitCourse,
  context:{ send:async payload => { mitSent=payload } },
})
const mitUrl = mitSent?.document?.url
if (!mitDelivery?.delivered || !/^https?:\/\//.test(String(mitUrl || ''))) {
  throw new Error('MIT OCW did not resolve a course package URL')
}
const mitBytes = await probeZip(mitUrl, 'MIT OCW')
console.log('PASS MIT OCW search -> course ZIP bytes:', mitCourse.title, mitBytes)

const wikiSearch = await wikiversity.run({ action:'search', query:'Python programming', context:{} })
const wikiCourse = (wikiSearch.items || []).find(item => /python/i.test(item.title)) || wikiSearch.items?.[0]
if (!wikiCourse) throw new Error('Wikiversity live search returned no learning page')
const wikiContents = await wikiversity.run({ action:'contents', item:wikiCourse, context:{} })
const wikiPart = wikiContents.contents?.[0]
if (!wikiPart) throw new Error('Wikiversity returned no selectable section')
let wikiSent = null
const wikiDelivery = await wikiversity.run({
  action:'download',
  item:wikiCourse,
  content:wikiPart,
  context:{ send:async payload => { wikiSent=payload } },
})
const wikiData = wikiSent?.document
if (!wikiDelivery?.delivered || !Buffer.isBuffer(wikiData) || wikiData.length < 200) {
  throw new Error('Wikiversity did not produce real section document bytes')
}
const wikiText = wikiData.toString('utf8')
if (!wikiText.includes('Source: https://en.wikiversity.org/wiki/') || !wikiText.includes('CC BY-SA')) {
  throw new Error('Wikiversity document lost source attribution')
}
console.log('PASS Wikiversity search -> sections -> document bytes:', wikiCourse.title, wikiPart.title, wikiData.length)

// Candidate qualification: Downloadly must survive the same GitHub-hosted network path
// before it is installed as a production course source.
const dlSearchUrl = 'https://thedownloadly.com/?s=python'
const dlSearchPage = await fetchLive(dlSearchUrl, { headers:{ accept:'text/html,application/xhtml+xml' } })
const dlItems = parseDownloadlySearch(dlSearchPage.text, dlSearchPage.response.url || dlSearchUrl)
if (!dlItems.length) throw new Error('Downloadly live search returned no course pages')
const dlCourse = dlItems.find(item => /python/i.test(item.title)) || dlItems[0]
const dlDetail = await fetchLive(dlCourse.url, { headers:{ accept:'text/html,application/xhtml+xml' } })
const dlFolders = googleDriveFolders(dlDetail.text)
if (!dlFolders.length) throw new Error(`Downloadly course resolved no Google Drive folder: ${dlCourse.url}`)
const folder = dlFolders[0]
const embeddedUrl = `https://drive.google.com/embeddedfolderview?id=${encodeURIComponent(folder.id)}#list`
const embedded = await fetchLive(embeddedUrl, { headers:{ accept:'text/html,application/xhtml+xml' } })
const fileIds = driveFileIds(embedded.text)
console.log('PROBE Downloadly search -> course -> Drive folder:', dlCourse.title, folder.id, 'folderHtmlBytes=', Buffer.byteLength(embedded.text), 'fileIds=', fileIds.length)
if (!fileIds.length) {
  console.log('PROBE Downloadly Drive payload markers:', embedded.text.slice(0, 1200).replace(/\s+/g, ' '))
  throw new Error('Downloadly Drive folder did not expose resolvable file IDs in the public payload')
}

console.log('PASS Downloadly candidate search -> detail -> public Drive file IDs:', dlCourse.title, fileIds.slice(0, 3).join(','))
console.log('PASS course live source qualification')
