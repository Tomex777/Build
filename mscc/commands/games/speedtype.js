import { newRound,readRound,scoreText,chatKey } from '../../utils/party-games.js'
import { activeOtherChallenge,gameGuess,gameAdmin,gameRoundStatus } from '../../utils/party-game-command.js'

export default {
  name:'speedtype',
  description:'Race to accurately retype the displayed sentence in 35 seconds.',
  usage:'.speedtype [start|answer <sentence>|score|skip|stop]',
  async run(ctx) {
    try {
      const args=Array.isArray(ctx.args)?ctx.args:[]
      const action=String(args[0]||'').toLowerCase()
      const round=readRound(ctx,'speedtype')
      if(action==='score'||action==='scores')return ctx.reply(scoreText(ctx,'speedtype'))
      if(action==='~input'||action==='answer')return gameGuess(ctx,'speedtype',
        action==='~input'?String(ctx.commandReplyInput||args.slice(1).join(' ')):args.slice(1).join(' '),
        {silentWrong:action==='~input'})
      if(action==='skip'||action==='stop'||action==='cancel')return gameAdmin(ctx,'speedtype',action,round)
      if(action==='help')return ctx.reply([
        '⌨️ *Speed Typing*',
        '.speedtype — Start 35-second round',
        'Retype the displayed line as quickly and accurately as you can.',
        '.speedtype score — Group leaderboard',
        '.speedtype stop — End round (host only)',
        'Correct letter order matters. Capitalization and punctuation are ignored.'
      ].join('\n'))
      if(action&&action!=='start'&&action!=='new')return gameGuess(ctx,'speedtype',args.join(' '))
      if(round)return ctx.reply(gameRoundStatus('speedtype',round))
      const conflict=activeOtherChallenge(ctx,'speedtype')
      if(conflict)return ctx.reply('Finish the current '+conflict+' round first.')
      const {record,choice}=newRound(ctx,'speedtype')
      try {
        return await ctx.reply([
          '⚡ *SPEED TYPE!*',
          '',
          '📝 *'+choice.title+'*',
          '',
          '⏱️ 35 seconds — first correct response wins!',
          '🥇 Faster answers earn more points.',
          'Send the sentence as an ordinary message (do not quote it).',
          '🏆 .speedtype score'
        ].join('\n'))
      } catch(error){ctx.shared?.delete('night-party-speedtype',chatKey(ctx));throw error}
    } catch(error){return ctx.reply(error?.message||'Speed Typing failed.')}
  }
}
