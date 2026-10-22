import assert from 'node:assert/strict'
import command from './commands/owner/channelai.js'

const channel = '120363456789012345@newsletter'
const events = []
const entries = new Map()
const metadata = async (mode, id) => {
  events.push('metadata:' + mode)
  return { id:channel, viewer_metadata:{ role:'ADMIN' } }
}
const basic = {
  args:['on', channel],
  message:{ key:{ remoteJid:'2341234567890@s.whatsapp.net' } },
  isSupremeOwner:true,
  botProfile:{ id:'josiah' },
  account:{ id:'bot-josiah', sock:{ newsletterMetadata:metadata } },
  channelAiGet: id => entries.get(id) || { enabled:false, accountId:'' },
  channelAiSet: (id, enabled, accountId) => {
    const value = { enabled, accountId:enabled ? accountId : '' }
    entries.set(id, value)
    return value
  },
  reply:text => { events.push('reply:' + text); return text },
}
await command.run({ ...basic, isSupremeOwner:false })
assert.equal(entries.size, 0, 'No user/admin/sudo may opt channels in')
await command.run({ ...basic, message:{ key:{ remoteJid:'123@g.us' } }, groupKey:'123@g.us' })
assert.equal(entries.size, 0, 'No group may opt channels in')
await command.run({ ...basic, message:{ key:{ remoteJid:channel } } })
assert.equal(entries.size, 0, 'No channel post can change its own permission')
await command.run({ ...basic, account:{ id:'bot-josiah', sock:{ newsletterMetadata:async () => ({ id:channel, viewer_metadata:{role:'SUBSCRIBER'} }) } } })
assert.equal(entries.size, 0, 'Bot without channel admin cannot enable AI')
await command.run(basic)
assert.equal(entries.get(channel)?.enabled, true)
assert.equal(entries.get(channel)?.accountId, 'bot-josiah')
await command.run({ ...basic, args:['status', channel] })
assert.ok(events.some(e => e.includes('Channel AI: *ON*')))
await command.run({ ...basic, args:['off', channel] })
assert.equal(entries.get(channel)?.enabled, false)
await command.run({ ...basic, args:['on', 'https://whatsapp.com/channel/AbCdEfGhIjKlMnOpQr'] })
assert.equal(entries.get(channel)?.enabled, true, 'Owner link resolves to exact WhatsApp channel JID')
console.log('Channel AI DM control: PASS')
