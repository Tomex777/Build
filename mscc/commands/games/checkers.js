import { randomUUID } from 'node:crypto'
import {
  CHECKERS_BOT_ID,
  CHECKERS_LEVELS,
  CheckersGame,
  normalizeCheckersLevel,
  parseCheckersInput,
  pickCheckersBotMove,
} from '../../utils/checkers-game.js'
import { renderCheckersBoard } from '../../utils/checkers-renderer.js'
import { getCheckersTheme, normalizeCheckersTheme } from '../../utils/game-themes.js'
import { gameMenuArt } from '../../utils/game-menu-art.js'\nimport { findOtherActiveGame, otherGameMessage } from '../../utils/game-session.js'

const NAMESPACE = 'checkers-game'
const WAITING_TTL = 15 * 60 * 1000
const PLAYING_TTL = 24 * 60 * 60 * 1000

const RULES = [
  '*How to play*',
  '• Squares are numbered 1–32.',
  '• Move: `9 13`',
  '• Multi-jump: `10 17 26`',
  '• Preview a piece: send its square, for example `9`.',
  '• Captures are mandatory.',
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
  if (!key) throw new Error('Checkers chat is unavailable.')
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
  if (player === CHECKERS_BOT_ID) return 'MSCC Bot'
  if (player === game.playerBlack) return game.blackName || 'Black'
  if (player === game.playerRed) return game.redName || 'Red'
  return 'Player'
}

function statusCaption(game, note = '') {
  let status
  if (game.winner) status = `🏆 ${labelFor(game, game.winner)} wins.`
  else if (game.isDraw) status = `🤝 Draw — ${game.drawReason || 'draw'}.`
  else status = `Turn: ${labelFor(game, game.currentTurn)} (${game.colorFor(game.currentTurn) === 'b' ? 'Black' : 'Red'})`

  return [
    '*Checkers*',
    note,
    status,
    '',
    `⚫ ${game.blackName || 'Black'}`,
    `🔴 ${game.redName || 'Red'}`,
    '',
    game.winner || game.isDraw ? '' : 'Send a move like `9 13`.',
  ].filter(Boolean).join('\n')
}

async function sendBoard(ctx, game, themeInput, note = '', preview = null) {
  const chat = chatKey(ctx)
  if (!chat || !ctx.account?.sock) throw new Error('Checkers connection is unavailable.')
  return ctx.account.sock.sendMessage(chat, {
    image:renderCheckersBoard(game, { ...(preview || {}), theme:normalizeCheckersTheme(themeInput) }),
    caption:statusCaption(game, note),
  }, { quoted:ctx.message })
}

async function showModePicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Checkers',
    text:'Who do you want to play?',
    caption:'Who do you want to play?',
    image:await gameMenuArt('checkers'),
    buttonText:'Choose opponent',
    rows:[
      {
        title:'👤 Play a person',
        description:'Open a challenge in this chat',
        id:`${prefix}checkers ~person`,
      },
      {
        title:'🤖 Play the bot',
        description:'Choose a difficulty and play now',
        id:`${prefix}checkers ~bot`,
      },
    ],
  })
}

