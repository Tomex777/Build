import assert from 'node:assert/strict'
import {
  LIVE_CASES, LIVE_NAMES, authorizeTest, attachFixture, sniffMedia,
  validateMediaPayload, verdictForOutputs, runLiveCase, createLiveAuditManager,
  makeTestFixtures, decodeOutput, materializeLocalOutput,
} from './command-live-audit.js'

const dm={isSupremeOwner:true,message:{key:{remoteJid:'owner@s.whatsapp.net',id:'start'},message:{conversation:'.testall live'}},
  account:{sock:{sendMessage:async(jid,payload)=>{
    assert.equal(jid,'owner@s.whatsapp.net')
    assert.ok(payload.text || payload.image || payload.video || payload.audio || payload.document || payload.sticker)
    return {key:{id:'ack-123'},message:{conversation:'ok'}}
  }}}}
assert.equal(authorizeTest(dm,'live',false),'')
assert.match(authorizeTest({...dm,isSupremeOwner:false},'live',true),/Only/)
assert.match(authorizeTest({...dm,groupKey:'group@g.us',message:{key:{remoteJid:'group@g.us'}}},'live',false),/confirm/)
assert.equal(authorizeTest({...dm,groupKey:'group@g.us',message:{key:{remoteJid:'group@g.us'}}},'live',true),'')
assert.equal(LIVE_NAMES.includes('kick'),false)
assert.equal(LIVE_NAMES.includes('disconnect'),false)
assert.equal(LIVE_NAMES.includes('restart'),false)
assert.equal(LIVE_NAMES.includes('broadcast'),false)
assert.equal(LIVE_NAMES.includes('sticker'),true)
assert.equal(LIVE_NAMES.includes('sfx'),true)
assert.equal(LIVE_NAMES.includes('tictactoe'),true)
assert.equal(sniffMedia(Buffer.from('OggS123456789')),'ogg')
assert.equal(sniffMedia(Buffer.from('%PDF-1.7\nabc')),'pdf')
assert.equal(validateMediaPayload({image:Buffer.alloc(300)}).valid,false)
assert.equal(validateMediaPayload({text:'NIGHT successful'}).valid,true)
assert.equal(verdictForOutputs([{delivered:true,kind:'text',valid:true,text:'Use .sticker'}],'text').status,'FAILED')
assert.equal(verdictForOutputs([{delivered:true,kind:'text',valid:true,text:'night-testall-qr'}],'text','night-testall-qr').status,'PASS')
assert.equal(verdictForOutputs([],'image').status,'FAILED')
assert.equal(verdictForOutputs([{delivered:true,kind:'image',valid:true}],'imageOrText').status,'PASS')
assert.match(verdictForOutputs([{delivered:true,kind:'text',valid:true,text:'Azure Speech is not configured'}],'audio').detail,/Azure Speech/)
assert.equal(verdictForOutputs([{delivered:true,kind:'video',valid:true}],'image').status,'FAILED')

const quoted=attachFixture(dm.message,{key:{id:'fixture-1'},message:{imageMessage:{url:'a',mediaKey:Buffer.alloc(32)}}},
  'owner@s.whatsapp.net','.sticker')
assert.equal(quoted.message.extendedTextMessage.contextInfo.stanzaId,'fixture-1')
assert(quoted.message.extendedTextMessage.contextInfo.quotedMessage.imageMessage)

const safeCommand={name:'ping',async run(ctx){await ctx.reply('NIGHT is live and running.')}}
const direct=await runLiveCase({original:dm,command:safeCommand,spec:LIVE_CASES.ping,
  fixtures:{get:async()=>{throw Error('unexpected media fixture')}},
  fixtureSends:new Map()})
assert.equal(direct.status,'PASS')

const noDelivery={name:'calc',async run(){}}
const empty=await runLiveCase({original:dm,command:noDelivery,spec:LIVE_CASES.calc,
  fixtures:{get:async()=>{}},fixtureSends:new Map()})
assert.equal(empty.status,'FAILED')
assert.match(empty.detail,/No WhatsApp/)

const outside={name:'ping',async run(ctx){await ctx.account.sock.sendMessage('someone-else@s.whatsapp.net',{text:'bad'})}}
const denied=await runLiveCase({original:dm,command:outside,spec:LIVE_CASES.ping,
  fixtures:{get:async()=>{}},fixtureSends:new Map()})
assert.equal(denied.status,'FAILED')
assert.match(denied.detail,/outside the authorized chat/)

// An instrumented live audit must execute only discovered allowlisted names,
// never unknown or administrator commands.
const recorded=[]
const registry={canonical:[safeCommand,noDelivery,{name:'kick'}],
  commands:new Map([['ping',safeCommand],['calc',noDelivery],['kick',{name:'kick'}]])}
const manager=createLiveAuditManager({
  load:async()=>registry,
  makeFixtures:async()=>({get:async()=>Buffer.alloc(0),close:async()=>{}}),
  runCase:async({command})=>{recorded.push(command.name);
    return command.name==='ping'?{status:'PASS',detail:'ok'}:{status:'FAILED',detail:'no reply'}},
})
assert.equal(manager.start(dm),true)
assert.equal(manager.start(dm),false)
for(let attempt=0;attempt<100 && manager.busy();attempt++)
  await new Promise(resolve=>setTimeout(resolve,30))
assert.equal(manager.busy(),false)
assert.deepEqual(recorded,['ping','calc'])
assert.deepEqual(manager.last().failed,['calc'])
assert(manager.details().join(' ').includes('no reply'))
assert.equal(manager.details('ping').some(line=>line.includes('PASS')),true)
assert.equal(manager.start(dm,{failedOnly:true}),true)
for(let attempt=0;attempt<100 && manager.busy();attempt++)
  await new Promise(resolve=>setTimeout(resolve,30))
assert.deepEqual(recorded,['ping','calc','calc'])
assert.equal(manager.start({...dm,account:{sock:{sendMessage:async()=>({key:{id:'x'}})}}},{failedOnly:false}),true)
assert.equal(manager.cancel(),true)
for(let attempt=0;attempt<100 && manager.busy();attempt++)
  await new Promise(resolve=>setTimeout(resolve,30))

// Exercise real deterministic fixtures and the decoder used by live sends.
// Tests catch broken WebP metadata, damaged media containers and missing FFmpeg.
const fx=await makeTestFixtures()
try {
  assert.equal((await decodeOutput({image:await fx.get('image')})).valid,true)
  assert.equal((await decodeOutput({image:await fx.get('qr')})).valid,true)
  assert.equal((await decodeOutput({document:await fx.get('pdf')})).valid,true)
  assert.equal((await decodeOutput({audio:await fx.get('audio')})).valid,true)
  assert.equal((await decodeOutput({video:await fx.get('video')})).valid,true)
  assert.equal((await decodeOutput({sticker:await fx.get('sticker')})).valid,true)
  assert.equal((await decodeOutput({sticker:await fx.get('animated-sticker')})).valid,true)
} finally {await fx.close()}
await assert.rejects(materializeLocalOutput({image:{url:'https://evil.example/secret'}}),/non-temporary/)

console.log('PASS NIGHT owner-only opt-in group/DM real command tests, output verification, fixture quote and fail-only retry')
