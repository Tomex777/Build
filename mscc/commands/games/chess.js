import { randomUUID } from 'node:crypto'
import {
  CHESS_BOT_ID,
  CHESS_LEVELS,
  ChessGame,
  normalizeChessLevel,
  parseChessInput,
  pickChessBotMove,
} from '../../utils/chess-game.js'
import { renderChessBoard, renderChessMoveVideo } from '../../utils/chess-renderer.js'
import { getChessTheme, normalizeChessTheme } from '../../utils/game-themes.js'
import { gameMenuArt } from '../../utils/game-menu-art.js'
import { findOtherActiveGame, otherGameMessage } from '../../utils/game-session.js'

const NAMESPACE = 'chess-game'
const WAITING_TTL = 15 * 60 * 1000
const PLAYING_TTL = 24 * 60 * 60 * 1000

const RULES = [
  '*How to play*',
  '• Move: `e2 e4`',
  '• Optional piece hint: `Nf3 e5`',
  '• Preview legal moves: `f3` or `Nf3`',
  '• Promotion: `e7 e8Q`',
  '• Type `surrender` or `resign` to concede.',
].join('\n')

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
  if (!key) throw new Error('Chess chat is unavailable.')
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
  if (player === CHESS_BOT_ID) return 'MSCC Bot'
  if (player === game.playerWhite) return game.whiteName || 'White'
  if (player === game.playerBlack) return game.blackName || 'Black'
  return 'Player'
}

function statusCaption(game, note = '') {
  let status
  if (game.winner) {
    status = `🏆 ${labelFor(game, game.winner)} wins by checkmate.`
  } else if (game.isDraw) {
    status = `🤝 Draw — ${game.drawReason || 'draw'}.`
  } else {
    status = `Turn: ${labelFor(game, game.currentTurn)} (${game.chess.turn() === 'w' ? 'White' : 'Black'})`
    if (game.inCheck()) status += ' — CHECK'
  }

  return [
    '*Chess*',
    note,
    status,
    '',
    `⚪ ${game.whiteName || 'White'}`,
    `⚫ ${game.blackName || 'Black'}`,
    '',
    game.isGameOver() ? '' : 'Send a move like `e2 e4`.',
  ].filter(Boolean).join('\n')
}

async function sendBoard(ctx, game, note = '', preview = null, themeInput = {}) {
  const chat = chatKey(ctx)
  if (!chat || !ctx.account?.sock) throw new Error('Chess connection is unavailable.')
  const image = renderChessBoard(game, { ...(preview || {}), theme:normalizeChessTheme(themeInput) })
  return ctx.account.sock.sendMessage(chat, {
    image,
    caption:statusCaption(game, note),
  }, { quoted:ctx.message })
}

async function sendMoveAnimation(ctx, game, note = '', themeInput = {}) {
  const chat = chatKey(ctx)
  if (!chat || !ctx.account?.sock) throw new Error('Chess connection is unavailable.')

  try {
    const video = await renderChessMoveVideo(game, { theme:normalizeChessTheme(themeInput) })
    if (!video) return sendBoard(ctx, game, note, null, themeInput)
    return ctx.account.sock.sendMessage(chat, {
      video,
      mimetype:'video/mp4',
      gifPlayback:true,
      caption:statusCaption(game, note),
    }, { quoted:ctx.message })
  } catch (error) {
    console.warn('MSCC chess move animation fallback:', error?.message || error)
    return sendBoard(ctx, game, note, null, themeInput)
  }
}

async function showModePicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Chess',
    text:'Who do you want to play?',
    caption:'Who do you want to play?',
    image:await gameMenuArt('chess'),
    buttonText:'Choose opponent',
    rows:[
      {
        title:'👤 Play a person',
        description:'Open a challenge in this chat',
        id:`${prefix}chess ~person`,
      },
      {
        title:'🤖 Play the bot',
        description:'Choose a difficulty and play immediately',
        id:`${prefix}chess ~bot`,
      },
    ],
  })
}

