import assert from 'node:assert/strict'
import { createAniListResolver } from './anilist-resolver.js'

function response(status, body, headers = {}) {
  return {
    ok:status >= 200 && status < 300,
    status,
    headers:{ get:name => headers[String(name).toLowerCase()] || null },
    async json() { return body },
  }
}

{
  let calls = 0
  const resolver = createAniListResolver({
    fetchImpl:async (_url, init) => {
      calls += 1
      const payload = JSON.parse(init.body)
      assert.equal(payload.variables.type, 'ANIME')
      assert.equal(payload.variables.search, 'AOT')
      return response(200, {
        data:{
          Page:{
            media:[{
              id:16498,
              idMal:16498,
              title:{
                userPreferred:'Shingeki no Kyojin',
                romaji:'Shingeki no Kyojin',
                english:'Attack on Titan',
                native:'進撃の巨人',
              },
              synonyms:['AoT'],
              format:'TV',
              status:'FINISHED',
              seasonYear:2013,
              episodes:25,
              siteUrl:'https://anilist.co/anime/16498',
            }],
          },
        },
      })
    },
  })

  const first = await resolver.resolve('AOT')
  assert.equal(first.source, 'anilist')
  assert(first.aliases.includes('Attack on Titan'))
  assert(first.aliases.includes('Shingeki no Kyojin'))
  assert(first.aliases.includes('進撃の巨人'))
  assert(first.aliases.includes('AoT'))
  assert.equal(first.matches[0].episodes, 25)

  const second = await resolver.resolve('AOT')
  assert.equal(second.source, 'cache')
  assert.equal(calls, 1)
}

{
  let now = 100000
  let calls = 0
  const resolver = createAniListResolver({
    now:() => now,
    fetchImpl:async () => {
      calls += 1
      return response(429, {}, { 'retry-after':'30' })
    },
  })

  const first = await resolver.resolve('Frieren')
  assert.equal(first.source, 'fallback')
  const second = await resolver.resolve('Bleach')
  assert.equal(second.source, 'fallback')
  assert.equal(calls, 1)

  now += 61000
  await resolver.resolve('Bleach')
  assert.equal(calls, 2)
}

{
  const resolver = createAniListResolver({
    fetchImpl:async () => { throw new Error('offline') },
  })
  const result = await resolver.resolve('Cowboy Bebop')
  assert.deepEqual(result.aliases, ['Cowboy Bebop'])
  assert.equal(result.source, 'fallback')
}

console.log('PASS AniList resolver selftest')
