import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import { runLyricsCommand } from './lyrics-flow.js'
import {
  durationSeconds,
  parseLyricsRecord,
  scoreLyricsCandidate,
  splitLyricsText,
} from './utils/lyrics.js'

assert.equal(durationSeconds('3:16'), 196)
assert.equal(durationSeconds('1:02:03'), 3723)
assert.equal(durationSeconds(245), 245)

const synced = parseLyricsRecord({
  id:1,
  trackName:'Test Song',
  artistName:'Test Artist',
  duration:180,
  syncedLyrics:'[00:01.00]First line\n[00:02.50]Second line',
})
assert.ok(synced)
assert.equal(synced.plain, 'First line\nSecond line')

const exactCandidate = parseLyricsRecord({
  id:2,
  trackName:'N95',
  artistName:'Kendrick Lamar',
  albumName:'Mr. Morale & The Big Steppers',
  duration:196,
  plainLyrics:'line one\nline two',
})
const wrongCandidate = parseLyricsRecord({
  id:3,
  trackName:'N95 Remix',
  artistName:'Different Artist',
  duration:260,
  plainLyrics:'other lyrics',
})
const track = {
  title:'N95',
  artist:'Kendrick Lamar',
  album:'Mr. Morale & The Big Steppers',
  duration:'3:16',
}
assert.ok(
  scoreLyricsCandidate(track, exactCandidate, 'N95 Kendrick Lamar') >
  scoreLyricsCandidate(track, wrongCandidate, 'N95 Kendrick Lamar')
)

const longLyrics = Array.from({ length:120 }, (_, i) => `Line ${i + 1} ${'x'.repeat(32)}`).join('\n')
const chunks = splitLyricsText(longLyrics, 500)
assert.ok(chunks.length > 1)
assert.ok(chunks.every(chunk => chunk.length <= 500))

const originalFetch = globalThis.fetch
const requests = []
globalThis.fetch = async urlValue => {
  const url = new URL(String(urlValue))
  requests.push(url)
  if (url.pathname === '/api/get') {
    assert.equal(url.searchParams.get('track_name'), 'N95')
    assert.equal(url.searchParams.get('artist_name'), 'Kendrick Lamar')
    assert.equal(url.searchParams.get('duration'), '196')
    return new Response(JSON.stringify({
      id:7,
      trackName:'N95',
      artistName:'Kendrick Lamar',
      albumName:'Mr. Morale & The Big Steppers',
      duration:196,
      plainLyrics:'First lyric line\nSecond lyric line',
      syncedLyrics:null,
      instrumental:false,
    }), { status:200, headers:{ 'content-type':'application/json' } })
  }
  throw new Error('Unexpected lyrics URL: ' + url)
}

const replies = []
const ctx = {
  publicPrefix:'.',
  reply:async value => { replies.push(String(value)); return value },
  executeSource:async ({ capability, payload }) => {
    assert.equal(capability, 'music')
    assert.equal(payload.action, 'search')
    return {
      status:'ok',
      source:{ id:'music', name:'Music' },
      result:{
        items:[{
          id:'video-id',
          title:'N95',
          artist:'Kendrick Lamar',
          album:'Mr. Morale & The Big Steppers',
          duration:'3:16',
        }],
      },
    }
  },
}

try {
  await runLyricsCommand(ctx, { args:['N95', 'Kendrick', 'Lamar'] })
} finally {
  globalThis.fetch = originalFetch
}

assert.equal(requests.length, 1)
assert.ok(replies[0].includes('*Lyrics*'))
assert.ok(replies[0].includes('N95 — Kendrick Lamar'))
assert.ok(replies[0].includes('First lyric line'))
assert.ok(replies[0].includes('Second lyric line'))

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})
for (const key of ['lyrics', 'lyric']) {
  const command = registry.commands.get(key)
  assert.ok(command, `Missing lyrics command/alias: ${key}`)
  assert.equal(command.name, 'lyrics')
  assert.equal(command.capability, 'music')
  assert.equal(command.ownerOnly === true, false)
  assert.equal(command.adminOnly === true, false)
}

console.log('lyrics resolver and command self-test passed')
