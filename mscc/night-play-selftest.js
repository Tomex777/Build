import './night-play-avatar-selftest.js'
import './night-play-matches-selftest.js'
import './night-play-channels-selftest.js'
import assert from 'node:assert/strict'
import { DatabaseSync } from 'node:sqlite'
import { randomBytes, generateKeyPairSync, sign } from 'node:crypto'
import { createServer } from 'node:http'
import { NightPlay, createNightPlayHandler, signatureMessage, PLAY_RPC_PATH } from './night-play.js'
import command from './commands/games/minigames.js'
import { patchNginx } from './tools/night-play-nginx.mjs'

const db = new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
let time=Date.now()
const play=new NightPlay(db,{now:()=>time})
const token=()=>randomBytes(32).toString('base64url')
const identity={userKey:'2348000000001',identityKeys:['2348000000001','jid:90000000000001@lid']}
const a=token(),b=token(),c=token()
assert.equal(play.request({action:'status'}).linked,false)
const link=play.request({action:'link',session:a,client:'a'})
assert.match(link.command,/^\.minigames link [A-HJ-NP-Z2-9]{4}(?:-[A-HJ-NP-Z2-9]{4}){2}$/)
assert.equal(link.expiresAt,time+300_000)
assert.equal(db.prepare('SELECT code_hash FROM play_challenges').get().code_hash.includes(link.code),false)
assert.throws(()=>play.request({action:'profile',session:a,displayName:'Imposter'}),error=>error.status===401)
let replies=[]
await command.run({args:['link',link.code],groupKey:'group@g.us',reply:async value=>replies.push(value),nightPlayLink:()=>{throw new Error('Group must not redeem')}})
assert.match(replies.pop(),/privately/)
await command.run({args:['link',link.code],message:{key:{remoteJid:'2348000000001@s.whatsapp.net'}},reply:async value=>replies.push(value),nightPlayLink:code=>play.linkFromWhatsApp({...identity,code,displayName:'<Pat>\u0000'})})
assert.match(replies.pop(),/Connected to NIGHT Play/)
const profile=play.request({action:'status',session:a}).profile
assert.equal(profile.displayName,'Pat')
assert.match(profile.id,/^NIGHT-/)
assert.equal(JSON.stringify(profile).includes(identity.userKey),false)
assert.throws(()=>play.linkFromWhatsApp({...identity,code:link.code}),/expired|used/)
assert.throws(()=>play.request({action:'link',session:a,client:'a'}),/already connected/)
const second=play.request({action:'link',session:b,client:'b'})
play.linkFromWhatsApp({userKey:'jid:90000000000001@lid',code:second.code,displayName:'Changed'})
assert.deepEqual(play.request({action:'status',session:b}).profile,profile,'Verified LID resolves same identity across bot accounts')
assert.equal(play.request({action:'profile',session:b,displayName:'Alex'}).profile.displayName,'Alex')
assert.equal(play.request({action:'status',session:a}).profile.displayName,'Alex')
play.request({action:'logout',session:a})
assert.equal(play.request({action:'status',session:a}).linked,false)
assert.equal(play.request({action:'status',session:b}).linked,true)
const third=play.request({action:'link',session:c,client:'c'})
play.request({action:'cancel',session:c})
assert.throws(()=>play.linkFromWhatsApp({...identity,code:third.code}),/expired/)
const expired=play.request({action:'link',session:c,client:'c'})
time+=300_001
assert.throws(()=>play.linkFromWhatsApp({...identity,code:expired.code}),/expired/)
const old=play.request({action:'link',session:c,client:'c'})
const fresh=play.request({action:'link',session:c,client:'c'})
assert.throws(()=>play.linkFromWhatsApp({...identity,code:old.code}),/expired/)
play.linkFromWhatsApp({...identity,code:fresh.code})
play.disconnectWhatsApp(identity)
assert.equal(play.request({action:'status',session:b}).linked,false)
assert.equal(play.request({action:'status',session:c}).linked,false)
const d=token(),restart=play.request({action:'link',session:d,client:'d'})
const reopened=new NightPlay(db,{now:()=>time})
reopened.linkFromWhatsApp({...identity,code:restart.code})
assert.equal(reopened.request({action:'status',session:d}).linked,true,'Pending links survive service reinitialization')
const tinyPng=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i4VsAAAAASUVORK5CYII=','base64')
const renewedProfile=reopened.request({action:'status',session:d}).profile
assert.deepEqual(reopened.avatarCandidates(renewedProfile.id).sort(),['2348000000001@s.whatsapp.net','90000000000001@lid'].sort())
assert.equal(reopened.saveAvatar(renewedProfile.id,Buffer.from('not-an-image')),false)
assert.equal(reopened.saveAvatar(renewedProfile.id,Buffer.alloc(196609)),false)
assert.equal(reopened.saveAvatar(renewedProfile.id,tinyPng),true)
assert.equal(reopened.request({action:'status',session:d}).profile.avatarData,'data:image/png;base64,'+tinyPng.toString('base64'))
assert.ok(!JSON.stringify(reopened.request({action:'status',session:d})).includes(identity.userKey))
reopened.request({action:'delete',session:d})
assert.equal(db.prepare('SELECT count(*) n FROM play_avatars').get().n,0)
assert.equal(db.prepare('SELECT count(*) n FROM play_identities').get().n,0)
assert.equal(reopened.request({action:'status',session:d}).linked,false)
assert.throws(()=>play.linkFromWhatsApp({userKey:'not-verified',code:fresh.code}),/not verified/)
const flood=token()
for(let n=0;n<5;n++)play.request({action:'link',session:flood,client:'flood'})
assert.throws(()=>play.request({action:'link',session:flood,client:'flood'}),error=>error.status===429)

