import assert from 'node:assert/strict'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import tweetCommand from './commands/fun/tweet.js'
import postCommand from './commands/fun/post.js'
import { renderTweetCard, renderPostCard, wrapSocialText } from './utils/social-card-renderer.js'
import { socialCardText, socialCardMentions, resolveSocialAuthor, parseSocialEngagementOptions } from './utils/social-card-command.js'
import { resolveCommandMedia } from './utils/media-conversion.js'
import { dispatchCommand } from './command-registry.js'
import { randomTweetEngagement, randomPostEngagement, resolveTweetEngagement, resolvePostEngagement } from './utils/tweet-engagement.js'

// Each tweet is a mock card with fresh, nonzero engagement. The injected
// random functions make every popularity tier testable without flaky seeds.
const engagementAt = (popularity, pickHigh = false) => {
  let first = true
  return randomTweetEngagement((low, high) => {
    if (first) { first = false; return popularity }
    return pickHigh ? high - 1 : low
  })
}
for (const [bucket, lower, upper] of [
  [0,24,550],[39,24,550],
  [40,551,9900],[84,551,9900],
  [85,9901,105000],[97,9901,105000],
  [98,105001,800000],[99,105001,800000],
]) {
  const low = engagementAt(bucket)
  const high = engagementAt(bucket,true)
  assert.equal(low.likeCount,lower)
  assert.equal(high.likeCount,upper)
  for (const counts of [low,high]) {
    assert(counts.likeCount > 0)
    assert(counts.replyCount > 0)
    assert(counts.repostCount > 0)
    assert(counts.replyCount <= counts.likeCount)
    assert(counts.repostCount <= counts.likeCount)
  }
}
const randomSamples = Array.from({length:32}, () => randomTweetEngagement())
assert(new Set(randomSamples.map(e => e.likeCount)).size > 1)
assert(randomSamples.every(e => e.replyCount > 0 && e.repostCount > 0))
const randomCountsImage = await loadImage(await renderTweetCard({
  text:'Testing engagement!',authorName:'NIGHT',
  ...engagementAt(40),
}))
assert.equal(randomCountsImage.width,1000)

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
// WhatsApp's UI renders @names, but Baileys often sends a raw @LID in the
// message string. Only its matching contextInfo mention is rewritten.
const lid = '25693048041628@lid'
const anotherLid = '11993344771@hosted.lid'
const quoted = { mentionedJid:[lid] }
const mentionCtx = {
  message:{
    message:{
      extendedTextMessage:{
        text:'.tweet Hello @25693048041628 behavior',
        contextInfo:{ mentionedJid:[lid,anotherLid] },
      },
    },
  },
  resolveSocialMentionName:async jid => jid === lid ? 'TOMEX' : '𝙉𝙞𝙜𝙝𝙩 ✨',
}
assert.equal(
  await socialCardMentions(mentionCtx,'Hello @25693048041628 behavior'),
  'Hello @TOMEX behavior',
)
assert.equal(
  await socialCardMentions(mentionCtx,'@25693048041628 + @11993344771!\n@25693048041628'),
  '@TOMEX + @𝙉𝙞𝙜𝙝𝙩 ✨!\n@TOMEX',
)
assert.equal(
  await socialCardMentions(mentionCtx,'@25693048041628oops @998877665544 text'),
  '@25693048041628oops @998877665544 text',
)
assert.equal(
  await socialCardMentions({...mentionCtx,resolveSocialMentionName:async () => ''},
    'LID @25693048041628 should not leak'),
  'LID @member should not leak',
)
assert.equal(
  await socialCardMentions({...mentionCtx,resolveSocialMentionName:async () => '25693048041628'},
    'LID @25693048041628 should not leak'),
  'LID @member should not leak',
)
assert.equal(
  await socialCardMentions({...mentionCtx,resolveSocialMentionName:async () => {throw new Error('offline')}},
    '@25693048041628'),
  '@member',
)
assert.equal(
  await socialCardMentions({message:{message:{extendedTextMessage:{contextInfo:{quotedMessage:{
    extendedTextMessage:{contextInfo:quoted},
  }}}}},resolveSocialMentionName:async () => 'TOMEX'},'@25693048041628'),
  '@25693048041628',
)
const phoneMention = '2348012345678@s.whatsapp.net'
assert.equal(
  await socialCardMentions({
    message:{message:{extendedTextMessage:{contextInfo:{mentionedJid:[phoneMention]}}}},
    resolveSocialMentionName:async ()=>'Ayo',
  },'@2348012345678'),
  '@Ayo',
)

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


// Every .post flavor uses mock metrics; views are for stories and the other
// variants show likes/comments/shares instead of frozen zero counters.
const postEngagement = randomPostEngagement()
assert(postEngagement.likeCount > 0)
assert(postEngagement.commentCount > 0)
assert(postEngagement.shareCount > 0)
assert(postEngagement.viewCount > postEngagement.likeCount)
const lowestPost = randomPostEngagement((low, high) => high === undefined ? 0 : low)
assert.equal(lowestPost.likeCount,24)
assert(lowestPost.viewCount > lowestPost.likeCount)

