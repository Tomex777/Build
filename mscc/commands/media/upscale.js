import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { upscaleFactor, upscaleImage } from '../../utils/upscale-media.js'

export default {
  name:'upscale',
  aliases:['enhance','hd','remini'],
  description:'Improve image resolution. Uses Clipdrop AI when configured; otherwise a clearly labeled HD resize.',
  usage:'.upscale [2|4] (reply to image)',
  async run(ctx) {
    let factor
    try { factor = upscaleFactor(ctx.args?.[0] || 2) }
    catch(error) { return ctx.reply(error?.message || 'Choose .upscale 2 or .upscale 4') }

    let source
    try { source = await downloadCommandMedia(ctx, ['image']) }
    catch(error) { return ctx.reply(error?.message || 'Unable to read the image.') }

    if (!source) return ctx.reply('🖼️ Reply to a JPG, PNG, or WebP image with .upscale 2 or .upscale 4.')
    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) return ctx.reply('WhatsApp is not connected.')

    try {
      const result = await upscaleImage(source.buffer, { factor })
      return sock.sendMessage(chat, {
        image:result.buffer,
        mimetype:result.mimeType,
        caption:'🖼️ ' + result.label,
      }, { quoted:ctx.message })
    } catch(error) {
      console.warn('MSCC image upscale failed:',error?.message || error)
      return ctx.reply(error?.message || 'Could not upscale that image.')
    }
  },
}
