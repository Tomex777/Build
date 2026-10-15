import { repliedImage, withMediaToolJob, runMediaWorker } from '../../utils/media-toolkit.js'

export default {
  name: 'scanqr',
  aliases: ['readqr'],
  description: 'Decode a QR code from a replied image without opening the link.',
  usage: '.scanqr (reply to an image containing a QR code)',
  async run(ctx) {
    try {
      const file = await repliedImage(ctx)
      if (!file) return ctx.reply('Reply to a QR-code image with .scanqr.')
      const decoded = await withMediaToolJob(() => runMediaWorker('scanqr', { input: file.bytes }, 20000))
      return ctx.reply('🔎 *QR contents*\n' + decoded + '\n\n⚠️ Be careful with unfamiliar links.')
    } catch (error) { return ctx.reply(error?.message || 'Could not decode the QR code.') }
  },
}
