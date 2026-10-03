import { runYouTubeCommand } from '../../youtube-flow.js'

export default {
  name:'trailer',
  aliases:['thriller'],
  description:'Find trailers on YouTube for a movie, series, game, or title.',
  usage:'.trailer <title>',
  async run(ctx) {
    const query = ctx.args.join(' ').trim()
    if (!query) return ctx.reply('Use .trailer <title>.')
    return runYouTubeCommand(ctx, { args:[query, 'official trailer'] })
  },
}
