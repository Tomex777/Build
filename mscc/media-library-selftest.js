import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { openSharedStorage } from './shared-storage.js'
import {
  addToLibraryAction,
  canonicalLibraryIdentity,
  libraryStatusLine,
  normalizeLibraryType,
} from './media-library.js'

const dir = await mkdtemp(join(tmpdir(), 'mscc-library-'))
const storage = await openSharedStorage({
  file:join(dir, 'test.sqlite'),
  ttlMs:3600000,
  maxMessagesPerAccount:100,
})

try {
  const user = '2348000000000'
  const items = [
    { mediaType:'anime', provider:'anilist', externalId:'1', itemKey:'anime:anilist:1', title:'Anime A' },
    { mediaType:'manga', provider:'anilist', externalId:'2', itemKey:'manga:anilist:2', title:'Manga B' },
    { mediaType:'movie', provider:'tmdb', externalId:'3', itemKey:'movie:tmdb:3', title:'Movie C' },
    { mediaType:'tv', provider:'tmdb', externalId:'4', itemKey:'tv:tmdb:4', title:'TV D' },
  ]

  for (const item of items) storage.putLibraryItem(user, item)

  const listed = storage.listLibraryItems(user)
  assert.equal(listed.length, 4)
  assert.deepEqual(storage.librarySummary(user), {
    total:4,
    anime:1,
    manga:1,
    movie:1,
    tv:1,
    watching:0,
  })
  assert.deepEqual(listed.map(item => item.slot), [1,2,3,4])
  assert.equal(storage.listLibraryItems(user, 'anime').length, 1)
  assert.equal(storage.libraryItemBySlot(user, 3).title, 'Movie C')

  storage.setLibraryWatch(user, 'movie:tmdb:3', true)
  assert.equal(storage.libraryItemBySlot(user, 3).watchReleases, true)
  assert.equal(storage.librarySummary(user).watching, 1)
  const watchedItems = storage.listWatchedLibraryItems()
  assert.equal(watchedItems.length, 1)
  assert.equal(watchedItems[0].userKey, user)
  assert.equal(watchedItems[0].itemKey, 'movie:tmdb:3')

  storage.setLibraryReleaseState(user, 'movie:tmdb:3', {
    cursor:{ kind:'movie', number:0, releasedAtMs:1234 },
    primedAtMs:100,
  })
  assert.equal(
    storage.getLibraryReleaseState(user, 'movie:tmdb:3').cursor.releasedAtMs,
    1234,
  )

  storage.setLibraryWatch(user, 'movie:tmdb:3', false)
  assert.equal(storage.getLibraryReleaseState(user, 'movie:tmdb:3'), null)
  storage.setLibraryWatch(user, 'movie:tmdb:3', true)

  storage.removeLibraryItem(user, 'manga:anilist:2')
  assert.equal(storage.listLibraryItems(user).length, 3)
  assert.equal(storage.libraryItemBySlot(user, 2), null)

  const later = storage.putLibraryItem(user, {
    mediaType:'manga',
    provider:'anilist',
    externalId:'5',
    itemKey:'manga:anilist:5',
    title:'Manga E',
  })
  assert.equal(later.slot, 5)

  assert.equal(normalizeLibraryType('series'), 'tv')
  assert.equal(canonicalLibraryIdentity('anime', { anilistId:9 }).key, 'anime:anilist:9')
  assert.equal(canonicalLibraryIdentity('movie', { tmdbId:10 }).key, 'movie:tmdb:10')

  const ctx = {
    libraryGet:key => storage.getLibraryItem(user, key),
  }

  const unsaved = addToLibraryAction(ctx, 'anime', { anilistId:99 }, { prefix:'.' })
  assert.equal(unsaved.id, '.anime ~library-add 99')

  storage.putLibraryItem(user, {
    mediaType:'anime',
    provider:'anilist',
    externalId:'99',
    itemKey:'anime:anilist:99',
    title:'Saved Anime',
  })

  assert.equal(addToLibraryAction(ctx, 'anime', { anilistId:99 }, { prefix:'.' }), null)
  assert.equal(libraryStatusLine(ctx, 'anime', { anilistId:99 }), '✓ In Library')

  storage.setLibraryWatch(user, 'anime:anilist:99', true)
  assert.equal(
    libraryStatusLine(ctx, 'anime', { anilistId:99 }),
    '✓ In Library · 🔔 Watching releases',
  )

  console.log('PASS media library persistence and conditional Library quick reply')
} finally {
  storage.close()
  await rm(dir, { recursive:true, force:true })
}
