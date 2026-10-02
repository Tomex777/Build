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
  if (!response.ok || bytes < 512 || html) {
    throw new Error(`${label} did not return real document/media bytes (status=${response.status}, type=${type}, bytes=${bytes})`)
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
const dlPart = dlParts.find(item => /\.txt$/i.test(item.title)) || dlParts[0]
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

console.log('PASS course live source qualification')
