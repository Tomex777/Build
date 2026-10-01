import assert from 'node:assert/strict'
import { createPersonalityAI } from './personality-ai.js'

function response(status, content) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return { choices: [{ message: { content } }] }
    },
  }
}

{
  const ai = createPersonalityAI({ env: {}, fetchImpl: async () => { throw new Error('should not call') } })
  assert.equal(await ai.say({ profileId:'josiah', fallback:'Still here.' }), 'Still here.')
}

{
  const ai = createPersonalityAI({
    env: {
      GROQ_API_KEY:'test-key',
      MSCC_PERSONALITY_AI_ENABLED:'true',
      MSCC_AI_MAX_ATTEMPTS:'1',
    },
    fetchImpl: async () => response(200, 'You checking up on me?\n*1h 2m 3s* online.'),
  })
  assert.equal(
    await ai.say({
      profileId:'josiah',
      intent:'uptime',
      fallback:'Uptime: 1h 2m 3s',
      preserve:['1h 2m 3s'],
    }),
    'You checking up on me?\n*1h 2m 3s* online.',
  )
}

{
  const ai = createPersonalityAI({
    env: { GROQ_API_KEY:'test-key', MSCC_PERSONALITY_AI_ENABLED:'true' },
    fetchImpl: async () => response(200, 'I have been up for a while.'),
  })
  assert.equal(
    await ai.say({
      profileId:'josiah',
      fallback:'Uptime: 9m 12s',
      preserve:['9m 12s'],
    }),
    'Uptime: 9m 12s',
  )
}

{
  const ai = createPersonalityAI({
    env: { GROQ_API_KEY:'test-key', MSCC_PERSONALITY_AI_ENABLED:'true' },
    fetchImpl: async () => response(200, 'Try .hack next.'),
  })
  assert.equal(
    await ai.say({
      profileId:'josiah',
      fallback:'Done.',
    }),
    'Done.',
  )
}

{
  let calls = 0
  const ai = createPersonalityAI({
    env: {
      GROQ_API_KEYS:'bad-key,good-key',
      MSCC_PERSONALITY_AI_ENABLED:'true',
      MSCC_AI_MAX_ATTEMPTS:'2',
    },
    fetchImpl: async () => {
      calls += 1
      return calls === 1 ? response(429, '') : response(200, 'Still here. ◇')
    },
  })
  assert.equal(await ai.say({ profileId:'josiah', fallback:'Still here.' }), 'Still here. ◇')
  assert.equal(calls, 2)
}

console.log('PASS personality AI selftest')
