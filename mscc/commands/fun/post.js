import { renderPostCard } from '../../utils/social-card-renderer.js'
import { resolveSocialAuthor, socialCardText } from '../../utils/social-card-command.js'
import { randomPostEngagement } from '../../utils/tweet-engagement.js'
import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { contextInfo, findViewOnceMedia } from '../../utils/whatsapp/messages.js'

const STYLES = new Set(['instagram','facebook','story'])

export default {
  name:'post',
  description:'Create a Night, Instagram, Facebook or story mock card, with photos and simulated engagement.',
  usage:'.post [instagram|facebook|story] [@user] <text> (or reply to a photo)',
  help:'Post mockups generate random likes/comments/shares or story views. Photos, paragraphs and Unicode are supported. These are fictional counters, not live platform engagement.',
  async run(ctx) {
    const args = [...(ctx.args || [])]
    let style = 'generic'
    let styleConsumed = 0
    if (STYLES.has(String(args[0] || '').toLowerCase())) {
      style = String(args.shift()).toLowerCase()
      styleConsumed = 1
    }

    const author = await resolveSocialAuthor(ctx, args)
    const text = socialCardText(ctx, styleConsumed + Number(author.targetConsumed))

    const quoted = contextInfo(ctx.message?.message)?.quotedMessage
    if (findViewOnceMedia(ctx.message?.message) || findViewOnceMedia(quoted)) {
      return ctx.reply('View-once photos cannot be used in post cards.')
    }

    let media = null
    try {
      media = await downloadCommandMedia(ctx, ['image'])
    } catch (error) {
      console.warn('Night post photo failed:', error?.message || error)
      return ctx.reply('I could not read that photo. Try replying to an ordinary image (not view-once).')
    }

    if (!text && !media) {
      return ctx.reply('Use ' + (ctx.publicPrefix || '.') + 'post [instagram|facebook|story] [@user] <text>, or reply to a photo with .post')
    }

    try {
      const image = await renderPostCard({
        text,
        style,
        authorName: author.authorName,
        avatarBuffer: author.avatarBuffer,
        imageBuffer: media?.buffer || null,
        ...randomPostEngagement(),
      })

      const chat = ctx.message?.key?.remoteJid
      const sock = ctx.account?.sock
      if (!chat || !sock) return ctx.reply('WhatsApp connection is unavailable.')

      return sock.sendMessage(chat, {
        image,
        mimetype:'image/png',
      }, { quoted:ctx.message })
    } catch (error) {
      console.warn('Night post card failed:',error?.message || error)
      return ctx.reply('I could not render that post. Please try a smaller image or shorter text.')
    }
  },
}
