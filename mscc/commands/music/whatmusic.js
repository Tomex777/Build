import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { identifyMusic, formatMusicMatch } from '../../media-recognition.js'

export default {
  name:'whatmusic',
  aliases:['identifymusic','shazam'],
  description:'Identify a song from a replied voice note, audio, or video clip.',
  usage:'.whatmusic (reply to audio or video)',
  async run(ctx) {
    try {
      const source = await downloadCommandMedia(ctx,['audio','video'])
      if (!source) return ctx.reply('Reply to an audio or voice note, or a video clip with .whatmusic.')
      const result = await identifyMusic(source.buffer,{
        isVideo:source.kind === 'video',
        mimeType:String(source.found?.media?.mimetype || ''),
      })
      return ctx.reply(formatMusicMatch(result))
    } catch(error) {
      return ctx.reply(error?.message || 'MiMi could not recognize that music clip.')
    }
  },
}
