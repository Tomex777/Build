import assert from 'node:assert/strict'
import {
  chooseProfileAsset,
  groupIntro,
  menuOpener,
  presentationFor,
  profileHeader,
  readProfileAsset,
  visibleCommandsForProfile,
} from './profile-presentation.js'

assert.equal(presentationFor('nami')?.displayName, 'Nami')
assert.equal(presentationFor('mimi')?.displayName, 'MiMi')
assert.equal(presentationFor('josiah')?.displayName, 'Josia')
assert(profileHeader('nami').includes('𝙽𝙰𝙼𝙸'))
assert(profileHeader('mimi').includes('𝙼𝙸𝙼𝙸'))
assert(profileHeader('josiah').includes('𝙹𝙾𝚂𝙸𝙰'))

const commands = [
  { name:'anime', capability:'anime' },
  { name:'manga', capability:'manga' },
  { name:'ping', capability:'general' },
  { name:'movie', capability:'movies' },
  { name:'tv', capability:'tv' },
  { name:'music', capability:'music' },
]
const nami = visibleCommandsForProfile('nami', commands).map(command => command.name)
const mimi = visibleCommandsForProfile('mimi', commands).map(command => command.name)
assert.deepEqual(nami, ['anime','manga'])
assert.deepEqual(mimi, ['movie','tv','music'])
assert.equal(visibleCommandsForProfile('josiah', commands).length, commands.length)

const seen = new Set()
for (let i = 0; i < 10; i += 1) seen.add(menuOpener('nami'))
assert(seen.size > 1)

const firstNami = groupIntro('nami', { returning:false })
const firstMimi = groupIntro('mimi', { returning:false })
const firstJosia = groupIntro('josiah', { returning:false })
const again = groupIntro('nami', { returning:true })
assert(firstNami.includes('Nami'))
assert(firstMimi.includes('MiMi'))
assert(firstJosia.includes('Josia'))
assert(again.length > 10)

for (let i = 0; i < 24; i += 1) {
  assert(!groupIntro('nami', { returning:i % 2 === 1 }).includes('.menu'))
  assert(!groupIntro('mimi', { returning:i % 2 === 1 }).includes('.menu'))
  const josiaIntro = groupIntro('josiah', { returning:i % 2 === 1 })
  assert(!josiaIntro.includes('.nami'))
  assert(!josiaIntro.includes('.mimi'))
}

const menuExpected = {
  josiah:new Set(['josia-futuristic-command.webp.b64','josia-mummy-calm-baddie.webp.b64']),
  nami:new Set(['nami-red-cyber.webp.b64','nami-blue-shinigami.webp.b64']),
  mimi:new Set(['mimi-neon-tactical-hangar.webp.b64','mimi-neon-gold-executive.webp.b64']),
}

for (const profileId of ['josiah','nami','mimi']) {
  const first = await chooseProfileAsset(profileId, 'menu')
  const second = await chooseProfileAsset(profileId, 'menu')
  const firstName = first.split('/').at(-1)
  const secondName = second.split('/').at(-1)
  assert(menuExpected[profileId].has(firstName))
  assert(menuExpected[profileId].has(secondName))
  assert.notEqual(first, second)
  assert.equal(new Set([firstName, secondName]).size, 2)

  for (const asset of [first, second]) {
    const bytes = await readProfileAsset(asset)
    assert(bytes.length > 1000)
    assert.equal(bytes.subarray(0, 4).toString('ascii'), 'RIFF')
    assert.equal(bytes.subarray(8, 12).toString('ascii'), 'WEBP')
  }
}

const namiFirstAsset = await chooseProfileAsset('nami', 'intro', { returning:false })
const namiReturnAsset = await chooseProfileAsset('nami', 'intro', { returning:true })
const josiaFirstAsset = await chooseProfileAsset('josiah', 'intro', { returning:false })
const josiaReturnAsset = await chooseProfileAsset('josiah', 'intro', { returning:true })
const mimiFirstAsset = await chooseProfileAsset('mimi', 'intro', { returning:false })
const mimiReturnAsset = await chooseProfileAsset('mimi', 'intro', { returning:true })

assert(namiFirstAsset.includes('/nami/intro/first/'))
assert(namiReturnAsset.includes('/nami/intro/return/'))
assert(josiaFirstAsset.includes('/josiah/intro/first/'))
assert(josiaReturnAsset.includes('/josiah/intro/return/'))
assert(mimiFirstAsset.includes('/mimi/intro/first/'))
assert(mimiReturnAsset.includes('/mimi/intro/return/'))

assert.notEqual(namiFirstAsset, namiReturnAsset)
assert.notEqual(josiaFirstAsset, josiaReturnAsset)
assert.notEqual(mimiFirstAsset, mimiReturnAsset)

for (const asset of [
  namiFirstAsset,
  namiReturnAsset,
  josiaFirstAsset,
  josiaReturnAsset,
  mimiFirstAsset,
  mimiReturnAsset,
]) {
  assert(asset.endsWith('.webp.b64'))
  const bytes = await readProfileAsset(asset)
  assert(bytes.length > 1000)
  assert.equal(bytes.subarray(0, 4).toString('ascii'), 'RIFF')
  assert.equal(bytes.subarray(8, 12).toString('ascii'), 'WEBP')
}

console.log('PASS profile presentation selftest')
