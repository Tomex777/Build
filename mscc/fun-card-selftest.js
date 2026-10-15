import assert from 'node:assert/strict'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import {
  renderAchievementCard,
  renderCaptionCard,
  renderJailCard,
  renderQuoteCard,
  renderWantedCard,
  renderWastedCard,
} from './utils/fun-card-renderer.js'
import { parseRedditMemes, randomJoke, randomMeme } from './fun-content.js'

const quote = await loadImage(await renderQuoteCard({ text:'Keep moving forward.', authorName:'Kai' }))
assert.equal(quote.width, 1080)
assert.equal(quote.height, 1080)

const wanted = await loadImage(await renderWantedCard({ authorName:'Kai' }))
assert.equal(wanted.width, 900)
assert.equal(wanted.height, 1200)

const jail = await loadImage(await renderJailCard({ authorName:'Kai' }))
assert.equal(jail.width, 900)
assert.equal(jail.height, 1000)

const wasted = await loadImage(await renderWastedCard({ authorName:'Kai' }))
assert.equal(wasted.width, 1000)
assert.equal(wasted.height, 1000)

const achievement = await loadImage(await renderAchievementCard({ text:'Finished the mission' }))
assert.equal(achievement.width, 1100)
assert.equal(achievement.height, 420)

const src = createCanvas(480, 320)
const srcCtx = src.getContext('2d')
srcCtx.fillStyle = '#222'
srcCtx.fillRect(0,0,480,320)
srcCtx.fillStyle = '#fff'
srcCtx.font = '40px sans-serif'
srcCtx.fillText('TEST', 150, 180)
const caption = await loadImage(await renderCaptionCard({
  imageBuffer:src.toBuffer('image/png'),
  text:'this is cinema',
}))
assert.equal(caption.width, 1080)
assert.equal(caption.height, 1080)

const parsed = parseRedditMemes({
  data:{ children:[
    { data:{ title:'safe', url:'https://example.com/a.jpg', subreddit:'memes', over_18:false } },
    { data:{ title:'nsfw', url:'https://example.com/b.jpg', subreddit:'memes', over_18:true } },
    { data:{ title:'not image', url:'https://example.com/page', subreddit:'memes', over_18:false } },
  ]},
})
assert.equal(parsed.length, 1)
assert.equal(parsed[0].title, 'safe')

const joke = await randomJoke({
  fetchImpl:async () => ({
    ok:true,
    async json() { return { type:'twopart', setup:'Setup?', delivery:'Punchline.' } },
  }),
})
assert.equal(joke, 'Setup?\n\nPunchline.')

const meme = await randomMeme('anime', {
  fetchImpl:async url => {
    assert.ok(String(url).startsWith('https://meme-api.com/gimme/'))
    return {
      ok:true,
      headers:{ get:() => 'application/json' },
      async json() {
        return { memes:[
          { title:'anime meme', url:'https://example.com/anime.png', subreddit:'animemes', nsfw:false },
          { title:'unsafe meme', url:'https://example.com/nsfw.png', subreddit:'animemes', nsfw:true },
          { title:'video', url:'https://example.com/clip.mp4', subreddit:'animemes', nsfw:false },
        ]}
      },
    }
  },
})
assert.equal(meme.title, 'anime meme')
assert.equal(meme.subreddit, 'animemes')

// Regression: Reddit/provider blocks return HTML, not JSON.
const urls = []
const fallbackMeme = await randomMeme('wholesome', {
  fetchImpl:async url => {
    urls.push(String(url))
    if (String(url).includes('meme-api.com')) {
      return { ok:true, headers:{ get:() => 'text/html' }, async json() { throw Error('HTML parsed as JSON') } }
    }
    return {
      ok:true,
      headers:{ get:() => 'application/json; charset=utf-8' },
      async json() {
        return { data:{ children:[{ data:{
          title:'wholesome fallback', url:'https://example.com/wholesome.jpg', subreddit:'wholesomememes',
        }}]}}
      },
    }
  },
})
assert.equal(fallbackMeme.title, 'wholesome fallback')
assert.ok(urls.length >= 2)
assert.ok(urls.some(url => url.includes('reddit.com/r/')))

// A blocked upstream should never reply with "Unexpected token '<'".
await assert.rejects(
  randomMeme('', { fetchImpl:async () => ({
    ok:true, headers:{ get:() => 'application/json' }, async json() { throw SyntaxError("Unexpected token '<'") },
  }) }),
  error => error.message.startsWith('Meme feeds are temporarily unavailable.'),
)

// An HTTP error from the primary provider must also use the fallback.
const recovered = await randomMeme('programming', {
  fetchImpl:async url => String(url).includes('meme-api.com')
    ? { ok:false, status:503, headers:{ get:() => 'text/html' } }
    : { ok:true, async json() {
      return { data:{ children:[{ data:{ title:'programmer fallback', url:'https://example.com/programming.webp' } }]}}
    }},
})
assert.equal(recovered.title, 'programmer fallback')

console.log('PASS Night fun cards, meme parser, and joke fallback plumbing')
