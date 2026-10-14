import assert from 'node:assert/strict'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import tweetCommand from './commands/fun/tweet.js'
import postCommand from './commands/fun/post.js'
import { renderTweetCard, renderPostCard, wrapSocialText } from './utils/social-card-renderer.js'
import { socialCardText, resolveSocialAuthor } from './utils/social-card-command.js'
import { resolveCommandMedia } from './utils/media-conversion.js'
import { dispatchCommand } from './command-registry.js'

const tweetLight = await renderTweetCard({
  text:'Some days you just move different.',
  theme:'light',
  authorName:'Kai',
})
assert.ok(Buffer.isBuffer(tweetLight))
assert.equal(tweetLight[0], 0x89)
const lightImage = await loadImage(tweetLight)
assert.equal(lightImage.width, 1000)
assert.ok(lightImage.height >= 480 && lightImage.height < 800)

const tweetDark = await renderTweetCard({
  text:'Discipline today, freedom tomorrow.',
  theme:'dark',
  authorName:'Zayn',
})
const darkImage = await loadImage(tweetDark)
assert.equal(darkImage.width, 1000)
assert.ok(darkImage.height >= 480 && darkImage.height < 800)

// Original command whitespace survives tokenization and theme/mention prefixes.
let rawCaptured = null
await dispatchCommand({
  commands:new Map([['probe',{name:'probe',run:async ctx => { rawCaptured = ctx.rawArgs }}]]),
  canonical:[],
}, '.probe first line✨\nsecond line\n\nthird line', {
  privateControl:true,
  reply:async()=>{},
}, {scope:'private'})
assert.equal(rawCaptured,'first line✨\nsecond line\n\nthird line')

const richText = 'First line — 🎉\nSecond line 👨‍👩‍👧‍👦\n\nThe last line #cool!'
assert.equal(socialCardText({rawArgs:'dark @friend ' + richText},2),richText)
const measure = createCanvas(1000, 100).getContext('2d')
measure.font = '35px sans-serif'
const wrapped = wrapSocialText(measure, richText, 892, 18)
assert.equal(wrapped.lines[2],'')
assert(wrapped.lines.join('\n').includes('👨‍👩‍👧‍👦'))
const overlong = wrapSocialText(measure, 'é'.repeat(500), 220, 10)
assert(overlong.lines.every(row => measure.measureText(row).width <= 226))
assert.equal(overlong.clipped,true)

const sampleCanvas = createCanvas(640, 400)
const sampleCtx = sampleCanvas.getContext('2d')
sampleCtx.fillStyle='#ff00ee'
sampleCtx.fillRect(0,0,640,400)
const samplePhoto = sampleCanvas.toBuffer('image/png')
const withPhoto = await loadImage(await renderTweetCard({
  text:richText, authorName:'𝒫𝒶𝓉𝓇𝒾𝒸𝓀 🤍', imageBuffer:samplePhoto,
}))
assert.equal(withPhoto.width,1000)
assert(withPhoto.height>800)
const onlyPhoto = await loadImage(await renderTweetCard({
  text:'', authorName:'Real WhatsApp User', imageBuffer:samplePhoto,
}))
assert(onlyPhoto.height>750)

const directPhoto=resolveCommandMedia({
  message:{key:{remoteJid:'group@g.us'},message:{imageMessage:{mimetype:'image/jpeg'}}},
},['image'])
assert.equal(directPhoto.kind,'image')
const repliedPhoto=resolveCommandMedia({
  message:{
    key:{remoteJid:'group@g.us'},
    message:{extendedTextMessage:{text:'.tweet',contextInfo:{
      stanzaId:'pic1', participant:'111@s.whatsapp.net',
      quotedMessage:{imageMessage:{mimetype:'image/jpeg'}},
    }}},
  },
},['image'])
assert.equal(repliedPhoto.kind,'image')

const realName = await resolveSocialAuthor({
  args:['hello'],userKey:'2348000000000',message:{pushName:'Real public WhatsApp name'},
  getPublicUserProfile:async()=>({displayName:'Saved contact nickname',photoUrl:''}),
},['hello'])
assert.equal(realName.authorName,'Real public WhatsApp name')

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

console.log('PASS Night rich tweet photos, line breaks, Unicode, push names, targeting and post cards')
