import { josiahPing } from '../../response-pools.js'

export default {
  name: 'ping',
  description: 'Check whether the bot is responding.',
  usage: '.ping',
  async run(ctx) {
    const name = ctx.botProfile?.displayName || 'Josiah'
    await ctx.reply(josiahPing(name))
  },
}
