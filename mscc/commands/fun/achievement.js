import { renderAchievementCard } from '../../utils/fun-card-renderer.js'

export default {
  name:'achievement',
  aliases:['achievementunlocked'],
  description:'Create an achievement-unlocked card.',
  usage:'.achievement <text>',
  async run(ctx) {
    const text = ctx.args.join(' ').trim()
    if (!text) return ctx.reply('Use .achievement <text>.')
    const image = await renderAchievementCard({ text })
    const chat = ctx.message?.key?.remoteJid
    return ctx.account.sock.sendMessage(chat, { image, mimetype:'image/png' }, { quoted:ctx.message })
  },
}
