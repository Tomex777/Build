import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import {
  TICTACTOE_BOT_ID,
  TicTacToeGame,
  parseTicTacToeInput,
  renderNexiaTicTacToeText,
  ticTacToeCoord,
  TICTACTOE_LEVELS,
  pickTicTacToeBotMove,
  ticTacToeRecordAcceptsInput,
} from './utils/tictactoe-game.js'
import { renderTicTacToeBoard, renderTicTacToeMenuArt,renderNexiaTicTacToePicture,TICTACTOE_BOARD_SIZE } from './utils/tictactoe-renderer.js'
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
assert.equal(TICTACTOE_LEVELS.length,5)
assert.deepEqual(TICTACTOE_LEVELS.map(level=>level.id),['beginner','easy','medium','hard','expert'])
assert.deepEqual(parseTicTacToeInput('B2','picture'),{type:'move',index:4})
assert.deepEqual(parseTicTacToeInput('c3','picture'),{type:'move',index:8})
assert.equal(parseTicTacToeInput('1','picture'),null)
assert.equal(parseTicTacToeInput('B2','classic'),null)
assert.equal(ticTacToeCoord(4),'B2')
assert.equal(renderNexiaTicTacToeText(new TicTacToeGame()).split('\n').length,3)
assert.ok(renderNexiaTicTacToeText(game).includes('❎'))


const botGame = new TicTacToeGame({
  playerX:'human',
  playerO:TICTACTOE_BOT_ID,
})
assert.equal(botGame.move('human', 0).ok, true)
const botMove = pickTicTacToeBotMove(botGame, 'hard')
assert.ok(botGame.availableMoves().includes(botMove))
assert.equal(botGame.move(TICTACTOE_BOT_ID, botMove).ok, true)


// Bot must also play correctly when randomized to X.
const botAsX = new TicTacToeGame({
  playerX:TICTACTOE_BOT_ID,
  playerO:'human',
})
const opening = pickTicTacToeBotMove(botAsX, 'hard', () => 0)
assert.ok(botAsX.availableMoves().includes(opening))
assert.equal(botAsX.move(TICTACTOE_BOT_ID, opening).ok, true)
assert.equal(botAsX.markFor(TICTACTOE_BOT_ID), 'X')

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

const nexiaPicture=renderNexiaTicTacToePicture(preview)
assert.equal(nexiaPicture[0],0xff)
assert.equal(nexiaPicture[1],0xd8)
assert.ok(nexiaPicture.length>5000)

const menuArt = renderTicTacToeMenuArt()
assert.equal(menuArt.subarray(1,4).toString('ascii'), 'PNG')
assert.ok(menuArt.length > 5000)


const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  allowMissing:true,
  capabilityFromDirectory:true,
})
assert.equal(registry.commands.get('tictactoe')?.capability, 'games')
assert.equal(registry.commands.get('ttt')?.name, 'tictactoe')
assert.equal(registry.commands.get('xo')?.name, 'tictactoe')
assert.equal(registry.commands.get('game')?.capability, 'games')
assert.equal(registry.commands.get('tttpic')?.name,'tictactoe')
assert.equal(registry.commands.get('tttboard')?.name,'tictactoe')

// Exercise the actual command handler: Nexia room waiting/join, bare 1-9
// moves, per-player turn enforcement, winner cleanup, picture coordinates
// and the immediate single-image bot turn.
const command=registry.commands.get('tictactoe')
const messages=[]
const sock={sendMessage:async(chat,payload)=>{
  messages.push({chat,payload})
  return {key:{id:'test-'+messages.length}}
}}
function chatCtx(user,content,args=[]){
  return {
    userKey:user,
    groupKey:'night-test@g.us',
    shared,
    publicPrefix:'.',
    rawCommandText:content,
    args,
    message:{key:{remoteJid:'night-test@g.us',participant:user+'@s.whatsapp.net'},
      pushName:user},
    account:{sock},
    reply:async text=>{messages.push({chat:'night-test@g.us',payload:{text}});return true},
  }
}
await command.run(chatCtx('alice','.ttt'))
assert.match(messages.at(-1).payload.text,/Waiting for opponent/)
assert.match(messages.at(-1).payload.text,/\.ttt/)
const lobby=shared.get('tictactoe-game','night-test@g.us')
assert.equal(lobby.state,'WAITING')
assert.equal(lobby.style,'classic')
await command.run(chatCtx('bob','.ttt'))
assert.ok(messages.at(-1).payload.text.includes('1️⃣2️⃣3️⃣'))
const started=shared.get('tictactoe-game','night-test@g.us')
assert.equal(started.game.playerX,'alice')
assert.equal(started.game.playerO,'bob')
await command.run({...chatCtx('bob','2',['~input']),commandReplyInput:'2'})
assert.match(messages.at(-1).payload.text,/not your turn/i)
await command.run({...chatCtx('alice','1',['~input']),commandReplyInput:'1'})
assert.match(messages.at(-1).payload.text,/❎2️⃣3️⃣/)
await command.run({...chatCtx('alice','3',['~input']),commandReplyInput:'3'})
assert.match(messages.at(-1).payload.text,/not your turn/i)
await command.run({...chatCtx('bob','4',['~input']),commandReplyInput:'4'})
await command.run({...chatCtx('alice','2',['~input']),commandReplyInput:'2'})
await command.run({...chatCtx('bob','5',['~input']),commandReplyInput:'5'})
await command.run({...chatCtx('alice','3',['~input']),commandReplyInput:'3'})
assert.match(messages.at(-1).payload.text,/wins the game/i)
assert.equal(shared.get('tictactoe-game','night-test@g.us'),null)
await command.run(chatCtx('alice','.tttpic'))
assert.equal(shared.get('tictactoe-game','night-test@g.us').style,'picture')
await command.run(chatCtx('bob','.tttpic'))
assert.ok(Buffer.isBuffer(messages.at(-1).payload.image))
await command.run({...chatCtx('alice','B2',['~input']),commandReplyInput:'B2'})
assert.ok(Buffer.isBuffer(messages.at(-1).payload.image))
assert.equal(shared.get('tictactoe-game','night-test@g.us').game.board[4],'X')
await command.run({...chatCtx('bob','surrender',['~input']),commandReplyInput:'surrender'})
assert.match(messages.at(-1).payload.text,/resigned/i)
assert.equal(shared.get('tictactoe-game','night-test@g.us'),null)
const dmCtx={...chatCtx('alice','.tttpic bot expert',['bot','expert']),
  groupKey:null,
  message:{key:{remoteJid:'alice@s.whatsapp.net'},pushName:'Alice'},
}
await command.run(dmCtx)
const before=messages.length
await command.run({...dmCtx,args:['~input'],commandReplyInput:'B2',
  rawCommandText:'B2'})
assert.equal(messages.length,before+1,'bot turn must send one board, not two')
assert.ok(Buffer.isBuffer(messages.at(-1).payload.image))
assert.equal(shared.get('tictactoe-game','alice@s.whatsapp.net').game.turns,2)


console.log('PASS Tic-Tac-Toe + game editor selftest')
