export default {
  name:'remind',
  aliases:['reminder'],
  description:'Set a persistent reminder that Night sends to your DM.',
  usage:'.remind <duration> <thing>',
  help:'Durations accept s, m, h, d, or w, for example 20m, 2h, 3d.',
  async run(ctx) {
    const duration = ctx.parseDuration?.(ctx.args?.[0]) || 0
    const text = ctx.args.slice(1).join(' ').trim()
    if (!duration || !text) return ctx.reply('Use .remind <duration> <thing>, for example .remind 20m check the oven.')
    try {
      const task = ctx.scheduleAdd?.({ kind:'reminder', text, dueAt:Date.now() + duration })
      return ctx.reply('⏰ Reminder set for *' + (ctx.formatDue?.(task.dueAt) || ctx.args[0]) + '* from now. ID: ' + task.id + '\nI’ll send it to your DM.')
    } catch (error) {
      return ctx.reply(error?.message || 'I could not set that reminder.')
    }
  },
}
