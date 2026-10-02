import { SourceRegistry } from './source-registry.js'

const storage = {
  brandForCapability(capability) {
    return capability === 'music' ? 'MiMi' : 'MSCC'
  },
}

const registry = new SourceRegistry({
  rootUrl:new URL('./sources/', import.meta.url),
  storage,
})

await registry.load()

const music = registry.list('music')
const ids = music.map(source => source.id)
const expected = [
  'youtube',
  'jiosaavn',
  'audius',
  'monochrome',
  'internet-archive',
  'openverse',
  'wikimedia',
  'musicdex',
  'animethemes',
]

if (ids.join('|') !== expected.join('|')) {
  throw new Error('Unexpected managed music source order: ' + ids.join('|'))
}

const primary = music.filter(source => source.primary)
if (primary.length !== 1 || primary[0].id !== 'youtube') {
  throw new Error('YouTube must remain the single primary music source.')
}

for (const source of music) {
  if (typeof source.run !== 'function') throw new Error(source.id + ' has no run() implementation.')
  if (!Number.isFinite(source.fallbackOrder)) throw new Error(source.id + ' has no fallbackOrder.')
}

console.log('PASS managed music sources: ' + ids.join(' -> '))
