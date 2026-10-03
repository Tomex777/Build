import { newsSearch } from '../../utility-services.js'
export default {
  name:'news',
  description:'Find current news headlines about a topic.',
  usage:'.news [topic]',
  async run(ctx) {
    const topic = ctx.args.join(' ').trim()
    try {
      const rows = await newsSearch(topic)
      if (!rows.length) return ctx.reply('I could not find current headlines.')
      return ctx.reply([
        '📰 *' + (topic ? topic + ' news' : 'Latest news') + '*',
        '',
        ...rows.slice(0,6).flatMap((row,index) => [
          (index + 1) + '. *' + row.title + '*',
          row.snippet || '',
          row.url,
          '',
        ]),
      ].filter(Boolean).join('\n'))
    } catch (error) { return ctx.reply(error?.message || 'News search failed.') }
  },
}
