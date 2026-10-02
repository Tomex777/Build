import { clean, fetchJson, fetchText, sendApk, textFromHtml } from './_shared.js'

const SITE = 'https://f-droid.org'
const SEARCH = 'https://search.f-droid.org'

function packageFromHref(href = '') {
  const match = /\/packages\/([a-zA-Z0-9._-]+)\/?(?:[?#].*)?$/i.exec(String(href || ''))
  return match?.[1] || ''
}

function parseSearch(html = '') {
  const source = String(html || '')
  const items = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']*\/packages\/[a-zA-Z0-9._-]+\/?[^"']*)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const packageName = packageFromHref(match[1])
    if (!packageName || seen.has(packageName)) continue
    const body = match[2] || ''
    const title = clean(textFromHtml(body), 180) || packageName
    if (!title) continue
    seen.add(packageName)
    items.push({
      id:packageName,
      title,
      packageName,
      developer:'',
      version:'',
      description:'',
      url:SITE + '/en/packages/' + packageName + '/',
    })
    if (items.length >= 25) break
  }
  return items
}

function parseVersions(data = {}) {
  const packageName = clean(data?.packageName || '', 220)
  const rows = Array.isArray(data?.packages) ? data.packages : []
  return rows.slice(0, 25).flatMap(row => {
    const version = clean(row?.versionName || '', 80)
    const versionCode = String(row?.versionCode || '').trim()
    if (!versionCode) return []
    return [{
      id:versionCode,
      version:version || `Version ${versionCode}`,
      versionCode,
      date:'',
      packageName,
      suggested:Number(data?.suggestedVersionCode) === Number(versionCode),
      raw:row,
    }]
  })
}

async function search(query) {
  const { text } = await fetchText(
    SEARCH + '/?' + new URLSearchParams({ q:query, lang:'en' }),
    { headers:{ accept:'text/html,application/xhtml+xml' } },
  )
  const items = parseSearch(text)
  if (!items.length) throw new Error('F-Droid returned no Android apps.')
  return items
}

async function versions(item = {}) {
  const packageName = clean(item?.packageName || item?.id || '', 220)
  if (!packageName) throw new Error('F-Droid package name is missing.')
  const { data } = await fetchJson(SITE + '/api/v1/packages/' + encodeURIComponent(packageName))
  const rows = parseVersions(data)
  if (!rows.length) throw new Error('F-Droid returned no published APK versions.')
  return rows
}

function apkUrl(item = {}, version = {}) {
  const packageName = clean(item?.packageName || item?.id || version?.packageName || '', 220)
  const versionCode = String(version?.versionCode || version?.id || '').trim()
  if (!packageName || !/^\d+$/.test(versionCode)) {
    throw new Error('F-Droid package/version is incomplete.')
  }
  return SITE + '/repo/' + encodeURIComponent(packageName) + '_' + encodeURIComponent(versionCode) + '.apk'
}

export default {
  id:'fdroid',
  name:'F-Droid',
  description:'Free/open-source Android apps with versioned repository APKs.',
  fallbackOrder:20,

  async run({ action, query, item, version, context }) {
    if (action === 'search') return { items:await search(clean(query, 100)) }
    if (action === 'versions') return { versions:await versions(item || {}) }
    if (action === 'variants') return { variants:[] }
    if (action === 'download') {
      const chosen = version || {}
      const url = apkUrl(item || {}, chosen)
      const label = [
        clean(item?.title || item?.packageName || item?.id || 'F-Droid app', 120),
        clean(chosen?.version || '', 60),
      ].filter(Boolean).join(' ')
      return sendApk(context, { url, fileName:label })
    }
    throw new Error('Unsupported F-Droid Android action: ' + action)
  },

  _test:{ packageFromHref, parseSearch, parseVersions, apkUrl },
}
