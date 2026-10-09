import { appendFile, cp, mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { randomUUID } from 'node:crypto'
import makeWASocket, {
  Browsers,
  DisconnectReason,
  fetchLatestWaWebVersion,
  WAMessageStubType,
  makeCacheableSignalKeyStore,
  useMultiFileAuthState
} from '@itsliaaa/baileys'
import pino from 'pino'
import QRCode from 'qrcode'
import { startWebPanel } from './web-panel.js'
import { dispatchNamespacedCommand, loadCommands } from './command-registry.js'
import { AccountRegistry, legacyAccountRecords } from './account-registry.js'
import { selectCcDestination } from './cc-routing.js'
import { isPrivateOwnerDm } from './control-context.js'
import { classifyDisconnect, jidPhoneNumber, reconnectDelay } from './session-policy.js'
import { openSharedStorage } from './shared-storage.js'
import { chooseGroupExecutor, canExecuteDirect } from './bot-routing.js'
import { SourceRegistry } from './source-registry.js'
import { createSmartAI } from './smart-ai.js'
import { createAniListResolver } from './anilist-resolver.js'
import { createTmdbResolver } from './tmdb-resolver.js'
import { createAdaptationResolver } from './adaptation-resolver.js'
import { createReleaseWatcher } from './release-watcher.js'
import {
  addScheduledTask,
  completeScheduledTask,
  dueScheduledTasks,
  formatDue,
  listScheduledTasks,
  parseDuration,
  removeScheduledTask,
} from './scheduled-tasks.js'
import { mangaDexLatest } from './utility-services.js'
import {
  addAfkEvent,
  clearAfk,
  getAfk,
  messageMentionJids,
  quotedParticipantJid,
  setAfk,
} from './afk-state.js'
import {
  addWarning,
  clearWarnings,
  enforceGroupMessage,
  groupPolicy,
  renderGroupTemplate,
  setGroupPolicy,
  setUserMentionMute,
  warningState,
} from './group-policy.js'
import { looksLikeNumberSelection } from './number-selection.js'
import { chessRecordAcceptsInput } from './utils/chess-game.js'
import { ticTacToeRecordAcceptsInput } from './utils/tictactoe-game.js'
import { checkersRecordAcceptsInput } from './utils/checkers-game.js'
import { ludoRecordAcceptsInput } from './utils/ludo-game.js'
import { createJosiahAssistant } from './josiah-assistant.js'
import { createNamiAssistant } from './nami-assistant.js'
import { createMiMiAssistant } from './mimi-assistant.js'
import { chooseProfileAsset, groupIntro, presentationFor, profileHeader, readProfileAsset } from './profile-presentation.js'
import {
  digits,
  normalizeJid,
  jidUser,
  selfJid,
  isGroupJid,
  isTrackableJid,
  maskedPhone,
} from './utils/whatsapp/jid.js'
import {
  commandText,
  contextInfo,
  quotedMessage,
  findViewOnceMedia,
  unlockViewOnce,
  encodeMessage,
  decodeMessage,
  normalizedContent,
  messageMedia,
} from './utils/whatsapp/messages.js'
import { createWhatsAppUi } from './utils/whatsapp/ui.js'
import { sendText, sendImageDataUrl, startProgress } from './utils/whatsapp/replies.js'

// Shared WhatsApp nativeFlow implementation lives in utils/whatsapp/native-flow.js.
// The max-row invariant is enforced there (legacy CI marker: remaining = 1000).

const PRIVATE_COMMANDS_URL = new URL('./private-commands/', import.meta.url)
const PUBLIC_COMMANDS_URL = new URL('./commands/', import.meta.url)
const SOURCES_URL = new URL('./sources/', import.meta.url)
let privateCommandRegistry = await loadCommands(PRIVATE_COMMANDS_URL)
let publicCommandRegistry = await loadCommands(PUBLIC_COMMANDS_URL, { allowMissing: true, capabilityFromDirectory: true })

const num = (name, fallback, min, max) => {
  const n = Number.parseInt(process.env[name] || '', 10)
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback
}

const LEGACY_ACCOUNT_A_NUMBER = digits(process.env.ACCOUNT_A_NUMBER || process.env.BOT_NUMBER)
const LEGACY_ACCOUNT_B_NUMBER = digits(process.env.ACCOUNT_B_NUMBER)
const OWNER_NUMBER = digits(process.env.OWNER_NUMBER || LEGACY_ACCOUNT_A_NUMBER)
const LEGACY_ACCOUNT_A_AUTH_DIR = process.env.ACCOUNT_A_AUTH_DIR || '/var/lib/mscc/auth'
const LEGACY_ACCOUNT_B_AUTH_DIR = process.env.ACCOUNT_B_AUTH_DIR || '/var/lib/mscc/auth-b'
const INDEX_FILE = process.env.MESSAGE_INDEX_FILE || '/var/lib/mscc/data/mscc-message-index.json'
const SETTINGS_FILE = process.env.SETTINGS_FILE || '/var/lib/mscc/data/mscc-settings.json'
const SHARED_DB_FILE = process.env.MSCC_SHARED_DB_FILE || join(dirname(SETTINGS_FILE), 'mscc-shared.sqlite')
const ACCOUNT_REGISTRY_FILE = process.env.ACCOUNT_REGISTRY_FILE || join(dirname(SETTINGS_FILE), 'mscc-accounts.json')
const ACCOUNT_AUTH_ROOT = process.env.ACCOUNT_AUTH_ROOT || '/var/lib/mscc/accounts'
const CORTEX_SETTINGS_SCHEMA_FILE = process.env.CORTEX_SETTINGS_SCHEMA_FILE || join(dirname(SETTINGS_FILE), 'cortex-settings-schema.json')
const CORTEX_RUNTIME_REGISTRY_FILE = process.env.CORTEX_RUNTIME_REGISTRY_FILE || join(dirname(SETTINGS_FILE), 'cortex-runtime-registry.json')
const ACTIVITY_FILE = process.env.MSCC_ACTIVITY_FILE || join(dirname(SETTINGS_FILE), 'mscc-activity.jsonl')
const AUTH_BACKUP_DIR = process.env.AUTH_BACKUP_DIR || '/var/backups/mscc'
const TTL_MS = num('MESSAGE_TTL_HOURS', 24, 1, 168) * 3600000
const MAX_CACHE = num('MAX_MESSAGE_CACHE', 5000, 100, 20000)
const MAX_ACCOUNTS = num('MAX_ACCOUNTS', 4, 1, 50)
const GROUP_META_TTL_MS = num('GROUP_META_TTL_SECONDS', 60, 10, 600) * 1000
const GROUP_META_CACHE_MAX = num('GROUP_META_CACHE_MAX', 128, 16, 1024)
const AI_HISTORY_DAYS = num('AI_HISTORY_DAYS', 30, 1, 365)
const AI_HISTORY_MAX_PER_CHAT = num('AI_HISTORY_MAX_PER_CHAT', 25000, 1000, 100000)
const RELEASE_WATCH_INTERVAL_MS = num('RELEASE_WATCH_INTERVAL_MINUTES', 5, 1, 1440) * 60000
const WEB_PORT = process.env.SERVER_PORT
  ? num('SERVER_PORT', 8787, 1, 65535)
  : num('MSCC_WEB_PORT', num('PORT', 8787, 1, 65535), 1, 65535)
const WEB_HOST = process.env.MSCC_WEB_HOST || '127.0.0.1'
const WEB_PASSWORD = process.env.WEB_PASSWORD || ''
const WEB_SESSION_SECRET = process.env.WEB_SESSION_SECRET || ''
const LOCAL_CONTROL_PORT = 8788
const logger = pino({ level: process.env.LOG_LEVEL || 'silent' })
const startedAt = Date.now()
const APP_VERSION = '2.3.1'
const smartAI = createSmartAI()
const aniListResolver = createAniListResolver()
const tmdbResolver = createTmdbResolver()
const adaptationResolver = createAdaptationResolver()

const controlNumbers = new Set(
  String(process.env.CONTROL_NUMBERS || OWNER_NUMBER)
    .split(',')
    .map(digits)
    .filter(v => /^\d{7,15}$/.test(v))
)

const accountRegistry = new AccountRegistry({
  file: ACCOUNT_REGISTRY_FILE,
  authRoot: ACCOUNT_AUTH_ROOT,
  maxAccounts: MAX_ACCOUNTS,
  legacy: legacyAccountRecords({
    accountA: LEGACY_ACCOUNT_A_NUMBER,
    accountB: LEGACY_ACCOUNT_B_NUMBER,
    authA: LEGACY_ACCOUNT_A_AUTH_DIR,
    authB: LEGACY_ACCOUNT_B_AUTH_DIR,
  }),
})

const accounts = new Map()
const makeAccount = record => ({
  id: record.id,
  number: record.phoneNumber,
  authDir: record.authDir,
  displayName: record.displayName || '',
  role: record.role || 'linked',
  createdAt: record.createdAt || Date.now(),
  enabled: Boolean(record.phoneNumber),
  sock: null, connected: false, registered: false, invalid: false,
  generation: 0, reconnectTimer: null, reconnectAttempts: 0,
  credSave: Promise.resolve(),
  pairingMode: '', pairingCode: '', pairingQr: '', pairingError: '',
  lastQr: '', lastCodeAt: 0, pairingRequested: false,
  op: Promise.resolve()
})

async function loadAccounts() {
  const records = await accountRegistry.load()
  accounts.clear()
  for (const record of records) accounts.set(record.id, makeAccount(record))
  if (!records.length) {
    console.log('No WhatsApp accounts configured yet; waiting for Cortex pairing.')
    return
  }
  if (!controlNumbers.size) {
    const owner = records.find(row => row.role === 'owner') || records[0]
    if (owner?.phoneNumber) controlNumbers.add(owner.phoneNumber)
  }
}

function resolveAccountId(value) {
  const registryId = accountRegistry.resolveId(value)
  if (registryId && accounts.has(registryId)) return registryId
  const raw = String(value || '').trim()
  if (accounts.has(raw)) return raw
  const lower = raw.toLowerCase()
  for (const id of accounts.keys()) if (id.toLowerCase() === lower) return id
  return ''
}

async function createAccount(input = {}) {
  const record = await accountRegistry.create(input)
  const account = makeAccount(record)
  accounts.set(account.id, account)
  if (account.id === 'A') sharedStorage?.assignProfile('A', 'control')
  destination = destinationIdFor()
  if (record.role === 'owner' && record.phoneNumber) controlNumbers.add(record.phoneNumber)
  await recordActivity('account.created', {
    account: account.id,
    displayName: account.displayName,
    numberMasked: masked(account.number),
  })
  return {
    ok: true,
    account: {
      id: account.id,
      displayName: account.displayName,
      role: account.role,
      profile: sharedStorage?.profileForAccount(account.id)?.id || 'unassigned',
      enabled: account.enabled,
      connected: false,
      status: statusOf(account),
      numberMasked: masked(account.number),
      indexCount: 0,
      indexLimit: MAX_CACHE,
      pairingMode: '',
      pairingCode: '',
      pairingQr: '',
      pairingError: '',
    },
  }
}

async function renameAccount(id, displayName) {
  const resolved = resolveAccountId(id)
  if (!resolved) throw new Error(`Unknown account: ${id}`)
  const record = await accountRegistry.rename(resolved, displayName)
  const account = accounts.get(resolved)
  if (account) account.displayName = record.displayName
  await recordActivity('account.renamed', {
    account: resolved,
    displayName: record.displayName,
  })
  return {
    ok: true,
    account: resolved,
    displayName: record.displayName,
  }
}

async function recordActivity(action, detail = {}) {
  try {
    await mkdir(dirname(ACTIVITY_FILE), { recursive: true })
    const row = JSON.stringify({
      id: randomUUID(),
      at: new Date().toISOString(),
      action,
      detail,
    })
    await appendFile(ACTIVITY_FILE, row + '\n', 'utf8')
    try {
      const info = await stat(ACTIVITY_FILE)
      if (info.size > 2 * 1024 * 1024) {
        const text = await readFile(ACTIVITY_FILE, 'utf8')
        const lines = text.trim().split(/\r?\n/).filter(Boolean).slice(-2500)
        await writeFile(ACTIVITY_FILE, lines.join('\n') + '\n', 'utf8')
      }
    } catch {}
  } catch (error) {
    console.warn('Activity write failed:', error?.message || error)
  }
}

async function activity(limit = 100) {
  const safeLimit = Math.max(10, Math.min(500, Number(limit) || 100))
  try {
    const text = await readFile(ACTIVITY_FILE, 'utf8')
    return text.trim().split(/\r?\n/).filter(Boolean).slice(-safeLimit).reverse().flatMap(line => {
      try { return [JSON.parse(line)] } catch { return [] }
    })
  } catch (error) {
    if (error?.code === 'ENOENT') return []
    throw error
  }
}

const DEFAULT_PUBLIC_PREFIX = '.'

function validatePublicPrefix(value) {
  const prefix = String(value ?? '').trim()
  if (!prefix) throw new Error('Public prefix cannot be empty')
  if (/\s/.test(prefix)) throw new Error('Public prefix cannot contain whitespace')
  if ([...prefix].length > 8) throw new Error('Public prefix must be 1 to 8 characters')
  return prefix
}

function commandSettingDefaults(registry = privateCommandRegistry) {
  const defaults = {}
  for (const command of registry.canonical) {
    const key = String(command.setting?.key || '').trim()
    if (!key) continue
    defaults[key] = command.setting?.default === true
  }
  return defaults
}

function mergeCommandSettings(raw, registry = privateCommandRegistry) {
  const defaults = commandSettingDefaults(registry)
  const merged = { ...defaults }
  for (const key of Object.keys(defaults)) {
    if (typeof raw?.[key] === 'boolean') merged[key] = raw[key]
  }
  try { merged.publicPrefix = validatePublicPrefix(raw?.publicPrefix || DEFAULT_PUBLIC_PREFIX) }
  catch { merged.publicPrefix = DEFAULT_PUBLIC_PREFIX }
  return merged
}

let settings = { ...commandSettingDefaults(), publicPrefix: DEFAULT_PUBLIC_PREFIX }
let destination = ''
let waVersion = null
let webServer = null
let sharedStorage = null
let sourceRegistry = null
let josiahAssistant = null
let namiAssistant = null
let mimiAssistant = null
let releaseWatcher = null
let releaseWatchTimer = null
let releaseWatchRunning = false
let scheduledTaskTimer = null
let scheduledTaskRunning = false
let persistedMessageWrites = 0
let settingsMtimeMs = 0
let settingsPollTimer = null

const handledReply = new Map()
const handledDelete = new Map()
const handledAuto = new Map()
const groupNames = new Map()
const groupMetaCache = new Map()
const routeLocks = new Map()
const senderResolutionCache = new Map()
const SENDER_CACHE_TTL_MS = 10 * 60 * 1000
const SENDER_CACHE_MAX = 4096

const isGroup = isGroupJid
const trackable = isTrackableJid
const masked = maskedPhone
const destinationIdFor = () => selectCcDestination({ accounts })
const destinationAccount = () => accounts.get(destinationIdFor())

const futureproof = findViewOnceMedia
const unlocked = unlockViewOnce

function cacheKey(accountId, msg) {
  const k = msg?.key || {}
  return `${accountId}|${normalizeJid(k.remoteJid)}|${k.id || ''}|${normalizeJid(k.participant)}`
}

function countFor(id) {
  return sharedStorage?.countMessages(id) || 0
}

function trimTimedMap(map, max, ttlMs) {
  const now = Date.now()
  for (const [key, value] of map) {
    const at = typeof value === 'number' ? value : Number(value?.at || 0)
    if (at && now - at > ttlMs) map.delete(key)
  }
  while (map.size > max) map.delete(map.keys().next().value)
}

function prune() {
  for (const map of [handledReply, handledDelete, handledAuto]) {
    trimTimedMap(map, 512, 6 * 3600000)
  }
  trimTimedMap(groupNames, 128, 3600000)
  trimTimedMap(groupMetaCache, GROUP_META_CACHE_MAX, GROUP_META_TTL_MS)

  if (sharedStorage && persistedMessageWrites >= 100) {
    sharedStorage.pruneMessages([...accounts.keys()])
    sharedStorage.pruneConversationMessages({
      days:AI_HISTORY_DAYS,
      maxPerChat:AI_HISTORY_MAX_PER_CHAT,
    })
    persistedMessageWrites = 0
  }
}

function remember(account, msg) {
  if (!sharedStorage || !msg?.message || !msg?.key?.id || !trackable(msg.key.remoteJid)) return false
  const n = normalizedContent(msg.message)
  if (n?.protocolMessage || n?.reactionMessage) return false

  sharedStorage.putMessage({
    accountId: account.id,
    chatJid: normalizeJid(msg.key.remoteJid),
    messageId: msg.key.id,
    participantJid: normalizeJid(msg.key.participant),
    atMs: Date.now(),
    data: encodeMessage(msg),
  })
  persistedMessageWrites += 1
  prune()
  return true
}

function conversationMediaType(message) {
  const found = messageMedia(message)
  if (!found?.key) return ''
  return String(found.key).replace(/Message$/, '').replace(/^./, value => value.toLowerCase())
}

function rememberConversation(account, msg, authority = {}) {
  if (!sharedStorage || !msg?.message || !msg?.key?.id) return false
  const chat = normalizeJid(msg.key.remoteJid)
  if (!chat || !trackable(chat)) return false

  const normalized = normalizedContent(msg.message)
  if (normalized?.protocolMessage || normalized?.reactionMessage) return false

  const profile = sharedStorage.profileForAccount(account.id)
  const fromBot = msg.key.fromMe === true
  const speaker = fromBot
    ? (profile?.displayName || account.displayName || 'Bot')
    : (String(msg.pushName || '').trim() || authority.senderNumber || jidUser(msg.key.participant) || 'User')
  const timestamp = Number(msg.messageTimestamp)
  const atMs = Number.isFinite(timestamp) && timestamp > 1000000000
    ? timestamp * 1000
    : Date.now()

  sharedStorage.putConversationMessage({
    chatJid:chat,
    messageId:msg.key.id,
    accountId:account.id,
    participantJid:normalizeJid(msg.key.participant),
    speaker,
    fromBot,
    text:commandText(msg.message),
    mediaType:conversationMediaType(msg.message),
    atMs,
  })
  return true
}

function findCached(accountId, key, fallbackChat) {
  if (!sharedStorage || !key?.id) return null
  const data = sharedStorage.findMessage({
    accountId,
    messageId: key.id,
    chatJid: normalizeJid(key?.remoteJid || fallbackChat),
    participantJid: normalizeJid(key?.participant),
  })
  if (!data) return null
  try { return decodeMessage(data) } catch { return null }
}

function cachedMessageAcrossAccounts(preferredAccountId, messageId, chat) {
  const ids = [preferredAccountId, ...accounts.keys()].filter((value, index, list) =>
    value && list.indexOf(value) === index
  )
  for (const accountId of ids) {
    const cached = findCached(accountId, { id:messageId, remoteJid:chat }, chat)
    if (cached?.key?.id) return { accountId, message:cached }
  }
  return null
}

function suppressAntiDelete(chat, messageId) {
  for (const accountId of accounts.keys()) {
    handledDelete.set(`${accountId}|${chat}|${messageId}`, Date.now())
  }
}

async function deleteRecentMessages(account, msg, requestedCount = 1) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !isGroup(chat) || !account?.sock || !sharedStorage) return { deleted:0, requested:0 }

  const safeCount = Math.max(1, Math.min(100, Number.parseInt(requestedCount, 10) || 1))
  if (!(await isBotGroupAdminContext(account, msg))) {
    return { deleted:0, requested:safeCount, botAdmin:false }
  }

  const rows = sharedStorage.listConversationMessages({
    chatJid:chat,
    limit:Math.min(25000, safeCount + 64),
    ascending:false,
  })

  const currentId = String(msg?.key?.id || '')
  const targets = []
  for (const row of rows) {
    if (!row?.messageId || row.messageId === currentId) continue
    const cached = cachedMessageAcrossAccounts(account.id, row.messageId, chat)
    if (!cached?.message?.key?.id) continue
    targets.push(cached)
    if (targets.length >= safeCount) break
  }

  let deleted = 0
  for (const target of targets) {
    const key = target.message.key
    const deleteKey = {
      remoteJid:chat,
      id:key.id,
      fromMe:target.accountId === account.id ? Boolean(key.fromMe) : false,
      ...(key.participant ? { participant:key.participant } : {}),
    }
    try {
      suppressAntiDelete(chat, key.id)
      await account.sock.sendMessage(chat, { delete:deleteKey })
      deleted += 1
    } catch {}
  }

  // The delete command is deliberately revoked last. Successful usage leaves
  // no confirmation message and no visible command behind.
  if (currentId) {
    try {
      suppressAntiDelete(chat, currentId)
      await account.sock.sendMessage(chat, { delete:msg.key })
    } catch {}
  }

  return { deleted, requested:safeCount, botAdmin:true }
}

