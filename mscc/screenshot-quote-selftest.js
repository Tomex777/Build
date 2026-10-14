import assert from 'node:assert/strict'
import { loadImage, createCanvas } from '@napi-rs/canvas'
import { loadCommands } from './command-registry.js'
import getpp from './commands/general/getpp.js'
import sq from './commands/fun/sq.js'
import { extractQuotedCapture, wrapScreenshotText, renderScreenshotQuote } from './utils/screenshot-quote.js'

function quotedMessage(text='Night should be beautiful.') {
  return {
    key:{remoteJid:'12345@g.us',id:'command123',participant:'333@s.whatsapp.net'},
    message:{
      extendedTextMessage:{
        text:'.sq',
        contextInfo:{
          stanzaId:'oldermessage123',
          participant:'111@s.whatsapp.net',
          quotedMessage:{conversation:text},
        },
      },
    },
  }
}
const capture=extractQuotedCapture(quotedMessage('Hello!\nHere is another line.'))
assert.equal(capture.text,'Hello!\nHere is another line.')
assert.equal(capture.participant,'111@s.whatsapp.net')
assert.equal(extractQuotedCapture({message:{conversation:'.sq'}}),null)
const viewOnce=quotedMessage()
viewOnce.message.extendedTextMessage.contextInfo.quotedMessage={
  viewOnceMessageV2:{message:{imageMessage:{mimetype:'image/jpeg'}}}
}
assert.match(extractQuotedCapture(viewOnce).error,/View-once/)
const fakeDeletion=quotedMessage()
fakeDeletion.message.extendedTextMessage.contextInfo.quotedMessage={
  protocolMessage:{type:0}
}
assert.match(extractQuotedCapture(fakeDeletion).error,/Deleted/)
const crop=quotedMessage('a'.repeat(4000))
assert.equal(extractQuotedCapture(crop).text.length,1800)
assert.equal(extractQuotedCapture(crop).truncated,true)

const canvas=createCanvas(800,100)
const measuring=canvas.getContext('2d')
measuring.font='31px sans-serif'
const wrapped=wrapScreenshotText(measuring,'x'.repeat(150)+'\nHello',650)
assert(wrapped.lines.length>2)
assert(wrapped.lines.every(s=>measuring.measureText(s).width<680))
const first=await renderScreenshotQuote({
  text:'We built this together — Night has a personality.\nAnd a beautiful quote card.',
  authorName:'Nami',theme:'dark',
})
const image=await loadImage(first)
assert.equal(image.width,900)
assert(image.height>=350 && image.height<=1750)
const light=await renderScreenshotQuote({text:'A lighter variation.',theme:'light',authorName:'MiMi'})
assert.equal((await loadImage(light)).width,900)
const extended=await renderScreenshotQuote({text:'That is a lot. '.repeat(110),
  theme:'dark',authorName:'Josia',truncated:true})
assert((await loadImage(extended)).height<=1750)

const sent=[]
const replies=[]
const ctx={
  args:[],
  publicPrefix:'.',
  userKey:'3333333333',
  message:quotedMessage(),
  account:{sock:{sendMessage:async(chat,payload,opts)=>{
    sent.push({chat,payload,opts})
    return payload
  }}},
  reply:async text=>{replies.push(String(text));return text},
  resolveCommandTarget:async()=>({phoneNumber:'11111111111',source:'reply'}),
  getPublicUserProfile:async()=>({
    displayName:'Actual group author',photoUrl:'',phoneNumber:'11111111111',
  }),
}
await sq.run(ctx)
assert.equal(sent.length,1)
assert.equal(sent[0].chat,'12345@g.us')
assert.equal(sent[0].opts.quoted,ctx.message)
assert.equal((await loadImage(sent[0].payload.image)).width,900)
ctx.args=['light']
await sq.run(ctx)
assert.equal(sent.length,2)
ctx.message={key:{remoteJid:'12345@g.us'},message:{conversation:'.sq'}}
await sq.run(ctx)
assert.match(replies.at(-1),/Reply to a message/)

ctx.args=[]
ctx.message=quotedMessage()
ctx.resolveCommandTarget=async()=>({phoneNumber:'11111111111',source:'reply'})
ctx.getPublicUserProfile=async()=>({displayName:'Invisible',photoUrl:''})
await getpp.run(ctx)
assert.match(replies.at(-1),/private, unavailable/)
assert.equal(sent.length,2)
ctx.resolveCommandTarget=async()=>({phoneNumber:'',source:''})
await getpp.run(ctx)
assert.match(replies.at(-1),/identify the quoted user/)
ctx.message={key:{remoteJid:'12345@g.us'},message:{conversation:'.getpp'}}
ctx.args=['2348123456789']
await getpp.run(ctx)
assert.match(replies.at(-1),/Phone-number lookups are disabled/)
ctx.args=[]
ctx.getPublicUserProfile=async()=>({displayName:'Self',photoUrl:''})
await getpp.run(ctx)
assert.match(replies.at(-1),/private, unavailable/)

const registry=await loadCommands(new URL('./commands/',import.meta.url),{capabilityFromDirectory:true})
assert.equal(registry.commands.get('sq')?.capability,'fun')
assert.equal(registry.commands.get('screenshotquote')?.name,'sq')
assert.equal(registry.commands.get('getpp')?.capability,'general')
assert.equal(registry.commands.get('getpic')?.name,'getpp')
console.log('PASS Night SQ/GETPP quoted-source accuracy, privacy checks, rendering themes and command registration.')
