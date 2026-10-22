import { createHash, randomBytes, randomUUID, verify, createPublicKey } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { NightPlayMatches } from './night-play-matches.js'
import { NightPlayParty, PartyRoomError } from './night-play-party.js'
import { NightPlayChannels, ChannelError } from './night-play-channels.js'

export const PLAY_URL = 'https://night-play-combined-preview.vercel.app'
export const PLAY_RPC_PATH = '/api/night-play/rpc'
const FIVE_MINUTES = 5 * 60_000
const SESSION_AGE = 30 * 86400_000
const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const hash = value => createHash('sha256').update(String(value)).digest('hex')
const cleanName = value => String(value || '').replace(/[\p{Cc}\p{Cf}<>]/gu, '').trim().slice(0, 28) || 'Player'
const identityPattern = /^(?:[1-9]\d{6,14}|jid:\d+@(?:lid|hosted\.lid))$/
export class PlayError extends Error {
  constructor(status, message) { super(message); this.status = status }
}
function requireSession(value) {
  if (!/^[A-Za-z0-9_-]{43}$/.test(String(value || ''))) throw new PlayError(401, 'Connect WhatsApp to continue.')
  return hash(value)
}
export function signatureMessage(timestamp, nonce, body) {
  return ['night-play-v1', timestamp, nonce, 'POST', PLAY_RPC_PATH, hash(body)].join('\n')
}

