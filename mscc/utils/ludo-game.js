import { randomInt } from 'node:crypto'

export const LUDO_COLORS = Object.freeze(['red','green','yellow','blue'])
export const LUDO_BOT_PREFIX = '__mscc_ludo_bot_'
export const LUDO_FINISH_PROGRESS = 57
export const LUDO_TRACK_LENGTH = 52

export const LUDO_LOOP = Object.freeze([
  [6,1],[6,2],[6,3],[6,4],[6,5],
  [5,6],[4,6],[3,6],[2,6],[1,6],[0,6],
  [0,7],[0,8],
  [1,8],[2,8],[3,8],[4,8],[5,8],
  [6,9],[6,10],[6,11],[6,12],[6,13],[6,14],
  [7,14],[8,14],
  [8,13],[8,12],[8,11],[8,10],[8,9],
  [9,8],[10,8],[11,8],[12,8],[13,8],[14,8],
  [14,7],[14,6],
  [13,6],[12,6],[11,6],[10,6],[9,6],
  [8,5],[8,4],[8,3],[8,2],[8,1],[8,0],
  [7,0],[6,0],
].map(value => Object.freeze(value)))

export const LUDO_START_OFFSETS = Object.freeze({
  red:0,
  green:13,
  yellow:26,
  blue:39,
})

export const LUDO_HOME_LANES = Object.freeze({
  red:Object.freeze([[7,1],[7,2],[7,3],[7,4],[7,5]].map(value => Object.freeze(value))),
  green:Object.freeze([[1,7],[2,7],[3,7],[4,7],[5,7]].map(value => Object.freeze(value))),
  yellow:Object.freeze([[7,13],[7,12],[7,11],[7,10],[7,9]].map(value => Object.freeze(value))),
  blue:Object.freeze([[13,7],[12,7],[11,7],[10,7],[9,7]].map(value => Object.freeze(value))),
})

export const LUDO_SAFE_GLOBALS = Object.freeze([0,8,13,21,26,34,39,47])

function cleanPlayer(player, index) {
  const color = LUDO_COLORS.includes(String(player?.color || '').toLowerCase())
    ? String(player.color).toLowerCase()
    : LUDO_COLORS[index % LUDO_COLORS.length]
  return {
    id:String(player?.id || ''),
    name:String(player?.name || `Player ${index + 1}`).trim().slice(0,80) || `Player ${index + 1}`,
    color,
    isBot:Boolean(player?.isBot),
    eliminated:Boolean(player?.eliminated),
  }
}

function cleanTokens(tokens = {}) {
  const out = {}
  for (const color of LUDO_COLORS) {
    const source = Array.isArray(tokens?.[color]) ? tokens[color] : []
    out[color] = Array.from({ length:4 }, (_, index) => {
      const value = Number(source[index])
      if (!Number.isInteger(value)) return -1
      return Math.max(-1, Math.min(LUDO_FINISH_PROGRESS, value))
    })
  }
  return out
}

function sameColorPlayers(players) {
  return new Set(players.map(player => player.color)).size !== players.length
}

export function isLudoBot(playerId) {
  return String(playerId || '').startsWith(LUDO_BOT_PREFIX)
}

export function ludoBotId(index = 1) {
  return `${LUDO_BOT_PREFIX}${Math.max(1, Number(index) || 1)}__`
}

export function rollLudoDie() {
  return randomInt(1, 7)
}

export function ludoGlobalIndex(color, progress) {
  if (!LUDO_COLORS.includes(color) || !Number.isInteger(progress) || progress < 0 || progress >= LUDO_TRACK_LENGTH) return -1
  return (LUDO_START_OFFSETS[color] + progress) % LUDO_TRACK_LENGTH
}

export function ludoCoordinate(color, progress) {
  if (!LUDO_COLORS.includes(color) || !Number.isInteger(progress)) return null
  if (progress >= 0 && progress < LUDO_TRACK_LENGTH) {
    return LUDO_LOOP[ludoGlobalIndex(color, progress)] || null
  }
  if (progress >= 52 && progress <= 56) {
    return LUDO_HOME_LANES[color][progress - 52] || null
  }
  return null
}

export function ludoTokenLabel(index) {
  return `Token ${Number(index) + 1}`
}

export function parseLudoInput(value) {
  const text = String(value || '').trim().toLowerCase()
  if (/^(roll|dice|throw)$/.test(text)) return { type:'roll' }
  if (/^(surrender|resign|quit|leave)$/.test(text)) return { type:'resign' }
  if (/^[1-4]$/.test(text)) return { type:'token', tokenIndex:Number(text) - 1 }
  return null
}

