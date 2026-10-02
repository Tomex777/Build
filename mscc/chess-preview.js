import { mkdir, writeFile } from 'node:fs/promises'
import { ChessGame, CHESS_BOT_ID, parseChessInput } from './utils/chess-game.js'
import { renderChessBoard } from './utils/chess-renderer.js'

const game = new ChessGame({
  playerWhite:'preview-user',
  playerBlack:CHESS_BOT_ID,
  whiteName:'Player',
  blackName:'MSCC Bot',
})

for (const [player, move] of [
  ['preview-user', 'e2 e4'],
  [CHESS_BOT_ID, 'c7 c5'],
]) {
  const result = game.move(player, parseChessInput(move))
  if (!result.ok) throw new Error(result.reason || ('Preview move failed: ' + move))
}

await mkdir('artifacts', { recursive:true })
const file = 'artifacts/chess-preview.png'
await writeFile(file, renderChessBoard(game))
console.log(file)
