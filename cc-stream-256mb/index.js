import 'dotenv/config'

import makeWASocket, {
  Browsers,
  DisconnectReason,
  delay,
  jidNormalizedUser,
  makeCacheableSignalKeyStore,
  normalizeMessageContent,
  useMultiFileAuthState
} from '@whiskeysockets/baileys'
import pino from 'pino'
import { mkdir, readFile, writeFile } from 'fs/promises'
import { join } from 'path'

const digits = value => String(value || '').replace(/\D/g, '')
const BOT_NUMBER = digits(process.env.BOT_NUMBER)
const OWNER_NUMBER = digits(process.env.OWNER_NUMBER || BOT_NUMBER)
const AUTH_DIR = process.env.AUTH_DIR || './auth'
const DATA_DIR = process.env.DATA_DIR || './data'
const SETTINGS_FILE = join(DATA_DIR, 'settings.json')

if (!/^\d{7,15}$/.test(BOT_NUMBER)) {
  console.error('BOT_NUMBER must be 7-15 digits including country code, e.g. 2348012345678')
  process.exit(1)
}
if (!/^\d{7,15}$/.test(OWNER_NUMBER)) {
  console.error('OWNER_NUMBER must be 7-15 digits including country code')
  process.exit(1)
}

const logger = pino({ level: process.env.LOG_LEVEL || 'silent' })
const startedAt = Date.now()

const handled = new Map()
const recentMessages = new Map()
let reconnecting = false

const defaults = {
  streamToOwner: true,
  autoChats: []
}

let settings = {
  ...defaults,
  autoChats: new Set()
}

const ownerJid = () => `${OWNER_NUMBER}@s.whatsapp.net`
const isGroup = jid => typeof jid === 'string' && jid.endsWith('@g.us')

function normalizeJid(jid = '') {
  try {
    return jidNormalizedUser(jid)
  } catch {
    return jid
  }
}

function isOwnerMessage(msg) {
  const chat = normalizeJid(msg?.key?.remoteJid || '')
  return Boolean(msg?.key?.fromMe) || chat === ownerJid()
}

async function loadSettings() {
  await mkdir(DATA_DIR, { recursive: true })

  try {
    const raw = JSON.parse(await readFile(SETTINGS_FILE, 'utf8'))
    settings = {
      streamToOwner: raw.streamToOwner !== false,
      autoChats: new Set(Array.isArray(raw.autoChats) ? raw.autoChats : [])
    }
  } catch {
    settings = {
      streamToOwner: defaults.streamToOwner,
      autoChats: new Set()
    }
    await saveSettings()
  }
}

async function saveSettings() {
  await mkdir(DATA_DIR, { recursive: true })
  await writeFile(
    SETTINGS_FILE,
    JSON.stringify({
      streamToOwner: Boolean(settings.streamToOwner),
      autoChats: [...settings.autoChats]
    }, null, 2)
  )
}

function pruneCaches() {
  const cutoff = Date.now() - 6 * 60 * 60 * 1000

  for (const [key, at] of handled) {
    if (at < cutoff) handled.delete(key)
  }

  for (const [key, entry] of recentMessages) {
    if (entry.at < cutoff) recentMessages.delete(key)
  }

  while (handled.size > 1000) {
    handled.delete(handled.keys().next().value)
  }

  while (recentMessages.size > 200) {
    recentMessages.delete(recentMessages.keys().next().value)
  }
}

function cacheMessage(msg) {
  if (!msg?.key?.id) return
  const chat = normalizeJid(msg.key.remoteJid || '')
  recentMessages.set(`${chat}|${msg.key.id}`, {
    at: Date.now(),
    msg
  })
  pruneCaches()
}

function getCachedMessage(chat, id) {
  return recentMessages.get(`${normalizeJid(chat)}|${id}`)?.msg || null
}

function findViewOnceMedia(message) {
  if (!message || typeof message !== 'object') return null

  const normalized = normalizeMessageContent(message) || message
  for (const key of ['imageMessage', 'videoMessage', 'audioMessage']) {
    const media = normalized[key]
    if (media?.viewOnce === true) {
      return { key, media }
    }
  }

  for (const wrapper of [
    'viewOnceMessage',
    'viewOnceMessageV2',
    'viewOnceMessageV2Extension',
    'ephemeralMessage',
    'documentWithCaptionMessage'
  ]) {
    const inner = message[wrapper]?.message
    if (!inner) continue

    const found = findViewOnceMedia(inner)
    if (found) return found
  }

  return null
}

function getMessageNode(message) {
  const normalized = normalizeMessageContent(message) || message
  if (!normalized || typeof normalized !== 'object') return null

  const key = Object.keys(normalized)[0]
  if (!key) return null

  const node = normalized[key]
  return node && typeof node === 'object' ? node : null
}

function getContextInfo(message) {
  return getMessageNode(message)?.contextInfo || null
}

function commandText(message) {
  const normalized = normalizeMessageContent(message) || message
  return (
    normalized?.conversation ||
    normalized?.extendedTextMessage?.text ||
    normalized?.imageMessage?.caption ||
    normalized?.videoMessage?.caption ||
    ''
  ).trim()
}

