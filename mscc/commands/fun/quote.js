import { resolveSocialAuthor } from '../../utils/social-card-command.js'
import { renderQuoteCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'quote',
  description:'Turn text into a polished Night quote card.',
  usage:'.quote [@user] <text>',
  async run(ctx) {
    const author = await resolveSocialAuthor(ctx, ctx.args)
    const text = author.remainingArgs.join(' ').trim()
    if (!text) return ctx.reply('Use .quote [@user] <text>.')

    const image = await renderQuoteCard({
      text,
      authorName:author.authorName,
      avatarBuffer:author.avatarBuffer,
    })
    const chat = ctx.message?.key?.remoteJid
    if (!chat || !ctx.account?.sock) return ctx.reply('WhatsApp connection is unavailable.')
    return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
  },
}
