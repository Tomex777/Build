import { clean, fetchJson, sendApk, sizeText } from './_shared.js'

const API = 'https://ws75.aptoide.com/api/7'

function fileOf(value = {}) {
  return value?.file || value?.apk || value
}

function normalizeSearchRow(row = {}) {
  const file = fileOf(row)
  const title = clean(row?.name || row?.title || '', 180)
  const packageName = clean(row?.package || row?.packageName || '', 220)
  if (!title || !packageName) return null
  return {
    id:String(row?.id || packageName),
    title,
    packageName,
    developer:clean(row?.developer?.name || row?.developer || '', 140),
    version:clean(file?.vername || row?.version || '', 80),
    description:clean(row?.description || '', 500),
    currentFile:file,
    raw:row,
  }
}

function versionRow(row = {}, packageName = '') {
  const file = fileOf(row)
  const version = clean(file?.vername || row?.vername || row?.version || row?.name || '', 80)
  const versionCode = String(file?.vercode ?? row?.vercode ?? row?.versionCode ?? '').trim()
  const path = String(file?.path || file?.path_alt || row?.path || '').trim()
  if (!version && !versionCode) return null
  return {
    id:versionCode || version || path,
    version:version || (versionCode ? `Version ${versionCode}` : 'Current'),
    versionCode,
    date:clean(file?.added || row?.added || row?.updated || '', 40),
    size:sizeText(file?.filesize || row?.size),
    path,
    packageName,
    hardware:file?.hardware || row?.hardware || null,
    malware:file?.malware || row?.malware || null,
    raw:row,
  }
}

function collectVersions(data = {}, item = {}) {
  const packageName = clean(item?.packageName || item?.package || '', 220)
  const candidates = [
    data?.nodes?.versions?.datalist?.list,
    data?.nodes?.versions?.data?.list,
    data?.nodes?.versions?.list,
    data?.versions?.datalist?.list,
    data?.versions?.list,
    data?.datalist?.list,
  ]
  const rows = candidates.find(Array.isArray) || []
  const out = []
  const seen = new Set()
  const push = raw => {
    const row = versionRow(raw, packageName)
    if (!row) return
    const key = row.versionCode || row.version || row.path
    if (!key || seen.has(key)) return
    seen.add(key)
    out.push(row)
  }
  rows.forEach(push)

  const meta = data?.nodes?.meta?.data || data?.data || item?.raw || item || {}
  push(meta)
  if (item?.currentFile) push({ file:item.currentFile })

  return out
    .sort((a,b) => (Number(b.versionCode) || 0) - (Number(a.versionCode) || 0))
    .slice(0, 25)
}

function metaFile(data = {}) {
  return data?.nodes?.meta?.data?.file || data?.data?.file || data?.file || null
}

async function search(query) {
  const url = API + '/apps/search/query=' + encodeURIComponent(query) + '/limit=25'
  const { data } = await fetchJson(url)
  const rows = Array.isArray(data?.datalist?.list) ? data.datalist.list : []
  const items = rows.map(normalizeSearchRow).filter(Boolean)
  if (!items.length) throw new Error('Aptoide returned no Android apps.')
  return items
}

async function versions(item = {}) {
  const packageName = clean(item?.packageName || item?.package || '', 220)
  if (!packageName) throw new Error('Aptoide package name is missing.')
  try {
    const { data } = await fetchJson(
      API + '/app/get/package_name=' + encodeURIComponent(packageName) + '/nodes=meta,versions',
    )
    const rows = collectVersions(data, item)
    if (rows.length) return rows
  } catch {}
  const fallback = collectVersions({}, item)
  if (fallback.length) return fallback
  throw new Error('Aptoide returned no downloadable versions.')
}

async function resolveDownload(item = {}, version = {}) {
  let path = String(version?.path || version?.raw?.file?.path || version?.raw?.path || '').trim()
  const packageName = clean(item?.packageName || item?.package || version?.packageName || '', 220)
  const versionCode = String(version?.versionCode || version?.id || '').trim()

  if (!path && packageName) {
    const suffix = versionCode && /^\d+$/.test(versionCode)
      ? '/vercode=' + encodeURIComponent(versionCode)
      : ''
    const { data } = await fetchJson(
      'https://ws2.aptoide.com/api/7/app/getMeta/package_name=' + encodeURIComponent(packageName) + suffix,
    )
    path = String(metaFile(data)?.path || metaFile(data)?.path_alt || '').trim()
  }
  if (!path) throw new Error('Aptoide did not return an APK download path.')
  return path
}

export default {
  id:'aptoide',
  name:'Aptoide',
  description:'Broad Android app catalog with versioned direct APK downloads.',
  fallbackOrder:10,

  async run({ action, query, item, version, context }) {
    if (action === 'search') return { items:await search(clean(query, 100)) }
    if (action === 'versions') return { versions:await versions(item || {}) }
    if (action === 'variants') return { variants:[] }
    if (action === 'download') {
      const chosen = version || {}
      const url = await resolveDownload(item || {}, chosen)
      const label = [
        clean(item?.title || item?.name || item?.packageName || 'Android app', 120),
        clean(chosen?.version || chosen?.versionName || '', 60),
      ].filter(Boolean).join(' ')
      return sendApk(context, { url, fileName:label })
    }
    throw new Error('Unsupported Aptoide Android action: ' + action)
  },

  _test:{ normalizeSearchRow, versionRow, collectVersions, metaFile },
}
