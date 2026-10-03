import { renderTweetCard } from '../../utils/social-card-renderer.js'
import { resolveSocialAuthor } from '../../utils/social-card-command.js'

const THEMES = new Set(['dark','light'])

export default {
  name:'tweet',
  description:'Create a tweet-like Night mock card from text.',
  usage:'.tweet [dark|light] [@user] <text>',
  help:'Creates a local tweet-like image card. Use dark/light for the theme. The card always includes a tiny transparent Night watermark.',
  async run(ctx) {
    const args = [...ctx.args]
    let theme = 'light'
    if (THEMES.has(String(args[0] || '').toLowerCase())) {
      theme = String(args.shift()).toLowerCase()
    }

    const author = await resolveSocialAuthor(ctx, args)
    const text = author.remainingArgs.join(' ').trim()
    if (!text) {
      return ctx.reply('Use ' + (ctx.publicPrefix || '.') + 'tweet [dark|light] [@user] <text>')
    }

    const image = await renderTweetCard({
      text,
      theme,
      authorName:author.authorName,
      avatarBuffer:author.avatarBuffer,
    })

    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) return ctx.reply('WhatsApp connection is unavailable.')

    return sock.sendMessage(chat, {
      image,
      mimetype:'image/png',
    }, { quoted:ctx.message })
  },
}
