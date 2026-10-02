import { Chess } from 'chess.js'

const MOVE_RE = /^([KQRBN])?([a-h][1-8])\s*(?:to\s+)?([a-h][1-8])([QRBN])?$/i
const SQUARE_RE = /^([KQRBN])?([a-h][1-8])$/i

export const CHESS_BOT_ID = '__mscc_chess_bot__'
export const PIECE_NAMES = { k:'king', q:'queen', r:'rook', b:'bishop', n:'knight', p:'pawn' }
const PIECE_LETTERS = { k:'K', q:'Q', r:'R', b:'B', n:'N', p:'' }

export const CHESS_LEVELS = [
  { id:'beginner', label:'🟢 Beginner', description:'Learning pace with frequent mistakes' },
  { id:'easy', label:'🟡 Easy', description:'Gentle, but it still sees captures' },
  { id:'medium', label:'🟠 Medium', description:'Balanced play with fewer mistakes' },
  { id:'hard', label:'🔴 Hard', description:'Strong search and rare mistakes' },
  { id:'expert', label:'⚫ Expert', description:'Strongest MSCC chess setting' },
]

const DEPTH = { beginner:1, easy:1, medium:2, hard:3, expert:3 }
const MISTAKE_CHANCE = { beginner:0.72, easy:0.42, medium:0.2, hard:0.06, expert:0 }
const PIECE_VALUE = { p:100, n:320, b:330, r:500, q:900, k:0 }
const CENTER_BONUS = {
  d4:10,d5:10,e4:10,e5:10,
  c3:4,c4:4,c5:4,c6:4,
  f3:4,f4:4,f5:4,f6:4,
}

const isCheck = chess =>
  typeof chess.isCheck === 'function' ? chess.isCheck() : Boolean(chess.inCheck?.())

const isGameOver = chess =>
  typeof chess.isGameOver === 'function' ? chess.isGameOver() : Boolean(chess.game_over?.())

const isCheckmate = chess =>
  typeof chess.isCheckmate === 'function' ? chess.isCheckmate() : Boolean(chess.in_checkmate?.())

const isStalemate = chess =>
  typeof chess.isStalemate === 'function' ? chess.isStalemate() : Boolean(chess.in_stalemate?.())

const isThreefold = chess =>
  typeof chess.isThreefoldRepetition === 'function'
    ? chess.isThreefoldRepetition()
    : Boolean(chess.in_threefold_repetition?.())

const isInsufficient = chess =>
  typeof chess.isInsufficientMaterial === 'function'
    ? chess.isInsufficientMaterial()
    : Boolean(chess.insufficient_material?.())

const isDraw = chess =>
  typeof chess.isDraw === 'function' ? chess.isDraw() : Boolean(chess.in_draw?.())

export function normalizeChessLevel(value) {
  const id = String(value || '').trim().toLowerCase()
  return CHESS_LEVELS.some(level => level.id === id) ? id : ''
}

export function parseChessInput(text) {
  if (typeof text !== 'string') return null
  const trimmed = text.trim()
  if (!trimmed) return null

  if (/^(surrender|resign)$/i.test(trimmed)) return { type:'resign' }

  const move = trimmed.match(MOVE_RE)
  if (move) {
    const [, pieceLetter, from, to, promotion] = move
    return {
      type:'move',
      from:from.toLowerCase(),
      to:to.toLowerCase(),
      promotion:promotion ? promotion.toLowerCase() : undefined,
      pieceLetterHint:pieceLetter ? pieceLetter.toUpperCase() : undefined,
    }
  }

  const square = trimmed.match(SQUARE_RE)
  if (square) return { type:'preview', square:square[2].toLowerCase() }
  return null
}

export const looksLikeChessInput = text => Boolean(parseChessInput(text))

export function chessRecordAcceptsInput(record, player, text) {
  if (!record || record.state !== 'PLAYING') return false
  if (!looksLikeChessInput(text)) return false
  const id = String(player || '')
  const game = record.game || {}
  return id === String(game.playerWhite || '') || id === String(game.playerBlack || '')
}

function evaluate(chess) {
  let score = 0
  const board = chess.board()
  for (let rank = 0; rank < 8; rank += 1) {
    for (let file = 0; file < 8; file += 1) {
      const piece = board[rank][file]
      if (!piece) continue
      const square = 'abcdefgh'[file] + (8 - rank)
      const value = PIECE_VALUE[piece.type] + (CENTER_BONUS[square] || 0)
      score += piece.color === 'w' ? value : -value
    }
  }
  if (isCheckmate(chess)) score += chess.turn() === 'w' ? -100000 : 100000
  return score
}

function minimax(chess, depth, alpha, beta, maximizing) {
  if (depth <= 0 || isGameOver(chess)) return { score:evaluate(chess), move:null }
  const moves = chess.moves({ verbose:true })
  let best = null

  for (const move of moves) {
    chess.move({ from:move.from, to:move.to, promotion:move.promotion })
    const result = minimax(chess, depth - 1, alpha, beta, !maximizing)
    chess.undo()

    if (!best || (maximizing && result.score > best.score) || (!maximizing && result.score < best.score)) {
      best = { score:result.score, move }
    }

    if (maximizing) alpha = Math.max(alpha, best.score)
    else beta = Math.min(beta, best.score)
    if (beta <= alpha) break
  }

  return best || { score:evaluate(chess), move:null }
}

