import { randomValue } from '../../utility-services.js'
export default {
  name:'random',
  aliases:['rand'],
  description:'Pick a random number or option.',
  usage:'.random [number <min> <max>|option1 option2 ...]',
  async run(ctx) {
    try {
      if (!ctx.args.length) return ctx.reply('🎲 ' + randomValue([]))
      const joined = ctx.args.join(' ')
      if (joined.includes('|')) {
        const options = joined.split('|').map(v => v.trim()).filter(Boolean)
        if (options.length < 2) return ctx.reply('Give me at least two choices.')
        return ctx.reply('🎲 *' + options[Math.floor(Math.random() * options.length)] + '*')
      }
      return ctx.reply('🎲 *' + randomValue(ctx.args) + '*')
    } catch (error) { return ctx.reply(error?.message || 'Random pick failed.') }
  },
}
