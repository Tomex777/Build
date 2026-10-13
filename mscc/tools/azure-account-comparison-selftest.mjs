import assert from 'node:assert/strict'
import { readAccountComparison, summarizeAccountComparison } from './azure-account-comparison.mjs'

const state = {
  accounts: [
    { id:'A', displayName:'Secret Main Name', number:'2348012345678', pairingCode:'SECRET-CODE', status:'auth-invalid' },
    { id:'B', displayName:'Secret Josia Name', number:'2348098765432', pairingQr:'SECRET-QR', status:'connected' },
  ],
  settings: { panelPassword:'SECRET-PASSWORD' }
}
const main = { snapshot: {
  id:'A', displayName:'private-name', connected:false, registered:true, paused:false,
  status:'auth-invalid', lastConnectedAt:1000, lastDisconnectedAt:2000, lastDisconnectCode:401,
  disconnectReason:'private@s.whatsapp.net, token=secret',
}, events:[
  { at:'2026-10-12T20:43:35Z', action:'account.disconnected', detail: { reasonCode:401, origin:'stream-error', protocolDetail:'device-removed', pairingCode:'SECRET-CODE', jid:'2348012345678@s.whatsapp.net' }},
  { at:'2026-10-12T20:29:39Z', action:'account.connected', detail: { id:'A', number:'2348012345678' } },
  { at:'2026-10-12T20:20:00Z', action:'message.received', detail: { message:'PRIVATE-MESSAGE' } },
]}
const linked = { snapshot: { id:'B', displayName:'Josia', connected:true, registered:true, paused:false, status:'connected',
  lastConnectedAt:1100, lastDisconnectedAt:0, lastDisconnectCode:null }, events: [] }
const inputs = [{id:'A',data:main},{id:'B',data:linked}]
const report = summarizeAccountComparison(state, inputs, '2026-10-12T21:30:00Z')
assert.equal(report.accountCount, 2)
assert.equal(report.verdict, 'main-revoked-while-linked-connected')
assert.deepEqual(report.accounts.map(a=>a.account), ['Main','Linked #1'])
assert.equal(report.accounts[0].status, 'auth-invalid')
assert.equal(report.accounts[1].status, 'connected')
assert.equal(report.accounts[0].recentEvents[0].code, 401)
assert.equal(report.accounts[0].recentEvents[0].origin, 'stream-error')
assert.equal(report.accounts[0].recentEvents[0].protocolDetail, 'device-removed')
const raw = JSON.stringify(report)
for(const secret of ['SECRET-CODE','SECRET-PASSWORD','SECRET-QR','PRIVATE-MESSAGE','2348012345678','2348098765432','private-name','private@s.whatsapp.net', 'Josia']) {
  assert.equal(raw.includes(secret),false,'Leaked private data: '+secret)
}
assert.equal(summarizeAccountComparison(state,[{id:'A',data:{...main,snapshot:{...main.snapshot,connected:true,status:'connected'}}},{id:'B',data:linked}]).verdict,'multiple-connected')
assert.equal(summarizeAccountComparison(state,[{id:'A',data:main},{id:'B',data:{...linked,snapshot:{...linked.snapshot,connected:false,status:'auth-invalid'}}}]).verdict,'inconclusive')

const seen=[]
const fetcher=async url=>{
  seen.push(url)
  return {ok:true,json:async()=>url.endsWith('/state')?state:url.includes('/accounts/A/')?main:linked}
}
const fetched = await readAccountComparison({fetcher,localPort:12345})
assert.equal(fetched.accountCount,2)
assert.deepEqual(seen,[
  'http://127.0.0.1:12345/state',
  'http://127.0.0.1:12345/accounts/A/diagnostics?limit=24',
  'http://127.0.0.1:12345/accounts/B/diagnostics?limit=24',
])
await assert.rejects(()=>readAccountComparison({fetcher,localPort:0}),/port/)
await assert.rejects(()=>readAccountComparison({fetcher:async()=>({ok:false}),localPort:12345}),/unavailable/)
assert.throws(()=>summarizeAccountComparison({accounts:[]}),/inventory/)
assert.throws(()=>summarizeAccountComparison({accounts:[{id:'B'}]}),/Main/)
console.log('PASS read-only A/B account comparison, 401 differentiation and sensitive-data redaction')
