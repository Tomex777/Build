import { readFile } from 'node:fs/promises'

const files = Object.freeze({
  chess:new URL('../assets/games/chess-menu.webp', import.meta.url),
  tictactoe:new URL('../assets/games/tictactoe-menu.webp', import.meta.url),
  checkers:new URL('../assets/games/checkers-menu.webp', import.meta.url),
})

const cache = new Map()

export async function gameMenuArt(gameId) {
  const id = String(gameId || '').trim().toLowerCase()
  const file = files[id]
  if (!file) throw new Error('Unknown game menu art: ' + id)
  if (!cache.has(id)) cache.set(id, await readFile(file))
  return cache.get(id)
}
