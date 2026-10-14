import { ANIME, newRound, readRound, chatKey, scoreText, remaining } from '../../utils/party-games.js'
import { activeOtherChallenge, gameGuess, gameAdmin, gameMenuText, gameRoundStatus } from '../../utils/party-game-command.js'

export default {
  name:'guessanime',
  description:'Guess the anime from a spoiler-light story clue. Timed group challenge with scores.',
  usage:'.guessanime [start|answer <title>|hint|score|skip|stop|help]',
  async run(ctx) {
    try {
      const args=Array.isArray(ctx.args)?ctx.args:[]
      const action=String(args[0]||'').toLowerCase()
      const round=readRound(ctx,'guessanime')
      if(action==='help')return ctx.reply(gameMenuText('guessanime'))
      if(action==='score'||action==='scores')return ctx.reply(scoreText(ctx,'guessanime'))
      if(action==='~input'||action==='answer'||action==='guess')return gameGuess(ctx,'guessanime',
        action==='~input'?String(ctx.commandReplyInput||args.slice(1).join(' ')):args.slice(1).join(' '),
        {silentWrong:action==='~input'})
      if(action==='skip'||action==='stop'||action==='cancel')return gameAdmin(ctx,'guessanime',action,round)
      if(action==='hint'){
        if(!round)return ctx.reply('No active anime round. Try .guessanime.')
        if(round.hints>=2)return ctx.reply('No more hints for this round. '+remaining(round)+'s remaining.')
        const chosen=ANIME.find(item=>item.id===round.selectedId)
        const hint=chosen?.hints?.[round.hints] || 'No hint available.'
        round.hints+=1
        ctx.shared?.set('night-party-guessanime',chatKey(ctx),round)
        return ctx.reply('💡 *Hint '+round.hints+'/2:* '+hint+'\n'+remaining(round)+'s left. (-25 points per hint)')
      }
      if(action&&!['start','new'].includes(action))return gameGuess(ctx,'guessanime',args.join(' '))
      if(round)return ctx.reply(gameRoundStatus('guessanime',round))
      const conflict=activeOtherChallenge(ctx,'guessanime')
      if(conflict)return ctx.reply('Finish the current '+conflict+' round first.')
      const {record,choice}=newRound(ctx,'guessanime')
      try {
        return await ctx.reply([
          '🎌 *GUESS THE ANIME!*',
          '🧩 '+choice.clue,
          '',
          '⏳ 90 seconds • 100 pts (fewer if hints used)',
          '💡 .guessanime hint • 🏆 .guessanime score',
          'Type the anime title, or .guessanime answer <title>.'
        ].join('\n'))
      } catch(error){ctx.shared?.delete('night-party-guessanime',chatKey(ctx));throw error}
    } catch(error){return ctx.reply(error?.message||'Could not start Guess the Anime.')}
  }
}
