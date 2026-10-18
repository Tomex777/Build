import { executeTestAll } from '../../command-audit.js'

export default {
  name:'testall',
  aliases:['commandaudit'],
  description:'Run a safe full command audit and send the results on WhatsApp.',
  usage:'.testall [status|cancel]',
  ownerOnly:true,
  async run(ctx) {
    // A sudo or session owner may have other owner-level public permissions;
    // command audits are restricted to the verified supreme owner only.
    if (ctx.isSupremeOwner !== true || ctx.groupKey ||
        String(ctx.message?.key?.remoteJid || '').endsWith('@g.us')) {
      return ctx.reply('NIGHT command audits are available only to the verified owner in a private DM.')
    }
    return executeTestAll(ctx)
  },
}
