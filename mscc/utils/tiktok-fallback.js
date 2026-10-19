// Public TikTok downloader: preserve Nexia's fallback order
// yt-dlp (social-download) -> Siputzx -> ruhend-scraper/ttdl, with TikWM as
// an additional source. No auth, TikTok cookies or private media bypass.
import { open, rm, rename } from 'node:fs/promises'
import { join } from 'node:path'

const MAX_ITEMS = 6
const MAX_ALBUM_BYTES = 128 * 1024 * 1024
const MAX_JSON_BYTES = 256 * 1024
const PROVIDER_TIMEOUT_MS = 15_000
const MEDIA_TIMEOUT_MS = 65_000
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36'
const MEDIA_HOSTS = Object.freeze([
  'tiktokcdn.com', 'tiktokv.com', 'tiktok.com', 'byteoversea.com',
  'ibytedtos.com', 'byteimg.com', 'bytecdn.cn', 'muscdn.com',
  'tikwm.com', 'tikwm.net', 'siputzx.my.id',
  'tiktokcdn-us.com', 'tiktokcdn-eu.com',
])

// Short TikTok links must resolve through the official TikTok hosts only.
// A failed expansion never proves that a post is private.
export function canonicalTikTokUrl(value) {
  let url
  try { url = new URL(value) } catch { return null }
  if (url.protocol !== 'https:' || url.username || url.password || url.port ||
      !['tiktok.com','www.tiktok.com','m.tiktok.com'].includes(url.hostname.toLowerCase()) ||
      !/^\/@[A-Za-z0-9_.-]+\/(?:video|photo)\/\d+\/?$/.test(url.pathname)) return null
  return `https://www.tiktok.com${url.pathname.replace(/\/$/,'')}`
}

export async function resolveTikTokShortUrl(value, fetchImpl = globalThis.fetch) {
  const canonical = canonicalTikTokUrl(value)
  if (canonical) return canonical
  let current
  try { current = new URL(value) } catch { return null }
  if (current.protocol !== 'https:' || current.username || current.password || current.port ||
      !['vt.tiktok.com','vm.tiktok.com','www.tiktok.com','tiktok.com'].includes(current.hostname.toLowerCase())) return null
  if (!['vt.tiktok.com','vm.tiktok.com'].includes(current.hostname.toLowerCase()) &&
      !/^\/t\/[A-Za-z0-9_-]+\/?$/.test(current.pathname)) return null
  for (let hop = 0; hop < 5; hop++) {
    let response
    try {
      response = await fetchImpl(current.href, {
        redirect:'manual', headers:{ 'User-Agent':UA, Accept:'text/html,*/*;q=0.8' },
        signal:AbortSignal.timeout(8000),
      })
      const location = response.headers?.get?.('location')
      await response.body?.cancel?.().catch(() => {})
      if (![301,302,303,307,308].includes(response.status) || !location) return null
      const next = new URL(location,current.href)
      if (next.protocol !== 'https:' || next.username || next.password || next.port ||
          !['tiktok.com','www.tiktok.com','m.tiktok.com','vt.tiktok.com','vm.tiktok.com'].includes(next.hostname.toLowerCase())) return null
      const found = canonicalTikTokUrl(next.href)
      if (found) return found
      current = next
    } catch { return null }
  }
  return null
}

export function validTikTokMediaUrl(value, origin = undefined) {
  if (typeof value !== 'string' || value.length > 4096) return null
  let url
  try { url = new URL(value, origin) } catch { return null }
  if (url.protocol !== 'https:' || url.username || url.password || url.port) return null
  const host = url.hostname.toLowerCase()
  if (!MEDIA_HOSTS.some(suffix => host === suffix || host.endsWith(`.${suffix}`))) return null
  return url.href
}

// Return one group for a gallery or several single-video alternatives.
// They are alternatives, NOT multiple videos to send to WhatsApp.
export function extractTikTokMediaVariants(provider, response) {
  const body = response?.data
  if (!body || typeof body !== 'object') return []
  if (provider === 'tikwm' && response.code !== 0) return []
  if (provider === 'siputzx' && !response.status) return []
  const urlFor = value => validTikTokMediaUrl(value, provider === 'tikwm' ? 'https://www.tikwm.com' : 'https://api.siputzx.my.id')
  const pictures = Array.isArray(body.images) ? body.images : []
  const gallery = pictures.slice(0,MAX_ITEMS).map(item => ({
    url:urlFor(typeof item === 'string' ? item : (item?.url || item?.url_list?.[0])),
    ext:'jpg', mime:'image/jpeg',
  })).filter(item => item.url)
  if (gallery.length) return [gallery]
  const raw = provider === 'siputzx'
    ? [...(Array.isArray(body.urls) ? body.urls : []), body.video_url, body.download_url, body.url]
    : provider === 'ruhend'
      ? [body.video_hd, body.video, body.video_wm]
      : [body.hdplay, body.play, body.wmplay]
  const urls = [...new Set(raw.map(urlFor).filter(Boolean))]
  return urls.slice(0,6).map(url => [{ url, ext:'mp4', mime:'video/mp4' }])
}

