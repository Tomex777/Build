import { randomBytes, randomUUID } from 'node:crypto'
import { PlayError } from './night-play.js'
const WIN=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]]
const ALPHABET='ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const newCode=()=>[...randomBytes(9)].map(n=>ALPHABET[n&31]).join('')
export class NightPlayMatches {
 constructor(db,{now=Date.now}={}){
  this.db=db;this.now=now
  db.exec(`CREATE TABLE IF NOT EXISTS play_matches (
   id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, x TEXT NOT NULL, o TEXT,
   visibility TEXT NOT NULL, board TEXT NOT NULL DEFAULT '.........',
   turn TEXT NOT NULL DEFAULT 'X', status TEXT NOT NULL,
   winner TEXT,version INTEGER NOT NULL DEFAULT 1,
   created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL
  );
  CREATE INDEX IF NOT EXISTS play_matches_lookup ON play_matches(status,visibility,created_at);`)
 }
 atomic(fn){
  this.db.exec('BEGIN IMMEDIATE')
  try{const r=fn();this.db.exec('COMMIT');return r}catch(e){this.db.exec('ROLLBACK');throw e}
 }
 row(id){
  if(typeof id!=='string'||!/^[\da-f-]{36}$/i.test(id))throw new PlayError(400,'Invalid match.')
  return this.db.prepare('SELECT * FROM play_matches WHERE id=?').get(id)
 }
 view(row,user){
  if(!row||(row.x!==user&&row.o!==user))throw new PlayError(404,'Match not found.')
  const side=row.x===user?'X':'O',opponentId=side==='X'?row.o:row.x
  const other=opponentId?this.db.prepare('SELECT display_name FROM play_users WHERE id=?').get(opponentId):null
  return {matchId:row.id,code:row.code,mode:row.visibility,side,
   opponent:other?.display_name||null,board:row.board,turn:row.turn,
   status:row.status,winner:row.winner,version:row.version,updatedAt:row.updated_at}
 }
 create(user,visibility='invite'){
  const count=this.db.prepare("SELECT COUNT(*) n FROM play_matches WHERE (x=? OR o=?) AND status IN ('waiting','active')").get(user,user).n
  if(count>=12)throw new PlayError(429,'Finish an existing match first.')
  const id=randomUUID(),code=newCode(),stamp=this.now()
  this.db.prepare("INSERT INTO play_matches(id,code,x,visibility,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")
   .run(id,code,user,visibility,'waiting',stamp,stamp)
  return this.view(this.row(id),user)
 }
 join(user,code){
  const key=String(code||'').trim().toUpperCase().replace(/[\s-]/g,'')
  if(!/^[A-HJ-NP-Z2-9]{9}$/.test(key))throw new PlayError(400,'Enter a valid invitation code.')
  return this.atomic(()=>{
   const row=this.db.prepare('SELECT * FROM play_matches WHERE code=?').get(key)
   if(!row)throw new PlayError(404,'Invitation not found.')
   if(row.x===user||row.o===user)return this.view(row,user)
   if(row.status!=='waiting'||row.o)throw new PlayError(409,'This match is full.')
   if(this.now()-row.created_at>86400000)throw new PlayError(410,'Invitation expired.')
   this.db.prepare("UPDATE play_matches SET o=?,status='active',version=version+1,updated_at=? WHERE id=?").run(user,this.now(),row.id)
   return this.view(this.row(row.id),user)
  })
 }
 random(user){
  return this.atomic(()=>{
   const mine=this.db.prepare("SELECT * FROM play_matches WHERE x=? AND visibility='public' AND status='waiting' ORDER BY created_at DESC LIMIT 1").get(user)
   if(mine)return this.view(mine,user)
   const row=this.db.prepare("SELECT * FROM play_matches WHERE x<>? AND status='waiting' AND visibility='public' AND created_at>? ORDER BY created_at LIMIT 1").get(user,this.now()-86400000)
   if(!row)return this.create(user,'public')
   this.db.prepare("UPDATE play_matches SET o=?,status='active',version=version+1,updated_at=? WHERE id=?").run(user,this.now(),row.id)
   return this.view(this.row(row.id),user)
  })
 }
 list(user){
  const rows=this.db.prepare("SELECT * FROM play_matches WHERE x=? OR o=? ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'waiting' THEN 1 ELSE 2 END,updated_at DESC LIMIT 16").all(user,user)
  return {matches:rows.map(row=>this.view(row,user))}
 }
 move(user,id,cell){
  if(!Number.isInteger(cell)||cell<0||cell>8)throw new PlayError(400,'Invalid square.')
  return this.atomic(()=>{
   const row=this.row(id)
   this.view(row,user)
   if(row.status!=='active')throw new PlayError(409,'This match is not active.')
   const side=row.x===user?'X':'O'
   if(row.turn!==side)throw new PlayError(409,'Wait for your turn.')
   if(row.board[cell]!=='.')throw new PlayError(409,'Square already taken.')
   const board=row.board.slice(0,cell)+side+row.board.slice(cell+1)
   const won=WIN.some(line=>line.every(i=>board[i]===side))
   const finished=won||!board.includes('.')
   const winner=won?side:(finished?'D':null)
   this.db.prepare('UPDATE play_matches SET board=?,turn=?,status=?,winner=?,version=version+1,updated_at=? WHERE id=?')
    .run(board,finished?'-':side==='X'?'O':'X',finished?'finished':'active',winner,this.now(),id)
   return this.view(this.row(id),user)
  })
 }
 leave(user,id){
  return this.atomic(()=>{
   const row=this.row(id)
   this.view(row,user)
   if(row.status==='finished')return this.view(row,user)
   if(row.status==='waiting'&&row.x===user){
    this.db.prepare('DELETE FROM play_matches WHERE id=?').run(id)
    return {left:true}
   }
   this.db.prepare("UPDATE play_matches SET status='finished',winner=?,turn='-',version=version+1,updated_at=? WHERE id=?")
    .run(row.x===user?'O':'X',this.now(),id)
   return this.view(this.row(id),user)
  })
 }
 removeUser(user){this.db.prepare('DELETE FROM play_matches WHERE x=? OR o=?').run(user,user)}
 request(action,user,{matchId,code,cell}={}){
  if(action==='match:create')return this.create(user)
  if(action==='match:random')return this.random(user)
  if(action==='match:join')return this.join(user,code)
  if(action==='match:list')return this.list(user)
  if(action==='match:state')return this.view(this.row(matchId),user)
  if(action==='match:move')return this.move(user,matchId,cell)
  if(action==='match:leave')return this.leave(user,matchId)
  throw new PlayError(400,'Unknown match action.')
 }
}
