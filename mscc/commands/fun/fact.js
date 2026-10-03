import { randomFact } from '../../utility-services.js'

export default {
  name:'fact',
  description:'Get a random sourced fact.',
  usage:'.fact',
  async run(ctx) {
    try {
      const fact = await randomFact()
      return ctx.reply([
        '🧠 *' + fact.title + '*',
        fact.text,
        fact.url || '',
      ].filter(Boolean).join('\n\n'))
    } catch (error) {
      return ctx.reply(error?.message || 'I could not fetch a fact right now.')
    }
  },
}
