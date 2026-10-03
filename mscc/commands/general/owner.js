function escapeVcard(value) {
  return String(value || '')
    .replace(/\\/g, '\\\\')
    .replace(/\r?\n/g, '\\n')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
}

export default {
  name:'owner',
  aliases:['creator'],
  description:'Show the configured Night owner contact.',
  usage:'.owner',
  async run(ctx) {
    const owner = ctx.ownerContact?.() || {}
    const phoneNumber = String(owner.phoneNumber || '').replace(/\D/g, '')
    const displayName = String(owner.displayName || 'Owner').trim() || 'Owner'

    if (!/^\d{7,15}$/.test(phoneNumber)) {
      return ctx.reply('The owner contact is not configured yet.')
    }

    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) return ctx.reply('WhatsApp connection is unavailable.')

    const vcard = [
      'BEGIN:VCARD',
      'VERSION:3.0',
      `FN:${escapeVcard(displayName)}`,
      `TEL;type=CELL;type=VOICE;waid=${phoneNumber}:+${phoneNumber}`,
      'END:VCARD',
    ].join('\n')

    return sock.sendMessage(chat, {
      contacts:{
        displayName,
        contacts:[{ vcard }],
      },
    }, { quoted:ctx.message })
  },
}
