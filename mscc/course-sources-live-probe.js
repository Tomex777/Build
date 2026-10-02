import mit from './sources/courses/mit-ocw.js'
import openlearn from './sources/courses/openlearn.js'

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

const olSearch = await openlearn.run({ action:'search', query:'artificial intelligence', context:{} })
const olCourse = (olSearch.items || []).find(item => /artificial intelligence/i.test(item.title)) || olSearch.items?.[0]
if (!olCourse) throw new Error('OpenLearn live search returned no course')
const olContents = await openlearn.run({ action:'contents', item:olCourse, context:{} })
const olPart = olContents.contents?.[0]
if (!olPart) throw new Error('OpenLearn returned no course section')
let olSent = null
const olDelivery = await openlearn.run({
  action:'download',
  item:olCourse,
  content:olPart,
  context:{ send:async payload => { olSent=payload } },
})
const olData = olSent?.document
if (!olDelivery?.delivered || !Buffer.isBuffer(olData) || olData.length < 500) {
  throw new Error('OpenLearn did not produce a real course-section document')
}
console.log('PASS OpenLearn search -> sections -> document bytes:', olCourse.title, olPart.title, olData.length)

console.log('PASS course live source qualification')
