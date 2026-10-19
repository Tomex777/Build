import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, readdir, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { tryPublicTikTokFallback } from './tiktok-fallback.js'

const BINARY = '/opt/mscc/tools/yt-dlp/bin/yt-dlp'
const GALLERY_BINARY = '/opt/mscc/tools/yt-dlp/bin/gallery-dl'
export const SOCIAL_MAX_BYTES = 64 * 1024 * 1024
const MAX_ALBUM_ITEMS = 6
const MAX_TOTAL_BYTES = 128 * 1024 * 1024
const MAX_RUNTIME_MS = 4 * 60 * 1000

const PLATFORMS = Object.freeze([
  { name:'TikTok', hosts:['tiktok.com','www.tiktok.com','m.tiktok.com','vm.tiktok.com','vt.tiktok.com'] },
  { name:'Instagram', hosts:['instagram.com','www.instagram.com','m.instagram.com','instagr.am','www.instagr.am'] },
  { name:'Facebook', hosts:['facebook.com','www.facebook.com','m.facebook.com','web.facebook.com','fb.watch','www.fb.watch'] },
  { name:'X', hosts:['x.com','www.x.com','twitter.com','www.twitter.com','mobile.twitter.com'] },
  { name:'Reddit', hosts:['reddit.com','www.reddit.com','old.reddit.com','v.redd.it','redd.it','www.redd.it'] },
  { name:'Pinterest', hosts:['pinterest.com','www.pinterest.com','m.pinterest.com','pin.it','www.pin.it'] },
  { name:'Threads', hosts:['threads.net','www.threads.net','threads.com','www.threads.com'] },
  { name:'Vimeo', hosts:['vimeo.com','www.vimeo.com','player.vimeo.com'] },
])

const MIME = Object.freeze({
  mp4:'video/mp4', mov:'video/quicktime', webm:'video/webm', mkv:'video/x-matroska',
  jpg:'image/jpeg', jpeg:'image/jpeg', png:'image/png', webp:'image/webp',
  gif:'image/gif', m4a:'audio/mp4', mp3:'audio/mpeg', opus:'audio/ogg',
})