for (const [mode,w,h] of [
  ['generic',1000,760],
  ['instagram',1000,1120],
  ['facebook',1000,760],
  ['story',1080,1920],
]) {
  const mock = await renderPostCard({
    style:mode,text:'Line one 😎\nLine two 🎉',
    authorName:'Actual WhatsApp Name',
    imageBuffer:samplePhoto,
    ...postEngagement,
  })
  const img = await loadImage(mock)
  assert.equal(img.width,w)
  assert(img.height >= h)
  assert.equal(mock[0],0x89)
}
const instaLong = await renderPostCard({
  style:'instagram',text:'one\ntwo\nthree\nfour\nfive\nsix',
  ...postEngagement,
})
assert((await loadImage(instaLong)).height > 1120)
const instagramZero = await renderPostCard({style:'instagram',text:'Hello!',likeCount:0,commentCount:0})
const instagramEngaged = await renderPostCard({
  style:'instagram',text:'Hello!',likeCount:12457,commentCount:700,
})
assert.notDeepEqual(instagramZero,instagramEngaged)
const fbZero = await renderPostCard({style:'facebook',text:'Hello!'})
const fbEngaged = await renderPostCard({
  style:'facebook',text:'Hello!',likeCount:12457,commentCount:700,shareCount:100,
})
assert.notDeepEqual(fbZero,fbEngaged)
const genericZero = await renderPostCard({style:'generic',text:'Hello!'})
const genericEngaged = await renderPostCard({
  style:'generic',text:'Hello!',likeCount:12457,commentCount:700,shareCount:100,
})
assert.notDeepEqual(genericZero,genericEngaged)

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
assert.equal(replies.at(-1), 'Use .post [instagram|facebook|story] [@user] <text>, or reply to a photo with .post')


// Custom fake engagement controls: exact numbers, suffixes, comma grouping,
// zero, invalid/duplicate options, Unicode/newlines and actual command output.
const customArgs = { rawArgs:'dark @friend --likes=1.2k --replies 35 --reposts=0 First line ✨\nSecond line', args:[] }
assert.deepEqual(
  parseSocialEngagementOptions(customArgs,2),
  { text:'First line ✨\nSecond line', engagement:{likeCount:1200,replyCount:35,repostCount:0},error:null },
)
assert.deepEqual(parseSocialEngagementOptions({rawArgs:'--likes=2m Hello'}).engagement,{likeCount:2_000_000})
assert.deepEqual(parseSocialEngagementOptions({rawArgs:'--likes=1,234 Hi'}).engagement,{likeCount:1234})
assert.equal(parseSocialEngagementOptions({rawArgs:'The likes=200 are a joke'}).text,'The likes=200 are a joke')
assert.match(parseSocialEngagementOptions({rawArgs:'--likes=hello Hi'}).error,/Invalid --likes/)
assert.match(parseSocialEngagementOptions({rawArgs:'--likes  Hi'}).error,/Invalid --likes/)
assert.match(parseSocialEngagementOptions({rawArgs:'--replies=5 --replies=7 Hi'}).error,/more than once/)
assert.match(parseSocialEngagementOptions({rawArgs:'--likes=1000000000 Hi'}).error,/Invalid --likes/)
assert.match(parseSocialEngagementOptions({rawArgs:'--likes=50 --comments=10 Hi'}).error,/not available/)
assert.deepEqual(
  parseSocialEngagementOptions({rawArgs:'instagram --likes=120 --replies 3 --shares=2 Picture'},1,{type:'post',style:'instagram'}),
  {text:'Picture',engagement:{likeCount:120,commentCount:3,shareCount:2},error:null},
)
assert.match(parseSocialEngagementOptions({rawArgs:'story --likes=50 Hi'},1,{type:'post',style:'story'}).error,/not available/)
assert.equal(parseSocialEngagementOptions({rawArgs:'story --views=20 Hello'},1,{type:'post',style:'story'}).engagement.viewCount,20)
assert.deepEqual(resolveTweetEngagement({likeCount:0,replyCount:0,repostCount:0}),
  {likeCount:0,replyCount:0,repostCount:0})
assert.deepEqual(resolveTweetEngagement({likeCount:100,replyCount:5,repostCount:3}),
  {likeCount:100,replyCount:5,repostCount:3})
const autoTweet = resolveTweetEngagement({likeCount:100},(low,high)=>high===undefined?0:low)
assert.equal(autoTweet.likeCount,100)
assert(autoTweet.replyCount>0 && autoTweet.replyCount<100)
assert(autoTweet.repostCount>0 && autoTweet.repostCount<100)
assert.deepEqual(resolvePostEngagement({likeCount:0,commentCount:0,shareCount:0,viewCount:0}),
  {likeCount:0,commentCount:0,shareCount:0,viewCount:0})
const autoPost = resolvePostEngagement({likeCount:100},(low,high)=>high===undefined?0:low)
assert.equal(autoPost.likeCount,100)
assert(autoPost.viewCount>autoPost.likeCount)

const customTweetCtx = {
  ...ctx,
  args:['dark','--likes=1.2k','--replies=35','--reposts=0','Hello','🌙'],
  rawArgs:'dark --likes=1.2k --replies=35 --reposts=0 Hello 🌙',
}
await tweetCommand.run(customTweetCtx)
assert.equal(sent.length,3)
assert.deepEqual(sent.at(-1).payload.image,await renderTweetCard({
  theme:'dark',text:'Hello 🌙',authorName:'Owner User',
  likeCount:1200,replyCount:35,repostCount:0,
}))

const customPostCtx = {
  ...ctx,
  args:['instagram','--likes=400','--comments=12','--shares=5','Hello!'],
  rawArgs:'instagram --likes=400 --comments=12 --shares=5 Hello!',
}
await postCommand.run(customPostCtx)
assert.equal(sent.length,4)
// The randomized internal view count is not displayed on an Instagram card.
assert.deepEqual(sent.at(-1).payload.image,await renderPostCard({
  style:'instagram',text:'Hello!',authorName:'Owner User',
  likeCount:400,commentCount:12,shareCount:5,viewCount:401,
}))

await tweetCommand.run({...ctx,args:['--likes=oops','Hi'],rawArgs:'--likes=oops Hi'})
assert.equal(sent.length,4)
assert.match(replies.at(-1),/Invalid --likes/)

console.log('PASS Night tweet & post custom engagement, images, paragraphs, names & glyphs')
