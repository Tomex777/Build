import { downloadMediaMessage } from '@itsliaaa/baileys'
import { resolveCommandMedia } from '../utils/media-conversion.js'
import { extensionForMime } from '../utils/whatsapp/media.js'
import { megaConfig, megaRecentFiles, megaStorageStatus, safeMegaName, uploadMegaMedia, validateMegaSize } from '../mega-storage.js'

function bytesLabel(bytes) {
  const n = Number(bytes) || 0
  return n >= 1024 ** 3 ? `${(n / 1024 ** 3).toFixed(2)} GiB` : `${(n / 1024 ** 2).toFixed(2)} MiB`
}

function mediaName(media = {}) {
  const explicit = String(media.fileName || '').trim()
  if (explicit) return safeMegaName(explicit)
  const ext = extensionForMime(media.mimetype) || 'bin'
  return `whatsapp-${new Date().toISOString().replace(/[:.]/g, '-') }.${ext}`
}

export default {
  name:'mega',
  description:'Private MEGA cloud archive: quota, recent files, and explicit media uploads.',
  usage:'.mega [status|list|upload] (reply to media for upload)',
  async run(ctx) {
    const action = String(ctx.args?.[0] || 'status').trim().toLowerCase()
    if (!['status', 'list', 'upload'].includes(action)) {
      return ctx.reply('Usage: .mega status | .mega list | .mega upload (reply to a file, image, video, or audio)')
    }
    if (!megaConfig().configured) {
      return ctx.reply('☁️ MEGA is not connected. Add MSCC_MEGA_EMAIL and MSCC_MEGA_PASSWORD to the secure Azure/Cortex environment. Do not send credentials in WhatsApp.')
    }
    try {
      if (action === 'status') {
        const data = await megaStorageStatus()
        return ctx.reply([
          '☁️ MEGA storage',
          `Used: ${bytesLabel(data.used)} / ${bytesLabel(data.total)}`,
          `Free: ${bytesLabel(Math.max(0, data.total - data.used))}`,
          `Folder: ${data.folder}`,
          'Local SQLite and WhatsApp sessions remain on Azure.',
        ].join('\n'))
      }
      if (action === 'list') {
        const files = await megaRecentFiles()
        return ctx.reply(files.length
          ? '☁️ Recent MEGA uploads\n' + files.map((f, i) => `${i + 1}. ${f.name} (${bytesLabel(f.size)})`).join('\n')
          : 'MEGA archive is empty.')
      }
      const selected = resolveCommandMedia(ctx)
      if (!selected) return ctx.reply('Reply to a file, image, video, or audio message with .mega upload.')
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
        source:() => downloadMediaMessage(selected.target, 'stream', {}, retryContext),
      })
      return ctx.reply(`✅ Saved to MEGA / ${megaConfig().folder}\n${result.name} • ${bytesLabel(result.size)}\nPrivate archive; no public link was created.`)
    } catch (error) {
      const safe = /known, nonzero size|512 MiB|readable stream/.test(String(error?.message || ''))
      return ctx.reply(`❌ MEGA ${action} failed. ${safe ? error.message : 'Check MEGA credentials, quota, and connectivity.'}`)
    }
  },
}
