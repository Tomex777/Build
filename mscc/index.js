import { cp, mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import makeWASocket, {
  Browsers,
  DisconnectReason,
  fetchLatestWaWebVersion,
  WAMessageStubType,
  jidNormalizedUser,
  makeCacheableSignalKeyStore,
  normalizeMessageContent,
  proto,
  useMultiFileAuthState
} from '@whiskeysockets/baileys'
import pino from 'pino'
import QRCode from 'qrcode'
import { startWebPanel } from './web-panel.js'
import { dispatchCommand, loadCommands } from './commands/registry.js'

const COMMANDS_URL = new URL('./commands/', import.meta.url)
let commandRegistry = await loadCommands(COMMANDS_URL)

const digits = value => String(value || '').replace(/\D/g, '')
const num = (name, fallback, min, max) => {
  const n = Number.parseInt(process.env[name] || '', 10)
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
}

const ACCOUNT_A_NUMBER = digits(process.env.ACCOUNT_A_NUMBER || process.env.BOT_NUMBER)
const ACCOUNT_B_NUMBER = digits(process.env.ACCOUNT_B_NUMBER)
const OWNER_NUMBER = digits(process.env.OWNER_NUMBER || ACCOUNT_A_NUMBER)
const ACCOUNT_A_AUTH_DIR = process.env.ACCOUNT_A_AUTH_DIR || '/var/lib/mscc/auth'
const ACCOUNT_B_AUTH_DIR = process.env.ACCOUNT_B_AUTH_DIR || '/var/lib/mscc/auth-b'
const DEFAULT_DESTINATION = String(process.env.CC_DESTINATION_ACCOUNT || 'A').toUpperCase() === 'B' ? 'B' : 'A'
const INDEX_FILE = process.env.MESSAGE_INDEX_FILE || '/var/lib/mscc/data/mscc-message-index.json'
const SETTINGS_FILE = process.env.SETTINGS_FILE || '/var/lib/mscc/data/mscc-settings.json'
const CORTEX_SETTINGS_SCHEMA_FILE = process.env.CORTEX_SETTINGS_SCHEMA_FILE || join(dirname(SETTINGS_FILE), 'cortex-settings-schema.json')
const AUTH_BACKUP_DIR = process.env.AUTH_BACKUP_DIR || '/var/backups/mscc'
const TTL_MS = num('MESSAGE_TTL_HOURS', 24, 1, 168) * 3600000
const MAX_CACHE = num('MAX_MESSAGE_CACHE', 5000, 100, 20000)
const WEB_PORT = process.env.SERVER_PORT
  ? num('SERVER_PORT', 8787, 1, 65535)
  : num('MSCC_WEB_PORT', num('PORT', 8787, 1, 65535), 1, 65535)
const WEB_PASSWORD = process.env.WEB_PASSWORD || ''
const WEB_SESSION_SECRET = process.env.WEB_SESSION_SECRET || ''
const LOCAL_CONTROL_PORT = 8788
const logger = pino({ level: process.env.LOG_LEVEL || 'silent' })
const startedAt = Date.now()
const APP_VERSION = '1.8.4'

if (!/^\d{7,15}$/.test(ACCOUNT_A_NUMBER)) {
  console.error('ACCOUNT_A_NUMBER (or BOT_NUMBER) is required.')
  process.exit(1)
}
if (ACCOUNT_B_NUMBER && !/^\d{7,15}$/.test(ACCOUNT_B_NUMBER)) {
  console.error('ACCOUNT_B_NUMBER is invalid.')
  process.exit(1)
}

const controlNumbers = new Set(
  String(process.env.CONTROL_NUMBERS || OWNER_NUMBER)
    .split(',')
    .map(digits)
    .filter(v => /^\d{7,15}$/.test(v))
)

const accounts = new Map()
const makeAccount = (id, number, authDir) => ({
  id, number, authDir, enabled: Boolean(number),
  sock: null, connected: false, registered: false, invalid: false,
  generation: 0, reconnectTimer: null,
  pairingMode: '', pairingCode: '', pairingQr: '', pairingError: '',
  lastQr: '', lastCodeAt: 0, pairingRequested: false,
  op: Promise.resolve()
})
accounts.set('A', makeAccount('A', ACCOUNT_A_NUMBER, ACCOUNT_A_AUTH_DIR))
accounts.set('B', makeAccount('B', ACCOUNT_B_NUMBER, ACCOUNT_B_AUTH_DIR))

let settings = { autoCc: false, replyCc: true, antiDelete: true }
let destination = DEFAULT_DESTINATION
let waVersion = null
let webServer = null
let saveTimer = null
let saveChain = Promise.resolve()
let settingsMtimeMs = 0
let settingsPollTimer = null

const cache = new Map()
const byId = new Map()
const handledReply = new Map()
const handledDelete = new Map()
const handledAuto = new Map()
const groupNames = new Map()

const normalizeJid = jid => jidNormalizedUser(jid || '')
const jidUser = jid => String(normalizeJid(jid)).split('@')[0].split(':')[0]
const selfJid = account => `${account.number}@s.whatsapp.net`
const isGroup = jid => normalizeJid(jid).endsWith('@g.us')
const trackable = jid => {
  const x = normalizeJid(jid)
  return x.endsWith('@g.us') || x.endsWith('@s.whatsapp.net') || x.endsWith('@lid')
}
const masked = n => !n ? 'Not configured' : n.length < 8 ? n : `${n.slice(0,3)}••••${n.slice(-4)}`
const destinationAccount = () => accounts.get(destination)

function futureproof(message) {
  let current = message
  for (let i = 0; i < 8 && current; i++) {
    for (const key of ['imageMessage','videoMessage','audioMessage']) {
      const media = current?.[key]
      if (media?.viewOnce === true) return { key, media }
    }
    const wrappers = ['viewOnceMessage','viewOnceMessageV2','viewOnceMessageV2Extension','ephemeralMessage','documentWithCaptionMessage','editedMessage','associatedChildMessage']
    let next = null
    for (const w of wrappers) {
      if (current?.[w]?.message) {
        next = current[w].message
        if (w.startsWith('viewOnceMessage')) {
          for (const key of ['imageMessage','videoMessage','audioMessage']) {
            const media = next?.[key]
            if (media) return { key, media: { ...media, viewOnce: true } }
          }
        }
        break
      }
    }
    if (!next) break
    current = next
  }
  const n = normalizeMessageContent(message) || message
  for (const key of ['imageMessage','videoMessage','audioMessage']) {
    if (n?.[key]?.viewOnce === true) return { key, media: n[key] }
  }
  return null
}

function unlocked(source) {
  const found = futureproof(source?.message)
  if (!found) return null
  return {
    ...source,
    key: { ...source.key },
    message: { [found.key]: { ...found.media, viewOnce: false } }
  }
}

function commandText(message) {
  const n = normalizeMessageContent(message) || message
  return String(
    n?.conversation ||
    n?.extendedTextMessage?.text ||
    n?.imageMessage?.caption ||
    n?.videoMessage?.caption ||
    n?.documentMessage?.caption ||
    ''
  ).trim()
}

function contextInfo(message) {
  const n = normalizeMessageContent(message) || message
  for (const value of Object.values(n || {})) {
    if (value && typeof value === 'object' && value.contextInfo) return value.contextInfo
  }
  return null
}

function quotedMessage(msg, context, chat) {
  if (!context?.stanzaId || !context?.quotedMessage) return null
  return {
    key: {
      remoteJid: context.remoteJid || chat || msg?.key?.remoteJid,
      id: context.stanzaId,
      participant: context.participant || undefined,
      fromMe: false
    },
    message: context.quotedMessage
  }
}

function encodeMessage(msg) {
  return Buffer.from(proto.WebMessageInfo.encode(msg).finish()).toString('base64')
}
function decodeMessage(data) {
  return proto.WebMessageInfo.decode(Buffer.from(data, 'base64'))
}
function idKey(accountId, id) { return `${accountId}|${id}` }
function cacheKey(accountId, msg) {
  const k = msg?.key || {}
  return `${accountId}|${normalizeJid(k.remoteJid)}|${k.id || ''}|${normalizeJid(k.participant)}`
}

function removeCache(key) {
  const e = cache.get(key)
  if (!e) return
  cache.delete(key)
  const indexKey = idKey(e.account, e.id)
  const set = byId.get(indexKey)
  if (set) {
    set.delete(key)
    if (!set.size) byId.delete(indexKey)
  }
}

function countFor(id) {
  let n = 0
  for (const e of cache.values()) if (e.account === id) n++
  return n
}

function prune() {
  const cutoff = Date.now() - TTL_MS
  for (const [k,e] of cache) if (!e.at || e.at < cutoff) removeCache(k)
  for (const a of accounts.values()) {
    let extra = countFor(a.id) - MAX_CACHE
    if (extra <= 0) continue
    for (const [k,e] of cache) {
      if (extra <= 0) break
      if (e.account === a.id) { removeCache(k); extra-- }
    }
  }
  for (const map of [handledReply, handledDelete, handledAuto]) {
    for (const [k,at] of map) if (Date.now() - at > 6 * 3600000) map.delete(k)
    while (map.size > 2500) map.delete(map.keys().next().value)
  }
}

function remember(account, msg) {
  if (!msg?.message || !msg?.key?.id || !trackable(msg.key.remoteJid)) return false
  const n = normalizeMessageContent(msg.message) || msg.message
  if (n?.protocolMessage || n?.reactionMessage) return false
  const key = cacheKey(account.id, msg)
  const entry = {
    account: account.id,
    id: msg.key.id,
    chat: normalizeJid(msg.key.remoteJid),
    participant: normalizeJid(msg.key.participant),
    at: Date.now(),
    data: encodeMessage(msg)
  }
  if (cache.has(key)) removeCache(key)
  cache.set(key, entry)
  const i = idKey(account.id, entry.id)
  let set = byId.get(i)
  if (!set) byId.set(i, set = new Set())
  set.add(key)
  prune()
  scheduleSave()
  return true
}

function findCached(accountId, key, fallbackChat) {
  const id = key?.id
  if (!id) return null
  const keys = byId.get(idKey(accountId, id))
  if (!keys) return null
  const chat = normalizeJid(key?.remoteJid || fallbackChat)
  const participant = normalizeJid(key?.participant)
  let fallback = null
  for (const k of keys) {
    const e = cache.get(k)
    if (!e) continue
    let msg
    try { msg = decodeMessage(e.data) } catch { continue }
    if (chat && e.chat === chat && (!participant || e.participant === participant)) return msg
    if (!fallback) fallback = msg
  }
  return fallback
}

async function loadState() {
  try {
    const raw = JSON.parse(await readFile(INDEX_FILE, 'utf8'))
    for (const item of raw?.messages || []) {
      if (!item?.data || !item?.at || Date.now() - item.at > TTL_MS) continue
      try {
        const accountId = item.account === 'B' ? 'B' : 'A'
        const msg = decodeMessage(item.data)
        const key = cacheKey(accountId, msg)
        const e = {
          account: accountId, id: msg.key.id,
          chat: normalizeJid(msg.key.remoteJid),
          participant: normalizeJid(msg.key.participant),
          at: item.at, data: item.data
        }
        cache.set(key, e)
        const i = idKey(accountId, e.id)
        let set = byId.get(i)
        if (!set) byId.set(i, set = new Set())
        set.add(key)
      } catch {}
    }
  } catch (e) {
    if (e?.code !== 'ENOENT') console.warn('Index load failed:', e?.message || e)
  }
  await reloadSettings(true)
  prune()
}

async function writeState() {
  prune()
  await mkdir(dirname(INDEX_FILE), { recursive: true })
  await writeFile(INDEX_FILE + '.tmp', JSON.stringify({
    version: 4,
    savedAt: Date.now(),
    messages: [...cache.values()].map(({account,at,data}) => ({account,at,data}))
  }))
  await rename(INDEX_FILE + '.tmp', INDEX_FILE)
}

function scheduleSave() {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveChain = saveChain.then(writeState).catch(e => console.error('Index save failed:', e?.message || e))
  }, 3000)
  saveTimer.unref?.()
}

