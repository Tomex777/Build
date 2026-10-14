import {
  repliedImage, addToMediaSession, mediaSession, clearMediaSession,
  withMediaToolJob, runMediaWorker, sendMediaResults,
} from '../../utils/media-toolkit.js'

export default {
  name: 'img2pdf',
  description: 'Convert one image, or a batch of up to eight images, into an A4 PDF.',
  usage: '.img2pdf (reply to image); .img2pdf add (repeat); .img2pdf finish | clear',
  async run(ctx) {
    const action = String(ctx.args[0] || '').toLowerCase()
    try {
      if (action === 'clear' || action === 'cancel') {
        return ctx.reply(clearMediaSession(ctx, 'img2pdf') ? '🗑️ Image batch cleared.' : 'No batch to clear.')
      }
      if (action === 'status') {
        return ctx.reply('🖼️ ' + mediaSession(ctx, 'img2pdf').length + ' image(s) staged.')
      }
      if (action === 'finish' || action === 'done') {
        const items = mediaSession(ctx, 'img2pdf')
        if (!items.length) return ctx.reply('Reply to each image with .img2pdf add first.')
        const results = await withMediaToolJob(() => runMediaWorker('img2pdf', {
          inputs: items.map(item => item.bytes),
        }))
        await sendMediaResults(ctx, results)
        clearMediaSession(ctx, 'img2pdf')
        return
      }
      if (action && action !== 'add') return ctx.reply('Use .img2pdf, .img2pdf add, finish, status or clear.')
      const file = await repliedImage(ctx)
      if (!file) return ctx.reply('Reply to an image with .img2pdf (one-page PDF) or .img2pdf add (multiple).')
      if (action === 'add') {
        const n = addToMediaSession(ctx, 'img2pdf', file, 8)
        return ctx.reply('🖼️ Image ' + n + '/8 saved. Use .img2pdf finish when ready.')
      }
      const results = await withMediaToolJob(() => runMediaWorker('img2pdf', { inputs: [file.bytes] }))
      return sendMediaResults(ctx, results)
    } catch (error) { return ctx.reply(error?.message || 'Image-to-PDF conversion failed.') }
  },
}
