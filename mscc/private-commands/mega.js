import { downloadMediaMessage } from '@itsliaaa/baileys'
import { resolveCommandMedia } from '../utils/media-conversion.js'
import { extensionForMime } from '../utils/whatsapp/media.js'
import {
  megaAvailableMounts, megaConfig, megaRecentFiles, megaStorageDashboard, megaMediaType,
  mediaTypeLabel, safeMegaName, uploadMegaMedia, validateMegaSize,
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
  return [
    '☁️ MSCC Community Cloud',
    `${prefix}cloud status — storage usage`,
    `${prefix}cloud folders — media types and available folders`,
    `${prefix}cloud list — latest files across connected folders`,
    `${prefix}cloud upload — reply to media and let the bot choose storage`,
    `${prefix}cloud discover — owner-only onboarding for incoming shared folders`,
    '',
    'Storage is selected automatically by the media type. Never include contributor IDs in commands.',
  ].join('\n')
}

export default {
  name:'mega',
  aliases:['cloud'],
  description:'Private MEGA cloud manager, including contributor-owned shared folders.',
  usage:'.cloud [status|folders|list|upload|discover] (reply to media for upload)',
  async run(ctx) {
    const prefix = '.'
    const action = String(ctx.args?.[0] || 'status').trim().toLowerCase()
    if (ctx.args?.length > 1) return ctx.reply('You no longer need to select a storage account. Use .cloud upload or .cloud list; the bot handles routing.')
    if (action === 'help') return ctx.reply(foldersHelp(prefix))
    if (!['status', 'folders', 'discover', 'list', 'upload'].includes(action)) return ctx.reply(foldersHelp(prefix))
    try {
      if (action === 'status' || action === 'folders') {
        const data = await megaStorageDashboard()
        if (action === 'status') {
          if (!data.configured) return ctx.reply('☁️ MEGA not connected. Set credentials in the secure server environment.')
          return ctx.reply([
            '☁️ MSCC Community Cloud',
            `Video archives: ${data.targets.filter(t => t.mediaTypes?.includes('videos') || t.mediaTypes?.includes('all')).length} configured`,
            `Video archives detected & enabled: ${data.targets.filter(t => (t.mediaTypes?.includes('videos') || t.mediaTypes?.includes('all')) && t.detected && t.uploadEnabled).length}`,
            `Primary account: ${bytesLabel(data.used)} / ${bytesLabel(data.total)}`,
            `Primary free: ${bytesLabel(Math.max(0, data.total - data.used))}`,
            `Contributor folders registered: ${data.targets.length - 1}`,
            `Contributor folders detected: ${data.targets.filter(t => t.type === 'contributed' && t.detected).length}`,
            'Media-type routing: automatic for configured folders.',
            'Other account quotas cannot be verified from shared folders.',
            `Details: ${prefix}cloud folders`,
          ].join('\n'))
        }
        return ctx.reply([
          '☁️ Cloud locations',
          ...data.targets.map(t => `• ${t.type === 'primary' ? 'Fallback' : mediaTypeLabel(t.mediaTypes || [])} — ${t.label} [${t.type === 'primary' ? (data.configured ? 'ready' : 'not connected') : !t.detected ? 'not mounted' : t.uploadEnabled ? 'ready' : 'uploads disabled'}]`),
          'Files are balanced by visible bytes across matching folders, NOT by MEGA account free quota.',
          'Unassigned types use the primary account. Donor quotas are not readable from a folder share.',
        ].join('\n'))
      }
      if (action === 'discover') {
        const mounts = await megaAvailableMounts()
        return ctx.reply(mounts.length
          ? '☁️ Available MEGA mounts (owner-only)\n' + mounts.map(m => `• ${m.label} — nodeId: ${m.nodeId}`).join('\n')
          : 'No incoming MEGA folder mounts were detected. Contributors must invite the primary MSCC MEGA account and grant upload access.')
      }
      if (!megaConfig().configured) return ctx.reply('☁️ MEGA is not connected. Configure the primary MEGA account in the secure server environment.')
      if (action === 'list') {
        const files = await megaRecentFiles()
        return ctx.reply(files.length
          ? '☁️ Recent cloud files\n' + files.map((f, i) => `${i + 1}. ${f.name} (${bytesLabel(f.size)}) · ${f.location}`).join('\n')
          : 'No files found in connected cloud folders.')
      }
      const selected = resolveCommandMedia(ctx)
      if (!selected) return ctx.reply(`Reply to a file, image, video, or audio message with ${prefix}cloud upload. MSCC will select the cloud automatically.`)
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
        kind:selected.kind,
        mimeType:selected.found.media?.mimetype,
        source:() => downloadMediaMessage(selected.target, 'stream', {}, retryContext),
      })
      return ctx.reply(`✅ Saved ${result.mediaType} to MEGA / ${result.label}\n${result.name} • ${bytesLabel(result.size)}\nStorage selected automatically. No public link created.`)
    } catch (error) {
      const message = String(error?.message || '')
      const allowed = /known, nonzero size|2 GiB|No connected writable MEGA folder|Not enough available space|not configured|MEGA folder could not be found/.test(message)
      return ctx.reply(`❌ Cloud ${action} failed. ${allowed ? message : 'Check cloud configuration, credentials, share permissions, quota and connectivity.'}`)
    }
  },
}
