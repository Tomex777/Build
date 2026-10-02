import { randomUUID } from 'node:crypto'
import {
  LUDO_FINISH_PROGRESS,
  LudoGame,
  chooseLudoBotToken,
  ludoBotId,
  ludoTokenLabel,
  parseLudoInput,
  randomLudoStartingIndex,
  shuffleLudoColors,
} from '../../utils/ludo-game.js'
import { renderLudoBoard } from '../../utils/ludo-renderer.js'
import { getLudoTheme, normalizeLudoTheme } from '../../utils/game-themes.js'
import { findOtherActiveGame, otherGameMessage } from '../../utils/game-session.js'

const NAMESPACE = 'ludo-game'
const WAITING_TTL = 30 * 60 * 1000
const PLAYING_TTL = 72 * 60 * 60 * 1000
const COLORS = Object.freeze({ red:'🔴', green:'🟢', yellow:'🟡', blue:'🔵' })
const DICE = Object.freeze(['','⚀','⚁','⚂','⚃','⚄','⚅'])

const RULES = [
  '*How to play Ludo*',
  '• Roll a 6 to bring a token out of the yard.',
  '• Move by the exact number rolled.',
  '• Roll a 6, capture, or reach Home for another turn.',
  '• Three sixes in a row forfeit the turn.',
  '• Safe circles cannot be captured.',
  '• Two same-color tokens form a blockade on ordinary track squares.',
  '• You need an exact roll to reach Home.',
  '• First player to bring all four tokens Home wins.',
  '• Send roll on your turn, then choose token 1–4 when asked.',
  '• Send surrender or resign to leave the game.',
].join('\n')

function chatKey(ctx) {
  return String(ctx.message?.key?.remoteJid || '').trim()
}

function playerName(ctx) {
  return String(ctx.message?.pushName || ctx.userKey || 'Player').trim().slice(0,80) || 'Player'
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
  if (!key) throw new Error('Ludo chat is unavailable.')
  const ttl = record.state === 'WAITING' ? WAITING_TTL : PLAYING_TTL
  const next = { ...record, updatedAt:Date.now(), expiresAt:Date.now() + ttl }
  ctx.shared?.set(NAMESPACE, key, next)
  return next
}

function clearRecord(ctx) {
  const key = chatKey(ctx)
  if (key) ctx.shared?.delete(NAMESPACE, key)
}

function checkOtherGame(ctx) {
  const conflict = findOtherActiveGame(ctx, NAMESPACE)
  return conflict ? otherGameMessage(conflict) : ''
}

function colorLabel(color) {
  const value = String(color || '')
  return value ? value[0].toUpperCase() + value.slice(1) : ''
}

function playerLabel(player) {
  if (!player) return 'Player'
  return player.name || (player.isBot ? 'MSCC Bot' : 'Player')
}

function tokenState(progress) {
  if (progress < 0) return 'Yard'
  if (progress === LUDO_FINISH_PROGRESS) return 'Home'
  if (progress >= 52) return 'Home lane ' + (progress - 51) + '/5'
  return 'Track ' + (progress + 1)
}

function statusCaption(game, note = '') {
  const current = game.currentPlayer
  const lines = [
    '*Ludo*',
    note,
    game.winner
      ? '🏆 ' + playerLabel(game.playerById(game.winner)) + ' wins!'
      : 'Turn: ' + (COLORS[current?.color] || '') + ' ' + playerLabel(current),
  ].filter(Boolean)

  if (game.pendingRoll != null) {
    lines.push('Roll: ' + DICE[game.pendingRoll] + ' *' + game.pendingRoll + '*')
  }

  lines.push('')
  for (const item of game.standings()) {
    lines.push(
      (COLORS[item.color] || '') + ' ' + item.name +
      ' · Home ' + item.home + '/4 · Yard ' + item.yard +
      (item.eliminated ? ' · Left' : ''),
    )
  }
  return lines.join('\n')
}

async function sendImage(ctx, game, theme, note = '') {
  const chat = chatKey(ctx)
  if (!chat || !ctx.account?.sock) throw new Error('Ludo connection is unavailable.')
  return ctx.account.sock.sendMessage(chat, {
    image:renderLudoBoard(game, { theme, roll:game.pendingRoll }),
    caption:statusCaption(game, note),
  }, { quoted:ctx.message })
}

