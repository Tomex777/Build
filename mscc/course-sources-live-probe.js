import mit from './sources/courses/mit-ocw.js'
import wikiversity from './sources/courses/wikiversity.js'
import downloadly from './sources/courses/downloadly.js'
import freecoursesite from './sources/courses/freecoursesite.js'

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

function chooseMedia(parts = []) {
  return parts.find(item => /\.(?:mp4|mkv|webm|zip|rar|7z|pdf)$/i.test(item.title))
    || parts.find(item => !/\.(?:txt|vtt|srt)$/i.test(item.title))
    || parts[0]
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
const dlPart = chooseMedia(dlParts)
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
)

const fcsSearch = await freecoursesite.run({ action:'search', query:'python', context:{} })
const fcsCourse = (fcsSearch.items || []).find(item => /python/i.test(item.title)) || fcsSearch.items?.[0]
if (!fcsCourse) throw new Error('FreeCourseSite live search returned no course')
const fcsContents = await freecoursesite.run({ action:'contents', item:fcsCourse, context:{} })
const fcsParts = fcsContents.contents || []
if (!fcsParts.length) throw new Error('FreeCourseSite returned no selectable course files')
const fcsPart = chooseMedia(fcsParts)
let fcsSent = null
const fcsDelivery = await freecoursesite.run({
  action:'download',
  item:fcsCourse,
  content:fcsPart,
  context:{ send:async payload => { fcsSent=payload } },
})
const fcsUrl = fcsSent?.document?.url
if (!fcsDelivery?.delivered || !/^https?:\/\//.test(String(fcsUrl || '')) || !fcsSent?.fileName) {
  throw new Error('FreeCourseSite source did not resolve a deliverable course file')
}
const fcsBytes = await probeDocumentBytes(fcsUrl, 'FreeCourseSite')
console.log(
  'PASS FreeCourseSite search -> recursive course files -> selected file -> actual bytes:',
  fcsCourse.title,
  'parts=', fcsParts.length,
  'file=', fcsSent.fileName,
  'section=', fcsPart.section || '(root)',
  'status=', fcsBytes.status,
  'type=', fcsBytes.type,
  'bytesRead=', fcsBytes.bytes,
)

async function probeAfraTafreeh() {
  try {
    const pageUrl = 'https://afratafreeh.com/audiolearn-endocrinology/'
    const response = await fetch(pageUrl, {
      headers:{ 'user-agent':UA, accept:'text/html,application/xhtml+xml' },
      redirect:'follow',
      signal:AbortSignal.timeout(30000),
    })
    const html = await response.text()
    const links = []
    for (const match of html.matchAll(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
      const title = match[2].replace(/<[^>]+>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim()
      if (!/download now/i.test(title)) continue
      links.push(match[1].replace(/&amp;/gi, '&'))
    }
    console.log(
      'CANDIDATE AfraTafreeh',
      'status=', response.status,
      'bytes=', Buffer.byteLength(html),
      'downloadLinks=', links.join(' | ') || '(none)',
    )
    for (const href of links.slice(0, 2)) {
      if (!/^https?:\/\//i.test(href)) continue
      const target = await fetch(href, {
        headers:{ 'user-agent':UA, accept:'*/*', range:'bytes=0-4095' },
        redirect:'follow',
        signal:AbortSignal.timeout(30000),
      })
      const type = String(target.headers.get('content-type') || '')
      const text = await target.text()
      console.log(
        'CANDIDATE Afra target',
        'status=', target.status,
        'final=', target.url,
        'type=', type,
        'bytes=', Buffer.byteLength(text),
        'html=', /text\/html/i.test(type) || /^\s*<!doctype html|^\s*<html/i.test(text),
      )
    }
  } catch (error) {
    console.log('CANDIDATE AfraTafreeh error=', error?.message || String(error))
  }
}
await probeAfraTafreeh()

console.log('PASS course live source qualification')
