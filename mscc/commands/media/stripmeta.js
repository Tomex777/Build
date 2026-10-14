import { repliedImage, withMediaToolJob, runMediaWorker, sendMediaResults } from '../../utils/media-toolkit.js'

export default {
  name: 'stripmeta',
  aliases: ['cleanmeta'],
  description: 'Remove GPS, EXIF and other embedded metadata by re-encoding an image locally.',
  usage: '.stripmeta (reply to an image)',
  async run(ctx) {
    try {
      const source = await repliedImage(ctx)
      if (!source) return ctx.reply('Reply to a photo or image with .stripmeta.')
      const results = await withMediaToolJob(() => runMediaWorker('stripmeta', { input: source.bytes }, 45000))
      return sendMediaResults(ctx, results)
    } catch (error) { return ctx.reply(error?.message || 'Could not strip image metadata.') }
  },
}
