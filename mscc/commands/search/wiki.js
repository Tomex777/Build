import { wikipediaArticle } from '../../utils/wikipedia.js'

export default {
  name:'wiki',
  aliases:['wikipedia'],
  description:'Search Wikipedia and return the best matching article summary.',
  usage:'.wiki <topic>',
  async run(ctx) {
    const query = (Array.isArray(ctx.args) ? ctx.args : [])
      .map(value => String(value || '').trim())
      .filter(Boolean)
      .join(' ')
      .trim()

    if (!query) return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}wiki <topic>`)

    try {
      const article = await wikipediaArticle(query)
      if (!article) return ctx.reply(`I could not find a Wikipedia article for “${query}”.`)

      const text = [
        `📚 *${article.title}*`,
        '',
        article.extract,
        '',
        article.url,
      ].join('\n')

      if (article.thumbnail && typeof ctx.sendImageUrl === 'function') {
        try {
          return await ctx.sendImageUrl(article.thumbnail, text)
        } catch {}
      }

      return ctx.reply(text)
    } catch (error) {
      console.error('MSCC Wikipedia lookup failed:', error)
      return ctx.reply(error?.message || 'Wikipedia lookup failed.')
    }
  },
}
