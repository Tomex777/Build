import assert from 'node:assert/strict'
import { stat } from 'node:fs/promises'
import { prepareYouTubeVideo, resolveYouTubeVideo } from './youtube-video.js'

const id = 'abcdefABC12'
const originalFetch = globalThis.fetch
const calls = { watch:0, player:0, media:0 }
const mediaUrl = 'https://stream.example.invalid/format18.mp4'
const bytes = Buffer.alloc(2048, 7)
try {
  globalThis.fetch = async (url, options = {}) => {
    const location = String(url)
    if (location.startsWith('https://www.youtube.com/watch?')) {
      calls.watch++
      return new Response('<html>"INNERTUBE_API_KEY":"fixture-key","INNERTUBE_CONTEXT_CLIENT_VERSION":"1.0"</html>')
    }
    if (location.includes('/youtubei/v1/player')) {
      calls.player++
      return Response.json({
        playabilityStatus:{ status:'OK' },
        videoDetails:{ title:'Fixture YouTube Clip', author:'Test Channel', lengthSeconds:'20' },
        streamingData:{ formats:[{
          itag:18, url:mediaUrl,
          mimeType:'video/mp4; codecs="avc1.4d401e, mp4a.40.2"',
          height:360, audioChannels:2, bitrate:500000, contentLength:String(bytes.length),
        }] },
      })
    }
    if (location === mediaUrl) {
      calls.media++
      return new Response(bytes, { status:200, headers:{
        'content-type':'video/mp4', 'content-length':String(bytes.length),
      } })
    }
    throw new Error('Unexpected fetch: ' + location)
  }

  const selected = await resolveYouTubeVideo(id)
  assert.equal(selected.candidates[0].kind, 'progressive')
  assert.equal(calls.watch, 1)
  const again = await resolveYouTubeVideo(id)
  assert.strictEqual(again, selected)
  assert.equal(calls.watch, 1)

  const output = await prepareYouTubeVideo(id, { quality:'360' })
  try {
    assert.equal(output.details.title, 'Fixture YouTube Clip')
    assert.equal(output.candidate.height, 360)
    assert.equal((await stat(output.file)).size, bytes.length)
  } finally {
    await output.cleanup()
  }
  assert.equal(calls.watch, 1, 'quality selection/download must not repeat player resolution')
  assert.equal(calls.player, 3, 'all player clients must only be queried during first resolution')
  assert.equal(calls.media, 2, 'signed stream probed once then downloaded once')
  console.log('PASS signed URL reuse between quality menu and video download')
} finally {
  globalThis.fetch = originalFetch
}
