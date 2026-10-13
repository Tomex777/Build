import { activityBadges } from '../../group-activity.js'

export default {
  name:'activityrewards',
  aliases:['activitybadges','weeklybadges'],
  description:'Show earned group participation badges without an economy.',
  usage:'.activityrewards [24h|7d|30d]',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('Activity recognition is available in groups only.')
    const stats = ctx.getGroupActivity?.(ctx.args?.[0])
    if (!stats) return ctx.reply('Group activity history is not available.')
    const mine = stats.ranked.find(member => member.key === ctx.activityUserKey)
    const count = mine?.messages || 0
    const badges = activityBadges(count)
    const earned = badges.earned.length
      ? badges.earned.map(item => item.icon + ' ' + item.name + ' (' + item.threshold + '+ messages)').join('\n')
      : 'No badges earned yet.'
    const next = badges.next
      ? '\nNext: ' + badges.next.name + ' — ' + (badges.next.threshold - count) + ' more messages'
      : '\nAll activity badges unlocked!'
    return ctx.reply('🎖️ *Your activity badges — ' + stats.period.label + '*\n\n' +
      earned + next + '\n\n_Recognition only: no virtual currency, payouts, or automated messages._')
  },
}
