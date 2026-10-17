export default {
  name: 'squidgame',
  aliases: ['sg'],
  description: 'NIGHT Squid Game — reaction registration with an admin-controlled start time.',
  usage: '.squidgame [15m|at HH:MM|cg 15m|launch|extend 15m|status|stop]',
  adminOnly: true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('🦑 Squid Game only works in WhatsApp groups.')
    try {
      if (!ctx.squidGame) return ctx.reply('Squid Game engine unavailable.')
      const args = (ctx.args || []).map(value => String(value || '').trim()).filter(Boolean)
      const action = (args[0] || '').toLowerCase()
      if (action === 'status') return ctx.squidGame.status(ctx)
      if (action === 'stop' || action === 'cancel') return ctx.squidGame.stop(ctx)
      if (action === 'launch' || (action === 'start' && ctx.squidGame.get(ctx.groupKey)?.phase === 'REGISTERING')) {
        return ctx.squidGame.launch(ctx)
      }
      if (action === 'extend') return ctx.squidGame.extend(ctx, args.slice(1))
      if (action === 'help') return ctx.reply([
        '🦑 .squidgame — open registration for 15 minutes',
        '.squidgame 30m — choose registration duration (1m–24h)',
        '.squidgame at 20:00 — start at 8 PM WAT (Nigeria time)',
        '.squidgame cg 30m — temporary-admin mode',
        '.squidgame launch — begin early with 2+ registered players',
        '.squidgame extend 30m — extend the open registration deadline',
        '.squidgame status • .squidgame stop',
      ].join('\n'))
      const mode = action === 'cg' ? 'cg' : 'kick'
      const timing = action === 'cg' || action === 'start' ? args.slice(1) : args
      return ctx.squidGame.start(ctx, mode, timing)
    } catch (error) {
      console.error('Squid Game command:', error?.message || error)
      return ctx.reply('🦑 Squid Game command failed: ' + (error?.message || 'Please retry.'))
    }
  },
}
