import assert from 'node:assert/strict'
import { mkdtemp, rm, chmod, writeFile, stat } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import youtube from './sources/music/youtube.js'

const folder = await mkdtemp(join(tmpdir(), 'mscc-youtube-music-test-'))
const originalFetch = globalThis.fetch
const previousBinary = process.env.MSCC_YOUTUBE_YTDLP_BIN
const videoId = 'dQw4w9WgXcQ'
const fetchLog = []
let temporaryPath = ''
try {
  const binary = join(folder, 'mock-yt-dlp')
  const script = `#!/usr/bin/env node
const { writeFileSync } = require('node:fs')
const { resolve } = require('node:path')
const args = process.argv.slice(2)
const pattern = args[args.indexOf('-o') + 1]
const output = resolve(pattern.replace('%(ext)s', 'm4a'))
writeFileSync(output, Buffer.alloc(4096, 1))
console.log(output)
`
  await writeFile(binary, script, { mode:0o700 })
  await chmod(binary, 0o700)
  process.env.MSCC_YOUTUBE_YTDLP_BIN = binary

  globalThis.fetch = async (url, options = {}) => {
    const target = String(url)
    fetchLog.push({ url:target.split('?')[0], range:options.headers?.range || '' })
    if (target.includes('/watch?v=')) {
      return new Response('{"INNERTUBE_API_KEY":"test-key","INNERTUBE_CONTEXT_CLIENT_VERSION":"test-version"}')
    }
    if (target.includes('/youtubei/v1/player')) {
      return new Response(JSON.stringify({
        playabilityStatus:{ status:'OK' },
        streamingData:{ adaptiveFormats:[{
          mimeType:'audio/mp4; codecs="mp4a.40.2"',
          url:'https://cdn.example.invalid/audio.m4a',
          bitrate:128000,
        }] },
      }), { headers:{'content-type':'application/json'} })
    }
    if (target === 'https://cdn.example.invalid/audio.m4a') {
      return new Response(Buffer.alloc(4096, 2), {
        status:options.headers?.range ? 206 : 200,
        headers:{'content-type':'audio/mp4', 'content-length':'4096'},
      })
    }
    throw new Error('Unexpected network request: ' + target)
  }

  const chosen = { id:videoId, title:'Test Song', artist:'Test Artist' }
  const firstSends = []
  const initial = await youtube.run({
    action:'download', track:chosen,
    context:{ requestedDelivery:'audio', send:async payload => {
      const path = payload.audio?.url
      assert(path?.startsWith('/'), 'Music must be sent as verified local media, not a remote URL')
      assert.equal((await stat(path)).size, 4096)
      temporaryPath = path
      firstSends.push(payload)
    } },
  })
  assert.equal(initial.delivered, true)
  assert.equal(firstSends.length, 1)
  assert.equal(firstSends[0].mimetype, 'audio/mp4')
  assert.equal(firstSends[0].ptt, false)
  assert.equal(fetchLog.length, 0, 'Successful yt-dlp download should not re-fetch signed URLs')
  await assert.rejects(stat(temporaryPath), { code:'ENOENT' })

  let attempts = 0
  const recovered = await youtube.run({
    action:'download', track:chosen,
    context:{ requestedDelivery:'document', send:async payload => {
      attempts += 1
      assert(payload.document?.url?.startsWith('/'))
      assert.equal((await stat(payload.document.url)).size, 4096)
      temporaryPath = payload.document.url
      if (attempts === 1) throw new Error('simulated WhatsApp send failure')
    } },
  })
  assert.equal(recovered.delivered, true)
  assert.equal(attempts, 2, 'A send failure must trigger the alternate local-media route')
  assert(fetchLog.some(row => row.range), 'Direct stream must be probed before downloading')
  assert(fetchLog.some(row => !row.range && row.url.includes('cdn.example.invalid')), 'Direct bytes must be downloaded locally')
  await assert.rejects(stat(temporaryPath), { code:'ENOENT' })

  await assert.rejects(youtube.run({
    action:'download', track:chosen,
    context:{ requestedDelivery:'audio', send:async () => { throw new Error('send remains offline') } },
  }), { code:'YOUTUBE_MUSIC_DOWNLOAD_FAILED' })

  console.log('PASS YouTube music: local audio, WhatsApp send retry, verified direct fallback, visible rejection, cleanup')
} finally {
  globalThis.fetch = originalFetch
  if (previousBinary === undefined) delete process.env.MSCC_YOUTUBE_YTDLP_BIN
  else process.env.MSCC_YOUTUBE_YTDLP_BIN = previousBinary
  await rm(folder, { recursive:true, force:true })
}
