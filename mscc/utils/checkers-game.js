export const CHECKERS_BOT_ID = '__mscc_checkers_bot__'

export const CHECKERS_LEVELS = Object.freeze([
  { id:'easy', label:'Easy', description:'Relaxed — mostly random legal moves' },
  { id:'normal', label:'Normal', description:'Balanced — prefers captures and promotion' },
  { id:'hard', label:'Hard', description:'Strategic — evaluates material and position' },
])

const BLACK = 'b'
const RED = 'r'
const KING = 'k'
const MAN = 'm'

function opponent(color) {
  return color === BLACK ? RED : BLACK
}

function coordFromIndex(index) {
  if (!Number.isInteger(index) || index < 0 || index > 31) return null
  const row = Math.floor(index / 4)
  const offset = row % 2 === 0 ? 1 : 0
  const col = (index % 4) * 2 + offset
  return { row, col }
}

function indexFromCoord(row, col) {
  if (row < 0 || row > 7 || col < 0 || col > 7 || (row + col) % 2 === 0) return -1
  const offset = row % 2 === 0 ? 1 : 0
  return row * 4 + Math.floor((col - offset) / 2)
}

function initialBoard() {
  const board = Array(32).fill(null)
  for (let i = 0; i < 12; i += 1) board[i] = { color:BLACK, rank:MAN }
  for (let i = 20; i < 32; i += 1) board[i] = { color:RED, rank:MAN }
  return board
}

function cloneBoard(board) {
  const source = Array.isArray(board) ? board : []
  return Array.from({ length:32 }, (_, i) => {
    const piece = source[i]
    if (!piece || ![BLACK,RED].includes(piece.color)) return null
    return { color:piece.color, rank:piece.rank === KING ? KING : MAN }
  })
}

function dirs(piece) {
  if (piece.rank === KING) return [[-1,-1],[-1,1],[1,-1],[1,1]]
  return piece.color === BLACK ? [[1,-1],[1,1]] : [[-1,-1],[-1,1]]
}

function shouldPromote(piece, index) {
  if (!piece || piece.rank === KING) return false
  const { row } = coordFromIndex(index)
  return piece.color === BLACK ? row === 7 : row === 0
}

function immediateCaptures(board, from) {
  const piece = board[from]
  if (!piece) return []
  const { row, col } = coordFromIndex(from)
  const out = []
  for (const [dr,dc] of dirs(piece)) {
    const middle = indexFromCoord(row + dr, col + dc)
    const landing = indexFromCoord(row + dr * 2, col + dc * 2)
    if (middle < 0 || landing < 0) continue
    if (!board[middle] || board[middle].color === piece.color || board[landing]) continue
    out.push({ from, to:landing, capture:middle })
  }
  return out
}

function captureSequences(board, from, path = [from], captured = []) {
  const piece = board[from]
  if (!piece) return []
  const options = immediateCaptures(board, from)
  if (!options.length) return captured.length ? [{ path:[...path], captures:[...captured] }] : []

  const sequences = []
  for (const option of options) {
    const next = cloneBoard(board)
    const moving = { ...next[from] }
    next[from] = null
    next[option.capture] = null
    next[option.to] = moving

    // In English/American checkers, reaching the king row ends this turn.
    if (shouldPromote(moving, option.to)) {
      sequences.push({
        path:[...path, option.to],
        captures:[...captured, option.capture],
      })
      continue
    }

    const tails = captureSequences(next, option.to, [...path, option.to], [...captured, option.capture])
    if (tails.length) sequences.push(...tails)
    else sequences.push({
      path:[...path, option.to],
      captures:[...captured, option.capture],
    })
  }
  return sequences
}

function simpleMoves(board, from) {
  const piece = board[from]
  if (!piece) return []
  const { row, col } = coordFromIndex(from)
  const out = []
  for (const [dr,dc] of dirs(piece)) {
    const to = indexFromCoord(row + dr, col + dc)
    if (to >= 0 && !board[to]) out.push({ path:[from,to], captures:[] })
  }
  return out
}

function sequencesFor(board, color) {
  const captures = []
  for (let i = 0; i < 32; i += 1) {
    if (board[i]?.color !== color) continue
    captures.push(...captureSequences(board, i))
  }
  if (captures.length) return captures

  const moves = []
  for (let i = 0; i < 32; i += 1) {
    if (board[i]?.color !== color) continue
    moves.push(...simpleMoves(board, i))
  }
  return moves
}

