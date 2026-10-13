import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { fetchText, loadDocument, solveBrowserSession } from './sources/manga/_common.js'
import kami from './sources/manga/18kami.js'
import { SourceRegistry } from './source-registry.js'
import { currentSourceSignal } from './source-execution-context.js'

// Metadata operations must stop as soon as the shared source-registry deadline
// expires, including the Cloudflare browser fallback path. No live websites.
const cancelled = new AbortController()
cancelled.abort(new Error('source deadline'))
await assert.rejects(
  () => fetchText('http://127.0.0.1:1/', {}, 25000, cancelled.signal),
  /source deadline/,
)
await assert.rejects(
  () => loadDocument('http://127.0.0.1:1/', {}, cancelled.signal),
  /source deadline/,
)
await assert.rejects(
  () => solveBrowserSession('http://127.0.0.1:1/', cancelled.signal),
  /source deadline/,
)
await assert.rejects(
  () => kami._probe.searchManga('', cancelled.signal),
  /source deadline/,
)
await assert.rejects(
  () => kami.run({ action:'browse', context:{ signal:cancelled.signal } }),
  /source deadline/,
)

let activeRequests = 0
const server = createServer((req, res) => {
  activeRequests++
  req.on('close', () => { activeRequests-- })
  // A remote website is stuck forever: abort should not wait 25s or start
  // FlareSolverr after the shared registry deadline has already passed.
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
try {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(new Error('timed out')), 40)
  const start = performance.now()
  await assert.rejects(
    () => fetchText('http://127.0.0.1:' + server.address().port + '/', {}, 25000, controller.signal),
    /timed out/,
  )
  clearTimeout(timer)
  assert.ok(performance.now() - start < 2000, 'cancel must not linger on browser fallback')

  // Third-party adapters sometimes omit forwarding context.signal. All
  // shared helper requests must still inherit this request's abort deadline.
  const beforeTimeout = process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
  process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = '120'
  try {
    const registry = new SourceRegistry({
      rootUrl:new URL('./sources/', import.meta.url),
      storage:{ brandForCapability:()=>'Nami', getSourceDefault:()=>'' },
    })
    let observedAmbient = false
    registry.sources.set('manga:stalled', {
      id:'stalled', name:'Stalled', capability:'manga', fallbackOrder:0,
      async run() {
        observedAmbient = Boolean(currentSourceSignal())
        return fetchText('http://127.0.0.1:' + server.address().port + '/')
      },
    })
    const started = performance.now()
    const outcome = await registry.execute({
      capability:'manga', explicitSource:'stalled',
      payload:{ action:'browse', query:'' },
    })
    assert.equal(outcome.status, 'source-error')
    assert.equal(outcome.error.code, 'source-lookup-timeout')
    assert.equal(observedAmbient, true)
    assert.ok(performance.now() - started < 2000, 'adapter ignored abort deadline')
    assert.equal(currentSourceSignal(), null, 'signal must not leak to another command')
  } finally {
    if (beforeTimeout === undefined) delete process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
    else process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = beforeTimeout
  }
} finally {
  server.closeAllConnections()
  await new Promise(resolve => server.close(resolve))
}
console.log('PASS manga/18Kami cancellation stops metadata requests and Cloudflare retries')
