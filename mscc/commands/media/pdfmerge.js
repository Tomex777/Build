import {
  repliedPdf, addToMediaSession, mediaSession, clearMediaSession,
  withMediaToolJob, runMediaWorker, sendMediaResults,
} from '../../utils/media-toolkit.js'

export default {
  name: 'pdfmerge',
  description: 'Combine 2-8 PDF files in the order you add them.',
  usage: '.pdfmerge add (reply to each PDF); .pdfmerge finish | status | clear',
  async run(ctx) {
    const action = String(ctx.args[0] || 'add').toLowerCase()
    try {
      if (action === 'clear' || action === 'cancel') {
        return ctx.reply(clearMediaSession(ctx, 'pdfmerge') ? '🗑️ PDF batch cleared.' : 'No PDF batch to clear.')
      }
      if (action === 'status') {
        const n = mediaSession(ctx, 'pdfmerge').length
        return ctx.reply('📄 ' + n + ' PDF(s) staged. Add at least two; then send .pdfmerge finish.')
      }
      if (action === 'finish' || action === 'done') {
        const items = mediaSession(ctx, 'pdfmerge')
        if (items.length < 2) return ctx.reply('Add at least two PDFs first: reply to each with .pdfmerge add.')
        const results = await withMediaToolJob(() => runMediaWorker('pdfmerge', {
          inputs: items.map(item => item.bytes),
        }))
        await sendMediaResults(ctx, results)
        clearMediaSession(ctx, 'pdfmerge')
        return
      }
      if (action !== 'add') return ctx.reply('Use .pdfmerge add, finish, status or clear.')
      const file = await repliedPdf(ctx)
      if (!file) return ctx.reply('Reply to a PDF with .pdfmerge add. Repeat for other PDFs, then .pdfmerge finish.')
      const total = addToMediaSession(ctx, 'pdfmerge', file, 8)
      return ctx.reply('📄 PDF ' + total + '/8 added. ' + (total >= 2 ? 'Use .pdfmerge finish when ready.' : 'Add another PDF.'))
    } catch (error) { return ctx.reply(error?.message || 'PDF merge failed.') }
  },
}
