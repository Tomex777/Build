import { contextInfo } from '../../utils/whatsapp/messages.js'

const participantJid = participant =>
  typeof participant === 'string'
    ? participant
    : participant?.id || participant?.jid || participant?.lid || ''

const mentionLabel = jid => '@' + String(jid || '').split('@')[0].split(':')[0]

export default {
  name:'ship',
  description:'Ship two chosen group members, or randomly choose two members.',
  usage:'.ship [@user1 @user2]',
  async run(ctx) {
    if (!ctx.groupKey || !ctx.account?.sock) {
      return ctx.reply('This one is for groups.')
    }

    let metadata
    try {
      metadata = await ctx.account.sock.groupMetadata(ctx.groupKey)
    } catch {
      return ctx.reply('I could not read the group members right now.')
    }

    const members = [...new Set(
      (metadata?.participants || []).map(participantJid).filter(Boolean)
    )]
    if (members.length < 2) return ctx.reply('I need at least two members to ship.')

    const mentioned = [...new Set(
      (contextInfo(ctx.message?.message)?.mentionedJid || [])
        .map(String)
        .filter(jid => members.includes(jid))
    )]

    let first = ''
    let second = ''

    if (mentioned.length >= 2) {
      ;[first, second] = mentioned
    } else if (mentioned.length === 1) {
      first = mentioned[0]
      const pool = members.filter(jid => jid !== first)
      second = pool[Math.floor(Math.random() * pool.length)]
    } else {
      first = members[Math.floor(Math.random() * members.length)]
      const pool = members.filter(jid => jid !== first)
      second = pool[Math.floor(Math.random() * pool.length)]
    }

    return ctx.account.sock.sendMessage(ctx.groupKey, {
      text:mentionLabel(first) + ' ❤️ ' + mentionLabel(second) + '\nCongratulations 💖🍻',
      mentions:[first, second],
    }, { quoted:ctx.message })
  },
}
