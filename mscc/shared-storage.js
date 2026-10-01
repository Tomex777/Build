import { mkdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { DatabaseSync } from 'node:sqlite'

const PROFILE_RE = /^[a-z0-9][a-z0-9_-]{0,31}$/
const CAPABILITY_RE = /^[a-z0-9][a-z0-9._-]{0,63}$/

const profileId = value => {
  const id = String(value || '').trim().toLowerCase()
  if (!PROFILE_RE.test(id)) throw new Error('Profile ID must use lowercase letters, numbers, _ or -')
  return id
}

const capabilityId = value => {
  const id = String(value || 'general').trim().toLowerCase()
  if (!CAPABILITY_RE.test(id)) throw new Error('Capability must use lowercase letters, numbers, ., _ or -')
  return id
}

export async function openSharedStorage({ file, ttlMs, maxMessagesPerAccount }) {
  const path = resolve(file)
  await mkdir(dirname(path), { recursive: true })
  const db = new DatabaseSync(path, { timeout: 5000 })
  const store = new SharedStorage(db, {
    file: path,
    ttlMs,
    maxMessagesPerAccount,
  })
  store.initialize()
  return store
}

export class SharedStorage {
  constructor(db, { file, ttlMs, maxMessagesPerAccount }) {
    this.db = db
    this.file = file
    this.ttlMs = Math.max(3600000, Number(ttlMs) || 86400000)
    this.maxMessagesPerAccount = Math.max(100, Number(maxMessagesPerAccount) || 5000)
  }

  initialize() {
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA synchronous = NORMAL;
      PRAGMA foreign_keys = ON;

      CREATE TABLE IF NOT EXISTS message_index (
        account_id TEXT NOT NULL,
        chat_jid TEXT NOT NULL,
        message_id TEXT NOT NULL,
        participant_jid TEXT NOT NULL DEFAULT '',
        at_ms INTEGER NOT NULL,
        data_b64 TEXT NOT NULL,
        PRIMARY KEY (account_id, chat_jid, message_id, participant_jid)
      );
      CREATE INDEX IF NOT EXISTS idx_message_lookup
        ON message_index(account_id, message_id, at_ms DESC);
      CREATE INDEX IF NOT EXISTS idx_message_expiry
        ON message_index(at_ms);

      CREATE TABLE IF NOT EXISTS bot_profiles (
        profile_id TEXT PRIMARY KEY,
        display_name TEXT NOT NULL,
        universal INTEGER NOT NULL DEFAULT 1,
        base_priority INTEGER NOT NULL DEFAULT 0,
        created_at_ms INTEGER NOT NULL,
        updated_at_ms INTEGER NOT NULL
      );

      CREATE TABLE IF NOT EXISTS profile_capabilities (
        profile_id TEXT NOT NULL,
        capability TEXT NOT NULL,
        priority INTEGER NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (profile_id, capability),
        FOREIGN KEY (profile_id) REFERENCES bot_profiles(profile_id) ON DELETE CASCADE
      );

      CREATE TABLE IF NOT EXISTS account_profiles (
        account_id TEXT PRIMARY KEY,
        profile_id TEXT NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        FOREIGN KEY (profile_id) REFERENCES bot_profiles(profile_id) ON DELETE RESTRICT
      );

      CREATE TABLE IF NOT EXISTS group_routes (
        group_jid TEXT NOT NULL,
        capability TEXT NOT NULL,
        account_id TEXT NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (group_jid, capability)
      );

      CREATE TABLE IF NOT EXISTS shared_kv (
        namespace TEXT NOT NULL,
        item_key TEXT NOT NULL,
        value_json TEXT NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (namespace, item_key)
      );

      CREATE TABLE IF NOT EXISTS source_defaults (
        user_key TEXT NOT NULL,
        capability TEXT NOT NULL,
        source_id TEXT NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (user_key, capability)
      );

      CREATE TABLE IF NOT EXISTS delivery_defaults (
        user_key TEXT NOT NULL,
        capability TEXT NOT NULL,
        quality TEXT NOT NULL,
        delivery TEXT NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (user_key, capability)
      );

      CREATE TABLE IF NOT EXISTS conversation_messages (
        chat_jid TEXT NOT NULL,
        message_id TEXT NOT NULL,
        account_id TEXT NOT NULL DEFAULT '',
        participant_jid TEXT NOT NULL DEFAULT '',
        speaker TEXT NOT NULL DEFAULT '',
        from_bot INTEGER NOT NULL DEFAULT 0,
        text_content TEXT NOT NULL DEFAULT '',
        media_type TEXT NOT NULL DEFAULT '',
        at_ms INTEGER NOT NULL,
        PRIMARY KEY (chat_jid, message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_conversation_chat_time
        ON conversation_messages(chat_jid, at_ms DESC);
      CREATE INDEX IF NOT EXISTS idx_conversation_expiry
        ON conversation_messages(at_ms);
    `)

    const now = Date.now()
    const seedProfile = this.db.prepare(`
      INSERT INTO bot_profiles(profile_id, display_name, universal, base_priority, created_at_ms, updated_at_ms)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(profile_id) DO UPDATE SET
        display_name = excluded.display_name,
        universal = excluded.universal,
        base_priority = excluded.base_priority,
        updated_at_ms = excluded.updated_at_ms
    `)
    seedProfile.run('control', 'Control', 0, 0, now, now)
    seedProfile.run('josiah', 'Josiah', 1, 10, now, now)
    seedProfile.run('nami', 'Nami', 1, 0, now, now)
    seedProfile.run('mimi', 'MiMi', 1, 0, now, now)

    const seedCapability = this.db.prepare(`
      INSERT INTO profile_capabilities(profile_id, capability, priority, updated_at_ms)
      VALUES (?, ?, 100, ?)
      ON CONFLICT(profile_id, capability) DO UPDATE SET
        priority = excluded.priority,
        updated_at_ms = excluded.updated_at_ms
    `)
    seedCapability.run('nami', 'anime', now)
    seedCapability.run('nami', 'manga', now)
    seedCapability.run('mimi', 'music', now)
    seedCapability.run('mimi', 'movies', now)
    seedCapability.run('mimi', 'tv', now)

    // Migrate superseded public profile ids while preserving account assignments.
    // Account A is always corrected to control again during startup.
    this.db.prepare("UPDATE account_profiles SET profile_id = 'josiah', updated_at_ms = ? WHERE profile_id IN ('main','hex') AND account_id <> 'A'").run(now)
    this.db.prepare("UPDATE account_profiles SET profile_id = 'mimi', updated_at_ms = ? WHERE profile_id = 'mira'").run(now)
    this.db.prepare("DELETE FROM profile_capabilities WHERE profile_id IN ('main','hex','mira')").run()
    this.db.prepare("DELETE FROM bot_profiles WHERE profile_id IN ('main','hex','mira') AND profile_id NOT IN (SELECT profile_id FROM account_profiles)").run()
  }

  close() {
    try { this.db.exec('PRAGMA wal_checkpoint(TRUNCATE)') } catch {}
    this.db.close()
  }

  checkpoint() {
    this.db.exec('PRAGMA wal_checkpoint(PASSIVE)')
  }

  putMessage({ accountId, chatJid, messageId, participantJid = '', atMs = Date.now(), data }) {
    this.db.prepare(`
      INSERT INTO message_index(account_id, chat_jid, message_id, participant_jid, at_ms, data_b64)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(account_id, chat_jid, message_id, participant_jid)
      DO UPDATE SET at_ms = excluded.at_ms, data_b64 = excluded.data_b64
    `).run(
      String(accountId),
      String(chatJid || ''),
      String(messageId),
      String(participantJid || ''),
      Number(atMs),
      String(data),
    )
  }

  putConversationMessage({
    chatJid,
    messageId,
    accountId = '',
    participantJid = '',
    speaker = '',
    fromBot = false,
    text = '',
    mediaType = '',
    atMs = Date.now(),
  }) {
    const chat = String(chatJid || '').trim()
    const id = String(messageId || '').trim()
    if (!chat || !id) return false
    this.db.prepare(`
      INSERT INTO conversation_messages(
        chat_jid, message_id, account_id, participant_jid, speaker,
        from_bot, text_content, media_type, at_ms
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(chat_jid, message_id) DO UPDATE SET
        account_id = excluded.account_id,
        participant_jid = excluded.participant_jid,
        speaker = CASE WHEN excluded.speaker <> '' THEN excluded.speaker ELSE conversation_messages.speaker END,
        from_bot = excluded.from_bot,
        text_content = CASE WHEN excluded.text_content <> '' THEN excluded.text_content ELSE conversation_messages.text_content END,
        media_type = CASE WHEN excluded.media_type <> '' THEN excluded.media_type ELSE conversation_messages.media_type END,
        at_ms = excluded.at_ms
    `).run(
      chat,
      id,
      String(accountId || ''),
      String(participantJid || ''),
      String(speaker || '').slice(0, 120),
      fromBot ? 1 : 0,
      String(text || '').slice(0, 12000),
      String(mediaType || '').slice(0, 32),
      Number(atMs) || Date.now(),
    )
    return true
  }

  listConversationMessages({
    chatJid,
    sinceMs = 0,
    beforeMs = Number.MAX_SAFE_INTEGER,
    limit = 120,
    ascending = true,
  } = {}) {
    const chat = String(chatJid || '').trim()
    if (!chat) return []
    const safeLimit = Math.max(1, Math.min(25000, Number(limit) || 120))
    const rows = this.db.prepare(`
      SELECT chat_jid, message_id, account_id, participant_jid, speaker,
             from_bot, text_content, media_type, at_ms
      FROM conversation_messages
      WHERE chat_jid = ? AND at_ms >= ? AND at_ms <= ?
      ORDER BY at_ms DESC
      LIMIT ?
    `).all(
      chat,
      Math.max(0, Number(sinceMs) || 0),
      Math.max(0, Number(beforeMs) || Number.MAX_SAFE_INTEGER),
      safeLimit,
    )
    const mapped = rows.map(row => ({
      chatJid:row.chat_jid,
      messageId:row.message_id,
      accountId:row.account_id,
      participantJid:row.participant_jid,
      speaker:row.speaker,
      fromBot:Boolean(row.from_bot),
      text:row.text_content,
      mediaType:row.media_type,
      atMs:Number(row.at_ms || 0),
    }))
    return ascending ? mapped.reverse() : mapped
  }

  countConversationMessages(chatJid, sinceMs = 0) {
    const row = this.db.prepare(`
      SELECT COUNT(*) AS count
      FROM conversation_messages
      WHERE chat_jid = ? AND at_ms >= ?
    `).get(String(chatJid || ''), Math.max(0, Number(sinceMs) || 0))
    return Number(row?.count || 0)
  }

  pruneConversationMessages({ days = 30, maxPerChat = 25000 } = {}) {
    const safeDays = Math.max(1, Math.min(365, Number(days) || 30))
    const safeMax = Math.max(1000, Math.min(100000, Number(maxPerChat) || 25000))
    const cutoff = Date.now() - safeDays * 86400000
    this.db.prepare('DELETE FROM conversation_messages WHERE at_ms < ?').run(cutoff)
    const chats = this.db.prepare('SELECT DISTINCT chat_jid FROM conversation_messages').all()
    const trim = this.db.prepare(`
      DELETE FROM conversation_messages
      WHERE rowid IN (
        SELECT rowid FROM conversation_messages
        WHERE chat_jid = ?
        ORDER BY at_ms DESC
        LIMIT -1 OFFSET ?
      )
    `)
    for (const row of chats) trim.run(row.chat_jid, safeMax)
  }

  findMessage({ accountId, messageId, chatJid = '', participantJid = '' }) {
    const rows = this.db.prepare(`
      SELECT chat_jid, participant_jid, data_b64
      FROM message_index
      WHERE account_id = ? AND message_id = ?
      ORDER BY at_ms DESC
      LIMIT 12
    `).all(String(accountId), String(messageId))

    let fallback = null
    for (const row of rows) {
      if (!fallback) fallback = row.data_b64
      const chatMatches = !chatJid || row.chat_jid === chatJid
      const participantMatches = !participantJid || row.participant_jid === participantJid
      if (chatMatches && participantMatches) return row.data_b64
    }
    return fallback
  }

  countMessages(accountId) {
    const row = this.db.prepare('SELECT COUNT(*) AS count FROM message_index WHERE account_id = ?').get(String(accountId))
    return Number(row?.count || 0)
  }

  totalMessageCount() {
    const row = this.db.prepare('SELECT COUNT(*) AS count FROM message_index').get()
    return Number(row?.count || 0)
  }

  pruneMessages(accountIds = []) {
    const cutoff = Date.now() - this.ttlMs
    this.db.prepare('DELETE FROM message_index WHERE at_ms < ?').run(cutoff)

    const ids = [...new Set(accountIds.map(String).filter(Boolean))]
    for (const id of ids) {
      this.db.prepare(`
        DELETE FROM message_index
        WHERE rowid IN (
          SELECT rowid FROM message_index
          WHERE account_id = ?
          ORDER BY at_ms DESC
          LIMIT -1 OFFSET ?
        )
      `).run(id, this.maxMessagesPerAccount)
    }
  }

  createProfile(id, displayName = '', { universal = true, basePriority = 0 } = {}) {
    const normalized = profileId(id)
    const now = Date.now()
    const name = String(displayName || normalized).trim().slice(0, 48) || normalized
    this.db.prepare(`
      INSERT INTO bot_profiles(profile_id, display_name, universal, base_priority, created_at_ms, updated_at_ms)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT(profile_id) DO UPDATE SET
        display_name = excluded.display_name,
        updated_at_ms = excluded.updated_at_ms
    `).run(normalized, name, universal ? 1 : 0, Number(basePriority) || 0, now, now)
    return this.getProfile(normalized)
  }

  getProfile(id) {
    const normalized = profileId(id)
    const row = this.db.prepare(`
      SELECT profile_id, display_name, universal, base_priority
      FROM bot_profiles WHERE profile_id = ?
    `).get(normalized)
    if (!row) return null
    const capabilities = this.db.prepare(`
      SELECT capability, priority FROM profile_capabilities
      WHERE profile_id = ? ORDER BY capability
    `).all(normalized)
    return {
      id: row.profile_id,
      displayName: row.display_name,
      universal: Boolean(row.universal),
      basePriority: Number(row.base_priority),
      capabilities: capabilities.map(item => ({
        capability: item.capability,
        priority: Number(item.priority),
      })),
    }
  }

  listProfiles() {
    const ids = this.db.prepare('SELECT profile_id FROM bot_profiles ORDER BY profile_id').all()
    return ids.map(row => this.getProfile(row.profile_id))
  }

  setProfileMode(id, universal) {
    const normalized = profileId(id)
    const result = this.db.prepare(`
      UPDATE bot_profiles SET universal = ?, updated_at_ms = ?
      WHERE profile_id = ?
    `).run(universal ? 1 : 0, Date.now(), normalized)
    if (!Number(result.changes)) throw new Error(`Unknown profile: ${normalized}`)
    return this.getProfile(normalized)
  }

  setCapability(id, capability, priority) {
    const normalized = profileId(id)
    if (!this.getProfile(normalized)) throw new Error(`Unknown profile: ${normalized}`)
    const cap = capabilityId(capability)
    if (priority === null || priority === undefined || String(priority).toLowerCase() === 'off') {
      this.db.prepare('DELETE FROM profile_capabilities WHERE profile_id = ? AND capability = ?').run(normalized, cap)
      return this.getProfile(normalized)
    }
    const value = Math.max(-1000, Math.min(1000, Number.parseInt(priority, 10) || 0))
    this.db.prepare(`
      INSERT INTO profile_capabilities(profile_id, capability, priority, updated_at_ms)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(profile_id, capability) DO UPDATE SET
        priority = excluded.priority,
        updated_at_ms = excluded.updated_at_ms
    `).run(normalized, cap, value, Date.now())
    return this.getProfile(normalized)
  }

  setSpecialty(id, capability, enabled = true) {
    return this.setCapability(id, capability, enabled ? 100 : null)
  }

  brandForCapability(capability) {
    const cap = capabilityId(capability)
    const row = this.db.prepare(`
      SELECT p.display_name
      FROM profile_capabilities c
      JOIN bot_profiles p ON p.profile_id = c.profile_id
      WHERE c.capability = ?
      ORDER BY c.priority DESC, p.profile_id ASC
      LIMIT 1
    `).get(cap)
    return String(row?.display_name || 'Josiah')
  }

  assignProfile(accountId, id) {
    const normalized = profileId(id)
    if (!this.getProfile(normalized)) throw new Error(`Unknown profile: ${normalized}`)
    this.db.prepare(`
      INSERT INTO account_profiles(account_id, profile_id, updated_at_ms)
      VALUES (?, ?, ?)
      ON CONFLICT(account_id) DO UPDATE SET
        profile_id = excluded.profile_id,
        updated_at_ms = excluded.updated_at_ms
    `).run(String(accountId), normalized, Date.now())
    return this.profileForAccount(accountId)
  }

  profileForAccount(accountId) {
    const row = this.db.prepare(`
      SELECT p.profile_id, p.display_name, p.universal, p.base_priority
      FROM account_profiles a
      JOIN bot_profiles p ON p.profile_id = a.profile_id
      WHERE a.account_id = ?
    `).get(String(accountId))

    const fallbackId = String(accountId) === 'A' ? 'control' : 'josiah'
    const effective = row || this.db.prepare(`
      SELECT profile_id, display_name, universal, base_priority
      FROM bot_profiles WHERE profile_id = ?
    `).get(fallbackId)

    return {
      id: effective.profile_id,
      displayName: effective.display_name,
      universal: Boolean(effective.universal),
      basePriority: Number(effective.base_priority),
    }
  }

  capabilityScore(accountId, capability = 'general') {
    const profile = this.profileForAccount(accountId)
    const cap = capabilityId(capability)
    const exact = this.db.prepare(`
      SELECT priority FROM profile_capabilities
      WHERE profile_id = ? AND capability = ?
    `).get(profile.id, cap)
    if (exact) return Number(exact.priority)
    return profile.universal ? Number(profile.basePriority) : null
  }

  assignments() {
    return this.db.prepare(`
      SELECT account_id, profile_id FROM account_profiles
      ORDER BY account_id
    `).all()
  }

  clearAccount(accountId) {
    const id = String(accountId)
    this.db.prepare('DELETE FROM account_profiles WHERE account_id = ?').run(id)
    this.db.prepare('DELETE FROM group_routes WHERE account_id = ?').run(id)
    this.db.prepare('DELETE FROM message_index WHERE account_id = ?').run(id)
  }

  getGroupRoute(groupJid, capability = 'general') {
    const row = this.db.prepare(`
      SELECT account_id FROM group_routes
      WHERE group_jid = ? AND capability = ?
    `).get(String(groupJid), capabilityId(capability))
    return row?.account_id || ''
  }

  setGroupRoute(groupJid, capability, accountId) {
    const group = String(groupJid)
    const cap = capabilityId(capability)
    this.db.prepare(`
      INSERT INTO group_routes(group_jid, capability, account_id, updated_at_ms)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(group_jid, capability) DO UPDATE SET
        account_id = excluded.account_id,
        updated_at_ms = excluded.updated_at_ms
    `).run(group, cap, String(accountId), Date.now())
    return String(accountId)
  }

  listGroupRoutes(groupJid = '') {
    if (groupJid) {
      return this.db.prepare(`
        SELECT group_jid, capability, account_id, updated_at_ms
        FROM group_routes WHERE group_jid = ?
        ORDER BY capability
      `).all(String(groupJid))
    }
    return this.db.prepare(`
      SELECT group_jid, capability, account_id, updated_at_ms
      FROM group_routes ORDER BY updated_at_ms DESC LIMIT 100
    `).all()
  }

  clearGroupRoutes(groupJid = '') {
    if (groupJid) return Number(this.db.prepare('DELETE FROM group_routes WHERE group_jid = ?').run(String(groupJid)).changes)
    return Number(this.db.prepare('DELETE FROM group_routes').run().changes)
  }



  getDeliveryDefault(userKey, capability) {
    const row = this.db.prepare(`
      SELECT quality, delivery FROM delivery_defaults
      WHERE user_key = ? AND capability = ?
    `).get(String(userKey), capabilityId(capability))
    if (!row) return null
    return { quality:String(row.quality), delivery:String(row.delivery) }
  }

  setDeliveryDefault(userKey, capability, quality, delivery) {
    const cap = capabilityId(capability)
    const q = String(quality || '').trim().toLowerCase()
    const mode = String(delivery || '').trim().toLowerCase()
    if (!q || !mode) throw new Error('Quality and delivery are required')
    this.db.prepare(`
      INSERT INTO delivery_defaults(user_key, capability, quality, delivery, updated_at_ms)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(user_key, capability) DO UPDATE SET
        quality = excluded.quality,
        delivery = excluded.delivery,
        updated_at_ms = excluded.updated_at_ms
    `).run(String(userKey), cap, q, mode, Date.now())
    return { quality:q, delivery:mode }
  }

  clearDeliveryDefault(userKey, capability) {
    return Number(this.db.prepare(`
      DELETE FROM delivery_defaults WHERE user_key = ? AND capability = ?
    `).run(String(userKey), capabilityId(capability)).changes)
  }

  getSourceDefault(userKey, capability) {
    const row = this.db.prepare(`
      SELECT source_id FROM source_defaults
      WHERE user_key = ? AND capability = ?
    `).get(String(userKey), capabilityId(capability))
    return String(row?.source_id || '')
  }

  setSourceDefault(userKey, capability, sourceId) {
    const cap = capabilityId(capability)
    const source = String(sourceId || '').trim().toLowerCase()
    if (!source) throw new Error('Source ID cannot be empty')
    this.db.prepare(`
      INSERT INTO source_defaults(user_key, capability, source_id, updated_at_ms)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_key, capability) DO UPDATE SET
        source_id = excluded.source_id,
        updated_at_ms = excluded.updated_at_ms
    `).run(String(userKey), cap, source, Date.now())
    return source
  }

  clearSourceDefault(userKey, capability) {
    return Number(this.db.prepare(`
      DELETE FROM source_defaults WHERE user_key = ? AND capability = ?
    `).run(String(userKey), capabilityId(capability)).changes)
  }

  sharedGet(namespace, key) {
    const row = this.db.prepare(`
      SELECT value_json FROM shared_kv WHERE namespace = ? AND item_key = ?
    `).get(String(namespace), String(key))
    if (!row) return null
    try { return JSON.parse(row.value_json) } catch { return null }
  }

  sharedSet(namespace, key, value) {
    const json = JSON.stringify(value)
    this.db.prepare(`
      INSERT INTO shared_kv(namespace, item_key, value_json, updated_at_ms)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(namespace, item_key) DO UPDATE SET
        value_json = excluded.value_json,
        updated_at_ms = excluded.updated_at_ms
    `).run(String(namespace), String(key), json, Date.now())
    return value
  }

  sharedDelete(namespace, key) {
    return Number(this.db.prepare('DELETE FROM shared_kv WHERE namespace = ? AND item_key = ?').run(String(namespace), String(key)).changes)
  }

  stats() {
    const messages = this.totalMessageCount()
    const profiles = Number(this.db.prepare('SELECT COUNT(*) AS count FROM bot_profiles').get()?.count || 0)
    const routes = Number(this.db.prepare('SELECT COUNT(*) AS count FROM group_routes').get()?.count || 0)
    const sharedItems = Number(this.db.prepare('SELECT COUNT(*) AS count FROM shared_kv').get()?.count || 0)
    const sourceDefaults = Number(this.db.prepare('SELECT COUNT(*) AS count FROM source_defaults').get()?.count || 0)
    const deliveryDefaults = Number(this.db.prepare('SELECT COUNT(*) AS count FROM delivery_defaults').get()?.count || 0)
    return { file: this.file, messages, profiles, routes, sharedItems, sourceDefaults, deliveryDefaults }
  }
}
