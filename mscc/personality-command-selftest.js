import assert from 'node:assert/strict'
import { dispatchCommand } from './command-registry.js'
import { profileCommandMenuText } from './profile-command-menu.js'

const commands = [
  { name:'nami', capability:'anime', usage:'.nami' },
  { name:'anime', capability:'anime', usage:'.anime <title>' },
  { name:'manga', capability:'manga', usage:'.manga <title>' },
  { name:'mimi', capability:'music', usage:'.mimi' },
  { name:'song', capability:'music', usage:'.song <query>' },
  { name:'movie', capability:'movies', usage:'.movie <title>' },
  { name:'tv', capability:'tv', usage:'.tv <title>' },
  { name:'ping', capability:'general', usage:'.ping' },
]

const namiMenu = profileCommandMenuText('nami', commands, '.')
assert(namiMenu.includes('.nami'))
assert(namiMenu.includes('.anime'))
assert(namiMenu.includes('.manga'))
assert(!namiMenu.includes('.mimi'))
assert(!namiMenu.includes('.movie'))
assert(!namiMenu.includes('.ping'))

const mimiMenu = profileCommandMenuText('mimi', commands, '.')
assert(mimiMenu.includes('.mimi'))
assert(mimiMenu.includes('.song'))
assert(mimiMenu.includes('.movie'))
assert(mimiMenu.includes('.tv'))
assert(!mimiMenu.includes('.nami'))
assert(!mimiMenu.includes('.anime'))
assert(!mimiMenu.includes('.ping'))

const profileCommand = {
  name:'nami',
  profileOnly:'nami',
  async run(ctx) { await ctx.reply('NAMI MENU') },
}
const registry = {
  commands:new Map([['nami', profileCommand]]),
  canonical:[profileCommand],
}

const replies = []
const baseContext = {
  publicCommandsEnabled:true,
  reply:async value => replies.push(String(value)),
}

assert.equal(await dispatchCommand(registry, '.nami', {
  ...baseContext,
  botProfile:{ id:'josiah' },
}, { scope:'public', prefix:'.' }), false)
assert.equal(replies.length, 0)

assert.equal(await dispatchCommand(registry, '.nami', {
  ...baseContext,
  botProfile:{ id:'nami' },
}, { scope:'public', prefix:'.' }), true)
assert.deepEqual(replies, ['NAMI MENU'])

console.log('PASS personality command menus and profile gate')
