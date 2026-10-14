import { renderTweetCard } from '../../utils/social-card-renderer.js'
import { resolveSocialAuthor, socialCardText } from '../../utils/social-card-command.js'
import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { contextInfo, findViewOnceMedia } from '../../utils/whatsapp/messages.js'
import { randomTweetEngagement } from '../../utils/tweet-engagement.js'

const THEMES = new Set(['dark','light'])

export default {
  name:'tweet',
  description:'Create a tweet-like Night card with multiline text and optional photos.',
  usage:'.tweet [dark|light] [@user] <text> (optionally reply to a photo or caption one)',
  help:'Preserves line breaks, Unicode and emojis. Reply to a regular image with .tweet <text>, or send an image with .tweet <text> as the caption. Photo-only cards are supported; view-once media is not.',
  async run(ctx) {
    const args = [...(ctx.args || [])]
    let theme = 'light'
    let themeConsumed = 0
    if (THEMES.has(String(args[0] || '').toLowerCase())) {
      theme = String(args.shift()).toLowerCase()
      themeConsumed = 1
    }

    const author = await resolveSocialAuthor(ctx, args)
    const text = socialCardText(ctx, themeConsumed + Number(author.targetConsumed))

    const quoted = contextInfo(ctx.message?.message)?.quotedMessage
    if (findViewOnceMedia(ctx.message?.message) || findViewOnceMedia(quoted)) {
      return ctx.reply('View-once photos cannot be used in tweet cards.')
    }

    let media = null
    try {
      media = await downloadCommandMedia(ctx, ['image'])
    } catch (error) {
      console.warn('Night tweet photo failed:', error?.message || error)
      return ctx.reply('I could not read that photo. Try replying to an ordinary image (not view-once).')
    }

    if (!text && !media) {
      return ctx.reply('Use ' + (ctx.publicPrefix || '.') + 'tweet [dark|light] [@user] <text>, or reply to a photo with .tweet')
    }

    try {
      const image = await renderTweetCard({
        text,
        theme,
        authorName:author.authorName,
        avatarBuffer:author.avatarBuffer,
        imageBuffer:media?.buffer || null,
        ...randomTweetEngagement(),
      })

      const chat = ctx.message?.key?.remoteJid
      const sock = ctx.account?.sock
      if (!chat || !sock) return ctx.reply('WhatsApp connection is unavailable.')

      return sock.sendMessage(chat, {
        image,
        mimetype:'image/png',
      }, { quoted:ctx.message })
    } catch (error) {
      console.warn('Night tweet card failed:', error?.message || error)
      return ctx.reply('I could not render that tweet card. Please try a smaller image or shorter text.')
    }
  },
}
