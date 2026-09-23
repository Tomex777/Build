try {
  process.loadEnvFile?.()
} catch {}

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

let reconnecting = false
let streamEnabled = true

// Small RAM-only caches. No media bytes are stored.
const recentMessages = new Map()
const delivered = new Map()

const ownerJid = () => `${OWNER_NUMBER}@s.whatsapp.net`

function normalizeJid(jid = '') {
  try {
    return jidNormalizedUser(jid)
  } catch {
    return jid
  }
}

function senderJid(msg) {
  if (msg?.key?.fromMe) return ownerJid()

  return normalizeJid(
    msg?.key?.participantPn ||
    msg?.key?.participant ||
    msg?.key?.remoteJid ||
    ''
  )
}

function isOwnerMessage(msg) {
  return Boolean(msg?.key?.fromMe) || senderJid(msg) === ownerJid()
}

async function loadSettings() {
  await mkdir(DATA_DIR, { recursive: true })

  try {
    const raw = JSON.parse(await readFile(SETTINGS_FILE, 'utf8'))
    streamEnabled = raw.streamEnabled !== false
  } catch {
    streamEnabled = true
    await saveSettings()
  }
}

async function saveSettings() {
  await mkdir(DATA_DIR, { recursive: true })
  await writeFile(
    SETTINGS_FILE,
    JSON.stringify({ streamEnabled }, null, 2)
  )
}

