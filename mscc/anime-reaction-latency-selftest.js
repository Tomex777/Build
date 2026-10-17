// Fast reactions must not redo two HTTPS requests + FFmpeg on every message.
import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runFfmpeg } from './utils/media-conversion.js'
import { sendReactionSticker, animeReactionCacheStats } from './anime-reaction-stickers.js'

const directory = await mkdtemp(join(tmpdir(), 'night-reaction-latency-'))
let gif
try {
  const source=join(directory,'reaction.gif')
  await runFfmpeg(['-f','lavfi','-i','testsrc=duration=1:size=128x128:rate=8','-loop','0',source])
  gif=await readFile(source)
} finally { await rm(directory,{recursive:true,force:true}) }
const previousFetch=globalThis.fetch
let metadataCalls=0, gifCalls=0
const sent=[],replies=[]
function ctx(senderName) {
  return {
    args:[], senderName,
    message:{key:{remoteJid:'performance-test@g.us'},message:{conversation:'.kiss'}},
    account:{sock:{async sendMessage(chat,payload,opts){sent.push({chat,payload,opts});return {key:{id:'ok'}}}}},
    reply:async message => {replies.push(String(message))},
  }
}
const actor=ctx('Alpha')
const secondPerson=ctx('Bravo')
function mockGif(url) {
  const value=String(url)
  if(value.startsWith('https://animegifs-enkidu.koyeb.app/v3/api/')) {
    metadataCalls++
    return Promise.resolve(new Response(JSON.stringify({
      gif:'https://frqs54dqeajn.objectstorage.eu-frankfurt-1.oci.customer-oci.com/kiss.gif',
    }),{status:200,headers:{'content-type':'application/json'}}))
  }
  if(value==='https://frqs54dqeajn.objectstorage.eu-frankfurt-1.oci.customer-oci.com/kiss.gif') {
    gifCalls++
    return Promise.resolve(new Response(gif,{status:200,headers:{'content-type':'image/gif'}}))
  }
  throw new Error('Unexpected provider: '+value)
}
try {
  globalThis.fetch=mockGif
  // Three users invoke .kiss in the same instant (one has a different
  // personalized pack name). Share work only among identical pack credits.
  await Promise.all([
    sendReactionSticker(actor,'kiss'),
    sendReactionSticker(actor,'kiss'),
    sendReactionSticker(actor,'kiss'),
  ])
  assert.equal(sent.length,3,'every request still sends its own sticker')
  assert.equal(metadataCalls,1,'one provider metadata fetch for three concurrent users')
  assert.equal(gifCalls,1,'one GIF download for concurrent identical cards')
  assert.ok(sent.every(item=>item.payload.sticker.toString('ascii',8,12)==='WEBP'),'keep animated WebP')
  assert.ok(sent[0].payload.sticker.equals(sent[2].payload.sticker),'single-flight prepared sticker is shared')
  assert.equal(replies.length,0)

  // Even after provider access is revoked, cached messages must send without
  // waiting through provider timeouts or running FFmpeg again.
  globalThis.fetch=async ()=> {throw new Error('No network permitted on warm path')}
  await sendReactionSticker(actor,'kiss')
  assert.equal(sent.length,4)
  assert.equal(metadataCalls,1)
  assert.equal(gifCalls,1)
  assert.equal(replies.length,0)
  assert.ok(sent[3].payload.sticker.equals(sent[0].payload.sticker))

  // Sender-specific EXIF metadata must never leak across cache partitions.
  globalThis.fetch=mockGif
  await sendReactionSticker(secondPerson,'kiss')
  assert.equal(metadataCalls,2,'new sticker pack generates independently')
  assert.equal(gifCalls,2)
  assert.ok(sent.at(-1).payload.sticker.includes(Buffer.from('Bravo')))
  assert.ok(!sent.at(-1).payload.sticker.includes(Buffer.from('Alpha')))

  // A failed HTTP source must be observable, not leave a permanently rejected
  // Promise in the in-flight map for the next request.
  const failed=ctx('Retry')
  globalThis.fetch=async()=>new Response('down',{status:503,headers:{'content-type':'text/html'}})
  await Promise.all([
    sendReactionSticker(failed,'bored'),
    sendReactionSticker(failed,'bored'),
  ])
  assert.equal(replies.length,2,'both callers receive an explicit failure')
  globalThis.fetch=async url=>{
    if(String(url).includes('nekos.best/api/v2/bored?')) {
      return new Response(JSON.stringify({results:[{url:'https://nekos.best/api/v2/bored/test.gif'}]}),
        {headers:{'content-type':'application/json'}})
    }
    if(String(url)==='https://nekos.best/api/v2/bored/test.gif') {
      return new Response(gif,{headers:{'content-type':'image/gif'}})
    }
    throw new Error('Unexpected retry URL: '+url)
  }
  await sendReactionSticker(failed,'bored')
  assert.equal(sent.length,6,'new request retries successfully after earlier failure')
  assert.equal(sent.at(-1).payload.sticker.toString('ascii',8,12),'WEBP')

  const metrics=animeReactionCacheStats()
  assert.ok(metrics.keys<=24)
  assert.ok(metrics.variants<=metrics.keys*2)
  assert.ok(metrics.bytes<=metrics.maxBytes)
  console.log('PASS reaction speed: concurrent single-flight, instant warm cache, separate EXIF packs, bounded RAM, and retry recovery')
} finally {globalThis.fetch=previousFetch}
