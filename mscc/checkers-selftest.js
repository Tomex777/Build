import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import {
  CHECKERS_BOT_ID,
  CHECKERS_BLACK,
  CHECKERS_KING,
  CheckersGame,
  parseCheckersInput,
  pickCheckersBotMove,
  checkersRecordAcceptsInput,
  coordFromIndex,
  indexFromCoord,
} from './utils/checkers-game.js'
import { renderCheckersBoard, CHECKERS_BOARD_SIZE } from './utils/checkers-renderer.js'
import {
  applyCheckersPreset,
  getCheckersTheme,
  resetCheckersTheme,
  setCheckersColor,
} from './utils/game-themes.js'
import { gameMenuArt } from './utils/game-menu-art.js'

const initial = new CheckersGame({
  playerBlack:'alice',
  playerRed:'bob',
  blackName:'Alice',
  redName:'Bob',
})
assert.equal(initial.board.filter(piece => piece?.color === 'b').length, 12)
assert.equal(initial.board.filter(piece => piece?.color === 'r').length, 12)
assert.equal(initial.legalSequences('alice').length, 7)
assert.equal(initial.currentTurn, 'alice')

assert.deepEqual(coordFromIndex(8), { row:2, col:1 })
assert.equal(indexFromCoord(2, 1), 8)
assert.deepEqual(parseCheckersInput('9'), { type:'preview', from:8 })
assert.deepEqual(parseCheckersInput('9 13'), { type:'move', path:[8,12] })
assert.deepEqual(parseCheckersInput('9-13'), { type:'move', path:[8,12] })
assert.deepEqual(parseCheckersInput('9 18 27'), { type:'move', path:[8,17,26] })
assert.deepEqual(parseCheckersInput('surrender'), { type:'resign' })
assert.equal(parseCheckersInput('33 29'), null)

const forcedBoard = Array(32).fill(null)
forcedBoard[8] = { color:'b', rank:'m' }
forcedBoard[13] = { color:'r', rank:'m' }
forcedBoard[22] = { color:'r', rank:'m' }
const forced = new CheckersGame({
  playerBlack:'alice',
  playerRed:'bob',
  blackName:'Alice',
  redName:'Bob',
  board:forcedBoard,
  currentTurn:'alice',
})
const forcedMoves = forced.legalSequences('alice')
assert.equal(forcedMoves.length, 1)
assert.deepEqual(forcedMoves[0].path, [8,17,26])
assert.deepEqual(forcedMoves[0].captures, [13,22])
const forcedResult = forced.move('alice', [8,17,26])
assert.equal(forcedResult.ok, true)
assert.equal(forcedResult.captures.length, 2)
assert.equal(forced.winner, 'alice')

const promotionBoard = Array(32).fill(null)
promotionBoard[24] = { color:'b', rank:'m' }
promotionBoard[31] = { color:'r', rank:'m' }
const promotion = new CheckersGame({
  playerBlack:'alice',
  playerRed:'bob',
  board:promotionBoard,
  currentTurn:'alice',
})
const promotionResult = promotion.move('alice', [24,28])
assert.equal(promotionResult.ok, true)
assert.equal(promotionResult.promoted, true)
assert.equal(promotion.pieceAt(28).rank, CHECKERS_KING)

const botGame = new CheckersGame({
  playerBlack:CHECKERS_BOT_ID,
  playerRed:'human',
  blackName:'MSCC Bot',
  redName:'Human',
})
const botMove = pickCheckersBotMove(botGame, 'hard')
assert.ok(botMove)
assert.ok(botGame.legalSequences(CHECKERS_BOT_ID).some(move =>
  JSON.stringify(move.path) === JSON.stringify(botMove.path)
))
assert.equal(botGame.move(CHECKERS_BOT_ID, botMove.path).ok, true)
assert.equal(botGame.colorFor(CHECKERS_BOT_ID), CHECKERS_BLACK)

const record = { state:'PLAYING', game:botGame.toRecord() }
assert.equal(checkersRecordAcceptsInput(record, 'human', '21'), true)
assert.equal(checkersRecordAcceptsInput(record, 'someone-else', '21'), false)
assert.equal(checkersRecordAcceptsInput(record, 'human', 'hello'), false)

const memory = new Map()
const shared = {
  get:(namespace,key) => memory.get(namespace + '|' + key) || null,
  set:(namespace,key,value) => {
    memory.set(namespace + '|' + key, value)
    return value
  },
  delete:(namespace,key) => memory.delete(namespace + '|' + key) ? 1 : 0,
}
assert.equal(getCheckersTheme(shared, 'alice').preset, 'night')
assert.equal(applyCheckersPreset(shared, 'alice', 'classic').preset, 'classic')
assert.equal(setCheckersColor(shared, 'alice', 'redPiece', 'purple').redPiece, '#9b7ad6')
assert.equal(resetCheckersTheme(shared, 'alice').preset, 'night')

const image = renderCheckersBoard(initial, { theme:getCheckersTheme(shared, 'alice') })
assert.equal(CHECKERS_BOARD_SIZE, 600)
assert.equal(image.subarray(1,4).toString('ascii'), 'PNG')
assert.ok(image.length > 5000)

for (const id of ['chess','tictactoe','checkers']) {
  const art = await gameMenuArt(id)
  assert.equal(art.subarray(0,4).toString('ascii'), 'RIFF')
  assert.ok(art.length > 5000)
}

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  allowMissing:true,
  capabilityFromDirectory:true,
})
assert.equal(registry.commands.get('checkers')?.capability, 'games')
assert.equal(registry.commands.get('draughts')?.name, 'checkers')
assert.equal(registry.commands.get('drafts')?.name, 'checkers')
assert.equal(registry.commands.get('game')?.capability, 'games')

console.log('PASS Checkers + locked game menu art selftest')
