import {
  SONGS, newRound, readRound, endRound, remaining, chatKey,
} from '../../utils/party-games.js'
import {
  activeOtherChallenge, gameGuess, gameAdmin, gameMenuText, gameRoundStatus,
} from '../../utils/party-game-command.js'
import { scoreText } from '../../utils/party-games.js'
import { melodyAudio } from '../../utils/party-melodies.js'

export default {
  name: 'guesssong',
  description: 'Guess a melody in your WhatsApp group. Public-domain, synthesized clips; no paid song APIs.',
  usage: '.guesssong [start|answer <title>|hint|score|skip|stop|help]',
  async run(ctx) {
    try {
      const args=Array.isArray(ctx.args)?ctx.args:[]
      const action=String(args[0]||'').toLowerCase()
      const round=readRound(ctx,'guesssong')
      if(action==='help')return ctx.reply(gameMenuText('guesssong'))
      if(action==='score'||action==='scores')return ctx.reply(scoreText(ctx,'guesssong'))
      if(action==='~input'||action==='answer'||action==='guess') {
        return gameGuess(ctx,'guesssong',
          action==='~input'?String(ctx.commandReplyInput||args.slice(1).join(' ')):args.slice(1).join(' '),
          {silentWrong:action==='~input'})
      }
      if(action==='skip'||action==='stop'||action==='cancel') return gameAdmin(ctx,'guesssong',action,round)
      if(action==='hint') {
        if(!round)return ctx.reply('No active song round. Start with .guesssong.')
        if(round.hints>=2)return ctx.reply('No further hints this round. '+remaining(round)+'s left.')
        const row=SONGS.find(x=>x.id===round.selectedId)
        const hint=round.hints===0?row?.hint:row?.hint2
        round.hints+=1
        ctx.shared?.set('night-party-guesssong',chatKey(ctx),round)
        return ctx.reply('💡 *Hint '+round.hints+'/2:* '+hint+'\n'+remaining(round)+'s remaining. (-25 points per hint)')
      }
      if(action&&!['start','new'].includes(action)) return gameGuess(ctx,'guesssong',args.join(' '))
      if(round) return ctx.reply(gameRoundStatus('guesssong',round))
      const conflict=activeOtherChallenge(ctx,'guesssong')
      if(conflict)return ctx.reply('Finish the current '+conflict+' round first.')
      const created=newRound(ctx,'guesssong')
      try {
        const clip=await melodyAudio(created.choice.id)
        const jid=ctx.message?.key?.remoteJid
        if(!jid||!ctx.account?.sock)throw new Error('WhatsApp connection unavailable.')
        return await ctx.account.sock.sendMessage(jid,{
          audio:clip,mimetype:'audio/mpeg',ptt:false,
        },{quoted:ctx.message}).then(async ()=>{
          return ctx.reply([
            '🎵 *GUESS THE SONG!*',
            '🎹 Listen to the melody above. What is the song called?',
            '🎼 These are synthesized public-domain tunes, not original commercial recordings.',
            '⏳ 90 seconds • 100 pts (less if hints used)',
            '💡 .guesssong hint • 🏆 .guesssong score',
            'Send the title as a regular message or use .guesssong answer <title>.'
          ].join('\n'))
        })
      } catch(error) {
        endRound(ctx,'guesssong')
        throw error
      }
    } catch(error) {return ctx.reply(error?.message||'Guess the Song round could not start.')}
  }
}
