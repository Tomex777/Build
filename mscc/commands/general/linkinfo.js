import { linkInfo } from '../../read-content.js'

export default {
  name:'linkinfo',
  aliases:['urlinfo'],
  description:'Safely inspect a public link before opening it.',
  usage:'.linkinfo <url>',
  async run(ctx) {
    const url = ctx.args.join(' ').trim()
    if (!url) return ctx.reply('Use .linkinfo <url>.')
    try {
      const info = await linkInfo(url)
      return ctx.reply([
        '🔎 *Link Info*',
        info.title ? '*' + info.title + '*' : '',
        info.domain || '',
        info.description || '',
        info.contentType ? 'Type: ' + info.contentType.split(';')[0] : '',
        info.url || '',
      ].filter(Boolean).join('\n'))
    } catch (error) {
      return ctx.reply(error?.message || 'I could not inspect that link.')
    }
  },
}
