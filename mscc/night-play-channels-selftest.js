import assert from 'node:assert/strict'
import { DatabaseSync } from 'node:sqlite'
import { randomBytes } from 'node:crypto'
import { NightPlay } from './night-play.js'
import join from './commands/general/joinchannel.js'

const db=new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
let time=Date.now(),role='SUBSCRIBER',followed=0,posts=[]
const play=new NightPlay(db,{now:()=>time}),channels=play.channels
const session=randomBytes(32).toString('base64url'),identity={userKey:'2348000000011',identityKeys:['2348000000011']}
const invite=play.request({action:'link',session,client:'test'})
play.linkFromWhatsApp({...identity,code:invite.code,displayName:'Channel owner'})
const userId=play.linkedProfile(identity).id
const fakeSocket={
  newsletterMetadata:async (type,key)=>{
    assert.ok(type==='jid'||type==='invite')
    assert.ok(key==='0029VExampleTestingCode'||key==='120363282083849178@newsletter')
    return {id:'120363282083849178@newsletter',name:'After Hours',viewer_metadata:{role}}
  },
  newsletterFollow:async id=>{assert.equal(id,'120363282083849178@newsletter');followed++},
  newsletterFetchMessages:async()=>posts,
}
const account={id:'A',connected:true,sock:fakeSocket}
const accounts=new Map([['A',account]])
let replies=[]
const ctx=args=>({args,message:{key:{remoteJid:'2348000000011@s.whatsapp.net'}},
  reply:async s=>{replies.push(s)},
  channelBegin:link=>channels.begin(identity,account,link),channelAccept:()=>channels.accept(identity,account),
  channelCancel:()=>channels.cancel(identity),channelVerify:()=>channels.verify(identity,accounts),
  channelList:()=>channels.list(userId)})
assert.equal(play.request({action:'channel:list',session}).channels.length,0)
assert.equal(play.request({action:'status'}).linked,false)
await join.run(ctx(['https://whatsapp.com/channel/0029VExampleTestingCode']))
assert.equal(followed,0,'Must not follow prior to consent')
assert.match(replies.pop(),/yes/)
await join.run(ctx(['no']))
assert.equal(followed,0,'Refusal must never follow')
await join.run(ctx(['yes']))
assert.match(replies.pop(),/No pending request/)
await join.run(ctx(['https://whatsapp.com/channel/0029VExampleTestingCode']))
await join.run(ctx(['yes']))
assert.equal(followed,1,'Explicit consent is necessary')
assert.match(replies.pop(),/NIGHT-VERIFY-/)
assert.equal(play.request({action:'channel:list',session}).channels.length,1)
assert.equal(play.request({action:'channel:list',session}).channels[0].status,'awaiting_admin')
let result=await channels.verify(identity,accounts)
assert.equal(result.reason,'admin')
role='ADMIN'
result=await channels.verify(identity,accounts)
assert.equal(result.reason,'proof')
const proof=result.proof
posts=[{messageTimestamp:Math.floor((time-3600000)/1000),message:{conversation:proof}}]
assert.equal((await channels.verify(identity,accounts)).reason,'proof','Old posts must not verify')
posts=[{messageTimestamp:Math.floor(time/1000),message:{conversation:proof}}]
result=await channels.verify(identity,accounts)
assert.equal(result.connected,true)
assert.equal(play.request({action:'channel:list',session}).channels[0].status,'connected')
assert.equal(play.request({action:'channel:list',session}).channels[0].proof,undefined,'No proof leaked after claiming')
const another=randomBytes(32).toString('base64url'),c2=play.request({action:'link',session:another,client:'second'})
const identity2={userKey:'2348000000022'}
play.linkFromWhatsApp({...identity2,code:c2.code})
await assert.rejects(channels.begin(identity2,account,'https://whatsapp.com/channel/0029VExampleTestingCode'),e=>e.status===409)
assert.equal(play.request({action:'channel:list',session:another}).channels.length,0,'Never reveal another owner')
assert.throws(()=>play.request({action:'channel:remove',session:another,channelId:'120363282083849178@newsletter'}),e=>e.status===404)
assert.equal(play.request({action:'channel:remove',session,channelId:'120363282083849178@newsletter'}).ok,true)
assert.equal(play.request({action:'channel:list',session}).channels.length,0)
await join.run({...ctx(['yes']),groupKey:'group@g.us'})
assert.match(replies.pop(),/privately/)
play.request({action:'delete',session})
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_channel_claims').get().n,0)
db.close()
console.log('PASS NIGHT Play Channels: explicit consent/refusal, link discovery, verifiable bot admin role + fresh channel post, scoped owner access and deletion')
