export default {
  name: 'ping',
  description: 'Check whether the bot is responding.',
  usage: '.ping',
  async run(ctx) {
    const name = ctx.botProfile?.displayName || 'Josiah'
    const fallback = `🏓 ${name} is here.`
    const text = ctx.personalityText
      ? await ctx.personalityText({ intent:'ping', fallback, preserve:[name] })
      : fallback
    await ctx.reply(text)
  },
}
