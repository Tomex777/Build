import assert from 'node:assert/strict'
import {
  createReleaseWatcher,
  isNewerRelease,
  releaseFingerprint,
  releaseNotificationText,
} from './release-watcher.js'

assert.equal(
  releaseNotificationText(
    { title:'Bleach' },
    { kind:'episode', season:0, number:14 },
  ),
  'Latest episode of Bleach: Episode 14',
)
assert.equal(
  releaseNotificationText(
    { title:'The Boys' },
    { kind:'episode', season:4, number:8 },
  ),
  'Latest episode of The Boys: Season 4, Episode 8',
)
assert.equal(
  releaseNotificationText(
    { title:'Dune: Part Three' },
    { kind:'movie', number:1 },
  ),
  'Dune: Part Three is out now.',
)

assert.equal(
  isNewerRelease(
    { kind:'episode', season:0, number:13 },
    { kind:'episode', season:0, number:14 },
  ),
  true,
)
assert.equal(
  isNewerRelease(
    { kind:'episode', season:4, number:8 },
    { kind:'episode', season:4, number:8 },
  ),
  false,
)
assert.equal(
  isNewerRelease(
    { kind:'episode', season:3, number:10 },
    { kind:'episode', season:4, number:1 },
  ),
  true,
)
assert.equal(releaseFingerprint({ kind:'episode', season:4, number:2 }), 'episode:4:2')

const states = new Map()
const watched = [{
  userKey:'2348000000000',
  itemKey:'anime:anilist:1',
  externalId:'1',
  mediaType:'anime',
  title:'Bleach',
  watchReleases:true,
}]
let current = { kind:'episode', season:0, number:13, source:'anilist' }
let now = 1000
let sendOk = true
const sent = []

const storage = {
  listWatchedLibraryItems:() => watched,
  getLibraryReleaseState:(userKey, itemKey) => states.get(userKey + '|' + itemKey) || null,
  setLibraryReleaseState:(userKey, itemKey, value) => {
    states.set(userKey + '|' + itemKey, structuredClone(value))
    return value
  },
}

const watcher = createReleaseWatcher({
  storage,
  now:() => now,
  resolveReleaseState:async () => structuredClone(current),
  sendDm:async (item, text) => {
    sent.push({ userKey:item.userKey, text })
    return sendOk
  },
})

const prime = await watcher.prime(watched[0])
assert.equal(prime.ok, true)
assert.equal(sent.length, 0)
assert.equal(states.get('2348000000000|anime:anilist:1').cursor.number, 13)

now += 1000
await watcher.checkOnce()
assert.equal(sent.length, 0)

current = { kind:'episode', season:0, number:14, source:'anilist' }
now += 1000
const release = await watcher.checkOnce()
assert.equal(sent.length, 1)
assert.equal(sent[0].userKey, '2348000000000')
assert.equal(sent[0].text, 'Latest episode of Bleach: Episode 14')
assert.equal(release[0].notified, true)
assert.equal(states.get('2348000000000|anime:anilist:1').cursor.number, 14)

now += 1000
await watcher.checkOnce()
assert.equal(sent.length, 1)

current = { kind:'episode', season:0, number:15, source:'anilist' }
sendOk = false
now += 1000
const failed = await watcher.checkOnce()
assert.equal(failed[0].reason, 'send-failed')
assert.equal(states.get('2348000000000|anime:anilist:1').cursor.number, 14)

sendOk = true
now += 1000
await watcher.checkOnce()
assert.equal(sent.at(-1).text, 'Latest episode of Bleach: Episode 15')
assert.equal(states.get('2348000000000|anime:anilist:1').cursor.number, 15)

console.log('PASS DM release watcher baseline, progression, retry, and dedupe')
