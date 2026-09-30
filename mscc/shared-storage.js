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
    `)

    const now = Date.now()
    this.db.prepare(`
      INSERT INTO bot_profiles(profile_id, display_name, universal, base_priority, created_at_ms, updated_at_ms)
      VALUES ('main', 'Main', 1, 10, ?, ?)
      ON CONFLICT(profile_id) DO NOTHING
    `).run(now, now)
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

    const effective = row || this.db.prepare(`
      SELECT profile_id, display_name, universal, base_priority
      FROM bot_profiles WHERE profile_id = 'main'
    `).get()

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
    return { file: this.file, messages, profiles, routes, sharedItems }
  }
}
