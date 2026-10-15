import { repliedPdf, withMediaToolJob, runMediaWorker, sendMediaResults } from '../../utils/media-toolkit.js'

export default {
  name: 'pdf2img',
  description: 'Render PDF pages as PNG images, up to five per request.',
  usage: '.pdf2img [1|1-3|all] (reply to PDF)',
  async run(ctx) {
    try {
      const file = await repliedPdf(ctx)
      if (!file) return ctx.reply('Reply to a PDF with .pdf2img 1-3. Default: page 1. Maximum five pages.')
      const pages = ctx.args.join(' ').trim() || '1'
      const results = await withMediaToolJob(() => runMediaWorker('pdf2img', { input: file.bytes, pages }))
      return sendMediaResults(ctx, results)
    } catch (error) { return ctx.reply(error?.message || 'PDF-to-image conversion failed.') }
  },
}
