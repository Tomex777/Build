export default {
  name: 'createprofile',
  description: 'Create a configurable bot profile. New profiles can still fall back to shared commands.',
  usage: '.createprofile <id> [display name]',
  async run(ctx) {
    const id = String(ctx.args[0] || '').trim()
    if (!id) return ctx.reply('Usage: .createprofile <id> [display name]')
    const profile = ctx.createBotProfile(id, ctx.args.slice(1).join(' ').trim())
    await ctx.reply(`✅ Created ${profile.displayName} [${profile.id}]. It is universal with base priority 0 until configured.`)
  },
}
