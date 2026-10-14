import { downloadMediaMessage } from '@itsliaaa/baileys'
import { resolveCommandMedia } from '../utils/media-conversion.js'
import { extensionForMime } from '../utils/whatsapp/media.js'
import {
  megaConfig, megaRecentFiles, megaStorageDashboard,
  safeMegaName, uploadMegaMedia, validateMegaSize,
} from '../mega-storage.js'

function bytesLabel(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n)) return 'unknown'
  return n >= 1024 ** 3 ? `${(n / 1024 ** 3).toFixed(2)} GiB` : `${(n / 1024 ** 2).toFixed(2)} MiB`
}

function mediaName(media = {}) {
  const explicit = String(media.fileName || '').trim()
  if (explicit) return safeMegaName(explicit)
  const ext = extensionForMime(media.mimetype) || 'bin'
  return `whatsapp-${new Date().toISOString().replace(/[:.]/g, '-') }.${ext}`
}

function foldersHelp(prefix) {
  return `☁️ MSCC Community Cloud\n${prefix}cloud status — primary account quota\n${prefix}cloud folders — connected contributor folders\n${prefix}cloud list [target] — recent files\n${prefix}cloud upload [target] — reply to media and upload\n\nShared folders must be accepted by MSCC's MEGA account and opted in via secure server configuration. No contributor passwords are required.`
}

export default {
  name:'mega',
  aliases:['cloud'],
  description:'Private MEGA cloud manager, including contributor-owned shared folders.',
  usage:'.cloud [status|folders|list [target]|upload [target]] (reply to media for upload)',
  async run(ctx) {
    const prefix = '.'
    const action = String(ctx.args?.[0] || 'status').trim().toLowerCase()
    const target = String(ctx.args?.[1] || 'primary').trim().toLowerCase()
    if (action === 'help') return ctx.reply(foldersHelp(prefix))
    if (!['status', 'folders', 'list', 'upload'].includes(action)) return ctx.reply(foldersHelp(prefix))
    try {
      if (action === 'status' || action === 'folders') {
        const data = await megaStorageDashboard()
        if (action === 'status') {
          if (!data.configured) return ctx.reply('☁️ MEGA not connected. Set credentials in the secure server environment.')
          return ctx.reply([
            '☁️ MSCC Community Cloud',
            `Primary account: ${bytesLabel(data.used)} / ${bytesLabel(data.total)}`,
            `Primary free: ${bytesLabel(Math.max(0, data.total - data.used))}`,
            `Contributor folders registered: ${data.targets.length - 1}`,
            `Contributor folders detected: ${data.targets.filter(t => t.type === 'contributed' && t.detected).length}`,
            'Donor storage is separate and cannot be assumed or summed without verification.',
            `Details: ${prefix}cloud folders`,
          ].join('\n'))
        }
        return ctx.reply([
          '☁️ Cloud locations',
          ...data.targets.map(t => `• ${t.id} — ${t.label} [${t.type === 'primary' ? (data.configured ? 'ready' : 'not connected') : !t.detected ? 'not mounted' : t.uploadEnabled ? 'upload enabled' : 'read-only'}]`),
          'Uploads use the primary folder unless you explicitly select a registered target.',
        ].join('\n'))
      }
      if (!megaConfig().configured) return ctx.reply('☁️ MEGA is not connected. Configure the primary MEGA account in the secure server environment.')
      if (action === 'list') {
        const files = await megaRecentFiles({ targetId:target })
        return ctx.reply(files.length
          ? `☁️ ${target} — recent uploads\n` + files.map((f, i) => `${i + 1}. ${f.name} (${bytesLabel(f.size)})`).join('\n')
          : `No files found in the ${target} folder.`)
      }
      const selected = resolveCommandMedia(ctx)
      if (!selected) return ctx.reply(`Reply to a file, image, video, or audio message with ${prefix}cloud upload [target].`)
      const size = selected.found?.media?.fileLength
      const bytes = validateMegaSize(size?.toNumber?.() ?? size)
      const sock = ctx.account?.sock
      if (!sock) return ctx.reply('WhatsApp is not connected.')
      const retryContext = typeof sock.updateMediaMessage === 'function'
        ? { reuploadRequest:message => sock.updateMediaMessage(message), logger:{ trace(){}, debug(){}, info(){}, warn(){}, error(){} } }
        : undefined
      const result = await uploadMegaMedia({
        name:mediaName(selected.found.media),
        size:bytes,
        targetId:target,
        source:() => downloadMediaMessage(selected.target, 'stream', {}, retryContext),
      })
      return ctx.reply(`✅ Saved to MEGA / ${result.label}\n${result.name} • ${bytesLabel(result.size)}\nPrivate archive; no public link created.`)
    } catch (error) {
      const message = String(error?.message || '')
      const allowed = /known, nonzero size|512 MiB|not enabled|Unknown MEGA storage target|not mounted|Not enough available space|not configured|MEGA folder could not be found/.test(message)
      return ctx.reply(`❌ Cloud ${action} failed. ${allowed ? message : 'Check cloud configuration, credentials, share permissions, quota and connectivity.'}`)
    }
  },
}