async function showBotLevels(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Checkers vs Bot',
    text:'Choose the bot difficulty.',
    buttonText:'Choose difficulty',
    rows:CHECKERS_LEVELS.map(level => ({
      title:level.label,
      description:level.description,
      id:`${prefix}checkers ~botlevel ${level.id}`,
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
    theme:getCheckersTheme(ctx.shared, user),
    game:new CheckersGame().toRecord(),
  }
}

async function createHumanChallenge(ctx, invitedUser = '') {
  if (!ctx.groupKey) {
    return ctx.reply('Human-vs-human Checkers starts in a group chat. In a DM, choose *Play the bot*.')
  }

  const existing = loadRecord(ctx)
  if (existing) return ctx.reply('There is already a Checkers game or challenge in this chat.')

  const record = saveRecord(ctx, newWaitingRecord(ctx, invitedUser))
  const prefix = String(ctx.publicPrefix || '.')
  const invitedLine = invitedUser
    ? 'This challenge is reserved for the player you selected.'
    : 'Anyone else in this group can join.'

  return ctx.ui.joinCancel({
    title:'Checkers challenge',
    text:`${playerName(ctx)} opened a Checkers game.\n\n${invitedLine}`,
    buttonText:'Checkers',
    joinText:'Join game',
    joinDescription:'Black and Red are assigned randomly',
    joinId:`${prefix}checkers ~join ${record.id}`,
    cancelText:'Cancel challenge',
    cancelDescription:'Only the challenger can cancel',
    cancelId:`${prefix}checkers ~cancel ${record.id}`,
  })
}

async function joinHumanChallenge(ctx, id) {
  const record = loadRecord(ctx)
  const user = String(ctx.userKey || '')

  if (!record || record.state !== 'WAITING' || record.mode !== 'human' || record.id !== id) {
    return ctx.reply('That Checkers challenge is no longer available.')
  }
  if (record.createdBy === user) return ctx.reply('You cannot join your own challenge.')
  if (record.invitedUser && record.invitedUser !== user) {
    return ctx.reply('That challenge was opened for someone else.')
  }

  const challenger = String(record.createdBy || '')
  const challengerName = String(record.createdByName || 'Challenger')
  const joinerName = playerName(ctx)
  const challengerIsBlack = Math.random() < 0.5
  const game = new CheckersGame({
    playerBlack:challengerIsBlack ? challenger : user,
    playerRed:challengerIsBlack ? user : challenger,
    blackName:challengerIsBlack ? challengerName : joinerName,
    redName:challengerIsBlack ? joinerName : challengerName,
  })

  saveRecord(ctx, {
    ...record,
    state:'PLAYING',
    game:game.toRecord(),
  })

  await sendBoard(
    ctx,
    game,
    record.theme,
    `Sides randomized — ${game.blackName} is Black, ${game.redName} is Red. Black moves first.`,
  )
  return ctx.reply(RULES)
}

async function cancelChallenge(ctx, id = '') {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'WAITING') return ctx.reply('There is no waiting Checkers challenge.')
  if (id && record.id !== id) return ctx.reply('That challenge has expired.')
  if (record.createdBy !== String(ctx.userKey || '')) return ctx.reply('Only the challenger can cancel it.')
  clearRecord(ctx)
  return ctx.reply('Checkers challenge cancelled.')
}

async function startBotGame(ctx, level) {
  if (loadRecord(ctx)) return ctx.reply('There is already an active Checkers game in this chat.')

  const user = String(ctx.userKey || '')
  const name = playerName(ctx)
  const userIsBlack = Math.random() < 0.5
  const game = new CheckersGame({
    playerBlack:userIsBlack ? user : CHECKERS_BOT_ID,
    playerRed:userIsBlack ? CHECKERS_BOT_ID : user,
    blackName:userIsBlack ? name : 'MSCC Bot',
    redName:userIsBlack ? 'MSCC Bot' : name,
  })
  const theme = getCheckersTheme(ctx.shared, user)

  let note = `Bot difficulty: ${level}. You are ${userIsBlack ? 'Black' : 'Red'}.`
  if (!userIsBlack) {
    const botMove = pickCheckersBotMove(game, level)
    if (botMove) {
      const result = game.move(CHECKERS_BOT_ID, botMove.path)
      if (!result.ok) throw new Error(result.reason || 'Bot opening move failed.')
      note += ` MSCC Bot is Black and opened ${botMove.path.map(x => x + 1).join('→')}.`
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

  await sendBoard(ctx, game, theme, note)
  return ctx.reply(RULES)
}

async function previewMove(ctx, record, game, parsed) {
  const user = String(ctx.userKey || '')
  if (user !== game.currentTurn) return ctx.reply("It's not your turn.")
  const piece = game.pieceAt(parsed.from)
  if (!piece) return ctx.reply(`There is no piece on square ${parsed.from + 1}.`)
  if (game.colorFor(user) !== piece.color) return ctx.reply('That is not one of your pieces.')

  const sequences = game.legalFrom(user, parsed.from)
  const targets = [...new Set(sequences.map(sequence => sequence.path[1]).filter(Number.isInteger))]
  await sendBoard(
    ctx,
    game,
    record.theme,
    targets.length
      ? `Square ${parsed.from + 1} can move to: ${targets.map(x => x + 1).join(', ')}`
      : `Square ${parsed.from + 1} has no legal moves.`,
    { previewOrigin:parsed.from, previewTargets:targets },
  )
  return true
}

async function resignGame(ctx, game) {
  const user = String(ctx.userKey || '')
  if (![game.playerBlack, game.playerRed].includes(user)) return false
  const winner = user === game.playerBlack ? game.playerRed : game.playerBlack
  clearRecord(ctx)
  return ctx.reply(`🏳️ ${labelFor(game, user)} resigned. ${labelFor(game, winner)} wins.`)
}

async function handleMoveInput(ctx, text) {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'PLAYING') return false

  const game = new CheckersGame(record.game)
  const user = String(ctx.userKey || '')
  if (![game.playerBlack, game.playerRed].includes(user)) return false

  const parsed = parseCheckersInput(text)
  if (!parsed) return false
  if (parsed.type === 'resign') return resignGame(ctx, game)
  if (parsed.type === 'preview') return previewMove(ctx, record, game, parsed)

  const result = game.move(user, parsed.path)
  if (!result.ok) return ctx.reply(result.reason)
  const humanNote = `${labelFor(game, user)} played ${parsed.path.map(x => x + 1).join('→')}.`

  if (game.winner || game.isDraw) {
    clearRecord(ctx)
    await sendBoard(ctx, game, record.theme, humanNote)
    return true
  }

  if (record.mode === 'bot') {
    saveRecord(ctx, { ...record, game:game.toRecord() })
    await sendBoard(ctx, game, record.theme, humanNote)

    const botMove = pickCheckersBotMove(game, record.level || 'normal')
    if (botMove) {
      const botResult = game.move(CHECKERS_BOT_ID, botMove.path)
      if (!botResult.ok) throw new Error(botResult.reason || 'Bot move failed.')
    }

    if (game.winner || game.isDraw) clearRecord(ctx)
    else saveRecord(ctx, { ...record, game:game.toRecord() })

    await sendBoard(
      ctx,
      game,
      record.theme,
      botMove ? `MSCC Bot played ${botMove.path.map(x => x + 1).join('→')}.` : 'MSCC Bot has no legal move.',
    )
    return true
  }

  saveRecord(ctx, { ...record, game:game.toRecord() })
  await sendBoard(ctx, game, record.theme, humanNote)
  return true
}

async function showCurrentGame(ctx, record) {
  if (record.state === 'WAITING') {
    return ctx.reply('A Checkers challenge is waiting in this chat. Someone else can tap *Join game*.')
  }
  return sendBoard(ctx, new CheckersGame(record.game), record.theme)
}

export default {
  name:'checkers',
  aliases:['draughts','drafts'],
  description:'Play visual Checkers against another person or the bot.',
  usage:'.checkers',
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
        const level = normalizeCheckersLevel(args[1])
        return level ? startBotGame(ctx, level) : showBotLevels(ctx)
      }

      if (first === 'cancel') return cancelChallenge(ctx)
      if (first === 'rules') return ctx.reply(RULES)
      if (first === 'bot') {
        const level = normalizeCheckersLevel(args[1])
        return level ? startBotGame(ctx, level) : showBotLevels(ctx)
      }
      if (first === 'person' || first === 'player' || first === 'human') return createHumanChallenge(ctx)

      if (args.length && parseCheckersInput(args.join(' '))) {
        return handleMoveInput(ctx, args.join(' '))
      }

      if (args.length && typeof ctx.resolveCommandTarget === 'function') {
        const target = await ctx.resolveCommandTarget(args[0])
        if (target?.phoneNumber) return createHumanChallenge(ctx, target.phoneNumber)
      }

      if (existing) return showCurrentGame(ctx, existing)
      return showModePicker(ctx)
    } catch (error) {
      console.error('MSCC Checkers failed:', error)
      return ctx.reply('I could not continue that Checkers game.')
    }
  },
}
