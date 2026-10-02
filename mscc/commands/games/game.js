import { ChessGame } from '../../utils/chess-game.js'
import { TicTacToeGame } from '../../utils/tictactoe-game.js'
import { CheckersGame } from '../../utils/checkers-game.js'
import { renderChessBoard } from '../../utils/chess-renderer.js'
import { renderTicTacToeBoard } from '../../utils/tictactoe-renderer.js'
import { renderCheckersBoard } from '../../utils/checkers-renderer.js'
import {
  CHESS_THEME_PRESETS,
  CHECKERS_THEME_PRESETS,
  TICTACTOE_COLOR_CHOICES,
  TICTACTOE_THEME_PRESETS,
  applyChessPreset,
  applyCheckersPreset,
  applyTicTacToePreset,
  getChessTheme,
  getCheckersTheme,
  getTicTacToeTheme,
  resetChessTheme,
  resetCheckersTheme,
  resetTicTacToeTheme,
  setChessColor,
  setCheckersColor,
  setTicTacToeColor,
} from '../../utils/game-themes.js'

const COLOR_LABELS = Object.freeze({
  cyan:'Cyan', gold:'Gold', white:'White', blue:'Blue', green:'Green',
  red:'Red', purple:'Purple', orange:'Orange', gray:'Gray',
})

function prefix(ctx) {
  return String(ctx.publicPrefix || '.')
}

function user(ctx) {
  return String(ctx.userKey || '')
}

async function sendPreview(ctx, gameId, note = '') {
  const chat = String(ctx.message?.key?.remoteJid || '')
  if (!chat || !ctx.account?.sock) throw new Error('Game editor connection is unavailable.')

  if (gameId === 'chess') {
    const game = new ChessGame({
      playerWhite:'preview-white',
      playerBlack:'preview-black',
      whiteName:'White',
      blackName:'Black',
    })
    game.move('preview-white', { type:'move', from:'e2', to:'e4', promotion:'q' })
    game.move('preview-black', { type:'move', from:'c7', to:'c5', promotion:'q' })
    return ctx.account.sock.sendMessage(chat, {
      image:renderChessBoard(game, { theme:getChessTheme(ctx.shared, user(ctx)) }),
      caption:['*Chess theme preview*', note, 'This theme will be snapshotted when you start your next match.'].filter(Boolean).join('\n'),
    }, { quoted:ctx.message })
  }

  if (gameId === 'checkers') {
    const game = new CheckersGame({
      playerBlack:'preview-black',
      playerRed:'preview-red',
      blackName:'Black',
      redName:'Red',
    })
    game.move('preview-black', [8,12])
    game.move('preview-red', [21,17])
    return ctx.account.sock.sendMessage(chat, {
      image:renderCheckersBoard(game, { theme:getCheckersTheme(ctx.shared, user(ctx)) }),
      caption:['*Checkers theme preview*', note, 'This theme will be snapshotted when you start your next match.'].filter(Boolean).join('\n'),
    }, { quoted:ctx.message })
  }

  const game = new TicTacToeGame({
    playerX:'preview-x',
    playerO:'preview-o',
    xName:'X',
    oName:'O',
  })
  game.move('preview-x', 0)
  game.move('preview-o', 4)
  game.move('preview-x', 8)
  return ctx.account.sock.sendMessage(chat, {
    image:renderTicTacToeBoard(game, getTicTacToeTheme(ctx.shared, user(ctx))),
    caption:['*Tic-Tac-Toe theme preview*', note, 'This theme will be snapshotted when you start your next match.'].filter(Boolean).join('\n'),
  }, { quoted:ctx.message })
}

async function showGameList(ctx) {
  const p = prefix(ctx)
  return ctx.ui.bottomSheet({
    title:'Game editor',
    text:'Choose a game to customize.',
    buttonText:'Choose game',
    rows:[
      {
        title:'♟️ Chess',
        description:'Board, pieces and indicators',
        id:p + 'game ~edit chess',
      },
      {
        title:'❎ Tic-Tac-Toe',
        description:'Board, X, O and grid',
        id:p + 'game ~edit tictactoe',
      },
      {
        title:'🔴 Checkers',
        description:'Board, pieces, kings and indicators',
        id:p + 'game ~edit checkers',
      },
    ],
  })
}

