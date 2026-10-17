import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { Resvg } from '@resvg/resvg-js'

// Live NIGHT Play Anonymous Nights. Admin decisions are bound to the verified
// website session. Public visitors possess only a short-lived, revocable
// submission token, and cannot view or publish the moderation queue.
export class NightError extends Error {
  constructor(status,message){super(message);this.status=status}
}
const fail=(status,text)=>{throw new NightError(status,text)}
const hash=v=>createHash('sha256').update(String(v)).digest('hex')
const tokenPattern=/^[A-Za-z0-9_-]{43}$/
const safeStyle=new Set(['cream','lilac','sage','rose','midnight','ink'])
const plain=v=>String(v||'').replace(/[\p{Cc}\p{Cf}]/gu,' ').trim()
const isAdmin=meta=>['ADMIN','OWNER','SUPERADMIN','SUPER_ADMIN','CREATOR'].includes(String((meta?.viewer_metadata||meta?.viewerMetadata||{}).role||'').toUpperCase())
const escape=s=>String(s).replace(/[<>&"']/g,c=>({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&apos;'}[c]))
const palettes={
 cream:['#FAEADD','#483C35','#9B5F43'],lilac:['#EBE7F7','#40394E','#7566AA'],
 sage:['#E1EEDF','#334536','#45765D'],rose:['#F6E2E7','#533E49','#AB6576'],
 midnight:['#192836','#F2F4F8','#A8BACD'],ink:['#202420','#F4F3EE','#BBC7BB'],
}
function graphic(body,style) {
  const [bg,fg,accent]=palettes[style]||palettes.cream
  const limit=body.length>170?29:body.length>110?33:39
  const size=body.length>170?42:body.length>110?46:54
  const words=body.split(/\s+/),lines=[],current={value:''}
  for(const word of words) {
    if(!word)continue
    const tryLine=(current.value?current.value+' ':'')+word
    if(tryLine.length>limit&&current.value){lines.push(current.value);current.value=word}
    else current.value=tryLine
    // A single long token must not break the card layout.
    while(current.value.length>limit){lines.push(current.value.slice(0,limit));current.value=current.value.slice(limit)}
  }
  if(current.value)lines.push(current.value)
  if(lines.length>10)fail(400,'This message is too long for a card.')
  const y0=380-Math.min(lines.length,9)*15
  const text=lines.map((line,i)=>'<text x="90" y="'+(y0+i*(size+15))+'" fill="'+fg+'" font-family="sans-serif" font-size="'+size+'" font-weight="600">'+escape(line)+'</text>').join('')
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'
    +'<rect width="1024" height="1024" fill="'+bg+'"/><rect x="33" y="33" width="958" height="958" rx="14" fill="none" stroke="'+fg+'" stroke-opacity=".18" stroke-width="2"/>'
    +'<path d="M90 155C90 133 108 115 130 115s40 18 40 40H90Zm-3 16h88v8H87Z" fill="'+fg+'"/>'
    +'<text x="195" y="160" fill="'+fg+'" font-family="sans-serif" font-size="28" font-weight="850" letter-spacing="3">NIGHT / PLAY</text>'
    +'<path d="M90 215h844" stroke="'+accent+'" stroke-opacity=".65" stroke-width="2"/>'
    +'<text x="90" y="290" fill="'+accent+'" font-family="sans-serif" font-size="20" font-weight="800" letter-spacing="3">ANONYMOUS NIGHT</text>'
    +text
    +'<path d="M90 901h844" stroke="'+accent+'" stroke-opacity=".5" stroke-width="2"/>'
    +'<text x="90" y="951" fill="'+fg+'" opacity=".8" font-family="sans-serif" font-size="19">Words shared in confidence.</text></svg>'
  try {return Buffer.from(new Resvg(svg,{fitTo:{mode:'width',value:1024}}).render().asPng())}
  catch {fail(500,'Card rendering is temporarily unavailable.')}
}
export class NightSessions {
  constructor(play,{now=Date.now}={}){
    this.play=play;this.db=play.db;this.now=now
    this.db.exec("CREATE TABLE IF NOT EXISTS play_nights(id TEXT PRIMARY KEY,channel_id TEXT NOT NULL,owner_id TEXT NOT NULL REFERENCES play_users(id) ON DELETE CASCADE,token_hash TEXT NOT NULL UNIQUE,status TEXT NOT NULL CHECK(status IN ('live','ended')),created_at INTEGER NOT NULL,expires_at INTEGER NOT NULL);"
      +"CREATE INDEX IF NOT EXISTS play_nights_owner ON play_nights(owner_id,channel_id);"
      +"CREATE TABLE IF NOT EXISTS play_night_posts(id TEXT PRIMARY KEY,night_id TEXT NOT NULL REFERENCES play_nights(id) ON DELETE CASCADE,style TEXT NOT NULL,body TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN ('pending','publishing','published','rejected','uncertain')),created_at INTEGER NOT NULL,published_at INTEGER);"
      +"CREATE INDEX IF NOT EXISTS play_night_posts_night ON play_night_posts(night_id,status,created_at);")
  }
  owner(session){
    const id=session?this.play.session(session)?.user_id:null
    if(!id)fail(401,'Connect WhatsApp to manage channel nights.')
    return id
  }
  requireClaim(userId,channelId){
    if(!/^\d{8,25}@newsletter$/.test(String(channelId||'')))fail(400,'Invalid channel.')
    const c=this.db.prepare("SELECT * FROM play_channel_claims WHERE user_id=? AND channel_id=? AND state='connected'").get(userId,channelId)
    if(!c)fail(403,'Verify this channel first in Your Channels.')
    return c
  }
  active(ownerId,channelId){
    return this.db.prepare("SELECT * FROM play_nights WHERE owner_id=? AND channel_id=? AND status='live' AND expires_at>? ORDER BY created_at DESC LIMIT 1").get(ownerId,channelId,this.now())
  }
  open(userId,channelId,duration){
    this.requireClaim(userId,channelId)
    this.play.limit('night-open:'+userId,6,3600000)
    const minutes=Number(duration)
    if(!Number.isInteger(minutes)||minutes<15||minutes>2880)fail(400,'Sessions must last between 15 minutes and 48 hours.')
    const id=randomUUID(),token=randomBytes(32).toString('base64url'),expires=this.now()+minutes*60000
    this.play.transaction(()=>{
      this.db.prepare("UPDATE play_nights SET status='ended' WHERE channel_id=? AND status='live'").run(channelId)
      this.db.prepare('INSERT INTO play_nights VALUES (?,?,?,?,?,?,?)').run(id,channelId,userId,hash(token),'live',this.now(),expires)
    })
    return {token,nightId:id,expiresAt:expires,channelId}
  }
  end(userId,channelId) {
    this.requireClaim(userId,channelId)
    const n=this.active(userId,channelId)
    if(!n)fail(404,'No active night.')
    this.db.prepare("UPDATE play_nights SET status='ended' WHERE id=?").run(n.id)
    return {ok:true}
  }
  overview(userId,channelId){
    this.requireClaim(userId,channelId)
    const night=this.active(userId,channelId)
    if(!night)return {night:null,posts:[],counts:{pending:0,published:0,rejected:0}}
    const posts=this.db.prepare('SELECT id,style,body,status,created_at,published_at FROM play_night_posts WHERE night_id=? ORDER BY created_at DESC LIMIT 100').all(night.id)
    const counts={pending:0,published:0,rejected:0}
    for(const p of this.db.prepare('SELECT status,COUNT(*) n FROM play_night_posts WHERE night_id=? GROUP BY status').all(night.id)){
      if(p.status in counts)counts[p.status]=p.n
    }
    return {night:{id:night.id,expiresAt:night.expires_at,createdAt:night.created_at},posts:posts.map(p=>({
      id:p.id,style:p.style,text:p.body,status:p.status,createdAt:p.created_at,publishedAt:p.published_at
    })),counts}
  }
  publicNight(token){
    if(!tokenPattern.test(String(token||'')))fail(404,'This night link is invalid.')
    const n=this.db.prepare("SELECT n.id,n.channel_id,n.expires_at,c.name FROM play_nights n JOIN play_channel_claims c ON c.channel_id=n.channel_id AND c.user_id=n.owner_id AND c.state='connected' WHERE n.token_hash=? AND n.status='live' AND n.expires_at>?").get(hash(token),this.now())
    if(!n)fail(410,'This session has ended or its link has expired.')
    return n
  }
  details(token){
    const n=this.publicNight(token)
    return {name:n.name,expiresAt:n.expires_at,maxLength:220,styles:[...safeStyle]}
  }
  submit(token,client,message,style) {
    const n=this.publicNight(token)
    this.play.limit('night-submit-global',120,60000)
    this.play.limit('night-submit:'+n.id+':'+plain(client),2,60000)
    this.play.limit('night-submit-session:'+n.id,40,60000)
    const body=plain(message)
    if(body.length<8||body.length>220)fail(400,'Use between 8 and 220 characters.')
    if(!safeStyle.has(style))fail(400,'Choose a valid card style.')
    if(/https?:\/\/|www\.|(?:[A-Z0-9._%+-]+)@(?:[A-Z0-9.-]+)\.[A-Z]{2,}|\+?\d[\d ()-]{7,}\d/iu.test(body))fail(400,'Remove links, phone numbers and email addresses.')
    const blocked=/\b(?:kill\s+yourself|nigger|faggot)\b/iu
    if(blocked.test(body))fail(400,'This message cannot be submitted.')
    const id=randomUUID()
    this.db.prepare("INSERT INTO play_night_posts VALUES (?,?,?,?,? ,?,NULL)").run(id,n.id,style,body,'pending',this.now())
    return {received:true,queued:true}
  }
  reject(userId,channelId,postId){
    const n=this.active(userId,channelId)
    if(!n)fail(404,'No active night.')
    const changed=this.db.prepare("UPDATE play_night_posts SET status='rejected' WHERE id=? AND night_id=? AND status='pending'").run(postId,n.id).changes
    if(!changed)fail(409,'This submission is no longer pending.')
    return {ok:true}
  }
  async publish(userId,channelId,postId,accounts){
    const claim=this.requireClaim(userId,channelId),night=this.active(userId,channelId)
    if(!night)fail(404,'No active night.')
    const post=this.db.prepare("SELECT * FROM play_night_posts WHERE id=? AND night_id=?").get(postId,night.id)
    if(!post||post.status!=='pending')fail(409,'Submission is not available to publish.')
    this.play.limit('night-publish:'+userId,15,60000)
    const bot=accounts.get(claim.account_id)
    if(!bot?.connected||!bot.sock?.newsletterMetadata||!bot.sock?.sendMessage)fail(503,'NIGHT channel bot is currently offline.')
    let meta
    try{meta=await bot.sock.newsletterMetadata('jid',channelId)}
    catch{fail(503,'Unable to verify NIGHT posting permissions.')}
    if(!isAdmin(meta))fail(403,'NIGHT has lost admin privileges. Restore them before publishing.')
    const png=graphic(post.body,post.style)
    // Claim delivery before network I/O; ambiguous sends never silently retry.
    const changed=this.db.prepare("UPDATE play_night_posts SET status='publishing' WHERE id=? AND night_id=? AND status='pending'").run(postId,night.id).changes
    if(!changed)fail(409,'Another publisher is processing this submission.')
    try{
      const sent=await bot.sock.sendMessage(channelId,{image:png,mimetype:'image/png',caption:'NIGHT Play · Anonymous Nights'})
      if(!sent)throw new Error('No message acknowledgement')
      this.db.prepare("UPDATE play_night_posts SET status='published',published_at=? WHERE id=? AND status='publishing'").run(this.now(),postId)
      return {published:true}
    }catch{
      this.db.prepare("UPDATE play_night_posts SET status='uncertain' WHERE id=? AND status='publishing'").run(postId)
      fail(502,'Publishing could not be confirmed. Check the WhatsApp Channel before retrying; NIGHT will not resend automatically.')
    }
  }
  async request(body,accounts){
    const action=String(body.action||'')
    if(action==='night:details')return this.details(body.token)
    if(action==='night:submit')return this.submit(body.token,body.client,body.message,body.style)
    const userId=this.owner(body.session),channelId=body.channelId
    if(action==='night:open')return this.open(userId,channelId,body.minutes)
    if(action==='night:end')return this.end(userId,channelId)
    if(action==='night:overview')return this.overview(userId,channelId)
    if(action==='night:reject')return this.reject(userId,channelId,body.postId)
    if(action==='night:publish')return this.publish(userId,channelId,body.postId,accounts)
    fail(400,'Unknown night action.')
  }
}
