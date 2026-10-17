// Anonymous public-post fallbacks. Based on Nexia's yt-dlp -> Siputzx
// strategy, with TikWM as an independent backup. No auth or private cookies.
import { open, rm, rename } from 'node:fs/promises'
import { join } from 'node:path'

const MAX_ITEMS = 6
const MAX_JSON_BYTES = 256 * 1024
const PROVIDER_TIMEOUT_MS = 15_000
const MEDIA_TIMEOUT_MS = 65_000
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36'
const MEDIA_HOSTS = Object.freeze([
  'tiktokcdn.com', 'tiktokv.com', 'tiktok.com', 'byteoversea.com',
  'ibytedtos.com', 'byteimg.com', 'bytecdn.cn', 'muscdn.com',
  'tikwm.com', 'tikwm.net', 'siputzx.my.id',
])

export function validTikTokMediaUrl(value, origin = undefined) {
  if (typeof value !== 'string' || value.length > 4096) return null
  let url
  try { url = new URL(value, origin) } catch { return null }
  if (url.protocol !== 'https:' || url.username || url.password || url.port) return null
  const host = url.hostname.toLowerCase()
  if (!MEDIA_HOSTS.some(suffix => host === suffix || host.endsWith(`.${suffix}`))) return null
  return url.href
}

export function extractTikTokMedia(provider, response) {
  const body = response?.data
  if (!body || typeof body !== 'object') return []
  if (provider === 'tikwm' && response.code !== 0) return []
  if (provider === 'siputzx' && !response.status) return []
  const urlFor = value => validTikTokMediaUrl(value, provider === 'tikwm' ? 'https://www.tikwm.com' : 'https://api.siputzx.my.id')
  const pictures = Array.isArray(body.images) ? body.images : []
  if (pictures.length) return pictures.slice(0, MAX_ITEMS).map(item => ({ url:urlFor(item), ext:'jpg', mime:'image/jpeg' })).filter(item => item.url)
  const raw = provider === 'siputzx'
    ? [body.urls?.[0], body.video_url, body.download_url, body.url]
    : [body.hdplay, body.play, body.wmplay]
  const url = raw.map(urlFor).find(Boolean)
  return url ? [{ url, ext:'mp4', mime:'video/mp4' }] : []
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

export async function tryPublicTikTokFallback(request, dir, { fetchImpl = globalThis.fetch, maxBytes = 64 * 1024 * 1024 } = {}) {
  if (request.platform !== 'TikTok') throw new Error('TikTok fallback requires a TikTok post')
  const providers = [
    ['siputzx', `https://api.siputzx.my.id/api/d/tiktok?url=${encodeURIComponent(request.url)}`],
    ['tikwm', `https://www.tikwm.com/api/?url=${encodeURIComponent(request.url)}&hd=1`],
  ]
  for (const [provider, endpoint] of providers) {
    try {
      const parsed = extractTikTokMedia(provider,await getProviderData(fetchImpl,provider,endpoint,request.url))
      if (!parsed.length) throw new Error('no downloadable video or images')
      const files = []
      let total = 0
      try {
        for (const [index, media] of parsed.entries()) {
          const name = `night-tiktok-${index + 1}.${media.ext}`
          const file = join(dir,name)
          const downloaded = await saveMedia(fetchImpl,media.url,file,media.mime,Math.min(maxBytes,128*1024*1024-total))
          const actualName = `night-tiktok-${index + 1}.${downloaded.ext}`
          const actualFile = join(dir,actualName)
          if (file !== actualFile) await rename(file,actualFile)
          total += downloaded.size
          files.push({ file:actualFile, size:downloaded.size, mime:downloaded.mime, ext:downloaded.ext, name:actualName })
        }
        return files
      } catch (error) {
        for (const item of files) await rm(item.file,{ force:true }).catch(() => {})
        throw error
      }
    } catch (error) {
      // A provider block, old CDN URL or extractor failure does not prove
      // that the user's post is private. Try the next public source.
      console.warn(`[NIGHT social] TikTok ${provider} fallback failed: ${error?.message || error}`)
    }
  }
  throw new Error('TikTok download failed across all public extractors. The post may be unavailable or TikTok may be blocking server downloads. Please retry or send another public link.')
}