async function sendGameUi(ctx, game, themeInput, note = '') {
  const theme = normalizeLudoTheme(themeInput)
  if (game.winner) return sendImage(ctx, game, theme, note)

  const current = game.currentPlayer
  if (!current || current.isBot) return sendImage(ctx, game, theme, note)
  const prefix = String(ctx.publicPrefix || '.')

  if (game.pendingRoll != null) {
    const legal = game.legalTokenIndexes(current.id)
    if (!legal.length) return sendImage(ctx, game, theme, note)
    const caption = statusCaption(game, note)
    return ctx.ui.bottomSheet({
      title:'Ludo',
      text:caption,
      caption,
      image:renderLudoBoard(game, {
        theme,
        selectablePlayerId:current.id,
        selectableTokens:legal,
        roll:game.pendingRoll,
      }),
      buttonText:'Choose token',
      rows:legal.map(index => ({
        title:(COLORS[current.color] || '') + ' ' + ludoTokenLabel(index),
        description:tokenState(game.tokenProgress(current.id, index)) + ' · move ' + game.pendingRoll,
        id:prefix + 'ludo ~token ' + (index + 1),
      })),
    })
  }

  const caption = statusCaption(game, note)
  return ctx.ui.bottomSheet({
    title:'Ludo',
    text:caption,
    caption,
    image:renderLudoBoard(game, { theme }),
    buttonText:'Your turn',
    rows:[{
      title:'🎲 Roll dice',
      description:playerLabel(current) + ' · ' + colorLabel(current.color),
      id:prefix + 'ludo ~roll',
    }],
  })
}

async function showModePicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Ludo',
    text:'Choose how you want to play.',
    buttonText:'Choose game',
    rows:[
      { title:'👥 Play with people', description:'2 to 4 players in this group', id:prefix + 'ludo ~people' },
      { title:'🤖 Play with bots', description:'1 to 3 bots — start immediately', id:prefix + 'ludo ~bots' },
    ],
  })
}

async function showHumanCountPicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Ludo with people',
    text:'How many total players?',
    buttonText:'Players',
    rows:[2,3,4].map(count => ({
      title:count + ' players',
      description:count === 2 ? 'Head-to-head' : count + '-player match',
      id:prefix + 'ludo ~create ' + count,
    })),
  })
}

async function showBotCountPicker(ctx) {
  const prefix = String(ctx.publicPrefix || '.')
  return ctx.ui.bottomSheet({
    title:'Ludo with bots',
    text:'How many bots do you want?',
    buttonText:'Bots',
    rows:[1,2,3].map(count => ({
      title:count + ' bot' + (count === 1 ? '' : 's'),
      description:(count + 1) + ' players total',
      id:prefix + 'ludo ~startbots ' + count,
    })),
  })
}

function waitingRecord(ctx, totalPlayers) {
  const creator = String(ctx.userKey || '')
  return {
    id:randomUUID(),
    state:'WAITING',
    mode:'human',
    totalPlayers,
    createdBy:creator,
    theme:getLudoTheme(ctx.shared, creator),
    lobbyPlayers:[{ id:creator, name:playerName(ctx) }],
  }
}

async function showLobby(ctx, record, note = '') {
  const prefix = String(ctx.publicPrefix || '.')
  const count = record.lobbyPlayers?.length || 0
  const total = Number(record.totalPlayers) || 2
  const names = (record.lobbyPlayers || [])
    .map((player,index) => (index + 1) + '. ' + player.name)
    .join('\n')
  const left = total - count

  return ctx.ui.joinCancel({
    title:'Ludo lobby · ' + count + '/' + total,
    text:[note, names, '', left > 0 ? 'Waiting for ' + left + ' more player' + (left === 1 ? '.' : 's.') : 'Starting…'].filter(Boolean).join('\n'),
    buttonText:'Ludo',
    joinText:'🎲 Join game',
    joinDescription:'Join this ' + total + '-player match',
    joinId:prefix + 'ludo ~join ' + record.id,
    cancelText:'✕ Cancel lobby',
    cancelDescription:'Only the host can cancel',
    cancelId:prefix + 'ludo ~cancel ' + record.id,
  })
}

