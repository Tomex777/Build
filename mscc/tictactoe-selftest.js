import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import {
  TICTACTOE_BOT_ID,
  TicTacToeGame,
  parseTicTacToeInput,
  pickTicTacToeBotMove,
  ticTacToeRecordAcceptsInput,
} from './utils/tictactoe-game.js'
import { renderTicTacToeBoard, TICTACTOE_BOARD_SIZE } from './utils/tictactoe-renderer.js'
import {
  applyTicTacToePreset,
  getTicTacToeTheme,
  resetTicTacToeTheme,
  setTicTacToeColor,
} from './utils/game-themes.js'

const game = new TicTacToeGame({
  playerX:'alice',
  playerO:'bob',
  xName:'Alice',
  oName:'Bob',
})
assert.equal(game.move('alice', 0).ok, true)
assert.equal(game.move('bob', 3).ok, true)
assert.equal(game.move('alice', 1).ok, true)
assert.equal(game.move('bob', 4).ok, true)
assert.equal(game.move('alice', 2).ok, true)
assert.equal(game.winner, 'alice')
assert.deepEqual(game.winningLine, [0,1,2])

assert.deepEqual(parseTicTacToeInput('9'), { type:'move', index:8 })
assert.deepEqual(parseTicTacToeInput('surrender'), { type:'resign' })
assert.equal(parseTicTacToeInput('10'), null)

const botGame = new TicTacToeGame({
  playerX:'human',
  playerO:TICTACTOE_BOT_ID,
})
assert.equal(botGame.move('human', 0).ok, true)
const botMove = pickTicTacToeBotMove(botGame, 'hard')
assert.ok(botGame.availableMoves().includes(botMove))
assert.equal(botGame.move(TICTACTOE_BOT_ID, botMove).ok, true)

const record = {
  state:'PLAYING',
  game:botGame.toRecord(),
}
assert.equal(ticTacToeRecordAcceptsInput(record, 'human', '5'), true)
assert.equal(ticTacToeRecordAcceptsInput(record, 'someone-else', '5'), false)
assert.equal(ticTacToeRecordAcceptsInput(record, 'human', 'hello'), false)

const memory = new Map()
const shared = {
  get:(namespace,key) => memory.get(namespace + '|' + key) || null,
  set:(namespace,key,value) => {
    memory.set(namespace + '|' + key, value)
    return value
  },
  delete:(namespace,key) => memory.delete(namespace + '|' + key) ? 1 : 0,
}
assert.equal(getTicTacToeTheme(shared, 'alice').preset, 'night')
assert.equal(applyTicTacToePreset(shared, 'alice', 'paper').preset, 'paper')
assert.equal(getTicTacToeTheme(shared, 'alice').background, '#ece9e1')
assert.equal(setTicTacToeColor(shared, 'alice', 'x', 'purple').x, '#9b7ad6')
assert.equal(resetTicTacToeTheme(shared, 'alice').preset, 'night')

const preview = new TicTacToeGame({
  playerX:'x',
  playerO:'o',
})
preview.move('x', 0)
preview.move('o', 4)
const image = renderTicTacToeBoard(preview, getTicTacToeTheme(shared, 'alice'))
assert.equal(TICTACTOE_BOARD_SIZE, 600)
assert.equal(image.subarray(1,4).toString('ascii'), 'PNG')
assert.ok(image.length > 5000)

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  allowMissing:true,
  capabilityFromDirectory:true,
})
assert.equal(registry.commands.get('tictactoe')?.capability, 'games')
assert.equal(registry.commands.get('ttt')?.name, 'tictactoe')
assert.equal(registry.commands.get('xo')?.name, 'tictactoe')
assert.equal(registry.commands.get('game')?.capability, 'games')

console.log('PASS Tic-Tac-Toe + game editor selftest')
