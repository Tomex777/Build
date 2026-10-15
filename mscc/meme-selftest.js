import assert from 'node:assert/strict'
import {
  memeSubreddits, parseMemeApi, parseRedditMemes, randomMeme, randomDarkMeme,
} from './fun-content.js'
import { loadCommands } from './command-registry.js'
import darkMemeCommand from './commands/fun/darkmeme.js'

assert.ok(memeSubreddits().length >= 20, 'Default memes should cover 20+ communities')
assert.ok(memeSubreddits('dark').length >= 10, 'Dark memes should cover 10+ communities')
assert.notDeepEqual(memeSubreddits('dark'), memeSubreddits(), 'Dark pool should differ from regular')
assert.deepEqual(memeSubreddits('programmer'), memeSubreddits('programming'))
assert.deepEqual(memeSubreddits('darkmeme'), memeSubreddits('dark'))

const valid = { title:'Safe', url:'https://i.redd.it/safe.png', subreddit:'memes' }
assert.deepEqual(parseMemeApi({ memes:[
  { ...valid, nsfw:true },
  { ...valid, spoiler:true },
  { ...valid, url:'https://example.com/movie.mp4' },
  valid,
]}).map(row => row.url), [valid.url])

const redditPosts = { data:{ children:[
  { data:{ title:'Over 18', over_18:true, url:'https://i.redd.it/no.png' } },
  { data:{ title:'Spoiler', spoiler:true, url:'https://i.redd.it/no2.png' } },
  { data:{ title:'Video', is_video:true, url:'https://i.redd.it/no3.png' } },
  { data:{ title:'Normal', subreddit:'memes', url:'https://i.redd.it/yes.jpg' } },
] } }
assert.deepEqual(parseRedditMemes(redditPosts).map(row => row.title), ['Normal'])

const apiCalls = []
const fakeApi = async input => {
  const url = String(input)
  apiCalls.push(url)
  const subreddit = decodeURIComponent(new URL(url).pathname.split('/')[2] || 'unknown')
  return {
    ok:true,
    headers:{ get:() => 'application/json' },
    async json() {
      return { memes:[
        { title:'First from ' + subreddit, subreddit, url:'https://i.redd.it/' + subreddit + '-first.png' },
        { title:'Second from ' + subreddit, subreddit, url:'https://i.redd.it/' + subreddit + '-second.jpg' },
      ] }
    },
  }
}

const regular = await randomMeme('', { fetchImpl:fakeApi, random:() => 0 })
assert.ok(regular.url.startsWith('https://i.redd.it/'))
assert.equal(new Set(apiCalls.map(url => decodeURIComponent(new URL(url).pathname.split('/')[2]))).size, 3,
  'Each call should sample three distinct subreddits')
assert.ok(apiCalls.every(url => url.startsWith('https://meme-api.com/gimme/')))
const second = await randomMeme('', { fetchImpl:fakeApi, random:() => 0 })
assert.notEqual(second.url, regular.url, 'Do not immediately repeat the same meme')

const darkCalls = []
const dark = await randomDarkMeme({
  fetchImpl: async (...args) => { darkCalls.push(String(args[0])); return fakeApi(...args) },
  random:() => 0.2,
})
assert.ok(memeSubreddits('dark').includes(dark.subreddit))
assert.equal(darkCalls.length, 3)
assert.ok(darkCalls.every(url => memeSubreddits('dark').includes(decodeURIComponent(new URL(url).pathname.split('/')[2]))))

const redditCalls = []
const htmlApi = async input => {
  const url = String(input)
  redditCalls.push(url)
  if (url.includes('meme-api.com')) {
    return {
      ok:true,
      headers:{ get:() => 'text/html' },
      async json() { throw new Error('Unexpected token <') },
    }
  }
  const subreddit = decodeURIComponent(new URL(url).pathname.split('/')[2] || 'unknown')
  return {
    ok:true,
    headers:{ get:() => 'application/json; charset=utf-8' },
    async json() {
      return { data:{ children:[
        { data:{ title:'A Reddit fallback', subreddit, over_18:true, url:'https://i.redd.it/nope.png' } },
        { data:{ title:'A safe Reddit fallback', subreddit, url:'https://i.redd.it/' + subreddit + '-fallback.jpg' } },
      ] } }
    },
  }
}
const fallback = await randomMeme('anime', { fetchImpl:htmlApi, random:() => 0.4 })
assert.equal(fallback.title, 'A safe Reddit fallback')
assert.ok(redditCalls.some(url => url.includes('reddit.com/r/')))

const unavailable = async () => ({
  ok:true,
  headers:{ get:() => 'text/html' },
  async json() { throw new Error('Unexpected token <') },
})
await assert.rejects(
  () => randomDarkMeme({ fetchImpl:unavailable, random:() => 0 }),
  error => /temporarily unavailable/.test(error.message) && !/Unexpected token|<body/.test(error.message),
)

const registry = await loadCommands(new URL('./commands/', import.meta.url), { capabilityFromDirectory:true })
assert.equal(registry.commands.get('darkmeme'), registry.commands.get('dmeme'))
let imageSent = null
const savedFetch = globalThis.fetch
globalThis.fetch = fakeApi
try {
  await darkMemeCommand.run({
    sendImageUrl:async (url, caption) => { imageSent = { url, caption } },
    reply:async message => { throw new Error('Unexpected error reply: ' + message) },
  })
} finally {
  globalThis.fetch = savedFetch
}
assert.ok(imageSent?.url)
assert.match(imageSent.caption, /r\/[^\s]+/)
assert.match(imageSent.caption, /Night/)

console.log('PASS diverse regular/dark meme pools, anti-repeat, NSFW filtering, HTML fallback, and command registration')
