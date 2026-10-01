function formatDuration(totalSeconds) {
  const seconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  return [
    days ? `${days}d` : '',
    (days || hours) ? `${hours}h` : '',
    (days || hours || minutes) ? `${minutes}m` : '',
    `${secs}s`,
  ].filter(Boolean).join(' ')
}

export default {
  name: 'uptime',
  aliases: ['up'],
  description: 'Show how long MSCC has been running.',
  usage: '.uptime',
  async run(ctx) {
    await ctx.reply(`⏱️ Uptime: ${formatDuration(process.uptime())}`)
  },
}
