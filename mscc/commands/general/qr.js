import QRCode from 'qrcode'
import { josiahQrSuccess, josiahUsage } from '../../response-pools.js'

export default {
  name: 'qr',
  description: 'Create a QR code from text or a link.',
  usage: '.qr <text or link>',
  async run(ctx) {
    const value = ctx.args.join(' ').trim()
    if (!value) return ctx.reply(josiahUsage(`${ctx.publicPrefix || '.'}qr <text or link>`))
    if (value.length > 2000) return ctx.reply('That text is too long for this QR command.')

    const dataUrl = await QRCode.toDataURL(value, {
      width: 768,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
    await ctx.sendImageDataUrl(dataUrl, josiahQrSuccess())
  },
}