function formatUptime(ms) {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60

  return [
    d && `${d}d`,
    (d || h) && `${h}h`,
    (d || h || m) && `${m}m`,
    `${sec}s`
  ].filter(Boolean).join(' ')
}

async function sendText(sock, jid, text, quoted) {
  return sock.sendMessage(jid, { text }, quoted ? { quoted } : undefined)
}

async function forwardAsNormal(sock, targetJid, message, dedupeId) {
  if (!message?.message || !targetJid) return false

  const found = findViewOnceMedia(message.message)
  if (!found) return false

  const key = `${dedupeId || message.key?.id || 'unknown'}|${targetJid}`
  pruneCaches()
  if (handled.has(key)) return true

  const previous = found.media.viewOnce
  found.media.viewOnce = false

  try {
    await sock.sendMessage(targetJid, {
      forward: message,
      force: true
    })
    handled.set(key, Date.now())
    return true
  } finally {
    found.media.viewOnce = previous
  }
}

function quotedMessageFrom(msg) {
  const context = getContextInfo(msg?.message)
  if (!context?.quotedMessage) return null

  const chat = normalizeJid(context.remoteJid || msg.key?.remoteJid || '')
  const id = context.stanzaId || ''
  const cached = id ? getCachedMessage(chat, id) : null

  if (cached?.message && findViewOnceMedia(cached.message)) {
    return {
      message: cached,
      source: 'cache',
      chat,
      id
    }
  }

  const quoted = {
    key: {
      remoteJid: chat,
      id,
      fromMe: false,
      participant: context.participant || undefined
    },
    message: context.quotedMessage
  }

  if (!findViewOnceMedia(quoted.message)) return null

  return {
    message: quoted,
    source: 'quote',
    chat,
    id
  }
}

async function handleOwnerCommand(sock, msg, chat, text) {
  if (!isOwnerMessage(msg)) return false

  const args = text.trim().split(/\s+/)
  const command = args[0]?.toLowerCase()

  if (command === '.ping') {
    const mem = process.memoryUsage()
    const rss = (mem.rss / 1024 / 1024).toFixed(1)
    const heap = (mem.heapUsed / 1024 / 1024).toFixed(1)
    const before = Date.now()

    const probe = await sendText(sock, chat, '🏓 Checking…', msg)
    const latency = Date.now() - before

    await sendText(
      sock,
      chat,
      `🏓 ${latency}ms\nUptime: ${formatUptime(Date.now() - startedAt)}\nRAM RSS: ${rss} MB\nHeap: ${heap} MB\nStream to owner: ${settings.streamToOwner ? 'ON' : 'OFF'}\nGroup auto here: ${settings.autoChats.has(chat) ? 'ON' : 'OFF'}\nCompanion: Android`,
      probe
    )
    return true
  }

  if (command === '.owner') {
    await sendText(
      sock,
      chat,
      `👑 Owner: +${OWNER_NUMBER}\nThis bot accepts control commands only from its own linked account/owner.`,
      msg
    )
    return true
  }

  if (command !== '.cc') return false

  const mode = args[1]?.toLowerCase()
  const action = args[2]?.toLowerCase()

  if (!mode) {
    const quoted = quotedMessageFrom(msg)

    if (!quoted) {
      await sendText(sock, chat, 'Reply to a view-once message with .cc', msg)
      return true
    }

    const ok = await forwardAsNormal(
      sock,
      ownerJid(),
      quoted.message,
      `manual:${quoted.chat}:${quoted.id || msg.key.id}`
    )

    await sendText(
      sock,
      chat,
      ok
        ? '✅ View-once sent to your Message Yourself chat.'
        : '❌ I could not recover that quoted view-once.',
      msg
    )
    return true
  }

  if (mode === 'auto') {
    if (!isGroup(chat)) {
      await sendText(sock, chat, 'Use .cc auto on/off/status inside a group.', msg)
      return true
    }

    if (!action || action === 'status') {
      await sendText(
        sock,
        chat,
        `👁️ Group auto reveal is ${settings.autoChats.has(chat) ? 'ON' : 'OFF'} here.`,
        msg
      )
      return true
    }

    if (action === 'on') {
      settings.autoChats.add(chat)
      await saveSettings()
      await sendText(sock, chat, '✅ Group auto reveal is ON. New view-once media will be reposted as normal media in this group.', msg)
      return true
    }

    if (action === 'off') {
      settings.autoChats.delete(chat)
      await saveSettings()
      await sendText(sock, chat, '✅ Group auto reveal is OFF for this group.', msg)
      return true
    }

    await sendText(sock, chat, 'Use .cc auto on, .cc auto off, or .cc auto status.', msg)
    return true
  }

  if (mode === 'stream') {
    if (!action || action === 'status') {
      await sendText(sock, chat, `📥 Private stream to owner is ${settings.streamToOwner ? 'ON' : 'OFF'}.`, msg)
      return true
    }

    if (action === 'on') {
      settings.streamToOwner = true
      await saveSettings()
      await sendText(sock, chat, '✅ Private view-once stream to your Message Yourself chat is ON.', msg)
      return true
    }

    if (action === 'off') {
      settings.streamToOwner = false
      await saveSettings()
      await sendText(sock, chat, '✅ Private view-once stream to your Message Yourself chat is OFF.', msg)
      return true
    }

    await sendText(sock, chat, 'Use .cc stream on, .cc stream off, or .cc stream status.', msg)
    return true
  }

  await sendText(
    sock,
    chat,
    'CC commands:\n.cc (reply to a V1)\n.cc auto on/off/status\n.cc stream on/off/status',
    msg
  )
  return true
}