async function createHumanGame(ctx, totalPlayers) {
  if (!ctx.groupKey) {
    return ctx.reply('Multiplayer Ludo starts in a group chat. In a DM, use .ludo bot.')
  }
  const count = Math.max(2, Math.min(4, Number(totalPlayers) || 2))
  if (loadRecord(ctx)) return ctx.reply('There is already a Ludo game or lobby in this chat.')
  const conflict = checkOtherGame(ctx)
  if (conflict) return ctx.reply(conflict)

  const record = saveRecord(ctx, waitingRecord(ctx, count))
  return showLobby(ctx, record)
}

function buildHumanGame(record) {
  const lobby = Array.isArray(record.lobbyPlayers) ? record.lobbyPlayers : []
  const colors = shuffleLudoColors(lobby.length)
  const players = lobby.map((player,index) => ({
    id:String(player.id),
    name:String(player.name || 'Player ' + (index + 1)),
    color:colors[index],
    isBot:false,
  }))
  return new LudoGame({
    players,
    currentPlayerIndex:randomLudoStartingIndex(players.length),
  })
}

async function joinHumanGame(ctx, id) {
  const record = loadRecord(ctx)
  const user = String(ctx.userKey || '')
  if (!record || record.state !== 'WAITING' || record.mode !== 'human' || record.id !== id) {
    return ctx.reply('That Ludo lobby is no longer available.')
  }

  const lobbyPlayers = Array.isArray(record.lobbyPlayers) ? [...record.lobbyPlayers] : []
  if (lobbyPlayers.some(player => player.id === user)) {
    return showLobby(ctx, record, 'You are already in this lobby.')
  }

  const total = Math.max(2, Math.min(4, Number(record.totalPlayers) || 2))
  if (lobbyPlayers.length >= total) return ctx.reply('That Ludo lobby is already full.')

  lobbyPlayers.push({ id:user, name:playerName(ctx) })
  if (lobbyPlayers.length < total) {
    const next = saveRecord(ctx, { ...record, lobbyPlayers })
    return showLobby(ctx, next, playerName(ctx) + ' joined.')
  }

  const ready = { ...record, lobbyPlayers }
  const game = buildHumanGame(ready)
  saveRecord(ctx, { ...ready, state:'PLAYING', game:game.toRecord() })

  const assignments = game.players
    .map(player => (COLORS[player.color] || '') + ' ' + player.name + ' = ' + colorLabel(player.color))
    .join(' · ')

  return sendGameUi(ctx, game, record.theme, 'Colors and first turn randomized. ' + assignments)
}

async function cancelLobby(ctx, id = '') {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'WAITING') return ctx.reply('There is no waiting Ludo lobby.')
  if (id && record.id !== id) return ctx.reply('That Ludo lobby has expired.')
  if (record.createdBy !== String(ctx.userKey || '')) return ctx.reply('Only the lobby host can cancel it.')
  clearRecord(ctx)
  return ctx.reply('Ludo lobby cancelled.')
}

function createBotPlayers(ctx, botCount) {
  const count = Math.max(1, Math.min(3, Number(botCount) || 1))
  const colors = shuffleLudoColors(count + 1)
  const players = [{
    id:String(ctx.userKey || ''),
    name:playerName(ctx),
    color:colors[0],
    isBot:false,
  }]
  for (let index = 1; index <= count; index += 1) {
    players.push({
      id:ludoBotId(index),
      name:count === 1 ? 'MSCC Bot' : 'MSCC Bot ' + index,
      color:colors[index],
      isBot:true,
    })
  }
  return players
}

function botTurnSummary(player, roll, move) {
  const die = DICE[roll] || String(roll)
  if (!move) return player.name + ' rolled ' + die + ' ' + roll + ' — no move.'
  const extras = []
  if (move.captured?.length) extras.push('captured ' + move.captured.length)
  if (move.finished) extras.push('reached Home')
  return player.name + ' rolled ' + die + ' ' + roll + ' · Token ' + (move.tokenIndex + 1) + (extras.length ? ' · ' + extras.join(', ') : '') + '.'
}