async function reloadSettings(silent = false) {
  try {
    const raw = JSON.parse(await readFile(SETTINGS_FILE, 'utf8'))
    settings = {
      autoCc: raw?.autoCc === true,
      replyCc: raw?.replyCc !== false,
      antiDelete: raw?.antiDelete !== false
    }
    destination = raw?.destination === 'B' ? 'B' : raw?.destination === 'A' ? 'A' : DEFAULT_DESTINATION
    settingsMtimeMs = (await stat(SETTINGS_FILE)).mtimeMs
    if (!silent) console.log('MSCC settings reloaded from disk')
  } catch (e) {
    if (e?.code !== 'ENOENT') console.warn('Settings load failed:', e?.message || e)
  }
}

async function saveSettings() {
  await mkdir(dirname(SETTINGS_FILE), { recursive: true })
  await writeFile(SETTINGS_FILE + '.tmp', JSON.stringify({ version: 1, ...settings, destination, savedAt: Date.now() }, null, 2))
  await rename(SETTINGS_FILE + '.tmp', SETTINGS_FILE)
  settingsMtimeMs = (await stat(SETTINGS_FILE)).mtimeMs
}

async function writeCommandSettingsSchema() {
  const entries = commandRegistry.canonical
    .filter(command => command.setting?.key)
    .map(command => ({
      key: command.setting.key,
      label: command.setting.label || command.name,
      description: command.setting.description || command.description || '',
      command: command.name,
      type: 'boolean',
    }))
  await mkdir(dirname(CORTEX_SETTINGS_SCHEMA_FILE), { recursive: true })
  await writeFile(CORTEX_SETTINGS_SCHEMA_FILE + '.tmp', JSON.stringify({
    version: 1,
    service: 'mscc',
    entries,
  }, null, 2))
  await rename(CORTEX_SETTINGS_SCHEMA_FILE + '.tmp', CORTEX_SETTINGS_SCHEMA_FILE)
}

