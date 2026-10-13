import assert from 'node:assert/strict'
import { SourceRegistry } from './source-registry.js'

const before = process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = '120'
try {
  let observedSignal = null
  let otherRuns = 0
  const registry = new SourceRegistry({
    rootUrl: new URL('./sources/', import.meta.url),
    storage: {
      getSourceDefault: () => 'stalled',
      brandForCapability: () => 'Nami',
    },
  })
  registry.sources.set('anime:stalled', {
    id:'stalled', capability:'anime', name:'Stalled', fallbackOrder:10,
    async run({ context }) {
      observedSignal = context.signal
      return new Promise(() => {})
    },
  })
  registry.sources.set('anime:healthy', {
    id:'healthy', capability:'anime', name:'Healthy', fallbackOrder:20,
    async run() {
      otherRuns++
      return { items:[{ title:'Example' }] }
    },
  })

  const start = performance.now()
  const outcome = await registry.execute({
    capability:'anime',
    userKey:'user',
    explicitSource:'stalled',
    payload:{ action:'search', query:'One Piece' },
  })
  assert.equal(outcome.status, 'source-error')
  assert.equal(outcome.error.code, 'source-lookup-timeout')
  assert.ok(observedSignal?.aborted, 'source receives abort signal')
  assert.ok(performance.now() - start < 1800, 'interactive command must complete promptly')

  // A user default with fallbacks must obey the *same* deadline; a timed-out
  // preferred provider may not launch more work after the deadline expires.
  otherRuns = 0
  const fallback = await registry.execute({
    capability:'anime',
    userKey:'user',
    payload:{ action:'search', query:'One Piece' },
  })
  assert.equal(fallback.status, 'all-failed')
  assert.equal(fallback.error.code, 'source-lookup-timeout')
  assert.equal(otherRuns, 0, 'do not start background fallback after shared timeout')

  let transferSignal = false
  registry.sources.set('anime:healthy', {
    id:'healthy', capability:'anime', name:'Healthy', fallbackOrder:20,
    async run({ context }) {
      transferSignal = Boolean(context.signal)
      return { delivered:true }
    },
  })
  const download = await registry.execute({
    capability:'anime', explicitSource:'healthy', payload:{ action:'download' },
  })
  assert.equal(download.status, 'ok')
  assert.equal(transferSignal, false, 'media delivery must keep its own transfer deadlines')
  console.log('PASS anime source deadline and cancellation (no WhatsApp session touched)')
} finally {
  if (before === undefined) delete process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
  else process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = before
}
