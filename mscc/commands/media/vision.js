import { azureVisionAsk } from '../../azure-media.js'
import { downloadCommandMedia } from '../../utils/media-conversion.js'

export default {
  name:'vision',
  description:'Ask Night a question about a replied image using Azure vision.',
  usage:'.vision [question]',
  async run(ctx) {
    const question = ctx.args.join(' ').trim() || 'Describe this image accurately and mention the important details.'
    try {
      const media = await downloadCommandMedia(ctx, ['image'])
      if (!media) return ctx.reply('Reply to an image with .vision [question].')
      const mime = String(media?.found?.media?.mimetype || 'image/jpeg')
      const answer = await azureVisionAsk(media.buffer, { question, mimeType:mime })
      return ctx.reply(answer)
    } catch (error) {
      return ctx.reply(error?.message || 'Vision analysis failed.')
    }
  },
}
