import { createHash, randomUUID } from 'node:crypto'
import { ChannelError } from '../night-play-channels.js'
import { prepareApprovedChannelContent, invalidateChannelWatermarkCache } from './format.js'

// Backend-only candidates: the browser never supplies images or licensing evidence.
// Publishing stays disabled until a trusted source registers a licensed batch.
const JID = /^\d{8,25}@newsletter$/
const MODULES = ['memes', 'news', 'anonymous', 'ai']
const CATEGORIES = new Set(['relatable', 'wholesome', 'anime', 'nigerian', 'animals', 'relationships', 'dark'])
const INTERESTS = new Set(['casting', 'releases', 'trailers', 'renewals', 'romance', 'platforms'])
const MAX_IMAGE_BYTES = 192 * 1024
const fail = (status, message) => { throw new ChannelError(status, message) }
const plain = x => x !== null && typeof x === 'object' && !Array.isArray(x)
const isoTime = value => {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/.test(value)) fail(400, 'Choose a valid UTC publishing time.')
  const millis = Date.parse(value)
  if (!Number.isFinite(millis) || new Date(millis).toISOString() !== (value.endsWith('Z') && !value.includes('.')?value.slice(0,-1)+'.000Z':value)) fail(400, 'Choose a valid UTC publishing time.')
  return millis
}
const clone = x => JSON.parse(JSON.stringify(x))
const defaultConfig = () => ({
  revision: 0,
  modules: { memes: false, news: false, anonymous: false, ai: false },
  capabilities: { ai: false },
  memes: { categories: ['relatable'], mode: 'review', dailyLimit: 1, localTime: '18:00', timezone: 'UTC' },
  news: { interests: ['releases'], mode: 'review', spoilers: 'avoid', dailyLimit: 1, localTime: '18:00', timezone: 'UTC' },
  branding: { signatureEnabled:false, posterName:'', watermarkEnabled:false, watermarkOpacity:0.2, watermarkPosition:'bottom-right', captionReplacementsEnabled:false, captionReplacements:[] },
})
const text = (x, max) => typeof x === 'string' && x.length <= max
const clock = s => typeof s === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(s)
const timezone = s => {
  if (!text(s, 80) || !/^[A-Za-z0-9_+\-\/]{1,80}$/.test(s)) return false
  try { new Intl.DateTimeFormat('en', {timeZone:s}); return true } catch { return false }
}
function patchConfig(current, patch) {
  if (!plain(patch) || !Object.keys(patch).length || Object.keys(patch).some(k => !['modules','memes','news','branding'].includes(k))) fail(400,'Unknown channel setting.')
  const next = clone(current)
  for (const [section, values] of Object.entries(patch)) {
    if (!plain(values) || Object.keys(values).some(k => !Object.hasOwn(next[section],k))) fail(400, 'Unknown or malformed channel setting.')
    for (const [k,v] of Object.entries(values)) {
      if (section === 'modules' && (typeof v !== 'boolean' || !MODULES.includes(k))) fail(400,'Channel module must be on or off.')
      if (section === 'modules' && k === 'ai' && v) fail(403, 'Channel AI requires separate supreme-owner authorization.')
      if (section === 'modules' && v && ['news','anonymous'].includes(k)) fail(503,'This content module is not connected to approved publishing yet.')
      if (section === 'memes' && k === 'categories' && (!Array.isArray(v) || !v.length || v.length>7 || v.some(y=>!CATEGORIES.has(y)) || new Set(v).size!==v.length)) fail(400,'Choose supported meme categories.')
      if (section === 'news' && k === 'interests' && (!Array.isArray(v) || !v.length || v.length>6 || v.some(y=>!INTERESTS.has(y)) || new Set(v).size!==v.length)) fail(400,'Choose supported news interests.')
      if (['memes','news'].includes(section) && k === 'mode' && !['review','automatic'].includes(v)) fail(400,'Invalid publishing mode.')
      if (['memes','news'].includes(section) && k === 'mode' && v === 'automatic') fail(503,'Automatic posting is not active until the approved scheduler is connected.')
      if (['memes','news'].includes(section) && k === 'dailyLimit' && (!Number.isInteger(v) || v<1 || v>(section==='memes'?4:3))) fail(400,'Invalid daily post limit.')
      if (['memes','news'].includes(section) && k === 'localTime' && !clock(v)) fail(400,'Invalid local posting time.')
      if (['memes','news'].includes(section) && k === 'timezone' && !timezone(v)) fail(400,'Invalid timezone.')
      if (section === 'news' && k === 'spoilers' && !['avoid','warn'].includes(v)) fail(400,'Invalid spoiler option.')
      if (section === 'branding' && ['signatureEnabled','watermarkEnabled','captionReplacementsEnabled'].includes(k) && typeof v !== 'boolean') fail(400,'Invalid branding toggle.')
      if (section === 'branding' && k === 'posterName' && (!text(v,100) || /[\p{Cc}]/u.test(v))) fail(400,'Invalid posting person.')
      if (section === 'branding' && k === 'watermarkOpacity' && ![0.15,0.2,0.25,0.3].includes(v)) fail(400,'Invalid watermark opacity.')
      if (section === 'branding' && k === 'watermarkPosition' && !['top-left','top-right','bottom-left','bottom-right'].includes(v)) fail(400,'Invalid watermark position.')
      if (section === 'branding' && k === 'captionReplacements' && (!Array.isArray(v) || v.length>20 || v.some(a=>!plain(a) || !text(a.from,40) || !text(a.to,60) || !a.from))) fail(400,'Invalid caption substitutions.')
      next[section][k] = v
    }
  }
  if (next.branding.signatureEnabled && !next.branding.posterName.trim()) fail(400,'Name the person credited in the caption.')
  return next
}
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
const role = meta => String((meta?.viewer_metadata || meta?.viewerMetadata || {}).role || '').toUpperCase()
const admin = meta => ['ADMIN','OWNER','SUPERADMIN','SUPER_ADMIN','CREATOR'].includes(role(meta))
const exactName = meta => {
  const value = (typeof meta?.name==='string' ? meta.name : meta?.name?.text) ?? meta?.thread_metadata?.name?.text ?? meta?.subject
  if (typeof value !== 'string' || !value.trim() || Buffer.byteLength(value,'utf8')>512 || /\p{Cc}/u.test(value)) fail(502,'WhatsApp returned an invalid channel display name.')
  return value // Exact verified Unicode, including ZWJ, RTL and colour-emoji sequences.
}
function assertImage(item) {
  const data=item.image
  if (!Buffer.isBuffer(data) || !data.length || data.length>MAX_IMAGE_BYTES) fail(400,'Curated images must be safe bounded buffers.')
  const png=data.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))
  const jpg=data.length>=3 && data[0]===255 && data[1]===216 && data[2]===255
  const webp=data.length>=12 && data.toString('ascii',0,4)==='RIFF' && data.toString('ascii',8,12)==='WEBP'
  const detected=png?'image/png':jpg?'image/jpeg':webp?'image/webp':''
  if (!detected || detected!==item.mime) fail(400,'Unsupported curated image type.')
}

