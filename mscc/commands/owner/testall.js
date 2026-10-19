import { executeTestAll } from '../../command-audit.js'

export default {
  name:'testall',
  aliases:['commandaudit'],
  description:'Audit all NIGHT commands or safely execute selected real commands in this WhatsApp chat.',
  usage:'.testall [live [confirm]|failed [confirm]|details [command]|status|cancel]',
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
