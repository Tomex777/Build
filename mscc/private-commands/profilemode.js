export default {
  name: 'profilemode',
  description: 'Choose whether a bot profile can fall back to unassigned capabilities.',
  usage: '.profilemode <profile> universal|strict',
  async run(ctx) {
    const profile = String(ctx.args[0] || '').trim()
    const mode = String(ctx.args[1] || '').trim().toLowerCase()
    if (!profile || !['universal','strict'].includes(mode)) return ctx.reply('Usage: .profilemode <profile> universal|strict')
    if (profile.toLowerCase() === 'main' && mode !== 'universal') return ctx.reply('Main must remain universal.')
    const updated = ctx.setBotProfileMode(profile, mode === 'universal')
    ctx.resetGroupRoutes()
    await ctx.reply(`✅ ${updated.displayName} is now ${mode}.`)
  },
}
