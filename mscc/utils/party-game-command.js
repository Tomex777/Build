import {
  CHALLENGE_TYPES, GAME_LABELS, remaining, chatKey, NAMESPACE,
  readRound, isCorrectGuess, isCorrectSpeedline, endRound, canManage,
  addScore, scoreForRound, scoreText,
} from './party-games.js'

export function activeOtherChallenge(ctx,type) {
  const key=chatKey(ctx)
  for (const other of CHALLENGE_TYPES) {
    if (other===type) continue
    const record=ctx.shared?.get(NAMESPACE(other),key)
    if (record?.state==='PLAYING' && record.expiresAt>Date.now()) return GAME_LABELS[other]
  }
  return ''
}
export async function gameGuess(ctx,type,guess,{silentWrong=false}={}) {
  const record=readRound(ctx,type)
  if (!record) return ctx.reply('No active '+GAME_LABELS[type]+' round. Use .'+type+' to start.')
  const answer=String(guess||'').trim()
  if (!answer) return ctx.reply('Use .'+type+' answer <your guess>, or type the correct answer in the chat.')
  const correct=type==='speedtype' ? isCorrectSpeedline(record,answer) : isCorrectGuess(record,answer)
  if (!correct) return silentWrong ? null : ctx.reply('Not quite! Try again. ⏳ '+remaining(record)+'s left.')
  const user=String(ctx.userKey||'')
  if (!user) return ctx.reply('Unable to verify your player identity.')
  const name=String(ctx.message?.pushName||'Player').slice(0,50)
  const points=scoreForRound(record)
  endRound(ctx,type) // Since inbound commands process FIFO per chat, one player wins per round.
  const totals=addScore(ctx,type,user,name,points)
  return ctx.reply([
    '🏆 *'+name+' wins '+GAME_LABELS[type]+'!*',
    '✅ Answer: *'+record.answer+'*',
    '✨ +'+points+' points',
    '📊 Total: '+totals.score+' points ('+totals.wins+' wins)',
    'Play again: .'+type
  ].join('\n'))
}
export async function gameAdmin(ctx,type,action,record) {
  if(!record) return ctx.reply('No active '+GAME_LABELS[type]+' round.')
  if(!canManage(ctx,record)) return ctx.reply('Only the player who started the round can end or skip it.')
  endRound(ctx,type)
  return ctx.reply((action==='skip'?'⏭️ Round skipped.':'🛑 Round ended.')+'\nAnswer: *'+record.answer+'*')
}
export function gameMenuText(type) {
  return [
    '🎮 *'+GAME_LABELS[type]+'*',
    '.'+type+' — Start a new round',
    '.'+type+' answer <guess> — Guess the answer',
    '.'+type+' hint — Reveal a clue (song/anime only)',
    '.'+type+' score — View leaderboard',
    '.'+type+' skip — Reveal and end (host only)',
    '.'+type+' stop — End round (host only)',
    'Send the correct answer as ordinary text to win. No AI keys or paid APIs.'
  ].join('\n')
}
export function gameRoundStatus(type,record) {
  return [
    '🎯 *'+GAME_LABELS[type]+'* in progress.',
    '⏳ '+remaining(record)+'s remaining',
    'Try a guess: .'+type+' answer <guess>',
    'Or send the correct answer as a normal message.',
    'Need help? .'+type+' help'
  ].join('\n')
}