// The existing on-disk MSCC SQLite connection owns these tables. No WhatsApp
// credentials or raw browser session tokens are copied into NIGHT Play.
export class NightPlay {
  constructor(db, { now = Date.now } = {}) {
    this.db = db
    this.now = now
    db.exec(`
      CREATE TABLE IF NOT EXISTS play_users (id TEXT PRIMARY KEY, display_name TEXT NOT NULL, created_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS play_identities (identity TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES play_users(id) ON DELETE CASCADE);
      CREATE TABLE IF NOT EXISTS play_avatars (user_id TEXT PRIMARY KEY REFERENCES play_users(id) ON DELETE CASCADE, mime TEXT NOT NULL, image BLOB NOT NULL, updated_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS play_sessions (id TEXT PRIMARY KEY, user_id TEXT REFERENCES play_users(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS play_challenges (code_hash TEXT PRIMARY KEY, session_id TEXT NOT NULL UNIQUE REFERENCES play_sessions(id) ON DELETE CASCADE, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS play_limits (id TEXT PRIMARY KEY, count INTEGER NOT NULL, expires_at INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS play_nonces (id TEXT PRIMARY KEY, expires_at INTEGER NOT NULL);
    `)
    this.nextCleanup = 0
    this.matches = new NightPlayMatches(db, { now })
    this.partyRooms = new NightPlayParty(db, { now })
    this.channels = new NightPlayChannels(this, { now })
  }
  transaction(fn) {
    this.db.exec('BEGIN IMMEDIATE')
    try { const result = fn(); this.db.exec('COMMIT'); return result }
    catch (error) { this.db.exec('ROLLBACK'); throw error }
  }
  cleanup() {
    if (this.now() < this.nextCleanup) return
    for (const table of ['play_challenges', 'play_sessions', 'play_limits', 'play_nonces']) {
      this.db.prepare(`DELETE FROM ${table} WHERE expires_at <= ?`).run(this.now())
    }
    this.db.prepare('DELETE FROM play_channel_consent WHERE expires_at<=?').run(this.now())
    this.db.prepare("DELETE FROM play_channel_claims WHERE state!='connected' AND expires_at<=?").run(this.now())
    this.channels.publisher.cleanup()
    this.partyRooms.cleanup()
    this.nextCleanup = this.now() + 60_000
  }
  limit(key, maximum, window = 60_000) {
    const id = hash(key)
    this.db.prepare(`INSERT INTO play_limits VALUES (?,1,?) ON CONFLICT(id) DO UPDATE SET
      count=CASE WHEN expires_at<=? THEN 1 ELSE count+1 END,
      expires_at=CASE WHEN expires_at<=? THEN excluded.expires_at ELSE expires_at END`).run(id, this.now() + window, this.now(), this.now())
    if (this.db.prepare('SELECT count FROM play_limits WHERE id=?').get(id).count > maximum) {
      throw new PlayError(429, 'Too many attempts. Please wait a minute and try again.')
    }
  }
  session(token) {
    const id = requireSession(token)
    return this.db.prepare('SELECT * FROM play_sessions WHERE id=? AND expires_at>?').get(id, this.now())
  }
  profile(id) {
    const row = this.db.prepare('SELECT id, display_name FROM play_users WHERE id=?').get(id)
    if (!row) return null
    const image = this.db.prepare('SELECT mime, image FROM play_avatars WHERE user_id=?').get(id)
    return { id:row.id, displayName:row.display_name, ...(image ? { avatarData:'data:' + image.mime + ';base64,' + Buffer.from(image.image).toString('base64') } : {}) }
  }
  avatarCandidates(id) {
    // Derived only from verified bot-side identities. Never sent to the browser.
    return [...new Set(this.db.prepare('SELECT identity FROM play_identities WHERE user_id=?').all(id)
      .flatMap(({ identity }) => /^\d{7,15}$/.test(identity) ? [identity + '@s.whatsapp.net'] :
        /^jid:\d+@(?:lid|hosted\.lid)$/.test(identity) ? [identity.slice(4)] : []))]
  }
  saveAvatar(id, data) {
    if (!Buffer.isBuffer(data) || !data.length || data.length > 196608) return false
    const jpeg = data.length > 3 && data[0] === 0xff && data[1] === 0xd8 && data[2] === 0xff
    const png = data.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))
    if (!jpeg && !png) return false
    if (!this.db.prepare('SELECT 1 FROM play_users WHERE id=?').get(id)) return false
    this.db.prepare('INSERT INTO play_avatars VALUES (?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET mime=excluded.mime, image=excluded.image, updated_at=excluded.updated_at')
      .run(id, jpeg ? 'image/jpeg' : 'image/png', data, this.now())
    return true
  }
  request({ action, session, client, displayName, matchId, code, cell, channelId, mode, questions, guestName, guestSession, answer }) {
    this.cleanup()
    if (action === 'health') return { ok:true, service:'night-play', version:1 }
    if (action === 'status') {
      const row = session ? this.session(session) : null
      const profile = row?.user_id ? this.profile(row.user_id) : null
      const pending = row && this.db.prepare('SELECT expires_at FROM play_challenges WHERE session_id=? AND expires_at>?').get(row.id, this.now())
      return { linked:Boolean(profile), profile, pendingUntil:pending?.expires_at || null }
    }
    if (action === 'link') {
      const id = requireSession(session)
      this.limit('link-global', 120)
      this.limit('link-client:' + client, 10, 10 * 60_000)
      this.limit('link-session:' + id, 5, 10 * 60_000)
      const existing = this.session(session)
      if (existing?.user_id) throw new PlayError(409, 'This browser is already connected.')
      return this.transaction(() => {
        this.db.prepare('INSERT INTO play_sessions VALUES (?,NULL,?) ON CONFLICT(id) DO UPDATE SET user_id=NULL, expires_at=excluded.expires_at').run(id, this.now() + SESSION_AGE)
        this.db.prepare('DELETE FROM play_challenges WHERE session_id=?').run(id)
        const code = [...randomBytes(12)].map(byte => alphabet[byte & 31]).join('')
        const expiresAt = this.now() + FIVE_MINUTES
        this.db.prepare('INSERT INTO play_challenges VALUES (?,?,?)').run(hash(code), id, expiresAt)
        const formatted = code.match(/.{4}/g).join('-')
        return { code:formatted, command:'.minigames link ' + formatted, expiresAt }
      })
    }
    const row = session ? this.session(session) : null
    if (action === 'cancel') {
      if (row && !row.user_id) this.db.prepare('DELETE FROM play_sessions WHERE id=?').run(row.id)
      return { ok:true, linked:Boolean(row?.user_id) }
    }
    if (action === 'logout') {
      if (row) this.db.prepare('DELETE FROM play_sessions WHERE id=?').run(row.id)
      return { ok:true }
    }
    if (typeof action === 'string' && action.startsWith('party:')) {
      this.limit((action==='party:state'?'party-poll-client:':'party-client:') + String(client || 'unknown'), action==='party:state'?900:160)
      return this.partyRooms.request(action, {userId:row?.user_id,guestSession,code,mode,questions,guestName,answer})
    }
    if (!row?.user_id) throw new PlayError(401, 'Connect WhatsApp to continue.')
    if (action === 'channel:list') {
      this.limit('channel-list:' + row.user_id, 30)
      return this.channels.list(row.user_id)
    }
    if (action === 'channel:remove') {
      this.limit('channel-remove:' + row.user_id, 10, 3600000)
      return this.channels.remove(row.user_id, channelId)
    }
    if (typeof action === 'string' && action.startsWith('match:')) {
      this.limit('match-client:' + String(client || 'unknown'), 120)
      return this.matches.request(action, row.user_id, {matchId,code,cell})
    }
    if (action === 'profile') {
      this.db.prepare('UPDATE play_users SET display_name=? WHERE id=?').run(cleanName(displayName), row.user_id)
      return { linked:true, profile:this.profile(row.user_id) }
    }
    if (action === 'delete') {
      this.matches.removeUser(row.user_id)
      this.partyRooms.removeUser(row.user_id)
      this.channels.removeUser(row.user_id)
      this.db.prepare('DELETE FROM play_avatars WHERE user_id=?').run(row.user_id)
      this.db.prepare('DELETE FROM play_users WHERE id=?').run(row.user_id)
      return { ok:true }
    }
    throw new PlayError(400, 'Unknown account action.')
  }
  identityKeys({ userKey, identityKeys = [] }) {
    if (!identityPattern.test(userKey || '')) throw new PlayError(400, 'Your WhatsApp identity is not verified yet. Please retry.')
    return [...new Set([userKey, ...identityKeys].filter(key => identityPattern.test(key)))]
  }
  linkedProfile(identity) {
    const keys = this.identityKeys(identity)
    const ids = [...new Set(keys.flatMap(key => {
      const row = this.db.prepare('SELECT user_id FROM play_identities WHERE identity=?').get(key)
      return row ? [row.user_id] : []
    }))]
    if (ids.length > 1) throw new PlayError(409, 'Conflicting WhatsApp identities. Disconnect the old NIGHT Play account first.')
    return ids[0] ? this.profile(ids[0]) : null
  }
  linkFromWhatsApp({ code, displayName, ...identity }) {
    const keys = this.identityKeys(identity)
    this.limit('redeem:' + keys[0], 6)
    const normalized = String(code || '').trim().toUpperCase().replace(/-/g, '')
    if (!/^[A-HJ-NP-Z2-9]{12}$/.test(normalized)) throw new PlayError(400, 'Use the code shown by Connect WhatsApp on NIGHT Play.')
    return this.transaction(() => {
      const challenge = this.db.prepare(`SELECT c.*, s.user_id FROM play_challenges c JOIN play_sessions s ON s.id=c.session_id
        WHERE code_hash=? AND c.expires_at>? AND s.expires_at>?`).get(hash(normalized), this.now(), this.now())
      if (!challenge || challenge.user_id) throw new PlayError(400, 'That code has expired or was already used. Generate a new code on NIGHT Play.')
      let profile = this.linkedProfile(identity)
      if (!profile) {
        profile = { id:'NIGHT-' + randomUUID(), displayName:cleanName(displayName) }
        this.db.prepare('INSERT INTO play_users VALUES (?,?,?)').run(profile.id, profile.displayName, this.now())
      }
      for (const key of keys) this.db.prepare('INSERT INTO play_identities VALUES (?,?) ON CONFLICT(identity) DO NOTHING').run(key, profile.id)
      this.db.prepare('UPDATE play_sessions SET user_id=? WHERE id=?').run(profile.id, challenge.session_id)
      this.db.prepare('DELETE FROM play_challenges WHERE session_id=?').run(challenge.session_id)
      return profile
    })
  }
  disconnectWhatsApp(identity) {
    const profile = this.linkedProfile(identity)
    if (profile) this.db.prepare('DELETE FROM play_sessions WHERE user_id=?').run(profile.id)
    return Boolean(profile)
  }
}

