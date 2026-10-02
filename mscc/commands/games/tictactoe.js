import { randomUUID } from 'node:crypto'
import {
  TICTACTOE_BOT_ID,
  TICTACTOE_LEVELS,
  TicTacToeGame,
  normalizeTicTacToeLevel,
  parseTicTacToeInput,
  pickTicTacToeBotMove,
} from '../../utils/tictactoe-game.js'
import { renderTicTacToeBoard } from '../../utils/tictactoe-renderer.js'\nimport { gameMenuArt } from '../../utils/game-menu-art.js'
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
  if (player === TICTACTOE_BOT_ID) return 'MSCC Bot'
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

async function sendBoard(ctx, game, theme, note = '') {
  const chat = chatKey(ctx)
  if (!chat || !ctx.account?.sock) throw new Error('Tic-Tac-Toe connection is unavailable.')
  return ctx.account.sock.sendMessage(chat, {
    image:renderTicTacToeBoard(game, theme),
    caption:statusCaption(game, note),
  }, { quoted:ctx.message })
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

function newWaitingRecord(ctx, invitedUser = '') {
  const user = String(ctx.userKey || '')
  return {
    id:randomUUID(),
    state:'WAITING',
    mode:'human',
    createdBy:user,
    createdByName:playerName(ctx),
    invitedUser:String(invitedUser || ''),
    theme:getTicTacToeTheme(ctx.shared, user),
    game:new TicTacToeGame().toRecord(),
  }
}

async function createHumanChallenge(ctx, invitedUser = '') {
  if (!ctx.groupKey) {
    return ctx.reply('Human-vs-human Tic-Tac-Toe starts in a group chat. In a DM, choose *Play the bot*.')
  }

  const existing = loadRecord(ctx)
  if (existing) return ctx.reply('There is already a Tic-Tac-Toe game or challenge in this chat.')

  const record = saveRecord(ctx, newWaitingRecord(ctx, invitedUser))
  const prefix = String(ctx.publicPrefix || '.')
  const invitedLine = invitedUser
    ? 'This challenge is reserved for the player you selected.'
    : 'Anyone else in this group can join.'

  return ctx.ui.joinCancel({
    title:'Tic-Tac-Toe challenge',
    text:`${playerName(ctx)} opened a game.\n\n${invitedLine}`,
    buttonText:'Tic-Tac-Toe',
    joinText:'Join game',
    joinDescription:'X and O are assigned randomly',
    joinId:`${prefix}ttt ~join ${record.id}`,
    cancelText:'Cancel challenge',
    cancelDescription:'Only the challenger can cancel',
    cancelId:`${prefix}ttt ~cancel ${record.id}`,
  })
}

async function joinHumanChallenge(ctx, id) {
  const record = loadRecord(ctx)
  const user = String(ctx.userKey || '')

  if (!record || record.state !== 'WAITING' || record.mode !== 'human' || record.id !== id) {
    return ctx.reply('That Tic-Tac-Toe challenge is no longer available.')
  }
  if (record.createdBy === user) return ctx.reply('You cannot join your own challenge.')
  if (record.invitedUser && record.invitedUser !== user) {
    return ctx.reply('That challenge was opened for someone else.')
  }

  const challenger = String(record.createdBy || '')
  const challengerName = String(record.createdByName || 'Challenger')
  const joinerName = playerName(ctx)
  const challengerIsX = Math.random() < 0.5
  const game = new TicTacToeGame({
    playerX:challengerIsX ? challenger : user,
    playerO:challengerIsX ? user : challenger,
    xName:challengerIsX ? challengerName : joinerName,
    oName:challengerIsX ? joinerName : challengerName,
  })

  saveRecord(ctx, {
    ...record,
    state:'PLAYING',
    game:game.toRecord(),
  })

  return sendBoard(
    ctx,
    game,
    normalizeTicTacToeTheme(record.theme),
    `Sides randomized — ${game.xName} is X, ${game.oName} is O. X moves first.`,
  )
}

async function cancelChallenge(ctx, id = '') {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'WAITING') return ctx.reply('There is no waiting Tic-Tac-Toe challenge.')
  if (id && record.id !== id) return ctx.reply('That challenge has expired.')
  if (record.createdBy !== String(ctx.userKey || '')) return ctx.reply('Only the challenger can cancel it.')
  clearRecord(ctx)
  return ctx.reply('Tic-Tac-Toe challenge cancelled.')
}

