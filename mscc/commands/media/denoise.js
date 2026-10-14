import { repliedAudio, withMediaToolJob, processAudio, sendProcessedAudio } from '../../utils/media-toolkit.js'

export default {
  name: 'denoise',
  description: 'Reduce steady background noise in an audio clip or WhatsApp voice note.',
  usage: '.denoise (reply to audio)',
  async run(ctx) {
    try {
      const file = await repliedAudio(ctx)
      if (!file) return ctx.reply('Reply to audio or a voice note with .denoise.')
      const result = await withMediaToolJob(() => processAudio(file.bytes, { mode: 'denoise' }))
      return sendProcessedAudio(ctx, result, { isVoice: file.isVoice, caption: '🎧 Reduced background noise.' })
    } catch (error) { return ctx.reply(error?.message || 'Audio denoising failed.') }
  },
}
