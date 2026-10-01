import QRCode from 'qrcode'

export default {
  name: 'qr',
  description: 'Create a QR code from text or a link.',
  usage: '.qr <text or link>',
  async run(ctx) {
    const value = ctx.args.join(' ').trim()
    if (!value) {
      const usage = `${ctx.publicPrefix || '.'}qr <text or link>`
      const fallback = `Usage: ${usage}`
      const text = ctx.personalityText
        ? await ctx.personalityText({ intent:'qr-usage', fallback, preserve:[usage] })
        : fallback
      return ctx.reply(text)
    }
    if (value.length > 2000) {
      const fallback = 'That text is too long for this QR command.'
      const text = ctx.personalityText
        ? await ctx.personalityText({ intent:'qr-too-long', fallback })
        : fallback
      return ctx.reply(text)
    }

    const dataUrl = await QRCode.toDataURL(value, {
      width: 768,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
    const captionFallback = 'There you go. ◇'
    const caption = ctx.personalityText
      ? await ctx.personalityText({ intent:'qr-success', fallback:captionFallback })
      : captionFallback
    await ctx.sendImageDataUrl(dataUrl, caption)
  },
}
