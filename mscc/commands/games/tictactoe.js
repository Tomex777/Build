import { randomUUID } from 'node:crypto'
import {
  TICTACTOE_BOT_ID,
  TICTACTOE_LEVELS,
  TicTacToeGame,
  normalizeTicTacToeLevel,
  parseTicTacToeInput,
  renderNexiaTicTacToeText,
  ticTacToeCoord,
  pickTicTacToeBotMove,
} from '../../utils/tictactoe-game.js'
import { renderTicTacToeBoard,renderNexiaTicTacToePicture } from '../../utils/tictactoe-renderer.js'
import { gameMenuArt } from '../../utils/game-menu-art.js'
import { findOtherActiveGame, otherGameMessage, userForStoredGame } from '../../utils/game-session.js'
import { getTicTacToeTheme, normalizeTicTacToeTheme } from '../../utils/game-themes.js'

const NAMESPACE = 'tictactoe-game'
const WAITING_TTL = 15 * 60 * 1000
const PLAYING_TTL = 12 * 60 * 60 * 1000

function chatKey(ctx) {
  return String(ctx.message?.key?.remoteJid || '').trim()
}

function playerName(ctx) {
  return String(ctx.message?.pushName || ctx.userKey || 'Player').trim().slice(0, 80) || 'Player'
}

function loadRecord(ctx) {
  const key = chatKey(ctx)
  if (!key) return null
  const record = ctx.shared?.get(NAMESPACE, key)
  if (!record) return null
  if (Number(record.expiresAt || 0) && Date.now() > Number(record.expiresAt)) {
    ctx.shared?.delete(NAMESPACE, key)
    return null
  }
  return record
}

function saveRecord(ctx, record) {
  const key = chatKey(ctx)
  if (!key) throw new Error('Tic-Tac-Toe chat is unavailable.')
  const ttl = record.state === 'WAITING' ? WAITING_TTL : PLAYING_TTL
  const next = {
    ...record,
    updatedAt:Date.now(),
    expiresAt:Date.now() + ttl,
  }
  ctx.shared?.set(NAMESPACE, key, next)
  return next
}

function clearRecord(ctx) {
  const key = chatKey(ctx)
  if (key) ctx.shared?.delete(NAMESPACE, key)
}

function labelFor(game, player) {
  if (player === TICTACTOE_BOT_ID) return '🤖 Bot'
  if (player === game.playerX) return game.xName || 'X'
  if (player === game.playerO) return game.oName || 'O'
  return 'Player'
}

function statusCaption(game, note = '') {
  let status
  if (game.winner) {
    status = `🏆 ${labelFor(game, game.winner)} wins.`
  } else if (game.isDraw) {
    status = '🤝 Draw.'
  } else {
    status = `Turn: ${labelFor(game, game.currentTurn)} (${game.markFor(game.currentTurn)})`
  }

  return [
    '*Tic-Tac-Toe*',
    note,
    status,
    '',
    `❌ ${game.xName || 'X'}`,
    `⭕ ${game.oName || 'O'}`,
    '',
    game.winner || game.isDraw ? '' : 'Send a number from *1–9* to place your mark.',
  ].filter(Boolean).join('\n')
}

function classicCaption(game,note='') {
  const cells=renderNexiaTicTacToeText(game)
  const status=game.winner
    ? '🎉 '+labelFor(game,game.winner)+' wins the game!'
    : game.isDraw
      ? '🤝 Game ended in a draw!'
      : '🎲 Turn: '+labelFor(game,game.currentTurn)+' ('+
        (game.markFor(game.currentTurn)==='X'?'❎':'⭕')+')'
  return [
    '🎮 *TicTacToe Game*',
    note,
    status,
    '',
    cells,
    '',
    '▢ Player ❎: '+labelFor(game,game.playerX),
    '▢ Player ⭕: '+labelFor(game,game.playerO),
    '',
    game.winner||game.isDraw ? '' : '• Type a number (1-9) to make your move',
    game.winner||game.isDraw ? '' : '• Type *surrender* to give up',
  ].filter(Boolean).join('\n')
}

