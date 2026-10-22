import assert from 'node:assert/strict'
import { DatabaseSync } from 'node:sqlite'
import { randomBytes, createHash } from 'node:crypto'
import { NightPlay } from '../night-play.js'

const db=new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
let time=Date.UTC(2026,9,22,21,0,0),role='ADMIN',sent=[],displayName='NIGHT 👩🏽‍💻 🇳🇬'
const now=()=>time,play=new NightPlay(db,{now}),channels=play.channels,svc=channels.publisher
const channelId='120363282083849178@newsletter', otherId='120363282083849179@newsletter'
function addUser(key) {
  const session=randomBytes(32).toString('base64url')
  const invite=play.request({action:'link',session,client:key})
  play.linkFromWhatsApp({userKey:key,code:invite.code,displayName:'Owner'})
  return play.linkedProfile({userKey:key}).id
}
const alice=addUser('2348000000011'),bob=addUser('2348000000022')
const proof=(user,channel,account='A')=>db.prepare('INSERT INTO play_channel_claims VALUES (?,?,?,?,?,?,?,?,?)').run(user,channel,account,'Original', 'connected','proof',time-1000,time+86400000,time)
proof(alice,channelId)
proof(bob,otherId)
const sock={
  newsletterMetadata:async (_type,id)=>({id,name:displayName,viewer_metadata:{role}}),
  sendMessage:async (jid,msg)=>{ assert.equal(jid,channelId);assert.ok(Buffer.isBuffer(msg.image));sent.push(msg);return {key:{id:'wa-'+sent.length}} },
}
const accounts=new Map([['A',{id:'A',connected:true,sock}]])
assert.deepEqual(svc.config(alice,channelId).modules,{memes:false,news:false,anonymous:false,ai:false})
assert.equal(svc.config(alice,channelId).branding.posterName,'')
assert.throws(()=>svc.config(bob,channelId),e=>e.status===404)
assert.throws(()=>svc.config(alice,otherId),e=>e.status===404)
assert.equal(svc.current(alice,channelId).selection.items.length,0)
await assert.rejects(svc.update(bob,channelId,0,{modules:{memes:true}},accounts),e=>e.status===404)
await assert.rejects(svc.update(alice,channelId,0,{modules:{ai:true}},accounts),e=>e.status===403)
await assert.rejects(svc.update(alice,channelId,0,{memes:{mode:'automatic'}},accounts),e=>e.status===503)
let cfg=await svc.update(alice,channelId,0,{modules:{memes:true},branding:{posterName:'TOMEX',signatureEnabled:true}},accounts)
assert.equal(cfg.revision,1)
assert.equal(cfg.channel.name,displayName)
assert.equal(db.prepare('SELECT name FROM play_channel_claims WHERE user_id=? AND channel_id=?').get(alice,channelId).name,displayName,'ZWJ preserved exactly')
assert.equal(svc.config(alice,channelId).branding.posterName,'TOMEX')
await assert.rejects(svc.update(alice,channelId,0,{modules:{memes:false}},accounts),e=>e.status===409)
role='SUBSCRIBER'
await assert.rejects(svc.update(alice,channelId,1,{modules:{memes:false}},accounts),e=>e.status===403)
role='ADMIN'
const image=Buffer.from([137,80,78,71,13,10,26,10,1,2,3,4,5,6,7,8])
const meme=i=>({image,mime:'image/png',caption:'Caption '+i,title:'Meme '+i,category:'anime',sourceName:'Approved source',sourceUrl:'https://example.org/licensed/'+i,rightsEvidence:i===0?null:{verifiedByServer:true,status:'cleared',licenseId:'license-'+i,sha256:createHash('sha256').update(image).digest('hex'),sourceUrl:'https://example.org/licensed/'+i}})
assert.throws(()=>svc.registerBatch(bob,channelId,[meme(0),meme(1),meme(2),meme(3)]),e=>e.status===404)
const batch=svc.registerBatch(alice,channelId,[meme(0),meme(1),meme(2),meme(3)])
const got=svc.batch(alice,channelId,batch)
assert.equal(got.candidates.length,4)
assert.equal(got.candidates[0].reviewOnly,true)
assert.equal(got.candidates[1].publishable,true)
assert.equal(got.candidates[1].rightsStatus,'verified')
assert.throws(()=>svc.review(alice,channelId,{batchId:batch,candidateId:got.candidates[0].id,decision:'approved',caption:'oops',expectedSelectionRevision:0}),e=>e.status===403)
let update=svc.review(alice,channelId,{batchId:batch,candidateId:got.candidates[1].id,decision:'approved',caption:'My first licensed one',expectedSelectionRevision:0})
assert.equal(update.selection.revision,1)
assert.equal(update.selection.items.length,1)
assert.equal(update.selection.items[0].title,'Meme 1')
assert.throws(()=>svc.review(alice,channelId,{batchId:batch,candidateId:got.candidates[2].id,decision:'approved',caption:'oops',expectedSelectionRevision:0}),e=>e.status===409)
update=svc.review(alice,channelId,{batchId:batch,candidateId:got.candidates[2].id,decision:'approved',caption:'My second licensed one',expectedSelectionRevision:1})
assert.equal(update.selection.items.length,2)
assert.throws(()=>svc.enqueue(bob,channelId,{selectionId:update.selection.id,expectedSelectionRevision:2,mode:'now'}),e=>e.status===404)
assert.throws(()=>svc.enqueue(alice,channelId,{selectionId:update.selection.id,expectedSelectionRevision:1,mode:'now'}),e=>e.status===409)
const enqueued=svc.enqueue(alice,channelId,{selectionId:update.selection.id,expectedSelectionRevision:2,mode:'now'})
assert.equal(enqueued.jobs.length,2)
assert.equal(enqueued.selection.items.length,0)
assert.equal(svc.jobs(alice,channelId).jobs.length,2)
assert.equal(svc.jobs(bob,otherId).jobs.length,0)
assert.throws(()=>svc.enqueue(alice,channelId,{selectionId:update.selection.id,expectedSelectionRevision:2,mode:'now'}),e=>e.status===409)
const first=svc.jobs(alice,channelId).jobs.find(j=>j.status==='queued')
assert.equal(typeof first.scheduledAt,'string')
assert.equal(svc.cancel(alice,channelId,first.id).cancelled,true)
for(const other of enqueued.jobs.filter(j=>j.id!==first.id))assert.equal(svc.cancel(alice,channelId,other.id).cancelled,true)
assert.throws(()=>svc.cancel(bob,channelId,first.id),e=>e.status===404)
// Persisted config and selections survive a new NightPlay process.
const reloaded=new NightPlay(db,{now}).channels.publisher
assert.equal(reloaded.config(alice,channelId).branding.posterName,'TOMEX')
assert.equal(reloaded.selection(alice,channelId).revision,3)
const nextBatch=reloaded.registerBatch(alice,channelId,[meme(1),meme(2),meme(3),meme(4)])
const next=reloaded.batch(alice,channelId,nextBatch)
const reviewed=reloaded.review(alice,channelId,{batchId:nextBatch,candidateId:next.candidates[2].id,decision:'approved',caption:'DO NOT SEND IF DISABLED',expectedSelectionRevision:3})
const scheduled=reloaded.enqueue(alice,channelId,{selectionId:reviewed.selection.id,expectedSelectionRevision:4,mode:'schedule',scheduledAt:new Date(time+5*60000).toISOString(),spacingMinutes:5})
assert.equal(scheduled.jobs.length,1)
await reloaded.update(alice,channelId,1,{modules:{memes:false}},accounts)
assert.equal(reloaded.jobs(alice,channelId).jobs.find(j=>j.id===scheduled.jobs[0].id).status,'cancelled')
let prepared=0
const fakePrepare=async arg=>{prepared++;assert.equal(arg.verifiedDisplayName,displayName);assert.equal(arg.branding.posterName,'TOMEX');return {mediaBuffer:arg.mediaBuffer,caption:arg.text+'\n> TOMEX'}}
assert.equal((await reloaded.drain(accounts,{prepare:fakePrepare,allowedChannelIds:[channelId]})).sent,0)
assert.equal(sent.length,0,'Turned-off module must prevent publishing')
// Exact account + admin gates at final send must remain enforced.
await reloaded.update(alice,channelId,2,{modules:{memes:true}},accounts)
const pendingBatch=reloaded.registerBatch(alice,channelId,[meme(1),meme(2),meme(3),meme(4)])
const pending=reloaded.batch(alice,channelId,pendingBatch)
const accept=reloaded.review(alice,channelId,{batchId:pendingBatch,candidateId:pending.candidates[1].id,decision:'approved',caption:'Allowed post',expectedSelectionRevision:5})
const job=reloaded.enqueue(alice,channelId,{selectionId:accept.selection.id,expectedSelectionRevision:6,mode:'now'}).jobs[0]
role='SUBSCRIBER'
assert.equal((await reloaded.drain(accounts,{prepare:fakePrepare,allowedChannelIds:[channelId]})).sent,0)
assert.equal(reloaded.jobs(alice,channelId).jobs.find(j=>j.id===job.id).status,'needs_review')
assert.equal(sent.length,0)
role='ADMIN'
const liveBatch=reloaded.registerBatch(alice,channelId,[meme(1),meme(2),meme(3),meme(4)])
const liveCandidates=reloaded.batch(alice,channelId,liveBatch)
const liveReview=reloaded.review(alice,channelId,{batchId:liveBatch,candidateId:liveCandidates.candidates[1].id,decision:'approved',caption:'One publishable example',expectedSelectionRevision:7})
assert.equal(liveReview.selection.items.length,1)
const liveJob=reloaded.enqueue(alice,channelId,{selectionId:liveReview.selection.id,expectedSelectionRevision:8,mode:'now'}).jobs[0]
const delivered=await reloaded.drain(accounts,{prepare:fakePrepare,allowedChannelIds:[channelId]})
assert.equal(delivered.sent,1)
assert.equal(sent.length,1)
assert.equal(sent[0].caption,'One publishable example\n> TOMEX')
assert.equal(reloaded.jobs(alice,channelId).jobs.find(j=>j.id===liveJob.id).status,'sent')
assert.equal((await reloaded.drain(accounts,{prepare:fakePrepare,allowedChannelIds:[channelId]})).sent,0)
assert.equal(sent.length,1,'A completed job must never send twice.')
console.log('NIGHT Channels publisher selftest passed: owner isolation, exact Unicode metadata, fail-closed module controls, rights, revisioned cross-batch tray, durability, scheduling, cancellation, admin revocation, no send without permission.')