export function extractTikTokMedia(provider,response) {
  return extractTikTokMediaVariants(provider,response)[0] || []
}

async function fetchJson(fetchImpl, url, init = {}) {
  const response = await fetchImpl(url, {
    redirect:'manual', ...init, headers:{ 'User-Agent':UA, Accept:'application/json', ...(init.headers || {}) },
    signal:AbortSignal.timeout(PROVIDER_TIMEOUT_MS),
  })
  if (!response.ok || response.status >= 300) throw new Error(`provider HTTP ${response.status}`)
  if (Number(response.headers?.get?.('content-length') || 0) > MAX_JSON_BYTES) throw new Error('oversized provider response')
  const chunks = []
  let total = 0
  for await (const chunk of response.body) {
    total += chunk.length
    if (total > MAX_JSON_BYTES) throw new Error('oversized provider response')
    chunks.push(chunk)
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

// Nexia's exact third extractor was ruhend-scraper's `ttdl` function,
// returning a FLAT object (video_hd/video/video_wm) rather than a data array.
// Import lazily only if yt-dlp and Siputzx fail; bounded to avoid holding a
// media worker indefinitely if the scraper hangs on a changed TikTok page.
export async function getRuhendTikTokData(postUrl, scraperImpl) {
  const run = async () => {
    const scraper = scraperImpl || await import('ruhend-scraper')
    const ttdl = typeof scraper === 'function' ? scraper :
      scraper?.ttdl || scraper?.default?.ttdl
    if (typeof ttdl !== 'function') throw new Error('ruhend ttdl extractor missing')
    return ttdl(postUrl)
  }
  let timeout
  try {
    return await Promise.race([
      run(),
      new Promise((_,reject) => {timeout=setTimeout(() => reject(new Error('ruhend timeout')),PROVIDER_TIMEOUT_MS)})
    ])
  } finally {clearTimeout(timeout)}
}

async function getProviderData(fetchImpl, provider, endpoint, originalUrl) {
  if (provider !== 'tikwm') return fetchJson(fetchImpl,endpoint)
  // TikWM accepts URL-encoded POST, with GET as a compatibility fallback.
  try {
    const post = await fetchJson(fetchImpl,'https://www.tikwm.com/api/',{
      method:'POST',
      headers:{ 'Content-Type':'application/x-www-form-urlencoded' },
      body:new URLSearchParams({ url:originalUrl, hd:'1' }).toString(),
    })
    if (post?.code === 0 && post?.data) return post
  } catch { /* Also retry if POST is blocked by the provider. */ }
  return fetchJson(fetchImpl,endpoint)
}

async function saveMedia(fetchImpl, url, file, mime, maxBytes) {
  let target = url
  let response
  for (let redirects = 0; redirects < 4; redirects++) {
    response = await fetchImpl(target, {
      redirect:'manual', headers:{ 'User-Agent':UA, Accept:'video/mp4,image/*,*/*;q=0.8', Referer:new URL(target).hostname.endsWith('tikwm.com') ? 'https://www.tikwm.com/' : 'https://www.tiktok.com/' },
      signal:AbortSignal.timeout(MEDIA_TIMEOUT_MS),
    })
    if (![301,302,303,307,308].includes(response.status)) break
    const next = validTikTokMediaUrl(response.headers.get('location'), target)
    await response.body?.cancel?.().catch(() => {})
    if (!next) throw new Error('unsafe media redirect')
    target = next
    response = null
  }
  if (!response?.ok || response.status >= 300) throw new Error(`media HTTP ${response?.status || 'redirect limit'}`)
  if (Number(response.headers?.get?.('content-length') || 0) > maxBytes) throw new Error('media exceeds size limit')
  const type = (response.headers?.get?.('content-type') || '').split(';')[0].trim().toLowerCase()
  if (type && !['application/octet-stream','binary/octet-stream',mime,'video/mp4','image/jpeg','image/png','image/webp'].includes(type)) throw new Error('provider sent non-media')
  let size = 0
  const handle = await open(file,'wx+')
  try {
    for await (const chunk of response.body) {
      size += chunk.length
      if (size > maxBytes) throw new Error('media exceeds size limit')
      await handle.write(chunk)
    }
    if (size < 12) throw new Error('empty media')
    const signature = Buffer.alloc(16)
    await handle.read(signature,0,16,0)
    const detected = signature.toString('ascii',4,8) === 'ftyp' ? { mime:'video/mp4', ext:'mp4' }
      : signature[0] === 0xff && signature[1] === 0xd8 ? { mime:'image/jpeg', ext:'jpg' }
      : signature.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])) ? { mime:'image/png', ext:'png' }
      : signature.toString('ascii',0,4) === 'RIFF' && signature.toString('ascii',8,12) === 'WEBP' ? { mime:'image/webp', ext:'webp' } : null
    if (!detected || (mime === 'video/mp4' ? detected.mime !== 'video/mp4' : !detected.mime.startsWith('image/'))) throw new Error('provider returned invalid media')
    return { size, ...detected }
  } catch (error) {
    await handle.close()
    await rm(file,{ force:true }).catch(() => {})
    throw error
  } finally { await handle.close().catch(() => {}) }
}