function pictureCaption(game,note='') {
  const status=game.winner ? '🎉 '+labelFor(game,game.winner)+' wins the game!'
    :game.isDraw ? '🤝 Game ended in a draw!'
    :'🎲 Turn: '+labelFor(game,game.currentTurn)+' ('+
       (game.markFor(game.currentTurn)==='X'?'❎':'⭕')+')'
  return [
    '🎮 *Picture Tic-Tac-Toe*',
    note,status,'',
    '▢ Player ❎: '+labelFor(game,game.playerX),
    '▢ Player ⭕: '+labelFor(game,game.playerO),
    '',
    game.winner||game.isDraw ? '' : '• Send a square like *B2* to move',
    game.winner||game.isDraw ? '' : '• Type *surrender* to give up',
  ].filter(Boolean).join('\n')
}

// A classic Nexia .ttt game sends emoji text, not a picture. The separate
// picture mode sends Nexia's cached 592px labelled JPEG board.
async function sendBoard(ctx,game,theme,note='',style='picture') {
  const chat=chatKey(ctx)
  if(!chat||!ctx.account?.sock)throw new Error('Tic-Tac-Toe connection is unavailable.')
  const payload=style==='classic'
    ? {text:classicCaption(game,note)}
    : {image:renderNexiaTicTacToePicture(game),caption:pictureCaption(game,note)}
  return ctx.account.sock.sendMessage(chat,payload,{quoted:ctx.message})
}

async function showModePicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Tic-Tac-Toe',
    text:'Who do you want to play?',
    caption:'Who do you want to play?',
    image:await gameMenuArt('tictactoe'),
    buttonText:'Choose opponent',
    rows:[
      {
        title:'👤 Play a person',
        description:'Open a challenge in this chat',
        id:`${prefix}ttt ~person`,
      },
      {
        title:'🤖 Play the bot',
        description:'Choose a difficulty and play now',
        id:`${prefix}ttt ~bot`,
      },
    ],
  })
}

async function showBotLevels(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Tic-Tac-Toe vs Bot',
    text:'Choose the bot difficulty.',
    buttonText:'Choose difficulty',
    rows:TICTACTOE_LEVELS.map(level => ({
      title:level.label,
      description:level.description,
      id:`${prefix}ttt ~botlevel ${level.id}`,
    })),
  })
}

function newWaitingRecord(ctx,invitedUser='',style='classic',roomName='') {
  const user=String(ctx.userKey||'')
  return {
    id:'tictactoe-'+randomUUID(),
    state:'WAITING',
    style,
    roomName,
    mode:'human',
    createdBy:user,
    createdByName:playerName(ctx),
    invitedUser:String(invitedUser||''),
    theme:getTicTacToeTheme(ctx.shared,user),
    game:new TicTacToeGame().toRecord(),
  }
}

async function createHumanChallenge(ctx,invitedUser='',style='classic',roomName='') {
  if(!ctx.groupKey)
    return ctx.reply('Human-vs-human Tic-Tac-Toe starts in a group chat. In a DM, try *.tttpic bot*.')
  const existing=loadRecord(ctx)
  if(existing) return ctx.reply('There is already a Tic-Tac-Toe game or challenge in this chat.')
  const conflict=findOtherActiveGame(ctx,NAMESPACE)
  if(conflict)return ctx.reply(otherGameMessage(conflict))
  const record=saveRecord(ctx,newWaitingRecord(ctx,invitedUser,style,roomName))
  const command=style==='picture'?'tttpic':'ttt'
  const name=roomName?' '+roomName:''
  return ctx.reply('⏳ *Waiting for opponent*\nType *.'+command+name+'* to join!\n'+
    '▢ *Room ID:* '+record.id+
    (invitedUser?'\nThis challenge is reserved for your chosen player.':''))
}