function samePath(a, b) {
  return a.length === b.length && a.every((value,index) => value === b[index])
}

export function normalizeCheckersLevel(value) {
  const id = String(value || '').trim().toLowerCase()
  return CHECKERS_LEVELS.some(level => level.id === id) ? id : ''
}

export function parseCheckersInput(value) {
  const text = String(value || '').trim().toLowerCase()
  if (/^(surrender|resign|give\s*up)$/.test(text)) return { type:'resign' }
  if (!/^\d{1,2}(?:\s*(?:-|>|,|\s)\s*\d{1,2})*$/.test(text)) return null
  const squares = (text.match(/\d{1,2}/g) || []).map(Number)
  if (!squares.length || squares.some(n => n < 1 || n > 32)) return null
  const path = squares.map(n => n - 1)
  return path.length === 1 ? { type:'preview', from:path[0] } : { type:'move', path }
}

export class CheckersGame {
  constructor(record = {}) {
    this.playerBlack = String(record.playerBlack || '')
    this.playerRed = String(record.playerRed || '')
    this.blackName = String(record.blackName || 'Black')
    this.redName = String(record.redName || 'Red')
    this.board = cloneBoard(record.board?.length === 32 ? record.board : initialBoard())
    this.currentTurn = String(record.currentTurn || this.playerBlack)
    this.winner = String(record.winner || '')
    this.isDraw = Boolean(record.isDraw)
    this.drawReason = String(record.drawReason || '')
    this.lastMove = record.lastMove && Array.isArray(record.lastMove.path)
      ? { path:record.lastMove.path.map(Number), captures:(record.lastMove.captures || []).map(Number) }
      : null
    this.quietTurns = Math.max(0, Number(record.quietTurns) || 0)
  }

  colorFor(player) {
    if (String(player) === this.playerBlack) return BLACK
    if (String(player) === this.playerRed) return RED
    return ''
  }

  playerFor(color) {
    return color === BLACK ? this.playerBlack : this.playerRed
  }

  nameFor(color) {
    return color === BLACK ? this.blackName : this.redName
  }

  legalSequences(player = this.currentTurn) {
    const color = this.colorFor(player)
    return color ? sequencesFor(this.board, color) : []
  }

  legalFrom(player, index) {
    return this.legalSequences(player).filter(sequence => sequence.path[0] === index)
  }

  pieceAt(index) {
    return this.board[index] ? { ...this.board[index] } : null
  }

  move(player, path) {
    if (this.winner || this.isDraw) return { ok:false, reason:'That game is already over.' }
    const actor = String(player || '')
    if (actor !== this.currentTurn) return { ok:false, reason:"It's not your turn." }
    if (!Array.isArray(path) || path.length < 2) return { ok:false, reason:'Send a move like 9 13.' }

    const legal = this.legalSequences(actor)
    const chosen = legal.find(sequence => samePath(sequence.path, path))
    if (!chosen) {
      const hasCapture = legal.some(sequence => sequence.captures.length)
      return {
        ok:false,
        reason:hasCapture
          ? 'That move is not legal. A capture is available, so you must take it.'
          : 'That move is not legal.',
      }
    }

    const from = chosen.path[0]
    const to = chosen.path.at(-1)
    const piece = { ...this.board[from] }
    this.board[from] = null
    for (const captured of chosen.captures) this.board[captured] = null
    const promoted = shouldPromote(piece, to)
    if (promoted) piece.rank = KING
    this.board[to] = piece

    this.lastMove = { path:[...chosen.path], captures:[...chosen.captures] }
    this.quietTurns = chosen.captures.length || promoted ? 0 : this.quietTurns + 1

    const nextColor = opponent(piece.color)
    const nextPlayer = this.playerFor(nextColor)
    const remaining = this.board.filter(p => p?.color === nextColor).length
    const nextMoves = remaining ? sequencesFor(this.board, nextColor) : []

    if (!remaining || !nextMoves.length) {
      this.winner = actor
      return { ok:true, path:[...chosen.path], captures:[...chosen.captures], promoted, winner:actor }
    }

    if (this.quietTurns >= 80) {
      this.isDraw = true
      this.drawReason = '80 moves without a capture or promotion'
      return { ok:true, path:[...chosen.path], captures:[...chosen.captures], promoted, draw:true }
    }

    this.currentTurn = nextPlayer
    return { ok:true, path:[...chosen.path], captures:[...chosen.captures], promoted }
  }

