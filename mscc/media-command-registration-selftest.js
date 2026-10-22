import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'

// Resolve the real production command tree, including aliases and the
// capability inferred from each command's folder. This catches flows that
// have self-tests but have become unreachable through WhatsApp dispatch.
const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  allowMissing:true,
  capabilityFromDirectory:true,
})

const mustExist = [
  ['anime','anime','anime'],
  ['manga','manga','manga'],
  ['movie','movies','movie'],
  ['movies','movies','movie'],
  ['film','movies','movie'],
  ['tv','tv','tv'],
  ['series','tv','tv'],
  ['show','tv','tv'],
  ['shows','tv','tv'],
  ['apk','android','apk'],
  ['app','android','apk'],
  ['apps','android','apk'],
  ['android','android','apk'],
]
for (const [name, capability, canonicalName] of mustExist) {
  const command = registry.commands.get(name)
  assert.ok(command, 'registered command/alias is missing: .' + name)
  assert.equal(command.capability, capability, '.' + name + ' must retain its source-routing capability')
  assert.equal(command.name, canonicalName, '.' + name + ' must resolve to .' + canonicalName)
  assert.equal(typeof command.run, 'function', '.' + name + ' must have an executable handler')
}

// Exercise the actual registered wrappers, not only the lower-level flow
// helpers. A fixture result avoids network access while asserting the
// wrapper reaches its matching capability and asks the expected action.
const calls = []
const replies = []
const ctx = {
  publicPrefix:'.',
  listSources:() => [{ id:'fixture', name:'Fixture source' }],
  reply:async message => { replies.push(String(message)); return message },
  replyList:async value => value,
  executeSource:async request => {
    calls.push(request)
    return {
      status:'ok',
      capability:request.capability,
      source:{ id:'fixture', name:'Fixture source' },
      result:{ items:[] },
    }
  },
}

await registry.commands.get('movie').run({ ...ctx, args:['Regression','Movie'] })
assert.equal(calls.length, 1)
assert.equal(calls[0].capability, 'movies')
assert.equal(calls[0].payload.action, 'search')
assert.equal(calls[0].payload.query, 'Regression Movie')

calls.length = 0
await registry.commands.get('tv').run({ ...ctx, args:['Regression','Show'] })
assert.equal(calls.length, 1)
assert.equal(calls[0].capability, 'tv')
assert.equal(calls[0].payload.action, 'search')
assert.equal(calls[0].payload.query, 'Regression Show')

assert(replies.some(message => message.includes('No movie results found')))
assert(replies.some(message => message.includes('No TV results found')))

console.log('PASS real command registry, aliases, media capability routing, and movie/TV wrapper entry points')
