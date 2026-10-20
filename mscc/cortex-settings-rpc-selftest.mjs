import assert from 'node:assert/strict'
import { createServer } from 'node:net'
import { startWebPanel } from './web-panel.js'

const port = () => new Promise((resolve, reject) => {
  const listener = createServer()
  listener.on('error', reject)
  listener.listen(0, '127.0.0.1', () => {
    const value = listener.address().port
    listener.close(() => resolve(value))
  })
})

const httpPort = await port()
const controlPort = await port()
const calls = []
const values = { autoCc: false }
const panel = startWebPanel({
  port: httpPort,
  localControlPort: controlPort,
  password: 'not-a-real-password',
  sessionSecret: 'fake-only-ci-secret',
  getState: async () => ({ settings: values }),
  setSetting: async (key, value) => {
    if (!Object.hasOwn(values, key)) throw new Error('Unknown command setting')
    calls.push({key,value})
    values[key] = value
  },
  getActivity: async () => [],
})
try {
  const url = `http://127.0.0.1:${controlPort}/settings`
  const send = (data, endpoint = url) => fetch(endpoint, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(4000),
  })
  const good = await send({ key: 'autoCc', value: true })
  assert.equal(good.status, 200)
  assert.deepEqual(await good.json(), {ok:true})
  assert.deepEqual(calls, [{key:'autoCc',value:true}])
  const again = await send({key:'autoCc',value:true})
  assert.equal(again.status, 200)
  assert.equal(values.autoCc, true)
  for (const bad of [
    {key:'autoCc',value:'false'},
    {key:'autoCc',enabled:true},
    {key:'',value:true},
    {key:'../tmp/a',value:true},
    {key:'autoCc',value:null},
  ]) assert.equal((await send(bad)).status, 400)
  assert.equal((await send({key:'nonexistentSetting',value:true})).status, 500)
  assert.equal(calls.length, 2)
  // The localhost listener has no public IP binding, and the same path must
  // not exist on the public web-panel listener.
  assert.equal((await send({key:'autoCc',value:false},
    `http://127.0.0.1:${httpPort}/settings`)).status, 401)
  assert.equal(values.autoCc, true)
  console.log('PASS: NIGHT owns private Cortex Settings writes via loopback only')
} finally {
  panel.close()
}
