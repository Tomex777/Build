export default {
  name:'groupstats',
  aliases:['gstats','groupactivity'],
  description:'Detailed message and media statistics for this group.',
  usage:'.groupstats [24h|7d|30d]',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('Group statistics are available in groups only.')
    if (typeof ctx.getGroupActivity !== 'function') return ctx.reply('Group activity history is not available.')
    const stats = ctx.getGroupActivity(ctx.args?.[0])
    const t = stats.totals
    return ctx.reply([
      '📊 *Group activity — ' + stats.period.label + '*',
      '',
      '💬 Total messages: *' + stats.total + '*',
      '👥 Active members: *' + stats.uniqueMembers + '*',
      '📝 Text messages: ' + t.text,
      '📁 Media sent: *' + t.media + '*',
      '🖼️ Photos: ' + t.photos,
      '🎞️ Videos: ' + t.videos,
      '✨ Stickers: ' + t.stickers,
      '🎤 Audio/voice notes: ' + t.audio,
      '📄 Documents: ' + t.documents,
      t.otherMedia ? '📎 Other media: ' + t.otherMedia : '',
      '',
      '🏆 Most active: ' + (stats.ranked[0] ? stats.ranked[0].name + ' (' + stats.ranked[0].messages + ')' : 'No activity yet'),
      '_Based on retained bot-visible group history; not all prior messages may be available._',
    ].filter(Boolean).join('\n'))
  },
}
