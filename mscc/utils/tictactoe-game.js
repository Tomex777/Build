export const TICTACTOE_BOT_ID = '__mscc_tictactoe_bot__'

// Nexia picture Tic-Tac-Toe's five levels, including an unbeaten Expert.
export const TICTACTOE_LEVELS = Object.freeze([
  { id:'beginner',label:'🟢 Beginner',description:'Great for learning the rules' },
  { id:'easy',label:'🟡 Easy',description:'A gentle challenge' },
  { id:'medium',label:'🟠 Medium',description:'Balanced — makes real threats' },
  { id:'hard',label:'🔴 Hard',description:'Strong play, rare mistakes' },
  { id:'expert',label:'⚫ Expert',description:'Plays optimally — unbeatable' },
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
  if (id === 'normal') return 'medium' // backwards compatibility for existing games
  return TICTACTOE_LEVELS.some(level => level.id === id) ? id : ''
}

export function parseTicTacToeInput(value, style = 'classic') {
  const text = String(value || '').trim().toLowerCase()
  if (/^(surrender|resign|give\s*up)$/.test(text)) return { type:'resign' }
  if (style === 'picture') {
    if (/^[a-c][1-3]$/.test(text)) {
      return { type:'move', index:(Number(text[1])-1)*3 + 'abc'.indexOf(text[0]) }
    }
    return null
  }
  if (/^[1-9]$/.test(text)) return { type:'move', index:Number(text) - 1 }
  return null
}

export function ticTacToeCoord(index) {
  return 'ABC'[index % 3] + String(Math.floor(index / 3) + 1)
}

const NEXIA_CELLS = Object.freeze(['1️⃣','2️⃣','3️⃣','4️⃣','5️⃣','6️⃣','7️⃣','8️⃣','9️⃣'])
export function renderNexiaTicTacToeText(game) {
  const cells=game.board.map((mark,i) => mark === 'X' ? '❎' : mark === 'O' ? '⭕' : NEXIA_CELLS[i])
  return [cells.slice(0,3).join(''),cells.slice(3,6).join(''),cells.slice(6).join('')].join('\n')
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

// Parity with Nexia's modules/games/ttt/ai.js: minimax plus tier-specific
// mistake chances, immediate-win/block heuristics and genuinely random beginner.
const NEXIA_MISTAKES = Object.freeze({beginner:.85,easy:.55,medium:.30,hard:.12,expert:0})
function nexiaMinimax(board,symbol,bot) {
  const winner=winnerFor(board)
  if(winner===bot) return {score:10}
  if(winner && winner!==bot) return {score:-10}
  const empties=board.flatMap((mark,index)=>mark?[]:[index])
  if(!empties.length)return {score:0}
  const isBot=symbol===bot
  let best=null
  for(const index of empties){
    board[index]=symbol
    const result=nexiaMinimax(board,symbol==='X'?'O':'X',bot)
    board[index]=null
    if(!best || (isBot?result.score>best.score:result.score<best.score))
      best={score:result.score,index}
  }
  return best
}
function nexiaHeuristic(board,bot){
  const opponent=bot==='X'?'O':'X'
  const empty=()=>board.flatMap((mark,index)=>mark?[]:[index])
  for(const symbol of [bot,opponent]){
    for(const index of empty()){
      board[index]=symbol
      const wins=winnerFor(board)===symbol
      board[index]=null
      if(wins)return index
    }
  }
  return [4,0,2,6,8,1,3,5,7].find(index=>!board[index])
}
export function pickTicTacToeBotMove(game, level = 'expert', random = Math.random) {
  const moves=game.availableMoves()
  if(!moves.length)return -1
  const normalized=normalizeTicTacToeLevel(level) || 'expert'
  const board=[...game.board]
  const bot=game.markFor(game.currentTurn)
  if(bot!=='X'&&bot!=='O')return -1
  if(random() >= NEXIA_MISTAKES[normalized])
    return nexiaMinimax(board,bot,bot).index
  if(normalized==='beginner')return moves[Math.floor(random()*moves.length)]
  return nexiaHeuristic(board,bot)
}

export function ticTacToeRecordAcceptsInput(record, userKey, text) {
  if (!record || record.state !== 'PLAYING') return false
  const game = new TicTacToeGame(record.game)
  const user = String(userKey || '')
  if (![game.playerX, game.playerO].includes(user)) return false
  return Boolean(parseTicTacToeInput(text, record.style === 'picture' ? 'picture' : 'classic'))
}

export const TICTACTOE_WIN_LINES = WIN_LINES
