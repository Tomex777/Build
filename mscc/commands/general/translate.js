import { translateWithMyMemory } from '../../utility-services.js'
const LANG = new Map(Object.entries({
  english:'en',french:'fr',spanish:'es',german:'de',portuguese:'pt',arabic:'ar',
  yoruba:'yo',igbo:'ig',hausa:'ha',japanese:'ja',korean:'ko',chinese:'zh',
  hindi:'hi',russian:'ru',italian:'it',dutch:'nl',turkish:'tr',swahili:'sw',
}))
export default {
  name:'translate',
  aliases:['tr'],
  description:'Translate text into another language.',
  usage:'.translate <language> <text>',
  async run(ctx) {
    const rawTarget = String(ctx.args?.[0] || '').trim().toLowerCase()
    const target = LANG.get(rawTarget) || (/^[a-z]{2,3}(?:-[a-z]{2})?$/i.test(rawTarget) ? rawTarget : '')
    const text = ctx.args.slice(1).join(' ').trim()
    if (!target || !text) return ctx.reply('Use .translate <language> <text>, for example .translate french Good morning.')
    try {
      const translated = await translateWithMyMemory(text, target)
      return ctx.reply('🌐 *' + rawTarget + '*\n' + translated)
    } catch (error) {
      if (typeof ctx.smartComplete === 'function') {
        const result = await ctx.smartComplete({
          system:'Translate the user text into ' + rawTarget + '. Return only the translated text. Preserve names, URLs, emoji, and meaning.',
          messages:[{ role:'user', content:text }],
          allowWeb:false,
          temperature:0.1,
          maxTokens:1200,
          reasoningEffort:'low',
        })
        if (result?.ok && result.text) return ctx.reply('🌐 *' + rawTarget + '*\n' + result.text)
      }
      return ctx.reply(error?.message || 'Translation failed.')
    }
  },
}