const {publicKey,privateKey}=generateKeyPairSync('ed25519')
const handler=createNightPlayHandler(play,{publicKey:publicKey.export({type:'spki',format:'pem'})})
const server=createServer(handler)
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const url='http://127.0.0.1:'+server.address().port+PLAY_RPC_PATH
const raw=JSON.stringify({action:'health'})
const timestamp=String(time),nonce=randomBytes(24).toString('base64url')
const headers={'x-night-timestamp':timestamp,'x-night-nonce':nonce,'x-night-signature':sign(null,Buffer.from(signatureMessage(timestamp,nonce,raw)),privateKey).toString('base64url')}
try {
  assert.equal((await fetch(url,{method:'POST',body:raw})).status,401)
  const signed=await fetch(url,{method:'POST',body:raw,headers})
  assert.equal(signed.status,200)
  assert.equal((await signed.json()).service,'night-play')
  assert.equal((await fetch(url,{method:'POST',body:raw,headers})).status,409,'Replay rejected')
  assert.equal((await fetch(url,{method:'POST',body:raw+' ',headers})).status,401,'Body tampering rejected')
  assert.equal((await fetch(url,{method:'POST',body:raw,headers:{...headers,'x-night-timestamp':String(time-60_001)}})).status,401)
} finally { server.closeAllConnections();await new Promise(resolve=>server.close(resolve));db.close() }
const nginx='server {\n listen 80;\n server_name _;\n}\nserver {\n listen 443 ssl;\n server_name 102.37.119.250;\n location / { proxy_pass http://127.0.0.1:47831; }\n}'
const patched=patchNginx(nginx)
assert.equal(patchNginx(patched),patched,'Ingress patch is idempotent')
assert.ok(patched.includes('proxy_pass http://127.0.0.1:47831;'),'Existing Cortex backend preserved')
assert.equal((patched.match(/location = \/api\/night-play\/rpc/g)||[]).length,1)
assert.match(patched, /proxy_intercept_errors off;/, 'signed RPC JSON errors must not be rewritten by nginx')
assert.match(patched, /add_header X-Night-Play-Route signed-v1 always;/, 'the precise nginx location must be verifiable')
assert.equal((patched.match(/X-Night-Play-Route/g)||[]).length,1, 'Cortex may not impersonate signed route')
assert.match(patched, /location = \/api\/night-play\/rpc \{[^}]*auth_request off;[^}]*auth_basic off;[^}]*proxy_pass http:\/\/127\.0\.0\.1:8787;/, 'Only signature-verified NIGHT Play RPC must bypass inherited Cortex auth')
assert.equal(patchNginx(patched),patched,'Repeated ingress install preserves explicit signed RPC auth exceptions')
assert.ok(!patched.split('location / {')[1].includes('auth_request off;'), 'Cortex location must retain its existing authentication policy')
assert.throws(()=>patchNginx('server { listen 80; }'),/existing Cortex/)
console.log('PASS NIGHT Play: private command, verified PN/LID identity, single-use expiring codes, durable sessions, multi-browser revocation/removal, request signatures, replay protection, throttling and HTTPS ingress')
