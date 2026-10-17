import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { runFfmpeg } from './utils/media-conversion.js'
import { reactionProviders, secureGifUrl, APPROVED_REACTION_HOSTS } from './utils/anime-reaction-providers.js'
// Isolate this provider-failover fixture from the registry test's warmed .smug
// cache: the production cache-first path correctly skips upstream providers.
import { REACTION_NAMES, REACTIONS, sendReactionSticker } from './anime-reaction-stickers.js?failover-fixture'

assert.equal(REACTION_NAMES.length,60)
for (const key of REACTION_NAMES) {
  const category = REACTIONS[key]
  const providers = reactionProviders(category)
  assert.ok(providers.length > 0,'no provider for ' + key)
  assert.equal(new Set(providers.map(p => p.name)).size,providers.length)
}
for (const name of ['hug','kiss','pat','cry','blush','bonk','dance','facepalm','handhold','pout','punch','sip','slap','tickle','wave','yawn']) {
  assert.equal(reactionProviders(name)[0].name,'enkidu','working alternate should be prioritized: ' + name)
}
assert.equal(reactionProviders('kill')[0].name,'enkidu')
assert.ok(reactionProviders('smug').some(p => p.name === 'otaku'))
assert.ok(reactionProviders('smug').some(p => p.name === 'waifu'))
assert.ok(reactionProviders('akick').length === 0,'akick is only a WhatsApp command alias')
assert.ok(reactionProviders('kick').some(p => p.name === 'waifu'))

for(const source of ['enkidu','nekos','waifu','gifukai','otaku','anibox']){
  for(const badUrl of [
    'http://127.0.0.1/x.gif','https://evil.com/x.gif','https://nekos.best.evil.com/x.gif',
    'https://localhost/x.gif','file:///etc/passwd','https://admin:secret@cdn.otakugifs.xyz/gifs/smug/2.gif',
    'https://cdn-anibox.pages.dev/hug/2.png',
  ]) assert.throws(()=>secureGifUrl(badUrl,source,'hug'),badUrl)
}
for(const [source,url,category] of [
  ['nekos','https://nekos.best/api/v2/hug/abc.gif','hug'],
  ['enkidu','https://frqs54dqeajn.objectstorage.eu-frankfurt-1.oci.customer-oci.com/file.gif','hug'],
  ['waifu','https://i.waifu.pics/demo.gif','hug'],
  ['gifukai','https://cdn.gifukai.com/hug/a.gif','hug'],
  ['otaku','https://cdn.otakugifs.xyz/gifs/smug/one.gif','smug'],
  ['anibox','https://cdn-anibox.pages.dev/cry/1.gif','cry'],
]) {
  assert.ok(APPROVED_REACTION_HOSTS.includes(new URL(url).hostname))
  assert.equal(secureGifUrl(url,source,category),url)
}
assert.throws(()=>secureGifUrl('https://cdn-anibox.pages.dev/hug/2.gif','anibox','cry'))

const directory = await mkdtemp(join(tmpdir(),'night-reaction-failover-'))
let gif
try {
  const file=join(directory,'sample.gif')
  await runFfmpeg(['-f','lavfi','-i','testsrc=duration=0.8:size=128x128:rate=6','-loop','0',file])
  gif=await readFile(file)
} finally { await rm(directory,{recursive:true,force:true}) }
assert.equal(gif.toString('ascii',0,6),'GIF89a')

const oldFetch=globalThis.fetch
const sent=[]
const replies=[]
const visits=[]
const ctx={
  args:[],message:{key:{remoteJid:'test@g.us'},message:{conversation:'.smug'}},publicPrefix:'.',
  account:{sock:{async sendMessage(_chat,payload){sent.push(payload)}}},
  reply:async message=>{replies.push(String(message))},
}
function fail(status=503) {return new Response('<html>down</html>',{status,headers:{'content-type':'text/html'}})}
function js(data){return new Response(JSON.stringify(data),{status:200,headers:{'content-type':'application/json'}})}
function img(){return new Response(gif,{status:200,headers:{'content-type':'image/gif'}})}
try{
  // Simulate Nekos blocked on Azure: .smug must succeed from an exact OtakuGIFs
  // reaction, not from a random unrelated action or a returned external host.
  globalThis.fetch=async url=>{
    visits.push(String(url))
    if(String(url).startsWith('https://api.otakugifs.xyz/')) return js({url:'https://cdn.otakugifs.xyz/gifs/smug/1.gif'})
    if(String(url).startsWith('https://cdn.otakugifs.xyz/')) return img()
    return fail()
  }
  await sendReactionSticker(ctx,'smug')
  assert.equal(sent.length,1,'smug should send one sticker after primary outage')
  assert.equal(sent[0].sticker.toString('ascii',8,12),'WEBP')
  assert.ok(visits.some(x=>x.includes('nekos.best')))
  assert.ok(visits.some(x=>x.includes('api.otakugifs.xyz')))
  assert.equal(replies.length,0)

  // If both Nekos and Otaku fail, a curated matching Anibox reaction works.
  visits.length=0
  globalThis.fetch=async url=>{
    visits.push(String(url))
    if(String(url)==='https://cdn-anibox.pages.dev/index.json') return js({cry:3})
    if(/^https:\/\/cdn-anibox.pages.dev\/cry\/[1-3]\.gif$/.test(String(url))) return img()
    return fail()
  }
  await sendReactionSticker(ctx,'cry')
  assert.equal(sent.length,2)
  assert.equal(sent.at(-1).sticker.toString('ascii',8,12),'WEBP')
  assert.ok(visits.some(x=>x.includes('animegifs-enkidu')))
  assert.ok(visits.some(x=>x.includes('cdn-anibox.pages.dev')))
  assert.equal(replies.length,0)

  // Maintain .kill even when its original source is down.
  globalThis.fetch=async url=>{
    if(String(url)==='https://api.waifu.pics/sfw/kill') return js({url:'https://i.waifu.pics/kill1.gif'})
    if(String(url)==='https://i.waifu.pics/kill1.gif') return img()
    return fail()
  }
  await sendReactionSticker(ctx,'kill')
  assert.equal(sent.length,3)
  assert.equal(sent.at(-1).sticker.toString('ascii',8,12),'WEBP')

  // A cache hit should preserve animated WebP (and avoid inventing GIF sends)
  // when every source later fails.
  globalThis.fetch=async()=>fail()
  await sendReactionSticker(ctx,'smug')
  assert.equal(sent.length,4)
  assert.equal(sent.at(-1).sticker.toString('ascii',8,12),'WEBP')
  assert.equal(replies.length,0)

  // Never forward a malicious JSON media location to the host even when a
  // provider is supposedly reputable. Last-good result is for its exact
  // category only.
  globalThis.fetch=async url=>{
    if(String(url).startsWith('https://api.otakugifs.xyz/')) return js({url:'https://127.0.0.1/secret.gif'})
    return fail()
  }
  await sendReactionSticker(ctx,'laugh')
  assert.equal(sent.length,4)
  assert.match(replies.at(-1),/could not load/i)
  console.log('PASS source/provider fallback, safe GIF URLs, WebP output, cached reuse, and existing .kill')
} finally {globalThis.fetch=oldFetch}
