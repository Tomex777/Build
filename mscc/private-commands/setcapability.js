export default {
  name: 'specialize',
  aliases: ['setcapability'],
  description: 'Give a bot profile one or more specialist command folders.',
  usage: '.specialize <profile> <folder...> | .specialize <profile> off <folder...>',
  async run(ctx) {
    const profile = String(ctx.args[0] || '').trim()
    if (!profile) return ctx.reply('Usage: .specialize <profile> <folder...>')

    const removing = String(ctx.args[1] || '').trim().toLowerCase() === 'off'
    const capabilities = ctx.args.slice(removing ? 2 : 1).map(value => String(value || '').trim().toLowerCase()).filter(Boolean)
    if (!capabilities.length) return ctx.reply('Give at least one command folder/capability.')

    for (const capability of capabilities) {
      ctx.setBotSpecialty(profile, capability, !removing)
    }
    ctx.resetGroupRoutes()

    await ctx.reply(removing
      ? `✅ ${profile}: removed specialties ${capabilities.join(', ')}.`
      : `✅ ${profile}: now specializes in ${capabilities.join(', ')}.`)
  },
}
