import { resolveSocialAuthor } from '../../utils/social-card-command.js'
import { renderWantedCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'wanted',
  description:'Make a wanted-poster card for yourself or a mentioned user.',
  usage:'.wanted [@user]',
  async run(ctx) {
    const author = await resolveSocialAuthor(ctx, ctx.args)
    const image = await renderWantedCard({
      authorName:author.authorName,
      avatarBuffer:author.avatarBuffer,
    })
    const chat = ctx.message?.key?.remoteJid
    return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
  },
}