async function migrateLegacyIndex() {
  if (!sharedStorage || sharedStorage.totalMessageCount() > 0) return
  try {
    const raw = JSON.parse(await readFile(INDEX_FILE, 'utf8'))
    let imported = 0
    for (const item of raw?.messages || []) {
      if (!item?.data || !item?.at || Date.now() - item.at > TTL_MS) continue
      try {
        const accountId = resolveAccountId(item.account) || resolveAccountId('A') || accounts.keys().next().value
        if (!accountId) continue
        const msg = decodeMessage(item.data)
        sharedStorage.putMessage({
          accountId,
          chatJid: normalizeJid(msg.key.remoteJid),
          messageId: msg.key.id,
          participantJid: normalizeJid(msg.key.participant),
          atMs: item.at,
          data: item.data,
        })
        imported += 1
      } catch {}
    }
    if (imported || Array.isArray(raw?.messages)) {
      try { await rm(INDEX_FILE + '.migrated', { force: true }) } catch {}
      try { await rename(INDEX_FILE, INDEX_FILE + '.migrated') } catch {}
      await recordActivity('storage.message-index-migrated', { imported })
    }
  } catch (error) {
    if (error?.code !== 'ENOENT') console.warn('Legacy index migration failed:', error?.message || error)
  }
}

async function loadState() {
  await migrateLegacyIndex()
  sharedStorage?.pruneMessages([...accounts.keys()])
  await reloadSettings(true)
  prune()
}

async function writeState() {
  sharedStorage?.checkpoint()
}

async function reloadSettings(silent = false) {
  let raw = {}
  let exists = true
  try {
    raw = JSON.parse(await readFile(SETTINGS_FILE, 'utf8'))
  } catch (e) {
    if (e?.code !== 'ENOENT') {
      console.warn('Settings load failed:', e?.message || e)
      return
    }
    exists = false
  }

  settings = mergeCommandSettings(raw)
  destination = destinationIdFor()

  const storedDestination = raw?.cc?.fixedDestinationAccountId || raw?.cc?.defaultDestinationAccountId || raw?.destination || ''
  const hadOverrides = Object.keys(raw?.cc?.overrides || {}).length > 0
  if (!exists || storedDestination !== destination || hadOverrides || raw?.cc?.policy !== 'main-control-account') {
    await saveSettings()
  } else {
    settingsMtimeMs = (await stat(SETTINGS_FILE)).mtimeMs
  }

  if (!silent) {
    console.log('Night settings reloaded from disk')
    await recordActivity('configuration.reloaded', {})
  }
}

async function saveSettings() {
  await mkdir(dirname(SETTINGS_FILE), { recursive: true })
  destination = destinationIdFor()
  await writeFile(SETTINGS_FILE + '.tmp', JSON.stringify({
    version: 3,
    ...settings,
    destination,
    cc: {
      policy: 'main-control-account',
      fixedDestinationAccountId: destination,
    },
    savedAt: Date.now(),
  }, null, 2))
  await rename(SETTINGS_FILE + '.tmp', SETTINGS_FILE)
  settingsMtimeMs = (await stat(SETTINGS_FILE)).mtimeMs
}

async function writeCommandSettingsSchema() {
  const entries = privateCommandRegistry.canonical
    .filter(command => command.setting?.key)
    .map(command => ({
      key: command.setting.key,
      label: command.setting.label || command.name,
      description: command.setting.description || command.description || '',
      command: command.name,
      type: 'boolean',
      default: command.setting.default === true,
    }))
  await mkdir(dirname(CORTEX_SETTINGS_SCHEMA_FILE), { recursive: true })
  await writeFile(CORTEX_SETTINGS_SCHEMA_FILE + '.tmp', JSON.stringify({
    version: 1,
    service: 'mscc',
    entries,
  }, null, 2))
  await rename(CORTEX_SETTINGS_SCHEMA_FILE + '.tmp', CORTEX_SETTINGS_SCHEMA_FILE)
}

function runtimeCommand(command, scope) {
  let permission = 'all'
  if (scope === 'private') permission = 'supreme-owner-account-a-private-dm'
  else if (command.ownerOnly === true && command.adminOnly === true) permission = 'session-owner-or-supreme+group-admin'
  else if (command.ownerOnly === true) permission = 'session-owner-or-supreme'
  else if (command.adminOnly === true) permission = 'group-admin'

  return {
    name: command.name,
    moduleId: scope === 'private' ? 'mscc-private-commands' : 'mscc-public-commands',
    scope,
    capability: String(command.capability || 'general'),
    description: command.description || '',
    aliases: Array.isArray(command.aliases) ? command.aliases : [],
    enabled: true,
    permission,
    usage: command.usage || '',
    error: '',
  }
}

async function writeRuntimeRegistry() {
  const privateCommands = privateCommandRegistry.canonical
    .map(command => runtimeCommand(command, 'private'))
    .sort((a, b) => a.name.localeCompare(b.name))
  const publicCommands = publicCommandRegistry.canonical
    .map(command => runtimeCommand(command, 'public'))
    .sort((a, b) => a.name.localeCompare(b.name))
  const commands = [...privateCommands, ...publicCommands]

  const configEntries = privateCommandRegistry.canonical
    .filter(command => command.setting?.key)
    .map(command => ({
      key: command.setting.key,
      label: command.setting.label || command.name,
      type: 'boolean',
      description: command.setting.description || command.description || '',
    }))

  const modules = [
    {
      id: 'mscc-private-commands',
      legacyIds: ['mscc-core-commands'],
      displayName: 'Night Private Commands',
      version: APP_VERSION,
      status: 'loaded',
      enabled: true,
      commands: privateCommands.map(command => command.name),
      configuration: configEntries,
      loadError: '',
      lastReload: new Date().toISOString(),
      moduleDirectory: 'private-commands',
      dependencies: [],
      permissions: ['supreme-owner-account-a-private-dm', 'settings'],
    },
    {
      id: 'mscc-public-commands',
      displayName: 'Night Public Commands',
      version: APP_VERSION,
      status: 'loaded',
      enabled: true,
      commands: publicCommands.map(command => command.name),
      configuration: [],
      loadError: '',
      lastReload: new Date().toISOString(),
      moduleDirectory: 'commands',
      dependencies: [],
      permissions: ['commands', 'session-owner', 'group-admin', 'capability-routing'],
    },
  ]

  await mkdir(dirname(CORTEX_RUNTIME_REGISTRY_FILE), { recursive: true })
  await writeFile(CORTEX_RUNTIME_REGISTRY_FILE + '.tmp', JSON.stringify({
    version: 3,
    generatedAt: new Date().toISOString(),
    modules,
    commands,
  }, null, 2))
  await rename(CORTEX_RUNTIME_REGISTRY_FILE + '.tmp', CORTEX_RUNTIME_REGISTRY_FILE)
}

