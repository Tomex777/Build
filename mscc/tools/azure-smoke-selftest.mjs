import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { checkAzureLoopback } from './azure-smoke.mjs'

async function listen(handler) {
  const server = createServer(handler)
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  return { server, port: server.address().port }
}
const validState = {
  destination: 'A',
  capabilities: { fixedCcDestination: true, changeCcDestination: false },
  accounts: [
    { id: 'A', role: 'owner', profile: 'control', status: 'connected', connected: true },
    { id: 'account-2', role: 'linked', profile: 'unassigned', status: 'paused', paused: true },
  ],
}
let state = structuredClone(validState)
const panel = await listen((_req, res) => {
  res.writeHead(200, { 'content-type': 'application/json' })
  res.end(JSON.stringify({ ok: true, service: 'mscc', panelConfigured: true }))
})
const control = await listen((req, res) => {
  const data = req.url === '/state' ? state : req.url?.startsWith('/activity') ? { entries: [] } : null
  res.writeHead(data ? 200 : 404, { 'content-type': 'application/json' })
  res.end(JSON.stringify(data || { error: 'Not found' }))
})
try {
  const test = () => checkAzureLoopback({ panelPort: panel.port, localPort: control.port })
  assert.deepEqual(await test(), { ok: true, accounts: 2, connected: 1, paused: 1 })
  state = { ...validState, destination: 'account-2' }
  await assert.rejects(test(), /CC destination/)
  state = { ...validState, accounts: [validState.accounts[0], { ...validState.accounts[1], profile: 'control' }] }
  await assert.rejects(test(), /control profile/)
  state = { ...validState, accounts: [validState.accounts[0], { ...validState.accounts[1], id: 'A' }] }
  await assert.rejects(test(), /Duplicate account IDs/)
  console.log('PASS MSCC post-deploy loopback smoke and invariant violation checks')
} finally {
  await Promise.all([new Promise(resolve => panel.server.close(resolve)), new Promise(resolve => control.server.close(resolve))])
}
