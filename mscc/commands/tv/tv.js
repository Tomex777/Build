import { runTvCommand } from '../../tv-flow.js'

export default {
  name: 'tv',
  aliases: ['series', 'show', 'shows'],
  description: 'Search TV series, choose a season, then reply with episode numbers to download.',
  usage: '.tv <title>',
  async run(ctx) {
    return runTvCommand(ctx, { args:ctx.args })
  },
}
