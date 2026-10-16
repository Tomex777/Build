import { PLAY_URL, PlayError } from '../../night-play.js'

export default {
  name:'minigames',
  description:'Open NIGHT Play or securely connect your WhatsApp profile to the website.',
  usage:'.minigames [link CODE|profile|unlink]',
  async run(ctx) {
    const [action = '', ...args] = ctx.args || []
    if (!action) return ctx.reply('*NIGHT Play*\n' + PLAY_URL + '\n\nTap Connect WhatsApp on the website, then send its .minigames link command to me privately.')
    if (ctx.groupKey || String(ctx.message?.key?.remoteJid || '').endsWith('@g.us')) {
      return ctx.reply('Send this command to NIGHT privately. Generate a fresh code if you posted one in this group.')
    }
    try {
      if (action.toLowerCase() === 'link') {
        const profile = ctx.nightPlayLink(args.join(''))
        return ctx.reply('*Connected to NIGHT Play*\n' + profile.displayName + '\n\nReturn to the browser where you generated the code. Only link codes you requested yourself.\n\n.minigames unlink signs out all your website sessions.')
      }
      if (action.toLowerCase() === 'profile') {
        const profile = ctx.nightPlayProfile()
        return ctx.reply(profile ? '*NIGHT Play*\n' + profile.displayName + '\n' + PLAY_URL : 'You have not connected NIGHT Play yet.\n' + PLAY_URL)
      }
      if (action.toLowerCase() === 'unlink') {
        ctx.nightPlayUnlink()
        return ctx.reply('Your NIGHT Play website sessions are signed out.')
      }
      return ctx.reply('Use .minigames, .minigames link CODE, .minigames profile or .minigames unlink.')
    } catch (error) {
      return ctx.reply(error instanceof PlayError ? error.message : 'NIGHT Play could not connect right now. Please try again.')
    }
  },
}