async function showTicTacToeEditor(ctx) {
  const p = prefix(ctx)
  return ctx.ui.bottomSheet({
    title:'Tic-Tac-Toe editor',
    text:'Edit your personal board. The host theme is used for the whole match.',
    buttonText:'Customize',
    rows:[
      { title:'👁️ Preview', description:'See your current board', id:p + 'game ~preview tictactoe' },
      { title:'🎨 Board preset', description:'Change the whole visual theme', id:p + 'game ~presets tictactoe' },
      { title:'❎ X color', description:'Change your X mark color', id:p + 'game ~colors tictactoe x' },
      { title:'⭕ O color', description:'Change your O mark color', id:p + 'game ~colors tictactoe o' },
      { title:'#️⃣ Grid color', description:'Change the grid lines', id:p + 'game ~colors tictactoe grid' },
      { title:'↺ Reset', description:'Restore the NIGHT default', id:p + 'game ~reset tictactoe' },
    ],
  })
}

async function showChessEditor(ctx) {
  const p = prefix(ctx)
  return ctx.ui.bottomSheet({
    title:'Chess editor',
    text:'Edit your personal chess appearance. The starter theme owns the match.',
    buttonText:'Customize',
    rows:[
      { title:'👁️ Preview', description:'See your current board', id:p + 'game ~preview chess' },
      { title:'🎨 Board preset', description:'Change squares and background', id:p + 'game ~presets chess' },
      { title:'⚪ White pieces', description:'Change White piece color', id:p + 'game ~colors chess whitePiece' },
      { title:'⚫ Black pieces', description:'Change Black piece color', id:p + 'game ~colors chess blackPiece' },
      { title:'◉ Move indicators', description:'Change preview/last-move color', id:p + 'game ~colors chess preview' },
      { title:'↺ Reset', description:'Restore the NIGHT default', id:p + 'game ~reset chess' },
    ],
  })
}

async function showCheckersEditor(ctx) {
  const p = prefix(ctx)
  return ctx.ui.bottomSheet({
    title:'Checkers editor',
    text:'Edit your personal Checkers board. The starter theme owns the match.',
    buttonText:'Customize',
    rows:[
      { title:'👁️ Preview', description:'See your current board', id:p + 'game ~preview checkers' },
      { title:'🎨 Board preset', description:'Change squares and background', id:p + 'game ~presets checkers' },
      { title:'⚫ Black pieces', description:'Change Black piece color', id:p + 'game ~colors checkers blackPiece' },
      { title:'🔴 Red pieces', description:'Change Red piece color', id:p + 'game ~colors checkers redPiece' },
      { title:'♛ King crown', description:'Change promoted-piece crown color', id:p + 'game ~colors checkers crown' },
      { title:'◉ Move indicators', description:'Change preview and last-move color', id:p + 'game ~colors checkers hint' },
      { title:'↺ Reset', description:'Restore the NIGHT default', id:p + 'game ~reset checkers' },
    ],
  })
}

function editorFor(ctx, gameId) {
  if (gameId === 'chess') return showChessEditor(ctx)
  if (gameId === 'checkers') return showCheckersEditor(ctx)
  return showTicTacToeEditor(ctx)
}

async function showPresets(ctx, gameId) {
  const p = prefix(ctx)
  const presets = gameId === 'chess' ? CHESS_THEME_PRESETS : gameId === 'checkers' ? CHECKERS_THEME_PRESETS : TICTACTOE_THEME_PRESETS
  return ctx.ui.bottomSheet({
    title:gameId === 'chess' ? 'Chess board preset' : gameId === 'checkers' ? 'Checkers board preset' : 'Tic-Tac-Toe board preset',
    text:'Choose a preset. You can still change individual colors afterward.',
    buttonText:'Choose preset',
    rows:Object.values(presets).map(item => ({
      title:item.label,
      description:item.id === 'night' ? 'Default NIGHT appearance' : 'Use this appearance',
      id:p + 'game ~setpreset ' + gameId + ' ' + item.id,
    })),
  })
}

