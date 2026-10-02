import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import chessCommand from './commands/games/chess.js'
import {
  CHESS_BOT_ID,
  CHESS_LEVELS,
  ChessGame,
  chessRecordAcceptsInput,
  normalizeChessLevel,
  parseChessInput,
  pickChessBotMove,
} from './utils/chess-game.js'
import { CHESS_BOARD_SIZE, CHESS_CELL_SIZE, renderChessBoard } from './utils/chess-renderer.js'

assert.equal(CHESS_LEVELS.length, 5)
assert.equal(normalizeChessLevel('HARD'), 'hard')
assert.equal(normalizeChessLevel('impossible'), '')

assert.deepEqual(parseChessInput('e2 e4'), {
  type:'move',
  from:'e2',
  to:'e4',
  promotion:undefined,
  pieceLetterHint:undefined,
})
assert.equal(parseChessInput('Nf3').type, 'preview')
assert.equal(parseChessInput('surrender').type, 'resign')
assert.equal(parseChessInput('hello'), null)

const game = new ChessGame({
  playerWhite:'111',
  playerBlack:'222',
  whiteName:'White Player',
  blackName:'Black Player',
})
assert.equal(game.currentTurn, '111')
assert.deepEqual(game.legalMovesFrom('e2').sort(), ['e3','e4'])
assert.equal(game.move('222', parseChessInput('e7 e5')).ok, false)
assert.equal(game.move('111', parseChessInput('e2 e4')).ok, true)
assert.equal(game.currentTurn, '222')

const restored = new ChessGame(game.toRecord())
assert.equal(restored.chess.fen(), game.chess.fen())
assert.deepEqual(restored.lastMove, { from:'e2', to:'e4' })

const image = renderChessBoard(restored)
assert.ok(Buffer.isBuffer(image))
assert.equal(image[0], 0x89)
assert.equal(image.subarray(1, 4).toString('ascii'), 'PNG')
assert.equal(CHESS_BOARD_SIZE, 590)
assert.equal(CHESS_CELL_SIZE, 65)

const decoded = await loadImage(image)
assert.equal(decoded.width, 590)
assert.equal(decoded.height, 590)
const probe = createCanvas(590, 590)
const probeCtx = probe.getContext('2d')
probeCtx.drawImage(decoded, 0, 0)
const rgba = (x, y) => Array.from(probeCtx.getImageData(x, y, 1, 1).data)
assert.deepEqual(rgba(35 + 5, 35 + 5).slice(0, 3), [22,22,22])
assert.deepEqual(rgba(35 + 65 + 5, 35 + 5).slice(0, 3), [43,43,43])

const botGame = new ChessGame({
  playerWhite:'111',
  playerBlack:CHESS_BOT_ID,
})
assert.equal(botGame.move('111', parseChessInput('e2 e4')).ok, true)
const botMove = pickChessBotMove(botGame.chess, 'beginner', () => 0.99)
assert.ok(botMove?.from && botMove?.to)
assert.equal(botGame.move(CHESS_BOT_ID, botMove).ok, true)
assert.equal(botGame.currentTurn, '111')

const activeRecord = {
  state:'PLAYING',
  expiresAt:Date.now() + 10_000,
  game:{ playerWhite:'111', playerBlack:'222' },
}
assert.equal(chessRecordAcceptsInput(activeRecord, '111', 'g1 f3'), true)
assert.equal(chessRecordAcceptsInput(activeRecord, '333', 'g1 f3'), false)
assert.equal(chessRecordAcceptsInput(activeRecord, '111', 'hello'), false)

const shared = new Map()
const lists = []
const replies = []
const sent = []
const ctx = {
  args:[],
  publicPrefix:'.',
  userKey:'111',
  groupKey:'group@g.us',
  message:{
    key:{ remoteJid:'group@g.us' },
    pushName:'Alice',
  },
  shared:{
    get:(ns, key) => shared.get(ns + '|' + key) || null,
    set:(ns, key, value) => { shared.set(ns + '|' + key, value); return value },
    delete:(ns, key) => shared.delete(ns + '|' + key),
  },
  reply:async value => { replies.push(String(value)); return value },
  ui:{
    bottomSheet:async value => { lists.push(value); return value },
    joinCancel:async value => { lists.push(value); return value },
  },
  replyList:async value => { lists.push(value); return value },
  account:{
    sock:{
      sendMessage:async (chat, payload) => { sent.push({ chat, payload }); return payload },
    },
  },
}

await chessCommand.run(ctx)
assert.equal(lists.at(-1).rows.length, 2)
assert.equal(lists.at(-1).rows[0].id, '.chess ~person')
assert.equal(lists.at(-1).rows[1].id, '.chess ~bot')

ctx.args = ['~bot']
await chessCommand.run(ctx)
assert.equal(lists.at(-1).rows.length, 5)
assert.ok(lists.at(-1).rows.every(row => row.id.startsWith('.chess ~botlevel ')))

ctx.args = ['~botlevel','beginner']
await chessCommand.run(ctx)
const stored = shared.get('chess-game|group@g.us')
assert.equal(stored.state, 'PLAYING')
assert.equal(stored.mode, 'bot')
assert.equal(stored.level, 'beginner')
assert.deepEqual(new Set([stored.game.playerWhite, stored.game.playerBlack]), new Set(['111', CHESS_BOT_ID]))
assert.ok(['111', CHESS_BOT_ID].includes(stored.game.playerWhite))
assert.ok(['111', CHESS_BOT_ID].includes(stored.game.playerBlack))
assert.ok(sent.some(item => Buffer.isBuffer(item.payload?.image)))

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})
for (const key of ['chess','chessgame']) {
  const command = registry.commands.get(key)
  assert.ok(command, 'Missing chess command/alias: ' + key)
  assert.equal(command.name, 'chess')
  assert.equal(command.capability, 'games')
}

console.log('visual chess self-test passed')