function startSettingsWatcher() {
  clearInterval(settingsPollTimer)
  settingsPollTimer = setInterval(async () => {
    try {
      const info = await stat(SETTINGS_FILE)
      if (info.mtimeMs > settingsMtimeMs + 1) await reloadSettings(false)
    } catch (e) {
      if (e?.code !== 'ENOENT') console.warn('Settings watch failed:', e?.message || e)
    }
  }, 1500)
  settingsPollTimer.unref?.()
}

async function pathExists(path) {
  try { await stat(path); return true } catch (e) { if (e?.code === 'ENOENT') return false; throw e }
}

async function backupAuth(account) {
  await mkdir(AUTH_BACKUP_DIR, { recursive: true })
  if (!(await pathExists(account.authDir))) {
    await mkdir(account.authDir, { recursive: true })
    return null
  }
  const stamp = new Date().toISOString().replace(/[:.]/g,'-')
  const target = join(AUTH_BACKUP_DIR, `account-${account.id}-${stamp}`)
  try {
    await rename(account.authDir, target)
  } catch (e) {
    if (e?.code !== 'EXDEV') throw e
    await cp(account.authDir, target, { recursive: true })
    await rm(account.authDir, { recursive: true, force: true })
  }
  await mkdir(account.authDir, { recursive: true })
  return target
}

