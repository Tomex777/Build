import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import {
  LUDO_BOT_PREFIX,
  LUDO_FINISH_PROGRESS,
  LUDO_SAFE_GLOBALS,
  LudoGame,
  chooseLudoBotToken,
  ludoGlobalIndex,
  ludoRecordAcceptsInput,
  parseLudoInput,
  shuffleLudoColors,
} from './utils/ludo-game.js'
import { renderLudoBoard, LUDO_BOARD_SIZE } from './utils/ludo-renderer.js'
import {
  applyLudoPreset,
  getLudoTheme,
  resetLudoTheme,
  setLudoColor,
} from './utils/game-themes.js'

const players = [
  { id:'alice', name:'Alice', color:'red' },
  { id:'bob', name:'Bob', color:'yellow' },
]

const initial = new LudoGame({ players })
assert.deepEqual(initial.tokens.red, [-1,-1,-1,-1])
assert.deepEqual(initial.tokens.yellow, [-1,-1,-1,-1])
assert.equal(initial.currentPlayer.id, 'alice')
assert.deepEqual(initial.legalTokenIndexes('alice', 1), [])
assert.deepEqual(initial.legalTokenIndexes('alice', 6), [0,1,2,3])

const noMove = initial.roll('alice', 3)
assert.equal(noMove.ok, true)
assert.equal(noMove.noMove, true)
assert.equal(initial.currentPlayer.id, 'bob')

const enter = new LudoGame({ players, currentPlayerIndex:0 })
const six = enter.roll('alice', 6)
assert.equal(six.ok, true)
assert.deepEqual(six.legalTokens, [0,1,2,3])
const entered = enter.move('alice', 0)
assert.equal(entered.ok, true)
assert.equal(enter.tokens.red[0], 0)
assert.equal(entered.extraTurn, true)
assert.equal(enter.currentPlayer.id, 'alice')

assert.deepEqual(parseLudoInput('roll'), { type:'roll' })
assert.deepEqual(parseLudoInput('dice'), { type:'roll' })
assert.deepEqual(parseLudoInput('4'), { type:'token', tokenIndex:3 })
assert.deepEqual(parseLudoInput('resign'), { type:'resign' })
assert.equal(parseLudoInput('5'), null)

const capture = new LudoGame({
  players,
  currentPlayerIndex:0,
  tokens:{
    red:[0,-1,-1,-1],
    yellow:[31,-1,-1,-1],
  },
})
assert.equal(ludoGlobalIndex('red', 5), ludoGlobalIndex('yellow', 31))
assert.equal(LUDO_SAFE_GLOBALS.includes(ludoGlobalIndex('red',5)), false)
assert.equal(capture.roll('alice', 5).ok, true)
const captureMove = capture.move('alice', 0)
assert.equal(captureMove.ok, true)
assert.equal(captureMove.captured.length, 1)
assert.equal(capture.tokens.yellow[0], -1)
assert.equal(capture.currentPlayer.id, 'alice')

const safe = new LudoGame({
  players,
  currentPlayerIndex:0,
  tokens:{
    red:[7,-1,-1,-1],
    yellow:[34,-1,-1,-1],
  },
})
assert.equal(ludoGlobalIndex('red',8), 8)
assert.equal(ludoGlobalIndex('yellow',34), 8)
assert.equal(safe.roll('alice', 1).ok, true)
const safeMove = safe.move('alice', 0)
assert.equal(safeMove.captured.length, 0)
assert.equal(safe.tokens.yellow[0], 34)

const blockade = new LudoGame({
  players,
  currentPlayerIndex:0,
  tokens:{
    red:[3,-1,-1,-1],
    yellow:[31,31,-1,-1],
  },
})
assert.equal(ludoGlobalIndex('yellow',31), 5)
assert.deepEqual(blockade.legalTokenIndexes('alice', 3), [])

const finish = new LudoGame({
  players,
  currentPlayerIndex:0,
  tokens:{
    red:[56,57,57,57],
    yellow:[-1,-1,-1,-1],
  },
})
assert.deepEqual(finish.legalTokenIndexes('alice', 2), [])
assert.deepEqual(finish.legalTokenIndexes('alice', 1), [0])
assert.equal(finish.roll('alice', 1).ok, true)
const finishMove = finish.move('alice', 0)
assert.equal(finishMove.finished, true)
assert.equal(finish.tokens.red[0], LUDO_FINISH_PROGRESS)
assert.equal(finish.winner, 'alice')

const triple = new LudoGame({
  players,
  currentPlayerIndex:0,
  tokens:{ red:[0,-1,-1,-1], yellow:[-1,-1,-1,-1] },
})
assert.equal(triple.roll('alice', 6).ok, true)
assert.equal(triple.move('alice', 0).ok, true)
assert.equal(triple.roll('alice', 6).ok, true)
assert.equal(triple.move('alice', 0).ok, true)
const thirdSix = triple.roll('alice', 6)
assert.equal(thirdSix.forfeited, true)
assert.equal(triple.currentPlayer.id, 'bob')

const botId = LUDO_BOT_PREFIX + '1__'
const bot = new LudoGame({
  players:[
    { id:botId, name:'Bot', color:'green', isBot:true },
    { id:'human', name:'Human', color:'blue' },
  ],
  currentPlayerIndex:0,
})
assert.equal(bot.roll(botId, 6).ok, true)
const botToken = chooseLudoBotToken(bot, botId)
assert.ok(botToken >= 0 && botToken <= 3)
assert.equal(bot.move(botId, botToken).ok, true)

const record = { state:'PLAYING', game:enter.toRecord() }
assert.equal(ludoRecordAcceptsInput(record, 'alice', 'roll'), true)
assert.equal(ludoRecordAcceptsInput(record, 'bob', 'roll'), false)

for (let i = 0; i < 20; i += 1) {
  const colors = shuffleLudoColors(4)
  assert.equal(colors.length, 4)
  assert.equal(new Set(colors).size, 4)
}
for (let i = 0; i < 20; i += 1) {
  const colors = shuffleLudoColors(2)
  assert.equal(colors.length, 2)
  const pair = new Set(colors)
  assert.ok(
    (pair.has('red') && pair.has('yellow')) ||
    (pair.has('green') && pair.has('blue'))
  )
}

const memory = new Map()
const shared = {
  get:(namespace,key) => memory.get(namespace + '|' + key) || null,
  set:(namespace,key,value) => {
    memory.set(namespace + '|' + key, value)
    return value
  },
  delete:(namespace,key) => memory.delete(namespace + '|' + key) ? 1 : 0,
}
assert.equal(getLudoTheme(shared, 'alice').preset, 'night')
assert.equal(applyLudoPreset(shared, 'alice', 'classic').preset, 'classic')
assert.equal(setLudoColor(shared, 'alice', 'red', 'purple').red, '#9b7ad6')
assert.equal(resetLudoTheme(shared, 'alice').preset, 'night')

const image = renderLudoBoard(enter, { theme:getLudoTheme(shared, 'alice') })
assert.equal(LUDO_BOARD_SIZE, 720)
assert.equal(image.subarray(1,4).toString('ascii'), 'PNG')
assert.ok(image.length > 5000)

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  allowMissing:true,
  capabilityFromDirectory:true,
})
assert.equal(registry.commands.get('ludo')?.capability, 'games')
assert.equal(registry.commands.get('game')?.capability, 'games')

console.log('PASS Ludo rules, bots, renderer, themes, and registry')
