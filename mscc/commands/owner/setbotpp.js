import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { selfJid } from '../../utils/whatsapp/jid.js'

export default {
  name:'setbotpp',
  aliases:['setbotpfp'],
  description:'Set the current Night WhatsApp account profile picture from a replied image.',
  usage:'.setbotpp',
  ownerOnly:true,
  async run(ctx) {
    const sock = ctx.account?.sock
    if (!sock?.updateProfilePicture) return ctx.reply('Profile-picture updates are unavailable on this session.')
    try {
      const media = await downloadCommandMedia(ctx, ['image'])
      if (!media) return ctx.reply('Reply to an image with .setbotpp.')
      const jid = selfJid(ctx.account)
      if (!jid) return ctx.reply('This Night account has no resolved WhatsApp number.')
      await sock.updateProfilePicture(jid, media.buffer)
      return ctx.reply('Profile picture updated.')
    } catch (error) {
      return ctx.reply(error?.message || 'I could not update the profile picture.')
    }
  },
}