function runBots(game, maxActions = 72) {
  const notes = []
  let actions = 0

  while (!game.winner && game.currentPlayer?.isBot && actions < maxActions) {
    actions += 1
    const bot = game.currentPlayer
    const rolled = game.roll(bot.id)

    if (!rolled.ok) {
      notes.push(bot.name + ': ' + rolled.reason)
      break
    }
    if (rolled.forfeited) {
      notes.push(bot.name + ' rolled three sixes — turn forfeited.')
      continue
    }
    if (rolled.noMove) {
      notes.push(botTurnSummary(bot, rolled.roll, null))
      continue
    }

    const tokenIndex = chooseLudoBotToken(game, bot.id)
    if (tokenIndex < 0) {
      notes.push(bot.name + ' could not choose a move.')
      game.pendingRoll = null
      game.advanceTurn()
      continue
    }

    const moved = game.move(bot.id, tokenIndex)
    if (!moved.ok) {
      notes.push(bot.name + ': ' + moved.reason)
      break
    }
    notes.push(botTurnSummary(bot, rolled.roll, moved))
  }

  if (actions >= maxActions && game.currentPlayer?.isBot && !game.winner) {
    notes.push('Bot turn limit reached; continue with .ludo.')
  }
  return notes.slice(-8)
}

async function startBotGame(ctx, botCount = 1) {
  if (loadRecord(ctx)) return ctx.reply('There is already a Ludo game or lobby in this chat.')
  const conflict = checkOtherGame(ctx)
  if (conflict) return ctx.reply(conflict)

  const players = createBotPlayers(ctx, botCount)
  const game = new LudoGame({
    players,
    currentPlayerIndex:randomLudoStartingIndex(players.length),
  })
  const theme = getLudoTheme(ctx.shared, String(ctx.userKey || ''))
  const notes = runBots(game)

  saveRecord(ctx, {
    id:randomUUID(),
    state:'PLAYING',
    mode:'bot',
    botCount:players.filter(player => player.isBot).length,
    createdBy:String(ctx.userKey || ''),
    theme,
    game:game.toRecord(),
  })

  const human = game.playerById(String(ctx.userKey || ''))
  const intro = 'You are ' + (COLORS[human?.color] || '') + ' ' + colorLabel(human?.color) + '. ' + players.length + '-player game.'
  return sendGameUi(ctx, game, theme, [intro, ...notes].filter(Boolean).join('\n'))
}

async function persistAndPresent(ctx, record, game, note = '') {
  if (game.winner) {
    clearRecord(ctx)
    return sendGameUi(ctx, game, record.theme, note)
  }

  const botNotes = runBots(game)
  saveRecord(ctx, { ...record, game:game.toRecord() })
  return sendGameUi(ctx, game, record.theme, [note, ...botNotes].filter(Boolean).join('\n'))
}

async function handleRoll(ctx, record, game) {
  const user = String(ctx.userKey || '')
  const rolled = game.roll(user)
  if (!rolled.ok) return ctx.reply(rolled.reason)

  if (rolled.forfeited) {
    return persistAndPresent(ctx, record, game, 'You rolled ' + DICE[rolled.roll] + ' ' + rolled.roll + '. Three sixes — turn forfeited.')
  }

  if (rolled.noMove) {
    const text = rolled.extraTurn
      ? 'You rolled ' + DICE[rolled.roll] + ' ' + rolled.roll + '. No legal move — roll again.'
      : 'You rolled ' + DICE[rolled.roll] + ' ' + rolled.roll + '. No legal move.'
    return persistAndPresent(ctx, record, game, text)
  }

  const legal = game.legalTokenIndexes(user)
  if (legal.length === 1) {
    const moved = game.move(user, legal[0])
    if (!moved.ok) return ctx.reply(moved.reason)
    const extra = moved.extraTurn && !moved.winner ? ' You get another roll.' : ''
    const capture = moved.captured?.length
      ? ' Captured ' + moved.captured.length + ' token' + (moved.captured.length === 1 ? '' : 's') + '.'
      : ''
    return persistAndPresent(
      ctx,
      record,
      game,
      'You rolled ' + DICE[rolled.roll] + ' ' + rolled.roll + '. Token ' + (legal[0] + 1) + ' moved automatically.' + capture + extra,
    )
  }

  saveRecord(ctx, { ...record, game:game.toRecord() })
  return sendGameUi(ctx, game, record.theme, 'You rolled ' + DICE[rolled.roll] + ' ' + rolled.roll + '. Choose a token.')
}