async function reloadModule(id) {
  const requestedId = String(id)
  const moduleId = requestedId === 'mscc-core-commands' ? 'mscc-private-commands' : requestedId
  if (!['mscc-private-commands', 'mscc-public-commands'].includes(moduleId)) throw new Error(`Unknown module: ${id}`)
  await reloadCommands()
  const registry = moduleId === 'mscc-private-commands' ? privateCommandRegistry : publicCommandRegistry
  const commands = registry.canonical.map(command => command.name).sort()
  await recordActivity('module.reloaded', { module: moduleId, commandCount: commands.length })
  return { ok:true, module:moduleId, commands }
}

function releaseProfileFor(item = {}) {
  return ['anime','manga'].includes(String(item?.mediaType || '')) ? 'nami' : 'mimi'
}

function releaseAccountCandidates(item = {}) {
  const preferred = releaseProfileFor(item)
  return [...accounts.values()]
    .filter(account => account?.enabled && account?.connected && account?.sock)
    .sort((a,b) => {
      const aProfile = sharedStorage?.profileForAccount(a.id)?.id || ''
      const bProfile = sharedStorage?.profileForAccount(b.id)?.id || ''
      const aScore = aProfile === preferred ? 0 : aProfile === 'control' ? 2 : 1
      const bScore = bProfile === preferred ? 0 : bProfile === 'control' ? 2 : 1
      return aScore - bScore
    })
}

async function resolveLibraryReleaseState(item = {}) {
  const id = Number(item?.externalId)
  if (!Number.isInteger(id) || id <= 0) return null

  if (item.mediaType === 'anime') return aniListResolver.releaseState(id)
  if (item.mediaType === 'tv') return tmdbResolver.releaseState(id, 'tv')
  if (item.mediaType === 'movie') return tmdbResolver.releaseState(id, 'movie')
  if (item.mediaType === 'manga') {
    try {
      const latest = await mangaDexLatest(item.title, item?.metadata?.aliases || [])
      return {
        kind:'chapter',
        number:Number(latest.chapter || 0) || 0,
        season:0,
        releasedAtMs:Date.parse(String(latest.publishedAt || '')) || 0,
        source:'mangadex',
        mangaDexId:latest.mangaId,
      }
    } catch {
      return null
    }
  }
  return null
}

async function sendLibraryReleaseDm(item, text) {
  const phone = digits(item?.userKey)
  if (!/^\d{7,15}$/.test(phone) || !String(text || '').trim()) return false
  const jid = normalizeJid(phone + '@s.whatsapp.net')

  for (const account of releaseAccountCandidates(item)) {
    try {
      await sendText(account.sock, jid, String(text).trim())
      await recordActivity('library.release-notified', {
        account:account.id,
        profile:sharedStorage?.profileForAccount(account.id)?.id || '',
        mediaType:item.mediaType,
        itemKey:item.itemKey,
      })
      return true
    } catch {}
  }
  return false
}

function scheduledTaskAccountCandidates() {
  return [...accounts.values()]
    .filter(account => account?.enabled && account?.connected && account?.sock)
    .sort((a,b) => {
      const ap = sharedStorage?.profileForAccount(a.id)?.id || ''
      const bp = sharedStorage?.profileForAccount(b.id)?.id || ''
      const score = value => value === 'josiah' ? 0 : value === 'control' ? 2 : 1
      return score(ap) - score(bp)
    })
}

async function sendScheduledTaskDm(task) {
  const phone = digits(task?.userKey)
  if (!/^\d{7,15}$/.test(phone)) return false
  const jid = normalizeJid(phone + '@s.whatsapp.net')
  let text = ''
  if (task.kind === 'reply-reminder') {
    const meta = task?.meta || {}
    text = [
      '⏰ *Reply reminder*',
      meta.senderName ? 'From: ' + meta.senderName : '',
      meta.chatLabel ? 'Chat: ' + meta.chatLabel : '',
      '',
      String(meta.messageText || task.text || '').trim(),
    ].filter(Boolean).join('\n')
  } else {
    const label = task.kind === 'timer' ? '⏱️ Timer finished' : '⏰ Reminder'
    const body = String(task.text || '').trim()
    text = body ? label + ': ' + body : label
  }

  for (const account of scheduledTaskAccountCandidates()) {
    try {
      await sendText(account.sock, jid, text)
      await recordActivity('schedule.delivered', {
        account:account.id,
        kind:task.kind,
        taskId:task.id,
      })
      return true
    } catch {}
  }
  return false
}

function startScheduledTaskRunner() {
  clearInterval(scheduledTaskTimer)

  const run = async () => {
    if (!sharedStorage || scheduledTaskRunning || shuttingDown) return
    scheduledTaskRunning = true
    try {
      const due = dueScheduledTasks(sharedStorage)
      for (const task of due) {
        if (await sendScheduledTaskDm(task)) completeScheduledTask(sharedStorage, task.id)
      }
    } catch (error) {
      console.warn('Scheduled task runner failed:', error?.message || error)
    } finally {
      scheduledTaskRunning = false
    }
  }

  scheduledTaskTimer = setInterval(run, 30000)
  scheduledTaskTimer.unref?.()
  setTimeout(run, 5000).unref?.()
}

async function resolveLatestRelease(query, type = '') {
  const term = String(query || '').trim()
  const kind = String(type || '').trim().toLowerCase()
  if (!term) return []

  if (kind === 'manga') {
    const latest = await mangaDexLatest(term)
    return [{
      type:'manga',
      title:latest.title,
      kind:'chapter',
      number:Number(latest.chapter || 0),
      source:'mangadex',
      publishedAt:latest.publishedAt,
    }]
  }

  const rows = []
  const wantAnime = !kind || kind === 'anime'
  const wantTv = !kind || kind === 'tv' || kind === 'series'
  const wantMovie = kind === 'movie' || kind === 'film'

  if (wantAnime) {
    try {
      const results = await aniListResolver.resolve(term, 'ANIME')
      const media = results?.[0]
      if (media?.anilistId) {
        const state = await aniListResolver.releaseState(media.anilistId)
        if (state) rows.push({
          type:'anime',
          title:media.title || term,
          ...state,
        })
      }
    } catch {}
  }

  if (wantTv) {
    try {
      const results = await tmdbResolver.search(term, 'tv')
      const media = results?.[0]
      if (media?.tmdbId) {
        const state = await tmdbResolver.releaseState(media.tmdbId, 'tv')
        if (state) rows.push({
          type:'tv',
          title:media.title || media.name || term,
          ...state,
        })
      }
    } catch {}
  }

  if (wantMovie) {
    try {
      const results = await tmdbResolver.search(term, 'movie')
      const media = results?.[0]
      if (media?.tmdbId) {
        const state = await tmdbResolver.releaseState(media.tmdbId, 'movie')
        if (state) rows.push({
          type:'movie',
          title:media.title || media.name || term,
          ...state,
        })
      }
    } catch {}
  }

  return rows
}

function startLibraryReleaseWatcher() {
  if (!releaseWatcher) return
  clearInterval(releaseWatchTimer)

  const run = async () => {
    if (releaseWatchRunning || shuttingDown) return
    releaseWatchRunning = true
    try {
      await releaseWatcher.checkOnce()
    } catch (error) {
      console.warn('Release watch failed:', error?.message || error)
    } finally {
      releaseWatchRunning = false
    }
  }

  releaseWatchTimer = setInterval(run, RELEASE_WATCH_INTERVAL_MS)
  releaseWatchTimer.unref?.()
  setTimeout(run, Math.min(60000, Math.max(5000, Math.floor(RELEASE_WATCH_INTERVAL_MS / 4)))).unref?.()
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

async function resolvePhoneJid(account, jid) {
  const normalized = normalizeJid(jid)
  if (!normalized) return ''
  if (normalized.endsWith('@s.whatsapp.net')) return normalized
  const key = String(account?.id || '') + '|' + normalized
  const cached = senderResolutionCache.get(key)
  if (cached && Date.now() - cached.at < SENDER_CACHE_TTL_MS) return cached.phoneJid
  if (normalized.endsWith('@lid') || normalized.endsWith('@hosted.lid')) {
    try {
      const ids = await account.sock?.findUserId?.(normalized)
      const phoneJid = normalizeJid(ids?.phoneNumber || '') || normalized
      senderResolutionCache.set(key, { phoneJid, at:Date.now() })
      while (senderResolutionCache.size > SENDER_CACHE_MAX) {
        senderResolutionCache.delete(senderResolutionCache.keys().next().value)
      }
      return phoneJid
    } catch {}
  }
  return normalized
}

async function resolveSender(account, msg) {
  if (msg?.key?.fromMe) return selfJid(account)
  const jid = isGroup(msg?.key?.remoteJid) ? normalizeJid(msg?.key?.participant || msg?.participant) : normalizeJid(msg?.key?.remoteJid)
  return resolvePhoneJid(account, jid)
}

async function resolveCommandTarget(account, msg, raw = '') {
  const token = String(raw || '').trim()
  const explicit = digits(token)
  if (explicit && /^\d{7,15}$/.test(explicit) && /^[+\d(). -]+$/.test(token)) return { phoneNumber: explicit, source: 'explicit' }

  const info = contextInfo(msg?.message)
  const mentions = Array.isArray(info?.mentionedJid) ? info.mentionedJid : []

  if (token.startsWith('@') && mentions.length) {
    const phoneJid = await resolvePhoneJid(account, mentions[0])
    const phoneNumber = digits(jidUser(phoneJid))
    if (phoneNumber) return { phoneNumber, source: 'mention' }
  }

  const quotedCandidate = info?.participant || (info?.remoteJid && !isGroup(info.remoteJid) ? info.remoteJid : '')
  if (info?.stanzaId && quotedCandidate) {
    const phoneJid = await resolvePhoneJid(account, quotedCandidate)
    const phoneNumber = digits(jidUser(phoneJid))
    if (phoneNumber) return { phoneNumber, source: 'reply' }
  }

  if (mentions.length) {
    const phoneJid = await resolvePhoneJid(account, mentions[0])
    const phoneNumber = digits(jidUser(phoneJid))
    if (phoneNumber) return { phoneNumber, source: 'mention' }
  }
  return { phoneNumber: '', source: '' }
}

async function jidBelongsToAccount(account, jid) {
  const normalized = normalizeJid(jid)
  if (!normalized) return false
  if (normalizeJid(account.sock?.user?.id) === normalized || normalizeJid(account.sock?.user?.lid) === normalized) return true
  const phoneJid = await resolvePhoneJid(account, normalized)
  return Boolean(account.number && jidUser(phoneJid) === account.number)
}

function escapeAssistantRegExp(value) {
  return String(value || '').replace(/[.*+?^$()|[\]\\]/g, '\\$&')
}

function stripAssistantAddress(text, displayName = 'Josiah') {
  let value = String(text || '').trim()
  value = value.replace(/^@\d{5,20}\s*/u, '').trimStart()
  const escaped = escapeAssistantRegExp(displayName || 'Josiah')
  value = value.replace(new RegExp('^' + escaped + '\\s*[:,\\-]?\\s*', 'i'), '').trim()
  return value
}

function assistantForAccount(account) {
  const profile = sharedStorage?.profileForAccount(account.id)
  if (profile?.id === 'josiah') return josiahAssistant
  if (profile?.id === 'nami') return namiAssistant
  if (profile?.id === 'mimi') return mimiAssistant
  return null
}

async function assistantActivation(account, msg, text) {
  const profile = sharedStorage?.profileForAccount(account.id)
  if (!assistantForAccount(account) || msg?.key?.fromMe || settings.publicCommandsEnabled === false) {
    return { active:false, reason:'' }
  }

  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) {
    return settings.aiDirectMessages === true
      ? { active:true, reason:'dm' }
      : { active:false, reason:'' }
  }

  const info = contextInfo(msg?.message)
  for (const mentioned of info?.mentionedJid || []) {
    if (await jidBelongsToAccount(account, mentioned)) return { active:true, reason:'mention' }
  }

  if (info?.stanzaId) {
    const cached = findCached(account.id, {
      id:info.stanzaId,
      remoteJid:info.remoteJid || chat,
      participant:info.participant,
    }, chat)
    if (cached?.key?.fromMe) return { active:true, reason:'reply' }
    if (info?.participant && await jidBelongsToAccount(account, info.participant)) {
      return { active:true, reason:'reply' }
    }
  }

  const name = String(profile?.displayName || 'Assistant').trim()
  if (name && new RegExp('^' + escapeAssistantRegExp(name) + '\\b', 'i').test(String(text || '').trim())) {
    return { active:true, reason:'name' }
  }

  return { active:false, reason:'' }
}

async function assistantQuote(account, msg) {
  const info = contextInfo(msg?.message)
  if (!info?.stanzaId) return { text:'', speaker:'' }

  const directText = commandText(info.quotedMessage)
  const cached = findCached(account.id, {
    id:info.stanzaId,
    remoteJid:info.remoteJid || msg?.key?.remoteJid,
    participant:info.participant,
  }, msg?.key?.remoteJid)

  let text = directText || commandText(cached?.message)
  if (!text && info?.quotedMessage) {
    const media = conversationMediaType(info.quotedMessage)
    if (media) text = '[' + media + ']'
  }

  const profile = sharedStorage?.profileForAccount(account.id)
  let speaker = ''
  if (cached?.key?.fromMe) {
    speaker = profile?.displayName || 'Assistant'
  } else if (cached?.pushName) {
    speaker = String(cached.pushName)
  } else if (info?.participant) {
    const phone = await resolvePhoneJid(account, info.participant)
    speaker = jidUser(phone) || jidUser(info.participant)
  }

  return { text:String(text || '').trim(), speaker:String(speaker || '').trim() }
}

