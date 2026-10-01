import assert from 'node:assert/strict'
import { rm } from 'node:fs/promises'
import { openSharedStorage } from './shared-storage.js'
import { createNamiAssistant } from './nami-assistant.js'

const root = '/tmp/mscc-nami-assistant-selftest'
await rm(root, { recursive:true, force:true })
const storage = await openSharedStorage({
  file:root + '/shared.sqlite',
  ttlMs:24 * 3600000,
  maxMessagesPerAccount:100,
})

storage.putConversationMessage({
  chatJid:'anime@g.us',
  messageId:'m1',
  participantJid:'u1@s.whatsapp.net',
  speaker:'Teddy',
  text:'I liked Frieren because it was patient and character-focused.',
  atMs:Date.now() - 5000,
})

const calls = []
const ai = {
  enabled:true,
  async complete(input) {
    calls.push(input)
    if (String(input.system).includes('slice of a WhatsApp group')) {
      return { ok:true, text:'- Teddy discussed Frieren and liked its patient character focus.' }
    }
    if (String(input.system).includes('faithful WhatsApp group recap')) {
      return { ok:true, text:'✦ TODAY\n• Frieren came up as a character-focused favorite.' }
    }
    return {
      ok:true,
      text:'If Frieren worked for you, try *Mushishi* for something even quieter and more atmospheric. ✦',
      usedWeb:Boolean(input.allowWeb),
      model:'test',
    }
  },
}

const nami = createNamiAssistant({
  ai,
  storage,
  getCommands:() => [
    { name:'anime', capability:'anime', description:'Browse/search anime and download episodes.', usage:'.anime <title>' },
    { name:'manga', capability:'manga', description:'Search manga.', usage:'.manga <title>' },
    { name:'ping', capability:'general', description:'Check whether the bot is responding.', usage:'.ping' },
    { name:'movie', capability:'movies', description:'Search movies.', usage:'.movie <title>' },
    { name:'music', capability:'music', description:'Find music.', usage:'.music <query>' },
  ],
})

const rec = await nami.answer({
  chatJid:'anime@g.us',
  text:'what should I watch next?',
  senderName:'Teddy',
  groupName:'Anime Club',
  isGroup:true,
})
assert.equal(rec.ok, true)
assert(rec.text.includes('Mushishi'))
const prompt = calls.at(-1).messages[0].content
assert(prompt.includes('.anime — Browse/search anime and download episodes.'))
assert(prompt.includes('.manga — Search manga.'))
assert(prompt.includes('.ping — Check whether the bot is responding.'))
assert(!prompt.includes('.movie — Search movies.'))
assert(!prompt.includes('.music — Find music.'))
assert(String(calls.at(-1).system).includes('specializes in anime and manga'))
assert(String(calls.at(-1).system).includes('Do not dump spoilers'))

calls.length = 0
const current = await nami.answer({
  chatJid:'anime@g.us',
  text:'what is the latest Frieren anime announcement?',
  senderName:'Teddy',
  groupName:'Anime Club',
  isGroup:true,
})
assert.equal(current.ok, true)
assert.equal(calls.at(-1).allowWeb, true)

calls.length = 0
const summary = await nami.answer({
  chatJid:'anime@g.us',
  text:'catch me up on today',
  senderName:'Teddy',
  groupName:'Anime Club',
  isGroup:true,
})
assert.equal(summary.ok, true)
assert(summary.text.includes('TODAY'))
assert(calls.length >= 2)

storage.close()
console.log('PASS Nami assistant selftest')
