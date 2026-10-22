import assert from 'node:assert/strict'
import { channelAiAllowed, channelAiSetting, isChannelJid, setChannelAiSetting } from './channel-ai-policy.js'

const rows = new Map()
const store = {
  sharedGet(ns, id) { return rows.get(ns + ':' + id) },
  sharedSet(ns, id, value) { rows.set(ns + ':' + id, value) },
}
const channel = '120363456789012345@newsletter'
const another = '120363456789012346@newsletter'
assert.equal(isChannelJid(channel), true)
for (const jid of [another, '123@g.us', '123@s.whatsapp.net', 'status@broadcast', 'all@newsletter', '', null]) {
  assert.equal(channelAiAllowed(store, jid, 'josiah-account'), false)
}
assert.deepEqual(channelAiSetting(store, channel), { enabled:false, accountId:'' })
assert.equal(channelAiAllowed(store, channel, 'josiah-account'), false)
assert.throws(() => setChannelAiSetting(null, channel, { enabled:true, accountId:'x' }), /Persistent/)
assert.throws(() => setChannelAiSetting(store, channel, { enabled:true }), /specific/)
assert.throws(() => setChannelAiSetting(store, '123@g.us', { enabled:true, accountId:'x' }), /Invalid/)
setChannelAiSetting(store, channel, { enabled:true, accountId:'josiah-account' })
assert.equal(channelAiAllowed(store, channel, 'josiah-account'), true)
assert.equal(channelAiAllowed(store, channel, 'nami-account'), false)
assert.equal(channelAiAllowed(store, another, 'josiah-account'), false)
rows.set('channel-ai-opt-in-v1:' + another, { enabled:'true', accountId:'nami-account' })
assert.equal(channelAiAllowed(store, another, 'nami-account'), false, 'String true must fail closed')
setChannelAiSetting(store, channel, { enabled:false, accountId:'josiah-account' })
assert.equal(channelAiAllowed(store, channel, 'josiah-account'), false)
console.log('Channel AI opt-in: PASS')
