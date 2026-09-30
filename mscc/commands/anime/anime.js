import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'anime',
  description: 'Search anime using your selected source.',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'anime',
      commandName: 'anime',
      args: ctx.args,
      botName: ctx.sourceBrand?.('anime') || 'Nami',
      action: 'search',
    })
  },
}
