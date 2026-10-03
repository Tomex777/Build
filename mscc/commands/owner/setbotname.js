export default {
  name:'setbotname',
  description:'Change the current Night WhatsApp account profile name.',
  usage:'.setbotname <name>',
  ownerOnly:true,
  async run(ctx) {
    const name = ctx.args.join(' ').trim().slice(0, 80)
    if (!name) return ctx.reply('Use .setbotname <name>.')
    const sock = ctx.account?.sock
    if (!sock?.updateProfileName) return ctx.reply('Profile-name updates are unavailable on this session.')
    try {
      await sock.updateProfileName(name)
      return ctx.reply('Profile name updated to *' + name + '*.')
    } catch (error) {
      return ctx.reply(error?.message || 'I could not update the profile name.')
    }
  },
}
