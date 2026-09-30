import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'movie',
  aliases: ['movies', 'film'],
  description: 'Find movies using the configured movie sources.',
  usage: '.movie <title>',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'movies',
      commandName: 'movie',
      args: ctx.args,
      botName: ctx.sourceBrand?.('movies') || 'MiMi',
      action: 'search',
      announceFallback: false,
    })
  },
}
