import assert from 'node:assert/strict'
import { DatabaseSync } from 'node:sqlite'
import { randomBytes, randomUUID, createHash } from 'node:crypto'
import { NightPlay } from './night-play.js'

const db=new DatabaseSync(':memory:')
db.exec('PRAGMA foreign_keys=ON')
let clock=1760900000000
const play=new NightPlay(db,{now:()=>clock})
const token=()=>randomBytes(32).toString('base64url')
const hostSession=token(),aliceSession=token()
function addUser(name,session) {
  const id='NIGHT-'+randomUUID()
  db.prepare('INSERT INTO play_users VALUES (?,?,?)').run(id,name,clock)
  db.prepare('INSERT INTO play_sessions VALUES (?,?,?)').run(createHash('sha256').update(session).digest('hex'),id,clock+86400000)
  return id
}
const host=addUser('Host verified',hostSession),alice=addUser('WhatsApp Alice',aliceSession)
const guest1=token(),guest2=token()
const questions=[
 {prompt:'Which place?',options:['Mountain','Beach'],correct:1},
 {prompt:'Which color?',options:['Green','Red'],correct:0},
 {prompt:'Who am I?',options:['A person','A robot'],correct:0},
]
const call=(session,action,rest={})=>play.request({action,session,client:'qa-'+(session||'guest'),...rest})
assert.throws(()=>call('', 'party:create',{mode:'quiz',questions}),e=>e.status===401)
const created=call(hostSession,'party:create',{mode:'quiz',questions})
assert.match(created.code,/^[A-HJ-NP-Z2-9]{8}$/)
assert.equal(created.host,true)
assert.equal(created.players.length,0,'host is not required to add their name to a player roster')
assert.equal(created.phase,'lobby')
const code=created.code
const guest=(session,action,options={})=>play.request({action,session:'',guestSession:session,client:'guest',code,...options})
assert.throws(()=>guest(guest1,'party:state'),e=>e.status===403)
assert.throws(()=>guest(guest1,'party:join',{guestName:''}),e=>e.status===400)
assert.equal(guest(guest1,'party:join',{guestName:'Guest One'}).players.length,1)
assert.equal(guest(guest1,'party:join',{guestName:'Another name'}).players.length,1,'repeat join is idempotent')
assert.throws(()=>guest(guest2,'party:join',{guestName:'guest one'}),e=>e.status===409,'duplicate guest names forbidden')
assert.equal(guest(guest2,'party:join',{guestName:'Guest Two'}).players.length,2)
assert.equal(call(aliceSession,'party:join',{code,guestName:'Impostor'}).players[2].name,'WhatsApp Alice','verified name supersedes submitted guestName')
assert.throws(()=>guest(guest1,'party:start'),e=>e.status===403)
let state=call(hostSession,'party:start',{code})
assert.equal(state.phase,'vote')
assert.equal(state.question.correct,null,'quiz answer key is private while voting')
assert.equal(guest(guest1,'party:state').question.correct,null)
assert.throws(()=>guest(token(),'party:state'),e=>e.status===403)
assert.throws(()=>guest(token(),'party:join',{guestName:'Late'}),e=>e.status===409)
guest(guest1,'party:vote',{answer:1})
assert.throws(()=>guest(guest1,'party:vote',{answer:0}),e=>e.status===409,'exactly one vote per round')
guest(guest2,'party:vote',{answer:0})
assert.equal(call(aliceSession,'party:vote',{code,answer:1}).phase,'reveal','auto-reveal after all members vote')
state=guest(guest2,'party:state')
assert.deepEqual(state.results,[1,2])
assert.equal(state.question.correct,1)
assert.throws(()=>guest(guest2,'party:advance'),e=>e.status===403)
assert.equal(call(hostSession,'party:advance',{code}).phase,'vote')
for(let round=1;round<=2;round++) {
  guest(guest1,'party:vote',{answer:0})
  guest(guest2,'party:vote',{answer:0})
  call(aliceSession,'party:vote',{code,answer:0})
  assert.equal(call(hostSession,'party:advance',{code}).phase,round===2?'finished':'vote')
}
state=guest(guest1,'party:state')
assert.equal(state.scores.find(s=>s.name==='Guest One').points,3)
assert.equal(state.scores.find(s=>s.name==='Guest Two').points,2)
assert.equal(state.scores.find(s=>s.name==='WhatsApp Alice').points,3)
call(aliceSession,'profile',{displayName:'Renamed Alice'})
assert.equal(guest(guest1,'party:state').players[2].name,'Renamed Alice','profile rename propagates into room player data')
assert.equal(guest(guest1,'party:state').scores.some(p=>p.name==='Renamed Alice'),true,'leaderboard name propagates')
const rebooted=new NightPlay(db,{now:()=>clock})
assert.equal(rebooted.request({action:'party:state',guestSession:guest1,code}).phase,'finished','rooms persist after process restart')
clock+=86400001
assert.throws(()=>rebooted.request({action:'party:state',guestSession:guest1,code}),e=>[404,410].includes(e.status),'expired rooms reject reads')
rebooted.partyRooms.cleanup()
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_party_rooms').get().n,0)
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_party_players').get().n,0,'expired players cascade')
assert.equal(db.prepare('SELECT COUNT(*) n FROM play_party_votes').get().n,0,'expired ballots cascade')
db.close()
console.log('PASS NIGHT Play remote party rooms: 3 clients, host-only actions, private votes, persisted state, names, expiration')
