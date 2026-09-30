import { runSeriesCommand } from '../../series-flow.js'

export default {
  name: 'anime',
  description: 'Browse/search anime, choose episodes, and download with source fallback.',
  async run(ctx) {
    return runSeriesCommand(ctx, {
      capability:'anime',
      commandName:'anime',
      args:ctx.args,
    })
  },
}
