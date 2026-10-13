export default {
  name:'myactivity',
  aliases:['mystats','mymsgs'],
  description:'Your messages, stickers, photos, videos, and media statistics.',
  usage:'.myactivity [24h|7d|30d]',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('Use this command in a group to see your activity there.')
    const stats = ctx.getGroupActivity?.(ctx.args?.[0])
    if (!stats) return ctx.reply('Group activity history is not available.')
    const mine = stats.ranked.find(member => member.key === ctx.activityUserKey)
    if (!mine) return ctx.reply('No activity recorded for you in ' + stats.period.label + ' yet.')
    const share = stats.total ? (100 * mine.messages / stats.total).toFixed(1) : '0.0'
    return ctx.reply([
      '📊 *Your activity — ' + stats.period.label + '*',
      '',
      '💬 Messages sent: *' + mine.messages + '*',
      '📝 Text messages: ' + mine.text,
      '📁 Media sent: *' + mine.media + '*',
      '🖼️ Photos sent: ' + mine.photos,
      '🎞️ Videos sent: ' + mine.videos,
      '✨ Stickers sent: ' + mine.stickers,
      '🎤 Audio/voice notes: ' + mine.audio,
      '📄 Documents: ' + mine.documents,
      mine.otherMedia ? '📎 Other media: ' + mine.otherMedia : '',
      '',
      '🏆 Rank: *#' + mine.rank + '* of ' + stats.uniqueMembers,
      '📈 Share of activity: ' + share + '%',
      '_Counts cover the messages this bot could see and retain._',
    ].filter(Boolean).join('\n'))
  },
}