async function assistantGroupName(account, chat) {
  if (!isGroup(chat)) return ''
  const metadata = await groupMetadataCached(account, chat)
  return String(metadata?.subject || '').trim()
}

const PUBLIC_PERSONALITY_IDS = new Set(['josiah', 'nami', 'mimi'])

async function groupPersonalityPresence(account, chat) {
  if (!isGroup(chat) || !sharedStorage) return []
  const metadata = await groupMetadataCached(account, chat)
  const participants = Array.isArray(metadata?.participants) ? metadata.participants : []
  const rows = []

  for (const candidate of accounts.values()) {
    const profile = sharedStorage.profileForAccount(candidate.id)
    if (!PUBLIC_PERSONALITY_IDS.has(profile?.id)) continue

    let present = false
    for (const participant of participants) {
      const jid = typeof participant === 'string'
        ? participant
        : participant?.id || participant?.jid || participant?.lid || ''
      if (jid && await jidBelongsToAccount(candidate, jid)) {
        present = true
        break
      }
    }
    if (!present) continue

    rows.push({
      profileId:profile.id,
      displayName:profile.displayName,
      accountId:candidate.id,
      mentionJid:selfJid(candidate),
      mentionToken:`[[mention:${profile.id}]]`,
    })
  }

  const order = new Map([['josiah', 0], ['nami', 1], ['mimi', 2]])
  rows.sort((a, b) =>
    (order.get(a.profileId) ?? 99) - (order.get(b.profileId) ?? 99) ||
    String(a.accountId).localeCompare(String(b.accountId))
  )
  return rows
}

function senderPersonalityFromPresence(authority, presence = []) {
  const sender = digits(authority?.senderNumber || jidUser(authority?.senderJid))
  if (!sender) return ''
  const row = presence.find(item => digits(jidUser(item?.mentionJid)) === sender)
  return String(row?.profileId || '')
}

function renderAssistantMentions(value, presence = [], { allowMentions = true } = {}) {
  const rows = new Map(
    presence.map(row => [String(row?.profileId || '').trim().toLowerCase(), row])
  )
  const mentions = []
  const text = String(value || '').replace(/\[\[mention:([a-z0-9_-]+)\]\]/gi, (_match, rawId) => {
    const id = String(rawId || '').trim().toLowerCase()
    const row = rows.get(id)
    if (!row) return id
    if (!allowMentions || !row.mentionJid) return row.displayName || id
    const user = jidUser(row.mentionJid)
    if (!user) return row.displayName || id
    mentions.push(row.mentionJid)
    return '@' + user
  })
  return { text, mentions:[...new Set(mentions)] }
}

async function handleProfileGroupIntro(account, update) {
  const group = normalizeJid(update?.id)
  const profile = sharedStorage?.profileForAccount(account.id)
  const presentation = presentationFor(profile?.id)
  if (!group || !presentation || String(update?.action || '').toLowerCase() !== 'add') return false

  const participants = Array.isArray(update?.participants) ? update.participants : []
  let addedSelf = false
  for (const participant of participants) {
    const jid = typeof participant === 'string' ? participant : participant?.id
    if (jid && await jidBelongsToAccount(account, jid)) {
      addedSelf = true
      break
    }
  }
  if (!addedSelf) return false

  await new Promise(resolve => setTimeout(resolve, 1500))
  if (!account.connected || !account.sock) return false

  const key = profile.id + '|' + group
  const previous = sharedStorage?.sharedGet('profile-group-intro', key)
  const returning = Boolean(previous?.count)
  const groupName = await assistantGroupName(account, group)
  const body = groupIntro(profile.id, { returning, groupName })
  const caption = [profileHeader(profile.id), '', body].filter(Boolean).join('\n')
  if (!caption) return false

  const imagePath = await chooseProfileAsset(profile.id, 'intro', { returning })
  if (imagePath) {
    try {
      const image = await readProfileAsset(imagePath)
      await account.sock.sendMessage(group, { image, caption })
    } catch {
      await sendText(account.sock, group, caption)
    }
  } else {
    await sendText(account.sock, group, caption)
  }

  sharedStorage?.sharedSet('profile-group-intro', key, {
    count:Number(previous?.count || 0) + 1,
    firstAt:Number(previous?.firstAt || 0) || Date.now(),
    lastAt:Date.now(),
    profileId:profile.id,
  })
  await recordActivity('profile.group-introduced', {
    account:account.id,
    profile:profile.id,
    group,
    returning,
    usedImage:Boolean(imagePath),
  })
  return true
}

async function sendAssistantReply(account, msg, text, {
  presence = [],
  allowPersonalityMentions = true,
} = {}) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  const rendered = renderAssistantMentions(text, presence, { allowMentions:allowPersonalityMentions })
  const value = String(rendered.text || '').trim()
  if (!chat || !value || !account?.sock) return null
  const sent = await sendText(account.sock, chat, value, { quoted:msg, mentions:rendered.mentions })

  if (sharedStorage && sent?.key?.id) {
    const profile = sharedStorage.profileForAccount(account.id)
    sharedStorage.putConversationMessage({
      chatJid:chat,
      messageId:sent.key.id,
      accountId:account.id,
      participantJid:normalizeJid(sent.key.participant),
      speaker:profile?.displayName || 'Personality',
      fromBot:true,
      text:value,
      atMs:Date.now(),
    })
  }
  return sent
}

async function handleProfileAssistant(account, msg, authority, rawText) {
  const assistant = assistantForAccount(account)
  if (!assistant) return false

  const activation = await assistantActivation(account, msg, rawText)
  if (!activation.active) return false

  const chat = normalizeJid(msg?.key?.remoteJid)
  const profile = sharedStorage?.profileForAccount(account.id)
  const quote = await assistantQuote(account, msg)
  const groupName = await assistantGroupName(account, chat)
  const presence = await groupPersonalityPresence(account, chat)
  const senderPersonality = senderPersonalityFromPresence(authority, presence)
  const text = stripAssistantAddress(rawText, profile?.displayName || assistant.displayName || 'Personality')
    || 'You were mentioned. Respond naturally.'

  const result = await assistant.answer({
    chatJid:chat,
    text,
    senderName:String(msg.pushName || '').trim() || authority.senderNumber || 'User',
    quotedText:quote.text,
    quotedSpeaker:quote.speaker,
    groupName,
    isGroup:isGroup(chat),
    groupPersonalities:presence,
    senderPersonality,
  })
  if (!result?.text) return false

  await sendAssistantReply(account, msg, result.text, {
    presence,
    allowPersonalityMentions:!senderPersonality,
  })
  await recordActivity('ai.responded', {
    account:account.id,
    profile:profile?.id || assistant.profileId || '',
    group:isGroup(chat),
    activation:activation.reason,
    usedWeb:result.usedWeb === true,
  })
  return true
}

function publicSudoNumbers() {
  const rows = sharedStorage?.sharedGet('public-sudo', 'numbers')
  return Array.isArray(rows)
    ? [...new Set(rows.map(digits).filter(value => /^\d{7,15}$/.test(value)))]
    : []
}

function isPublicSudoNumber(phoneNumber) {
  const phone = digits(phoneNumber)
  return Boolean(phone && publicSudoNumbers().includes(phone))
}

function setPublicSudoNumber(phoneNumber, enabled) {
  const phone = digits(phoneNumber)
  if (!/^\d{7,15}$/.test(phone)) throw new Error('That is not a valid WhatsApp number.')
  const current = new Set(publicSudoNumbers())
  if (enabled) current.add(phone)
  else current.delete(phone)
  const rows = [...current].sort()
  sharedStorage?.sharedSet('public-sudo', 'numbers', rows)
  return rows
}

async function authorityContext(account, msg) {
  const senderJid = await resolveSender(account, msg)
  const senderNumber = jidUser(senderJid)
  const isSupremeOwner = controlNumbers.has(senderNumber)
  const isSudo = isPublicSudoNumber(senderNumber)
  return {
    senderJid,
    senderNumber,
    isSupremeOwner,
    isSudo,
    isPublicOwner:isSupremeOwner || isSudo,
    isSessionOwner: isSupremeOwner || Boolean(senderNumber && senderNumber === account.number),
  }
}

async function isController(account, msg) {
  return (await authorityContext(account, msg)).isSupremeOwner
}

async function broadcastNightGroups(sourceAccount, text) {
  const body = String(text || '').trim()
  if (!body) throw new Error('Broadcast text is empty.')

  const ordered = [
    sourceAccount,
    ...[...accounts.values()].filter(account => account?.id !== sourceAccount?.id),
  ].filter((account, index, rows) =>
    account?.enabled &&
    account?.connected &&
    account?.sock &&
    rows.findIndex(other => other?.id === account.id) === index
  )

  const targets = new Map()
  for (const account of ordered) {
    let groups = {}
    try { groups = await account.sock.groupFetchAllParticipating() || {} } catch { continue }
    for (const group of Object.keys(groups)) {
      const jid = normalizeJid(group)
      if (!isGroup(jid) || targets.has(jid)) continue
      targets.set(jid, account)
    }
  }

  let sent = 0
  let failed = 0
  for (const [group, account] of [...targets.entries()].slice(0, 500)) {
    try {
      await account.sock.sendMessage(group, { text:body })
      sent += 1
    } catch {
      failed += 1
    }
    await new Promise(resolve => setTimeout(resolve, 80))
  }

  await recordActivity('owner.broadcast', {
    sourceAccount:sourceAccount?.id || '',
    groups:targets.size,
    sent,
    failed,
  })
  return { total:targets.size, sent, failed }
}

async function setPublicSudoTarget(account, msg, raw, enabled) {
  const target = await resolveCommandTarget(account, msg, raw)
  const phone = digits(target?.phoneNumber)
  if (!phone) throw new Error('Mention somebody, reply to their message, or provide a phone number.')
  if (controlNumbers.has(phone)) throw new Error('That number is already a primary owner/control number.')
  setPublicSudoNumber(phone, enabled)
  return {
    phoneNumber:phone,
    enabled:Boolean(enabled),
  }
}

async function resolveDirectPeer(account, msg) {
  const jid = normalizeJid(msg?.key?.remoteJid)
  if (!jid || isGroup(jid)) return ''
  return resolvePhoneJid(account, jid)
}

async function isPrivateControlContext(account, msg, authority = null) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  const sender = jidUser(authority?.senderJid || await resolveSender(account, msg))
  const peer = jidUser(await resolveDirectPeer(account, msg))
  return isPrivateOwnerDm({
    account,
    mainAccountId: destinationIdFor(),
    chat,
    group: isGroup(chat),
    senderNumber: sender,
    peerNumber: peer,
    controlNumbers,
  })
}

function invalidateGroupMetadata(accountId = '', groupJid = '') {
  for (const key of [...groupMetaCache.keys()]) {
    const [id, group] = key.split('|', 2)
    if (accountId && id !== accountId) continue
    if (groupJid && group !== normalizeJid(groupJid)) continue
    groupMetaCache.delete(key)
  }
}

async function groupMetadataCached(account, groupJid) {
  const group = normalizeJid(groupJid)
  const key = `${account.id}|${group}`
  const cached = groupMetaCache.get(key)
  if (cached && Date.now() - cached.at < GROUP_META_TTL_MS) return cached.value

  try {
    const value = await account.sock?.groupMetadata?.(group)
    if (!value) return null
    groupMetaCache.set(key, { at: Date.now(), value })
    trimTimedMap(groupMetaCache, GROUP_META_CACHE_MAX, GROUP_META_TTL_MS)
    return value
  } catch {
    groupMetaCache.delete(key)
    return null
  }
}

async function resolveGroupParticipant(account, msg, raw = '') {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) return null

  const target = await resolveCommandTarget(account, msg, raw)
  const phone = digits(target?.phoneNumber)
  if (!phone) return null

  const metadata = await groupMetadataCached(account, chat)
  const participants = Array.isArray(metadata?.participants) ? metadata.participants : []
  for (const participant of participants) {
    const jid = normalizeJid(
      typeof participant === 'string'
        ? participant
        : participant?.id || participant?.jid || participant?.lid || ''
    )
    if (!jid) continue
    const phoneJid = await resolvePhoneJid(account, jid)
    if (jidPhoneNumber(phoneJid) !== phone) continue
    return {
      phoneNumber:phone,
      jid,
      phoneJid:phoneJid || phone + '@s.whatsapp.net',
      participant,
      displayName:String(
        participant?.notify ||
        participant?.displayName ||
        participant?.name ||
        ''
      ).trim() || sharedStorage?.latestConversationSpeaker(chat, jid) || 'WhatsApp user',
    }
  }
  return null
}

function preferredJosiaAccount(fallback = null) {
  const josia = [...accounts.values()].find(candidate =>
    candidate?.enabled &&
    candidate?.connected &&
    candidate?.sock &&
    sharedStorage?.profileForAccount(candidate.id)?.id === 'josiah'
  )
  return josia || (fallback?.connected && fallback?.sock ? fallback : null)
}

function awayDuration(since) {
  const diff = Math.max(0, Date.now() - Number(since || Date.now()))
  if (diff < 60000) return Math.max(1, Math.floor(diff / 1000)) + 's'
  if (diff < 3600000) return Math.floor(diff / 60000) + 'm'
  if (diff < 86400000) return (diff / 3600000).toFixed(diff < 10 * 3600000 ? 1 : 0).replace('.0','') + 'h'
  return (diff / 86400000).toFixed(diff < 10 * 86400000 ? 1 : 0).replace('.0','') + 'd'
}

