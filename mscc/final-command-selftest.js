import assert from 'node:assert/strict'
import { dispatchNamespacedCommand, loadCommands } from './command-registry.js'
import aboutCommand from './commands/general/about.js'
import setBotNameCommand from './commands/owner/setbotname.js'
import sudoCommand from './commands/owner/sudo.js'

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})

const expected = [
  'about','meme','joke','quote','caption','wanted','jail','wasted','achievement',
  'broadcast','setbotname','setbotpp','sudo',
]
for (const name of expected) assert.ok(registry.commands.has(name), 'Missing command ' + name)

for (const name of ['broadcast','setbotname','setbotpp','sudo']) {
  assert.equal(registry.commands.get(name).ownerOnly, true, name + ' must be owner-only')
}

let aboutText = ''
await aboutCommand.run({
  reply:async value => { aboutText = String(value); return value },
  appVersion:'2.3.0',
})
assert.match(aboutText, /Night/)
assert.match(aboutText, /Josia/)
assert.match(aboutText, /Nami/)
assert.match(aboutText, /MiMi/)
assert.doesNotMatch(aboutText, /MSCC/i)

let renamed = ''
let renameReply = ''
await setBotNameCommand.run({
  args:['Josia','Prime'],
  account:{ sock:{ updateProfileName:async value => { renamed = value } } },
  reply:async value => { renameReply = String(value); return value },
})
assert.equal(renamed, 'Josia Prime')
assert.match(renameReply, /updated/i)

let denied = ''
await sudoCommand.run({
  args:['list'],
  isSupremeOwner:false,
  reply:async value => { denied = String(value); return value },
})
assert.match(denied, /primary Night owner/i)

let publicRan = false
const publicCommand = {
  name:'ownercmd',
  ownerOnly:true,
  async run() { publicRan = true },
}
const privateCommand = {
  name:'secret',
  async run() { throw new Error('private command should not run') },
}
const publicRegistry = {
  commands:new Map([['ownercmd',publicCommand]]),
  canonical:[publicCommand],
}
const privateRegistry = {
  commands:new Map([['secret',privateCommand]]),
  canonical:[privateCommand],
}
const baseContext = {
  publicPrefix:'.',
  publicCommandsEnabled:true,
  isPublicOwner:true,
  isSessionOwner:false,
  isSupremeOwner:false,
  privateControl:false,
  botProfile:{ id:'main' },
  shouldExecutePublicCommand:async () => true,
  reply:async () => {},
}
assert.equal(await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.ownercmd',
  context:baseContext,
}), true)
assert.equal(publicRan, true)

assert.equal(await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.secret',
  context:baseContext,
}), false)

console.log('PASS final Night commands, branding, owner flags, and sudo/private isolation')