async function handleOwnerReplyFallback(sock, msg, chat) {
  if (!isOwnerMessage(msg)) return false

  const quoted = quotedMessageFrom(msg)
  if (!quoted) return false

  const ok = await forwardAsNormal(
    sock,
    chat,
    quoted.message,
    `reply:${quoted.chat}:${quoted.id || msg.key.id}`
  )

  if (ok) {
    console.log(`Owner reply fallback revealed view-once in ${chat} via ${quoted.source}`)
  }

  return ok
}

async function handleLiveViewOnce(sock, msg, chat, upsertType) {
  if (!findViewOnceMedia(msg.message)) return

  cacheMessage(msg)

  const jobs = []

  if (settings.streamToOwner && chat !== ownerJid()) {
    jobs.push(
      forwardAsNormal(
        sock,
        ownerJid(),
        msg,
        `stream:${chat}:${msg.key.id}`
      ).then(ok => {
        if (ok) console.log(`Streamed view-once from ${chat} to owner (${upsertType || 'upsert'})`)
      })
    )
  }

  if (isGroup(chat) && settings.autoChats.has(chat)) {
    jobs.push(
      forwardAsNormal(
        sock,
        chat,
        msg,
        `auto:${chat}:${msg.key.id}`
      ).then(ok => {
        if (ok) console.log(`Auto-revealed view-once in ${chat} (${upsertType || 'upsert'})`)
      })
    )
  }

  if (jobs.length) {
    await Promise.allSettled(jobs)
  }
}

async function start() {
  await loadSettings()

  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR)

  const sock = makeWASocket({
    auth: {
      creds: state.creds,
      keys: makeCacheableSignalKeyStore(state.keys, logger)
    },
    logger,
    browser: Browsers.android('CC Stream'),
    markOnlineOnConnect: false,
    syncFullHistory: false,
    shouldSyncHistoryMessage: () => false,
    generateHighQualityLinkPreview: false,
    getMessage: async key => {
      const chat = normalizeJid(key?.remoteJid || '')
      return getCachedMessage(chat, key?.id || '')?.message
    }
  })

  sock.ev.on('creds.update', saveCreds)

  if (!state.creds.registered) {
    await delay(1500)
    const code = await sock.requestPairingCode(BOT_NUMBER)
    const pretty = code?.match(/.{1,4}/g)?.join('-') || code
    console.log('\nPAIRING CODE:', pretty)
    console.log('WhatsApp > Linked devices > Link with phone number instead\n')
  }

  sock.ev.on('connection.update', async update => {
    if (update.connection === 'open') {
      reconnecting = false
      console.log('Connected as', sock.user?.id || BOT_NUMBER)
      console.log('Private view-once stream:', settings.streamToOwner ? 'ON' : 'OFF')
      console.log('Owner-only commands: ON')
      return
    }

    if (update.connection !== 'close' || reconnecting) return

    const status = update.lastDisconnect?.error?.output?.statusCode
    if (status === DisconnectReason.loggedOut || status === DisconnectReason.badSession) {
      console.error('WhatsApp session is no longer valid. Delete AUTH_DIR and pair again.')
      process.exit(1)
    }

    reconnecting = true
    console.log('Connection closed; reconnecting...')

    setTimeout(() => {
      reconnecting = false
      start().catch(error => console.error('Reconnect failed:', error?.message || error))
    }, 2000)
  })

  sock.ev.on('messages.upsert', async ({ messages, type }) => {
    for (const msg of messages || []) {
      try {
        if (!msg?.message || !msg?.key?.id) continue

        const chat = normalizeJid(msg.key.remoteJid || '')
        const text = commandText(msg.message)
        const owner = isOwnerMessage(msg)

        cacheMessage(msg)

        if (owner && text.startsWith('.')) {
          const consumed = await handleOwnerCommand(sock, msg, chat, text)
          if (consumed) continue
        }

        if (owner) {
          const usedReplyFallback = await handleOwnerReplyFallback(sock, msg, chat)
          if (usedReplyFallback) continue
        }

        await handleLiveViewOnce(sock, msg, chat, type)
      } catch (error) {
        console.error('Message handling error:', error?.message || error)
      }
    }
  })

  return sock
}

start().catch(error => {
  console.error('Fatal startup error:', error)
  process.exit(1)
})
