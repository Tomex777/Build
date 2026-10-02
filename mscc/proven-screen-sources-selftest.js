import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { openSharedStorage } from './shared-storage.js'
import { SourceRegistry } from './source-registry.js'

const dir = await mkdtemp(join(tmpdir(), 'mscc-proven-sources-'))
const storage = await openSharedStorage({
  file:join(dir, 'shared.sqlite'),
  ttlMs:86400000,
  maxMessagesPerAccount:100,
})

try {
  const registry = new SourceRegistry({
    rootUrl:new URL('./sources/', import.meta.url),
    storage,
  })
  await registry.load()

  const movies = registry.list('movies').map(source => source.id)
  const tv = registry.list('tv').map(source => source.id)

  assert.deepEqual(movies, ['streamingunity', 'vaplayer', 'vixsrc'])
  assert.deepEqual(tv, ['streamingunity', 'vaplayer', 'vixsrc'])

  for (const capability of ['movies', 'tv']) {
    assert.equal(registry.mode(capability), 'user-choice')
    for (const id of ['streamingunity', 'vaplayer', 'vixsrc']) {
      const source = registry.get(capability, id)
      assert(source, `${capability} source ${id} must be registered`)
      assert.equal(typeof source.run, 'function')
    }
    assert.equal(registry.get(capability, 'tfpdl'), null)
    assert.equal(registry.get(capability, 'thenkiri'), null)
  }

  console.log('PASS proven Movies/TV source registry: StreamingUnity, VaPlayer, VixSrc')
} finally {
  storage.close()
  await rm(dir, { recursive:true, force:true })
}