export function createNightPlayHandler(play, { publicKey = readFileSync(new URL('./night-play-public-key.pem', import.meta.url), 'utf8'), refreshAvatar = null, accounts = new Map() } = {}) {
  const key = createPublicKey(publicKey)
  return async (req, res) => {
    const reply = (status, body) => {
      res.writeHead(status, { 'content-type':'application/json; charset=utf-8', 'cache-control':'no-store', 'x-content-type-options':'nosniff' })
      res.end(JSON.stringify(body))
    }
    try {
      if (req.method !== 'POST') return reply(405, { error:'Method not allowed.' })
      const timestamp = String(req.headers['x-night-timestamp'] || '')
      const nonce = String(req.headers['x-night-nonce'] || '')
      const signature = String(req.headers['x-night-signature'] || '')
      if (!/^\d{13}$/.test(timestamp) || Math.abs(play.now() - Number(timestamp)) > 60_000 ||
          !/^[A-Za-z0-9_-]{32}$/.test(nonce) || !/^[A-Za-z0-9_-]{86}$/.test(signature)) {
        return reply(401, { error:'Signed website request required.' })
      }
      const chunks = []; let size = 0
      for await (const chunk of req) {
        size += chunk.length
        if (size > 8192) return reply(413, { error:'Request too large.' })
        chunks.push(chunk)
      }
      const raw = Buffer.concat(chunks).toString('utf8')
      if (!verify(null, Buffer.from(signatureMessage(timestamp, nonce, raw)), key, Buffer.from(signature, 'base64url'))) {
        return reply(401, { error:'Signed website request required.' })
      }
      play.cleanup()
      try { play.db.prepare('INSERT INTO play_nonces VALUES (?,?)').run(nonce, play.now() + 120_000) }
      catch { return reply(409, { error:'Request was already processed.' }) }
      let body
      try { body = JSON.parse(raw) } catch { return reply(400, { error:'Invalid JSON.' }) }
      if (!body || Array.isArray(body) || typeof body !== 'object') return reply(400, { error:'Invalid request.' })
      play.limit('rpc-global', 1800)
      play.limit((body.action==='party:state'?'rpc-poll-client:':'rpc-client:') + String(body.client || 'unknown'), body.action==='party:state'?900:180)
      const channelAction = typeof body.action === 'string' &&
        /^channel:(?:config:(?:get|update)|memes:(?:find|current|review|publish)|post:preview|jobs:(?:list|cancel))$/.test(body.action)
      const result = body.action === 'channel:verify' || channelAction ? await (async () => {
        const userId = body.session ? play.session(body.session)?.user_id : null
        if (!userId) throw new PlayError(401,'Connect WhatsApp to continue.')
        play.limit('channel-rpc:' + userId, 120, 60000)
        if (body.channelId !== undefined && !/^\d{8,25}@newsletter$/.test(String(body.channelId))) throw new PlayError(400,'Invalid channel.')
        if (channelAction) return play.channels.publisher.handle(body.action, body, userId, accounts)
        return play.channels.verifyUser(userId, accounts, body.channelId)
      })() : play.request(body)
      // An already-linked browser can obtain its permitted WhatsApp photo
      // without another QR/code. Background only: signed RPC stays responsive.
      if (body.action === 'status' && result.linked && !result.profile?.avatarData && refreshAvatar) {
        Promise.resolve().then(() => refreshAvatar(result.profile.id)).catch(() => {})
      }
      return reply(200, result)
    } catch (error) {
      return reply((error instanceof PlayError || error instanceof ChannelError || error instanceof PartyRoomError) ? error.status : 500, { error:(error instanceof PlayError || error instanceof ChannelError || error instanceof PartyRoomError) ? error.message : 'NIGHT Play could not process the request.' })
    }
  }
}
