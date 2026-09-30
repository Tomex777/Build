export default {
  name: 'setcapability',
  description: 'Set or remove a bot profile priority for a command capability.',
  usage: '.setcapability <profile> <capability> <priority|off>',
  async run(ctx) {
    const profile = String(ctx.args[0] || '').trim()
    const capability = String(ctx.args[1] || '').trim()
    const raw = String(ctx.args[2] || '').trim()
    if (!profile || !capability || !raw) return ctx.reply('Usage: .setcapability <profile> <capability> <priority|off>')
    const updated = ctx.setBotCapability(profile, capability, raw.toLowerCase() === 'off' ? null : raw)
    ctx.resetGroupRoutes()
    const current = updated.capabilities.find(item => item.capability === capability.toLowerCase())
    await ctx.reply(current
      ? `✅ ${updated.displayName}: ${current.capability} priority ${current.priority}`
      : `✅ Removed ${capability} specialist priority from ${updated.displayName}.`)
  },
}
