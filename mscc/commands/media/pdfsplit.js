import { repliedPdf, withMediaToolJob, runMediaWorker, sendMediaResults } from '../../utils/media-toolkit.js'

export default {
  name: 'pdfsplit',
  description: 'Extract selected PDF pages or divide into equal-sized parts.',
  usage: '.pdfsplit 1-3,5 (reply to PDF); .pdfsplit every 5',
  async run(ctx) {
    const pages = ctx.args.join(' ').trim()
    if (!pages) return ctx.reply('Reply to a PDF with .pdfsplit 1-3,5 or .pdfsplit every 5.')
    try {
      const file = await repliedPdf(ctx)
      if (!file) return ctx.reply('Reply to a PDF document with this command.')
      const results = await withMediaToolJob(() => runMediaWorker('pdfsplit', { input: file.bytes, pages }))
      return sendMediaResults(ctx, results)
    } catch (error) { return ctx.reply(error?.message || 'Could not split PDF.') }
  },
}
