export default {
  name:'topactive',
  aliases:['topmsgs','weeklyactive'],
  description:'Show the most active group members, ranked by messages.',
  usage:'.topactive [24h|7d|30d]',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('Activity rankings are available in groups only.')
    if (typeof ctx.getGroupActivity !== 'function') return ctx.reply('Group activity history is not available.')
    const stats = ctx.getGroupActivity(ctx.args?.[0])
    const medals = ['🥇','🥈','🥉']
    const top = stats.ranked.slice(0, 10)
    if (!top.length) return ctx.reply('There is no tracked group activity for ' + stats.period.label + ' yet.')
    const lines = top.map((member,index) => {
      const label = member.name === 'Member' ? 'Member #' + member.rank : member.name
      const share = stats.total ? Math.round(100 * member.messages / stats.total) : 0
      return (medals[index] || '🏅') + ' *' + (index + 1) + '.* ' + label + ' — ' + member.messages + ' messages (' + share + '%)'
    })
    return ctx.reply('🏆 *Top active — ' + stats.period.label + '*\n\n' + lines.join('\n') + '\n\n_Activity is based on bot-visible, retained group history._')
  },
}
