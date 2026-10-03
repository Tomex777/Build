import { azureSpeechToText } from '../../azure-media.js'

export default {
  name:'stt',
  aliases:['transcribe'],
  description:'Transcribe a replied voice note, audio file, or video with Azure Speech.',
  usage:'.stt [language-code]',
  async run(ctx) {
    const language = /^[a-z]{2,3}(?:-[A-Z]{2})?$/.test(String(ctx.args?.[0] || ''))
      ? String(ctx.args[0])
      : ''
    try {
      const result = await azureSpeechToText(ctx, { language })
      return ctx.reply('📝 *Transcript*\n' + result.text)
    } catch (error) {
      return ctx.reply(error?.message || 'Speech transcription failed.')
    }
  },
}
