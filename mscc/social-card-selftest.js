import assert from 'node:assert/strict'
import { loadImage } from '@napi-rs/canvas'
import tweetCommand from './commands/fun/tweet.js'
import postCommand from './commands/fun/post.js'
import { renderTweetCard, renderPostCard } from './utils/social-card-renderer.js'

const tweetLight = await renderTweetCard({
  text:'Some days you just move different.',
  theme:'light',
  authorName:'Kai',
})
assert.ok(Buffer.isBuffer(tweetLight))
assert.equal(tweetLight[0], 0x89)
const lightImage = await loadImage(tweetLight)
assert.equal(lightImage.width, 1000)
assert.equal(lightImage.height, 650)

const tweetDark = await renderTweetCard({
  text:'Discipline today, freedom tomorrow.',
  theme:'dark',
  authorName:'Zayn',
})
const darkImage = await loadImage(tweetDark)
assert.equal(darkImage.width, 1000)
assert.equal(darkImage.height, 650)

for (const [style, width, height] of [
  ['generic',1000,760],
  ['instagram',1000,1120],
  ['facebook',1000,760],
  ['story',1080,1920],
]) {
  const image = await renderPostCard({
    text:'Better days ahead.',
    style,
    authorName:'Nami',
  })
  const decoded = await loadImage(image)
  assert.equal(decoded.width, width)
  assert.equal(decoded.height, height)
}

const sent = []
const replies = []
const ctx = {
  args:['dark','@friend','Not everyone will get it, and that is fine.'],
  publicPrefix:'.',
  userKey:'2348000000000',
  message:{
    key:{ remoteJid:'group@g.us' },
    pushName:'Owner User',
  },
  account:{
    sock:{
      async sendMessage(chat, payload, options) {
        sent.push({ chat, payload, options })
        return payload
      },
    },
  },
  reply:async value => { replies.push(String(value)); return value },
  resolveCommandTarget:async raw => {
    assert.equal(raw, '@friend')
    return { phoneNumber:'2348111111111', source:'mention' }
  },
  getPublicUserProfile:async phone => ({
    phoneNumber:phone,
    displayName:phone === '2348111111111' ? 'Friend User' : 'Owner User',
    photoUrl:'',
  }),
}

await tweetCommand.run(ctx)
assert.equal(sent.length, 1)
assert.equal(sent[0].chat, 'group@g.us')
assert.ok(Buffer.isBuffer(sent[0].payload.image))
assert.equal(sent[0].payload.mimetype, 'image/png')
assert.equal(sent[0].options.quoted, ctx.message)

ctx.args = ['story','New chapter. Same me, just better.']
await postCommand.run(ctx)
assert.equal(sent.length, 2)
const story = await loadImage(sent[1].payload.image)
assert.equal(story.width, 1080)
assert.equal(story.height, 1920)

ctx.args = ['instagram']
await postCommand.run(ctx)
assert.equal(replies.at(-1), 'Use .post [instagram|facebook|story] [@user] <text>')

console.log('PASS Night tweet/post cards, variants, targeting, PNG rendering, and tiny watermark renderer')