function runOp(account, fn) {
  const r = account.op.catch(() => {}).then(fn)
  account.op = r.catch(() => {})
  return r
}

async function resolveSender(account, msg) {
  if (msg?.key?.fromMe) return selfJid(account)
  let jid = isGroup(msg?.key?.remoteJid) ? normalizeJid(msg?.key?.participant || msg?.participant) : normalizeJid(msg?.key?.remoteJid)
  if (jid.endsWith('@lid')) {
    try { jid = normalizeJid(await account.sock?.signalRepository?.lidMapping?.getPNForLID?.(jid) || jid) } catch {}
  }
  return jid
}

async function isController(account, msg) {
  return controlNumbers.has(jidUser(await resolveSender(account, msg)))
}

async function describe(account, msg) {
  const sender = jidUser(await resolveSender(account, msg)) || 'unknown'
  const chat = normalizeJid(msg?.key?.remoteJid)
  const accountLine = `Account: ${account.id}\n`
  if (isGroup(chat)) {
    const gk = `${account.id}|${chat}`
    let name = groupNames.get(gk)?.name
    if (!name || Date.now() - (groupNames.get(gk)?.at || 0) > 3600000) {
      try { name = (await account.sock.groupMetadata(chat))?.subject || chat } catch { name = chat }
      groupNames.set(gk, { name, at: Date.now() })
    }
    return `${accountLine}Group: ${name}\nSender: ${sender}`
  }
  return `${accountLine}DM: ${sender}`
}

async function sendInbox(source, content) {
  const dest = destinationAccount()
  if (!dest?.enabled) throw new Error(`Destination Account ${destination} is not configured`)
  if (dest.connected && dest.sock) {
    try { return await dest.sock.sendMessage(selfJid(dest), content) }
    catch (e) {
      if (!source?.connected || !source.sock || source.id === dest.id) throw e
    }
  }
  if (source?.connected && source.sock) return source.sock.sendMessage(selfJid(dest), content)
  throw new Error('Destination is offline')
}

