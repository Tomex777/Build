import { randomBytes, randomUUID, createHash } from 'node:crypto'

// Permanent authoritative party-room state, stored beside NIGHT's existing
// verified WhatsApp sessions. Every action is reached only through signed RPC.
export class PartyRoomError extends Error {
  constructor(status, message) { super(message); this.status = status }
}
const modes = new Set(['wyr','mlt','tot','nhie','trivia','quiz'])
const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const hash = v => createHash('sha256').update(v).digest('hex')
const fail = (status, message) => { throw new PartyRoomError(status, message) }
const cleanName = s => String(s || '').replace(/[\p{Cc}\p{Cf}<>]/gu, '').replace(/\s+/g,' ').trim().slice(0,24)
function validate(mode, questions) {
  if (!modes.has(mode) || !Array.isArray(questions) || questions.length < 3 || questions.length > 10) fail(400,'Choose 3 to 10 questions.')
  return questions.map(q => {
    if (!q || typeof q !== 'object' || typeof q.prompt !== 'string' || q.prompt.trim().length < 3 || q.prompt.length > 180) fail(400,'Invalid question.')
    const p = q.prompt.trim()
    const opts = Array.isArray(q.options) ? q.options : []
    if (mode === 'mlt' || mode === 'nhie') {
      if (opts.length) fail(400,'Invalid prompt choices.')
      return { prompt:p, options:[], correct:null }
    }
    if (opts.length < 2 || opts.length > 4 || opts.some(o => typeof o !== 'string' || !o.trim() || o.length > 90)) fail(400,'Invalid question choices.')
    if ((mode === 'quiz' || mode === 'trivia') && (!Number.isInteger(q.correct) || q.correct < 0 || q.correct >= opts.length)) fail(400,'Invalid answer key.')
    return { prompt:p, options:opts.map(o=>o.trim()), correct:mode==='quiz'||mode==='trivia'?q.correct:null }
  })
}
export class NightPlayParty {
  constructor(db, { now = Date.now } = {}) {
    this.db = db
    this.now = now
    db.exec('CREATE TABLE IF NOT EXISTS play_party_rooms (id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, mode TEXT NOT NULL, host_id TEXT NOT NULL REFERENCES play_users(id) ON DELETE CASCADE, questions TEXT NOT NULL, phase TEXT NOT NULL, round INTEGER NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)')
    db.exec('CREATE TABLE IF NOT EXISTS play_party_players (room_id TEXT NOT NULL REFERENCES play_party_rooms(id) ON DELETE CASCADE, player_id TEXT NOT NULL, auth_kind TEXT NOT NULL, auth_id TEXT NOT NULL, guest_name TEXT NOT NULL, joined_at INTEGER NOT NULL, PRIMARY KEY(room_id,player_id), UNIQUE(room_id,auth_kind,auth_id))')
    db.exec('CREATE TABLE IF NOT EXISTS play_party_votes (room_id TEXT NOT NULL, round INTEGER NOT NULL, player_id TEXT NOT NULL, answer INTEGER NOT NULL, PRIMARY KEY(room_id,round,player_id), FOREIGN KEY(room_id,player_id) REFERENCES play_party_players(room_id,player_id) ON DELETE CASCADE)')
    db.exec('CREATE INDEX IF NOT EXISTS play_party_expiry ON play_party_rooms(expires_at)')
  }
  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE')
    try { const result=fn();this.db.exec('COMMIT');return result }
    catch(e){this.db.exec('ROLLBACK');throw e}
  }
  cleanup() {
    this.db.prepare('DELETE FROM play_party_rooms WHERE expires_at<=?').run(this.now())
  }
  room(code) {
    if (!/^[A-HJ-NP-Z2-9]{8}$/.test(String(code||''))) fail(400,'Invalid invitation.')
    const row=this.db.prepare('SELECT * FROM play_party_rooms WHERE code=?').get(code)
    if (!row) fail(404,'Room not found.')
    if (row.expires_at<=this.now()) fail(410,'This room has expired.')
    return row
  }
  players(room) {
    // Verified user names are resolved on every read, not snapshotted. This
    // means Profile renames update active rosters, results and leaderboards.
    return this.db.prepare("SELECT p.player_id id, p.auth_kind, p.auth_id, CASE WHEN p.auth_kind='user' THEN COALESCE(u.display_name,p.guest_name) ELSE p.guest_name END name FROM play_party_players p LEFT JOIN play_users u ON p.auth_kind='user' AND u.id=p.auth_id WHERE p.room_id=? ORDER BY p.joined_at,p.player_id").all(room.id)
  }
  actor(userId, guestSession) {
    if (userId) return {kind:'user',id:userId}
    if (!/^[A-Za-z0-9_-]{43}$/.test(String(guestSession||''))) return null
    return {kind:'guest',id:hash(guestSession)}
  }
  state(room, actor) {
    const host = actor?.kind==='user' && room.host_id===actor.id
    const players = this.players(room)
    const me=actor ? players.find(p=>p.auth_kind===actor.kind && p.auth_id===actor.id):null
    // Only room members and the verified host can read game state.
    if (!host && !me) fail(403,'Join using your invitation link first.')
    const questions=JSON.parse(room.questions),current=questions[room.round]
    const votes=this.db.prepare('SELECT player_id,answer FROM play_party_votes WHERE room_id=? AND round=?').all(room.id,room.round)
    const choices=room.mode==='mlt'?players.map(p=>p.name):room.mode==='nhie'?['I have','Never']:current.options
    const revealed=room.phase==='reveal' || room.phase==='finished'
    const count=choices.map((_,i)=>votes.filter(v=>v.answer===i).length)
    const scoreboard=players.map(p=>({
      id:p.id,name:p.name,
      points:0
    }))
    // Score all completed rounds, including previous rounds. Use JS over at
    // most 160 votes: SQLite JSON operators differ between supported builds.
    const allVotes=room.mode==='quiz'||room.mode==='trivia'?
      this.db.prepare('SELECT round,player_id,answer FROM play_party_votes WHERE room_id=?').all(room.id):[]
    if (allVotes.length) for (const p of scoreboard) p.points=allVotes.filter(v=>v.player_id===p.id&&questions[v.round]?.correct===v.answer).length
    const hostName=this.db.prepare('SELECT display_name FROM play_users WHERE id=?').get(room.host_id)?.display_name||'Quiz host'
    return {code:room.code,mode:room.mode,host,hostName,me:me?.id||null,
      phase:room.phase,round:room.round,total:questions.length,players:players.map(p=>({id:p.id,name:p.name})),
      question:room.phase==='lobby'?null:{prompt:current.prompt,choices,correct:revealed?current.correct:null},
      voted:me?votes.some(v=>v.player_id===me.id):false,votedCount:votes.length,
      results:revealed?count:null,scores:revealed?scoreboard.sort((a,b)=>b.points-a.points):null,
      expiresAt:room.expires_at}
  }
  request(action, {userId,guestSession,code,mode,questions,guestName,answer}) {
    const actor=this.actor(userId,guestSession)
    if(action==='party:create'){
      if(!userId)fail(401,'Connect WhatsApp to host an online room.')
      const q=validate(mode,questions)
      const id=randomUUID()
      return this.transaction(()=>{
        let invite
        for(let n=0;n<5;n++){
          invite=Array.from(randomBytes(8),b=>alphabet[b&31]).join('')
          if(!this.db.prepare('SELECT 1 FROM play_party_rooms WHERE code=?').get(invite))break
          invite=null
        }
        if(!invite)fail(503,'Room codes temporarily unavailable.')
        this.db.prepare("INSERT INTO play_party_rooms VALUES (?,?,?,?,?,'lobby',0,?,?)")
          .run(id,invite,mode,userId,JSON.stringify(q),this.now(),this.now()+86400000)
        return this.state(this.room(invite),actor)
      })
    }
    const room=this.room(code)
    const host=actor?.kind==='user'&&room.host_id===actor.id
    if(action==='party:join'){
      if(!actor)fail(400,'Reopen your invitation and try joining again.')
      if(room.phase!=='lobby')fail(409,'This room has already started.')
      if(host)fail(400,'The host is already running this room.')
      const name=actor.kind==='user'?
        this.db.prepare('SELECT display_name FROM play_users WHERE id=?').get(actor.id)?.display_name:cleanName(guestName)
      if(!name)fail(400,'Enter your player name.')
      return this.transaction(()=>{
        const all=this.players(room)
        const previous=all.find(p=>p.auth_kind===actor.kind&&p.auth_id===actor.id)
        if(previous)return this.state(room,actor)
        if(all.length>=16)fail(409,'This room is full.')
        if(all.some(p=>p.name.toLowerCase()===name.toLowerCase()))fail(409,'That name is already in use.')
        this.db.prepare('INSERT INTO play_party_players VALUES (?,?,?,?,?,?)').run(room.id,randomUUID(),actor.kind,actor.id,name,this.now())
        return this.state(room,actor)
      })
    }
    if(action==='party:state')return this.state(room,actor)
    if(action==='party:start'){
      if(!host)fail(403,'Only the host can start.')
      return this.transaction(()=>{
        if(room.phase!=='lobby')fail(409,'Already started.')
        if(this.players(room).length<2)fail(409,'Wait for at least two people to join.')
        this.db.prepare("UPDATE play_party_rooms SET phase='vote' WHERE id=?").run(room.id)
        return this.state({...room,phase:'vote'},actor)
      })
    }
    if(action==='party:vote'){
      if(host||!actor)fail(403,'Only joined players can vote.')
      if(room.phase!=='vote')fail(409,'Voting is closed.')
      const players=this.players(room)
      const me=players.find(p=>p.auth_kind===actor.kind&&p.auth_id===actor.id)
      if(!me)fail(403,'Join the room first.')
      const q=JSON.parse(room.questions)[room.round]
      const choiceCount=room.mode==='mlt'?players.length:room.mode==='nhie'?2:q.options.length
      if(!Number.isInteger(answer)||answer<0||answer>=choiceCount)fail(400,'Invalid choice.')
      return this.transaction(()=>{
        try{this.db.prepare('INSERT INTO play_party_votes VALUES (?,?,?,?)').run(room.id,room.round,me.id,answer)}
        catch{fail(409,'You already answered this round.')}
        const count=this.db.prepare('SELECT COUNT(*) n FROM play_party_votes WHERE room_id=? AND round=?').get(room.id,room.round).n
        const phase=count===players.length?'reveal':'vote'
        if(phase==='reveal')this.db.prepare('UPDATE play_party_rooms SET phase=? WHERE id=?').run(phase,room.id)
        return this.state({...room,phase},actor)
      })
    }
    if(action==='party:advance'){
      if(!host)fail(403,'Only the host can move to the next question.')
      if(room.phase!=='reveal')fail(409,"Wait for everyone's vote.")
      return this.transaction(()=>{
        const round=room.round+1,total=JSON.parse(room.questions).length
        const phase=round>=total?'finished':'vote'
        const next=phase==='finished'?room.round:round
        this.db.prepare('UPDATE play_party_rooms SET phase=?,round=? WHERE id=?').run(phase,next,room.id)
        return this.state({...room,phase,round:next},actor)
      })
    }
    fail(400,'Unknown party action.')
  }
}