async function joinHumanChallenge(ctx,id='',style='',name='') {
  const record=loadRecord(ctx)
  const user=String(ctx.userKey||'')
  if(!record||record.state!=='WAITING'||record.mode!=='human'||
     (id&&record.id!==id))
    return ctx.reply('That Tic-Tac-Toe challenge is no longer available.')
  if(style&&record.style!==style)
    return ctx.reply('That room is for *.'+(record.style==='picture'?'tttpic':'ttt')+'*.')
  if(record.roomName!==name&& !id)
    return ctx.reply('That room has a different name. Join with *.'+
      (record.style==='picture'?'tttpic':'ttt')+
      (record.roomName?' '+record.roomName:'')+'*.')
  if(record.createdBy===user)return ctx.reply('You cannot join your own challenge.')
  if(record.invitedUser&&record.invitedUser!==user)
    return ctx.reply('That challenge was opened for someone else.')
  // Nexia assigns X to the room creator and O to the joining player.
  const game=new TicTacToeGame({
    playerX:String(record.createdBy),
    playerO:user,
    xName:String(record.createdByName||'Challenger'),
    oName:playerName(ctx),
  })
  saveRecord(ctx,{...record,state:'PLAYING',game:game.toRecord()})
  const instructions=record.style==='picture'
    ? 'Send a square like *B2* (columns A–C, rows 1–3).'
    : 'Send a number (1–9) to place your symbol.'
  return sendBoard(ctx,game,record.theme,
    '🎮 Game started! '+game.xName+' is ❎ and moves first.\n'+
    '▢ *Room ID:* '+record.id+'\n'+instructions,
    record.style||'classic')
}

async function cancelChallenge(ctx, id = '') {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'WAITING') return ctx.reply('There is no waiting Tic-Tac-Toe challenge.')
  if (id && record.id !== id) return ctx.reply('That challenge has expired.')
  if (record.createdBy !== String(ctx.userKey || '')) return ctx.reply('Only the challenger can cancel it.')
  clearRecord(ctx)
  return ctx.reply('Tic-Tac-Toe challenge cancelled.')
}

async function startBotGame(ctx,level) {
  if(loadRecord(ctx))return ctx.reply('There is already an active Tic-Tac-Toe game in this chat.')
  const conflict=findOtherActiveGame(ctx,NAMESPACE)
  if(conflict)return ctx.reply(otherGameMessage(conflict))
  const user=String(ctx.userKey||'')
  const game=new TicTacToeGame({
    playerX:user,
    playerO:TICTACTOE_BOT_ID,
    xName:playerName(ctx),
    oName:'🤖 Bot',
  })
  const theme=getTicTacToeTheme(ctx.shared,user)
  saveRecord(ctx,{
    id:'tttpic-'+randomUUID(),
    state:'PLAYING',
    mode:'bot',
    style:'picture',
    level,
    createdBy:user,
    theme,
    game:game.toRecord(),
  })
  return sendBoard(ctx,game,theme,
    "You're ❎, 🤖 Bot is ⭕. Your move first.\nBot difficulty: *"+level+
    '*.\nSend *B2* to move. Type *surrender* to quit.',
    'picture')
}

async function resignGame(ctx, game) {
  const user = String(ctx.userKey || '')
  if (![game.playerX, game.playerO].includes(user)) return false
  const winner = user === game.playerX ? game.playerO : game.playerX
  clearRecord(ctx)
  return ctx.reply(`🏳️ ${labelFor(game, user)} resigned. ${labelFor(game, winner)} wins.`)
}

async function handleMoveInput(ctx,text) {
  const record=loadRecord(ctx)
  if(!record||record.state!=='PLAYING')return false
  const game=new TicTacToeGame(record.game)
  const user=String(ctx.userKey||'')
  if(![game.playerX,game.playerO].includes(user))return false
  const style=record.style||'picture' // legacy saved NIGHT boards were images
  const parsed=parseTicTacToeInput(text,style)
  if(!parsed)return false
  if(parsed.type==='resign')return resignGame(ctx,game)
  const result=game.move(user,parsed.index)
  if(!result.ok)return ctx.reply(result.reason)
  const theme=normalizeTicTacToeTheme(record.theme)
  let note=labelFor(game,user)+' played '+
    (style==='picture'?ticTacToeCoord(parsed.index):parsed.index+1)+'.'
  // Nexia's picture bot responds in the SAME board image, not two consecutive
  // images that flood WhatsApp and make the animation feel laggy.
  if(!game.winner&&!game.isDraw&&record.mode==='bot'){
    const botIndex=pickTicTacToeBotMove(game,record.level||'expert')
    if(botIndex>=0){
      const played=game.move(TICTACTOE_BOT_ID,botIndex)
      if(!played.ok)throw Error(played.reason||'Bot move failed.')
      note+='\n🤖 Bot played *'+ticTacToeCoord(botIndex)+'*.'
    }
  }
  if(!game.winner&&!game.isDraw)
    saveRecord(ctx,{...record,game:game.toRecord()})
  await sendBoard(ctx,game,theme,note,style)
  if(game.winner||game.isDraw)clearRecord(ctx)
  return true
}

