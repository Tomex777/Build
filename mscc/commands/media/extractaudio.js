import { repliedAudio, withMediaToolJob, processAudio, sendProcessedAudio } from '../../utils/media-toolkit.js'

export default {
  name: 'extractaudio',
  aliases: ['video2audio'],
  description: 'Extract the audio track from a replied video as MP3.',
  usage: '.extractaudio (reply to video)',
  async run(ctx) {
    try {
      const file = await repliedAudio(ctx, { video: true })
      if (!file) return ctx.reply('Reply to a video with .extractaudio.')
      const result = await withMediaToolJob(() => processAudio(file.bytes, { video: true }))
      return sendProcessedAudio(ctx, result, { video: true })
    } catch (error) { return ctx.reply(error?.message || 'Audio extraction failed.') }
  },
}
