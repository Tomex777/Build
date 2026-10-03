import { resolveSocialAuthor } from '../../utils/social-card-command.js'
import { renderWastedCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'wasted',
  description:'Make a GTA-style wasted card for yourself or a mentioned user.',
  usage:'.wasted [@user]',
  async run(ctx) {
    const author = await resolveSocialAuthor(ctx, ctx.args)
    const image = await renderWastedCard({
      authorName:author.authorName,
      avatarBuffer:author.avatarBuffer,
    })
    const chat = ctx.message?.key?.remoteJid
    return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
  },
}
