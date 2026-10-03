import { readCommandContent } from '../../read-content.js'

export default {
  name:'read',
  description:'Read and summarize a replied image/document or an article URL.',
  usage:'.read [url|question]',
  async run(ctx) {
    const question = ctx.args.join(' ').trim()
    try {
      const content = await readCommandContent(ctx, { question })
      if (content.kind === 'answer') return ctx.reply(content.text)

      const extracted = String(content.text || '').trim()
      if (!extracted) return ctx.reply('I could not extract readable content.')

      if (typeof ctx.smartComplete === 'function') {
        const result = await ctx.smartComplete({
          system:[
            'Read the supplied content faithfully.',
            question ? 'Answer the user question using only the supplied content.' : 'Summarize the important information clearly.',
            'Do not invent facts that are not present.',
            'Keep the answer suitable for WhatsApp.',
          ].join(' '),
          messages:[{
            role:'user',
            content:(question ? 'Question: ' + question + '\n\n' : '') + 'CONTENT:\n' + extracted.slice(0,30000),
          }],
          allowWeb:false,
          temperature:0.2,
          maxTokens:1600,
          reasoningEffort:'medium',
        })
        if (result?.ok && result.text) return ctx.reply(result.text)
      }

      return ctx.reply('📄 *Extracted text*\n' + extracted.slice(0,3500))
    } catch (error) {
      return ctx.reply(error?.message || 'I could not read that.')
    }
  },
}
