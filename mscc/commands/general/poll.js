export default {
  name:'poll',
  description:'Create a WhatsApp poll.',
  usage:'.poll <question> | <option 1> | <option 2> [| more]',
  async run(ctx) {
    const parts = ctx.args.join(' ').split('|').map(v => v.trim()).filter(Boolean)
    if (parts.length < 3) return ctx.reply('Use .poll <question> | <option 1> | <option 2>.')
    const [question, ...options] = parts
    if (options.length > 12) return ctx.reply('A poll can have up to 12 options.')
    try {
      return await ctx.sendPoll?.({ question, options, selectableCount:1 })
    } catch (error) {
      return ctx.reply(error?.message || 'I could not create that poll.')
    }
  },
}
