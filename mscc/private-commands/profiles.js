export default {
  name: 'profiles',
  aliases: ['bots'],
  description: 'Show bot profiles, specialties and session assignments.',
  async run(ctx) {
    const profiles = ctx.botProfiles()
    const accountRows = ctx.diagnostics().accounts.map(account => `${account.displayName || account.id} [${account.id}] → ${account.profile || 'unassigned'}`)
    const profileRows = profiles.map(profile => {
      const specialties = profile.capabilities.length
        ? profile.capabilities.map(item => item.capability).join(', ')
        : 'none'
      return `${profile.displayName} [${profile.id}] • ${profile.universal ? 'can fall back' : 'strict'} • specialties: ${specialties}`
    })
    await ctx.reply(['🤖 Bot profiles','',...accountRows,'','Profiles:',...profileRows].join('\n'))
  },
}