export function ludoRecordAcceptsInput(record, userKey, text) {
  if (!record || record.state !== 'PLAYING') return false
  const game = new LudoGame(record.game)
  const user = String(userKey || '')
  const player = game.playerById(user)
  if (!player || player.eliminated || game.winner) return false
  const parsed = parseLudoInput(text)
  if (!parsed) return false
  if (parsed.type === 'resign') return true
  if (game.currentPlayer?.id !== user) return false
  if (parsed.type === 'roll') return game.pendingRoll == null
  if (parsed.type === 'token') return game.pendingRoll != null
  return false
}

function countByGlobal(game, globalIndex, exceptColor = '') {
  const counts = new Map()
  for (const player of game.players) {
    if (player.eliminated || player.color === exceptColor) continue
    for (const progress of game.tokens[player.color] || []) {
      if (progress < 0 || progress >= 52) continue
      const global = ludoGlobalIndex(player.color, progress)
      if (global !== globalIndex) continue
      counts.set(player.color, (counts.get(player.color) || 0) + 1)
    }
  }
  return counts
}

function opponentBlockadeOn(game, color, globalIndex) {
  if (LUDO_SAFE_GLOBALS.includes(globalIndex)) return false
  const counts = countByGlobal(game, globalIndex, color)
  return [...counts.values()].some(count => count >= 2)
}

function pathBlocked(game, color, fromProgress, toProgress) {
  if (toProgress < 0) return false
  const start = fromProgress < 0 ? 0 : fromProgress + 1
  const end = Math.min(toProgress, 51)
  for (let progress = start; progress <= end; progress += 1) {
    const global = ludoGlobalIndex(color, progress)
    if (opponentBlockadeOn(game, color, global)) return true
  }
  return false
}

function destinationFor(progress, roll) {
  if (progress < 0) return roll === 6 ? 0 : null
  const next = progress + roll
  return next <= LUDO_FINISH_PROGRESS ? next : null
}

function tokenIsFinished(progress) {
  return progress === LUDO_FINISH_PROGRESS
}

function allFinished(tokens) {
  return Array.isArray(tokens) && tokens.every(tokenIsFinished)
}

export class LudoGame {
  constructor(record = {}) {
    this.players = Array.isArray(record.players)
      ? record.players.slice(0,4).map(cleanPlayer).filter(player => player.id)
      : []
    if (sameColorPlayers(this.players)) {
      this.players = this.players.map((player,index) => ({ ...player, color:LUDO_COLORS[index] }))
    }

    this.tokens = cleanTokens(record.tokens)
    this.currentPlayerIndex = this.players.length
      ? Math.max(0, Math.min(this.players.length - 1, Number(record.currentPlayerIndex) || 0))
      : 0
    this.pendingRoll = Number.isInteger(record.pendingRoll) && record.pendingRoll >= 1 && record.pendingRoll <= 6
      ? record.pendingRoll
      : null
    this.consecutiveSixes = Math.max(0, Math.min(2, Number(record.consecutiveSixes) || 0))
    this.winner = String(record.winner || '')
    this.lastMove = record.lastMove && typeof record.lastMove === 'object'
      ? {
          playerId:String(record.lastMove.playerId || ''),
          color:String(record.lastMove.color || ''),
          tokenIndex:Number(record.lastMove.tokenIndex),
          from:Number(record.lastMove.from),
          to:Number(record.lastMove.to),
          roll:Number(record.lastMove.roll),
          captured:Array.isArray(record.lastMove.captured)
            ? record.lastMove.captured.map(item => ({ playerId:String(item.playerId || ''), color:String(item.color || ''), tokenIndex:Number(item.tokenIndex) }))
            : [],
          finished:Boolean(record.lastMove.finished),
        }
      : null
    this.turnNumber = Math.max(0, Number(record.turnNumber) || 0)
  }

  get currentPlayer() {
    return this.players[this.currentPlayerIndex] || null
  }

  playerById(id) {
    return this.players.find(player => player.id === String(id || '')) || null
  }

  playerByColor(color) {
    return this.players.find(player => player.color === color) || null
  }

  tokenProgress(playerId, tokenIndex) {
    const player = this.playerById(playerId)
    if (!player || tokenIndex < 0 || tokenIndex > 3) return null
    return this.tokens[player.color]?.[tokenIndex] ?? null
  }

