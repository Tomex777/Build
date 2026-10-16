import { wordChainCommand } from '../../utils/word-chain.js'

export default {
  name:'wordchain',
  description:'A timed last-letter word chain. Group marathon and head-to-head duels.',
  usage:'.wordchain [duel|join|word <word>|status|best|stop|names on|off|help]',
  async run(ctx) {
    try { return await wordChainCommand(ctx) }
    catch(error) {
      console.error('[wordchain] failed:', error?.message)
      return ctx.reply('Word Chain could not process that turn. Please try again.')
    }
  },
}
