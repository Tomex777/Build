import { repliedAudio, withMediaToolJob, cutAudioSilence, sendProcessedAudio } from '../../utils/media-toolkit.js'

export default {
  name: 'silencecut',
  aliases: ['cutsilence'],
  description: 'Shorten genuinely quiet gaps using audio levels (no AI). Loud background noises are not silence.',
  usage: '.silencecut [gentle|normal|aggressive] (reply to audio)',
  async run(ctx) {
    const mode = String(ctx.args?.[0] || 'normal').toLowerCase()
    if (!['gentle','normal','aggressive'].includes(mode)) {
      return ctx.reply('Use .silencecut, .silencecut gentle, or .silencecut aggressive.')
    }
    try {
      const source = await repliedAudio(ctx)
      if (!source) return ctx.reply('Reply to audio or a voice note with .silencecut.')
      const result = await withMediaToolJob(() => cutAudioSilence(source.bytes, { mode }))
      return sendProcessedAudio(ctx, result, {
        isVoice: source.isVoice, caption: '✂️ Quiet gaps shortened (' + mode + '). Loud noise may remain.',
      })
    } catch (error) { return ctx.reply(error?.message || 'Silence cutting failed.') }
  },
}
