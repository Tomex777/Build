import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'music',
  aliases: ['song','play'],
  description: 'Find music using the automatic managed source chain.',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'music',
      commandName: 'music',
      args: ctx.args,
      botName: ctx.sourceBrand?.('music') || 'MiMi',
      action: 'search',
      announceFallback: false,
    })
  },
}
