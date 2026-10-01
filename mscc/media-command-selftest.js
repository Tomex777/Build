import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory: true,
})

const expected = new Map([
  ['sticker', 'sticker'],
  ['s', 'sticker'],
  ['toimg', 'toimg'],
  ['toimage', 'toimg'],
  ['simage', 'toimg'],
  ['togif', 'togif'],
  ['tovideo', 'tovideo'],
  ['tovid', 'tovideo'],
])

for (const [key, canonical] of expected) {
  const command = registry.commands.get(key)
  assert.ok(command, `Missing media command/alias: ${key}`)
  assert.equal(command.name, canonical, `Unexpected canonical command for ${key}`)
  assert.equal(command.capability, 'media', `Media command ${key} escaped the media capability`)
}

for (const name of ['sticker', 'toimg', 'togif', 'tovideo']) {
  const command = registry.commands.get(name)
  assert.equal(command.ownerOnly === true, false, `${name} unexpectedly became owner-only`)
  assert.equal(command.adminOnly === true, false, `${name} unexpectedly became admin-only`)
}

console.log('media command registry self-test passed')
