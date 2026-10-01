function parseHours(args = []) {
  const raw = String(args[0] || '').trim().toLowerCase()
  if (!raw) return 24
  const hour = raw.match(/^(\d{1,3})(?:h|hr|hrs|hour|hours)?$/)
  if (hour) return Math.max(1, Math.min(168, Number(hour[1]) || 24))
  const day = raw.match(/^(\d{1,2})(?:d|day|days)$/)
  if (day) return Math.max(1, Math.min(168, (Number(day[1]) || 1) * 24))
  if (raw === 'today' || raw === 'yesterday') return 24
  return 24
}

export default {
  name: 'summary',
  aliases: ['recap', 'catchup'],
  description: 'Summarize recent activity in this group.',
  usage: '.summary [24h]',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups. ◇')
    if (typeof ctx.summarizeGroup !== 'function') return ctx.reply('Group summaries are unavailable right now.')
    const hours = parseHours(ctx.args)
    const result = await ctx.summarizeGroup(hours)
    return ctx.reply(result?.text || 'I could not build that summary right now.')
  },
}
