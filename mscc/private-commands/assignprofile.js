export default {
  name: 'assignprofile',
  aliases: ['assignbot'],
  description: 'Assign a bot profile to a WhatsApp session.',
  usage: '.assignprofile <account-id> <profile-id>',
  async run(ctx) {
    const accountId = ctx.resolveAccountId(ctx.args[0])
    const profileId = String(ctx.args[1] || '').trim()
    if (!accountId || !profileId) return ctx.reply('Usage: .assignprofile <account-id> <profile-id>')
    if (accountId === 'A' && profileId.toLowerCase() !== 'control') {
      return ctx.reply('Account A is the permanent supreme-control number and does not run a public bot profile.')
    }
    const profile = ctx.assignBotProfile(accountId, profileId)
    ctx.resetGroupRoutes()
    await ctx.reply(`✅ ${accountId} now uses ${profile.displayName} [${profile.id}]. Group routing will recalculate automatically.`)
  },
}
