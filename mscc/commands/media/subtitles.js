import { repliedAudioOrVideo, withMediaToolJob, sendMediaResults } from '../../utils/media-toolkit.js'
import { makeLocalSubtitles } from '../../utils/subtitle-tools.js'

export default {
  name: 'subtitles',
  aliases: ['srt'],
  description: 'Make a timestamped SRT subtitle file from the first 90 seconds using local whisper.cpp.',
  usage: '.subtitles [auto|en|fr|es|...] (reply to audio/video)',
  async run(ctx) {
    const language = String(ctx.args?.[0] || 'auto').toLowerCase()
    try {
      if (!/^(auto|[a-z]{2,3})$/.test(language)) {
        return ctx.reply('Use .subtitles or .subtitles en for English.')
      }
      const source = await repliedAudioOrVideo(ctx)
      if (!source) return ctx.reply('Reply to a video, audio file or voice note with .subtitles.')
      const result = await withMediaToolJob(() => makeLocalSubtitles(source.bytes, { language }))
      return sendMediaResults(ctx, [{
        bytes: result, mime: 'application/x-subrip', name: 'night-subtitles.srt',
        caption: '🎬 Timed captions for the first 90 seconds. Import this SRT into a video editor.',
      }])
    } catch (error) { return ctx.reply(error?.message || 'Subtitle generation failed.') }
  },
}
