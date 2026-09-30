import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'tv',
  aliases: ['series', 'show', 'shows'],
  description: 'Find TV series using the configured TV sources.',
  usage: '.tv <title>',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'tv',
      commandName: 'tv',
      args: ctx.args,
      botName: ctx.sourceBrand?.('tv') || 'MiMi',
      action: 'search',
      announceFallback: false,
    })
  },
}
