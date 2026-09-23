export default {
  name: 'uptime',
  description: 'Show how long this MSCC process has been running.',
  ownerOnly: true,
  async run(ctx) {
    const total = Math.floor(process.uptime())
    const days = Math.floor(total / 86400)
    const hours = Math.floor((total % 86400) / 3600)
    const minutes = Math.floor((total % 3600) / 60)
    const seconds = total % 60
    const parts = [
      days && `${days}d`,
      (days || hours) && `${hours}h`,
      (days || hours || minutes) && `${minutes}m`,
      `${seconds}s`,
    ].filter(Boolean)
    await ctx.reply(`MSCC uptime: ${parts.join(' ')}`)
  },
}
