import { azureTextToSpeech } from '../../azure-media.js'

export default {
  name:'tts',
  description:'Turn text into a voice note with Azure Speech.',
  usage:'.tts [voice=<Azure voice>] <text>',
  async run(ctx) {
    const args = [...ctx.args]
    let voice = ''
    if (/^voice=/i.test(String(args[0] || ''))) voice = String(args.shift()).slice(6).trim()
    const text = args.join(' ').trim()
    if (!text) return ctx.reply('Use .tts <text> or .tts voice=<Azure voice> <text>.')
    try {
      const audio = await azureTextToSpeech(text, { voice })
      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) return ctx.reply('WhatsApp connection is unavailable.')
      return ctx.account.sock.sendMessage(chat, {
        audio:audio.buffer,
        mimetype:audio.mimetype,
        ptt:true,
      }, { quoted:ctx.message })
    } catch (error) {
      return ctx.reply(error?.message || 'Text-to-speech failed.')
    }
  },
}
