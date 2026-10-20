import assert from 'node:assert/strict'
import { mkdtemp, open, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { prepareCommandMediaPayload } from './utils/whatsapp/prepare-command-media-payload.js'
import { loadCommands } from './command-registry.js'

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory: true,
})

const expected = new Map([
  ['sticker', 'sticker'],
  ['s', 'sticker'],
  ['take', 'take'],
  ['ps', 'ps'],
  ['toimg', 'toimg'],
  ['toimage', 'toimg'],
  ['simage', 'toimg'],
  ['togif', 'togif'],
  ['tovideo', 'tovideo'],
  ['tovid', 'tovideo'],
  ['sfx', 'sfx'],
  ['dl', 'dl'],
  ['social', 'dl'],
  ['tt', 'dl'],
  ['tiktok', 'dl'],
  ['ig', 'dl'],
  ['instagram', 'dl'],
  ['fb', 'dl'],
  ['facebook', 'dl'],
  ['xdl', 'dl'],
  ['twitterdl', 'dl'],
  ['redditdl', 'dl'],
])

for (const [key, canonical] of expected) {
  const command = registry.commands.get(key)
  assert.ok(command, `Missing media command/alias: ${key}`)
  assert.equal(command.name, canonical, `Unexpected canonical command for ${key}`)
  assert.equal(command.capability, 'media', `Media command ${key} escaped the media capability`)
}

for (const name of ['sticker', 'take', 'ps', 'toimg', 'togif', 'tovideo', 'sfx', 'dl']) {
  const command = registry.commands.get(name)
  assert.equal(command.ownerOnly === true, false, `${name} unexpectedly became owner-only`)
  assert.equal(command.adminOnly === true, false, `${name} unexpectedly became admin-only`)
}

const mediaFixtureDir = await mkdtemp(join(tmpdir(), 'mscc-media-payload-test-'))
try {
  const smallPath = join(mediaFixtureDir, 'small.txt')
  const largePath = join(mediaFixtureDir, 'large.mkv')
  await writeFile(smallPath, 'small media fixture')
  const largeFile = await open(largePath, 'w')
  try { await largeFile.truncate(8 * 1024 * 1024 + 1) } finally { await largeFile.close() }
  const prepared = await prepareCommandMediaPayload({ document:{ url:largePath }, image:{ url:smallPath } })
  assert.deepEqual(prepared.document, { url:largePath }, 'large local media must remain a local path for Baileys streaming')
  assert.ok(Buffer.isBuffer(prepared.image), 'small local media retains the legacy Buffer path')
} finally {
  await rm(mediaFixtureDir, { recursive:true, force:true })
}
console.log('PASS local media payload streams files above 8 MiB without buffering them')
console.log('media command registry self-test passed')

// Reuse the ordinary media registry CI step for exhaustive SFX FFmpeg checks.
await import('./sfx-selftest.js')
await import('./social-download-selftest.js')
