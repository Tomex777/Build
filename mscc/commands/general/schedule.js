export default {
  name:'schedule',
  aliases:['reminders'],
  description:'List or cancel your Night reminders and timers.',
  usage:'.schedule [cancel <id>]',
  async run(ctx) {
    const action = String(ctx.args?.[0] || '').toLowerCase()
    if (action === 'cancel' || action === 'remove' || action === 'delete') {
      const id = String(ctx.args?.[1] || '').trim()
      if (!id) return ctx.reply('Use .schedule cancel <id>.')
      return ctx.reply(ctx.scheduleRemove?.(id) ? 'Cancelled ' + id + '.' : 'I could not find that scheduled item.')
    }

    const rows = ctx.scheduleList?.() || []
    if (!rows.length) return ctx.reply('You have no active reminders or timers.')
    return ctx.reply([
      '🗓️ *Your Night schedule*',
      '',
      ...rows.slice(0,25).map(row =>
        row.id + ' · ' + (row.kind === 'timer' ? '⏱️' : '⏰') + ' ' +
        (ctx.formatDue?.(row.dueAt) || '') + ' · ' + (row.text || '(no label)')
      ),
      rows.length > 25 ? '\n…plus ' + (rows.length - 25) + ' more.' : '',
      '',
      'Cancel: .schedule cancel <id>',
    ].filter(Boolean).join('\n'))
  },
}
