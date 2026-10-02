import { contextInfo } from '../../utils/whatsapp/messages.js'

const COMPLIMENTS = [
  "You're amazing just the way you are!",
  'You have a great sense of humor!',
  "You're incredibly thoughtful and kind.",
  'You are more powerful than you know.',
  'You light up the room!',
  "You're a true friend.",
  'You inspire people.',
  'Your creativity knows no bounds!',
  'You have a heart of gold.',
  'You make a difference in the world.',
  'Your positivity is contagious!',
  'You bring out the best in people.',
  "You're stronger than you think!",
  'Your laughter is infectious.',
  'You have a natural gift for making people feel valued.',
  'You make the world better just by being in it.',
]

const pick = values => values[Math.floor(Math.random() * values.length)]

function rawTarget(ctx) {
  const info = contextInfo(ctx.message?.message)
  const mentioned = Array.isArray(info?.mentionedJid) ? info.mentionedJid.filter(Boolean) : []
  if (mentioned[0]) return mentioned[0]
  if (info?.stanzaId && info?.participant) return info.participant
  return ''
}

export default {
  name: 'compliment',
  description: 'Send a random compliment to someone.',
  usage: '.compliment @user',
  help: 'Mention someone or reply to their message.',
  async run(ctx) {
    let target = rawTarget(ctx)
    if (!target && typeof ctx.resolveCommandTarget === 'function') {
      const resolved = await ctx.resolveCommandTarget(ctx.args[0] || '')
      if (resolved?.phoneNumber) target = resolved.phoneNumber + '@s.whatsapp.net'
    }

    if (!target) {
      return ctx.reply(`Mention someone or reply to their message with ${ctx.publicPrefix || '.'}compliment.`)
    }

    const chat = ctx.message?.key?.remoteJid
    if (!chat || !ctx.account?.sock) return
    const label = String(target).split('@')[0].split(':')[0]
    return ctx.account.sock.sendMessage(chat, {
      text:`Hey @${label}, ${pick(COMPLIMENTS)}`,
      mentions:[target],
    })
  },
}
