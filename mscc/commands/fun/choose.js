export default {
  name:'choose',
  aliases:['pick'],
  description:'Choose one option for you.',
  usage:'.choose <option 1> | <option 2> [| more]',
  async run(ctx) {
    const options = ctx.args.join(' ').split('|').map(v => v.trim()).filter(Boolean)
    if (options.length < 2) return ctx.reply('Use .choose option one | option two.')
    return ctx.reply('I choose: *' + options[Math.floor(Math.random() * options.length)] + '*')
  },
}
