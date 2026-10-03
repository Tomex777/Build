const TYPES = new Set(['anime','manga','tv','series','movie','film'])
export default {
  name:'latest',
  description:'Check the latest released episode/chapter for a title.',
  usage:'.latest [anime|manga|tv|movie] <title>',
  async run(ctx) {
    const args = [...ctx.args]
    let type = ''
    if (TYPES.has(String(args[0] || '').toLowerCase())) type = String(args.shift()).toLowerCase()
    const query = args.join(' ').trim()
    if (!query) return ctx.reply('Use .latest [anime|manga|tv|movie] <title>.')
    try {
      const rows = await ctx.latestRelease?.(query, type)
      if (!rows?.length) return ctx.reply('I could not find a current release for that title.')
      const lines = rows.slice(0,3).map(row => {
        if (row.kind === 'chapter') return '📖 *' + row.title + '* — Chapter ' + row.number
        if (row.kind === 'episode') {
          return '🎬 *' + row.title + '* — ' + (Number(row.season || 0) > 0 ? 'Season ' + row.season + ', ' : '') + 'Episode ' + row.number
        }
        if (row.kind === 'movie') return '🎞️ *' + row.title + '* — ' + (Number(row.number || 0) > 0 ? 'Released' : 'Not released yet')
        return '*' + row.title + '*'
      })
      return ctx.reply(lines.join('\n'))
    } catch (error) {
      return ctx.reply(error?.message || 'Latest-release lookup failed.')
    }
  },
}
