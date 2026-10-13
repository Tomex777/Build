import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { DatabaseSync } from 'node:sqlite'
import { openSharedStorage } from './shared-storage.js'
import { aggregateActivity, activityBadges, activityReport, activitySenderKey } from './group-activity.js'
import groupstats from './commands/group/groupstats.js'
import topactive from './commands/group/topactive.js'
import myactivity from './commands/group/myactivity.js'
import activityrewards from './commands/group/activityrewards.js'

const now = Date.now()
assert.equal(activitySenderKey('234800000000@s.whatsapp.net'), '234800000000')
assert.equal(activitySenderKey('98765@lid'), 'jid:98765@lid')
assert.equal(activitySenderKey('98765:12@lid'), 'jid:98765@lid')

const groups = aggregateActivity([
  { messageId:'a', atMs:now - 3000, senderKey:'11111111111', speaker:'Ada', mediaType:'sticker' },
  { messageId:'b', atMs:now - 2000, senderKey:'11111111111', speaker:'Ada', mediaType:'video' },
  { messageId:'c', atMs:now - 1000, senderKey:'22222222222', speaker:'Ben', mediaType:'', text:'Hello' },
  { messageId:'d', atMs:now - 1000, senderKey:'11111111111', speaker:'Ada', mediaType:'image' },
  { messageId:'e', atMs:now - 1000, senderKey:'11111111111', speaker:'Ada', mediaType:'audio' },
  { messageId:'f', atMs:now - 1000, senderKey:'11111111111', speaker:'Ada', mediaType:'document' },
  { messageId:'bot', atMs:now - 1000, senderKey:'bot', fromBot:true },
  { messageId:'old', atMs:now - 31 * 86400000, senderKey:'11111111111', mediaType:'image' },
], { nowMs:now })
assert.equal(groups.total,6)
assert.equal(groups.uniqueMembers,2)
assert.equal(groups.totals.stickers,1)
assert.equal(groups.totals.videos,1)
assert.equal(groups.totals.photos,1)
assert.equal(groups.totals.audio,1)
assert.equal(groups.totals.documents,1)
assert.equal(groups.totals.media,5)
assert.equal(groups.totals.text,1)
assert.equal(groups.ranked[0].messages,5)
assert.equal(groups.ranked[0].rank,1)
assert.deepEqual(activityBadges(75).earned.map(x => x.name), ['Contributor','Regular'])

const dir = await mkdtemp(join(tmpdir(), 'mscc-group-activity-'))
const file = join(dir,'shared.sqlite')
try {
  // Exercise additive migration for databases created by earlier versions.
  const db = new DatabaseSync(file)
  db.exec(`CREATE TABLE conversation_messages(
    chat_jid TEXT NOT NULL, message_id TEXT NOT NULL, account_id TEXT NOT NULL DEFAULT '',
    participant_jid TEXT NOT NULL DEFAULT '', speaker TEXT NOT NULL DEFAULT '',
    from_bot INTEGER NOT NULL DEFAULT 0, text_content TEXT NOT NULL DEFAULT '',
    media_type TEXT NOT NULL DEFAULT '', at_ms INTEGER NOT NULL,
    PRIMARY KEY (chat_jid, message_id)
  )`)
  db.close()
  const storage = await openSharedStorage({ file, ttlMs:86400000, maxMessagesPerAccount:100 })
  try {
    const payload = { chatJid:'group@g.us', accountId:'B', participantJid:'11111111111@s.whatsapp.net',
      senderKey:'11111111111', speaker:'Ada', fromBot:false, atMs:now - 1000 }
    storage.putConversationMessage({ ...payload, messageId:'one', mediaType:'sticker' })
    // Multi-account replay of same WhatsApp message must not double count.
    storage.putConversationMessage({ ...payload, accountId:'C', messageId:'one', mediaType:'sticker' })
    storage.putConversationMessage({ ...payload, messageId:'two', mediaType:'video' })
    storage.putConversationMessage({ ...payload, messageId:'three', mediaType:'', text:'hi' })
    storage.putConversationMessage({ chatJid:'group@g.us', messageId:'bot', accountId:'B', fromBot:true, atMs:now })
    storage.putConversationMessage({ ...payload, chatJid:'other@g.us', messageId:'four', mediaType:'image' })
    const report = activityReport(storage,'group@g.us', { nowMs:now })
    assert.equal(report.total,3)
    assert.equal(report.uniqueMembers,1)
    assert.equal(report.totals.media,2)
    assert.equal(report.ranked[0].key,'11111111111')
    const replied = []
    const ctx = {
      groupKey:'group@g.us', activityUserKey:'11111111111', args:['7d'],
      getGroupActivity:period => activityReport(storage,'group@g.us',{ period,nowMs:now }),
      reply:value => { replied.push(String(value)); return value },
    }
    await groupstats.run(ctx)
    await topactive.run(ctx)
    await myactivity.run(ctx)
    await activityrewards.run(ctx)
    assert(replied[0].includes('Stickers: 1'))
    assert(replied[1].includes('Ada'))
    assert(replied[2].includes('Stickers sent: 1'))
    assert(replied[2].includes('Media sent: *2*'))
    assert(replied[3].includes('no virtual currency'))
  } finally { storage.close() }
} finally { await rm(dir,{recursive:true,force:true}) }
console.log('PASS MSCC deduplicated group activity statistics and recognition')