async function afkPhoneFromJid(account, jid) {
  const normalized = normalizeJid(jid)
  if (!normalized) return ''
  if (normalized.endsWith('@s.whatsapp.net')) return jidUser(normalized)
  try {
    const phoneJid = await resolvePhoneJid(account, normalized)
    return jidPhoneNumber(phoneJid) || jidUser(phoneJid)
  } catch {
    return ''
  }
}

function afkNameFor(group, jid, phone = '') {
  return String(
    sharedStorage?.latestConversationSpeaker(group, normalizeJid(jid)) ||
    phone ||
    jidUser(jid) ||
    'member'
  ).trim()
}

async function plainAfkMessage(account, group, message, text, protectedPhone = '') {
  let value = String(text || '').trim()
  for (const jid of messageMentionJids(message)) {
    const phone = await afkPhoneFromJid(account, jid)
    if (!phone || phone === protectedPhone) continue
    const name = afkNameFor(group, jid, phone)
    const escaped = [...phone].map(ch => '^$.*+?()[]{}|\\\\'.includes(ch) ? '\\\\' + ch : ch).join('')
    value = value.replace(new RegExp('@' + escaped + '(?=\\b|\\s|$|[.,!?;:])', 'g'), name)
  }
  return value
}

async function handleAfkGroupMessage(account, msg, authority, text) {
  const group = normalizeJid(msg?.key?.remoteJid)
  if (!sharedStorage || !isGroup(group) || msg?.key?.fromMe || !authority?.senderNumber) return false

  const senderPhone = authority.senderNumber
  const senderAfk = getAfk(sharedStorage, group, senderPhone)
  const prefix = settings.publicPrefix || DEFAULT_PUBLIC_PREFIX
  const isAfkCommand = String(text || '').trim().toLowerCase().startsWith((prefix + 'afk').toLowerCase())

  if (senderAfk && !isAfkCommand) {
    const state = clearAfk(sharedStorage, group, senderPhone)
    const events = Array.isArray(state?.events) ? state.events : []
    const senderJid = normalizeJid(await resolveSender(account, msg)) || normalizeJid(senderPhone + '@s.whatsapp.net')
    const mentions = [senderJid]
    const seen = new Set()
    const lines = []

    for (const event of events.slice(-12)) {
      const phone = digits(event?.senderPhone)
      if (!phone) continue
      const jid = normalizeJid(phone + '@s.whatsapp.net')
      if (!seen.has(phone)) {
        mentions.push(jid)
        seen.add(phone)
      }
      const label = '@' + phone
      const body = String(event?.text || '').trim() || (event?.kind === 'reply' ? 'replied to one of your messages' : 'mentioned you')
      lines.push('• ' + label + ': ' + body.slice(0,500))
    }

    const josia = preferredJosiaAccount(account)
    if (josia?.sock) {
      const body = [
        '◇ *Josia*',
        '@' + senderPhone + ', you’re back. You were away for *' + awayDuration(state?.since) + '*.',
        events.length ? 'While you were away:' : 'Nobody called for you while you were away.',
        ...lines,
        events.length > 12 ? '• …and ' + (events.length - 12) + ' more.' : '',
      ].filter(Boolean).join('\n')
      await sendText(josia.sock, group, body, { mentions:[...new Set(mentions)] }).catch(() => {})
    }
    await recordActivity('group.afk-returned', { group, phone:senderPhone, events:events.length })
  }

  const targets = new Map()
  for (const jid of messageMentionJids(msg.message)) {
    const phone = await afkPhoneFromJid(account, jid)
    if (phone && phone !== senderPhone && getAfk(sharedStorage, group, phone)) {
      targets.set(phone, { jid:normalizeJid(jid), kind:'mention' })
    }
  }

  const quotedJid = quotedParticipantJid(msg.message)
  if (quotedJid) {
    const phone = await afkPhoneFromJid(account, quotedJid)
    if (phone && phone !== senderPhone && getAfk(sharedStorage, group, phone) && !targets.has(phone)) {
      targets.set(phone, { jid:quotedJid, kind:'reply' })
    }
  }

  if (!targets.size) return Boolean(senderAfk && !isAfkCommand)

  const senderName = String(msg?.pushName || '').trim() || afkNameFor(group, await resolveSender(account,msg), senderPhone)
  const josia = preferredJosiaAccount(account)
  for (const [phone, target] of targets) {
    const state = getAfk(sharedStorage, group, phone)
    if (!state) continue
    const safeText = await plainAfkMessage(account, group, msg.message, text, phone)
    addAfkEvent(sharedStorage, group, phone, {
      senderPhone,
      senderName,
      text:safeText,
      kind:target.kind,
    })

    if (josia?.sock) {
      const targetJid = normalizeJid(target.jid) || normalizeJid(phone + '@s.whatsapp.net')
      const notice = [
        '◇ *Josia*',
        '@' + phone + ' is away right now.',
        state.reason ? 'Reason: ' + state.reason : '',
        'Away for: ' + awayDuration(state.since),
        safeText ? '' : '',
        safeText ? 'Message: ' + safeText.slice(0,700) : '',
      ].filter(Boolean).join('\n')
      await sendText(josia.sock, group, notice, { mentions:[targetJid] }).catch(() => {})
    }
  }
  return false
}

async function groupParticipantAction(account, msg, raw, action) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) throw new Error('This one is for groups.')
  if (!(await isBotGroupAdminContext(account, msg))) throw new Error('Night needs to be a group admin for that.')

  const target = await resolveGroupParticipant(account, msg, raw)
  if (!target) throw new Error('Mention somebody or reply to their message.')
  if (await jidBelongsToAccount(account, target.jid)) throw new Error('Night cannot apply that action to itself.')

  await account.sock.groupParticipantsUpdate(chat, [target.jid], String(action || ''))
  invalidateGroupMetadata(account.id, chat)
  return target
}

async function setGroupAnnouncement(account, msg, closed) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) throw new Error('This one is for groups.')
  if (!(await isBotGroupAdminContext(account, msg))) throw new Error('Night needs to be a group admin for that.')
  await account.sock.groupSettingUpdate(chat, closed ? 'announcement' : 'not_announcement')
  invalidateGroupMetadata(account.id, chat)
  return { closed:Boolean(closed) }
}

async function groupInviteLink(account, msg) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) throw new Error('This one is for groups.')
  if (!(await isBotGroupAdminContext(account, msg))) throw new Error('Night needs to be a group admin to read the invite link.')
  const code = await account.sock.groupInviteCode(chat)
  if (!code) throw new Error('I could not get the group invite link.')
  return 'https://chat.whatsapp.com/' + code
}

async function hiddenTag(account, msg, text = '') {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) throw new Error('This one is for groups.')
  const metadata = await groupMetadataCached(account, chat)
  const mentions = (metadata?.participants || []).map(item => normalizeJid(
    typeof item === 'string' ? item : item?.id || item?.jid || item?.lid || ''
  )).filter(Boolean)
  return account.sock.sendMessage(chat, {
    text:String(text || '').trim() || '📢',
    mentions,
  }, { quoted:msg })
}

async function groupStatusSnapshot(account, msg) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) return null
  const metadata = await groupMetadataCached(account, chat)
  if (!metadata) return null
  const participants = Array.isArray(metadata.participants) ? metadata.participants : []
  const admins = participants.filter(item => item?.admin === 'admin' || item?.admin === 'superadmin')
  const policy = groupPolicy(sharedStorage, chat)
  return {
    subject:String(metadata.subject || 'Group'),
    participants:participants.length,
    admins:admins.length,
    announcement:Boolean(metadata.announce),
    policy,
  }
}

async function deletePolicyMessage(account, msg) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !msg?.key?.id || !account?.sock) return false
  suppressAntiDelete(chat, msg.key.id)
  await account.sock.sendMessage(chat, { delete:msg.key })
  return true
}

async function enforceCurrentGroupPolicy(account, msg, authority) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat) || msg?.key?.fromMe || !sharedStorage) return { handled:false }
  return enforceGroupMessage({
    storage:sharedStorage,
    msg,
    senderPhone:authority.senderNumber,
    senderIsAdmin:await isGroupAdminContext(account, msg),
    botIsAdmin:await isBotGroupAdminContext(account, msg),
    resolvePhoneJid:jid => resolvePhoneJid(account, jid),
    deleteMessage:target => deletePolicyMessage(account, target),
    resendQuoted:(text, mentions, quoted) => sendText(account.sock, chat, text, { quoted, mentions }),
    replyMessage:text => sendText(account.sock, chat, text, { quoted:msg }),
    warnMember:trigger => addWarning(sharedStorage, chat, authority.senderNumber, 'Filter: ' + trigger, 'Night'),
    record:recordActivity,
  })
}

async function handleGroupMemberPolicyEvent(account, update) {
  const group = normalizeJid(update?.id)
  const action = String(update?.action || '').toLowerCase()
  if (!group || !['add','remove'].includes(action) || !sharedStorage || !account?.sock) return false

  const policy = groupPolicy(sharedStorage, group)
  if (action === 'add' && !policy.welcome && !policy.aiGreet && !String(policy.rulesText || '').trim()) return false
  if (action === 'remove' && !policy.goodbye) return false

  const rawParticipants = Array.isArray(update?.participants) ? update.participants : []
  const mentions = []
  for (const participant of rawParticipants) {
    const rawJid = normalizeJid(typeof participant === 'string' ? participant : participant?.id || participant?.jid || participant?.lid || '')
    if (!rawJid) continue

    let nightAccount = false
    for (const candidate of accounts.values()) {
      if (await jidBelongsToAccount(candidate, rawJid)) {
        nightAccount = true
        break
      }
    }
    if (nightAccount) continue

    const phoneJid = await resolvePhoneJid(account, rawJid)
    mentions.push(phoneJid || rawJid)
  }
  if (!mentions.length) return false

  const dedupeKey = [group, action, ...mentions.map(normalizeJid).sort()].join('|')
  const prior = sharedStorage.sharedGet('group-member-event-dedupe', dedupeKey)
  if (prior?.at && Date.now() - Number(prior.at) < 15000) return false
  sharedStorage.sharedSet('group-member-event-dedupe', dedupeKey, { at:Date.now(), account:account.id })

  const metadata = await groupMetadataCached(account, group)
  const groupName = String(metadata?.subject || 'the group').trim()

  if (action === 'add') {
    const rulesText = String(policy.rulesText || '').trim()
    for (const memberJid of mentions) {
      let greeting = ''

      if (policy.aiGreet) {
        const assistant = assistantForAccount(account)
        if (assistant) {
          const mentionText = '@' + jidUser(memberJid)
          try {
            const result = await assistant.answer({
              chatJid:group,
              text:'Write one short, friendly welcome for ' + mentionText + ' joining "' + groupName + '". Keep that @mention exactly unchanged. Do not explain.',
              senderName:'Night',
              groupName,
              isGroup:true,
              groupPersonalities:await groupPersonalityPresence(account, group),
            })
            greeting = String(result?.text || '').trim()
          } catch {}
        }
      }

      if (!greeting && policy.welcome) {
        greeting = renderGroupTemplate(policy.welcomeText, { groupName, mentions:[memberJid] })
      }

      const rulesBlock = rulesText
        ? (greeting
          ? '📜 *Group Rules*\n' + rulesText
          : '@' + jidUser(memberJid) + ', these are the rules for *' + groupName + '*:\n\n' + rulesText)
        : ''

      const body = [greeting, rulesBlock].filter(Boolean).join('\n\n')
      if (body) await sendText(account.sock, group, body, { mentions:[memberJid] })
    }

    await recordActivity('group.member-welcomed', {
      account:account.id,
      group,
      count:mentions.length,
      ai:policy.aiGreet,
      rules:Boolean(rulesText),
    })
    return true
  }

  const text = renderGroupTemplate(policy.goodbyeText, { groupName, mentions })
  if (!text) return false
  await sendText(account.sock, group, text, { mentions })
  await recordActivity('group.member-goodbye', {
    account:account.id,
    group,
    count:mentions.length,
  })
  return true
}

async function publicUserProfile(account, msg, phoneNumber) {
  const phone = digits(phoneNumber)
  if (!phone) return null

  const chat = normalizeJid(msg?.key?.remoteJid)
  const senderNumber = jidUser(await resolveSender(account, msg))
  let mentionJid = phone + '@s.whatsapp.net'
  let displayName = senderNumber === phone
    ? String(msg?.pushName || '').trim()
    : ''
  let role = ''
  let groupMember = false

  if (isGroup(chat)) {
    const metadata = await groupMetadataCached(account, chat)
    const participants = Array.isArray(metadata?.participants) ? metadata.participants : []

    for (const participant of participants) {
      const jid = normalizeJid(
        typeof participant === 'string'
          ? participant
          : participant?.id || participant?.jid || participant?.lid || ''
      )
      if (!jid) continue

      const phoneJid = (jid.endsWith('@lid') || jid.endsWith('@hosted.lid'))
        ? await resolvePhoneJid(account, jid)
        : jid
      const participantPhone = jidPhoneNumber(phoneJid)
      if (participantPhone !== phone) continue

      mentionJid = jid
      groupMember = true
      role = participant?.admin === 'superadmin'
        ? 'Group owner'
        : participant?.admin === 'admin'
          ? 'Admin'
          : 'Member'

      if (!displayName) {
        displayName = String(
          participant?.notify ||
          participant?.displayName ||
          participant?.name ||
          ''
        ).trim() || sharedStorage?.latestConversationSpeaker(chat, jid) || ''
      }
      break
    }
  }

  if (!displayName) displayName = 'WhatsApp user'

  let photoUrl = ''
  if (account?.sock?.profilePictureUrl) {
    const candidates = [...new Set([
      mentionJid,
      phone + '@s.whatsapp.net',
    ].filter(Boolean))]
    for (const jid of candidates) {
      try {
        photoUrl = String(await account.sock.profilePictureUrl(jid, 'image') || '').trim()
        if (photoUrl) break
      } catch {}
    }
  }

  const library = sharedStorage?.librarySummary(phone) || {
    total:0,
    anime:0,
    manga:0,
    movie:0,
    tv:0,
    watching:0,
  }

  return {
    phoneNumber:phone,
    displayName,
    mentionJid,
    groupMember,
    role,
    photoUrl,
    library,
  }
}

