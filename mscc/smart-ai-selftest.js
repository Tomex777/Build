import assert from 'node:assert/strict'
import { createSmartAI } from './smart-ai.js'

function response(status, body = {}, headers = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get:name => headers[String(name).toLowerCase()] || null },
    async json() { return body },
  }
}

{
  let calls = 0
  const ai = createSmartAI({
    env:{},
    fetchImpl:async () => { calls += 1; throw new Error('no') },
  })
  const result = await ai.complete({ messages:[{ role:'user', content:'hello' }] })
  assert.equal(result.ok, false)
  assert.equal(calls, 0)
}

{
  const auth = []
  const ai = createSmartAI({
    env:{
      GROQ_API_KEYS:'key-a,key-b,key-c',
      GROQ_SMART_MODELS:'openai/gpt-oss-120b',
    },
    fetchImpl:async (_url, init) => {
      auth.push(init.headers.authorization)
      return response(200, { choices:[{ message:{ content:'ok' } }] })
    },
  })
  for (let i = 0; i < 4; i += 1) {
    const result = await ai.complete({ messages:[{ role:'user', content:'hello' }] })
    assert.equal(result.ok, true)
  }
  assert.deepEqual(auth, ['Bearer key-a','Bearer key-b','Bearer key-c','Bearer key-a'])
}

{
  const auth = []
  const ai = createSmartAI({
    env:{
      GROQ_API_KEYS:'expired,working',
      GROQ_SMART_MODELS:'openai/gpt-oss-120b',
    },
    fetchImpl:async (_url, init) => {
      auth.push(init.headers.authorization)
      if (init.headers.authorization === 'Bearer expired') return response(401)
      return response(200, { choices:[{ message:{ content:'recovered' } }] })
    },
  })
  const first = await ai.complete({ messages:[{ role:'user', content:'hello' }] })
  assert.equal(first.text, 'recovered')
  const second = await ai.complete({ messages:[{ role:'user', content:'again' }] })
  assert.equal(second.text, 'recovered')
  assert.deepEqual(auth, ['Bearer expired','Bearer working','Bearer working'])
  assert.equal(ai.health()[0].disabled, true)
}

{
  let payload = null
  const ai = createSmartAI({
    env:{
      GROQ_API_KEY:'key',
      GROQ_SMART_MODELS:'openai/gpt-oss-20b',
    },
    fetchImpl:async (_url, init) => {
      payload = JSON.parse(init.body)
      return response(200, {
        choices:[{
          message:{
            content:'fresh answer',
            executed_tools:[{ type:'browser_search' }],
          },
        }],
      })
    },
  })
  const result = await ai.complete({
    allowWeb:true,
    messages:[{ role:'user', content:'what happened today?' }],
  })
  assert.equal(result.ok, true)
  assert.equal(result.usedWeb, true)
  assert.deepEqual(payload.tools, [{ type:'browser_search' }])
  assert.equal(payload.tool_choice, 'auto')
}

console.log('PASS smart AI selftest')
