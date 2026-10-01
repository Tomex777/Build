import { pickTruthOrDare } from '../../utils/truth-dare-engine.js'

export default {
  name: 'dare',
  description: 'Get a random dare.',
  usage: '.dare',
  async run(ctx) {
    const result = pickTruthOrDare(ctx, 'dare')
    if (!result.question) {
      await ctx.reply('This chat has already seen every dare in the last 72 hours.')
      return
    }
    await ctx.reply(`*Dare*\n\n${result.question.text}`)
  },
}
