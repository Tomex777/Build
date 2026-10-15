export default {
  name: 'squidgame',
  aliases: ['sg'],
  description: 'NIGHT Squid Game — unpredictable Red Light / Green Light with reaction registration.',
  usage: '.squidgame [cg|status|stop]',
  adminOnly: true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('🦑 Squid Game only works in WhatsApp groups.')
    try {
      const action = String(ctx.args?.[0] || '').trim().toLowerCase()
      if (action === 'status') return ctx.squidGame?.status(ctx)
      if (action === 'stop' || action === 'cancel') return ctx.squidGame?.stop(ctx)
      if (action && action !== 'cg' && action !== 'start') {
        return ctx.reply('🦑 .squidgame — kick mode (dedicated group)\n.squidgame cg — temporary admin mode (current group)\n.squidgame status • .squidgame stop')
      }
      if (!ctx.squidGame) return ctx.reply('Squid Game engine unavailable.')
      return ctx.squidGame.start(ctx, action === 'cg' ? 'cg' : 'kick')
    } catch (error) {
      console.error('Squid Game command:', error?.message || error)
      return ctx.reply('🦑 Squid Game could not start: ' + (error?.message || 'Please retry.'))
    }
  },
}
