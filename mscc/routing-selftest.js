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

store.assignProfile('A', 'control')
store.assignProfile('B', 'josiah')
store.assignProfile('C', 'nami')
store.assignProfile('D', 'mimi')

const make = id => ({ id, enabled: true, connected: true, sock: {} })
const accounts = [make('A'), make('B'), make('C'), make('D')]
const members = new Set(['A', 'B', 'C', 'D'])
const choose = capability => chooseGroupExecutor({
  groupJid: 'group-1@g.us',
  capability,
  accounts,
  isMember: async account => members.has(account.id),
  scoreFor: (id, cap) => store.capabilityScore(id, cap),
  getSticky: (group, cap) => store.getGroupRoute(group, cap),
  setSticky: (group, cap, id) => store.setGroupRoute(group, cap, id),
})

if (canExecuteDirect({ accountId:'A', capability:'general', scoreFor:(id,cap)=>store.capabilityScore(id,cap) })) {
  throw new Error('Account A control session must not run public commands')
}
if ((await choose('general')) !== 'B') throw new Error('Josiah must be the public general/universal bot')
if ((await choose('anime')) !== 'C') throw new Error('Nami must take anime precedence over HEX')
if ((await choose('manga')) !== 'C') throw new Error('Nami must take manga precedence over HEX')
if ((await choose('music')) !== 'D') throw new Error('MiMi must take music precedence over HEX')
if ((await choose('movies')) !== 'D') throw new Error('MiMi must take movies precedence over HEX')
if ((await choose('tv')) !== 'D') throw new Error('MiMi must take TV precedence over HEX')

members.delete('C')
if ((await choose('anime')) !== 'B') throw new Error('Josiah must fall back to anime when Nami is absent')

members.clear()
members.add('C')
if ((await choose('general')) !== 'C') throw new Error('Nami must still run shared/general commands when it is the only public bot present')

store.sharedSet('user', '234000000001', { animeCount: 2 })
if (store.sharedGet('user', '234000000001')?.animeCount !== 2) throw new Error('Shared disk data failed')

store.putMessage({ accountId:'A', chatJid:'g@g.us', messageId:'m1', participantJid:'p@lid', data:'abc' })
if (store.findMessage({ accountId:'A', messageId:'m1', chatJid:'g@g.us', participantJid:'p@lid' }) !== 'abc') {
  throw new Error('Disk-backed message lookup failed')
}

store.close()
console.log('MSCC routing/storage self-test OK')
