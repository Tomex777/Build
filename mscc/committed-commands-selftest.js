import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})

const required = [
  'warn','kick','promote','demote','mute','close','unmute','open',
  'antilink','antitag','antigroupmention','hidetag','grouplink','groupstatus','gcs',
  'welcome','setwelcome','goodbye','setgoodbye','aigreet','muteuser','unmuteuser',
  'poll','remind','latest','translate','define','weather','news','vision','stt','tts',
  'read','github','random','choose','timer','schedule',
]
for (const name of required) {
  assert.ok(registry.commands.has(name), 'Missing committed Night command: ' + name)
}

for (const name of [
  'warn','kick','promote','demote','mute','unmute','antilink','antitag',
  'antigroupmention','hidetag','grouplink','groupstatus','welcome','setwelcome',
  'goodbye','setgoodbye','aigreet',
]) {
  assert.equal(registry.commands.get(name).adminOnly, true, name + ' must be admin-only')
}

assert.equal(registry.commands.get('muteuser').adminOnly, undefined)
assert.equal(registry.commands.get('unmuteuser').adminOnly, undefined)
assert.equal(registry.commands.get('close').name, 'mute')
assert.equal(registry.commands.get('open').name, 'unmute')
assert.equal(registry.commands.get('gcs').name, 'groupstatus')

console.log('PASS committed group/admin and utility command registry')
