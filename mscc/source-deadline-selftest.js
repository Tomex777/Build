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
  // Music lookups must not hang indefinitely if YouTube or a fallback stalls.
  let musicSignal = null
  const musicRegistry = new SourceRegistry({
    rootUrl:new URL('./sources/', import.meta.url),
    storage:{ brandForCapability: () => 'NIGHT' },
  })
  musicRegistry.sources.set('music:youtube', {
    id:'youtube', capability:'music', name:'YouTube', primary:true, fallbackOrder:0,
    async run({ context }) {
      musicSignal = context.signal
      return new Promise(() => {})
    },
  })
  musicRegistry.sources.set('music:backup', {
    id:'backup', capability:'music', name:'Backup', primary:false, fallbackOrder:10,
    async run() { throw new Error('Backup unavailable') },
  })
  const musicStart = performance.now()
  const music = await musicRegistry.execute({
    capability:'music', payload:{ action:'search', query:'test' },
  })
  assert.equal(music.status, 'all-failed')
  assert.equal(music.error.code, 'source-lookup-timeout')
  assert.ok(musicSignal?.aborted)
  assert.ok(performance.now() - musicStart < 1800)
  console.log('PASS music source deadline and bounded fallback')

  // Movie and TV search/season lookups must use the same shared deadline.
  for (const [capability, action] of [['movies','search'], ['tv','seasons']]) {
    let signal = null
    const mediaRegistry = new SourceRegistry({
      rootUrl:new URL('./sources/', import.meta.url),
      storage:{ brandForCapability:() => 'NIGHT' },
    })
    mediaRegistry.sources.set(capability + ':stalled', {
      id:'stalled', capability, name:'Stalled media source', fallbackOrder:10,
      async run({ context }) {
        signal = context.signal
        return new Promise(() => {})
      },
    })
    const lookupStart = performance.now()
    const timedOut = await mediaRegistry.execute({
      capability,
      explicitSource:'stalled',
      payload:{ action, query:'Example title', item:{ id:'example', title:'Example title' } },
    })
    assert.equal(timedOut.status, 'source-error', capability + ' lookup must fail clearly on timeout')
    assert.equal(timedOut.error.code, 'source-lookup-timeout', capability + ' must return the typed timeout')
    assert.ok(signal?.aborted, capability + ' source receives an aborted signal')
    assert.ok(performance.now() - lookupStart < 1800, capability + ' lookup must not leave the command hanging')

    let transferHadSignal = false
    mediaRegistry.sources.set(capability + ':healthy', {
      id:'healthy', capability, name:'Healthy transfer source',
      async run({ context }) {
        transferHadSignal = Boolean(context.signal)
        return { delivered:true }
      },
    })
    const transfer = await mediaRegistry.execute({
      capability,
      explicitSource:'healthy',
      payload:{ action:'download', item:{ id:'example', title:'Example title' } },
    })
    assert.equal(transfer.status, 'ok', capability + ' media transfer remains independent')
    assert.equal(transferHadSignal, false, capability + ' transfer must not inherit lookup cancellation')
  }
  console.log('PASS movie/TV lookup deadlines and independent media transfers')

  console.log('PASS anime source deadline and cancellation (no WhatsApp session touched)')
} finally {
  if (before === undefined) delete process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
  else process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = before
}
