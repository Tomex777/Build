import { mkdir, writeFile } from 'node:fs/promises'
import { LudoGame } from './utils/ludo-game.js'
import { renderLudoBoard } from './utils/ludo-renderer.js'
import { normalizeLudoTheme } from './utils/game-themes.js'

const game = new LudoGame({
  players:[
    { id:'red', name:'Red', color:'red' },
    { id:'green', name:'Green', color:'green' },
    { id:'yellow', name:'Yellow', color:'yellow' },
    { id:'blue', name:'Blue', color:'blue' },
  ],
  tokens:{
    red:[0,12,-1,-1],
    green:[8,28,-1,-1],
    yellow:[18,54,-1,-1],
    blue:[5,57,-1,-1],
  },
  currentPlayerIndex:0,
  pendingRoll:6,
})

await mkdir(new URL('./artifacts/', import.meta.url), { recursive:true })
await writeFile(
  new URL('./artifacts/ludo-preview.png', import.meta.url),
  renderLudoBoard(game, {
    theme:normalizeLudoTheme({ preset:'night' }),
    selectablePlayerId:'red',
    selectableTokens:[0,1,2,3],
    roll:6,
  }),
)
console.log('Wrote artifacts/ludo-preview.png')


const inactiveGame = new LudoGame({
  players:[
    { id:'red-active', name:'Red Active', color:'red' },
    { id:'green-left', name:'Green Player', color:'green', eliminated:true },
    { id:'yellow-active', name:'Yellow Active', color:'yellow' },
  ],
  tokens:{
    red:[4,16,-1,-1],
    green:[9,28,-1,-1],
    yellow:[22,55,-1,-1],
    blue:[-1,-1,-1,-1],
  },
  currentPlayerIndex:0,
})
await writeFile(
  new URL('./artifacts/ludo-inactive-left-preview.png', import.meta.url),
  renderLudoBoard(inactiveGame, {
    theme:normalizeLudoTheme({ preset:'night' }),
  }),
)
console.log('Wrote artifacts/ludo-inactive-left-preview.png')
