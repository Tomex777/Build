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
        const raw = String(ctx.args?.[1] || '').trim()
        const result = await ctx.groupWarnClear?.(raw)
        return ctx.reply('Warnings cleared for *' + (result?.target?.displayName || 'that member') + '*.')
      }
      if (mode === 'count' || mode === 'status') {
        const raw = String(ctx.args?.[1] || '').trim()
        const result = await ctx.groupWarningState?.(raw)
        return ctx.reply('*' + (result?.target?.displayName || 'Member') + '* has ' + Number(result?.state?.count || 0) + ' warning(s).')
      }
      const parsed = targetAndReason(ctx.args)
      const result = await ctx.groupWarn?.(parsed.target, parsed.reason)
      return ctx.reply('⚠️ *' + (result?.target?.displayName || 'Member') + '* warned. Total: *' + Number(result?.state?.count || 0) + '*.' + (parsed.reason ? '\nReason: ' + parsed.reason : ''))
    } catch (error) { return ctx.reply(error?.message || 'I could not update that warning.') }
  },
}