  standings() {
    return this.players.map(player => {
      const tokens = this.tokens[player.color] || []
      return {
        id:player.id,
        name:player.name,
        color:player.color,
        isBot:player.isBot,
        eliminated:player.eliminated,
        yard:tokens.filter(value => value < 0).length,
        home:tokens.filter(tokenIsFinished).length,
        active:tokens.filter(value => value >= 0 && value < LUDO_FINISH_PROGRESS).length,
      }
    })
  }

  legalTokenIndexes(playerId = this.currentPlayer?.id, roll = this.pendingRoll) {
    const player = this.playerById(playerId)
    if (!player || player.eliminated || !Number.isInteger(roll) || roll < 1 || roll > 6) return []
    const values = this.tokens[player.color] || []
    const legal = []

    for (let index = 0; index < values.length; index += 1) {
      const progress = values[index]
      if (tokenIsFinished(progress)) continue
      const destination = destinationFor(progress, roll)
      if (destination == null) continue
      if (pathBlocked(this, player.color, progress, destination)) continue
      legal.push(index)
    }
    return legal
  }

  previewTokenMove(playerId, tokenIndex, roll = this.pendingRoll) {
    const player = this.playerById(playerId)
    if (!player || !this.legalTokenIndexes(playerId, roll).includes(tokenIndex)) return null
    const from = this.tokens[player.color][tokenIndex]
    const to = destinationFor(from, roll)
    const captured = []

    if (to >= 0 && to < 52) {
      const global = ludoGlobalIndex(player.color, to)
      if (!LUDO_SAFE_GLOBALS.includes(global)) {
        for (const opponent of this.players) {
          if (opponent.eliminated || opponent.id === player.id) continue
          for (let i = 0; i < 4; i += 1) {
            const otherProgress = this.tokens[opponent.color][i]
            if (otherProgress < 0 || otherProgress >= 52) continue
            if (ludoGlobalIndex(opponent.color, otherProgress) === global) {
              captured.push({ playerId:opponent.id, color:opponent.color, tokenIndex:i })
            }
          }
        }
      }
    }

    return {
      playerId:player.id,
      color:player.color,
      tokenIndex,
      from,
      to,
      roll,
      captured,
      finished:to === LUDO_FINISH_PROGRESS,
      leavesYard:from < 0 && to === 0,
      landsSafe:to >= 0 && to < 52 && LUDO_SAFE_GLOBALS.includes(ludoGlobalIndex(player.color, to)),
    }
  }

  advanceTurn() {
    if (!this.players.length || this.winner) return this.currentPlayer
    for (let step = 1; step <= this.players.length; step += 1) {
      const nextIndex = (this.currentPlayerIndex + step) % this.players.length
      const candidate = this.players[nextIndex]
      if (!candidate.eliminated && !allFinished(this.tokens[candidate.color])) {
        this.currentPlayerIndex = nextIndex
        this.pendingRoll = null
        this.consecutiveSixes = 0
        this.turnNumber += 1
        return candidate
      }
    }
    return this.currentPlayer
  }

  roll(playerId, forcedValue = null) {
    if (this.winner) return { ok:false, reason:'That Ludo game is already over.' }
    const player = this.playerById(playerId)
    if (!player || player.eliminated) return { ok:false, reason:'You are not an active player in this game.' }
    if (this.currentPlayer?.id !== player.id) return { ok:false, reason:"It's not your turn." }
    if (this.pendingRoll != null) return { ok:false, reason:'Choose a token before rolling again.' }

    const roll = forcedValue == null ? rollLudoDie() : Number(forcedValue)
    if (!Number.isInteger(roll) || roll < 1 || roll > 6) return { ok:false, reason:'Invalid die roll.' }

    if (roll === 6) this.consecutiveSixes += 1
    else this.consecutiveSixes = 0

    if (this.consecutiveSixes >= 3) {
      const previous = player
      this.pendingRoll = null
      this.advanceTurn()
      return {
        ok:true,
        roll,
        forfeited:true,
        reason:`${previous.name} rolled three sixes — turn forfeited.`,
        nextPlayer:this.currentPlayer,
      }
    }

    const legalTokens = this.legalTokenIndexes(player.id, roll)
    if (!legalTokens.length) {
      const extraTurn = roll === 6
      if (!extraTurn) this.advanceTurn()
      return {
        ok:true,
        roll,
        noMove:true,
        extraTurn,
        legalTokens:[],
        nextPlayer:this.currentPlayer,
      }
    }

    this.pendingRoll = roll
    return { ok:true, roll, legalTokens:[...legalTokens], needsChoice:true }
  }

