import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createAssistantTyping, naturalTypingMs } from './assistant-typing.js'

assert.equal(naturalTypingMs('hi'), 1100)
assert(naturalTypingMs('A sentence with some actual content.') > 1100)
assert.equal(naturalTypingMs('a'.repeat(500)), 3300)
assert.equal(naturalTypingMs(''), 1100)

{
  let now = 5000
  const presence = []
  const waits = []
  let tick = null
  let cleared = false
  const typing = createAssistantTyping({
    sock:{ sendPresenceUpdate:async (state, jid) => { presence.push([state, jid]) } },
    chat:'group@g.us',
    now:() => now,
    sleep:async ms => { waits.push(ms); now += ms },
    schedule:fn => { tick = fn; return { unref() {} } },
    unschedule:() => { cleared = true },
  })
  typing.start()
  assert.deepEqual(presence, [['composing', 'group@g.us']])
  tick()
  assert.deepEqual(presence.at(-1), ['composing', 'group@g.us'])
  await typing.beforeSend('Hi!')
  assert.deepEqual(waits, [1100], 'fast AI completions must still show typing')
  typing.stop()
  assert.deepEqual(presence.at(-1), ['paused', 'group@g.us'])
  assert(cleared)
  const sent = presence.length
  typing.stop()
  tick()
  assert.equal(presence.length, sent, 'stopped indicator must never resume')
}
{
  let now = 1000
  const waits = []
  const typing = createAssistantTyping({
    sock:{ sendPresenceUpdate() { return Promise.resolve() } },
    chat:'friend@s.whatsapp.net',
    now:() => now,
    sleep:async ms => waits.push(ms),
    schedule:() => ({ unref() {} }),
    unschedule:() => {},
  })
  typing.start()
  now += 7000 // Provider time is already more than the capped human typing time.
  await typing.beforeSend('A long reply '.repeat(30))
  assert.equal(waits.length, 0, 'never stack an artificial delay on slow Groq replies')
  typing.stop()
}
{
  let current = true
  const states = []
  const typing = createAssistantTyping({
    sock:{ sendPresenceUpdate(state) { states.push(state); return Promise.reject(new Error('offline')) } },
    chat:'group@g.us',
    canSignal:() => current,
    schedule:() => ({ unref() {} }),
    unschedule:() => {},
  })
  typing.start()
  current = false // Socket reconnected; never signal using the old session.
  typing.stop()
  assert.deepEqual(states, ['composing'])
}
{
  // An unavailable presence transport is a no-op, not an AI failure.
  const typing = createAssistantTyping({ sock:{}, chat:'group@g.us' })
  typing.start()
  await typing.beforeSend('Still deliver the answer')
  typing.stop()
}
const source = readFileSync(new URL('./index.js', import.meta.url), 'utf8')
const handler = source.slice(source.indexOf('async function handleProfileAssistant('), source.indexOf('function publicSudoNumbers('))
assert(handler.includes('createAssistantTyping({'))
assert(handler.includes('typing.start()'))
assert(handler.includes('await typing.beforeSend(reaction.text)'))
assert(handler.includes('} finally {\n    typing.stop()'))
assert(handler.indexOf('typing.start()') > handler.indexOf('if (!activation.active) return false'))
assert(handler.indexOf('typing.start()') > handler.indexOf('if (!input.respond) return false'))
assert(handler.indexOf('await typing.beforeSend(') < handler.indexOf('await sendAssistantReply('))
console.log('PASS native WhatsApp typing indicators, capped pacing, cancellation, and no presence on ignored media')
