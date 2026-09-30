import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'manga',
  description: 'Search manga using your selected source.',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'manga',
      commandName: 'manga',
      args: ctx.args,
      botName: ctx.sourceBrand?.('manga') || 'Nami',
      action: 'search',
    })
  },
}
