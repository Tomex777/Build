import { rm } from 'node:fs/promises'
import { openSharedStorage } from './shared-storage.js'
import { chooseGroupExecutor, canExecuteDirect } from './bot-routing.js'

const root = '/tmp/mscc-routing-selftest'
await rm(root, { recursive: true, force: true })
const store = await openSharedStorage({
  file: root + '/shared.sqlite',
  ttlMs: 24 * 3600000,
  maxMessagesPerAccount: 100,
})

store.createProfile('nami', 'Nami')
store.setCapability('nami', 'anime', 100)
store.assignProfile('B', 'nami')

store.createProfile('mira', 'Mira')
store.setCapability('mira', 'youtube', 100)
store.setCapability('mira', 'movies', 100)
store.assignProfile('C', 'mira')

const make = id => ({ id, enabled: true, connected: true, sock: {} })
const accounts = [make('A'), make('B'), make('C')]
const members = new Set(['A', 'B', 'C'])
const choose = capability => chooseGroupExecutor({
  groupJid: 'group-1@g.us',
  capability,
  accounts,
  isMember: async account => members.has(account.id),
  scoreFor: (id, cap) => store.capabilityScore(id, cap),
  getSticky: (group, cap) => store.getGroupRoute(group, cap),
  setSticky: (group, cap, id) => store.setGroupRoute(group, cap, id),
})

if ((await choose('general')) !== 'A') throw new Error('Main must win general commands when specialists coexist')
if ((await choose('anime')) !== 'B') throw new Error('Nami must take anime precedence over Main')
if ((await choose('youtube')) !== 'C') throw new Error('Mira must take YouTube precedence over Main')

members.delete('B')
if ((await choose('anime')) !== 'A') throw new Error('Main must fall back to anime when Nami is absent')

members.clear()
members.add('B')
if ((await choose('general')) !== 'B') throw new Error('A specialist must still run shared commands when it is the only bot present')
if (!canExecuteDirect({ accountId:'B', capability:'general', scoreFor:(id, cap) => store.capabilityScore(id, cap) })) {
  throw new Error('Universal specialist direct fallback failed')
}

store.createProfile('strict', 'Strict', { universal:false })
store.setCapability('strict', 'games', 100)
store.assignProfile('D', 'strict')
if (canExecuteDirect({ accountId:'D', capability:'anime', scoreFor:(id, cap) => store.capabilityScore(id, cap) })) {
  throw new Error('Strict specialist ran an unassigned capability')
}

store.sharedSet('user', '234000000001', { animeCount: 2 })
if (store.sharedGet('user', '234000000001')?.animeCount !== 2) throw new Error('Shared disk data failed')

store.putMessage({ accountId:'A', chatJid:'g@g.us', messageId:'m1', participantJid:'p@lid', data:'abc' })
if (store.findMessage({ accountId:'A', messageId:'m1', chatJid:'g@g.us', participantJid:'p@lid' }) !== 'abc') {
  throw new Error('Disk-backed message lookup failed')
}

store.close()
console.log('MSCC routing/storage self-test OK')
