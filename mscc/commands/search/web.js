import { searchWeb } from '../../utils/web-search.js'

function formatResult(item, index) {
  return [
    `*${index + 1}. ${item.title}*`,
    item.domain ? `_${item.domain}_` : '',
    item.snippet || '',
    item.url,
  ].filter(Boolean).join('\n')
}

export default {
  name:'web',
  aliases:['www'],
  description:'Search the web and return concise website results.',
  usage:'.web <search>',
  async run(ctx) {
    const query = (Array.isArray(ctx.args) ? ctx.args : []).map(value => String(value || '').trim()).filter(Boolean).join(' ').trim()
    if (!query) return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}web <search>`)

    try {
      const results = await searchWeb(query, { limit:8 })
      if (!results.length) return ctx.reply(`No web results found for “${query}”.`)

      return ctx.reply([
        `🌐 *Web Search* — ${query}`,
        '',
        ...results.map(formatResult),
      ].join('\n\n'))
    } catch (error) {
      console.error('MSCC web search failed:', error)
      return ctx.reply(error?.message || 'Web search failed.')
    }
  },
}
