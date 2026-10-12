import { targetAndReason } from './_group.js'

export default {
  name:'warn',
  description:'Warn a group member and keep a persistent warning count.',
  usage:'.warn @user [reason]',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const mode = String(ctx.args?.[0] || '').toLowerCase()
    try {
      if (mode === 'clear' || mode === 'reset') {
        const result = await ctx.groupWarnClear?.(String(ctx.args?.[1] || '').trim())
        return ctx.replyWithMentions('Warnings cleared for ' + result.target.mentionText + '.', [result.target.jid])
      }
      if (mode === 'count' || mode === 'status') {
        const result = await ctx.groupWarningState?.(String(ctx.args?.[1] || '').trim())
        return ctx.replyWithMentions(result.target.mentionText + ' has ' + Number(result.state?.count || 0) + ' warning(s).', [result.target.jid])
      }
      const parsed = targetAndReason(ctx.args)
      const result = await ctx.groupWarn?.(parsed.target, parsed.reason)
      return ctx.replyWithMentions(
        '⚠️ ' + result.target.mentionText + ' warned. Total: *' + Number(result.state?.count || 0) + '*.' +
        (parsed.reason ? '\nReason: ' + parsed.reason : ''),
        [result.target.jid],
      )
    } catch (error) { return ctx.reply(error?.message || 'I could not update that warning.') }
  },
}
