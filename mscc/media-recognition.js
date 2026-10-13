import { spawn } from 'node:child_process'
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const MAX_AUDIO = 12 * 1024 * 1024

export function normalizeAnimeMatch(json) {
  const row = Array.isArray(json?.result) ? json.result[0] : null
  const similarity = Number(row?.similarity || 0)
  if (!row || similarity < 0.64) return null
  const meta = row.anilist && typeof row.anilist === 'object' ? row.anilist : {}
  const title = meta.title?.english || meta.title?.romaji || meta.title?.native ||
    (typeof row.filename === 'string' ? row.filename.replace(/\.[^.]+$/,'') : 'Unknown anime')
  return {
    title, episode:row.episode == null ? '?' : String(row.episode),
    seconds:Number(row.from || 0),
    similarity, anilistId:Number(meta.id || row.anilist || 0),
  }
}

export async function identifyAnime(buffer, { fetcher=fetch } = {}) {
  if (!Buffer.isBuffer(buffer) || !buffer.length) throw new Error('Reply to an anime screenshot.')
  if (buffer.length > 8*1024*1024) throw new Error('Image exceeds the 8 MB recognition limit.')
  const response = await fetcher('https://api.trace.moe/search?anilistInfo&cutBorders', {
    method:'POST', headers:{'Content-Type':'application/octet-stream'},
    body:buffer, signal:AbortSignal.timeout(19000),
  })
  if (response.status === 429) throw new Error('Anime identification is rate-limited. Try again later.')
  if (!response.ok) throw new Error('Anime recognition service unavailable (' + response.status + ').')
  return normalizeAnimeMatch(await response.json())
}

export function formatAnimeMatch(match) {
  if (!match) return 'Nami could not identify that scene. Try a clearer screenshot from an actual anime episode.'
  const seconds = Math.floor(match.seconds || 0)
  const stamp = String(Math.floor(seconds/60)).padStart(2,'0') + ':' + String(seconds%60).padStart(2,'0')
  return [
    '🎬 *Anime scene identified*',
    '*Title:* ' + match.title,
    '*Episode:* ' + match.episode,
    '*Timestamp:* ' + stamp,
    '*Similarity:* ' + (match.similarity*100).toFixed(1) + '%',
    match.anilistId ? 'https://anilist.co/anime/' + match.anilistId : '',
    match.similarity < 0.85 ? '_This is a tentative match; verify the scene._' : '',
  ].filter(Boolean).join('\n')
}

function spawnBounded(binary,args, maxMs=14000) {
  return new Promise((resolve,reject) => {
    let out = '', err = '', settled = false
    const child = spawn(binary,args,{stdio:['ignore','pipe','pipe']})
    const timer = setTimeout(()=>child.kill('SIGKILL'),maxMs)
    timer.unref?.()
    const done = (error, result) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      error ? reject(error) : resolve(result)
    }
    child.on('error',error=>done(error))
    child.stdout.on('data',v=>{ out+=v; if (out.length>1024*1024) child.kill('SIGKILL') })
    child.stderr.on('data',v=>{ err+=v; if (err.length>16384) err=err.slice(-16384) })
    child.on('close',code=>done(code===0 ? null : new Error(err.trim() || binary + ' failed or timed out.'),out))
  })
}

export function normalizeAcoustId(json) {
  const choices = (json?.results || [])
    .filter(row=>row?.recordings?.length)
    .sort((a,b)=>Number(b.score)-Number(a.score))
  const row = choices[0]
  if (!row || Number(row.score) < 0.55) return null
  const recording = row.recordings[0]
  const artist = (recording.artists || []).map(a=>a.name).filter(Boolean).join(', ')
  if (!recording.title || !artist) return null
  return {
    title:recording.title, artist, score:Number(row.score),
    provider:'AcoustID', album:recording.releasegroups?.[0]?.title || '',
    url:recording.id ? 'https://musicbrainz.org/recording/' + recording.id : '',
  }
}

export function normalizeAudd(json) {
  if (json?.status !== 'success') {
    throw new Error('Music recognition provider error: ' + String(json?.error?.error_message || json?.error?.message || 'unknown error').slice(0,160))
  }
  const row = json.result
  if (!row?.artist || !row?.title) return null
  return { artist:row.artist, title:row.title, album:row.album || '',
    url:row.song_link || row.spotify?.external_urls?.spotify || '',
    provider:'AudD' }
}

