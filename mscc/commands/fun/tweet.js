import { renderTweetCard } from '../../utils/social-card-renderer.js'
import { resolveSocialAuthor, socialCardMentions, parseSocialEngagementOptions } from '../../utils/social-card-command.js'
import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { contextInfo, findViewOnceMedia } from '../../utils/whatsapp/messages.js'
import { resolveTweetEngagement } from '../../utils/tweet-engagement.js'

const THEMES = new Set(['dark','light'])

export default {
  name:'tweet',
  description:'Create a tweet-like Night card with multiline text and optional photos.',
  usage:'.tweet [dark|light] [@user] [--likes=1500 --replies=40 --reposts=12] <text>',
  help:'Set fictional engagement using --likes=1.5k --replies=40 --reposts=12 (or --likes 1500). Omitted counts stay random. Preserves photos, paragraphs, Unicode and mentions; mock card only, not real X engagement.',
  async run(ctx) {
    const args = [...(ctx.args || [])]
    let theme = 'light'
    let themeConsumed = 0
    if (THEMES.has(String(args[0] || '').toLowerCase())) {
      theme = String(args.shift()).toLowerCase()
      themeConsumed = 1
    }

    const author = await resolveSocialAuthor(ctx, args)
    const parsed = parseSocialEngagementOptions(ctx, themeConsumed + Number(author.targetConsumed))
    if (parsed.error) return ctx.reply(parsed.error)
    const text = await socialCardMentions(ctx, parsed.text)

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
        ...resolveTweetEngagement(parsed.engagement),
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
