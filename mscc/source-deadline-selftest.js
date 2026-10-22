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

  // TV search/season metadata lookups were previously unbounded, so a
  // stalled catalog provider could leave .tv/.series without a reply.
  let tvSignal = null
  registry.sources.set('tv:stalled', {
    id:'stalled', capability:'tv', name:'Stalled TV', fallbackOrder:10,
    async run({ context }) {
      tvSignal = context.signal
      return new Promise(() => {})
    },
  })
  const tvStart = performance.now()
  const tv = await registry.execute({
    capability:'tv',
    userKey:'user',
    explicitSource:'stalled',
    payload:{ action:'seasons', item:{ id:'show', title:'Example Show' } },
  })
  assert.equal(tv.status, 'source-error')
  assert.equal(tv.error.code, 'source-lookup-timeout')
  assert.ok(tvSignal?.aborted, 'TV season lookup receives an abort signal')
  assert.ok(performance.now() - tvStart < 1800, 'TV metadata lookup must complete promptly')

  // Movie catalog lookups use the same guard, without applying it to downloads.
  let movieSignal = null
  registry.sources.set('movies:stalled', {
    id:'stalled', capability:'movies', name:'Stalled Movies', fallbackOrder:10,
    async run({ context }) {
      movieSignal = context.signal
      return new Promise(() => {})
    },
  })
  const movie = await registry.execute({
    capability:'movies',
    userKey:'user',
    explicitSource:'stalled',
    payload:{ action:'search', query:'Hope' },
  })
  assert.equal(movie.status, 'source-error')
  assert.equal(movie.error.code, 'source-lookup-timeout')
  assert.ok(movieSignal?.aborted, 'movie search receives an abort signal')

  let tvTransferSignal = false
  registry.sources.set('tv:transfer', {
    id:'transfer', capability:'tv', name:'TV transfer',
    async run({ context }) {
      tvTransferSignal = Boolean(context.signal)
      return { delivered:true }
    },
  })
  const tvTransfer = await registry.execute({
    capability:'tv',
    explicitSource:'transfer',
    payload:{ action:'download', item:{ id:'show' }, episode:{ number:1 } },
  })
  assert.equal(tvTransfer.status, 'ok')
  assert.equal(tvTransferSignal, false, 'TV media transfers must not inherit catalog lookup deadlines')

  console.log('PASS TV/movie lookup deadlines and unbounded media transfer')
  console.log('PASS anime source deadline and cancellation (no WhatsApp session touched)')
} finally {
  if (before === undefined) delete process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS
  else process.env.MSCC_SOURCE_LOOKUP_TIMEOUT_MS = before
}
