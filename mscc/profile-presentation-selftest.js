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
assert(profileHeader('nami').includes('𝙽𝙰𝙼𝙸'))
assert(profileHeader('josiah').includes('𝙹𝙾𝚂𝙸𝙰𝙷'))

const commands = [
  { name:'anime', capability:'anime' },
  { name:'manga', capability:'manga' },
  { name:'ping', capability:'general' },
  { name:'movie', capability:'movies' },
  { name:'music', capability:'music' },
]
const nami = visibleCommandsForProfile('nami', commands).map(command => command.name)
assert.deepEqual(nami, ['anime','manga','ping'])
assert.equal(visibleCommandsForProfile('josiah', commands).length, commands.length)

const seen = new Set()
for (let i = 0; i < 10; i += 1) seen.add(menuOpener('nami'))
assert(seen.size > 1)

const first = groupIntro('nami', { returning:false })
const again = groupIntro('nami', { returning:true })
assert(first.includes('Nami'))
assert(again.length > 10)

assert.equal(await chooseProfileAsset('nami', 'menu'), '')
assert.equal(await chooseProfileAsset('nami', 'intro'), '')

console.log('PASS profile presentation selftest')
