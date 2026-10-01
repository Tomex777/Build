import { downloadCommandMedia, stickerToVideo } from '../../utils/media-conversion.js'

export default {
  name: 'tovideo',
  aliases: ['tovid'],
  description: 'Turn an animated WhatsApp sticker into an MP4 video.',
  usage: '.tovideo',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['sticker'])
      if (!source) {
        await ctx.reply(`Reply to a sticker with ${prefix}tovideo.`)
        return
      }
      if (!source.animated) {
        await ctx.reply(`That sticker is static. Use ${prefix}toimg for a PNG image.`)
        return
      }

      const video = await stickerToVideo(source.buffer)
      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')

      await ctx.account.sock.sendMessage(chat, {
        video,
        mimetype: 'video/mp4',
      }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC sticker-to-video conversion failed:', error)
      await ctx.reply(error?.message || 'I could not turn that sticker into a video.')
    }
  },
}