export function pickChessBotMove(chess, level = 'medium', random = Math.random) {
  const moves = chess.moves({ verbose:true })
  if (!moves.length) return null

  const normalizedLevel = normalizeChessLevel(level) || 'medium'
  if (random() < MISTAKE_CHANCE[normalizedLevel]) {
    const move = moves[Math.floor(random() * moves.length)]
    return { from:move.from, to:move.to, promotion:move.promotion }
  }

  const best = minimax(
    chess,
    DEPTH[normalizedLevel] || 2,
    -Infinity,
    Infinity,
    chess.turn() === 'w',
  )
  const move = best?.move || moves[0]
  return { from:move.from, to:move.to, promotion:move.promotion }
}

function drawReason(chess) {
  if (isStalemate(chess)) return 'stalemate'
  if (isThreefold(chess)) return 'threefold repetition'
  if (isInsufficient(chess)) return 'insufficient material'
  return 'draw'
}

export class ChessGame {
  constructor({
    playerWhite,
    playerBlack,
    whiteName = '',
    blackName = '',
    fen = '',
    lastMove = null,
    winner = '',
    draw = false,
    drawReason:reason = '',
  } = {}) {
    this.playerWhite = String(playerWhite || '')
    this.playerBlack = String(playerBlack || '')
    this.whiteName = String(whiteName || '')
    this.blackName = String(blackName || '')
    this.chess = fen ? new Chess(fen) : new Chess()
    this.lastMove = lastMove?.from && lastMove?.to
      ? { from:String(lastMove.from), to:String(lastMove.to) }
      : null
    this.winner = String(winner || '')
    this.isDraw = Boolean(draw)
    this.drawReason = String(reason || '')
  }

  get currentTurn() {
    return this.chess.turn() === 'w' ? this.playerWhite : this.playerBlack
  }

  colorOf(player) {
    const id = String(player || '')
    if (id === this.playerWhite) return 'w'
    if (id === this.playerBlack) return 'b'
    return ''
  }

  isGameOver() {
    return Boolean(this.winner || this.isDraw || isGameOver(this.chess))
  }

  inCheck() {
    return isCheck(this.chess)
  }

  pieceAt(square) {
    return this.chess.get(square)
  }

  legalMovesFrom(square) {
    try {
      return this.chess.moves({ square, verbose:true }).map(move => move.to)
    } catch {
      return []
    }
  }

  checkedKingSquare() {
    if (!this.inCheck()) return ''
    const color = this.chess.turn()
    const board = this.chess.board()
    for (let rank = 0; rank < 8; rank += 1) {
      for (let file = 0; file < 8; file += 1) {
        const piece = board[rank][file]
        if (piece?.type === 'k' && piece.color === color) {
          return 'abcdefgh'[file] + (8 - rank)
        }
      }
    }
    return ''
  }

  boardMap() {
    const map = {}
    const board = this.chess.board()
    for (let rank = 0; rank < 8; rank += 1) {
      for (let file = 0; file < 8; file += 1) {
        const piece = board[rank][file]
        if (piece) map['abcdefgh'[file] + (8 - rank)] = piece
      }
    }
    return map
  }

  move(player, parsed) {
    if (this.isGameOver()) return { ok:false, reason:'This game is already over.' }
    if (String(player) !== this.currentTurn) return { ok:false, reason:"It's not your turn." }

    const { from, to, promotion, pieceLetterHint } = parsed || {}
    const piece = this.chess.get(from)
    if (!piece) return { ok:false, reason:`There's no piece on ${from}.` }
    if (piece.color !== this.chess.turn()) {
      return { ok:false, reason:`That's not your piece — ${from} has your opponent's ${PIECE_NAMES[piece.type]}.` }
    }

    if (pieceLetterHint) {
      const expected = PIECE_LETTERS[piece.type]
      if (expected !== pieceLetterHint.toUpperCase()) {
        return { ok:false, reason:`${from} has a ${PIECE_NAMES[piece.type]}, not that piece.` }
      }
    }

    let result = null
    try {
      result = this.chess.move({ from, to, promotion:promotion || 'q' })
    } catch {
      result = null
    }

    if (!result) {
      const legal = this.legalMovesFrom(from)
      return {
        ok:false,
        reason:legal.length
          ? `Illegal move. The ${PIECE_NAMES[piece.type]} on ${from} can go to: ${legal.join(', ')}.`
          : `The ${PIECE_NAMES[piece.type]} on ${from} has no legal moves.`,
      }
    }

    this.lastMove = { from, to }

    if (isCheckmate(this.chess)) {
      this.winner = String(player)
    } else if (isDraw(this.chess) || isStalemate(this.chess) || isThreefold(this.chess) || isInsufficient(this.chess)) {
      this.isDraw = true
      this.drawReason = drawReason(this.chess)
    }

    return { ok:true, move:result }
  }

  toRecord() {
    return {
      playerWhite:this.playerWhite,
      playerBlack:this.playerBlack,
      whiteName:this.whiteName,
      blackName:this.blackName,
      fen:this.chess.fen(),
      lastMove:this.lastMove,
      winner:this.winner,
      draw:this.isDraw,
      drawReason:this.drawReason,
    }
  }
}
