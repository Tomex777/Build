import { randomBytes, randomUUID, createHash } from 'node:crypto'
import { PartyRoomError } from './night-play-party.js'

const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const words=['bicycle','butterfly','castle','cat','coconut','elephant','football','giraffe','guitar','helicopter','ice cream','island','jellyfish','keyboard','kite','lighthouse','moon','mountain','octopus','pencil','penguin','pineapple','pizza','rainbow','robot','rocket','sailboat','sandcastle','snowman','spider','sunflower','telescope','train','turtle','umbrella','volcano','waterfall','whale']
const fail=(status,message)=>{throw new PartyRoomError(status,message)}
const hash=x=>createHash('sha256').update(x).digest('hex')
const clean=x=>String(x||'').replace(/[\p{Cc}\p{Cf}<>]/gu,'').replace(/\s+/g,' ').trim().slice(0,24)
const normalize=x=>String(x||'').normalize('NFKC').toLowerCase().replace(/[^a-z0-9]/g,'')
const colors=new Set(['#202f2c','#e2343b','#2876ca','#2a914e','#e3b02a','#ffffff'])

// State survives restarts in the existing account database. The drawing word,
// clock, turns and scores are authoritative; guests never receive the answer.
export class NightPlayDraw {
 constructor(db,{now=Date.now}={}){this.db=db;this.now=now;db.exec('CREATE TABLE IF NOT EXISTS play_draw_rooms (code TEXT PRIMARY KEY, host_id TEXT NOT NULL REFERENCES play_users(id) ON DELETE CASCADE, state TEXT NOT NULL, expires_at INTEGER NOT NULL)')}
 cleanup(){this.db.prepare('DELETE FROM play_draw_rooms WHERE expires_at<=?').run(this.now())}
 removeUser(id){this.db.prepare('DELETE FROM play_draw_rooms WHERE host_id=?').run(id);for(const row of this.db.prepare('SELECT * FROM play_draw_rooms').all()){const s=JSON.parse(row.state);if(s.players.some(p=>p.kind==='user'&&p.auth===id)){s.players=s.players.filter(p=>!(p.kind==='user'&&p.auth===id));s.phase='finished';this.save(row.code,s)}}}
 save(code,s){this.db.prepare('UPDATE play_draw_rooms SET state=? WHERE code=?').run(JSON.stringify(s),code)}
 actor(userId,guestSession){return userId?{kind:'user',auth:userId}:/^[A-Za-z0-9_-]{43}$/.test(String(guestSession||''))?{kind:'guest',auth:hash(guestSession)}:null}
 name(p){return p.kind==='user'?this.db.prepare('SELECT display_name FROM play_users WHERE id=?').get(p.auth)?.display_name||p.name:p.name}
 round(s){s.phase='drawing';s.word=words[randomBytes(2).readUInt16BE()%words.length];s.lines=[];s.canvasVersion++;s.guessed=[];s.log=[];s.endsAt=this.now()+60000}
 snapshot(code,s,actor,since){const me=s.players.find(p=>p.kind===actor?.kind&&p.auth===actor?.auth);if(!me)fail(403,'Join using your invitation link first.');const drawer=s.players[s.turn%s.players.length];const offset=since?.canvasVersion===s.canvasVersion&&Number.isInteger(since?.count)&&since.count>=0&&since.count<=s.lines.length?since.count:0;return {code,me:me.id,host:actor.kind==='user'&&s.host===actor.auth,phase:s.phase,round:s.round,total:s.total,drawer:drawer.id,word:s.phase==='drawing'&&drawer.id!==me.id?null:s.word,wordLength:s.word?.length||0,endsAt:s.endsAt,serverNow:this.now(),players:s.players.map(p=>({id:p.id,name:this.name(p),points:p.points})),guessed:s.guessed,log:s.log,canvasVersion:s.canvasVersion,strokeOffset:offset,strokeCount:s.lines.length,strokes:s.lines.slice(offset)}}
 request(action,{userId,guestSession,guestName,code,stroke,guess,since,mutationId}){
  const actor=this.actor(userId,guestSession)
  this.cleanup()
  if(action==='draw:create'){
   if(!userId)fail(401,'Connect WhatsApp to host an online room.')
   const active=this.db.prepare('SELECT COUNT(*) n FROM play_draw_rooms WHERE host_id=?').get(userId).n
   if(active>=8)fail(429,'You already have eight drawing rooms. Rejoin one of them.')
   for(let attempt=0;attempt<5;attempt++){const invite=Array.from(randomBytes(8),b=>alphabet[b&31]).join('');const s={host:userId,players:[{id:randomUUID(),kind:'user',auth:userId,name:'Host',points:0}],phase:'lobby',round:0,total:0,turn:0,word:null,lines:[],canvasVersion:0,guessed:[],log:[],endsAt:0,mutations:[]};try{this.db.prepare('INSERT INTO play_draw_rooms VALUES (?,?,?,?)').run(invite,userId,JSON.stringify(s),this.now()+86400000);return this.snapshot(invite,s,actor,since)}catch(e){if(!String(e.message).includes('UNIQUE'))throw e}}
   fail(503,'Room codes temporarily unavailable.')
  }
  if(!/^[A-HJ-NP-Z2-9]{8}$/.test(String(code||'')))fail(400,'Invalid invitation.')
  this.db.exec('BEGIN IMMEDIATE')
  try{
   const row=this.db.prepare('SELECT * FROM play_draw_rooms WHERE code=?').get(code)
   if(!row)fail(404,'Room not found or expired.')
   const s=JSON.parse(row.state),host=userId===s.host
   let me=s.players.find(p=>p.kind===actor?.kind&&p.auth===actor?.auth)
   if(action==='draw:join'&&!me){if(!actor)fail(400,'Reopen your invitation to join.');if(s.phase!=='lobby')fail(409,'The game has already started.');if(s.players.length>=8)fail(409,'This room is full.');const name=userId?this.db.prepare('SELECT display_name FROM play_users WHERE id=?').get(userId)?.display_name:clean(guestName);if(!name)fail(400,'Enter your player name.');if(s.players.some(p=>this.name(p).toLowerCase()===name.toLowerCase()))fail(409,'That name is already in use.');me={id:randomUUID(),kind:actor.kind,auth:actor.auth,name,points:0};s.players.push(me)}
   if(!me)fail(403,'Join using your invitation link first.')
   if(s.phase==='drawing'&&this.now()>=s.endsAt)s.phase='reveal'
   const drawer=s.players[s.turn%s.players.length]
   if(action==='draw:start'){if(!host)fail(403,'Only the host can start.');if(s.phase!=='lobby')fail(409,'The game has already started.');if(s.players.length<2)fail(409,'Wait for a friend to join.');s.total=s.players.length*2;s.round=1;this.round(s)}
   else if(action==='draw:stroke'){
    if(s.phase!=='drawing'||me.id!==drawer.id)fail(403,'Only the current artist can draw.')
    if(!/^[A-Za-z0-9_-]{16,64}$/.test(String(mutationId||'')))fail(400,'Invalid drawing update.')
    if(!s.mutations.includes(mutationId)){
     if(!stroke||!colors.has(stroke.color)||!Array.isArray(stroke.points)||stroke.points.length<1||stroke.points.length>48||stroke.points.some(p=>!Array.isArray(p)||p.length!==2||p.some(n=>!Number.isFinite(n)||n<0||n>1)))fail(400,'Invalid drawing update.')
     if(s.lines.length>=600)fail(429,'The canvas is full. Clear it to keep drawing.')
     s.lines.push({color:stroke.color,points:stroke.points});s.mutations.push(mutationId);s.mutations=s.mutations.slice(-800)
    }
   }else if(action==='draw:clear'){if(s.phase!=='drawing'||drawer.id!==me.id)fail(403,'Only the current artist can clear.');s.lines=[];s.canvasVersion++}
   else if(action==='draw:guess'){
    if(s.phase!=='drawing'||drawer.id===me.id)fail(403,'Wait for your turn to guess.')
    if(s.guessed.includes(me.id))fail(409,'You already guessed this word.')
    const text=clean(guess);if(!text)fail(400,'Enter a guess.')
    const correct=normalize(text)===normalize(s.word)
    if(correct){s.guessed.push(me.id);me.points+=100+Math.ceil(Math.max(0,s.endsAt-this.now())/1000);drawer.points+=50;if(s.guessed.length===s.players.length-1)s.phase='reveal'}
    s.log.push({name:this.name(me),text:correct?'Guessed the word!':text,correct});s.log=s.log.slice(-20)
   }else if(action==='draw:advance'){if(!host)fail(403,'Only the host can continue.');if(s.phase!=='reveal')fail(409,'This round is still playing.');if(s.round>=s.total)s.phase='finished';else{s.turn++;s.round++;this.round(s)}}
   else if(!['draw:state','draw:join','draw:start','draw:stroke','draw:clear','draw:guess','draw:advance'].includes(action))fail(400,'Unknown drawing action.')
   this.save(code,s);const result=this.snapshot(code,s,actor,since);this.db.exec('COMMIT');return result
  }catch(e){this.db.exec('ROLLBACK');throw e}
 }
}
