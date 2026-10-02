import { mkdir, rm, writeFile } from 'node:fs/promises'
import { openSharedStorage } from './shared-storage.js'
import { SourceRegistry, brandedTitle } from './source-registry.js'
import { loadCommands } from './command-registry.js'

const root = new URL('./.source-selftest/', import.meta.url)
const rootPath = root.pathname
await rm(root, { recursive:true, force:true })
await mkdir(new URL('./sources/anime/', root), { recursive:true })
await mkdir(new URL('./sources/manga/', root), { recursive:true })
await mkdir(new URL('./sources/music/', root), { recursive:true })

await writeFile(new URL('./sources/anime/alpha.js', root), `
export default {
  id:'alpha',
  name:'AnimePahe',
  description:'Primary',
  brandAliases:['Anime Pahe'],
  async run({ query, context }) {
    if (query === 'fail') throw new Error('offline')
    return { items:[{ title:context.brandTitle('AnimePahe - Bleach') }] }
  }
}
`)
await writeFile(new URL('./sources/anime/beta.js', root), `
export default {
  id:'beta',
  name:'KayoAnime',
  async run({ query, context }) {
    return { items:[{ title:context.brandTitle('KayoAnime - ' + query) }] }
  }
}
`)
await writeFile(new URL('./sources/manga/only.js', root), `
export default {
  id:'only',
  name:'OnlyManga',
  async run({ query }) { return { text:'ONLY:' + query } }
}
`)
await writeFile(new URL('./sources/music/youtube.js', root), `
export default {
  id:'youtube',
  name:'YouTube',
  primary:true,
  async run({ query }) {
    if (query === 'fallback') throw new Error('primary unavailable')
    return { text:'YT:' + query }
  }
}
`)
await writeFile(new URL('./sources/music/api.js', root), `
export default {
  id:'api',
  name:'Music API',
  fallbackOrder:1,
  async run({ query }) { return { text:'API:' + query } }
}
`)

const store = await openSharedStorage({
  file:new URL('./shared.sqlite', root).pathname,
  ttlMs:86400000,
  maxMessagesPerAccount:100,
})
const registry = new SourceRegistry({ rootUrl:new URL('./sources/', root), storage:store })
await registry.load()

if (store.brandForCapability('anime') !== 'Nami') throw new Error('Anime brand must seed as Nami')
if (store.brandForCapability('manga') !== 'Nami') throw new Error('Manga brand must seed as Nami')
if (store.brandForCapability('music') !== 'MiMi') throw new Error('Music brand must seed as MiMi')

let out = await registry.execute({ capability:'anime', userKey:'2341', payload:{query:'Bleach'} })
if (out.status !== 'choice-required') throw new Error('Multiple sources without default must require a choice')

registry.setDefault('2341','anime','alpha')
if (registry.getDefault('2341','anime') !== 'alpha') throw new Error('Persistent source default was not saved')

out = await registry.execute({ capability:'anime', userKey:'2341', payload:{query:'Bleach'} })
if (out.status !== 'ok' || out.source.id !== 'alpha' || out.fallback) throw new Error('Saved default was not preferred')
if (out.result.items[0].title !== 'Nami - Bleach') throw new Error('Provider branding was not replaced with Nami')

out = await registry.execute({ capability:'anime', userKey:'2341', payload:{query:'fail'} })
if (out.status !== 'ok' || out.source.id !== 'beta' || !out.fallback || out.fallbackFrom?.id !== 'alpha') {
  throw new Error('Default-source fallback did not select and identify the fallback')
}
if (out.result.items[0].title !== 'Nami - fail') throw new Error('Fallback provider branding was not replaced')

out = await registry.execute({ capability:'manga', userKey:'2341', payload:{query:'Berserk'} })
if (out.status !== 'ok' || out.source.id !== 'only') throw new Error('Single source should run without a chooser')

out = await registry.execute({ capability:'music', userKey:'2341', payload:{query:'song'} })
if (out.status !== 'ok' || out.source.id !== 'youtube' || !out.managed) throw new Error('Managed music did not use primary source')
out = await registry.execute({ capability:'music', userKey:'2341', payload:{query:'fallback'} })
if (out.status !== 'ok' || out.source.id !== 'api' || !out.fallback) throw new Error('Managed music fallback chain failed')
out = await registry.execute({ capability:'music', userKey:'2341', explicitSource:'api', payload:{query:'song'} })
if (out.status !== 'source-choice-disabled') throw new Error('Music must not allow user source selection')
out = await registry.execute({ capability:'music', userKey:'2341', pinnedSource:'api', payload:{query:'song'} })
if (out.status !== 'ok' || out.source.id !== 'api' || !out.pinned) throw new Error('Internal managed-source pinning failed')
out = await registry.execute({ capability:'music', userKey:'2341', excludedSources:['youtube'], payload:{query:'song'} })
if (out.status !== 'ok' || out.source.id !== 'api') throw new Error('Managed source exclusion did not advance to fallback')
const musicOrder = registry.list('music').map(source => source.id).join('|')
if (musicOrder !== 'youtube|api') throw new Error('Managed music source list is not priority ordered')

store.setDeliveryDefault('2341','anime','720','document')
const delivery = store.getDeliveryDefault('2341','anime')
if (delivery?.quality !== '720' || delivery?.delivery !== 'document') throw new Error('Delivery default persistence failed')

if (brandedTitle('AnimePahe • Bleach', { botName:'Nami', sourceName:'AnimePahe' }) !== 'Nami • Bleach') {
  throw new Error('Title branding helper failed')
}

const publicRegistry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
  allowMissing:true,
})
if (publicRegistry.commands.get('anime')?.capability !== 'anime') throw new Error('commands/anime folder did not define anime capability')
if (publicRegistry.commands.get('manga')?.capability !== 'manga') throw new Error('commands/manga folder did not define manga capability')
if (publicRegistry.commands.get('source')?.capability !== 'core') throw new Error('commands/core folder did not define core capability')

store.close()
await rm(root, { recursive:true, force:true })
console.log('MSCC folder capability/source selection self-test OK')
