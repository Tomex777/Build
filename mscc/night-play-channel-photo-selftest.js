import assert from 'node:assert/strict'
import {DatabaseSync} from 'node:sqlite'
import {randomBytes} from 'node:crypto'
import {NightPlay} from './night-play.js'
import {createNightPlayChannelPhotoSync} from './night-play-channel-photo.js'
const jid='120363282083849178@newsletter'
const now=()=>1000000+offset
let offset=0,fetchCount=0,lookups=0
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i4VsAAAAASUVORK5CYII=','base64')
const db=new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
const play=new NightPlay(db,{now})
const session=randomBytes(32).toString('base64url')
const challenge=play.request({action:'link',session,client:'channel-photo-test'})
play.linkFromWhatsApp({userKey:'2348000000001',code:challenge.code})
const userId=play.request({action:'status',session}).profile.id
db.prepare('INSERT INTO play_channel_claims VALUES (?,?,?,?,?,?,?,?,?)').run(userId,jid,'bot1','My verified channel','connected','',now(),now()+86400000,now())
const fetcher=async(url,options)=>{
 fetchCount++
 assert.equal(url,'https://pps.whatsapp.net/example')
 assert.equal(options.redirect,'manual','prevent unvalidated redirects/SSRF')
 return {ok:true,status:200,headers:new Headers({'content-type':'image/png','content-length':String(png.length)}),
  body:new ReadableStream({start(controller){controller.enqueue(png);controller.close()}})}
}
const sock={profilePictureUrl:async(id,size)=>{lookups++;assert.equal(id,jid);assert.equal(size,'preview');return 'https://pps.whatsapp.net/example'}}
const accounts=new Map([['bot1',{connected:true,sock}]])
const sync=createNightPlayChannelPhotoSync(play,accounts,{now,fetcher})
assert.equal(play.request({action:'channel:list',session}).channels[0].photoData,undefined)
await sync(userId,play.request({action:'channel:list',session}).channels)
const listed=play.request({action:'channel:list',session}).channels[0]
assert.equal(listed.photoData,'data:image/png;base64,'+png.toString('base64'),'serve cached image data, never transient media URL')
assert.equal(listed.name,'My verified channel')
await sync(userId,[listed])
assert.equal(fetchCount,1,'no duplicate request during cooldown')
offset+=12*3600000+1
await sync(userId,[listed])
assert.equal(lookups,2,'refresh after cooldown')
const foreign=play.request({action:'link',session:randomBytes(32).toString('base64url'),client:'other'})
assert.equal(foreign.code.length>0,true)
assert.equal(play.channels.savePhoto('unknown',jid,png),false,'never write unverified owner image')
assert.equal(play.channels.canUsePhoto('unknown',jid),false)
const bad=createNightPlayChannelPhotoSync(play,new Map([['bot1',{connected:true,sock:{profilePictureUrl:async()=> 'https://notwhatsapp.example.org/internal'}}]]),
 {now,fetcher:async()=>{throw Error('Unsafe URL was fetched')}})
await bad(userId,[listed])
assert.equal(fetchCount,2,'refuse external media URLs')
play.request({action:'channel:remove',session,channelId:jid})
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_channel_photos').get().n,0,'unlinked channels delete cached photos')
await sync(userId,[listed])
assert.equal(fetchCount,2,'cannot fetch unowned channel photos after removal')
db.close()
console.log('PASS NIGHT Play channel photos: owner-scoped, secure Baileys fetch, cached data, refresh cooldown, remove cascade and SSRF block')
