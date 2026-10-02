export const TICTACTOE_BOT_ID = '__mscc_tictactoe_bot__'

export const TICTACTOE_LEVELS = Object.freeze([
  { id:'easy', label:'Easy', description:'Relaxed — often plays randomly' },
  { id:'normal', label:'Normal', description:'Balanced — sometimes perfect, sometimes human' },
  { id:'hard', label:'Hard', description:'Perfect play — never intentionally blunders' },
])

const WIN_LINES = Object.freeze([
  [0,1,2],[3,4,5],[6,7,8],
  [0,3,6],[1,4,7],[2,5,8],
  [0,4,8],[2,4,6],
])

function cloneBoard(board) {
  const source = Array.isArray(board) ? board : []
  return Array.from({ length:9 }, (_, i) => {
    const value = source[i]
    return value === 'X' || value === 'O' ? value : null
  })
}

export function normalizeTicTacToeLevel(value) {
  const id = String(value || '').trim().toLowerCase()
  return TICTACTOE_LEVELS.some(level => level.id === id) ? id : ''
}

export function parseTicTacToeInput(value) {
  const text = String(value || '').trim().toLowerCase()
  if (/^(surrender|resign|give\s*up)$/.test(text)) return { type:'resign' }
  if (/^[1-9]$/.test(text)) return { type:'move', index:Number(text) - 1 }
  return null
}

export class TicTacToeGame {
  constructor(record = {}) {
    this.playerX = String(record.playerX || '')
    this.playerO = String(record.playerO || '')
    this.xName = String(record.xName || 'X')
    this.oName = String(record.oName || 'O')
    this.board = cloneBoard(record.board)
    this.currentTurn = String(record.currentTurn || this.playerX)
    this.winner = String(record.winner || '')
    this.winnerMark = String(record.winnerMark || '')
    this.turns = Number.isFinite(Number(record.turns))
      ? Math.max(0, Math.min(9, Number(record.turns)))
      : this.board.filter(Boolean).length
    this.lastMove = Number.isInteger(record.lastMove) ? record.lastMove : -1
    this.winningLine = Array.isArray(record.winningLine) ? record.winningLine.slice(0,3).map(Number) : []
    this.isDraw = Boolean(record.isDraw)
  }

  markFor(player) {
    if (String(player) === this.playerX) return 'X'
    if (String(player) === this.playerO) return 'O'
    return ''
  }

  availableMoves() {
    const moves = []
    for (let i = 0; i < 9; i += 1) if (!this.board[i]) moves.push(i)
    return moves
  }

  checkWinner() {
    for (const line of WIN_LINES) {
      const [a,b,c] = line
      const mark = this.board[a]
      if (mark && mark === this.board[b] && mark === this.board[c]) {
        this.winnerMark = mark
        this.winner = mark === 'X' ? this.playerX : this.playerO
        this.winningLine = [...line]
        return this.winner
      }
    }
    if (this.turns >= 9) this.isDraw = true
    return ''
  }

  move(player, index) {
    if (this.winner || this.isDraw) return { ok:false, reason:'That game is already over.' }
    const actor = String(player || '')
    if (actor !== this.currentTurn) return { ok:false, reason:"It's not your turn." }
    if (!Number.isInteger(index) || index < 0 || index > 8) return { ok:false, reason:'Choose a square from 1 to 9.' }
    if (this.board[index]) return { ok:false, reason:'That square is already taken.' }

    const mark = this.markFor(actor)
    if (!mark) return { ok:false, reason:'You are not playing in this game.' }

    this.board[index] = mark
    this.turns += 1
    this.lastMove = index
    this.checkWinner()

    if (!this.winner && !this.isDraw) {
      this.currentTurn = actor === this.playerX ? this.playerO : this.playerX
    }

    return { ok:true, mark, index }
  }

  toRecord() {
    return {
      playerX:this.playerX,
      playerO:this.playerO,
      xName:this.xName,
      oName:this.oName,
      board:[...this.board],
      currentTurn:this.currentTurn,
      winner:this.winner,
      winnerMark:this.winnerMark,
      turns:this.turns,
      lastMove:this.lastMove,
      winningLine:[...this.winningLine],
      isDraw:this.isDraw,
    }
  }
}

function winnerFor(board) {
  for (const [a,b,c] of WIN_LINES) {
    if (board[a] && board[a] === board[b] && board[a] === board[c]) return board[a]
  }
  return ''
}

function minimax(board, maximizing, depth = 0) {
  const winner = winnerFor(board)
  if (winner === 'O') return 10 - depth
  if (winner === 'X') return depth - 10
  const available = board.flatMap((cell,index) => cell ? [] : [index])
  if (!available.length) return 0

  if (maximizing) {
    let best = -Infinity
    for (const index of available) {
      board[index] = 'O'
      best = Math.max(best, minimax(board, false, depth + 1))
      board[index] = null
    }
    return best
  }

  let best = Infinity
  for (const index of available) {
    board[index] = 'X'
    best = Math.min(best, minimax(board, true, depth + 1))
    board[index] = null
  }
  return best
}

function perfectMove(game) {
  const board = [...game.board]
  let bestScore = -Infinity
  let best = []
  for (const index of game.availableMoves()) {
    board[index] = 'O'
    const score = minimax(board, false, 0)
    board[index] = null
    if (score > bestScore) {
      bestScore = score
      best = [index]
    } else if (score === bestScore) {
      best.push(index)
    }
  }
  return best.length ? best[Math.floor(Math.random() * best.length)] : -1
}

export function pickTicTacToeBotMove(game, level = 'normal') {
  const moves = game.availableMoves()
  if (!moves.length) return -1
  const normalized = normalizeTicTacToeLevel(level) || 'normal'
  if (normalized === 'easy') return moves[Math.floor(Math.random() * moves.length)]
  if (normalized === 'normal' && Math.random() < 0.32) {
    return moves[Math.floor(Math.random() * moves.length)]
  }
  return perfectMove(game)
}

export function ticTacToeRecordAcceptsInput(record, userKey, text) {
  if (!record || record.state !== 'PLAYING') return false
  const game = new TicTacToeGame(record.game)
  const user = String(userKey || '')
  if (![game.playerX, game.playerO].includes(user)) return false
  return Boolean(parseTicTacToeInput(text))
}

export const TICTACTOE_WIN_LINES = WIN_LINES
