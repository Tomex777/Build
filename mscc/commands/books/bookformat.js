function normalize(value) {
  return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '')
}

export default {
  name:'bookformat',
  aliases:['edition','bookedition','bookpref'],
  description:'Save your preferred book format such as EPUB or PDF.',
  usage:'.bookformat <epub|pdf|mobi|azw3|clear>',
  async run(ctx) {
    const key = String(ctx.userKey || '')
    if (!key || !ctx.shared?.get || !ctx.shared?.set || !ctx.shared?.delete) {
      return ctx.reply('Book format preferences are unavailable here.')
    }

    const raw = String(ctx.args[0] || '').trim()
    if (!raw) {
      const current = ctx.shared.get('book-format-default', key)
      const format = normalize(current?.format || current || '')
      return ctx.reply(format
        ? `Saved book format: ${format.toUpperCase()}\nChange: ${ctx.publicPrefix || '.'}bookformat epub\nClear: ${ctx.publicPrefix || '.'}bookformat clear`
        : `No saved book format. Set one with ${ctx.publicPrefix || '.'}bookformat epub`)
    }

    const value = normalize(raw)
    if (value === 'clear' || value === 'ask' || value === 'none') {
      ctx.shared.delete('book-format-default', key)
      return ctx.reply('✅ Cleared your saved book format.')
    }

    const allowed = new Set(['epub','pdf','mobi','azw3','cbz','cbr','txt'])
    if (!allowed.has(value)) {
      return ctx.reply('Use: .bookformat epub, .bookformat pdf, .bookformat mobi, .bookformat azw3, or .bookformat clear')
    }

    ctx.shared.set('book-format-default', key, { format:value })
    return ctx.reply(`✅ Saved book format: ${value.toUpperCase()}.`)
  },
}