export async function identifyWithAcoustId(file, { key=process.env.ACOUSTID_API_KEY, fetcher=fetch } = {}) {
  if (!key) return null
  const stdout = await spawnBounded(process.env.FPCALC_PATH || 'fpcalc', ['-json','-length','45',file])
  const fingerprint = JSON.parse(stdout)
  if (!fingerprint.duration || !fingerprint.fingerprint) return null
  const body = new URLSearchParams({
    client:key, duration:String(Math.round(fingerprint.duration)),
    fingerprint:fingerprint.fingerprint, meta:'recordings releasegroups compress',
  })
  const response = await fetcher('https://api.acoustid.org/v2/lookup', {
    method:'POST', body, signal:AbortSignal.timeout(15000),
  })
  if (!response.ok) throw new Error('AcoustID service unavailable ('+response.status+').')
  return normalizeAcoustId(await response.json())
}

export async function identifyWithAudd(buffer, { token=process.env.AUDD_API_TOKEN, fetcher=fetch } = {}) {
  if (!token) return null
  if (buffer.length > MAX_AUDIO) throw new Error('Audio clip is too large for recognition.')
  const body = new FormData()
  body.append('api_token',token)
  body.append('return','spotify')
  body.append('file',new Blob([buffer], { type:'audio/mpeg' }), 'sample.mp3')
  const response = await fetcher('https://api.audd.io/', {
    method:'POST', body, signal:AbortSignal.timeout(18000),
  })
  if (!response.ok) throw new Error('Music recognition service unavailable ('+response.status+').')
  return normalizeAudd(await response.json())
}

export async function identifyMusic(buffer, { isVideo=false, mimeType='' } = {}) {
  if (!Buffer.isBuffer(buffer) || !buffer.length) throw new Error('Reply to a voice note or music/video clip.')
  if (!process.env.ACOUSTID_API_KEY && !process.env.AUDD_API_TOKEN) {
    throw new Error('Music recognition is not configured. Set ACOUSTID_API_KEY (fpcalc required) or AUDD_API_TOKEN on the server.')
  }
  const directory = await mkdtemp(join(tmpdir(),'night-recognize-'))
  try {
    const input = join(directory,'sample' + (isVideo ? '.mp4' : mimeType.includes('ogg') ? '.ogg' : '.mp3'))
    const output = join(directory,'clip.mp3')
    await writeFile(input,buffer)
    // Convert bounded portions of voice notes and video to a small compatible audio clip.
    // ffmpeg runs as a child process instead of blocking Night's message event loop.
    await spawnBounded(process.env.FFMPEG_PATH || 'ffmpeg',[
      '-hide_banner','-nostdin','-loglevel','error','-y',
      '-i',input,'-t','35','-vn','-ac','1','-ar','16000','-b:a','64k',output,
    ],20000)
    const audio = await readFile(output)
    if (!audio.length) return null
    const errors = []
    if (process.env.ACOUSTID_API_KEY) {
      try {
        const hit = await identifyWithAcoustId(output)
        if (hit) return hit
      } catch(error) { errors.push(error?.message || 'AcoustID unavailable') }
    }
    if (process.env.AUDD_API_TOKEN) {
      try {
        const hit = await identifyWithAudd(audio)
        if (hit) return hit
      } catch(error) { errors.push(error?.message || 'AudD unavailable') }
    }
    if (errors.length && errors.length === Number(Boolean(process.env.ACOUSTID_API_KEY)) + Number(Boolean(process.env.AUDD_API_TOKEN))) {
      throw new Error('Music recognition failed: ' + errors.join('; ').slice(0,250))
    }
    return null
  } finally {
    await rm(directory,{recursive:true,force:true}).catch(()=>{})
  }
}

export function formatMusicMatch(hit) {
  return hit
    ? ['🎵 *Music identified*','*Song:* ' + hit.title,'*Artist:* ' + hit.artist,
        hit.album ? '*Album:* '+hit.album : '',hit.url || '',
        '_Recognized using ' + hit.provider + '._'].filter(Boolean).join('\n')
    : 'MiMi could not identify that recording. Try a clear 10–30 second clip with audible music.'
}
