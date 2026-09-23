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

const digits = value => String(value || '').replace(/\D/g, '')
const BOT_NUMBER = digits(process.env.BOT_NUMBER)
const OWNER_NUMBER = digits(process.env.OWNER_NUMBER || BOT_NUMBER)
const AUTH_DIR = process.env.AUTH_DIR || './auth'

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
let reconnecting = false

const ownerJid = () => `${OWNER_NUMBER}@s.whatsapp.net`

function pruneHandled() {
  const cutoff = Date.now() - 6 * 60 * 60 * 1000
  for (const [id, at] of handled) if (at < cutoff) handled.delete(id)
  while (handled.size > 500) handled.delete(handled.keys().next().value)
}

function findViewOnceMedia(message) {
  if (!message || typeof message !== 'object') return null

  const normalized = normalizeMessageContent(message) || message
  for (const key of ['imageMessage', 'videoMessage', 'audioMessage']) {
    const media = normalized[key]
    if (media?.viewOnce === true) return { key, media }
  }

  for (const wrapper of ['viewOnceMessage', 'viewOnceMessageV2', 'viewOnceMessageV2Extension', 'ephemeralMessage', 'documentWithCaptionMessage']) {
    const inner = message[wrapper]?.message
    if (inner) {
      const found = findViewOnceMedia(inner)
      if (found) return found
    }
  }

  return null
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
  return [d && `${d}d`, (d || h) && `${h}h`, (d || h || m) && `${m}m`, `${sec}s`].filter(Boolean).join(' ')
}

async function start() {
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
    getMessage: async () => undefined
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
      console.log('View-once auto-forward: ON')
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

        const chat = jidNormalizedUser(msg.key.remoteJid || '')
        const fromMe = Boolean(msg.key.fromMe)
        const text = commandText(msg.message).toLowerCase()

        // Tiny private health command. Accept it only from the linked account / owner DM.
        if (text === '.ping' && (fromMe || chat === ownerJid())) {
          const mem = process.memoryUsage()
          const rss = (mem.rss / 1024 / 1024).toFixed(1)
          const heap = (mem.heapUsed / 1024 / 1024).toFixed(1)
          const before = Date.now()
          await sock.sendMessage(ownerJid(), { text: '🏓 Checking…' })
          const latency = Date.now() - before
          await sock.sendMessage(ownerJid(), {
            text: `🏓 ${latency}ms\nUptime: ${formatUptime(Date.now() - startedAt)}\nRAM RSS: ${rss} MB\nHeap: ${heap} MB\nCC Stream: ON\nCompanion: Android`
          })
          continue
        }

        if (fromMe) continue

        const found = findViewOnceMedia(msg.message)
        if (!found) continue

        pruneHandled()
        if (handled.has(msg.key.id)) continue

        const originalViewOnce = found.media.viewOnce
        found.media.viewOnce = false

        try {
          // Forward the original live WAMessage. No media download/buffer is created.
          await sock.sendMessage(ownerJid(), {
            forward: msg,
            force: true
          })
          handled.set(msg.key.id, Date.now())
          console.log(`Forwarded view-once ${found.key} from ${chat || 'unknown'} (${type || 'upsert'})`)
        } finally {
          found.media.viewOnce = originalViewOnce
        }
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
