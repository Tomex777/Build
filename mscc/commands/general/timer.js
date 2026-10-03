export default {
  name:'timer',
  description:'Start a persistent timer and get the finish alert in your DM.',
  usage:'.timer <duration> [label]',
  async run(ctx) {
    const duration = ctx.parseDuration?.(ctx.args?.[0]) || 0
    const text = ctx.args.slice(1).join(' ').trim() || 'Time is up'
    if (!duration) return ctx.reply('Use .timer <duration> [label], for example .timer 10m noodles.')
    try {
      const task = ctx.scheduleAdd?.({ kind:'timer', text, dueAt:Date.now() + duration })
      return ctx.reply('⏱️ Timer started for *' + (ctx.formatDue?.(task.dueAt) || ctx.args[0]) + '*. ID: ' + task.id)
    } catch (error) {
      return ctx.reply(error?.message || 'I could not start that timer.')
    }
  },
}