async function showColors(ctx, gameId, field) {
  const p = prefix(ctx)
  return ctx.ui.bottomSheet({
    title:'Choose color',
    text:'Pick a color for this part of the game.',
    buttonText:'Choose color',
    rows:Object.keys(TICTACTOE_COLOR_CHOICES).map(id => ({
      title:COLOR_LABELS[id] || id,
      description:'Apply this color',
      id:p + 'game ~setcolor ' + gameId + ' ' + field + ' ' + id,
    })),
  })
}

async function setPreset(ctx, gameId, presetId) {
  const theme = gameId === 'chess'
    ? applyChessPreset(ctx.shared, user(ctx), presetId)
    : gameId === 'checkers'
      ? applyCheckersPreset(ctx.shared, user(ctx), presetId)
      : applyTicTacToePreset(ctx.shared, user(ctx), presetId)
  if (!theme) return ctx.reply('That game preset is not available.')
  await sendPreview(ctx, gameId, 'Preset: ' + presetId)
  return editorFor(ctx, gameId)
}

async function setColor(ctx, gameId, field, colorId) {
  let theme = null
  if (gameId === 'chess') {
    theme = setChessColor(ctx.shared, user(ctx), field, colorId)
    if (field === 'preview' && theme) {
      theme = setChessColor(ctx.shared, user(ctx), 'lastMove', colorId)
    }
  } else if (gameId === 'checkers') {
    theme = setCheckersColor(ctx.shared, user(ctx), field, colorId)
    if (field === 'hint' && theme) theme = setCheckersColor(ctx.shared, user(ctx), 'last', colorId)
  } else {
    theme = setTicTacToeColor(ctx.shared, user(ctx), field, colorId)
  }
  if (!theme) return ctx.reply('That color option is not available for this part.')
  await sendPreview(ctx, gameId, (COLOR_LABELS[colorId] || colorId) + ' applied.')
  return editorFor(ctx, gameId)
}

async function resetTheme(ctx, gameId) {
  if (gameId === 'chess') resetChessTheme(ctx.shared, user(ctx))
  else if (gameId === 'checkers') resetCheckersTheme(ctx.shared, user(ctx))
  else resetTicTacToeTheme(ctx.shared, user(ctx))
  await sendPreview(ctx, gameId, 'Reset to the NIGHT default.')
  return editorFor(ctx, gameId)
}

export default {
  name:'game',
  aliases:['games'],
  description:'Open shared game tools and visual editors.',
  usage:'.game edit',
  async run(ctx) {
    try {
      const args = Array.isArray(ctx.args) ? ctx.args.map(String) : []
      const first = String(args[0] || '').toLowerCase()

      if (!first || first === 'edit') return showGameList(ctx)
      if (first === '~edit') {
        const gameId = String(args[1] || '').toLowerCase()
        return editorFor(ctx, gameId)
      }
      if (first === '~preview') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        await sendPreview(ctx, gameId)
        return editorFor(ctx, gameId)
      }
      if (first === '~presets') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        return showPresets(ctx, gameId)
      }
      if (first === '~colors') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        return showColors(ctx, gameId, String(args[2] || ''))
      }
      if (first === '~setpreset') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        return setPreset(ctx, gameId, String(args[2] || ''))
      }
      if (first === '~setcolor') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        return setColor(ctx, gameId, String(args[2] || ''), String(args[3] || ''))
      }
      if (first === '~reset') {
        const rawGame = String(args[1] || '').toLowerCase()
        const gameId = rawGame === 'chess' ? 'chess' : rawGame === 'checkers' ? 'checkers' : 'tictactoe'
        return resetTheme(ctx, gameId)
      }

      return ctx.reply('Use .game edit to customize a game.')
    } catch (error) {
      console.error('MSCC game editor failed:', error)
      return ctx.reply('I could not open the game editor.')
    }
  },
}