function pruneCaches() {
  const cutoff = Date.now() - 6 * 60 * 60 * 1000

  for (const [key, entry] of recentMessages) {
    if (entry.at < cutoff) recentMessages.delete(key)
  }

  for (const [key, at] of delivered) {
    if (at < cutoff) delivered.delete(key)
  }

  while (recentMessages.size > 200) {
    recentMessages.delete(recentMessages.keys().next().value)
  }

  while (delivered.size > 1000) {
    delivered.delete(delivered.keys().next().value)
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

  const type = Object.keys(normalized)[0]
  if (!type) return null

  const node = normalized[type]
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
  const total = Math.floor(ms / 1000)
  const d = Math.floor(total / 86400)
  const h = Math.floor((total % 86400) / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60

  return [
    d && `${d}d`,
    (d || h) && `${h}h`,
    (d || h || m) && `${m}m`,
    `${s}s`
  ].filter(Boolean).join(' ')
}

async function ownerText(sock, text, quoted) {
  return sock.sendMessage(
    ownerJid(),
    { text },
    quoted ? { quoted } : undefined
  )
}

function quotedViewOnce(msg) {
  const context = getContextInfo(msg?.message)
  if (!context?.quotedMessage) return null

  const sourceChat = normalizeJid(
    context.remoteJid ||
    msg.key?.remoteJid ||
    ''
  )
  const sourceId = context.stanzaId || ''

  // Prefer the complete live message if we still have it.
  const cached = sourceId ? getCachedMessage(sourceChat, sourceId) : null
  if (cached?.message && findViewOnceMedia(cached.message)) {
    return {
      message: cached,
      sourceChat,
      sourceId,
      source: 'cache'
    }
  }

  const quoted = {
    key: {
      remoteJid: sourceChat,
      id: sourceId,
      fromMe: false,
      participant: context.participant || undefined
    },
    message: context.quotedMessage
  }

  if (!findViewOnceMedia(quoted.message)) return null

  return {
    message: quoted,
    sourceChat,
    sourceId,
    source: 'quoted'
  }
}

async function forwardToOwner(sock, message, sourceChat, sourceId) {
  if (!message?.message) return false

  const found = findViewOnceMedia(message.message)
  if (!found) return false

  const id = sourceId || message.key?.id || 'unknown'
  const dedupeKey = `${normalizeJid(sourceChat || message.key?.remoteJid || '')}|${id}`

  pruneCaches()
  if (delivered.has(dedupeKey)) return true

  const previousViewOnce = found.media.viewOnce
  found.media.viewOnce = false

  try {
    await sock.sendMessage(ownerJid(), {
      forward: message,
      force: true
    })

    delivered.set(dedupeKey, Date.now())
    return true
  } finally {
    found.media.viewOnce = previousViewOnce
  }
}

async function handleOwnerCommand(sock, msg, text) {
  if (!isOwnerMessage(msg)) return false

  const args = text.trim().split(/\s+/)
  const command = args[0]?.toLowerCase()
  if (!command?.startsWith('.')) return false

  if (command === '.ping') {
    const mem = process.memoryUsage()
    const before = Date.now()

    await ownerText(sock, '🏓 Checking…')
    const latency = Date.now() - before

    await ownerText(
      sock,
      `🏓 ${latency}ms\n` +
      `Uptime: ${formatUptime(Date.now() - startedAt)}\n` +
      `RAM RSS: ${(mem.rss / 1024 / 1024).toFixed(1)} MB\n` +
      `Heap: ${(mem.heapUsed / 1024 / 1024).toFixed(1)} MB\n` +
      `CC Stream: ${streamEnabled ? 'ON' : 'OFF'}\n` +
      'Destination: owner self-chat only\n' +
      'Companion: Android'
    )
    return true
  }

  if (command === '.owner') {
    await ownerText(
      sock,
      `👑 Owner: +${OWNER_NUMBER}\nCommands are owner-only. All recovered V1 media is sent only to this chat.`
    )
    return true
  }

  if (command === '.help') {
    await ownerText(
      sock,
      'Commands:\n' +
      '.cc — reply to a V1 to recover it here\n' +
      '.cc stream on/off/status\n' +
      '.ping — latency, uptime and RAM\n' +
      '.owner — owner status\n\n' +
      'Also: reply to a V1 with any normal message and the bot will try to recover it here automatically.'
    )
    return true
  }

  if (command !== '.cc') return false

  const mode = args[1]?.toLowerCase()
  const action = args[2]?.toLowerCase()

  if (!mode) {
    const quoted = quotedViewOnce(msg)

    if (!quoted) {
      await ownerText(sock, 'Reply to a view-once message with .cc')
      return true
    }

    const ok = await forwardToOwner(
      sock,
      quoted.message,
      quoted.sourceChat,
      quoted.sourceId
    )

    if (!ok) {
      await ownerText(sock, '❌ I could not recover that quoted view-once.')
    }

    return true
  }

  if (mode === 'stream') {
    if (!action || action === 'status') {
      await ownerText(sock, `📥 Automatic V1 stream is ${streamEnabled ? 'ON' : 'OFF'}.`)
      return true
    }

    if (action === 'on') {
      streamEnabled = true
      await saveSettings()
      await ownerText(sock, '✅ Automatic V1 stream is ON. Captured V1 media goes only to this chat.')
      return true
    }

    if (action === 'off') {
      streamEnabled = false
      await saveSettings()
      await ownerText(sock, '✅ Automatic V1 stream is OFF. Reply fallback and manual .cc still work.')
      return true
    }

    await ownerText(sock, 'Use .cc stream on, .cc stream off, or .cc stream status.')
    return true
  }

  await ownerText(sock, 'Use .cc or .cc stream on/off/status.')
  return true
}

async function handleOwnerReplyFallback(sock, msg) {
  if (!isOwnerMessage(msg)) return false

  const quoted = quotedViewOnce(msg)
  if (!quoted) return false

  const ok = await forwardToOwner(
    sock,
    quoted.message,
    quoted.sourceChat,
    quoted.sourceId
  )

  if (ok) {
    console.log(
      `Owner reply fallback recovered V1 from ${quoted.sourceChat || 'unknown'} via ${quoted.source}`
    )
  }

  return ok
}

async function handleLiveViewOnce(sock, msg, upsertType) {
  if (!streamEnabled || msg?.key?.fromMe) return false
  if (!findViewOnceMedia(msg.message)) return false

  const sourceChat = normalizeJid(msg.key.remoteJid || '')
  const ok = await forwardToOwner(
    sock,
    msg,
    sourceChat,
    msg.key.id
  )

  if (ok) {
    console.log(
      `Streamed V1 from ${sourceChat || 'unknown'} to owner self-chat (${upsertType || 'upsert'})`
    )
  }

  return ok
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

  sock.ev.on('connection.update', update => {
    if (update.connection === 'open') {
      reconnecting = false
      console.log('Connected as', sock.user?.id || BOT_NUMBER)
      console.log('Owner-only commands: ON')
      console.log('V1 destination: owner self-chat only')
      console.log('Automatic V1 stream:', streamEnabled ? 'ON' : 'OFF')
      return
    }

    if (update.connection !== 'close' || reconnecting) return

    const status = update.lastDisconnect?.error?.output?.statusCode

    if (
      status === DisconnectReason.loggedOut ||
      status === DisconnectReason.badSession
    ) {
      console.error('WhatsApp session is invalid. Delete AUTH_DIR and pair again.')
      process.exit(1)
    }

    reconnecting = true
    console.log('Connection closed; reconnecting...')

    setTimeout(() => {
      reconnecting = false
      start().catch(error =>
        console.error('Reconnect failed:', error?.message || error)
      )
    }, 2000)
  })

  sock.ev.on('messages.upsert', async ({ messages, type }) => {
    for (const msg of messages || []) {
      try {
        if (!msg?.message || !msg?.key?.id) continue

        cacheMessage(msg)

        const owner = isOwnerMessage(msg)
        const text = commandText(msg.message)

        // Commands never respond into the source group/DM; responses go to owner self-chat.
        if (owner && text.startsWith('.')) {
          const consumed = await handleOwnerCommand(sock, msg, text)
          if (consumed) continue
        }

        // Owner can reply with literally anything to a quoted V1.
        // Recovery still goes only to the owner self-chat.
        if (owner) {
          const recovered = await handleOwnerReplyFallback(sock, msg)
          if (recovered) continue
        }

        // True live automatic path for incoming V1 messages.
        await handleLiveViewOnce(sock, msg, type)
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
