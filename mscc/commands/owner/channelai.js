import { isChannelJid } from '../../channel-ai-policy.js'

// Supreme-owner DM only: neither a channel post, a group admin, a sudo user,
// nor another linked-account owner may change the channel allowlist.
const LINK = /^https:\/\/(?:www\.)?whatsapp\.com\/channel\/([A-Za-z0-9]{10,64})\/?(?:\?[^#]*)?$/i
const isPrivateChat = jid => /@(s\.whatsapp\.net|lid|hosted\.lid)$/.test(String(jid || '').trim().toLowerCase())
const isChannelAdmin = metadata => {
  const role = String(metadata?.viewer_metadata?.role || metadata?.viewerMetadata?.role || '').toUpperCase()
  return ['ADMIN', 'OWNER', 'SUPERADMIN', 'SUPER_ADMIN', 'CREATOR'].includes(role)
}
async function resolveChannel(sock, value) {
  const token = String(value || '').trim()
  const invite = LINK.exec(token)
  const id = invite
    ? String((await sock.newsletterMetadata('invite', invite[1]))?.id || '').trim()
    : token.toLowerCase()
  if (!isChannelJid(id)) throw new Error('Supply a valid channel JID or https://whatsapp.com/channel/… link.')
  return id
}

export default {
  name:'channelai',
  description:'Supreme owner: opt a specific WhatsApp Channel in or out of NIGHT AI (default off).',
  usage:'.channelai <on|off|status> <channel JID or WhatsApp Channel link>',
  ownerOnly:true,
  async run(ctx) {
    const jid = ctx.message?.key?.remoteJid
    if (ctx.isSupremeOwner !== true || !isPrivateChat(jid) || ctx.groupKey) return
    const action = String(ctx.args?.[0] || '').trim().toLowerCase()
    if (!['on', 'off', 'status'].includes(action) || !ctx.args?.[1]) {
      return ctx.reply('Usage: .channelai <on|off|status> <channel JID or https://whatsapp.com/channel/… link>\nChannels default to OFF. Only the selected bot account may reply when ON.')
    }
    const sock = ctx.account?.sock
    if (!sock?.newsletterMetadata) return ctx.reply('Channel lookup is unavailable for this NIGHT account.')
    try {
      const channel = await resolveChannel(sock, ctx.args.slice(1).join(' '))
      if (action === 'status') {
        const current = ctx.channelAiGet(channel)
        return ctx.reply('Channel AI: *' + (current.enabled ? 'ON' : 'OFF') + '*\n' + channel + (current.enabled ? '\nAccount: ' + current.accountId : ''))
      }
      if (action === 'on') {
        // Explicitly confirm that this account can actually publish to the channel.
        const metadata = await sock.newsletterMetadata('jid', channel)
        if (!isChannelAdmin(metadata)) return ctx.reply('Not enabled: this NIGHT account must be a channel admin to publish AI replies.')
      }
      const current = ctx.channelAiSet(channel, action === 'on', ctx.account.id)
      return ctx.reply('Channel AI: *' + (current.enabled ? 'ON' : 'OFF') + '*\n' + channel +
        (current.enabled ? '\nOnly NIGHT account ' + current.accountId + ' may respond in this channel.' : '\nNo AI replies or channel commands will run.'))
    } catch (error) {
      return ctx.reply('Channel AI was not changed. ' + String(error?.message || 'Channel lookup failed.').slice(0, 180))
    }
  },
}
