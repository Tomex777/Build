import { resolveSocialAuthor } from '../../utils/social-card-command.js'
import { renderJailCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'jail',
  description:'Put yourself or a mentioned user behind Night jail bars.',
  usage:'.jail [@user]',
  async run(ctx) {
    const author = await resolveSocialAuthor(ctx, ctx.args)
    const image = await renderJailCard({
      authorName:author.authorName,
      avatarBuffer:author.avatarBuffer,
    })
    const chat = ctx.message?.key?.remoteJid
    return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
  },
}