export function parseSocialRequest(text = '') {
  const raw = String(text || '').trim()
  const match = raw.match(/https?:\/\/[^\s<>"'`]+/i)
  if (!match) throw new Error('Send a public post link: .dl <TikTok / Instagram / Facebook / X / Reddit URL>.')
  const candidate = match[0].replace(/[),.!?]+$/g, '')
  if (candidate.length > 2048) throw new Error('That social-media URL is too long.')
  let url
  try { url = new URL(candidate) } catch { throw new Error('That is not a valid link.') }
  if (!['https:','http:'].includes(url.protocol) || url.username || url.password || url.port) {
    throw new Error('Only ordinary public HTTPS social-media links are supported.')
  }
  // Upgrade conventional HTTP share links; do not accept custom ports, credentials or non-social hosts.
  url.protocol = 'https:'
  const host = url.hostname.toLowerCase()
  const platform = PLATFORMS.find(item => item.hosts.includes(host))
  if (!platform) throw new Error('Supported links: TikTok, Instagram, Facebook, X/Twitter, Reddit, Pinterest, Threads and Vimeo.')
  if (!url.pathname || url.pathname === '/') throw new Error('Send a link to one post or video, not a social-media homepage.')
  if (platform.name === 'TikTok' && !['vm.tiktok.com','vt.tiktok.com'].includes(host) &&
      !/^\/(?:@[\w.-]+\/(?:video|photo)\/\d+|t\/[A-Za-z0-9_-]+)\/?$/i.test(url.pathname)) {
    throw new Error('Send a TikTok video/photo or share-link URL, not a profile or redirect.')
  }
  if (platform.name === 'Facebook' && !['fb.watch','www.fb.watch'].includes(host) &&
      !(/^\/(?:reel\/\d+|[^/]+\/videos\/\d+|videos\/\d+|share\/(?:v|r|p)\/[^/]+)\/?$/i.test(url.pathname) ||
        (url.pathname === '/watch/' || url.pathname === '/watch') && url.searchParams.has('v'))) {
    throw new Error('Send a Facebook Reel, watch, share or video post URL.')
  }
  if (platform.name === 'Pinterest' && !['pin.it','www.pin.it'].includes(host) &&
      !/^\/pin\/\d+\/?$/i.test(url.pathname)) {
    throw new Error('Send one Pinterest Pin link.')
  }
  if (platform.name === 'Vimeo' && !/^\/(?:\d+|video\/\d+)\/?$/i.test(url.pathname)) {
    throw new Error('Send a Vimeo video link.')
  }
  if (platform.name === 'X' && !/^\/(?:[^/]+\/status|i\/status)\/\d+\/?$/i.test(url.pathname)) {
    throw new Error('Send the X/Twitter post link ending in /status/<number>.')
  }
  if (platform.name === 'Instagram' && !/^\/(?:p|reel|reels|tv)\/[^/]+\/?$/i.test(url.pathname)) {
    throw new Error('Send one Instagram post or Reel URL. Private stories and profiles are not supported.')
  }
  if (platform.name === 'Threads' && !/^\/(?:@[^/]+\/post|t)\/[^/]+\/?$/i.test(url.pathname)) {
    throw new Error('Send a specific Threads post URL.')
  }
  const isAlbum = platform.name === 'Instagram' && /^\/p\//i.test(url.pathname)
  return { url:url.href, displayUrl:url.origin + url.pathname, platform:platform.name, isAlbum }
}

function extractorError(stderr, status) {
  if (/\b(?:private video|video is private|private account|privacy settings|friends.only)\b/i.test(stderr)) {
    return new Error('The platform reports this video is private or restricted. NIGHT can only fetch publicly available posts.')
  }
  // Login prompts and HTTP 403 frequently indicate extractor bot-blocking,
  // even on public posts. Never tell the user their post is private on that basis.
  if (/login|sign.in|not available|forbidden|403|cookies|age.restricted/i.test(stderr)) {
    return new Error('The site blocked anonymous extraction. This does not mean the video is private.')
  }
  if (/unsupported URL|no video formats|no video|not a valid url/i.test(stderr)) {
    return new Error('This post has no downloadable media, or its format is not supported yet.')
  }
  if (/HTTP Error 429|rate.limit|too many requests/i.test(stderr)) {
    return new Error('The social platform is rate-limiting NIGHT. Try again later.')
  }
  if (/larger than max|maximum filesize|does not pass filter/i.test(stderr)) {
    return new Error('That post exceeds NIGHT’s 64 MB per-file download limit.')
  }
  return new Error(`NIGHT could not download this public post (extractor exit ${status}). The site may have changed or blocked server downloads.`)
}

export function socialExtractorArgs(request, dir, maxBytes = SOCIAL_MAX_BYTES) {
  return [
    '--ignore-config', '--no-warnings', '--no-exec', '--no-remote-components',
    '--no-write-thumbnail', '--no-write-info-json', '--no-mtime', '--no-cache-dir',
    '--restrict-filenames', '--no-part', '--socket-timeout', '20',
    '--retries', '1', '--fragment-retries', '1', '--js-runtimes', 'node',
    '--max-filesize', String(maxBytes),
    ...(request.isAlbum ? ['--yes-playlist', '--playlist-items', `1:${MAX_ALBUM_ITEMS}`] : ['--no-playlist']),
    '-f', 'bv*+ba/best', '--merge-output-format', 'mp4',
    '-o', join(dir, 'night-%(playlist_index|0)s-%(id|media)s.%(ext)s'),
    '--', request.url,
  ]
}

export function galleryExtractorArgs(request,dir,maxBytes = SOCIAL_MAX_BYTES) {
  return [
    '--config-ignore', '--no-postprocessors', '--no-part', '--no-mtime',
    '--restrict-filenames', 'ascii', '--range', '1-6',
    '--filesize-max', String(maxBytes), '-R', '1', '--http-timeout', '20',
    '-D', dir, '-f', 'night-{filename}.{extension}', request.url,
  ]
}

async function diskUsage(dir) {
  const entries = await readdir(dir, { withFileTypes:true })
  let total = 0
  for (const entry of entries) {
    if (!entry.isFile()) continue
    const file = await stat(join(dir,entry.name)).catch(() => null)
    total += file?.size || 0
  }
  return total
}

async function executeExtractor(request, dir, { engine = 'video', binary = engine === 'gallery'
  ? (process.env.MSCC_SOCIAL_GALLERY_BIN || GALLERY_BINARY)
  : (process.env.MSCC_SOCIAL_YTDLP_BIN || process.env.MSCC_YOUTUBE_YTDLP_BIN || BINARY),
  spawnProcess = spawn, maxBytes = SOCIAL_MAX_BYTES, timeoutMs = MAX_RUNTIME_MS } = {}) {
  const args = engine === 'gallery' ? galleryExtractorArgs(request,dir,maxBytes) : socialExtractorArgs(request,dir,maxBytes)
  await new Promise((resolve, reject) => {
    const child = spawnProcess(binary,args,{ stdio:['ignore','ignore','pipe'],env:process.env })
    let stderr = ''
    let settled = false
    let checking = false
    let killedForSize = false
    let killedForTimeout = false
    const done = (error) => {
      if (settled) return
      settled = true
      clearInterval(watcher)
      clearTimeout(timer)
      error ? reject(error) : resolve()
    }
    const timer = setTimeout(() => { killedForTimeout = true; child.kill('SIGKILL') },timeoutMs)
    const watcher = setInterval(async () => {
      if (checking || settled) return
      checking = true
      try {
        if (await diskUsage(dir) > MAX_TOTAL_BYTES) {
          killedForSize = true
          child.kill('SIGKILL')
        }
      } catch { /* The final stat check still runs after extraction. */ }
      finally { checking = false }
    }, 300)
    child.stderr?.on('data', data => { stderr = (stderr + String(data)).slice(-4000) })
    child.once('error', error => {
      done(error?.code === 'ENOENT'
        ? new Error('Social downloads are unavailable: the NIGHT ' + (engine === 'gallery' ? 'gallery-dl' : 'yt-dlp') + ' extractor is not installed.')
        : new Error('Could not start the social-media downloader.'))
    })
    child.once('close', code => {
      if (killedForSize) return done(new Error('That download exceeds NIGHT’s 128 MB total job limit.'))
      if (killedForTimeout) return done(new Error('That social-media download timed out.'))
      return done(code === 0 ? null : extractorError(stderr,code))
    })
  })
}

async function mediaFiles(dir, maxBytes = SOCIAL_MAX_BYTES) {
  const entries = await readdir(dir, { withFileTypes:true })
  const output = []
  let total = 0
  for (const entry of entries) {
    if (!entry.isFile() || !/^night-[a-zA-Z0-9_.-]+\.[a-z0-9]+$/i.test(entry.name)) continue
    const ext = entry.name.split('.').at(-1).toLowerCase()
    if (!MIME[ext]) continue
    const file = join(dir,entry.name)
    const info = await stat(file)
    if (!info.size || info.size > maxBytes) throw new Error('One social-media file exceeds NIGHT’s 64 MB limit.')
    total += info.size
    if (total > MAX_TOTAL_BYTES) throw new Error('That social-media post exceeds NIGHT’s total download limit.')
    output.push({ file, size:info.size, mime:MIME[ext], ext, name:entry.name })
  }
  if (!output.length) throw new Error('No downloadable video or image was found in that public post.')
  if (output.length > MAX_ALBUM_ITEMS) throw new Error('This post has too many media items. Try a single post.')
  return output.sort((a,b) => a.name.localeCompare(b.name,undefined,{numeric:true}))
}

export async function prepareSocialDownload(rawUrl, options = {}) {
  const request = typeof rawUrl === 'string' ? parseSocialRequest(rawUrl) : rawUrl
  const dir = await mkdtemp(join(tmpdir(),'night-social-'))
  const cleanup = async () => rm(dir,{ recursive:true,force:true }).catch(() => {})
  try {
    // yt-dlp is a video extractor. gallery-dl handles photo-only Instagram
    // carousels and Pinterest pins, including multiple images in one post.
    const galleryFirst = (request.platform === 'Instagram' && request.isAlbum) || request.platform === 'Pinterest'
    const engines = galleryFirst ? ['gallery','video'] :
      request.platform === 'Instagram' ? ['video','gallery'] : ['video']
    let lastError
    for (const engine of engines) {
      try {
        // Nexia bounded the initial TikTok yt-dlp attempt to 90 seconds.
        // Keep other social platforms' existing 4-minute limit unchanged.
        await executeExtractor(request,dir,{ ...options,engine,
          ...(request.platform === 'TikTok' ? {timeoutMs:Math.min(options.timeoutMs || 90_000,90_000)} : {}),
        })
        return { request, files:await mediaFiles(dir,options.maxBytes), cleanup }
      } catch (error) {
        lastError = error
        await rm(dir,{ recursive:true,force:true })
        await mkdir(dir,{ recursive:true })
      }
    }
    if (request.platform === 'TikTok') {
      // Nexia used yt-dlp then a public TikTok API. Restore this resilience:
      // try the older Siputzx method, then an independent TikWM extractor.
      // Neither public provider receives private credentials or cookies.
      const files = await tryPublicTikTokFallback(request,dir,options)
      return { request, files, cleanup }
    }
    throw lastError
  } catch (error) { await cleanup(); throw error }
}