async function handleToken(ctx, record, game, tokenIndex) {
  const user = String(ctx.userKey || '')
  const moved = game.move(user, tokenIndex)
  if (!moved.ok) return ctx.reply(moved.reason)

  const notes = ['Token ' + (tokenIndex + 1) + ' moved ' + moved.roll + '.']
  if (moved.captured?.length) notes.push('Captured ' + moved.captured.length + ' token' + (moved.captured.length === 1 ? '' : 's') + '.')
  if (moved.finished) notes.push('Token reached Home.')
  if (moved.extraTurn && !moved.winner) notes.push('You get another roll.')

  return persistAndPresent(ctx, record, game, notes.join(' '))
}

async function resign(ctx, record, game) {
  const user = String(ctx.userKey || '')
  if (record.mode === 'bot') {
    clearRecord(ctx)
    return ctx.reply('You left the Ludo game.')
  }

  const result = game.resign(user)
  if (!result.ok) return ctx.reply(result.reason)
  if (game.winner) {
    clearRecord(ctx)
    return sendGameUi(ctx, game, record.theme, playerName(ctx) + ' left the game.')
  }

  saveRecord(ctx, { ...record, game:game.toRecord() })
  return sendGameUi(ctx, game, record.theme, playerName(ctx) + ' left the game.')
}

async function handleInput(ctx, text) {
  const record = loadRecord(ctx)
  if (!record || record.state !== 'PLAYING') return false

  const game = new LudoGame(record.game)
  const user = String(ctx.userKey || '')
  if (!game.playerById(user)) return false

  const parsed = parseLudoInput(text)
  if (!parsed) return false
  if (parsed.type === 'resign') return resign(ctx, record, game)
  if (game.currentPlayer?.id !== user) return ctx.reply("It's not your turn.")
  if (parsed.type === 'roll') return handleRoll(ctx, record, game)
  if (parsed.type === 'token') return handleToken(ctx, record, game, parsed.tokenIndex)
  return false
}

async function showCurrent(ctx, record) {
  if (record.state === 'WAITING') return showLobby(ctx, record)
  return sendGameUi(ctx, new LudoGame(record.game), record.theme)
}

export default {
  name:'ludo',
  aliases:[],
  description:'Play full visual Ludo with bots or 2–4 people.',
  usage:'.ludo',
  async run(ctx) {
    try {
      const args = Array.isArray(ctx.args) ? ctx.args.map(String) : []
      const first = String(args[0] || '').toLowerCase()
      const existing = loadRecord(ctx)

      if (first === '~input') return handleInput(ctx, String(ctx.commandReplyInput || args.slice(1).join(' ')))
      if (first === '~people') return showHumanCountPicker(ctx)
      if (first === '~bots') return showBotCountPicker(ctx)
      if (first === '~create') return createHumanGame(ctx, Number(args[1]))
      if (first === '~startbots') return startBotGame(ctx, Number(args[1]))
      if (first === '~join') return joinHumanGame(ctx, String(args[1] || ''))
      if (first === '~cancel') return cancelLobby(ctx, String(args[1] || ''))
      if (first === '~roll') return handleInput(ctx, 'roll')
      if (first === '~token') return handleInput(ctx, String(args[1] || ''))

      if (first === 'rules') return ctx.reply(RULES)
      if (first === 'cancel') return cancelLobby(ctx)
      if (first === 'bot') {
        const botCount = Math.max(1, Math.min(3, Number(args[1]) || 1))
        return startBotGame(ctx, botCount)
      }
      if (first === 'person' || first === 'people' || first === 'human') {
        const count = Number(args[1])
        return count >= 2 && count <= 4 ? createHumanGame(ctx, count) : showHumanCountPicker(ctx)
      }
      if (/^[234]$/.test(first)) return createHumanGame(ctx, Number(first))

      if (args.length && parseLudoInput(args.join(' '))) return handleInput(ctx, args.join(' '))
      if (existing) return showCurrent(ctx, existing)
      return showModePicker(ctx)
    } catch (error) {
      console.error('MSCC Ludo failed:', error)
      return ctx.reply('I could not continue that Ludo game.')
    }
  },
}
