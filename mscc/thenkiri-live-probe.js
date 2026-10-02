import { browseTheNkiri } from './providers/thenkiri.js'

const movies = await browseTheNkiri('movie')
const tv = await browseTheNkiri('tv')
const summary = {
  movieCount:movies.length,
  tvCount:tv.length,
  movieSample:movies.slice(0,3).map(row => ({ id:row.id, title:row.title, year:row.year, available:row.available })),
  tvSample:tv.slice(0,3).map(row => ({ id:row.id, title:row.title, year:row.year, available:row.available })),
}
if (!movies.length && !tv.length) throw new Error('TheNkiri catalog returned no usable Movies/TV rows')
console.log(JSON.stringify(summary,null,2))