  toRecord() {
    return {
      playerBlack:this.playerBlack,
      playerRed:this.playerRed,
      blackName:this.blackName,
      redName:this.redName,
      board:cloneBoard(this.board),
      currentTurn:this.currentTurn,
      winner:this.winner,
      isDraw:this.isDraw,
      drawReason:this.drawReason,
      lastMove:this.lastMove ? { path:[...this.lastMove.path], captures:[...this.lastMove.captures] } : null,
      quietTurns:this.quietTurns,
    }
  }
}

function scoreBoard(board, botColor) {
  let score = 0
  for (let i = 0; i < 32; i += 1) {
    const piece = board[i]
    if (!piece) continue
    const sign = piece.color === botColor ? 1 : -1
    const { row, col } = coordFromIndex(i)
    let value = piece.rank === KING ? 3.2 : 1
    if (piece.rank === MAN) {
      const advance = piece.color === BLACK ? row : 7 - row
      value += advance * 0.05
    }
    if (col >= 2 && col <= 5) value += 0.08
    score += sign * value
  }
  return score
}

function applySequence(board, sequence) {
  const next = cloneBoard(board)
  const from = sequence.path[0]
  const to = sequence.path.at(-1)
  const piece = { ...next[from] }
  next[from] = null
  for (const captured of sequence.captures) next[captured] = null
  if (shouldPromote(piece, to)) piece.rank = KING
  next[to] = piece
  return next
}

function minimax(board, turnColor, botColor, depth, alpha, beta) {
  const legal = sequencesFor(board, turnColor)
  if (!legal.length) return turnColor === botColor ? -1000 - depth : 1000 + depth
  if (depth <= 0) return scoreBoard(board, botColor)

  const maximize = turnColor === botColor
  let best = maximize ? -Infinity : Infinity
  for (const sequence of legal) {
    const next = applySequence(board, sequence)
    const value = minimax(next, opponent(turnColor), botColor, depth - 1, alpha, beta)
    if (maximize) {
      best = Math.max(best, value)
      alpha = Math.max(alpha, best)
    } else {
      best = Math.min(best, value)
      beta = Math.min(beta, best)
    }
    if (beta <= alpha) break
  }
  return best
}

export function pickCheckersBotMove(game, level = 'normal') {
  const botColor = game.colorFor(CHECKERS_BOT_ID)
  if (!botColor) return null
  const legal = game.legalSequences(CHECKERS_BOT_ID)
  if (!legal.length) return null

  const normalized = normalizeCheckersLevel(level) || 'normal'
  if (normalized === 'easy') return legal[Math.floor(Math.random() * legal.length)]

  if (normalized === 'normal') {
    let best = []
    let bestScore = -Infinity
    for (const sequence of legal) {
      const next = applySequence(game.board, sequence)
      const score = scoreBoard(next, botColor) + sequence.captures.length * 0.6
      if (score > bestScore) {
        bestScore = score
        best = [sequence]
      } else if (score === bestScore) best.push(sequence)
    }
    return best[Math.floor(Math.random() * best.length)]
  }

  let best = []
  let bestScore = -Infinity
  for (const sequence of legal) {
    const next = applySequence(game.board, sequence)
    const score = minimax(next, opponent(botColor), botColor, 4, -Infinity, Infinity)
    if (score > bestScore) {
      bestScore = score
      best = [sequence]
    } else if (score === bestScore) best.push(sequence)
  }
  return best[Math.floor(Math.random() * best.length)]
}

export function checkersRecordAcceptsInput(record, userKey, text) {
  if (!record || record.state !== 'PLAYING') return false
  const game = new CheckersGame(record.game)
  const user = String(userKey || '')
  if (![game.playerBlack, game.playerRed].includes(user)) return false
  return Boolean(parseCheckersInput(text))
}

export const CHECKERS_BLACK = BLACK
export const CHECKERS_RED = RED
export const CHECKERS_KING = KING
export { coordFromIndex, indexFromCoord }