async function onDelete(account, updates) {
  if (!settings.antiDelete) return
  for (const u of updates || []) {
    if (u?.update?.messageStubType !== WAMessageStubType.REVOKE || u?.update?.message !== null) continue
    const original = findCached(account.id, u.key, u.key?.remoteJid)
    if (!original) continue
    const dk = `${account.id}|${u.key?.remoteJid}|${u.key?.id}`
    if (handledDelete.has(dk)) continue
    try {
      await sendInbox(account, { text: `🗑️ Deleted message recovered\n${await describe(account, original)}` })
      await sendInbox(account, { forward: unlocked(original) || original, force: true })
      handledDelete.set(dk, Date.now())
    } catch (e) { console.error(`[${account.id}] delete recovery:`, e?.message || e) }
  }
}

async function onMessages(account, { messages, type }) {
  for (const msg of messages || []) {
    try {
      if (!msg?.message || !msg?.key?.id) continue
      const chat = normalizeJid(msg.key.remoteJid)
      const text = commandText(msg.message)
      const controller = await isController(account, msg)

      const commandHandled = await dispatchCommand(commandRegistry, text, {
        account,
        message: msg,
        controller,
        settings,
        reply: async value => sendInbox(account, { text: String(value) }),
        setSetting,
        setDestination,
        reloadCommands,
        statusText,
        diagnostics: commandDiagnostics,
      })
      if (commandHandled) continue

      remember(account, msg)
      const vo = !msg.key.fromMe && futureproof(msg.message)

      if (settings.autoCc && vo) {
        const ak = cacheKey(account.id, msg)
        if (!handledAuto.has(ak)) {
          if (account.id !== destination) await sendInbox(account, { text: `📥 Auto CC\n${await describe(account, msg)}` })
          await sendInbox(account, { forward: unlocked(msg), force: true })
          handledAuto.set(ak, Date.now())
        }
      }

      if (!settings.replyCc) continue
      const ctx = contextInfo(msg.message)
      if (!ctx?.stanzaId) continue
      const rk = `${account.id}|${msg.key.id}`
      if (handledReply.has(rk)) continue

      const quoted = quotedMessage(msg, ctx, chat)
      let source = quoted && futureproof(quoted.message) ? quoted : null
      if (!source) {
        const c = findCached(account.id, {
          id: ctx.stanzaId,
          remoteJid: ctx.remoteJid || chat,
          participant: ctx.participant
        }, chat)
        if (c && futureproof(c.message)) source = c
      }
      if (!source) continue

      if (!controller || account.id !== destination) {
        await sendInbox(account, { text: `↩️ V1 reply detected\n${await describe(account, msg)}` })
      }
      await sendInbox(account, { forward: unlocked(source), force: true })
      handledReply.set(rk, Date.now())
    } catch (e) {
      console.error(`[${account.id}] message error:`, e?.message || e)
    }
  }
}

function statusOf(a) {
  if (!a.enabled) return 'disabled'
  if (a.connected) return 'connected'
  if (a.invalid) return 'auth-invalid'
  if (a.pairingMode) return 'pairing'
  if (a.sock) return 'connecting'
  return 'offline'
}

async function closeAccount(a) {
  clearTimeout(a.reconnectTimer)
  a.reconnectTimer = null
  a.generation++
  const old = a.sock
  a.sock = null
  a.connected = false
  try { old?.ws?.close?.() } catch {}
  await new Promise(r => setTimeout(r, 120))
}

async function makePairOutput(account) {
  if (!account.lastQr || !account.sock || account.connected || account.registered) return
  if (account.pairingMode === 'qr') {
    try {
      account.pairingQr = await QRCode.toDataURL(account.lastQr, { width: 340, margin: 1 })
      account.pairingCode = ''
      account.pairingError = ''
    } catch (e) { account.pairingError = e?.message || String(e) }
    return
  }
  if (account.pairingMode !== 'code' || account.pairingRequested) return
  account.pairingRequested = true
  try {
    const code = await account.sock.requestPairingCode(account.number)
    account.pairingCode = code?.match(/.{1,4}/g)?.join('-') || code || ''
    account.pairingQr = ''
    account.pairingError = ''
    account.lastCodeAt = Date.now()
    console.log(`PAIRING CODE [${account.id}]: ${account.pairingCode}`)
  } catch (e) {
    account.pairingError = e?.message || String(e)
  } finally { account.pairingRequested = false }
}