  move(playerId, tokenIndex) {
    if (this.winner) return { ok:false, reason:'That Ludo game is already over.' }
    const player = this.playerById(playerId)
    if (!player || player.eliminated) return { ok:false, reason:'You are not an active player in this game.' }
    if (this.currentPlayer?.id !== player.id) return { ok:false, reason:"It's not your turn." }
    if (this.pendingRoll == null) return { ok:false, reason:'Roll the die first.' }

    const roll = this.pendingRoll
    const legal = this.legalTokenIndexes(player.id, roll)
    const index = Number(tokenIndex)
    if (!legal.includes(index)) return { ok:false, reason:'That token cannot move with this roll.' }

    const preview = this.previewTokenMove(player.id, index, roll)
    if (!preview) return { ok:false, reason:'That token cannot move.' }

    this.tokens[player.color][index] = preview.to
    for (const captured of preview.captured) {
      this.tokens[captured.color][captured.tokenIndex] = -1
    }

    this.pendingRoll = null
    this.lastMove = {
      playerId:player.id,
      color:player.color,
      tokenIndex:index,
      from:preview.from,
      to:preview.to,
      roll,
      captured:preview.captured.map(item => ({ ...item })),
      finished:preview.finished,
    }

    if (allFinished(this.tokens[player.color])) {
      this.winner = player.id
      return { ok:true, ...preview, winner:player.id, extraTurn:false }
    }

    const extraTurn = roll === 6 || preview.captured.length > 0 || preview.finished
    if (!extraTurn) this.advanceTurn()

    return {
      ok:true,
      ...preview,
      extraTurn,
      nextPlayer:this.currentPlayer,
    }
  }

  resign(playerId) {
    const player = this.playerById(playerId)
    if (!player || player.eliminated || this.winner) return { ok:false, reason:'You cannot leave this game.' }
    player.eliminated = true
    this.pendingRoll = null
    this.consecutiveSixes = 0

    const active = this.players.filter(item => !item.eliminated)
    if (active.length === 1) {
      this.winner = active[0].id
      return { ok:true, winner:this.winner }
    }

    if (this.currentPlayer?.id === player.id) this.advanceTurn()
    return { ok:true, nextPlayer:this.currentPlayer }
  }

  toRecord() {
    return {
      players:this.players.map(player => ({ ...player })),
      tokens:cleanTokens(this.tokens),
      currentPlayerIndex:this.currentPlayerIndex,
      pendingRoll:this.pendingRoll,
      consecutiveSixes:this.consecutiveSixes,
      winner:this.winner,
      lastMove:this.lastMove ? {
        ...this.lastMove,
        captured:this.lastMove.captured.map(item => ({ ...item })),
      } : null,
      turnNumber:this.turnNumber,
    }
  }
}

export function shuffleLudoColors(count = 4) {
  const safeCount = Math.max(2, Math.min(4, Number(count) || 2))
  let pool
  if (safeCount === 2) {
    pool = randomInt(0,2) === 0 ? ['red','yellow'] : ['green','blue']
  } else {
    pool = [...LUDO_COLORS]
    if (safeCount === 3) pool.splice(randomInt(0,pool.length), 1)
  }

  for (let i = pool.length - 1; i > 0; i -= 1) {
    const j = randomInt(0, i + 1)
    ;[pool[i], pool[j]] = [pool[j], pool[i]]
  }
  return pool.slice(0, safeCount)
}

export function randomLudoStartingIndex(playerCount) {
  const count = Math.max(1, Number(playerCount) || 1)
  return randomInt(0, count)
}

export function chooseLudoBotToken(game, playerId = game.currentPlayer?.id) {
  const roll = game.pendingRoll
  const legal = game.legalTokenIndexes(playerId, roll)
  if (!legal.length) return -1

  let bestScore = -Infinity
  let best = []
  for (const tokenIndex of legal) {
    const move = game.previewTokenMove(playerId, tokenIndex, roll)
    if (!move) continue
    let score = move.to
    if (move.finished) score += 1000
    if (move.captured.length) score += 450 + move.captured.length * 100
    if (move.leavesYard) score += 160
    if (move.landsSafe) score += 90
    if (move.from >= 0 && move.from < 52 && !move.landsSafe) {
      const global = ludoGlobalIndex(move.color, move.to)
      const opponents = countByGlobal(game, global, move.color)
      if ([...opponents.values()].some(count => count === 1)) score += 120
    }

    if (score > bestScore) {
      bestScore = score
      best = [tokenIndex]
    } else if (score === bestScore) {
      best.push(tokenIndex)
    }
  }

  return best.length ? best[randomInt(0, best.length)] : legal[0]
}
