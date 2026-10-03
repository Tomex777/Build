import { shortenUrl } from '../../utility-services.js'

export default {
  name:'shorten',
  aliases:['shorturl'],
  description:'Create a short URL.',
  usage:'.shorten <url>',
  async run(ctx) {
    const url = ctx.args.join(' ').trim()
    if (!url) return ctx.reply('Use .shorten <url>.')
    try {
      return ctx.reply('🔗 ' + await shortenUrl(url))
    } catch (error) {
      return ctx.reply(error?.message || 'I could not shorten that URL.')
    }
  },
}
