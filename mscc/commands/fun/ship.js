const participantJid = participant =>
  typeof participant === 'string'
    ? participant
    : participant?.id || participant?.jid || participant?.lid || ''

const mentionLabel = jid => '@' + String(jid || '').split('@')[0].split(':')[0]

export default {
  name: 'ship',
  description: 'Randomly ship two different members of the group.',
  usage: '.ship',
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

    const first = members[Math.floor(Math.random() * members.length)]
    let second = first
    while (second === first) {
      second = members[Math.floor(Math.random() * members.length)]
    }

    return ctx.account.sock.sendMessage(ctx.groupKey, {
      text:`${mentionLabel(first)} ❤️ ${mentionLabel(second)}\nCongratulations 💖🍻`,
      mentions:[first, second],
    })
  },
}
