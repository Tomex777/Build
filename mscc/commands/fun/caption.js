import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { renderCaptionCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'caption',
  description:'Put a clean caption over a replied image.',
  usage:'.caption <text>',
  async run(ctx) {
    const text = ctx.args.join(' ').trim()
    if (!text) return ctx.reply('Reply to an image with .caption <text>.')
    try {
      const media = await downloadCommandMedia(ctx, ['image'])
      if (!media) return ctx.reply('Reply to an image with .caption <text>.')
      const image = await renderCaptionCard({ imageBuffer:media.buffer, text })
      const chat = ctx.message?.key?.remoteJid
      return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
    } catch (error) {
      return ctx.reply(error?.message || 'I could not make that caption card.')
    }
  },
}