async function isGroupAdminContext(account, msg) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) return false
  const rawSender = normalizeJid(msg?.key?.participant || msg?.participant)
  if (!rawSender) return false

  const metadata = await groupMetadataCached(account, chat)
  const participants = metadata?.participants || []
  let participant = participants.find(item => normalizeJid(item?.id) === rawSender)

  if (!participant) {
    const senderPhone = jidUser(await resolvePhoneJid(account, rawSender))
    participant = participants.find(item => jidPhoneNumber(item?.id) === senderPhone)
  }

  return participant?.admin === 'admin' || participant?.admin === 'superadmin'
}

async function isBotGroupAdminContext(account, msg) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!isGroup(chat)) return false
  const metadata = await groupMetadataCached(account, chat)
  const participants = metadata?.participants || []

  for (const participant of participants) {
    const jid = typeof participant === 'string'
      ? participant
      : participant?.id || participant?.jid || participant?.lid || ''
    if (!jid || !(await jidBelongsToAccount(account, jid))) continue
    return participant?.admin === 'admin' || participant?.admin === 'superadmin'
  }
  return false
}

async function accountIsMemberOfGroup(account, groupJid) {
  if (!account?.connected || !account?.sock) return false
  const metadata = await groupMetadataCached(account, groupJid)
  return Boolean(metadata)
}

async function withRouteLock(key, fn) {
  const previous = routeLocks.get(key) || Promise.resolve()
  const current = previous.catch(() => {}).then(fn)
  routeLocks.set(key, current)
  try {
    return await current
  } finally {
    if (routeLocks.get(key) === current) routeLocks.delete(key)
  }
}

async function shouldExecutePublicCommand(account, msg, command) {
  if (!sharedStorage) return account.id === 'A'
  const capability = String(command?.capability || 'general').trim().toLowerCase() || 'general'
  const chat = normalizeJid(msg?.key?.remoteJid)

  if (!isGroup(chat)) {
    return canExecuteDirect({
      accountId: account.id,
      capability,
      scoreFor: (id, cap) => sharedStorage.capabilityScore(id, cap),
    })
  }

  return withRouteLock(`${chat}|${capability}`, async () => {
    const selected = await chooseGroupExecutor({
      groupJid: chat,
      capability,
      accounts: [...accounts.values()],
      isMember: accountCandidate => accountIsMemberOfGroup(accountCandidate, chat),
      scoreFor: (id, cap) => sharedStorage.capabilityScore(id, cap),
      getSticky: (group, cap) => sharedStorage.getGroupRoute(group, cap),
      setSticky: (group, cap, id) => {
        const previous = sharedStorage.getGroupRoute(group, cap)
        sharedStorage.setGroupRoute(group, cap, id)
        if (previous !== id) {
          recordActivity('routing.group-selected', {
            group,
            capability: cap,
            account: id,
            previous: previous || null,
          }).catch(() => {})
        }
      },
    })
    return selected === account.id
  })
}

function commandReplySessionKey(account, msg, authority = {}) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  const user = String(
    authority.senderNumber ||
    jidUser(msg?.key?.participant) ||
    jidUser(chat) ||
    'unknown'
  )
  return [account?.id || '', chat, user].join('|')
}

function readCommandReplySession(account, msg, authority = {}) {
  if (!sharedStorage) return null
  const key = commandReplySessionKey(account, msg, authority)
  const session = sharedStorage.sharedGet('command-reply-session', key)
  if (!session) return null
  if (Number(session.expiresAt || 0) && Date.now() > Number(session.expiresAt)) {
    sharedStorage.sharedDelete('command-reply-session', key)
    return null
  }
  return session
}

function writeCommandReplySession(account, msg, authority = {}, session = null) {
  if (!sharedStorage) return null
  const key = commandReplySessionKey(account, msg, authority)
  if (!session) {
    sharedStorage.sharedDelete('command-reply-session', key)
    return null
  }
  return sharedStorage.sharedSet('command-reply-session', key, {
    ...session,
    updatedAt:Date.now(),
    expiresAt:Number(session.expiresAt || 0) || Date.now() + 30 * 60000,
  })
}

async function sendCommandReply(account, msg, value) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !account?.sock) throw new Error('Command reply target is unavailable')
  return sendText(account.sock, chat, value)
}

function commandUi(account, msg, prefix = settings.publicPrefix || DEFAULT_PUBLIC_PREFIX) {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !account?.sock) throw new Error('Command UI target is unavailable')
  return createWhatsAppUi({
    sock:account.sock,
    chat,
    quoted:msg,
    prefix,
  })
}

async function sendCommandList(account, msg, options = {}) {
  return commandUi(account, msg).singleSelect(options)
}

async function sendCommandImageDataUrl(account, msg, dataUrl, caption = '') {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !account?.sock) throw new Error('Command reply target is unavailable')
  return sendImageDataUrl(account.sock, chat, dataUrl, caption)
}

async function sendCommandImageFile(account, msg, file, caption = '') {
  const chat = normalizeJid(msg?.key?.remoteJid)
  if (!chat || !account?.sock) throw new Error('Command reply target is unavailable')
  const image = await readProfileAsset(String(file || ''))
  return account.sock.sendMessage(chat, {
    image,
    caption:String(caption || ''),
  }, { quoted:msg })
}

async function prepareCommandMediaPayload(payload) {
  if (!payload || typeof payload !== 'object') return payload
  const next = { ...payload }
  for (const kind of ['image', 'video', 'audio', 'document', 'sticker']) {
    const media = next[kind]
    if (!media || typeof media !== 'object' || typeof media.url !== 'string') continue
    const url = String(media.url)
    if (!url.startsWith('/')) continue
    try {
      next[kind] = await readFile(url)
    } catch (error) {
      throw new Error('Unable to read local ' + kind + ' media: ' + (error?.message || error))
    }
  }
  return next
}

async function describe(account, msg) {
  const sender = jidUser(await resolveSender(account, msg)) || 'unknown'
  const chat = normalizeJid(msg?.key?.remoteJid)
  const accountLine = `Account: ${account.displayName || 'Account'} [${account.id}]\n`
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
  const destinationId = destinationIdFor()
  const dest = destinationAccount()
  if (!dest?.enabled) throw new Error(`Destination Account ${destinationId} is not configured`)
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
      await recordActivity('cc.deleted-recovery', {
        sourceAccount: account.id,
        destinationAccount: destinationIdFor(),
      })
    } catch (e) { console.error(`[${account.id}] delete recovery:`, e?.message || e) }
  }
}

