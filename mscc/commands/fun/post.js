import { renderPostCard } from '../../utils/social-card-renderer.js'
import { resolveSocialAuthor } from '../../utils/social-card-command.js'

const STYLES = new Set(['instagram','facebook','story'])

export default {
  name:'post',
  description:'Create a generic or social-style Night post card.',
  usage:'.post [instagram|facebook|story] [@user] <text>',
  help:'Creates a local social-post image. Plain .post uses Night’s generic card; instagram, facebook, and story switch the layout. Every card keeps a tiny transparent Night watermark.',
  async run(ctx) {
    const args = [...ctx.args]
    let style = 'generic'
    if (STYLES.has(String(args[0] || '').toLowerCase())) {
      style = String(args.shift()).toLowerCase()
    }

    const author = await resolveSocialAuthor(ctx, args)
    const text = author.remainingArgs.join(' ').trim()
    if (!text) {
      return ctx.reply('Use ' + (ctx.publicPrefix || '.') + 'post [instagram|facebook|story] [@user] <text>')
    }

    const image = await renderPostCard({
      text,
      style,
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
