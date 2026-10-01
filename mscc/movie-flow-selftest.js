import { runMovieCommand } from './movie-flow.js'

const calls = []
const lists = []
const replies = []
let saved = null

const ctx = {
  publicPrefix:'.',
  listSources:() => [{ id:'alpha', name:'Alpha Movies' }],
  getDeliveryDefault:() => saved,
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
  resolveTmdbTitles:async query => ({
    query,
    aliases:[query, 'The Matrix'],
    matches:[{ id:603, title:'The Matrix', year:1999 }],
    source:'tmdb',
  }),
  resolveTmdbMedia:async id => ({
    id:Number(id),
    type:'movie',
    title:'The Matrix',
    aliases:['The Matrix','Matrix'],
    year:1999,
  }),
  executeSource:async ({ explicitSource, payload }) => {
    calls.push({ explicitSource, payload })
    if (payload.action === 'search') {
      if (payload.query === 'matrixx') {
        return { status:'ok', source:{ id:'alpha', name:'Alpha Movies' }, result:{ items:[] } }
      }
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Movies' },
        result:{ items:[{ id:'matrix', title:'The Matrix', year:1999 }] },
      }
    }
    if (payload.action === 'options') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Movies' },
        result:{ qualities:['1080','720'], deliveries:['document','video'] },
      }
    }
    if (payload.action === 'download') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Movies' },
        result:{ text:`OK:movie:${payload.quality}:${payload.delivery}` },
      }
    }
    throw new Error('Unexpected movie action ' + payload.action)
  },
}

await runMovieCommand(ctx, { args:['matrixx'] })
const searches = calls.filter(call => call.payload.action === 'search').map(call => call.payload.query)
if (searches.join('|') !== 'matrixx|The Matrix') {
  throw new Error('Movie TMDB alias retry failed: ' + searches.join('|'))
}
const optionList = lists.at(-1)
const option = optionList?.sections?.flatMap(section => section.rows)
  .find(row => row.id.includes('~download') && /1080p.*Document/.test(row.title))
if (!option) throw new Error('Movie download options missing')

replies.length = 0
await runMovieCommand(ctx, { args:option.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:movie:1080:document'))) {
  throw new Error('Movie download option did not reach source download')
}

saved = { quality:'720', delivery:'video' }
calls.length = 0
replies.length = 0
await runMovieCommand(ctx, { args:['The','Matrix'] })
if (!calls.some(call => call.payload.action === 'download' && call.payload.quality === '720' && call.payload.delivery === 'video')) {
  throw new Error('Saved movie delivery default was not applied')
}

lists.length = 0
saved = null
await runMovieCommand(ctx, { args:['~tmdb','603'] })
if (!lists.at(-1)?.sections?.length) throw new Error('TMDB movie handoff did not enter download options')

console.log('PASS movie search/identity/download flow')
