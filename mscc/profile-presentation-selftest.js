import assert from 'node:assert/strict'
import {
  chooseProfileAsset,
  groupIntro,
  menuOpener,
  presentationFor,
  profileHeader,
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

assert.equal(await chooseProfileAsset('nami', 'menu'), '')
assert.equal(await chooseProfileAsset('nami', 'intro'), '')
assert.equal(await chooseProfileAsset('mimi', 'menu'), '')
assert.equal(await chooseProfileAsset('mimi', 'intro'), '')

console.log('PASS profile presentation selftest')