async function startAccount(account) {
  if (!account.enabled || account.invalid) return
  const generation = ++account.generation
  const { state, saveCreds } = await useMultiFileAuthState(account.authDir)
  account.registered = Boolean(state.creds.registered)

  const options = {
    auth: { creds: state.creds, keys: makeCacheableSignalKeyStore(state.keys, logger) },
    logger,
    browser: Browsers.macOS('Chrome'),
    markOnlineOnConnect: false,
    syncFullHistory: false,
    shouldSyncHistoryMessage: () => false,
    generateHighQualityLinkPreview: false,
    getMessage: async key => findCached(account.id, key, key?.remoteJid)?.message
  }
  if (Array.isArray(waVersion)) options.version = waVersion

  const sock = makeWASocket(options)
  account.sock = sock
  account.connected = false

  sock.ev.on('creds.update', async () => { if (generation === account.generation) await saveCreds() })
  sock.ev.on('messages.upsert', upsert => { if (generation === account.generation) onMessages(account, upsert) })
  sock.ev.on('messages.update', updates => { if (generation === account.generation) onDelete(account, updates) })
  sock.ev.on('connection.update', async update => {
    if (generation !== account.generation || sock !== account.sock) return
    if (!state.creds.registered && update.qr) {
      account.lastQr = update.qr
      await makePairOutput(account)
      return
    }
    if (update.connection === 'open') {
      account.connected = true
      account.registered = true
      account.invalid = false
      account.pairingMode = ''
      account.pairingCode = ''
      account.pairingQr = ''
      account.pairingError = ''
      account.lastQr = ''
      console.log(`[${account.id}] connected as ${sock.user?.id || account.number}`)
      return
    }
    if (update.connection !== 'close') return
    account.connected = false
    account.sock = null
    const code = update.lastDisconnect?.error?.output?.statusCode
    if (code === DisconnectReason.loggedOut || code === DisconnectReason.badSession) {
      account.invalid = true
      account.pairingMode = ''
      account.pairingError = 'Saved auth is no longer valid. Use Re-pair.'
      return
    }
    account.reconnectTimer = setTimeout(() => {
      if (generation !== account.generation) return
      startAccount(account).catch(e => console.error(`[${account.id}] reconnect:`, e?.message || e))
    }, 2000)
    account.reconnectTimer.unref?.()
  })
}

function requireAccount(id) {
  const a = accounts.get(String(id).toUpperCase())
  if (!a?.enabled) throw new Error(`Account ${id} is not configured in /etc/mscc.env`)
  return a
}

async function pairAccount(id, mode) {
  const a = requireAccount(id)
  return runOp(a, async () => {
    if (a.connected) return { ok: true, message: `Account ${a.id} is already connected.` }
    if (a.registered || a.invalid) throw new Error(`Use Re-pair for Account ${a.id} because it already has saved auth.`)
    await closeAccount(a)
    if (await pathExists(a.authDir)) await backupAuth(a)
    a.invalid = false
    a.registered = false
    a.pairingMode = mode === 'qr' ? 'qr' : 'code'
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    a.lastQr = ''
    await startAccount(a)
    return { ok: true, message: `Preparing ${a.pairingMode} pairing for Account ${a.id}.` }
  })
}

async function reconnectAccount(id) {
  const a = requireAccount(id)
  return runOp(a, async () => {
    if (a.invalid) throw new Error('Use Re-pair because the saved auth is invalid.')
    await closeAccount(a)
    a.pairingMode = ''
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    await startAccount(a)
    return { ok: true }
  })
}

async function repairAccount(id, mode = 'code') {
  const a = requireAccount(id)
  return runOp(a, async () => {
    await closeAccount(a)
    await backupAuth(a)
    a.invalid = false
    a.registered = false
    a.pairingMode = mode === 'qr' ? 'qr' : 'code'
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    a.lastQr = ''
    await startAccount(a)
    return { ok: true, message: `Account ${a.id} auth backed up. Code pairing started.` }
  })
}

async function setSetting(key, value) {
  settings[key] = Boolean(value)
  await saveSettings()
}

async function reloadCommands() {
  const next = await loadCommands(COMMANDS_URL, { cacheBust: Date.now() })
  commandRegistry = next
  await writeCommandSettingsSchema()
  return next.canonical.map(command => command.name).sort()
}

