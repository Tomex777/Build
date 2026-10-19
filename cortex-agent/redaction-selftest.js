import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { runInNewContext } from 'node:vm'

const source=await readFile(fileURLToPath(new URL('./index.js',import.meta.url)),'utf8')
const start=source.indexOf('function redactLogLine(input)')
const end=source.indexOf('function publicErrorMessage(',start)
assert(start>=0&&end>start,'Agent redaction function must exist')
const redact=runInNewContext(source.slice(start,end)+'\nredactLogLine',{})
const samples=[
  '2026-10-19 08:13:30 baseKey: <Buffer 05 84 2f 5c 5e da>',
  '2026-10-19 08:13:30 privKey: <Buffer 98 8c 3e 45 22 aa>',
  '2026-10-19 08:13:30 signedKeyId: 13854413',
  '2026-10-19 08:13:30 currentRatchet: {',
  '2026-10-19 08:13:30 chainKey: <Buffer 05 84>',
  '2026-10-19 08:13:30 remoteIdentityKey: <Buffer 11 22>',
]
for(const line of samples){
  const safe=redact(line)
  assert(safe.includes('[REDACTED]'), 'Signal material leaked: '+line.split(':').pop())
  assert(!safe.includes('<Buffer'), 'Raw Signal Buffer leaked')
}
assert(!redact('received session: <Buffer 05 84 2f 5c>').includes('05 84'))
assert(!redact('authorization: bearer secret-super-long-header').includes('secret-super-long-header'))
assert.match(redact('MSCC service active 12h'),/MSCC service active 12h/)
console.log('PASS Cortex Agent hides Signal sessions and Buffer material before log delivery')
