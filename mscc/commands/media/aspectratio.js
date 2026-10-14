import { repliedAudio, withMediaToolJob, formatVideoAspectRatio } from '../../utils/media-toolkit.js'

export default {
  name: 'aspectratio',
  description: 'Reframe a video for WhatsApp Status, reels or landscape without stretching people.',
  usage: '.aspectratio <9:16|16:9|1:1|4:5> [blur|fit|crop] (reply to video)',
  async run(ctx) {
    const ratio = String(ctx.args?.[0] || '').trim()
    const mode = String(ctx.args?.[1] || 'blur').toLowerCase()
    if (!['9:16','16:9','1:1','4:5'].includes(ratio) || !['blur','fit','crop'].includes(mode)) {
      return ctx.reply('Reply to a video with .aspectratio 9:16 [blur|fit|crop]. Also: 16:9, 1:1, 4:5.')
    }
    try {
      const video = await repliedAudio(ctx, { video: true })
      if (!video) return ctx.reply('Reply to a video with .aspectratio 9:16.')
      const buffer = await withMediaToolJob(() => formatVideoAspectRatio(video.bytes, { ratio, mode }))
      const jid = ctx.message?.key?.remoteJid
      if (!jid || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')
      return ctx.account.sock.sendMessage(jid, {
        video: buffer, mimetype: 'video/mp4',
        caption: '🎬 Resized to ' + ratio + ' (' + mode + ').',
      }, { quoted: ctx.message })
    } catch (error) { return ctx.reply(error?.message || 'Video aspect ratio conversion failed.') }
  },
}
