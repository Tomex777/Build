import { downloadCommandMedia } from '../../utils/media-conversion.js'
import {
  applyStickerMetadata,
  readStickerMetadata,
  stickerPackNameForCommand,
} from '../../utils/sticker-metadata.js'

// .take defaults to the sender's WhatsApp profile name, but also accepts
// a custom pack name. Do not silently replace a missing profile name with a
// phone number or an opaque WhatsApp LID.
export function takeStickerPackName(ctx = {}) {
  const provided = String(ctx.rawArgs || (Array.isArray(ctx.args) ? ctx.args.join(' ') : '')).trim()
  const fallback = stickerPackNameForCommand({
    message:ctx.message,
    senderName:ctx.senderName,
  }, '')
  const normalized = (provided || fallback)
    .replace(/[\u0000-\u001f\u007f-\u009f]/gu, ' ')
    .replace(/\s+/gu, ' ')
    .trim()
  if (!normalized) {
    throw new Error('I could not read your WhatsApp profile name. Use .take <name> to choose one.')
  }
  return Array.from(normalized).slice(0, 128).join('')
}

export default {
  name: 'take',
  description: 'Copy a sticker with your WhatsApp name or a custom pack name.',
  usage: '.take [pack name] (reply to a sticker)',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['sticker'])
      if (!source) {
        await ctx.reply(`Reply to a sticker with ${prefix}take or ${prefix}take <pack name>.`)
        return
      }

      const packName = takeStickerPackName(ctx)
      const sticker = await applyStickerMetadata(source.buffer, { packName })
      // A successful send is not enough: reject silent metadata no-ops.
      const embedded = await readStickerMetadata(sticker)
      if (embedded?.['sticker-pack-name'] !== packName) {
        throw new Error('The sticker name could not be embedded. Nothing was sent.')
      }

      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')
      await ctx.account.sock.sendMessage(chat, { sticker }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC take sticker metadata failed:', error)
      await ctx.reply(error?.message || 'I could not update that sticker.')
    }
  },
}
