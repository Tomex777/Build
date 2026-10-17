import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { DatabaseSync } from 'node:sqlite'
import { NightPlay } from './night-play.js'

const db=new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
let time=Date.now(),role='ADMIN',sent=[]
const play=new NightPlay(db,{now:()=>time})
const identity={userKey:'2348000098765'}
const session=randomBytes(32).toString('base64url')
const link=play.request({action:'link',session,client:'nights'})
play.linkFromWhatsApp({...identity,code:link.code})
const owner=play.linkedProfile(identity).id
const channelId='120363282083849178@newsletter'
db.prepare('INSERT INTO play_channel_claims VALUES (?,?,?,?,?,?,?,?,?)')
  .run(owner,channelId,'A','Night Confessions','connected','proof',time,time+86400000,time)
const sock={newsletterMetadata:async()=>({viewer_metadata:{role}}),
  sendMessage:async(jid,content)=>{
    assert.equal(jid,channelId)
    assert.equal(content.mimetype,'image/png')
    assert.ok(Buffer.isBuffer(content.image)&&content.image.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))
    sent.push(content);return {key:{id:'TEST'}}
  }}
const accounts=new Map([['A',{id:'A',connected:true,sock}]])
const nights=play.nights
assert.throws(()=>nights.overview('someone-else',channelId),e=>e.status===403)
assert.throws(()=>nights.open(owner,channelId,1),e=>e.status===400)
const open=nights.open(owner,channelId,120)
assert.match(open.token,/^[A-Za-z0-9_-]{43}$/)
assert.equal(nights.details(open.token).name,'Night Confessions')
assert.deepEqual(nights.overview(owner,channelId).counts,{pending:0,published:0,rejected:0})
assert.throws(()=>nights.details('unrecognized'),e=>e.status===404)
assert.throws(()=>nights.submit(open.token,'viewer-1','https://spam.com','cream'),e=>e.status===400)
assert.deepEqual(nights.submit(open.token,'viewer-1','I miss the friends I had before moving to a new city.','sage'),{received:true,queued:true})
assert.deepEqual(nights.submit(open.token,'viewer-2','The first time I told somebody I needed help was today.','cream'),{received:true,queued:true})
const posts=nights.overview(owner,channelId).posts
assert.equal(posts.length,2)
assert.equal(posts[0].status,'pending')
role='SUBSCRIBER'
await assert.rejects(nights.publish(owner,channelId,posts[0].id,accounts),e=>e.status===403)
assert.equal(sent.length,0)
role='ADMIN'
const result=await nights.publish(owner,channelId,posts[0].id,accounts)
assert.equal(result.published,true)
assert.equal(sent.length,1)
await assert.rejects(nights.publish(owner,channelId,posts[0].id,accounts),e=>e.status===409)
assert.equal(nights.reject(owner,channelId,posts[1].id).ok,true)
assert.equal(nights.overview(owner,channelId).counts.published,1)
assert.equal(nights.overview(owner,channelId).counts.rejected,1)
assert.equal(nights.end(owner,channelId).ok,true)
assert.throws(()=>nights.details(open.token),e=>e.status===410)
assert.throws(()=>nights.submit(open.token,'viewer-3','One thing I never told anybody.','rose'),e=>e.status===410)
const fresh=nights.open(owner,channelId,15)
assert.equal(nights.details(fresh.token).name,'Night Confessions')
time+=900001
assert.throws(()=>nights.details(fresh.token),e=>e.status===410)
nights.removeUser(owner)
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_nights').get().n,0)
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_night_posts').get().n,0)
db.close()
console.log('PASS NIGHT anonymous sessions: per-owner access, ephemeral invites, safe submissions, manual review, actual PNG send, admin recheck, replay protection and expiring links')
