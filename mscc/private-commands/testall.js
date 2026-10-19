import { executeTestAll } from '../command-audit.js'

export default {
  name:'testall',
  aliases:['commandaudit'],
  description:'Owner-only live WhatsApp diagnostic with an optional offline command audit.',
  usage:'.testall [audit|live [confirm]|failed [confirm]|status|cancel]',
  async run(ctx) {
    // Private-control routing alone is not proof of supreme-owner identity.
    if (ctx.isSupremeOwner !== true) {
      return ctx.reply('Only the verified NIGHT supreme owner can run command audits.')
    }
    return executeTestAll(ctx)
  },
}
