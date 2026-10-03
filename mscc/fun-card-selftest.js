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
    assert.match(String(url), /animemes/)
    return {
      ok:true,
      async json() {
        return { data:{ children:[
          { data:{ title:'anime meme', url:'https://example.com/anime.png', subreddit:'animemes', over_18:false } },
        ]}}
      },
    }
  },
})
assert.equal(meme.title, 'anime meme')
assert.equal(meme.subreddit, 'animemes')

console.log('PASS Night fun cards, meme parser, and joke fallback plumbing')
