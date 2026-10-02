import mit from './sources/courses/mit-ocw.js'
import wikiversity from './sources/courses/wikiversity.js'
import downloadly from './sources/courses/downloadly.js'

const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 MSCC-course-probe'

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

async function probeDocumentBytes(url, label) {
  const response = await fetch(url, {
    headers:{
      'user-agent':UA,
      accept:'*/*',
      range:'bytes=0-8191',
    },
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  const type = String(response.headers.get('content-type') || '').toLowerCase()
  const disposition = String(response.headers.get('content-disposition') || '')
  const reader = response.body?.getReader?.()
  let bytes = 0
  let prefix = ''
  try {
    if (reader) {
      while (bytes < 4096) {
        const { done, value } = await reader.read()
        if (done) break
        if (value) {
          bytes += value.byteLength
          if (prefix.length < 256) prefix += Buffer.from(value).subarray(0, 256 - prefix.length).toString('utf8')
        }
      }
    }
  } finally {
    await reader?.cancel?.().catch?.(() => {})
  }
  const html = type.includes('text/html') || /^\s*<!doctype html|^\s*<html/i.test(prefix)
  const attached = /attachment/i.test(disposition)
  if (!response.ok || bytes < 16 || html || (!attached && bytes < 512)) {
    throw new Error(`${label} did not return real document/media bytes (status=${response.status}, type=${type}, bytes=${bytes}, disposition=${disposition.slice(0, 120)})`)
  }
  return { status:response.status, type, disposition, bytes, finalUrl:response.url }
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

const dlSearch = await downloadly.run({ action:'search', query:'python', context:{} })
const dlCourse = (dlSearch.items || []).find(item => /python/i.test(item.title)) || dlSearch.items?.[0]
if (!dlCourse) throw new Error('Downloadly live search returned no course')
const dlContents = await downloadly.run({ action:'contents', item:dlCourse, context:{} })
const dlParts = dlContents.contents || []
if (!dlParts.length) throw new Error('Downloadly returned no selectable course files')
console.log('PROBE Downloadly resolved parts:', dlParts.slice(0, 12).map(item => item.title).join(' | '))
const dlPart = dlParts.find(item => /\.(?:mp4|mkv|webm|zip|rar|7z|pdf)$/i.test(item.title))
  || dlParts.find(item => !/\.txt$/i.test(item.title))
  || dlParts[0]
let dlSent = null
const dlDelivery = await downloadly.run({
  action:'download',
  item:dlCourse,
  content:dlPart,
  context:{ send:async payload => { dlSent=payload } },
})
const dlUrl = dlSent?.document?.url
if (!dlDelivery?.delivered || !/^https?:\/\//.test(String(dlUrl || '')) || !dlSent?.fileName) {
  throw new Error('Downloadly source did not resolve a deliverable course file')
}
const dlBytes = await probeDocumentBytes(dlUrl, 'Downloadly')
console.log(
  'PASS Downloadly search -> course files -> selected file -> actual bytes:',
  dlCourse.title,
  'parts=', dlParts.length,
  'file=', dlSent.fileName,
  'status=', dlBytes.status,
  'type=', dlBytes.type,
  'bytesRead=', dlBytes.bytes,
  'disposition=', dlBytes.disposition.slice(0, 160),
)

async function probeCandidatePage(label, url) {
  try {
    const response = await fetch(url, {
      headers:{ 'user-agent':UA, accept:'text/html,application/xhtml+xml' },
      redirect:'follow',
      signal:AbortSignal.timeout(30000),
    })
    const html = await response.text()
    const blocked = response.status >= 400
      || /<title>\s*(?:just a moment|one moment, please)/i.test(html)
      || /please wait while your request is being verified/i.test(html)
    const actions = []
    for (const match of html.matchAll(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
      const text = match[2].replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
      if (!/(?:download link|get course(?: now)?|get tutorial)/i.test(text)) continue
      let href = match[1].replace(/&amp;/gi, '&')
      try { href = new URL(href, response.url || url).href } catch {}
      actions.push(`${text} => ${href}`)
    }
    console.log(
      'CANDIDATE',
      label,
      'status=', response.status,
      'final=', response.url,
      'htmlBytes=', Buffer.byteLength(html),
      'blocked=', blocked,
      'actions=', actions.slice(0, 5).join(' | ') || '(none)',
    )
  } catch (error) {
    console.log('CANDIDATE', label, 'request-error=', error?.message || String(error))
  }
}

await probeCandidatePage(
  'FreeEducationWeb',
  'https://freeeducationweb.com/restful-web-api-in-net-core-the-beginners-guide-net-10/',
)
await probeCandidatePage(
  'DevCourseWeb',
  'https://devcourseweb.com/tutorials/it-software/beginners-guide-to-python-programming-learn-code-succeed/',
)
await probeCandidatePage(
  'FreeCourseSite',
  'https://freecoursesites.com/python-mega-course-learn-python-in-60-days-build-20-apps/',
)
await probeCandidatePage(
  'CoursesBag',
  'https://www.coursesbag.com/search?q=python',
)

async function probePublicDriveCandidate(label, pageUrl) {
  const page = await fetch(pageUrl, {
    headers:{ 'user-agent':UA, accept:'text/html,application/xhtml+xml' },
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  const html = await page.text()
  if (!page.ok) throw new Error(`${label} page HTTP ${page.status}`)
  const folders = downloadly._test.parseDriveFolders(html)
  if (!folders.length) throw new Error(`${label} exposed no public Drive folder`)
  const folder = folders[0]
  const embedded = await fetch(downloadly._test.embeddedFolderUrl(folder), {
    headers:{ 'user-agent':UA, accept:'text/html,application/xhtml+xml' },
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  const folderHtml = await embedded.text()
  if (!embedded.ok) throw new Error(`${label} Drive folder HTTP ${embedded.status}`)
  const entries = downloadly._test.parseDriveEntries(folderHtml).filter(entry => entry.kind === 'file')
  if (!entries.length) throw new Error(`${label} Drive folder exposed no files`)
  const chosen = entries.find(entry => /\.(?:mp4|mkv|webm|zip|rar|7z|pdf)$/i.test(entry.title))
    || entries.find(entry => !/\.txt$/i.test(entry.title))
    || entries[0]
  const url = downloadly._test.driveDownloadUrl(chosen.id, chosen.resourceKey)
  const bytes = await probeDocumentBytes(url, label)
  console.log(
    'CANDIDATE-PASS',
    label,
    'folder=', folder.id,
    'files=', entries.length,
    'file=', chosen.title,
    'status=', bytes.status,
    'type=', bytes.type,
    'bytesRead=', bytes.bytes,
    'disposition=', bytes.disposition.slice(0, 160),
  )
}

await probePublicDriveCandidate(
  'FreeCourseSite',
  'https://freecoursesites.com/python-mega-course-learn-python-in-60-days-build-20-apps/',
)

console.log('PASS course live source qualification')
