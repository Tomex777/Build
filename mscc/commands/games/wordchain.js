import { wordChainCommand } from '../../utils/word-chain.js'

export default {
  name:'wordchain',
  description:'Last-letter word game with joinable group lobbies. Untimed by default.',
  usage:'.wordchain [timed|duel [timed]|join|start|word <word>|status|best|stop|names on|off|help]',
  async run(ctx) {
    try { return await wordChainCommand(ctx) }
    catch(error) {
      console.error('[wordchain] failed:', error?.message)
      return ctx.reply('Word Chain could not process that turn. Please try again.')
    }
  },
}