async function showCurrentGame(ctx,record) {
  if(record.state==='WAITING'){
    const command=record.style==='picture'?'tttpic':'ttt'
    return ctx.reply('⏳ *Waiting for opponent*\nType *.'+command+
      (record.roomName?' '+record.roomName:'')+'* to join!\n'+
      'Room ID: '+record.id)
  }
  return sendBoard(ctx,new TicTacToeGame(record.game),normalizeTicTacToeTheme(record.theme),'',record.style||'picture')
}

export default {
  name:'tictactoe',
  aliases:['ttt','xo','tttpic','tttboard','imgttt','picttt'],
  description:'Play Nexia-style emoji Tic-Tac-Toe, or a picture game with five bot levels.',
  usage:'.ttt [room name] | .tttpic [room name] | .tttpic bot [beginner|easy|medium|hard|expert]',
  async run(ctx) {
    try {
      const args=Array.isArray(ctx.args)?ctx.args.map(String):[]
      const first=String(args[0]||'').toLowerCase()
      const invoked=String(ctx.rawCommandText||'').trim().match(/^[^\w\s]?([a-z]+)/i)?.[1]?.toLowerCase()
      const requestedStyle=['tttpic','tttboard','imgttt','picttt'].includes(invoked)?'picture':'classic'
      const existing=loadRecord(ctx)
      if(existing)ctx={...ctx,userKey:userForStoredGame(ctx,existing)}
      if(first==='~input')
        return handleMoveInput(ctx,String(ctx.commandReplyInput||args.slice(1).join(' ')))
      if(first==='~person')return createHumanChallenge(ctx)
      if(first==='~join')return joinHumanChallenge(ctx,String(args[1]||''))
      if(first==='~cancel')return cancelChallenge(ctx,String(args[1]||''))
      if(first==='~bot')return showBotLevels(ctx)
      if(first==='~botlevel'){
        const level=normalizeTicTacToeLevel(args[1])
        return level?startBotGame(ctx,level):showBotLevels(ctx)
      }
      if(first==='cancel')return cancelChallenge(ctx)
      if(first==='menu'||first==='mode')return showModePicker(ctx)
      if(first==='bot'){
        const level=normalizeTicTacToeLevel(args[1])
        return level?startBotGame(ctx,level):showBotLevels(ctx)
      }
      if(first==='person'||first==='player'||first==='human')
        return createHumanChallenge(ctx,'',requestedStyle,args.slice(1).join(' ').trim())
      const input=args.join(' ')
      if(input&&parseTicTacToeInput(input,existing?.style||requestedStyle))
        return handleMoveInput(ctx,input)
      if(input&&typeof ctx.resolveCommandTarget==='function'&&input.startsWith('@')){
        const target=await ctx.resolveCommandTarget(input)
        if(target?.phoneNumber)return createHumanChallenge(ctx,target.phoneNumber,requestedStyle)
      }
      // Nexia: sending .ttt with no parameters joins the existing unnamed
      // WAITING room. Named rooms are joined using the exact room name.
      if(existing?.state==='WAITING'){
        if(String(existing.createdBy)===String(ctx.userKey))
          return showCurrentGame(ctx,existing)
        return joinHumanChallenge(ctx,'',requestedStyle,input.trim())
      }
      if(existing)return showCurrentGame(ctx,existing)
      return createHumanChallenge(ctx,'',requestedStyle,input.trim())
    } catch(error) {
      console.error('NIGHT Nexia Tic-Tac-Toe failed:',error)
      return ctx.reply('I could not continue that Tic-Tac-Toe game.')
    }
  },
}
