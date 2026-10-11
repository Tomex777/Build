import assert from 'node:assert/strict'
import { checkRemoteCortex } from './remote-readiness.mjs'

const base = 'https://cortex.example.org'
const token = 'fake-test-only-bearer-token-please-ignore'
const account = {
  id: 'A', role: 'owner', profile: 'control', connected: true, paused: false, status: 'connected',
}
const secondary = {
  id: 'account-2', role: 'linked', profile: 'josiah', connected: false, paused: true, status: 'offline',
}
const success = {
  host: { state: 'active', runtime: { runtime: 'Node.js' } },
  pairing: {
    destination: 'A',
    capabilities: { fixedCcDestination: true, changeCcDestination: false },
    accounts: [account, secondary],
  },
}
function mock({ host = success.host, pairing = success.pairing, open = false, badToken = false } = {}) {
  const paths = []
  const fetcher = async (url, opts) => {
    const u = new URL(url)
    assert.equal(u.origin, base)
    assert.equal(opts.method, 'GET', 'Readiness must never mutate remote services')
    paths.push(u.pathname)
    if (!opts.headers.authorization) return { status: open ? 200 : 401 }
    if (badToken) return { status: 403, ok: false }
    return {
      status: 200, ok: true,
      json: async () => u.pathname === '/api/cortex/host/status' ? host : pairing,
    }
  }
  return { paths, fetcher }
}
{
  const m = mock()
  const r = await checkRemoteCortex({ url: base, token, fetcher: m.fetcher })
  assert.deepEqual(r, { ok: true, accounts: 2, connected: 1, paused: 1 })
  assert.deepEqual(m.paths, [
    '/api/cortex/host/status', '/api/cortex/host/status', '/api/cortex/mscc/pairing',
  ])
  assert.equal(JSON.stringify(r).includes('josiah'), false)
  assert.equal(JSON.stringify(r).includes('token'), false)
}
for (const bad of ['http://cortex.example.org', 'https://user:pass@cortex.example.org',
  'https://cortex.example.org/api/cortex/host/status', 'https://cortex.example.org/?token=x']) {
  await assert.rejects(checkRemoteCortex({ url: bad, token, fetcher: mock().fetcher }))
}
await assert.rejects(checkRemoteCortex({ url: base, token: 'short', fetcher: mock().fetcher }))
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ open: true }).fetcher }),
  /without required bearer authorization/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ badToken: true }).fetcher }),
  /rejected the authenticated probe/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ host: { state: 'inactive', runtime: { runtime: 'Node.js' } } }).fetcher }),
  /not active/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ pairing: { ...success.pairing, destination: 'account-2' } }).fetcher }),
  /destination/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ pairing: { ...success.pairing, accounts: [account, { ...secondary, profile: 'control' }] } }).fetcher }),
  /reserved control/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ pairing: { ...success.pairing, accounts: [account, account] } }).fetcher }),
  /duplicate stable/)
await assert.rejects(checkRemoteCortex({ url: base, token, fetcher: mock({ pairing: { ...success.pairing, capabilities: { changeCcDestination: true, fixedCcDestination: true } } }).fetcher }),
  /destination policy/)
{
  const fetcher = async () => { throw new Error('SECRET-DO-NOT-LEAK-IN-ERROR') }
  await assert.rejects(checkRemoteCortex({ url: base, token, fetcher }), err => {
    assert.equal(err.message.includes('SECRET-DO-NOT-LEAK-IN-ERROR'), false)
    return true
  })
}
console.log('PASS Cortex remote deployment readiness, TLS, auth isolation, account contract and privacy safeguards')
