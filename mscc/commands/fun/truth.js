import { pickTruthOrDare } from '../../utils/truth-dare-engine.js'

export default {
  name: 'truth',
  description: 'Get a random truth question.',
  usage: '.truth',
  async run(ctx) {
    const result = pickTruthOrDare(ctx, 'truth')
    if (!result.question) {
      await ctx.reply('This chat has already seen every truth in the last 72 hours.')
      return
    }
    await ctx.reply(`*Truth*\n\n${result.question.text}`)
  },
}
