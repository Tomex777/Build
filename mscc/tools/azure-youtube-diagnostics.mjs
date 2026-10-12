// One-shot, read-only diagnostic on the actual Azure host.
// Never print signed stream URLs, raw yt-dlp logs, proxy settings, cookies or account data.
import { spawn } from 'node:child_process'

const YTDLP = process.env.MSCC_YOUTUBE_YTDLP_BIN || '/opt/mscc/tools/yt-dlp/bin/yt-dlp'
const VIDEO_ID = 'XEuaRjnuHlc'
const VIDEO_URL = 'https://www.youtube.com/watch?v=' + VIDEO_ID

function categorize(reason = '') {
  const msg = String(reason)
  if (/sign in to confirm.{0,80}(not a bot|you.re not a bot)|unusual traffic|automated queries|confirm.{0,40}not a bot/i.test(msg)) return 'youtube_access_challenge'
  if (/HTTP Error 429|too many requests|rate.limit/i.test(msg)) return 'rate_limited'
  if (/geo.restrict|not available in your country/i.test(msg)) return 'georestricted'
  if (/not available|video unavailable|private video/i.test(msg)) return 'video_unavailable'
  if (/no video formats|requested format is not available/i.test(msg)) return 'no_formats'
  return 'extractor_error'
}

function run(args, timeoutMs = 45000) {
  return new Promise(resolve => {
    let stdout = ''
    let stderr = ''
    let done = false
    let timer
    let child
    const finish = result => {
      if (done) return
      done = true
      clearTimeout(timer)
      resolve(result)
    }
    try {
      child = spawn(YTDLP, args, {stdio:['ignore','pipe','pipe']})
    } catch (error) {
      return finish({error:error?.code || 'spawn_error'})
    }
    timer = setTimeout(() => {
      child.kill('SIGKILL')
      finish({error:'timeout'})
    }, timeoutMs)
    child.stdout.on('data', chunk => {
      stdout += String(chunk)
      if (stdout.length > 8 * 1024 * 1024) {
        child.kill('SIGKILL')
        finish({error:'output_limit'})
      }
    })
    child.stderr.on('data', chunk => { stderr = (stderr + String(chunk)).slice(-8192) })
    child.once('error', error => finish({error:error?.code || 'spawn_error'}))
    child.once('close', code => finish({code, stdout, stderr}))
  })
}

async function main() {
  const summary = {
    component:'mscc-youtube-azure',
    testedVideo:VIDEO_ID,
    runtime:process.version,
    extractorInstalled:false,
    extractorVersion:null,
    result:'not_run',
  }
  const version = await run(['--version'], 5000)
  if (version.code !== 0) {
    summary.result = version.error === 'ENOENT' ? 'extractor_missing' : 'extractor_not_executable'
    return summary
  }
  summary.extractorInstalled = true
  summary.extractorVersion = version.stdout.trim().slice(0, 60)
  const profiles = ['default', 'tv,web_safari', 'web_embedded']
  summary.attempts = []
  let parsed
  for (const profile of profiles) {
    const args = [
      '--ignore-config', '--no-playlist', '--no-warnings', '--js-runtimes', 'node',
      '--socket-timeout', '12', '--retries', '0', '--fragment-retries', '0',
      ...(profile === 'default' ? [] : ['--extractor-args', 'youtube:player_client=' + profile]),
      '--dump-single-json', '--skip-download', VIDEO_URL,
    ]
    const result = await run(args, 30000)
    if (result.error) {
      const status = result.error === 'timeout' ? 'timeout' : 'extractor_execution_failed'
      summary.attempts.push({profile, status})
      summary.result = status
      continue
    }
    if (result.code !== 0) {
      const status = categorize(result.stderr)
      summary.attempts.push({profile, status})
      summary.result = status
      continue
    }
    try { parsed = JSON.parse(result.stdout) }
    catch {
      summary.attempts.push({profile, status:'invalid_extractor_json'})
      summary.result = 'invalid_extractor_json'
      continue
    }
    summary.attempts.push({profile, status:'resolved'})
    summary.successfulProfile = profile
    break
  }
  if (!parsed) {
    // Preserve a confirmed challenge instead of overwriting it with a later
    // unrelated error when every client fails on this IP.
    if (summary.attempts.some(row => row.status === 'youtube_access_challenge')) {
      summary.result = 'youtube_access_challenge'
    }
    return summary
  }
  const formats = Array.isArray(parsed.formats) ? parsed.formats : []
  const videos = formats.filter(f => f?.vcodec && f.vcodec !== 'none')
  const direct = videos.filter(f => {
    try {
      const host = new URL(f.url).hostname
      return host === 'googlevideo.com' || host.endsWith('.googlevideo.com')
    } catch { return false }
  })
  summary.result = videos.length ? 'formats_resolved' : 'no_formats'
  summary.videoFormatCount = videos.length
  summary.directVideoFormatCount = direct.length
  summary.maxHeight = Math.max(0, ...videos.map(f => Number(f.height || 0) || 0))
  if (!direct.length) return summary

  // Probe one bounded byte-range against the CDN, never download the full video.
  const best = direct.sort((a,b) => Number(a.height || 0) - Number(b.height || 0))[0]
  try {
    const response = await fetch(best.url, {
      headers:{range:'bytes=0-0', referer:'https://www.youtube.com/'},
      signal:AbortSignal.timeout(8000),
      redirect:'error',
    })
    summary.cdnRangeStatus = response.status
    await response.body?.cancel().catch(() => {})
  } catch {
    summary.cdnRangeStatus = 'network_error'
  }
  return summary
}

try {
  console.log(JSON.stringify(await main()))
} catch {
  console.log(JSON.stringify({component:'mscc-youtube-azure', result:'diagnostic_internal_error'}))
}