async function showBotLevels(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Chess vs Bot',
    text:'Choose the bot difficulty.',
    buttonText:'Choose difficulty',
    rows:CHESS_LEVELS.map(level => ({
      title:level.label,
      description:level.description,
      id:`${prefix}chess ~botlevel ${level.id}`,
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
    theme:getChessTheme(ctx.shared, user),
    game:new ChessGame().toRecord(),
  }
}

async function createHumanChallenge(ctx, invitedUser = '') {
  if (!ctx.groupKey) {
    return ctx.reply('Human-vs-human chess starts in a group chat. In a DM, choose *Play the bot*.')
  }

  const existing = loadRecord(ctx)
  if (existing) {
    if (existing.state === 'WAITING' && existing.createdBy === String(ctx.userKey || '')) {
      return ctx.reply('You already have a chess challenge waiting in this chat. Use `.chess cancel` first if you want a new one.')
    }
    return ctx.reply('There is already an active chess game in this chat.')
  }

  const record = saveRecord(ctx, newWaitingRecord(ctx, invitedUser))
  const prefix = String(ctx.publicPrefix || '.')
  const invitedLine = invitedUser
    ? 'This challenge is reserved for the player you selected.'
    : 'Anyone else in this group can join.'

  return ctx.ui.joinCancel({
    title:'Chess challenge',
    text:`${playerName(ctx)} opened a chess game.\n\n${invitedLine}`,
    buttonText:'Chess',
    joinText:'♟️ Join game',
    joinDescription:'White and Black are assigned randomly',
    joinId:`${prefix}chess ~join ${record.id}`,
    cancelText:'✕ Cancel challenge',
    cancelDescription:'Only the challenger can cancel',
    cancelId:`${prefix}chess ~cancel ${record.id}`,
  })
}

async function joinHumanChallenge(ctx, id) {
  const record = loadRecord(ctx)
  const user = String(ctx.userKey || '')

  if (!record || record.state !== 'WAITING' || record.mode !== 'human' || record.id !== id) {
    return ctx.reply('That chess challenge is no longer available.')
  }
  if (record.createdBy === user) {
    return ctx.reply('You cannot join your own chess challenge.')
  }
  if (record.invitedUser && record.invitedUser !== user) {
    return ctx.reply('That chess challenge was opened for someone else.')
  }

  const challenger = String(record.createdBy || '')
  const challengerName = String(record.createdByName || 'Challenger')
  const joinerName = playerName(ctx)
  const challengerIsWhite = Math.random() < 0.5
  const game = new ChessGame({
    playerWhite:challengerIsWhite ? challenger : user,
    playerBlack:challengerIsWhite ? user : challenger,
    whiteName:challengerIsWhite ? challengerName : joinerName,
    blackName:challengerIsWhite ? joinerName : challengerName,
  })

  saveRecord(ctx, {
    ...record,
    state:'PLAYING',
    game:game.toRecord(),
  })

  await sendBoard(
    ctx,
    game,
    `Sides randomized — ${game.whiteName} is White, ${game.blackName} is Black. White moves first.`,
    null,
    record.theme,
  )
  return ctx.reply(RULES)
}

async function cancelChallenge(ctx, id = '') {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'WAITING') {
    return ctx.reply('There is no waiting chess challenge to cancel.')
  }
  if (id && record.id !== id) return ctx.reply('That chess challenge has expired.')
  if (record.createdBy !== String(ctx.userKey || '')) {
    return ctx.reply('Only the player who opened the challenge can cancel it.')
  }

  clearRecord(ctx)
  return ctx.reply('Chess challenge cancelled.')
}

async function startBotGame(ctx, level) {
  const existing = loadRecord(ctx)
  if (existing) return ctx.reply('There is already an active chess game in this chat.')

  const user = String(ctx.userKey || '')
  const name = playerName(ctx)
  const userIsWhite = Math.random() < 0.5
  const game = new ChessGame({
    playerWhite:userIsWhite ? user : CHESS_BOT_ID,
    playerBlack:userIsWhite ? CHESS_BOT_ID : user,
    whiteName:userIsWhite ? name : 'MSCC Bot',
    blackName:userIsWhite ? 'MSCC Bot' : name,
  })
  const theme = getChessTheme(ctx.shared, user)

  let note = `Bot difficulty: ${level}. You are ${userIsWhite ? 'White' : 'Black'}.`
  if (!userIsWhite) {
    const botMove = pickChessBotMove(game.chess, level)
    if (botMove) {
      const result = game.move(CHESS_BOT_ID, botMove)
      if (!result.ok) throw new Error(result.reason || 'Bot opening move failed.')
      note += ` MSCC Bot is White and opened ${botMove.from}→${botMove.to}.`
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

  await sendBoard(ctx, game, note, null, theme)
  return ctx.reply(RULES)
}

async function previewMove(ctx, record, game, parsed) {
  if (String(ctx.userKey || '') !== game.currentTurn) {
    return ctx.reply("It's not your turn.")
  }

  const piece = game.pieceAt(parsed.square)
  if (!piece) return ctx.reply(`There is no piece on ${parsed.square}.`)
  if (piece.color !== game.chess.turn()) return ctx.reply('That is not one of your pieces.')

  const targets = game.legalMovesFrom(parsed.square)
  await sendBoard(
    ctx,
    game,
    targets.length
      ? `${parsed.square} can move to: ${targets.join(', ')}`
      : `${parsed.square} has no legal moves.`,
    { previewTargets:targets, previewOrigin:parsed.square },
    record.theme,
  )
  return true
}

async function resignGame(ctx, record, game) {
  const user = String(ctx.userKey || '')
  if (![game.playerWhite, game.playerBlack].includes(user)) return false

  const winner = user === game.playerWhite ? game.playerBlack : game.playerWhite
  clearRecord(ctx)
  return ctx.reply(`🏳️ ${labelFor(game, user)} resigned. ${labelFor(game, winner)} wins.`)
}

async function handleMoveInput(ctx, text) {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'PLAYING') return false

  const game = new ChessGame(record.game)
  const user = String(ctx.userKey || '')
  if (![game.playerWhite, game.playerBlack].includes(user)) return false

  const parsed = parseChessInput(text)
  if (!parsed) return false
  if (parsed.type === 'resign') return resignGame(ctx, record, game)
  if (parsed.type === 'preview') return previewMove(ctx, record, game, parsed)

  const humanResult = game.move(user, parsed)
  if (!humanResult.ok) return ctx.reply(humanResult.reason)

  const humanNote = `${labelFor(game, user)} played ${parsed.from}→${parsed.to}.`

  if (game.isGameOver()) {
    clearRecord(ctx)
    await sendMoveAnimation(ctx, game, humanNote, record.theme)
    return true
  }

  if (record.mode === 'bot') {
    // Persist the human move before rendering/replying so a transient media
    // failure never loses legal game state.
    saveRecord(ctx, { ...record, game:game.toRecord() })
    await sendMoveAnimation(ctx, game, humanNote, record.theme)

    const botMove = pickChessBotMove(game.chess, record.level || 'medium')
    if (botMove) {
      const botResult = game.move(CHESS_BOT_ID, botMove)
      if (!botResult.ok) throw new Error(botResult.reason || 'Bot move failed.')
    }

    if (game.isGameOver()) clearRecord(ctx)
    else saveRecord(ctx, { ...record, game:game.toRecord() })

    await sendMoveAnimation(
      ctx,
      game,
      botMove
        ? `MSCC Bot played ${botMove.from}→${botMove.to}.`
        : 'MSCC Bot has no legal move.',
      record.theme,
    )
    return true
  }

  saveRecord(ctx, { ...record, game:game.toRecord() })
  await sendMoveAnimation(ctx, game, humanNote, record.theme)
  return true
}

async function showCurrentGame(ctx, record) {
  if (record.state === 'WAITING') {
    return ctx.reply('A chess challenge is waiting in this chat. Someone else can tap *Join game* on the challenge message.')
  }
  const game = new ChessGame(record.game)
  return sendBoard(ctx, game, '', null, record.theme)
}

export default {
  name:'chess',
  aliases:['chessgame'],
  description:'Play visual chess against another person or the bot.',
  usage:'.chess',
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
        const level = normalizeChessLevel(args[1])
        if (!level) return showBotLevels(ctx)
        return startBotGame(ctx, level)
      }

      if (first === 'cancel') return cancelChallenge(ctx)
      if (first === 'rules') return ctx.reply(RULES)
      if (first === 'bot') {
        const level = normalizeChessLevel(args[1]) || 'medium'
        return startBotGame(ctx, level)
      }
      if (first === 'person' || first === 'player' || first === 'human') {
        return createHumanChallenge(ctx)
      }

      if (args.length && parseChessInput(args.join(' '))) {
        return handleMoveInput(ctx, args.join(' '))
      }

      if (args.length && typeof ctx.resolveCommandTarget === 'function') {
        const target = await ctx.resolveCommandTarget(args[0])
        if (target?.phoneNumber) return createHumanChallenge(ctx, target.phoneNumber)
      }

      if (existing) return showCurrentGame(ctx, existing)
      return showModePicker(ctx)
    } catch (error) {
      console.error('MSCC chess failed:', error)
      return ctx.reply('I could not continue that chess game.')
    }
  },
}
