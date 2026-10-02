import aptoide from './sources/android/aptoide.js'
import fdroid from './sources/android/fdroid.js'

async function probeBytes(url, label) {
  const response = await fetch(url, {
    headers:{ range:'bytes=0-4095', 'user-agent':'MSCC APK live probe' },
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  try {
    if (!response.ok && response.status !== 206) throw new Error(`${label} HTTP ${response.status}`)
    const bytes = new Uint8Array(await response.arrayBuffer())
    if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
      throw new Error(`${label} did not return ZIP/APK bytes`)
    }
    return bytes.length
  } finally {
    await response.body?.cancel?.().catch?.(() => {})
  }
}

async function resolveDelivered(source, { query, pick }) {
  const search = await source.run({ action:'search', query, context:{} })
  const item = pick(search.items || [])
  if (!item) throw new Error(source.name + ' search did not return the expected app')
  const result = await source.run({ action:'versions', item, context:{} })
  const version = result?.versions?.[0]
  if (!version) throw new Error(source.name + ' returned no version')
  let sent = null
  const delivery = await source.run({
    action:'download',
    item,
    version,
    context:{ send:async payload => { sent=payload } },
  })
  const url = sent?.document?.url
  if (!delivery?.delivered || !/^https?:\/\//.test(String(url || ''))) {
    throw new Error(source.name + ' did not resolve a document URL')
  }
  return { item, version, url }
}

const apt = await resolveDelivered(aptoide, {
  query:'Aptoide',
  pick:items => items.find(item => item.packageName === 'cm.aptoide.pt') || items[0],
})
const aptBytes = await probeBytes(apt.url, 'Aptoide')
console.log('PASS Aptoide search -> versions -> APK bytes:', apt.item.title, apt.version.version, aptBytes)

const fd = await resolveDelivered(fdroid, {
  query:'Termux',
  pick:items => items.find(item => item.packageName === 'com.termux'),
})
const fdBytes = await probeBytes(fd.url, 'F-Droid')
console.log('PASS F-Droid search -> versions -> APK bytes:', fd.item.title, fd.version.version, fdBytes)

console.log('PASS APK live source qualification')
