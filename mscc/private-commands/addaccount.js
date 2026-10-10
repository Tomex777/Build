export default {
  name: 'addaccount',
  aliases: ['newaccount', 'addsession'],
  description: 'Register another WhatsApp session without starting pairing yet.',
  usage: '.addaccount <number|mention|reply> [friendly name]',
  async run(ctx) {
    const target = await ctx.resolveCommandTarget(ctx.args[0])
    if (!target.phoneNumber) return ctx.reply('Usage: .addaccount <number|mention|reply> [friendly name]')
    const nameArgs = target.source === 'reply' ? ctx.args : ctx.args.slice(1)
    const result = await ctx.createAccount({ phoneNumber: target.phoneNumber, displayName: nameArgs.join(' ').trim() })
    await ctx.reply(`✅ Added ${result.account.displayName} [${result.account.id}] (${result.account.numberMasked}).\nUse .pair ${result.account.id}, then .assignprofile ${result.account.id} <profile-id> to enable bot commands.`)
  },
}