export async function tryPublicTikTokFallback(request, dir, { fetchImpl = globalThis.fetch, maxBytes = 64 * 1024 * 1024, scraperImpl } = {}) {
  if (request.platform !== 'TikTok' || !/^https:\/\/(?:www\.|m\.|vm\.|vt\.)?tiktok\.com\//i.test(request.url) ||
      !/^https:$/.test(new URL(request.url).protocol)) throw new Error('TikTok fallback requires an official HTTPS TikTok post')
  // Provider APIs often reject vt/vm short links while accepting their canonical post.
  // Retain the original as a backup if redirect resolution succeeds but a provider fails.
  const canonical = await resolveTikTokShortUrl(request.url,fetchImpl)
  const urls = [...new Set([canonical,request.url].filter(Boolean))]
  for (const postUrl of urls) {
    const providers = [
      ['siputzx', `https://api.siputzx.my.id/api/d/tiktok?url=${encodeURIComponent(postUrl)}`],
      ['ruhend', null],
      ['tikwm', `https://www.tikwm.com/api/?url=${encodeURIComponent(postUrl)}&hd=1`],
    ]
    for (const [provider,endpoint] of providers) {
      try {
        const response = provider === 'ruhend' ? {data:await getRuhendTikTokData(postUrl,scraperImpl)} :
          await getProviderData(fetchImpl,provider,endpoint,postUrl)
        const variants = extractTikTokMediaVariants(provider,response)
        if (!variants.length) throw new Error('provider returned no supported media URLs')
        for (const [variantIndex, medias] of variants.entries()) {
          const files = []
          let total = 0
          try {
            for (const [index, media] of medias.entries()) {
              const name = `night-tiktok-${index + 1}.${media.ext}`
              const file = join(dir,name)
              const downloaded = await saveMedia(fetchImpl,media.url,file,media.mime,Math.min(maxBytes,MAX_ALBUM_BYTES-total))
              const actualName = `night-tiktok-${index + 1}.${downloaded.ext}`
              const actualFile = join(dir,actualName)
              if (file !== actualFile) await rename(file,actualFile)
              total += downloaded.size
              files.push({ file:actualFile, size:downloaded.size, mime:downloaded.mime, ext:downloaded.ext, name:actualName })
            }
            const label = typeof response?.data?.title === 'string' ? response.data.title :
              typeof response?.data?.metadata?.title === 'string' ? response.data.metadata.title : ''
            return files.map(file=>({...file,title:label.replace(/[\\r\\n\\t]+/g,' ').trim().slice(0,240)}))
          } catch (error) {
            for (const item of files) await rm(item.file,{ force:true }).catch(() => {})
            console.warn(`[NIGHT social] TikTok ${provider} media option ${variantIndex+1}/${variants.length} failed: ${error?.message || error}`)
          }
        }
        throw new Error('all returned media URLs failed')
      } catch (error) {
        console.warn(`[NIGHT social] TikTok ${provider} (${postUrl === request.url ? 'shared' : 'canonical'}) fallback failed: ${error?.message || error}`)
      }
    }
  }
  throw new Error('TikTok download failed across all public extractors. The post may be unavailable or TikTok may be blocking server downloads. Please retry or send another public link.')
}
