import assert from 'node:assert/strict'
import { createNightPlayAvatarSync } from './night-play-avatar.js'
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i4VsAAAAASUVORK5CYII=','base64')
let requests=0
let clock=1000000
let saved=null
const play={
  avatarCandidates:id=>id==='player-1'?['2348000000001@s.whatsapp.net']:[],
  saveAvatar:(id,data)=>{saved={id,data};return true},
}
const accounts=new Map([['A',{sock:{profilePictureUrl:async jid=>{
  requests++
  assert.equal(jid,'2348000000001@s.whatsapp.net')
  return 'https://pps.whatsapp.net/profile/photo'
}}}]])
const fetcher=async url=>({
  ok:true,url,
  headers:new Headers({'content-type':'image/png','content-length':String(png.length)}),
  body:new ReadableStream({start(controller){controller.enqueue(png);controller.close()}})
})
const sync=createNightPlayAvatarSync(play,accounts,{fetcher,now:()=>clock})
assert.equal(await sync('player-1'),true)
assert.equal(saved.id,'player-1')
assert.deepEqual(saved.data,png)
assert.equal(await sync('player-1'),false,'Repeated checks should not redownload')
assert.equal(requests,1)
clock+=900001
assert.equal(await sync('player-1'),true,'Refresh allowed after cooldown')
const unavailable=createNightPlayAvatarSync(play,new Map([['A',{sock:{
  profilePictureUrl:async()=> 'https://example.org/not-a-whatsapp-photo'
}}]]),{fetcher:async()=>{throw Error('Unexpected download')},now:()=>clock})
assert.equal(await unavailable('player-1'),false,'Only approved media hosts are accessed')
assert.equal(await unavailable('other-player'),false)
console.log('PASS NIGHT Play verified avatar sync and bounded refresh')