async function onMessages(account, { messages, type }) {
  for (const msg of messages || []) {
    try {
      if (!msg?.message || !msg?.key?.id) continue
      const chat = normalizeJid(msg.key.remoteJid)
      const text = commandText(msg.message)
      const authority = await authorityContext(account, msg)
      const controller = authority.isSupremeOwner
      const privateControl = await isPrivateControlContext(account, msg, authority)

      remember(account, msg)
      rememberConversation(account, msg, authority)

      const publicPrefix = settings.publicPrefix || DEFAULT_PUBLIC_PREFIX

      const moderation = await enforceCurrentGroupPolicy(account, msg, authority)
      if (moderation.handled) continue

      await handleAfkGroupMessage(account, msg, authority, text)

      const ui = commandUi(account, msg, publicPrefix)
      const pendingReply = readCommandReplySession(account, msg, authority)
      const isExplicitCommand = Boolean(publicPrefix && String(text || '').trim().startsWith(publicPrefix))
      const ludoRecord = sharedStorage?.sharedGet('ludo-game', chat) || null
      const consumeLudoInput = Boolean(
        !isExplicitCommand &&
        ludoRecordAcceptsInput(ludoRecord, authority.senderNumber, text)
      )
      const checkersRecord = sharedStorage?.sharedGet('checkers-game', chat) || null
      const consumeCheckersInput = Boolean(
        !isExplicitCommand &&
        !consumeLudoInput &&
        checkersRecordAcceptsInput(checkersRecord, authority.senderNumber, text)
      )
      const ticTacToeRecord = sharedStorage?.sharedGet('tictactoe-game', chat) || null
      const consumeTicTacToeInput = Boolean(
        !isExplicitCommand &&
        !consumeLudoInput &&
        !consumeCheckersInput &&
        ticTacToeRecordAcceptsInput(ticTacToeRecord, authority.senderNumber, text)
      )
      const chessRecord = sharedStorage?.sharedGet('chess-game', chat) || null
      const consumeChessInput = Boolean(
        !isExplicitCommand &&
        !consumeLudoInput &&
        !consumeCheckersInput &&
        !consumeTicTacToeInput &&
        chessRecordAcceptsInput(chessRecord, authority.senderNumber, text)
      )
      const consumePendingReply = Boolean(
        !isExplicitCommand &&
        !consumeLudoInput &&
        !consumeCheckersInput &&
        !consumeTicTacToeInput &&
        !consumeChessInput &&
        pendingReply?.command &&
        (
          (pendingReply?.kind === 'number-selection' && looksLikeNumberSelection(text)) ||
          (pendingReply?.kind === 'album-selection' && (
            looksLikeNumberSelection(text) ||
            String(text || '').trim().toLowerCase() === 'all'
          )) ||
          (pendingReply?.kind === 'choice-selection' && String(text || '').trim())
        )
      )

      const dispatchText = consumeLudoInput
        ? `${publicPrefix}ludo ~input`
        : consumeCheckersInput
          ? `${publicPrefix}checkers ~input`
          : consumeTicTacToeInput
            ? `${publicPrefix}ttt ~input`
            : consumeChessInput
              ? `${publicPrefix}chess ~input`
              : consumePendingReply
                ? pendingReply.kind === 'album-selection'
                  ? `${publicPrefix}${pendingReply.command} ~selection`
                  : pendingReply.kind === 'choice-selection'
                    ? `${publicPrefix}${pendingReply.command} ~choice`
                    : `${publicPrefix}${pendingReply.command} ~numbers`
                : text

      const commandHandled = await dispatchNamespacedCommand({
        privateRegistry: privateCommandRegistry,
        publicRegistry: publicCommandRegistry,
        rawText: dispatchText,
        context: {
          account,
          message: msg,
          controller,
          privateControl,
          isSupremeOwner: authority.isSupremeOwner,
          isSudo: authority.isSudo,
          isPublicOwner: authority.isPublicOwner,
          isSessionOwner: authority.isSessionOwner,
          isGroupAdmin: () => isGroupAdminContext(account, msg),
          isBotGroupAdmin: () => isBotGroupAdminContext(account, msg),
          deleteRecentMessages: count => deleteRecentMessages(account, msg, count),
          groupParticipantAction: (raw, action) => groupParticipantAction(account, msg, raw, action),
          groupSetAnnouncement: closed => setGroupAnnouncement(account, msg, closed),
          groupInviteLink: () => groupInviteLink(account, msg),
          groupHiddenTag: text => hiddenTag(account, msg, text),
          groupStatusSnapshot: () => groupStatusSnapshot(account, msg),
          groupPolicyGet: () => groupPolicy(sharedStorage, chat),
          groupPolicySet: patch => setGroupPolicy(sharedStorage, chat, patch),
          afkSet: reason => setAfk(sharedStorage, chat, authority.senderNumber, {
            reason,
            displayName:String(msg?.pushName || '').trim(),
          }),
          afkGet: () => getAfk(sharedStorage, chat, authority.senderNumber),
          afkClear: () => clearAfk(sharedStorage, chat, authority.senderNumber),
          groupWarn: async (raw, reason = '') => {
            const target = await resolveGroupParticipant(account, msg, raw)
            if (!target) throw new Error('Mention somebody or reply to their message.')
            return { target, state:addWarning(sharedStorage, chat, target.phoneNumber, reason, authority.senderNumber) }
          },
          groupWarnClear: async raw => {
            const target = await resolveGroupParticipant(account, msg, raw)
            if (!target) throw new Error('Mention somebody or reply to their message.')
            return { target, cleared:clearWarnings(sharedStorage, chat, target.phoneNumber) }
          },
          groupWarningState: async raw => {
            const target = await resolveGroupParticipant(account, msg, raw)
            if (!target) throw new Error('Mention somebody or reply to their message.')
            return { target, state:warningState(sharedStorage, chat, target.phoneNumber) }
          },
          setMentionMute: async (raw, enabled) => {
            const target = await resolveGroupParticipant(account, msg, raw)
            if (!target) throw new Error('Mention somebody or reply to their message.')
            if (target.phoneNumber === authority.senderNumber) throw new Error('Choose somebody else.')
            setUserMentionMute(sharedStorage, chat, authority.senderNumber, target.phoneNumber, enabled)
            return target
          },
          shouldExecutePublicCommand: command => shouldExecutePublicCommand(account, msg, command),
          settings,
          publicPrefix,
          rawCommandText:text,
          commandReplyInput: (consumePendingReply || consumeLudoInput || consumeCheckersInput || consumeTicTacToeInput || consumeChessInput) ? String(text || '').trim() : '',
          getCommandReplySession: () => readCommandReplySession(account, msg, authority),
          setCommandReplySession: session => writeCommandReplySession(account, msg, authority, session),
          clearCommandReplySession: () => writeCommandReplySession(account, msg, authority, null),
          publicCommandsEnabled: settings.publicCommandsEnabled !== false,
          botProfile: sharedStorage?.profileForAccount(account.id) || { id:'unassigned', displayName:'Unassigned', universal:false, basePriority:0 },
          userKey: authority.senderNumber || '',
          groupKey: isGroup(chat) ? chat : '',
          shared: {
            get: (namespace, key) => sharedStorage?.sharedGet(namespace, key) ?? null,
            set: (namespace, key, value) => sharedStorage?.sharedSet(namespace, key, value),
            delete: (namespace, key) => sharedStorage?.sharedDelete(namespace, key) || 0,
          },
          reply: async value => sendCommandReply(account, msg, value),
          ui,
          replyList: options => ui.singleSelect(options),
          replySelectors: options => ui.native(options),
          replyInstant: options => ui.instantReplies(options),
          replyInteractive: options => ui.interactive(options),
          progress: initial => startProgress(account.sock, chat, initial, { quoted:msg }),
          scheduleAdd: ({ kind = 'reminder', text = '', dueAt, meta = {} }) => addScheduledTask(sharedStorage, {
            userKey:authority.senderNumber,
            kind,
            text,
            dueAt,
            meta,
          }),
          scheduleList: () => listScheduledTasks(sharedStorage, authority.senderNumber),
          scheduleRemove: id => removeScheduledTask(sharedStorage, authority.senderNumber, id),
          parseDuration,
          formatDue,
          sendPoll: async ({ question, options, selectableCount = 1 }) => account.sock.sendMessage(chat, {
            poll:{
              name:String(question || '').trim(),
              values:(options || []).map(value => String(value || '').trim()).filter(Boolean).slice(0, 12),
              selectableCount:Math.max(1, Math.min(Number(selectableCount) || 1, (options || []).length || 1)),
            },
          }, { quoted:msg }),
          smartComplete: options => smartAI.complete(options),
          latestRelease: (query, type = '') => resolveLatestRelease(query, type),
          currentChatLabel: async () => {
            if (!isGroup(chat)) return 'DM'
            const metadata = await groupMetadataCached(account, chat)
            return String(metadata?.subject || 'Group')
          },
          summarizeGroup: async hours => {
            const assistant = assistantForAccount(account)
            if (!assistant || !isGroup(chat)) return { ok:false, text:'This one is for groups.' }
            return assistant.summarize({
              chatJid:chat,
              hours,
              groupName:await assistantGroupName(account, chat),
            })
          },
          sendImageDataUrl: async (dataUrl, caption) => sendCommandImageDataUrl(account, msg, dataUrl, caption),
          sendImageFile: async (file, caption) => sendCommandImageFile(account, msg, file, caption),
          sendImageUrl: async (url, caption = '') => account.sock.sendMessage(chat, {
            image:{ url:String(url || '') },
            caption:String(caption || ''),
          }, { quoted:msg }),
          resolveCommandTarget: raw => resolveCommandTarget(account, msg, raw),
          getPublicUserProfile: phoneNumber => publicUserProfile(account, msg, phoneNumber),
          resolveAccountId,
          createAccount,
          renameAccount,
          pairAccount,
          repairAccount,
          reconnectAccount,
          disconnectAccount,
          removeAccount,
          preparePairTarget: args => preparePairTarget(account, msg, args),
          waitForPairing,
          setSetting,
          setPublicPrefix,
          setDestination,
          reloadCommands,
          reloadCommandsDetailed,
          reloadModule,
          reloadSettings: () => reloadSettings(false),
          activity,
          botProfiles: () => sharedStorage?.listProfiles() || [],
          botAssignments: () => sharedStorage?.assignments() || [],
          createBotProfile: (id, name) => sharedStorage.createProfile(id, name),
          assignBotProfile: (accountId, profileId) => sharedStorage.assignProfile(accountId, profileId),
          setBotCapability: (profileId, capability, priority) => sharedStorage.setCapability(profileId, capability, priority),
          setBotSpecialty: (profileId, capability, enabled) => sharedStorage.setSpecialty(profileId, capability, enabled),
          setBotProfileMode: (profileId, universal) => sharedStorage.setProfileMode(profileId, universal),
          groupRoutes: group => sharedStorage?.listGroupRoutes(group) || [],
          resetGroupRoutes: group => sharedStorage?.clearGroupRoutes(group) || 0,
          storageStats: () => sharedStorage?.stats() || { messages:0, profiles:0, routes:0, sharedItems:0, sourceDefaults:0, deliveryDefaults:0 },
          listSources: capability => sourceRegistry?.list(capability) || [],
          sourceMode: capability => sourceRegistry?.mode(capability) || 'user-choice',
          getSourceDefault: capability => sourceRegistry?.getDefault(authority.senderNumber, capability) || '',
          setSourceDefault: (capability, sourceId) => sourceRegistry.setDefault(authority.senderNumber, capability, sourceId),
          clearSourceDefault: capability => sourceRegistry?.clearDefault(authority.senderNumber, capability) || 0,
          getDeliveryDefault: capability => sharedStorage?.getDeliveryDefault(authority.senderNumber, capability) || null,
          setDeliveryDefault: (capability, quality, delivery) => sharedStorage.setDeliveryDefault(authority.senderNumber, capability, quality, delivery),
          clearDeliveryDefault: capability => sharedStorage?.clearDeliveryDefault(authority.senderNumber, capability) || 0,
          sourceBrand: capability => sharedStorage?.brandForCapability(capability) || 'Main',
          appVersion:APP_VERSION,
          broadcastNight: text => broadcastNightGroups(account, text),
          sudoList: () => publicSudoNumbers(),
          sudoSet: (raw, enabled) => setPublicSudoTarget(account, msg, raw, enabled),
          ownerContact: () => {
            const main = accountRegistry.main()
            const phoneNumber = digits(main?.phoneNumber || OWNER_NUMBER)
            const displayName = String(
              process.env.OWNER_NAME ||
              process.env.BOT_OWNER ||
              main?.displayName ||
              'Owner'
            ).trim().slice(0, 64) || 'Owner'
            return { phoneNumber, displayName }
          },
          libraryGet: itemKey => sharedStorage?.getLibraryItem(authority.senderNumber, itemKey) || null,
          libraryBySlot: slot => sharedStorage?.libraryItemBySlot(authority.senderNumber, slot) || null,
          libraryList: mediaType => sharedStorage?.listLibraryItems(authority.senderNumber, mediaType) || [],
          libraryPut: item => sharedStorage?.putLibraryItem(authority.senderNumber, item) || null,
          libraryRemove: itemKey => sharedStorage?.removeLibraryItem(authority.senderNumber, itemKey) || 0,
          librarySetWatch: (itemKey, enabled) => sharedStorage?.setLibraryWatch(authority.senderNumber, itemKey, enabled) || null,
          libraryPrimeWatch: item => releaseWatcher?.prime(item) || Promise.resolve({ ok:false, reason:'unavailable' }),
          resolveAnimeTitles: query => aniListResolver.resolve(query, 'ANIME'),
          resolveAniListTitles: (query, type = 'ANIME') => aniListResolver.resolve(query, type),
          resolveAniListMedia: (id, type = 'ANIME') => aniListResolver.getMedia(id, type),
          resolveTmdbTitles: (query, type = 'movie') => tmdbResolver.search(query, type),
          browseTmdbMedia: (type = 'movie') => tmdbResolver.browse(type),
          resolveTmdbMedia: (id, type = 'movie') => tmdbResolver.details(id, type),
          resolveTmdbSeason: (id, seasonNumber) => tmdbResolver.seasonDetails(id, seasonNumber),
          resolveBookScreens: input => adaptationResolver.bookToScreen(input),
          resolveScreenBooks: input => adaptationResolver.screenToBooks(input),
          resolveScreenCounterparts: input => adaptationResolver.screenCounterparts(input),
          executeSource: ({ capability, explicitSource = '', pinnedSource = '', excludedSources = [], payload = {} }) => sourceRegistry.execute({
            capability,
            userKey: authority.senderNumber,
            explicitSource,
            pinnedSource,
            excludedSources,
            payload,
            context: {
              accountId: account.id,
              chat,
              userKey: authority.senderNumber,
              reply: value => sendCommandReply(account, msg, value),
              ui,
              replyList: options => ui.singleSelect(options),
              replySelectors: options => ui.native(options),
              resolveTmdbTitles: (query, type = 'movie') => tmdbResolver.search(query, type),
              browseTmdbMedia: (type = 'movie') => tmdbResolver.browse(type),
              resolveTmdbMedia: (id, type = 'movie') => tmdbResolver.details(id, type),
              resolveTmdbSeason: (id, seasonNumber) => tmdbResolver.seasonDetails(id, seasonNumber),
              progress: initial => startProgress(account.sock, chat, initial, { quoted:msg }),
              send: async payload => account.sock.sendMessage(
                chat,
                await prepareCommandMediaPayload(payload),
                { quoted:msg },
              ),
            },
          }),
          requestRestart,
          statusText,
          diagnostics: commandDiagnostics,
        },
      })
      if (commandHandled) continue

      if (await handleProfileAssistant(account, msg, authority, text)) continue

      const vo = !msg.key.fromMe && futureproof(msg.message)

      if (settings.autoCc && vo) {
        const ak = cacheKey(account.id, msg)
        if (!handledAuto.has(ak)) {
          if (account.id !== destinationIdFor()) await sendInbox(account, { text: `📥 Auto CC\n${await describe(account, msg)}` })
          await sendInbox(account, { forward: unlocked(msg), force: true })
          handledAuto.set(ak, Date.now())
          await recordActivity('cc.forwarded', {
            sourceAccount: account.id,
            destinationAccount: destinationIdFor(),
            mode: 'auto',
          })
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

      if (!controller || account.id !== destinationIdFor()) {
        await sendInbox(account, { text: `↩️ V1 reply detected\n${await describe(account, msg)}` })
      }
      await sendInbox(account, { forward: unlocked(source), force: true })
      handledReply.set(rk, Date.now())
      await recordActivity('cc.forwarded', {
        sourceAccount: account.id,
        destinationAccount: destinationIdFor(),
        mode: 'reply',
      })
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
  try { await a.credSave } catch {}
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

  sock.ev.on('creds.update', () => {
    if (generation !== account.generation) return
    account.credSave = account.credSave
      .catch(() => {})
      .then(saveCreds)
      .catch(error => console.error(`[${account.id}] credential save:`, error?.message || error))
  })
  sock.ev.on('messages.upsert', upsert => { if (generation === account.generation) onMessages(account, upsert) })
  sock.ev.on('messages.update', updates => { if (generation === account.generation) onDelete(account, updates) })
  sock.ev.on('groups.update', updates => {
    if (generation !== account.generation) return
    for (const update of updates || []) invalidateGroupMetadata(account.id, update?.id)
  })
  sock.ev.on('group-participants.update', update => {
    if (generation !== account.generation) return
    invalidateGroupMetadata(account.id, update?.id)
    handleProfileGroupIntro(account, update).catch(error => {
      console.error(`[${account.id}] group intro:`, error?.message || error)
    })
  })
  sock.ev.on('connection.update', async update => {
    if (generation !== account.generation || sock !== account.sock) return
    if (!state.creds.registered && update.qr) {
      account.lastQr = update.qr
      await makePairOutput(account)
      return
    }
    if (update.connection === 'open') {
      const authenticatedNumber = jidPhoneNumber(sock.user?.id)
      if (authenticatedNumber && account.number && authenticatedNumber !== account.number) {
        account.invalid = true
        account.pairingMode = ''
        account.pairingError = `Authenticated WhatsApp account ${masked(authenticatedNumber)} does not match configured ${masked(account.number)}. Use Re-pair.`
        await recordActivity('account.identity-mismatch', {
          account: account.id,
          displayName: account.displayName,
          expectedNumberMasked: masked(account.number),
          authenticatedNumberMasked: masked(authenticatedNumber),
        })
        await closeAccount(account)
        return
      }
      account.connected = true
      invalidateGroupMetadata(account.id)
      account.registered = true
      account.invalid = false
      account.reconnectAttempts = 0
      account.pairingMode = ''
      account.pairingCode = ''
      account.pairingQr = ''
      account.pairingError = ''
      account.lastQr = ''
      console.log(`[${account.id}] connected as ${sock.user?.id || account.number}`)
      await recordActivity('account.connected', {
        account: account.id,
        displayName: account.displayName,
      })
      return
    }
    if (update.connection !== 'close') return
    account.connected = false
    invalidateGroupMetadata(account.id)
    account.sock = null
    const code = update.lastDisconnect?.error?.output?.statusCode
    await recordActivity('account.disconnected', {
      account: account.id,
      displayName: account.displayName,
      reasonCode: code ?? null,
    })
    const policy = classifyDisconnect(code)
    if (policy.action === 'repair') {
      account.invalid = true
      account.pairingMode = ''
      account.pairingError = policy.message
      return
    }
    if (policy.action === 'halt') {
      account.pairingMode = ''
      account.pairingError = policy.message
      return
    }
    account.reconnectAttempts += 1
    const delayMs = policy.delayMs ?? reconnectDelay(account.reconnectAttempts)
    clearTimeout(account.reconnectTimer)
    account.reconnectTimer = setTimeout(() => {
      if (generation !== account.generation) return
      startAccount(account).catch(e => console.error(`[${account.id}] reconnect:`, e?.message || e))
    }, delayMs)
    account.reconnectTimer.unref?.()
  })
}

function requireAccount(id) {
  const resolved = resolveAccountId(id)
  const a = resolved ? accounts.get(resolved) : null
  if (!a?.enabled) throw new Error(`Account ${id} is not configured`)
  return a
}

function accountIdByPhone(phoneNumber) {
  const number = digits(phoneNumber)
  for (const account of accounts.values()) if (account.number === number) return account.id
  return ''
}

async function preparePairTarget(account, msg, args = []) {
  const first = String(args?.[0] || '').trim()
  const existingById = first ? resolveAccountId(first) : ''
  if (existingById) return { id: existingById, created: false }

  const target = await resolveCommandTarget(account, msg, first)
  if (!target.phoneNumber) throw new Error('Specify an account ID or phone number, mention somebody, or reply to their message.')
  const existingByPhone = accountIdByPhone(target.phoneNumber)
  if (existingByPhone) return { id: existingByPhone, created: false }

  const nameArgs = target.source === 'reply' ? args : args.slice(1)
  const created = await createAccount({ phoneNumber: target.phoneNumber, displayName: nameArgs.join(' ').trim() })
  return { id: created.account.id, created: true }
}

async function waitForPairing(id, timeoutMs = 15000) {
  const a = requireAccount(id)
  const deadline = Date.now() + Math.max(1000, Math.min(30000, Number(timeoutMs) || 15000))
  while (Date.now() < deadline) {
    if (a.connected || a.pairingError || a.pairingCode || a.pairingQr) break
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  return { id:a.id, displayName:a.displayName || `Account ${a.id}`, connected:a.connected, mode:a.pairingMode, code:a.pairingCode, qr:a.pairingQr, error:a.pairingError }
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
    a.reconnectAttempts = 0
    a.pairingMode = mode === 'qr' ? 'qr' : 'code'
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    a.lastQr = ''
    await startAccount(a)
    await recordActivity('pairing.requested', { account: a.id, mode: a.pairingMode })
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
    a.reconnectAttempts = 0
    await startAccount(a)
    await recordActivity('account.reconnect-requested', { account: a.id })
    return { ok: true }
  })
}

async function disconnectAccount(id) {
  const a = requireAccount(id)
  return runOp(a, async () => {
    await closeAccount(a)
    a.pairingMode = ''
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    await recordActivity('account.disconnected-manually', { account: a.id })
    return { ok: true, account: a.id, status: statusOf(a) }
  })
}

async function removeAccount(id) {
  const resolved = resolveAccountId(id)
  if (!resolved) throw new Error(`Unknown account: ${id}`)
  if (resolved === destination) throw new Error('Choose a different CC destination before removing this account')
  if (accounts.size <= 1) throw new Error('At least one WhatsApp account must remain')

  const a = accounts.get(resolved)
  if (a.role === 'owner') throw new Error('Account A is the permanent main control account and cannot be removed')
  await runOp(a, async () => closeAccount(a))
  const removed = await accountRegistry.remove(resolved)
  accounts.delete(resolved)
  sharedStorage?.clearAccount(resolved)
  invalidateGroupMetadata(resolved)

  destination = destinationIdFor()
  await saveSettings()
  await recordActivity('account.removed', {
    account: resolved,
    displayName: a.displayName,
    authPreserved: removed.authPreserved === true,
  })
  return { ok: true, account: resolved, authPreserved: removed.authPreserved === true }
}

async function repairAccount(id, mode = 'code') {
  const a = requireAccount(id)
  return runOp(a, async () => {
    await closeAccount(a)
    await backupAuth(a)
    a.invalid = false
    a.registered = false
    a.reconnectAttempts = 0
    a.pairingMode = mode === 'qr' ? 'qr' : 'code'
    a.pairingCode = ''
    a.pairingQr = ''
    a.pairingError = ''
    a.lastQr = ''
    await startAccount(a)
    await recordActivity('pairing.repair-requested', { account: a.id, mode: a.pairingMode })
    return { ok: true, message: `Account ${a.id} auth backed up. ${a.pairingMode === 'qr' ? 'QR' : 'Code'} pairing started.` }
  })
}

async function setSetting(key, value) {
  const defaults = commandSettingDefaults()
  if (!Object.prototype.hasOwnProperty.call(defaults, key)) {
    throw new Error(`Unknown command setting: ${key}`)
  }
  settings[key] = Boolean(value)
  await saveSettings()
  await recordActivity('configuration.changed', { key, value: Boolean(value) })
}

async function setPublicPrefix(value) {
  const prefix = validatePublicPrefix(value)
  settings.publicPrefix = prefix
  await saveSettings()
  await recordActivity('configuration.public-prefix-changed', { prefix })
  await writeRuntimeRegistry()
  return prefix
}

async function reloadCommandsDetailed() {
  const cacheBust = Date.now()
  const nextPrivate = await loadCommands(PRIVATE_COMMANDS_URL, { cacheBust })
  const nextPublic = await loadCommands(PUBLIC_COMMANDS_URL, { cacheBust, allowMissing: true, capabilityFromDirectory: true })
  const nextSources = sourceRegistry ? await sourceRegistry.load({ cacheBust }) : []
  privateCommandRegistry = nextPrivate
  publicCommandRegistry = nextPublic
  settings = mergeCommandSettings(settings, nextPrivate)
  await saveSettings()
  await writeCommandSettingsSchema()
  await writeRuntimeRegistry()
  const privateNames = nextPrivate.canonical.map(command => command.name).sort()
  const publicNames = nextPublic.canonical.map(command => command.name).sort()
  await recordActivity('command.registry-changed', { count:privateNames.length + publicNames.length, privateCount:privateNames.length, publicCount:publicNames.length })
  return { private:privateNames, public:publicNames, sources:nextSources.map(source => `${source.capability}:${source.id}`).sort() }
}

async function reloadCommands() {
  const result = await reloadCommandsDetailed()
  return [...result.private, ...result.public].sort()
}

async function setDestination(value) {
  const fixed = destinationIdFor()
  if (!fixed) throw new Error('Account A/main control account is not configured')
  const requested = resolveAccountId(value)
  if (requested && requested !== fixed) {
    throw new Error('CC destination is fixed to Account A/main control account')
  }
  destination = fixed
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
    destination: destinationIdFor(),
    indexLimit: MAX_CACHE,
    retentionHours: Math.round(TTL_MS / 3600000),
    waVersion: Array.isArray(waVersion) ? waVersion.join('.') : '',
    privateCommandCount: privateCommandRegistry.canonical.length,
    publicCommandCount: publicCommandRegistry.canonical.length,
    sourceCount: sourceRegistry?.listAll().length || 0,
    publicPrefix: settings.publicPrefix || DEFAULT_PUBLIC_PREFIX,
    publicCommandsEnabled: settings.publicCommandsEnabled !== false,
    accounts: [...accounts.values()].map(a => ({
      id: a.id,
      displayName: a.displayName,
      role: a.role,
      profile: sharedStorage?.profileForAccount(a.id)?.id || 'unassigned',
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
  const fixedDestination = destinationIdFor()
  const accountLines = [...accounts.values()].map(account => {
    const name = account.displayName || `Account ${account.id}`
    const marker = account.id === fixedDestination ? ' • CC inbox' : ''
    const profile = sharedStorage?.profileForAccount(account.id)?.id || 'unassigned'
    return `${name} [${account.id}] • ${profile}: ${statusOf(account)} • ${countFor(account.id)}/${MAX_CACHE}${marker}`
  })
  return [
    ping ? '🏓 Night' : null,
    `Uptime: ${uptime(Date.now()-startedAt)}`,
    `CC inbox: ${accounts.get(fixedDestination)?.displayName || 'Account A'} [${fixedDestination || 'A'}]`,
    ...accountLines,
    `RAM RSS: ${(mem.rss/1048576).toFixed(1)} MB`,
    `Auto CC: ${settings.autoCc?'ON':'OFF'}`,
    `Reply CC: ${settings.replyCc?'ON':'OFF'}`,
    `Anti-delete: ${settings.antiDelete?'ON':'OFF'}`,
  ].filter(Boolean).join('\n')
}

async function webState() {
  const mem = process.memoryUsage()
  return {
    version: APP_VERSION,
    destination: destinationIdFor(),
    settings: { ...settings },
    capabilities: {
      addAccount: true,
      fixedCcDestination: true,
      changeCcDestination: false,
      perAccountCcOverride: false,
      botProfiles: true,
      specialistRouting: true,
      sharedDiskState: true,
    },
    botProfiles: sharedStorage?.listProfiles() || [],
    groupRoutes: sharedStorage?.listGroupRoutes() || [],
    sharedStorage: sharedStorage?.stats() || { messages:0, profiles:0, routes:0, sharedItems:0, sourceDefaults:0 },
    sources: sourceRegistry?.listAll().map(source => ({
      id: source.id,
      name: source.name,
      capability: source.capability,
      description: source.description,
    })) || [],
    entitlements: {
      maxAccounts: accountRegistry.maxAccounts,
    },
    accounts: [...accounts.values()].map(a => ({
      id: a.id,
      displayName: a.displayName,
      role: a.role,
      profile: sharedStorage?.profileForAccount(a.id)?.id || 'unassigned',
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

async function requestRestart() {
  await recordActivity('service.restart-requested', { source:'private-command' })
  setTimeout(() => {
    shutdown('private-command', 1).catch(error => {
      console.error('Restart shutdown failed:', error?.message || error)
      process.exit(1)
    })
  }, 300).unref?.()
  return { ok:true }
}

async function init() {
  await loadAccounts()
  sharedStorage = await openSharedStorage({
    file: SHARED_DB_FILE,
    ttlMs: TTL_MS,
    maxMessagesPerAccount: MAX_CACHE,
  })
  if (accounts.has('A')) sharedStorage.assignProfile('A', 'control')
  const profileUpgrade = sharedStorage.migrateLegacyAccountProfiles([...accounts.values()])
  if (profileUpgrade.migrated) {
    console.log(`MSCC profile isolation upgrade: ${profileUpgrade.assigned.length} legacy assignment(s) retained; new sessions require explicit profiles.`)
  }
  sharedStorage.pruneConversationMessages({
    days:AI_HISTORY_DAYS,
    maxPerChat:AI_HISTORY_MAX_PER_CHAT,
  })
  josiahAssistant = createJosiahAssistant({
    ai:smartAI,
    storage:sharedStorage,
    getCommands:() => publicCommandRegistry.canonical,
  })
  namiAssistant = createNamiAssistant({
    ai:smartAI,
    storage:sharedStorage,
    getCommands:() => publicCommandRegistry.canonical,
  })
  mimiAssistant = createMiMiAssistant({
    ai:smartAI,
    storage:sharedStorage,
    getCommands:() => publicCommandRegistry.canonical,
  })
  sourceRegistry = new SourceRegistry({ rootUrl:SOURCES_URL, storage:sharedStorage })
  await sourceRegistry.load()
  releaseWatcher = createReleaseWatcher({
    storage:sharedStorage,
    resolveReleaseState:resolveLibraryReleaseState,
    sendDm:sendLibraryReleaseDm,
  })
  await loadState()
  await writeCommandSettingsSchema()
  await writeRuntimeRegistry()
  startSettingsWatcher()
  webServer = startWebPanel({
    port: WEB_PORT,
    host: WEB_HOST,
    password: WEB_PASSWORD,
    sessionSecret: WEB_SESSION_SECRET,
    localControlPort: LOCAL_CONTROL_PORT,
    getState: webState,
    getActivity: activity,
    pairAccount,
    reconnectAccount,
    disconnectAccount,
    removeAccount,
    repairAccount,
    createAccount,
    renameAccount,
    setSetting,
    setDestination,
    reloadCommands,
    reloadModule
  })

  try {
    const latest = await fetchLatestWaWebVersion()
    waVersion = latest?.version
    if (Array.isArray(waVersion)) console.log('WhatsApp Web version:', waVersion.join('.'))
  } catch (e) {
    console.warn('Could not fetch latest WhatsApp Web version:', e?.message || e)
  }

  const enabledAccounts = [...accounts.values()].filter(account => account.enabled)
  if (enabledAccounts[0]) await startAccount(enabledAccounts[0])
  enabledAccounts.slice(1).forEach((account, index) => {
    setTimeout(
      () => startAccount(account).catch(e => console.error(`[${account.id}] startup:`, e?.message || e)),
      1200 * (index + 1),
    ).unref?.()
  })
  startLibraryReleaseWatcher()
  startScheduledTaskRunner()
}

let shuttingDown = false

async function shutdown(signal, exitCode = 0) {
  if (shuttingDown) return
  shuttingDown = true
  try {
    console.log(`Shutting down Night (${signal})...`)
    webServer?.close?.()
    if (settingsPollTimer) clearInterval(settingsPollTimer)
    if (releaseWatchTimer) clearInterval(releaseWatchTimer)
    releaseWatchTimer = null
    if (scheduledTaskTimer) clearInterval(scheduledTaskTimer)
    scheduledTaskTimer = null
    await Promise.allSettled([...accounts.values()].map(account => closeAccount(account)))
    await Promise.allSettled([...accounts.values()].map(account => account.credSave))
    await writeState()
    sharedStorage?.close()
    sharedStorage = null
    sourceRegistry = null
    josiahAssistant = null
    namiAssistant = null
    mimiAssistant = null
    releaseWatcher = null
  } finally {
    process.exit(exitCode)
  }
}

for (const signal of ['SIGINT','SIGTERM']) {
  process.once(signal, () => {
    shutdown(signal).catch(error => {
      console.error('Shutdown failed:', error?.message || error)
      process.exit(1)
    })
  })
}

init().catch(error => {
  console.error('Fatal:', error)
  process.exit(1)
})
