const QUALITY_RE = /^(source|best|144|240|360|480|720|1080|1440|2160|4k)p?$/i
const DELIVERY = new Set(['document','doc','file','video','inline'])

const normalizeQuality = value => {
  const q = String(value || '').trim().toLowerCase().replace(/p$/, '')
  return q === '4k' ? '2160' : q
}
const normalizeDelivery = value => {
  const mode = String(value || '').trim().toLowerCase()
  return ['document','doc','file'].includes(mode) ? 'document' : ['video','inline'].includes(mode) ? 'video' : ''
}

export default {
  name: 'delivery',
  aliases: ['downloadpref','downloadprefs'],
  description: 'Save the default quality and delivery style for source-backed downloads.',
  usage: '.delivery <folder> [quality delivery|clear]',
  async run(ctx) {
    const capability = String(ctx.args[0] || '').trim().toLowerCase()
    if (!capability) return ctx.reply('Usage: .delivery <folder> [quality delivery|clear]')

    const action = String(ctx.args[1] || '').trim().toLowerCase()
    if (!action) {
      const current = ctx.getDeliveryDefault(capability)
      return ctx.reply(current
        ? `${capability}: ${current.quality} • ${current.delivery}\nChange: ${ctx.publicPrefix || '.'}delivery ${capability} 720 document\nClear: ${ctx.publicPrefix || '.'}delivery ${capability} clear`
        : `No saved ${capability} delivery default. The download picker will be shown.`)
    }

    if (action === 'clear' || action === 'ask') {
      ctx.clearDeliveryDefault(capability)
      return ctx.reply(`✅ Cleared your ${capability} delivery default. The picker will be shown next time.`)
    }

    const delivery = normalizeDelivery(ctx.args[2])
    if (!QUALITY_RE.test(action) || !delivery) {
      return ctx.reply('Usage: .delivery <folder> <source|360|480|720|1080|1440|2160> <video|document>')
    }
    const saved = ctx.setDeliveryDefault(capability, normalizeQuality(action), delivery)
    return ctx.reply(`✅ Saved ${capability} downloads as ${saved.quality} • ${saved.delivery}. Use .delivery ${capability} clear to ask each time.`)
  },
}
