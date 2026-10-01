import assert from 'node:assert/strict'
import { rm } from 'node:fs/promises'
import { openSharedStorage } from './shared-storage.js'
import { createJosiahAssistant, likelyNeedsWeb, summaryWindowFor } from './josiah-assistant.js'

assert.equal(summaryWindowFor('summarize the last 6 hours'), 6)
assert.equal(summaryWindowFor('what happened today?'), 24)
assert.equal(summaryWindowFor('hello there'), 0)
assert.equal(likelyNeedsWeb('what is the latest Android news?'), true)
assert.equal(likelyNeedsWeb('why did you say that?'), false)

const root = '/tmp/mscc-josiah-assistant-selftest'
await rm(root, { recursive:true, force:true })
const storage = await openSharedStorage({
  file:root + '/shared.sqlite',
  ttlMs:24 * 3600000,
  maxMessagesPerAccount:100,
})

const now = Date.now()
storage.putConversationMessage({
  chatJid:'group@g.us',
  messageId:'m1',
  participantJid:'u1@s.whatsapp.net',
  speaker:'Teddy',
  text:'We should meet Saturday evening.',
  atMs:now - 3000,
})
storage.putConversationMessage({
  chatJid:'group@g.us',
  messageId:'m2',
  participantJid:'u2@s.whatsapp.net',
  speaker:'Maya',
  text:'Saturday works but we have not picked a time.',
  atMs:now - 2000,
})

const calls = []
const ai = {
  enabled:true,
  async complete(input) {
    calls.push(input)
    if (String(input.system).includes('slice of a WhatsApp group')) {
      return { ok:true, text:'- Saturday evening was proposed; exact time remains undecided.' }
    }
    if (String(input.system).includes('faithful WhatsApp group recap')) {
      return { ok:true, text:'◇ TODAY\n• Saturday evening was proposed, but no exact time was settled.' }
    }
    return { ok:true, text:'I meant the Saturday plan. ◇', usedWeb:false, model:'test' }
  },
}

const assistant = createJosiahAssistant({
  ai,
  storage,
  getCommands:() => [
    { name:'summary', capability:'group', description:'Summarize recent activity in this group.', usage:'.summary [24h]', aliases:['recap','catchup'] },
    { name:'ping', capability:'general', description:'Check whether the bot is responding.', usage:'.ping' },
  ],
})

const answer = await assistant.answer({
  chatJid:'group@g.us',
  text:'But what did you mean?',
  senderName:'Teddy',
  quotedText:'Saturday works.',
  quotedSpeaker:'Josiah',
  groupName:'Test Group',
  isGroup:true,
})
assert.equal(answer.ok, true)
assert.equal(answer.text, 'I meant the Saturday plan. ◇')
const prompt = calls.at(-1).messages[0].content
assert(prompt.includes('REPLYING TO: Josiah: Saturday works.'))
assert(prompt.includes('Teddy: We should meet Saturday evening.'))
assert(prompt.includes('CURRENT MESSAGE:\nBut what did you mean?'))

calls.length = 0
const summary = await assistant.summarize({
  chatJid:'group@g.us',
  hours:24,
  groupName:'Test Group',
})
assert.equal(summary.ok, true)
assert.equal(summary.messageCount, 2)
assert(summary.text.includes('Saturday evening'))
assert(calls.length >= 2)

storage.close()
console.log('PASS Josiah assistant selftest')
