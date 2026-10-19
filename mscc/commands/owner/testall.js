import { executeTestAll } from '../../command-audit.js'

export default {
  name:'testall',
  aliases:['commandaudit'],
  description:'Run verified-owner live WhatsApp command tests, or request the offline registry audit.',
  usage:'.testall [audit|live [confirm]|failed [confirm]|status|cancel]',
  ownerOnly:true,
  async run(ctx) {
    // Group access is explicit and requires "confirm" for media-intensive
    // live tests. Supreme-owner identity, not group-admin/sudo, is required.
    if (ctx.isSupremeOwner !== true) {
      return ctx.reply('Only the verified NIGHT supreme owner can run command audits.')
    }
    return executeTestAll(ctx)
  },
}
