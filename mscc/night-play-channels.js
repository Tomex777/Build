import { randomBytes } from 'node:crypto'
import { ChannelPublisher } from './channel-publisher/service.js'

// Only an already verified NIGHT Play identity can request a channel claim.
// Following a channel or knowing its public URL never confers admin access.
const LINK=/^https:\/\/(?:www\.)?whatsapp\.com\/channel\/([A-Za-z0-9]{10,64})\/?(?:\?[^#]*)?$/i
const JID=/^\d{8,25}@newsletter$/
export class ChannelError extends Error {
  constructor(status, message) { super(message); this.status=status }
}
const fail=(status, message)=>{ throw new ChannelError(status,message) }
const str=x=>String(x||'').trim()
const exactChannelName=x=>{
  // This is authoritative WhatsApp metadata, not a user-entered HTML fragment.
  // Never strip ZWJ/RTL controls or slice UTF-16 code units: watermark must be exact.
  if(typeof x!=='string'||!x.trim()||Buffer.byteLength(x,'utf8')>512||/\p{Cc}/u.test(x))
    fail(502,'WhatsApp returned an invalid channel name.')
  return x
}
const roleOf=meta=>str((meta?.viewer_metadata||meta?.viewerMetadata||{}).role).toUpperCase()
const isAdmin=role=>['ADMIN','OWNER','SUPERADMIN','SUPER_ADMIN','CREATOR'].includes(role)
function infoOf(meta) {
  const id=str(meta?.id||meta?.jid)
  if(!JID.test(id)) fail(502,'WhatsApp could not identify this channel.')
  return {id,name:exactChannelName((typeof meta?.name==='string'?meta.name:meta?.name?.text)||meta?.thread_metadata?.name?.text||meta?.subject),role:roleOf(meta)}
}
const view=r=>({id:r.channel_id,name:r.name,status:r.state,verifiedAt:r.verified_at||null,
  ...(r.state!=='connected'?{proof:r.proof,expiresAt:r.expires_at}:{} )})
export class NightPlayChannels {
  constructor(play,{now=Date.now}={}) {
    this.play=play;this.db=play.db;this.now=now
    this.db.exec('CREATE TABLE IF NOT EXISTS play_channel_consent(user_id TEXT PRIMARY KEY REFERENCES play_users(id) ON DELETE CASCADE,account_id TEXT NOT NULL,channel_id TEXT NOT NULL,channel_name TEXT NOT NULL,expires_at INTEGER NOT NULL);'
      +'CREATE TABLE IF NOT EXISTS play_channel_claims(user_id TEXT NOT NULL REFERENCES play_users(id) ON DELETE CASCADE,channel_id TEXT NOT NULL,account_id TEXT NOT NULL,name TEXT NOT NULL,state TEXT NOT NULL CHECK(state IN (\'awaiting_admin\',\'awaiting_proof\',\'connected\')),proof TEXT NOT NULL,created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL,verified_at INTEGER,PRIMARY KEY(user_id,channel_id));'
      +'CREATE INDEX IF NOT EXISTS play_channel_claim_by_channel ON play_channel_claims(channel_id);')
    this.publisher = new ChannelPublisher(this,{now})
  }
  owner(identity) {
    const p=this.play.linkedProfile(identity)
    if(!p)fail(401,'Connect your WhatsApp account to NIGHT Play first. Use .minigames.')
    return p.id
  }
  list(userId) {
    if(!userId)fail(401,'Connect WhatsApp to continue.')
    return {channels:this.db.prepare('SELECT * FROM play_channel_claims WHERE user_id=? ORDER BY verified_at DESC,created_at DESC LIMIT 40').all(userId).map(view)}
  }
  async begin(identity, account, invite) {
    const userId=this.owner(identity)
    const match=LINK.exec(str(invite))
    if(!match)fail(400,'Send a full WhatsApp Channel link: .joinchannel https://whatsapp.com/channel/...')
    if(!account?.connected||!account.sock?.newsletterMetadata)fail(503,'NIGHT is not connected to WhatsApp right now.')
    this.play.limit('channel-begin:'+userId,8,600000)
    let info
    try {info=infoOf(await account.sock.newsletterMetadata('invite',match[1]))}
    catch(e){if(e instanceof ChannelError)throw e;fail(502,'Could not retrieve this channel. Check your link.')}
    const occupied=this.db.prepare("SELECT user_id FROM play_channel_claims WHERE channel_id=? AND state='connected'").get(info.id)
    if(occupied && occupied.user_id!==userId)fail(409,'This channel is already connected to another NIGHT Play account.')
    // Stage consent only; intentionally no subscribe or admin action.
    this.db.prepare('INSERT INTO play_channel_consent VALUES (?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET account_id=excluded.account_id,channel_id=excluded.channel_id,channel_name=excluded.channel_name,expires_at=excluded.expires_at')
      .run(userId,account.id,info.id,info.name,this.now()+900000)
    return {name:info.name}
  }
  cancel(identity){
    const id=this.owner(identity)
    return Boolean(this.db.prepare('DELETE FROM play_channel_consent WHERE user_id=?').run(id).changes)
  }
  async accept(identity, account) {
    const userId=this.owner(identity)
    this.play.limit('channel-accept:'+userId,5,600000)
    const pending=this.db.prepare('SELECT * FROM play_channel_consent WHERE user_id=? AND expires_at>?').get(userId,this.now())
    if(!pending)fail(400,'No pending request. Start with .joinchannel <channel link>.')
    if(!account?.connected||pending.account_id!==account.id)fail(503,'Reply Yes to the same connected NIGHT account.')
    if(!account.sock?.newsletterMetadata||!account.sock?.newsletterFollow)fail(503,'Channel joining is unavailable.')
    const info=infoOf(await account.sock.newsletterMetadata('jid',pending.channel_id))
    const occupied=this.db.prepare("SELECT user_id FROM play_channel_claims WHERE channel_id=? AND state='connected'").get(info.id)
    if(occupied&&occupied.user_id!==userId)fail(409,'Another administrator already connected this channel.')
    if(!isAdmin(info.role)) {
      try{await account.sock.newsletterFollow(info.id)}
      catch{fail(502,'WhatsApp could not confirm the channel follow. No admin access was granted.')}
    }
    const current=this.db.prepare('SELECT * FROM play_channel_claims WHERE user_id=? AND channel_id=?').get(userId,info.id)
    this.db.prepare('DELETE FROM play_channel_consent WHERE user_id=?').run(userId)
    if(current?.state==='connected')return {name:current.name,already:true}
    const proof='NIGHT-VERIFY-'+randomBytes(9).toString('hex').toUpperCase()
    const state=isAdmin(info.role)?'awaiting_proof':'awaiting_admin'
    this.db.prepare('INSERT INTO play_channel_claims VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,channel_id) DO UPDATE SET account_id=excluded.account_id,name=excluded.name,state=excluded.state,proof=excluded.proof,created_at=excluded.created_at,expires_at=excluded.expires_at,verified_at=NULL')
      .run(userId,info.id,account.id,info.name,state,proof,this.now(),this.now()+86400000,null)
    return {name:info.name,proof,state,channelId:info.id}
  }
  async verify(identity, accounts, channelId='') {
    return this.verifyUser(this.owner(identity),accounts,channelId)
  }
  async verifyUser(userId, accounts, channelId='') {
    if(!userId)fail(401,'Connect WhatsApp to continue.')
    this.play.limit('channel-verify:'+userId,10,600000)
    const row=channelId?
      this.db.prepare('SELECT * FROM play_channel_claims WHERE user_id=? AND channel_id=?').get(userId,channelId):
      this.db.prepare("SELECT * FROM play_channel_claims WHERE user_id=? AND state!='connected' ORDER BY created_at DESC").get(userId)
    if(!row)fail(404,'No channel awaiting verification. Run .joinchannel <link> first.')
    if(row.state==='connected')return {connected:true,name:row.name}
    if(row.expires_at<=this.now())fail(410,'Channel verification expired. Start again with .joinchannel <link>.')
    const account=accounts.get(row.account_id)
    if(!account?.connected||!account.sock?.newsletterMetadata)fail(503,'NIGHT is not connected to WhatsApp.')
    let meta
    try{meta=infoOf(await account.sock.newsletterMetadata('jid',row.channel_id))}
    catch{fail(502,'WhatsApp channel metadata is unavailable.')}
    if(!isAdmin(meta.role)){
      this.db.prepare("UPDATE play_channel_claims SET state='awaiting_admin' WHERE user_id=? AND channel_id=?").run(userId,row.channel_id)
      return {connected:false,name:meta.name,reason:'admin',proof:row.proof}
    }
    if(typeof account.sock.newsletterFetchMessages!=='function')fail(503,'This NIGHT connection cannot verify channel posts.')
    let messages
    try{messages=await account.sock.newsletterFetchMessages('jid',row.channel_id,30)}
    catch{fail(502,'Could not retrieve recent channel posts. Try again later.')}
    // Only a real channel admin can publish a unique, newly generated proof.
    const confirmed=Array.isArray(messages)&&messages.some(m=>{
      const timestamp=Number(m.messageTimestamp||0)*1000
      const body=str(m.message?.conversation||m.message?.extendedTextMessage?.text||m.message?.imageMessage?.caption)
      return timestamp>=row.created_at-1000&&timestamp<=this.now()+60000&&body.includes(row.proof)
    })
    if(!confirmed){
      this.db.prepare("UPDATE play_channel_claims SET state='awaiting_proof' WHERE user_id=? AND channel_id=?").run(userId,row.channel_id)
      return {connected:false,name:meta.name,reason:'proof',proof:row.proof}
    }
    this.play.transaction(()=>{
      const occupied=this.db.prepare("SELECT user_id FROM play_channel_claims WHERE channel_id=? AND state='connected'").get(row.channel_id)
      if(occupied&&occupied.user_id!==userId)fail(409,'Another NIGHT Play account already manages this channel.')
      this.db.prepare("UPDATE play_channel_claims SET state='connected',verified_at=?,name=? WHERE user_id=? AND channel_id=?").run(this.now(),meta.name,userId,row.channel_id)
    })
    return {connected:true,name:meta.name,channelId:row.channel_id}
  }
  remove(userId,id){
    if(!JID.test(str(id)))fail(400,'Invalid channel.')
    const result=this.db.prepare('DELETE FROM play_channel_claims WHERE user_id=? AND channel_id=?').run(userId,id)
    if(!result.changes)fail(404,'This channel is not connected to your account.')
    this.publisher.invalidateChannel(id)
    return {ok:true}
  }
  removeUser(userId) {
    // Cache eviction must occur after revoking all connected claims.
    const names=this.db.prepare('SELECT channel_id FROM play_channel_claims WHERE user_id=?').all(userId)
    this.db.prepare('DELETE FROM play_channel_consent WHERE user_id=?').run(userId)
    this.db.prepare('DELETE FROM play_channel_claims WHERE user_id=?').run(userId)
    for(const {channel_id} of names) this.publisher.invalidateChannel(channel_id)
  }
}
