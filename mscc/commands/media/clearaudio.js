import { repliedAudio, withMediaToolJob, processAudio, sendProcessedAudio } from '../../utils/media-toolkit.js'

export default {
  name: 'clearaudio',
  aliases: ['cleanvoice'],
  description: 'Improve voice clarity, volume consistency and mild noise.',
  usage: '.clearaudio [natural|strong] (reply to voice note or audio)',
  async run(ctx) {
    const mode = String(ctx.args[0] || 'auto').toLowerCase()
    if (!['auto', 'natural', 'strong'].includes(mode)) {
      return ctx.reply('Use .clearaudio, .clearaudio natural, or .clearaudio strong.')
    }
    try {
      const file = await repliedAudio(ctx)
      if (!file) return ctx.reply('Reply to a voice note or audio file with .clearaudio.')
      const result = await withMediaToolJob(() => processAudio(file.bytes, { mode }))
      return sendProcessedAudio(ctx, result, { isVoice: file.isVoice, caption: '✨ Audio clarity improved (' + mode + ').' })
    } catch (error) { return ctx.reply(error?.message || 'Audio enhancement failed.') }
  },
}
