export default {
  name: 'profiles',
  aliases: ['bots'],
  description: 'Show bot profiles, capability priorities and session assignments.',
  async run(ctx) {
    const profiles = ctx.botProfiles()
    const assignments = new Map(ctx.botAssignments().map(row => [row.account_id, row.profile_id]))
    const accountRows = ctx.diagnostics().accounts.map(account => `${account.displayName} [${account.id}] → ${assignments.get(account.id) || 'main'}`)
    const profileRows = profiles.map(profile => {
      const caps = profile.capabilities.length
        ? profile.capabilities.map(item => `${item.capability}:${item.priority}`).join(', ')
        : 'no specialist priorities'
      return `${profile.displayName} [${profile.id}] • ${profile.universal ? 'universal' : 'strict'} • base ${profile.basePriority} • ${caps}`
    })
    await ctx.reply(['🤖 Bot profiles','',...accountRows,'','Profiles:',...profileRows].join('\n'))
  },
}
