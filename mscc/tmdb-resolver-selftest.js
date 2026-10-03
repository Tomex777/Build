import assert from 'node:assert/strict'
import { createTmdbResolver } from './tmdb-resolver.js'

function response(status, body, headers = {}) {
  return {
    ok:status >= 200 && status < 300,
    status,
    headers:{ get:name => headers[String(name).toLowerCase()] || null },
    async json() { return body },
  }
}

{
  const seen = []
  const resolver = createTmdbResolver({
    readAccessToken:'secret-token',
    fetchImpl:async (url, init) => {
      seen.push({ url:String(url), init })
      if (String(url).includes('/trending/movie/day')) {
        return response(200, {
          results:[{
            id:10331,
            title:'Night of the Living Dead',
            original_title:'Night of the Living Dead',
            release_date:'1968-10-04',
          }],
        })
      }
      if (String(url).includes('/search/movie')) {
        return response(200, {
          results:[{
            id:603,
            title:'The Matrix',
            original_title:'The Matrix',
            release_date:'1999-03-30',
            overview:'A hacker discovers reality is a simulation.',
          }],
        })
      }
      if (String(url).includes('/movie/603')) {
        return response(200, {
          id:603,
          title:'The Matrix',
          original_title:'The Matrix',
          release_date:'1999-03-30',
          alternative_titles:{ titles:[{ title:'Matrix' }] },
          external_ids:{ imdb_id:'tt0133093' },
        })
      }
      throw new Error('Unexpected TMDB path ' + url)
    },
  })

  const search = await resolver.search('matrix', 'movie')
  assert.equal(search.source, 'tmdb')
  assert.equal(search.matches[0].id, 603)
  assert.equal(search.matches[0].year, 1999)

  const browse = await resolver.browse('movie')
  assert.equal(browse.matches[0].id, 10331)

  const details = await resolver.details(603, 'movie')
  assert(details.aliases.includes('Matrix'))
  assert.equal(details.imdbId, 'tt0133093')
  assert.equal(seen[0].init.headers.authorization, 'Bearer secret-token')
}

{
  const resolver = createTmdbResolver({
    apiKey:'api-key',
    fetchImpl:async url => {
      const value = String(url)
      if (value.includes('/search/tv')) {
        assert(value.includes('api_key=api-key'))
        return response(200, {
          results:[{
            id:1396,
            name:'Breaking Bad',
            original_name:'Breaking Bad',
            first_air_date:'2008-01-20',
          }],
        })
      }
      if (value.includes('/tv/1396?')) {
        return response(200, {
          id:1396,
          name:'Breaking Bad',
          original_name:'Breaking Bad',
          first_air_date:'2008-01-20',
          number_of_seasons:5,
          number_of_episodes:62,
          seasons:[
            { id:3572, season_number:1, name:'Season 1', episode_count:7 },
          ],
          alternative_titles:{ results:[{ title:'Breaking Bad: Chemie des Todes' }] },
          external_ids:{ imdb_id:'tt0903747' },
        })
      }
      if (value.includes('/tv/1396/season/1')) {
        return response(200, {
          id:3572,
          name:'Season 1',
          season_number:1,
          episodes:[
            { id:62085, episode_number:1, season_number:1, name:'Pilot', runtime:59 },
            { id:62086, episode_number:2, season_number:1, name:'Cat’s in the Bag...', runtime:49 },
          ],
        })
      }
      throw new Error('Unexpected TMDB path ' + value)
    },
  })

  const found = await resolver.search('Breaking Bad', 'tv')
  assert.equal(found.matches[0].id, 1396)

  const show = await resolver.details(1396, 'tv')
  assert.equal(show.numberOfSeasons, 5)
  assert.equal(show.seasons[0].episodeCount, 7)
  assert(show.aliases.includes('Breaking Bad: Chemie des Todes'))
  assert.equal(show.imdbId, 'tt0903747')

  const season = await resolver.seasonDetails(1396, 1)
  assert.equal(season.episodes.length, 2)
  assert.equal(season.episodes[0].number, '1')
}

{
  const resolver = createTmdbResolver({ readAccessToken:'', apiKey:'' })
  const fallback = await resolver.search('Anything', 'movie')
  assert.equal(fallback.source, 'fallback')
  assert.deepEqual(fallback.aliases, ['Anything'])
  assert.equal(resolver.enabled(), false)
}


{
  let now = Date.parse('2026-10-03T00:00:00Z')
  const resolver = createTmdbResolver({
    apiKey:'api-key',
    now:() => now,
    fetchImpl:async url => {
      const value = String(url)
      if (value.includes('/tv/9001')) {
        return response(200, {
          id:9001,
          name:'Example Show',
          last_episode_to_air:{
            season_number:2,
            episode_number:6,
            air_date:'2026-10-02',
          },
        })
      }
      if (value.includes('/movie/9002')) {
        return response(200, {
          id:9002,
          title:'Future Film',
          release_date:'2026-10-04',
        })
      }
      throw new Error('Unexpected release path ' + value)
    },
  })

  const tv = await resolver.releaseState(9001, 'tv')
  assert.equal(tv.kind, 'episode')
  assert.equal(tv.season, 2)
  assert.equal(tv.number, 6)

  const moviePending = await resolver.releaseState(9002, 'movie')
  assert.equal(moviePending.kind, 'movie')
  assert.equal(moviePending.number, 0)

  now = Date.parse('2026-10-05T00:00:00Z')
  const resolver2 = createTmdbResolver({
    apiKey:'api-key',
    now:() => now,
    fetchImpl:async () => response(200, {
      id:9002,
      title:'Future Film',
      release_date:'2026-10-04',
    }),
  })
  const movieReleased = await resolver2.releaseState(9002, 'movie')
  assert.equal(movieReleased.number, 1)
}

console.log('PASS TMDB resolver selftest')
