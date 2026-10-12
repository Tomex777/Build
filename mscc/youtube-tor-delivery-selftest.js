import assert from 'node:assert/strict'
import { mkdtemp, writeFile, chmod, readFile, rm, mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { resolveYouTubeWithDlp, downloadYouTubeWithDlp, isYouTubeAccessChallenge } from './youtube-dlp.js'
import { prepareYouTubeVideo } from './youtube-video.js'

const dir = await mkdtemp(join(tmpdir(), 'mscc-youtube-tor-delivery-'))
const binary = join(dir, 'fake-yt-dlp')
const calls = join(dir, 'calls.jsonl')
const id = 'XEuaRjnuHlc'
const ytMock = `#!/usr/bin/env node
const {appendFileSync, writeFileSync} = require('node:fs')
const args = process.argv.slice(2)
const tor = args.includes('--proxy') && args[args.indexOf('--proxy') + 1] === 'socks5://127.0.0.1:9050'
appendFileSync(process.env.MSCC_YOUTUBE_TEST_TRACE, JSON.stringify({
  tor, metadata:args.includes('--dump-single-json'), proxy:args.includes('--proxy')
}) + '\\n')
if (!tor) {
  console.error("ERROR: Sign in to confirm you're not a bot")
  process.exit(1)
}
if (args.includes('--dump-single-json')) {
  console.log(JSON.stringify({
    title:'Fortnite Museum',channel:'Unreal Engine',
    formats:[
      {height:360,vcodec:'avc1',acodec:'mp4a.40.2',format_id:'18',ext:'mp4',protocol:'https',
       url:'https://edge.invalid/only-valid-from-tor'},
      {height:720,vcodec:'avc1',acodec:'none',format_id:'136',ext:'mp4',protocol:'https',
       url:'https://edge.invalid/only-valid-from-tor'}
    ]
  }))
} else {
  const template = args[args.indexOf('-o') + 1]
  const file = template.replace('%(ext)s','mp4')
  writeFileSync(file, Buffer.alloc(1024,7))
  console.log(file)
}
`
const previous = {
  bin:process.env.MSCC_YOUTUBE_YTDLP_BIN,
  trace:process.env.MSCC_YOUTUBE_TEST_TRACE,
  opt:process.env.MSCC_YOUTUBE_TOR_FALLBACK,
  fetch:globalThis.fetch,
}
try {
  await writeFile(binary, ytMock, {mode:0o700})
  await chmod(binary, 0o700)
  process.env.MSCC_YOUTUBE_YTDLP_BIN = binary
  process.env.MSCC_YOUTUBE_TEST_TRACE = calls
  delete process.env.MSCC_YOUTUBE_TOR_FALLBACK

  const info = await resolveYouTubeWithDlp(id)
  assert.equal(info.transport,'tor')
  assert.equal(info.formats.length,0, 'never expose Tor-signed URLs to direct fetch')
  assert.equal(info.candidates.length,2)
  assert(info.candidates.every(candidate => candidate.transport === 'tor'))

  const outputDir = join(dir,'direct')
  await mkdir(outputDir)
  const delivered = await downloadYouTubeWithDlp(id,360,outputDir,4096,info.transport)
  assert.equal(delivered.size,1024)

  let videoFetch = false
  globalThis.fetch = async (url) => {
    const u = String(url)
    if (u.startsWith('https://www.youtube.com/watch?')) {
      return new Response('<html>"INNERTUBE_API_KEY":"key","INNERTUBE_CONTEXT_CLIENT_VERSION":"1"</html>')
    }
    if (u.includes('/youtubei/v1/player')) {
      return Response.json({ playabilityStatus:{status:'LOGIN_REQUIRED',
        reason:"Sign in to confirm you're not a bot"} })
    }
    videoFetch = true
    throw new Error('Direct fetch unexpectedly received a Tor-signed media URL: '+u)
  }

  const prepared = await prepareYouTubeVideo(id,{quality:'360'})
  try {
    assert.equal(prepared.candidate.height,360)
    assert.equal(prepared.candidate.transport,'tor')
    assert.equal(prepared.mimetype,'video/mp4')
    assert.equal(prepared.size,1024)
    assert.equal((await readFile(prepared.file)).length,1024)
  } finally {
    await prepared.cleanup()
  }
  assert.equal(videoFetch,false,'media bytes must go through yt-dlp Tor, not direct fetch')
  const traceRows = (await readFile(calls,'utf8')).trim().split('\\n').map(JSON.parse)
  assert(traceRows.some(row => !row.tor && row.metadata), 'direct metadata is tried first')
  assert(traceRows.some(row => row.tor && row.metadata), 'Tor metadata runs only as fallback')
  assert(traceRows.filter(row => !row.metadata).every(row => row.tor),
    'Tor-resolved downloads must not make unproxied yt-dlp download requests')

  process.env.MSCC_YOUTUBE_TOR_FALLBACK = '0'
  await assert.rejects(resolveYouTubeWithDlp(id),isYouTubeAccessChallenge)
  console.log('PASS Azure direct YouTube botgate falls back to Tor metadata and full media delivery; opt-out works')
} finally {
  for(const [key,value] of Object.entries({
    MSCC_YOUTUBE_YTDLP_BIN:previous.bin,
    MSCC_YOUTUBE_TEST_TRACE:previous.trace,
    MSCC_YOUTUBE_TOR_FALLBACK:previous.opt,
  })) {
    if(value === undefined) delete process.env[key]
    else process.env[key] = value
  }
  globalThis.fetch = previous.fetch
  await rm(dir,{recursive:true,force:true})
}
