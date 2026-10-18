// Owner-only NIGHT Play Studio. Website sessions are verified by signed RPC;
// role assignment is derived exclusively from bot-verified identity mappings.
const USER_ID=/^(?:[1-9]\d{6,14}|jid:\d+@(?:lid|hosted\.lid))$/
const clean=(value,max)=>String(value||'').replace(/[\p{Cc}\p{Cf}<>]/gu,'').trim().slice(0,max)
export class StudioAccessError extends Error {
 constructor(status,message){super(message);this.status=status}
}
export class NightPlayStudio {
 constructor(play,{operatorIdentities=process.env.NIGHT_PLAY_OPERATOR_IDENTITIES||''}={}){
  this.play=play
  this.db=play.db
  this.operators=new Set(String(operatorIdentities).split(/[\s,;]+/).map(x=>x.trim()).filter(x=>USER_ID.test(x)))
  this.db.exec(`CREATE TABLE IF NOT EXISTS play_site_notice (
   id INTEGER PRIMARY KEY CHECK(id=1),
   title TEXT NOT NULL,
   message TEXT NOT NULL,
   enabled INTEGER NOT NULL DEFAULT 0,
   revision INTEGER NOT NULL DEFAULT 1,
   updated_at INTEGER NOT NULL
  )`)
 }
 isOperator(userId){
  if(!userId||!this.operators.size)return false // configuration must never default to public access
  return this.db.prepare('SELECT identity FROM play_identities WHERE user_id=?').all(userId)
   .some(({identity})=>this.operators.has(identity))
 }
 assertOperator(userId){
  if(!this.isOperator(userId))throw new StudioAccessError(403,'NIGHT Play Studio access is restricted.')
 }
 publicNotice(){
  const row=this.db.prepare('SELECT title,message,enabled,revision FROM play_site_notice WHERE id=1').get()
  if(!row)return {enabled:false,revision:0}
  return {title:row.title,message:row.message,enabled:row.enabled===1,revision:row.revision}
 }
 status(userId){
  this.assertOperator(userId)
  return {authorized:true,notice:this.publicNotice()}
 }
 save(userId,{noticeTitle,noticeMessage,noticeEnabled}){
  this.assertOperator(userId)
  if(typeof noticeTitle!=='string'||typeof noticeMessage!=='string'||typeof noticeEnabled!=='boolean')throw new StudioAccessError(400,'Complete the announcement fields.')
  const title=clean(noticeTitle,80),message=clean(noticeMessage,240)
  if(!title||!message||noticeTitle.length>160||noticeMessage.length>420)throw new StudioAccessError(400,'Announcement must have a short title and message.')
  this.play.limit('studio-write:'+userId,12,60_000)
  this.db.prepare(`INSERT INTO play_site_notice (id,title,message,enabled,revision,updated_at)
   VALUES (1,?,?,?,1,?)
   ON CONFLICT(id) DO UPDATE SET title=excluded.title,message=excluded.message,enabled=excluded.enabled,
     revision=play_site_notice.revision+1,updated_at=excluded.updated_at`)
   .run(title,message,noticeEnabled?1:0,this.play.now())
  return {saved:true,notice:this.publicNotice()}
 }
}
