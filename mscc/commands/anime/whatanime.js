import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { identifyAnime, formatAnimeMatch } from '../../media-recognition.js'

export default {
  name:'whatanime',
  aliases:['traceanime','identifyanime'],
  description:'Identify anime and the episode/time from a replied screenshot (trace.moe).',
  usage:'.whatanime (reply to anime screenshot)',
  async run(ctx) {
    try {
      const source = await downloadCommandMedia(ctx,['image'])
      if (!source) return ctx.reply('Reply to an anime screenshot with .whatanime.')
      return ctx.reply(formatAnimeMatch(await identifyAnime(source.buffer)))
    } catch(error) {
      return ctx.reply(error?.message || 'Nami could not identify that anime scene.')
    }
  },
}
