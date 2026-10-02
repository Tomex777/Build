import assert from 'node:assert/strict'
import { rm } from 'node:fs/promises'
import { openSharedStorage } from './shared-storage.js'
import { createMiMiAssistant } from './mimi-assistant.js'

const root = '/tmp/mscc-mimi-assistant-selftest'
await rm(root, { recursive:true, force:true })
const storage = await openSharedStorage({
  file:root + '/shared.sqlite',
  ttlMs:24 * 3600000,
  maxMessagesPerAccount:100,
})

const calls = []
const ai = {
  enabled:true,
  async complete(input) {
    calls.push(input)
    return { ok:true, text:'You want dramatic? Fine. Start with *Arcane*. ✧', usedWeb:false, model:'test' }
  },
}

const mimi = createMiMiAssistant({
  ai,
  storage,
  getCommands:() => [
    { name:'music', capability:'music', description:'Find music.', usage:'.music <query>' },
    { name:'movie', capability:'movies', description:'Find movies.', usage:'.movie <title>' },
    { name:'tv', capability:'tv', description:'Find TV series.', usage:'.tv <title>' },
    { name:'anime', capability:'anime', description:'Find anime.', usage:'.anime <title>' },
    { name:'ping', capability:'general', description:'Check availability.', usage:'.ping' },
  ],
})

const result = await mimi.answer({
  chatJid:'media@g.us',
  text:'give me something dramatic to watch',
  senderName:'Teddy',
  groupName:'Media Club',
  isGroup:true,
  groupPersonalities:[
    { profileId:'mimi', displayName:'MiMi', mentionToken:'[[mention:mimi]]' },
    { profileId:'nami', displayName:'Nami', mentionToken:'[[mention:nami]]' },
  ],
})
assert.equal(result.ok, true)
assert(result.text.includes('Arcane'))
const prompt = calls.at(-1).messages[0].content
assert(prompt.includes('.music'))
assert(prompt.includes('.movie'))
assert(prompt.includes('.tv'))
assert(!prompt.includes('.anime'))
assert(!prompt.includes('.ping'))
assert(prompt.includes('mention token: [[mention:nami]]'))
assert.equal(calls.at(-1).allowWeb, false)
assert(String(calls.at(-1).system).includes('operational specialty is music, movies, and TV series'))
assert(String(calls.at(-1).system).includes('Never call yourself a bot'))

storage.close()
console.log('PASS MiMi assistant selftest')