async function setDestination(value) {
  const id = String(value || '').toUpperCase()
  if (!['A', 'B'].includes(id)) throw new Error('Destination must be A or B')
  const account = accounts.get(id)
  if (!account?.enabled) throw new Error(`Account ${id} is not configured`)
  destination = id
  await saveSettings()
  return destination
}

function uptime(ms) {
  const s = Math.floor(ms / 1000), d = Math.floor(s/86400), h = Math.floor((s%86400)/3600), m = Math.floor((s%3600)/60)
  return [d&&`${d}d`,(d||h)&&`${h}h`,(d||h||m)&&`${m}m`,`${s%60}s`].filter(Boolean).join(' ')
}

function commandDiagnostics() {
  return {
    version: APP_VERSION,
    destination,
    indexLimit: MAX_CACHE,
    retentionHours: Math.round(TTL_MS / 3600000),
    waVersion: Array.isArray(waVersion) ? waVersion.join('.') : '',
    accounts: [...accounts.values()].map(a => ({
      id: a.id,
      enabled: a.enabled,
      connected: a.connected,
      status: statusOf(a),
      numberMasked: masked(a.number),
      indexCount: countFor(a.id),
    })),
  }
}

async function statusText(ping = false) {
  const mem = process.memoryUsage()
  return `${ping ? '🏓 MSCC\n' : ''}Uptime: ${uptime(Date.now()-startedAt)}\nDestination: Account ${destination}\nA: ${statusOf(accounts.get('A'))} • ${countFor('A')}/${MAX_CACHE}\nB: ${statusOf(accounts.get('B'))} • ${countFor('B')}/${MAX_CACHE}\nRAM RSS: ${(mem.rss/1048576).toFixed(1)} MB\nAuto CC: ${settings.autoCc?'ON':'OFF'}\nReply CC: ${settings.replyCc?'ON':'OFF'}\nAnti-delete: ${settings.antiDelete?'ON':'OFF'}`
}

async function webState() {
  const mem = process.memoryUsage()
  return {
    version: APP_VERSION,
    destination,
    settings: { ...settings },
    accounts: [...accounts.values()].map(a => ({
      id: a.id,
      enabled: a.enabled,
      connected: a.connected,
      status: statusOf(a),
      numberMasked: masked(a.number),
      indexCount: countFor(a.id),
      indexLimit: MAX_CACHE,
      pairingMode: a.pairingMode,
      pairingCode: a.connected ? '' : a.pairingCode,
      pairingQr: a.connected ? '' : a.pairingQr,
      pairingError: a.connected ? '' : a.pairingError
    })),
    system: {
      uptime: uptime(Date.now()-startedAt),
      rssMb: Number((mem.rss/1048576).toFixed(1)),
      heapMb: Number((mem.heapUsed/1048576).toFixed(1)),
      port: WEB_PORT
    }
  }
}

async function init() {
  await loadState()
  await writeCommandSettingsSchema()
  startSettingsWatcher()
  webServer = startWebPanel({
    port: WEB_PORT,
    password: WEB_PASSWORD,
    sessionSecret: WEB_SESSION_SECRET,
    localControlPort: LOCAL_CONTROL_PORT,
    getState: webState,
    pairAccount,
    reconnectAccount,
    repairAccount,
    setSetting,
    setDestination
  })

  try {
    const latest = await fetchLatestWaWebVersion()
    waVersion = latest?.version
    if (Array.isArray(waVersion)) console.log('WhatsApp Web version:', waVersion.join('.'))
  } catch (e) {
    console.warn('Could not fetch latest WhatsApp Web version:', e?.message || e)
  }

  await startAccount(accounts.get('A'))
  if (accounts.get('B').enabled) {
    setTimeout(() => startAccount(accounts.get('B')).catch(e => console.error('[B] startup:', e?.message || e)), 1200).unref?.()
  }
}

for (const signal of ['SIGINT','SIGTERM']) {
  process.once(signal, async () => {
    try {
      webServer?.close?.()
      if (saveTimer) clearTimeout(saveTimer)
      if (settingsPollTimer) clearInterval(settingsPollTimer)
      await writeState()
    } finally { process.exit(0) }
  })
}

init().catch(error => {
  console.error('Fatal:', error)
  process.exit(1)
})
