import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { loadCommands, dispatchCommand } from './command-registry.js'
import { runFfmpeg } from './utils/media-conversion.js'
import { DEFAULT_STICKER_PUBLISHER } from './utils/sticker-metadata.js'
import { REACTIONS, REACTION_NAMES, reactionCatalog } from './anime-reaction-stickers.js'

const publicRegistry=await loadCommands(new URL('./commands/',import.meta.url),{capabilityFromDirectory:true})
const privateRegistry=await loadCommands(new URL('./private-commands/',import.meta.url))
assert.equal(REACTION_NAMES.length,60,'59 live-verified primary GIF categories plus live-verified kill')
assert.equal(new Set(REACTION_NAMES).size,60)
for(const name of REACTION_NAMES){
  const command=publicRegistry.commands.get(name)
  assert.equal(command?.name,name,'missing distinct reaction command '+name)
  assert.equal(command?.capability,'reactions')
  assert.ok(!privateRegistry.commands.has(name),'reaction shadows private command: '+name)
}
assert.equal(REACTIONS.akick,'kick')
assert.equal(publicRegistry.commands.get('kick')?.name,'kick')
assert.equal(publicRegistry.commands.get('kick')?.adminOnly,true,'moderation must remain admin-only')
assert.equal(publicRegistry.commands.get('tagall')?.name,'hidetag')
assert.equal(publicRegistry.commands.get('tagall')?.adminOnly,true)
assert.equal(publicRegistry.commands.get('smug')?.name,'smug')
assert.equal(publicRegistry.commands.get('kill')?.name,'kill','live-verified kill reaction must be registered')
assert.ok(reactionCatalog('.').includes('.smug'))
assert.ok(reactionCatalog('.').includes('.akick'))

const oldFetch=globalThis.fetch
let requests=0
let sent=[]
let replies=[]
const peer='80456789@lid'
const ctx={
  args:['@all'],
  message:{key:{remoteJid:'group@g.us'},message:{extendedTextMessage:{text:'.smug @all'}}},
  groupKey:'group@g.us',senderName:'Night User',
  publicPrefix:'.',
  account:{sock:{async sendMessage(jid,payload,opts){sent.push({jid,payload,opts})}}},
  reply:async text=>{replies.push(text)},
}
try {
  globalThis.fetch=async()=>{requests++;throw new Error('This request must not happen')}
  await publicRegistry.commands.get('smug').run(ctx)
  assert.equal(requests,0)
  assert.equal(sent.length,0)
  assert.match(replies.at(-1),/cannot tag everyone/i)
  replies=[]
  ctx.args=['@Bob','@Alice']
  ctx.message.message.extendedTextMessage.contextInfo={mentionedJid:[peer,'another@lid']}
  await publicRegistry.commands.get('hug').run(ctx)
  assert.equal(requests,0)
  assert.equal(sent.length,0)
  assert.match(replies.at(-1),/one person/i)
  replies=[]

  const directory=await mkdtemp(join(tmpdir(),'night-reaction-test-'))
  let gif
  try {
    const filename=join(directory,'sample.gif')
    await runFfmpeg(['-f','lavfi','-i','testsrc=duration=1:size=160x120:rate=6','-vf','fps=6','-loop','0',filename])
    gif=await readFile(filename)
  } finally {await rm(directory,{recursive:true,force:true})}
  assert.equal(gif.toString('ascii',0,6),'GIF89a')
  globalThis.fetch=async url=>{
    requests++
    if(String(url).includes('?amount=')) return new Response(
      JSON.stringify({results:[
        {url:'https://nekos.best/api/v2/smug/test1.gif'},
        {url:'https://nekos.best/api/v2/smug/test2.gif'},
      ]}),{status:200,headers:{'content-type':'application/json'}})
    if(String(url).includes('/smug/test')) return new Response(gif,{status:200,headers:{'content-type':'image/gif'}})
    throw new Error('Unexpected URL '+url)
  }
  ctx.args=['@Bob']
  ctx.message.message.extendedTextMessage.contextInfo={mentionedJid:[peer]}
  replies=[]
  await publicRegistry.commands.get('smug').run(ctx)
  assert.equal(sent.length,1,'only the sticker should be sent; never a GIF or extra text message')
  assert.deepEqual(sent[0].payload.mentions,[peer],'target must remain a WhatsApp LID')
  assert.equal(sent[0].payload.sticker.toString('ascii',0,4),'RIFF')
  assert.equal(sent[0].payload.sticker.toString('ascii',8,12),'WEBP')
  assert.ok(sent[0].payload.sticker.includes(Buffer.from(DEFAULT_STICKER_PUBLISHER)),'keep existing NIGHT publisher metadata')
  assert.ok(sent[0].payload.sticker.includes(Buffer.from('Night User')),'preserve existing sticker pack naming')
  assert.equal(replies.length,0)
  assert.ok(requests>=2)

  // Separately verify .kill's secondary API path and exact approved CDN hostname.
  globalThis.fetch=async url=>{
    requests++
    if(String(url).includes('animegifs-enkidu.koyeb.app/v3/api/'))
      return new Response(JSON.stringify({
        gif:'https://frqs54dqeajn.objectstorage.eu-frankfurt-1.oci.customer-oci.com/sample.gif',
      }),{status:200,headers:{'content-type':'application/json'}})
    if(String(url).includes('objectstorage.eu-frankfurt-1.oci.customer-oci.com/'))
      return new Response(gif,{status:200,headers:{'content-type':'image/gif'}})
    throw new Error('Unexpected kill URL '+url)
  }
  const priorStickerCount=sent.length
  await publicRegistry.commands.get('kill').run(ctx)
  assert.equal(sent.length,priorStickerCount+1,'kill must send a second real sticker')
  assert.equal(sent.at(-1).payload.sticker.toString('ascii',8,12),'WEBP')

  // The ordinary .reactions menu does not access external services.
  const before=requests
  await publicRegistry.commands.get('reactions').run(ctx)
  assert.equal(requests,before)
  assert.match(replies.at(-1),/NIGHT Anime Stickers/)

  // HTML-as-JSON source failure must produce a friendly response, not a SyntaxError leak.
  replies=[]
  sent=[]
  globalThis.fetch=async()=>new Response('<body>oops</body>',{status:200,headers:{'content-type':'text/html'}})
  await publicRegistry.commands.get('smug').run(ctx)
  assert.equal(sent.length,0)
  assert.match(replies.at(-1),/could not load.*right now/i)

  console.log('PASS 60 collision-free anime reaction commands, rounded sticker metadata, mention safety and failover')
} finally {
  globalThis.fetch=oldFetch
}

await import('./anime-reaction-failover-selftest.js')
