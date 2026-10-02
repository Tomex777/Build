import { mkdir, writeFile } from 'node:fs/promises'
import { CheckersGame } from './utils/checkers-game.js'
import { renderCheckersBoard } from './utils/checkers-renderer.js'
import { getCheckersTheme } from './utils/game-themes.js'

const game = new CheckersGame({
  playerBlack:'preview-black',
  playerRed:'preview-red',
  blackName:'Black',
  redName:'Red',
})

for (const [player, path] of [
  ['preview-black', [8,12]],
  ['preview-red', [21,17]],
]) {
  const result = game.move(player, path)
  if (!result.ok) throw new Error(result.reason || 'Checkers preview move failed')
}

const legal = game.legalSequences('preview-black')
const previewOrigin = legal[0]?.path?.[0] ?? -1
const previewTargets = [...new Set(legal.filter(move => move.path[0] === previewOrigin).map(move => move.path[1]))]

await mkdir(new URL('./artifacts/', import.meta.url), { recursive:true })
await writeFile(
  new URL('./artifacts/checkers-preview.png', import.meta.url),
  renderCheckersBoard(game, {
    theme:getCheckersTheme(null, 'preview'),
    previewOrigin,
    previewTargets,
  }),
)
console.log('Wrote artifacts/checkers-preview.png')
