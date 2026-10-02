import WebTorrent from 'webtorrent'
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

async function destroyTorrentClient(client) {
  if (!client) return
  await new Promise(resolve => {
    try { client.destroy(() => resolve()) }
    catch { resolve() }
  })
}

async function probeDevCourseWeb() {
  const client = new WebTorrent({ maxConns:48 })
  try {
    const courses = []
    const seen = new Set()
    const jsonEndpoints = [
      'https://devcourseweb.com/wp-json/wp/v2/search?search=python&per_page=20',
      'https://devcourseweb.com/wp-json/wp/v2/posts?search=python&per_page=20&_fields=link,title',
    ]
    for (const endpoint of jsonEndpoints) {
      try {
        const response = await fetch(endpoint, {
          headers:{ 'user-agent':UA, accept:'application/json' },
          redirect:'follow',
          signal:AbortSignal.timeout(30000),
        })
        const text = await response.text()
        console.log('CANDIDATE DevCourseWeb search endpoint', endpoint, 'status=', response.status, 'type=', response.headers.get('content-type'), 'bytes=', Buffer.byteLength(text))
        if (!response.ok || !/json/i.test(String(response.headers.get('content-type') || ''))) continue
        const rows = JSON.parse(text)
        for (const row of Array.isArray(rows) ? rows : []) {
          const url = String(row?.url || row?.link || '')
          if (!/\/tutorials\//i.test(url) || seen.has(url)) continue
          const rawTitle = typeof row?.title === 'string' ? row.title : row?.title?.rendered
          const title = String(rawTitle || '')
            .replace(/<[^>]+>/g, ' ')
            .replace(/&(?:nbsp|#160);/gi, ' ')
            .replace(/&amp;/gi, '&')
            .replace(/&#8217;|&rsquo;/gi, '’')
            .replace(/\s+/g, ' ')
            .trim()
          if (!title) continue
          seen.add(url)
          courses.push({ title, url })
        }
        if (courses.length) break
      } catch (error) {
        console.log('CANDIDATE DevCourseWeb search endpoint error=', error?.message || String(error))
      }
    }
    const legacy = {
      title:"Beginner's Guide to Python Programming: Learn, Code, Succeed",
      url:'https://devcourseweb.com/tutorials/it-software/beginners-guide-to-python-programming-learn-code-succeed/',
    }
    if (!seen.has(legacy.url)) courses.push(legacy)
    if (!courses.length) throw new Error('DevCourseWeb search returned no course pages')

    const candidates = (await Promise.all(courses.slice(0, 8).map(async course => {
      try {
        const detailResponse = await fetch(course.url, {
          headers:{ 'user-agent':UA, accept:'text/html,application/xhtml+xml' },
          redirect:'follow',
          signal:AbortSignal.timeout(30000),
        })
        const detailHtml = await detailResponse.text()
        const magnetMatch = /href=["'](magnet:\?[^"']+)["']/i.exec(detailHtml)
        if (!magnetMatch) return null
        const magnet = magnetMatch[1].replace(/&amp;/gi, '&').replace(/&#038;/gi, '&')
        const infoHash = /[?&]xt=urn:btih:([^&]+)/i.exec(magnet)?.[1] || ''
        return { course, magnet, infoHash }
      } catch {
        return null
      }
    }))).filter(Boolean)

    console.log(
      'CANDIDATE DevCourseWeb magnet candidates:',
      candidates.map(row => `${row.course.title} [${row.infoHash.slice(0, 12)}]`).join(' | ') || '(none)',
    )
    if (!candidates.length) throw new Error('DevCourseWeb search results exposed no magnets')

    function metadataAttempt(row) {
      return new Promise((resolve, reject) => {
        let settled = false
        let torrent
        const finish = (fn, value) => {
          if (settled) return
          settled = true
          clearTimeout(timer)
          fn(value)
        }
        const timer = setTimeout(
          () => finish(reject, new Error('metadata timeout: ' + row.course.title)),
          75000,
        )
        try {
          torrent = client.add(row.magnet, { deselect:true }, ready => finish(resolve, { ...row, torrent:ready }))
          torrent.once('error', error => finish(reject, error))
        } catch (error) {
          finish(reject, error)
        }
      })
    }

    let winner
    try {
      winner = await Promise.any(candidates.map(metadataAttempt))
    } catch {
      throw new Error(`DevCourseWeb had ${candidates.length} magnets but none returned torrent metadata`)
    }

    const { torrent, course } = winner
    const files = (torrent.files || [])
      .filter(file => Number(file.length || 0) > 0)
      .sort((a,b) => Number(a.length || 0) - Number(b.length || 0))
    const file = files.find(row => /\.(?:mp4|mkv|webm|pdf|zip|rar|7z)$/i.test(row.name || '')) || files[0]
    if (!file) throw new Error('DevCourseWeb torrent metadata contained no files')

    const wanted = Math.min(65536, Number(file.length || 0))
    const bytes = await new Promise((resolve, reject) => {
      let count = 0
      let settled = false
      const stream = file.createReadStream({ start:0, end:Math.max(0, wanted - 1) })
      const finish = (fn, value) => {
        if (settled) return
        settled = true
        clearTimeout(timer)
        try { stream.destroy() } catch {}
        fn(value)
      }
      const timer = setTimeout(() => finish(reject, new Error('DevCourseWeb torrent byte read timed out')), 120000)
      stream.on('data', chunk => {
        count += chunk.length
        if (count >= Math.min(32768, wanted)) finish(resolve, count)
      })
      stream.once('end', () => finish(resolve, count))
      stream.once('error', error => finish(reject, error))
    })
    if (bytes < Math.min(1024, wanted)) throw new Error('DevCourseWeb torrent returned too few bytes')

    console.log(
      'CANDIDATE-PASS DevCourseWeb search -> course -> magnet -> torrent bytes:',
      course.title,
      'searchResults=', courses.length,
      'magnetCandidates=', candidates.length,
      'torrentFiles=', files.length,
      'file=', file.name,
      'fileBytes=', file.length,
      'bytesRead=', bytes,
      'infoHash=', torrent.infoHash,
    )
  } catch (error) {
    console.log('CANDIDATE DevCourseWeb error=', error?.message || String(error))
  } finally {
    await destroyTorrentClient(client)
  }
}
await probeDevCourseWeb()

console.log('PASS course live source qualification')