export class ChannelPublisher {
  constructor(channels, {now=Date.now} = {}) {
    this.channels=channels; this.play=channels.play; this.db=channels.db; this.now=now
    this.db.exec(
      'CREATE TABLE IF NOT EXISTS play_channel_config (user_id TEXT NOT NULL, channel_id TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0, payload TEXT NOT NULL, PRIMARY KEY(user_id,channel_id), FOREIGN KEY(user_id,channel_id) REFERENCES play_channel_claims(user_id,channel_id) ON DELETE CASCADE);'+
      'CREATE TABLE IF NOT EXISTS play_channel_batches (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, channel_id TEXT NOT NULL, created_at INTEGER NOT NULL, FOREIGN KEY(user_id,channel_id) REFERENCES play_channel_claims(user_id,channel_id) ON DELETE CASCADE);'+
      'CREATE TABLE IF NOT EXISTS play_channel_candidates (id TEXT PRIMARY KEY, batch_id TEXT NOT NULL REFERENCES play_channel_batches(id) ON DELETE CASCADE, user_id TEXT NOT NULL, channel_id TEXT NOT NULL, caption TEXT NOT NULL, title TEXT NOT NULL, category TEXT NOT NULL, source_name TEXT NOT NULL, source_url TEXT NOT NULL, image BLOB NOT NULL, mime TEXT NOT NULL, sha256 TEXT NOT NULL, license_id TEXT NOT NULL, rights_verified INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(user_id,channel_id) REFERENCES play_channel_claims(user_id,channel_id) ON DELETE CASCADE);'+
      'CREATE TABLE IF NOT EXISTS play_channel_reviews (candidate_id TEXT PRIMARY KEY REFERENCES play_channel_candidates(id) ON DELETE CASCADE, user_id TEXT NOT NULL, channel_id TEXT NOT NULL, decision TEXT NOT NULL CHECK(decision IN (\'approved\',\'declined\',\'skipped\')), caption TEXT NOT NULL, updated_at INTEGER NOT NULL);'+
      'CREATE TABLE IF NOT EXISTS play_channel_jobs (id TEXT PRIMARY KEY, user_id TEXT NOT NULL, channel_id TEXT NOT NULL, candidate_id TEXT NOT NULL UNIQUE REFERENCES play_channel_candidates(id) ON DELETE CASCADE, caption TEXT NOT NULL, scheduled_at INTEGER NOT NULL, created_at INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN (\'queued\',\'sending\',\'sent\',\'cancelled\',\'blocked\',\'needs_review\')), sent_at INTEGER, message_id TEXT, last_error TEXT, branding_json TEXT NOT NULL,channel_name TEXT NOT NULL,FOREIGN KEY(user_id,channel_id) REFERENCES play_channel_claims(user_id,channel_id) ON DELETE CASCADE);'+
      'CREATE INDEX IF NOT EXISTS play_channel_jobs_due ON play_channel_jobs(state,scheduled_at);'+
      'CREATE TABLE IF NOT EXISTS play_channel_selection (user_id TEXT NOT NULL,channel_id TEXT NOT NULL,id TEXT NOT NULL,revision INTEGER NOT NULL,PRIMARY KEY(user_id,channel_id),FOREIGN KEY(user_id,channel_id) REFERENCES play_channel_claims(user_id,channel_id) ON DELETE CASCADE);'
    )
  }
  invalidateChannel(channelId) { invalidateChannelWatermarkCache(channelId) }
  cleanup() {
    // Bound historical images and never retain an approved tray indefinitely.
    // No queued/sending post can be removed by this maintenance path.
    const day=86400000
    this.db.prepare("DELETE FROM play_channel_jobs WHERE state IN ('sent','cancelled','blocked','needs_review') AND created_at<?").run(this.now()-90*day)
    const expiring=this.db.prepare("SELECT DISTINCT user_id,channel_id FROM play_channel_batches b WHERE b.created_at<? AND NOT EXISTS (SELECT 1 FROM play_channel_jobs j JOIN play_channel_candidates c ON c.id=j.candidate_id WHERE c.batch_id=b.id)").all(this.now()-14*day)
    this.play.transaction(()=>{
      for (const {user_id,channel_id} of expiring)
        this.db.prepare('UPDATE play_channel_selection SET revision=revision+1 WHERE user_id=? AND channel_id=?').run(user_id,channel_id)
      this.db.prepare("DELETE FROM play_channel_batches WHERE created_at<? AND NOT EXISTS (SELECT 1 FROM play_channel_jobs j JOIN play_channel_candidates c ON c.id=j.candidate_id WHERE c.batch_id=play_channel_batches.id)").run(this.now()-14*day)
    })
  }
  claim(userId,channelId) {
    if (!userId) fail(401,'Connect WhatsApp to continue.')
    if (!JID.test(String(channelId))) fail(400,'Invalid WhatsApp Channel.')
    const row=this.db.prepare('SELECT * FROM play_channel_claims WHERE user_id=? AND channel_id=? AND state=\'connected\'').get(userId,channelId)
    if (!row) fail(404,'This verified channel is not connected to your account.')
    return row
  }
  config(userId,channelId) {
    const channel=this.claim(userId,channelId)
    let row=this.db.prepare('SELECT * FROM play_channel_config WHERE user_id=? AND channel_id=?').get(userId,channelId)
    if(!row) {
      const first=defaultConfig()
      this.db.prepare('INSERT INTO play_channel_config VALUES (?,?,?,?) ON CONFLICT(user_id,channel_id) DO NOTHING').run(userId,channelId,0,JSON.stringify(first))
      row=this.db.prepare('SELECT * FROM play_channel_config WHERE user_id=? AND channel_id=?').get(userId,channelId)
    }
    return {channel:{id:channelId,name:channel.name,status:channel.state},...JSON.parse(row.payload),revision:row.revision}
  }
  async permission(userId,channelId,accounts) {
    const claim=this.claim(userId,channelId)
    const account=accounts?.get(claim.account_id)
    if (!account?.connected || typeof account.sock?.newsletterMetadata!=='function') fail(503,'The verified WhatsApp posting account is offline.')
    let meta
    try { meta=await account.sock.newsletterMetadata('jid',channelId) }
    catch { fail(503,'Unable to recheck current WhatsApp Channel permissions.') }
    if (!meta || String(meta.id||meta.jid)!==channelId || !admin(meta)) fail(403,'This NIGHT account is no longer a channel admin.')
    const name=exactName(meta)
    if (name!==claim.name) {
      this.db.prepare('UPDATE play_channel_claims SET name=? WHERE user_id=? AND channel_id=?').run(name,userId,channelId)
      invalidateChannelWatermarkCache(channelId)
    }
    return {account,name}
  }
  async update(userId,channelId,revision,patch,accounts) {
    // Emergency shutdown is permitted even when WhatsApp is offline or admin role
    // was revoked; it can only make server-side features less permissive.
    this.claim(userId,channelId)
    const emergencyOff=plain(patch)&&Object.keys(patch).length===1&&plain(patch.modules)&&
      Object.keys(patch.modules).length>0&&Object.entries(patch.modules).every(([k,v])=>MODULES.includes(k)&&v===false)
    if(!emergencyOff) await this.permission(userId,channelId,accounts)
    if (!Number.isInteger(revision) || revision<0) fail(400,'Invalid settings revision.')
    // Optimistic atomic revision guard, prevents two devices overwriting each other.
    const prev=this.config(userId,channelId)
    if (prev.revision!==revision) fail(409,'Channel settings changed on another device. Refresh first.')
    const next=patchConfig(prev,patch)
    delete next.channel
    next.revision=revision+1
    const changed=this.db.prepare('UPDATE play_channel_config SET revision=?,payload=? WHERE user_id=? AND channel_id=? AND revision=?').run(next.revision,JSON.stringify(next),userId,channelId,revision)
    if(!changed.changes) fail(409,'Channel settings changed. Refresh first.')
    // Revocation invalidates pending jobs, including jobs queued by a second device.
    if (patch.modules?.memes===false || patch.branding) this.db.prepare("UPDATE play_channel_jobs SET state='cancelled',last_error='Publishing settings changed; fresh approval required' WHERE user_id=? AND channel_id=? AND state='queued'").run(userId,channelId)
    if(patch.branding) invalidateChannelWatermarkCache(channelId)
    return this.config(userId,channelId)
  }
  selection(userId,channelId) {
    this.claim(userId,channelId)
    this.db.prepare('INSERT INTO play_channel_selection VALUES (?,?,?,0) ON CONFLICT(user_id,channel_id) DO NOTHING').run(userId,channelId,randomUUID().replaceAll('-','_'))
    const row=this.db.prepare('SELECT * FROM play_channel_selection WHERE user_id=? AND channel_id=?').get(userId,channelId)
    const cs=this.db.prepare("SELECT c.*,r.caption AS reviewed_caption FROM play_channel_reviews r JOIN play_channel_candidates c ON c.id=r.candidate_id LEFT JOIN play_channel_jobs j ON j.candidate_id=c.id WHERE r.user_id=? AND r.channel_id=? AND r.decision='approved' AND j.id IS NULL ORDER BY r.updated_at,c.rowid LIMIT 11").all(userId,channelId)
    if(cs.length>10) fail(409,'Approved tray exceeded its safe capacity.')
    return {id:row.id,revision:row.revision,items:cs.map(c=>({
      batchId:c.batch_id,candidateId:c.id,caption:c.reviewed_caption,title:c.title,
      publishable:c.rights_verified===1,rightsStatus:c.rights_verified===1?'verified':'unverified',reviewOnly:c.rights_verified!==1,
      previewData:'data:'+c.mime+';base64,'+Buffer.from(c.image).toString('base64')
    }))}
  }
  selectionCheck(userId,channelId,revision,selectionId=null) {
    const selected=this.selection(userId,channelId)
    if(!Number.isInteger(revision) || selected.revision!==revision || (selectionId!==null && selected.id!==selectionId))
      fail(409,'Approved selection changed on another device. Refresh first.')
    return selected
  }
  // Trusted internal source only, NEVER exposed through signed website RPC.
  registerBatch(userId,channelId,items) {
    this.claim(userId,channelId)
    if (!Array.isArray(items) || items.length!==4) fail(400,'A batch requires four actual sourced images.')
    const fresh=this.db.prepare('SELECT COUNT(*) AS n FROM play_channel_batches WHERE user_id=? AND channel_id=? AND created_at>?').get(userId,channelId,this.now()-86400000)
    if(fresh.n>=24) fail(429,'This channel has reached the daily content discovery budget.')
    for(const item of items) {
      assertImage(item)
      if (!text(item.caption,1800) || !text(item.title,160) || !text(item.category,48) || !text(item.sourceName,100) || !text(item.sourceUrl,500) || !/^https:\/\//.test(item.sourceUrl)) fail(400,'Invalid candidate or provenance.')
    }
    const batchId=randomUUID().replaceAll('-','_')
    this.play.transaction(()=>{
      this.db.prepare('INSERT INTO play_channel_batches VALUES (?,?,?,?)').run(batchId,userId,channelId,this.now())
      for (const item of items) {
        const rights=item.rightsEvidence
        const cleared=rights?.verifiedByServer===true && rights?.status==='cleared' && text(rights?.licenseId,140) && !!rights.licenseId && rights.sha256===digest(item.image) && rights.sourceUrl===item.sourceUrl
        this.db.prepare('INSERT INTO play_channel_candidates VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)').run(randomUUID().replaceAll('-','_'),batchId,userId,channelId,item.caption,item.title,item.category,item.sourceName,item.sourceUrl,item.image,item.mime,digest(item.image),cleared?rights.licenseId:'',cleared?1:0)
      }
    })
    return batchId
  }
  batch(userId,channelId,id) {
    this.claim(userId,channelId)
    const batch=this.db.prepare('SELECT id FROM play_channel_batches WHERE id=? AND user_id=? AND channel_id=?').get(id,userId,channelId)
    if(!batch) fail(404,'No review batch belongs to this channel.')
    const candidates=this.db.prepare('SELECT * FROM play_channel_candidates WHERE batch_id=? AND user_id=? AND channel_id=?').all(id,userId,channelId)
    return {batchId:id,selection:this.selection(userId,channelId),candidates:candidates.map(c=>({
      id:c.id,caption:c.caption,title:c.title,category:c.category,sourceName:c.source_name,sourceUrl:c.source_url,
      previewData:'data:'+c.mime+';base64,'+Buffer.from(c.image).toString('base64'),
      rightsStatus:c.rights_verified===1?'verified':'unverified', publishable:c.rights_verified===1, reviewOnly:c.rights_verified!==1,
      ...(c.rights_verified!==1?{publishBlockedReason:'rights_unverified'}:{})
    })),decisions:this.db.prepare('SELECT r.candidate_id AS candidateId,r.decision,r.caption FROM play_channel_reviews r JOIN play_channel_candidates c ON c.id=r.candidate_id WHERE c.batch_id=? AND r.user_id=? AND r.channel_id=?').all(id,userId,channelId)}
  }
  current(userId,channelId) {
    this.claim(userId,channelId)
    const r=this.db.prepare('SELECT id FROM play_channel_batches WHERE user_id=? AND channel_id=? ORDER BY created_at DESC LIMIT 1').get(userId,channelId)
    if(!r) return {batchId:null,candidates:[],decisions:[],selection:this.selection(userId,channelId)}
    return this.batch(userId,channelId,r.id)
  }
  review(userId,channelId,{batchId,candidateId,decision,caption,expectedSelectionRevision},accounts) {
    this.claim(userId,channelId)
    if(!['approved','declined','skipped'].includes(decision)||!text(caption,1800)) fail(400,'Invalid review decision.')
    const c=this.db.prepare('SELECT * FROM play_channel_candidates WHERE id=? AND batch_id=? AND user_id=? AND channel_id=?').get(candidateId,batchId,userId,channelId)
    if(!c) fail(404,'This review candidate is unavailable.')
    if(decision==='approved' && (!c.rights_verified || !c.license_id || digest(c.image)!==c.sha256)) fail(403,'Media reuse rights are not verified. Cannot approve.')
    const cfg=this.config(userId,channelId)
    if(!cfg.modules.memes) fail(403,'Enable channel memes before reviewing for publication.')
    this.play.transaction(()=>{
      const selected=this.selectionCheck(userId,channelId,expectedSelectionRevision)
      if(decision==='approved' && selected.items.length>=10 && !selected.items.some(i=>i.candidateId===candidateId))fail(409,'Approved tray is full. Publish or remove a post first.')
      if(this.db.prepare('SELECT 1 FROM play_channel_jobs WHERE candidate_id=?').get(candidateId)) fail(409,'This post was already queued. A review cannot rewrite the sent snapshot.')
      this.db.prepare('INSERT INTO play_channel_reviews VALUES (?,?,?,?,?,?) ON CONFLICT(candidate_id) DO UPDATE SET decision=excluded.decision,caption=excluded.caption,updated_at=excluded.updated_at').run(candidateId,userId,channelId,decision,caption,this.now())
      this.db.prepare('UPDATE play_channel_selection SET revision=revision+1 WHERE user_id=? AND channel_id=? AND revision=?').run(userId,channelId,selected.revision)
    })
    return {candidateId,decision,caption,selection:this.selection(userId,channelId)}
  }
  enqueue(userId,channelId,{selectionId,expectedSelectionRevision,mode,scheduledAt,spacingMinutes},accounts) {
    const claim=this.claim(userId,channelId)
    const cfg=this.config(userId,channelId)
    if(!cfg.modules.memes) fail(403,'This channel has memes turned off.')
    if(!['now','schedule'].includes(mode)) fail(400,'Invalid publishing mode.')
    if (mode==='schedule' && (!Number.isInteger(spacingMinutes)||spacingMinutes<1||spacingMinutes>1440)) fail(400,'Invalid post spacing.')
    const first=mode==='now'?this.now():isoTime(scheduledAt)
    if(mode==='schedule' && (first<this.now()+60000||first>this.now()+30*86400000)) fail(400,'Choose a time within the next 30 days.')
    const jobs=[]
    this.play.transaction(()=>{
      const selected=this.selectionCheck(userId,channelId,expectedSelectionRevision,selectionId)
      if(!selected.items.length) fail(400,'Approve at least one licensed meme first.')
      selected.items.forEach((item,i)=>{
        const c=this.db.prepare('SELECT * FROM play_channel_candidates WHERE id=? AND batch_id=? AND user_id=? AND channel_id=?').get(item.candidateId,item.batchId,userId,channelId)
        if(!c||!c.rights_verified||!c.license_id||digest(c.image)!==c.sha256) fail(403,'Cannot queue unlicensed or changed media.')
        const existing=this.db.prepare('SELECT id FROM play_channel_jobs WHERE candidate_id=?').get(c.id)
        if(existing)fail(409,'A selected post is already queued. Reload before trying again.')
        const id=randomUUID(),due=first+i*(mode==='schedule'?spacingMinutes:1)*60000
        this.db.prepare("INSERT INTO play_channel_jobs (id,user_id,channel_id,candidate_id,caption,scheduled_at,created_at,state,branding_json,channel_name) VALUES (?,?,?,?,?,?,?,'queued',?,?)").run(id,userId,channelId,c.id,item.caption,due,this.now(),JSON.stringify(cfg.branding),claim.name)
        jobs.push({id,status:'queued',scheduledAt:new Date(due).toISOString(),title:c.title})
      })
      this.db.prepare('UPDATE play_channel_selection SET revision=revision+1 WHERE user_id=? AND channel_id=?').run(userId,channelId)
    })
    return {jobs,selection:this.selection(userId,channelId)}
  }
  jobs(userId,channelId) {
    this.claim(userId,channelId)
    return {jobs:this.db.prepare('SELECT id,state AS status,scheduled_at AS scheduledAt,sent_at AS sentAt,last_error AS error FROM play_channel_jobs WHERE user_id=? AND channel_id=? ORDER BY created_at DESC LIMIT 60').all(userId,channelId).map(j=>({...j,scheduledAt:new Date(j.scheduledAt).toISOString(),sentAt:j.sentAt?new Date(j.sentAt).toISOString():null}))}
  }
  cancel(userId,channelId,jobId) {
    this.claim(userId,channelId)
    if(typeof jobId!=='string') fail(400,'Invalid job.')
    const changed=this.db.prepare("UPDATE play_channel_jobs SET state='cancelled' WHERE id=? AND user_id=? AND channel_id=? AND state='queued'").run(jobId,userId,channelId)
    if(!changed.changes) fail(409,'This pending channel post cannot be cancelled.')
    return {cancelled:true}
  }
  async preview(userId,channelId,{batchId,candidateId,caption},accounts) {
    if(!text(caption,1800))fail(400,'Invalid caption preview.')
    const cfg=this.config(userId,channelId)
    if(!cfg.modules.memes)fail(403,'Enable memes to preview this post.')
    const c=this.db.prepare('SELECT * FROM play_channel_candidates WHERE id=? AND batch_id=? AND user_id=? AND channel_id=?').get(candidateId,batchId,userId,channelId)
    if(!c)fail(404,'This post does not belong to your verified channel.')
    const {name}=await this.permission(userId,channelId,accounts)
    const formatted=await prepareApprovedChannelContent({
      text:caption,mediaBuffer:Buffer.from(c.image),channelId,verifiedDisplayName:name,
      branding:cfg.branding,sourceKind:'automation'
    })
    const image='data:'+c.mime+';base64,'+formatted.mediaBuffer.toString('base64')
    if(image.length>1_750_000)fail(413,'Image is too large for a safe on-screen preview.')
    return {previewData:image,formattedCaption:formatted.caption}
  }
  async handle(action,body,userId,accounts) {
    const id=body.channelId
    if (action==='channel:config:get') return this.config(userId,id)
    if (action==='channel:config:update') return this.update(userId,id,body.expectedRevision,body.patch,accounts)
    if (action==='channel:memes:current') return this.current(userId,id)
    if (action==='channel:memes:find') {
      // B's licensed ingestion must register genuine candidates first. Never invent posts.
      const current=this.current(userId,id)
      if(current.candidates.length===4 && body.next!==true) return current
      fail(503,'NIGHT has no rights-cleared meme batch available yet. No content was published.')
    }
    if (action==='channel:memes:review') { await this.permission(userId,id,accounts); return this.review(userId,id,body,accounts) }
    if (action==='channel:memes:publish') { await this.permission(userId,id,accounts); return this.enqueue(userId,id,body,accounts) }
    if (action==='channel:post:preview') return this.preview(userId,id,body,accounts)
    if (action==='channel:jobs:list') return this.jobs(userId,id)
    if (action==='channel:jobs:cancel') return this.cancel(userId,id,body.jobId)
    fail(503,'This channel operation is not ready for publication.')
  }
  // Explicit worker; caller must wire a bounded scheduler before auto sending.
  // A stale 'sending' job remains unresolved: NEVER blindly retry an ambiguous send.
  async drain(accounts,{limit=2,prepare=prepareApprovedChannelContent,allowedChannelIds=[]}={}) {
    let sent=0
    const due=this.db.prepare("SELECT * FROM play_channel_jobs WHERE state='queued' AND scheduled_at<=? ORDER BY scheduled_at LIMIT ?").all(this.now(),Math.min(2,Math.max(1,limit)))
    for(const j of due){
      const guard=this.db.prepare("UPDATE play_channel_jobs SET state='sending' WHERE id=? AND state='queued'").run(j.id)
      if(!guard.changes) continue
      try{
        if(!Array.isArray(allowedChannelIds)||!allowedChannelIds.includes(j.channel_id)) fail(403,'Channel not enabled for approved publishing smoke.')
        const {account,name}=await this.permission(j.user_id,j.channel_id,accounts)
        if(name!==j.channel_name) fail(409,'Verified channel name changed after approval. Fresh review required.')
        const cfg=this.config(j.user_id,j.channel_id)
        const c=this.db.prepare("SELECT * FROM play_channel_candidates WHERE id=? AND user_id=? AND channel_id=?").get(j.candidate_id,j.user_id,j.channel_id)
        const r=this.db.prepare("SELECT * FROM play_channel_reviews WHERE candidate_id=? AND user_id=? AND channel_id=?").get(j.candidate_id,j.user_id,j.channel_id)
        if (!cfg.modules.memes||!c||!r||r.decision!=='approved'||r.caption!==j.caption||!c.rights_verified||!c.license_id||digest(c.image)!==c.sha256) fail(403,'Module, approval, or licensed media was revoked.')
        if(typeof prepare!=='function') fail(503,'Verified watermark and caption renderer is not connected.')
        // Recheck once immediately before send; all sends remain fail-closed by default.
        const formatted=await prepare({mediaBuffer:Buffer.from(c.image),text:j.caption,branding:JSON.parse(j.branding_json),verifiedDisplayName:name,channelId:j.channel_id,sourceKind:'automation'})
        if(!Buffer.isBuffer(formatted?.mediaBuffer) || typeof formatted.caption!=='string') fail(503,'Unable to validate the final post.')
        const finalGuard=await this.permission(j.user_id,j.channel_id,accounts)
        if(finalGuard.name!==j.channel_name)fail(403,'Verified channel name changed before sending.')
        if(!this.config(j.user_id,j.channel_id).modules.memes) fail(403,'Module disabled before sending.')
        const finalReview=this.db.prepare("SELECT decision,caption FROM play_channel_reviews WHERE candidate_id=? AND user_id=? AND channel_id=?").get(j.candidate_id,j.user_id,j.channel_id)
        if(finalReview?.decision!=='approved'||finalReview.caption!==j.caption)fail(403,'Approval was revoked before sending.')
        const finalCandidate=this.db.prepare("SELECT image,sha256,rights_verified,license_id FROM play_channel_candidates WHERE id=? AND user_id=? AND channel_id=?").get(j.candidate_id,j.user_id,j.channel_id)
        if(!finalCandidate?.rights_verified||!finalCandidate.license_id||digest(finalCandidate.image)!==finalCandidate.sha256)fail(403,'Content rights changed before sending.')
        if(typeof account.sock?.sendMessage!=='function') fail(503,'Newsletter media publishing is unsupported.')
        const result=await account.sock.sendMessage(j.channel_id,{image:formatted.mediaBuffer,caption:formatted.caption})
        if(!result?.key?.id) fail(502,'WhatsApp did not confirm the sent message; operator review required.')
        this.db.prepare("UPDATE play_channel_jobs SET state='sent',sent_at=?,message_id=?,last_error=NULL WHERE id=? AND state='sending'").run(this.now(),result.key.id,j.id)
        sent++
      }catch(error){
        // At-most-once automatic attempts: unknown outcomes never blindly resubmit.
        this.db.prepare("UPDATE play_channel_jobs SET state='needs_review',last_error=? WHERE id=? AND state='sending'").run(error instanceof ChannelError?error.message:'Delivery uncertain; operator review required.',j.id)
      }
    }
    return {sent,attempted:due.length}
  }
}
