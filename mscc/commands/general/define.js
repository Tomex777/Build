import { defineWord } from '../../utility-services.js'
export default {
  name:'define',
  aliases:['dictionary'],
  description:'Look up an English word definition.',
  usage:'.define <word>',
  async run(ctx) {
    const word = ctx.args.join(' ').trim()
    if (!word) return ctx.reply('Use .define <word>.')
    try {
      const entry = await defineWord(word)
      const lines = ['📚 *' + entry.word + '*' + (entry.phonetic ? ' · ' + entry.phonetic : '')]
      for (const item of entry.meanings || []) {
        lines.push('', item.partOfSpeech ? '*' + item.partOfSpeech + '*' : '', item.definition)
        if (item.example) lines.push('_“' + item.example + '”_')
      }
      return ctx.reply(lines.filter(Boolean).join('\n'))
    } catch (error) { return ctx.reply(error?.message || 'Definition lookup failed.') }
  },
}