async function startBotGame(ctx, level) {
  if (loadRecord(ctx)) return ctx.reply('There is already an active Tic-Tac-Toe game in this chat.')

  const user = String(ctx.userKey || '')
  const name = playerName(ctx)
  const userIsX = Math.random() < 0.5
  const game = new TicTacToeGame({
    playerX:userIsX ? user : TICTACTOE_BOT_ID,
    playerO:userIsX ? TICTACTOE_BOT_ID : user,
    xName:userIsX ? name : 'MSCC Bot',
    oName:userIsX ? 'MSCC Bot' : name,
  })
  const theme = getTicTacToeTheme(ctx.shared, user)

  let note = `Bot difficulty: ${level}. You are ${userIsX ? 'X' : 'O'}.`
  if (!userIsX) {
    const botIndex = pickTicTacToeBotMove(game, level)
    if (botIndex >= 0) {
      const result = game.move(TICTACTOE_BOT_ID, botIndex)
      if (!result.ok) throw new Error(result.reason || 'Bot opening move failed.')
      note += ` MSCC Bot is X and opened on ${botIndex + 1}.`
    }
  }

  saveRecord(ctx, {
    id:randomUUID(),
    state:'PLAYING',
    mode:'bot',
    level,
    createdBy:user,
    theme,
    game:game.toRecord(),
  })

  return sendBoard(ctx, game, theme, note)
}

async function resignGame(ctx, game) {
  const user = String(ctx.userKey || '')
  if (![game.playerX, game.playerO].includes(user)) return false
  const winner = user === game.playerX ? game.playerO : game.playerX
  clearRecord(ctx)
  return ctx.reply(`🏳️ ${labelFor(game, user)} resigned. ${labelFor(game, winner)} wins.`)
}

async function handleMoveInput(ctx, text) {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'PLAYING') return false

  const game = new TicTacToeGame(record.game)
  const user = String(ctx.userKey || '')
  if (![game.playerX, game.playerO].includes(user)) return false

  const parsed = parseTicTacToeInput(text)
  if (!parsed) return false
  if (parsed.type === 'resign') return resignGame(ctx, game)

  const result = game.move(user, parsed.index)
  if (!result.ok) return ctx.reply(result.reason)

  const theme = normalizeTicTacToeTheme(record.theme)
  const humanNote = `${labelFor(game, user)} played ${parsed.index + 1}.`

  if (game.winner || game.isDraw) {
    clearRecord(ctx)
    await sendBoard(ctx, game, theme, humanNote)
    return true
  }

  if (record.mode === 'bot') {
    saveRecord(ctx, { ...record, game:game.toRecord() })
    await sendBoard(ctx, game, theme, humanNote)

    const botIndex = pickTicTacToeBotMove(game, record.level || 'normal')
    if (botIndex >= 0) {
      const botResult = game.move(TICTACTOE_BOT_ID, botIndex)
      if (!botResult.ok) throw new Error(botResult.reason || 'Bot move failed.')
    }

    if (game.winner || game.isDraw) clearRecord(ctx)
    else saveRecord(ctx, { ...record, game:game.toRecord() })

    await sendBoard(
      ctx,
      game,
      theme,
      botIndex >= 0 ? `MSCC Bot played ${botIndex + 1}.` : 'MSCC Bot has no move.',
    )
    return true
  }

  saveRecord(ctx, { ...record, game:game.toRecord() })
  await sendBoard(ctx, game, theme, humanNote)
  return true
}

async function showCurrentGame(ctx, record) {
  if (record.state === 'WAITING') {
    return ctx.reply('A Tic-Tac-Toe challenge is waiting in this chat. Someone else can tap *Join game*.')
  }
  const game = new TicTacToeGame(record.game)
  return sendBoard(ctx, game, normalizeTicTacToeTheme(record.theme))
}

export default {
  name:'tictactoe',
  aliases:['ttt','xo'],
  description:'Play visual Tic-Tac-Toe against another person or the bot.',
  usage:'.ttt',
  async run(ctx) {
    try {
      const args = Array.isArray(ctx.args) ? ctx.args.map(String) : []
      const first = String(args[0] || '').toLowerCase()
      const existing = loadRecord(ctx)

      if (first === '~input') {
        return handleMoveInput(ctx, String(ctx.commandReplyInput || args.slice(1).join(' ')))
      }
      if (first === '~person') return createHumanChallenge(ctx)
      if (first === '~join') return joinHumanChallenge(ctx, String(args[1] || ''))
      if (first === '~cancel') return cancelChallenge(ctx, String(args[1] || ''))
      if (first === '~bot') return showBotLevels(ctx)
      if (first === '~botlevel') {
        const level = normalizeTicTacToeLevel(args[1])
        return level ? startBotGame(ctx, level) : showBotLevels(ctx)
      }

      if (first === 'cancel') return cancelChallenge(ctx)
      if (first === 'bot') {
        const level = normalizeTicTacToeLevel(args[1])
        return level ? startBotGame(ctx, level) : showBotLevels(ctx)
      }
      if (first === 'person' || first === 'player' || first === 'human') return createHumanChallenge(ctx)

      if (args.length && parseTicTacToeInput(args.join(' '))) {
        return handleMoveInput(ctx, args.join(' '))
      }

      if (args.length && typeof ctx.resolveCommandTarget === 'function') {
        const target = await ctx.resolveCommandTarget(args[0])
        if (target?.phoneNumber) return createHumanChallenge(ctx, target.phoneNumber)
      }

      if (existing) return showCurrentGame(ctx, existing)
      return showModePicker(ctx)
    } catch (error) {
      console.error('MSCC Tic-Tac-Toe failed:', error)
      return ctx.reply('I could not continue that Tic-Tac-Toe game.')
    }
  },
}
