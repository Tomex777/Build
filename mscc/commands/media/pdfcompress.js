import { repliedPdf, withMediaToolJob, runMediaWorker, sendMediaResults } from '../../utils/media-toolkit.js'

export default {
  name: 'pdfcompress',
  aliases: ['compresspdf'],
  description: 'Reduce PDF file size while keeping the document readable.',
  usage: '.pdfcompress (reply to PDF)',
  async run(ctx) {
    try {
      const file = await repliedPdf(ctx)
      if (!file) return ctx.reply('Reply to a PDF document with .pdfcompress.')
      const results = await withMediaToolJob(() => runMediaWorker('pdfcompress', { input: file.bytes }, 110000))
      return sendMediaResults(ctx, results)
    } catch (error) { return ctx.reply(error?.message || 'PDF compression failed.') }
  },
}
