import assert from 'node:assert/strict'
import { createAdaptationResolver } from './adaptation-resolver.js'

function response(status, body) {
  return { ok:status >= 200 && status < 300, status, async json(){ return body } }
}

const seen=[]
const resolver=createAdaptationResolver({
  fetchImpl:async url => {
    const value=String(url)
    seen.push(value)
    if (value.includes('wbsearchentities')) {
      return response(200,{search:[{id:'Q123',label:'Dune',description:'1965 science fiction novel'}]})
    }
    if (value.includes('query.wikidata.org')) {
      const decoded=decodeURIComponent(value)
      if (decoded.includes('wd:Q123')) {
        return response(200,{results:{bindings:[
          {work:{value:'http://www.wikidata.org/entity/Q999'},workLabel:{value:'Dune'},movieId:{value:'438631'}},
          {work:{value:'http://www.wikidata.org/entity/Q998'},workLabel:{value:'Dune: Prophecy'},tvId:{value:'194764'}},
        ]}})
      }
      if (decoded.includes('wdt:P4947')) {
        return response(200,{results:{bindings:[
          {source:{value:'http://www.wikidata.org/entity/Q123'},sourceLabel:{value:'Dune'}},
        ]}})
      }
    }
    throw new Error('Unexpected adaptation URL '+value)
  },
})

const screens=await resolver.bookToScreen({title:'Dune',author:'Frank Herbert'})
assert.equal(screens[0].tmdbMovieId,438631)
assert.equal(screens[1].tmdbTvId,194764)

const books=await resolver.screenToBooks({tmdbId:438631,type:'movie'})
assert.equal(books[0].title,'Dune')
assert.equal(books[0].wikidataId,'Q123')

const cached=await resolver.bookToScreen({title:'Dune',author:'Frank Herbert'})
assert.equal(cached.length,2)
assert(seen.length >= 3)

console.log('PASS book screen adaptation resolver')
