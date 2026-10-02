export const TICTACTOE_GAME_ID = 'tictactoe'
export const GAME_THEME_NAMESPACE = 'game-theme'

export const TICTACTOE_THEME_PRESETS = Object.freeze({
  night:Object.freeze({
    id:'night',
    label:'NIGHT',
    background:'#0e0e0e',
    board:'#161616',
    grid:'#4a4a4a',
    x:'#4ca9c9',
    o:'#c9a84c',
    hint:'#737373',
    last:'#25333a',
    win:'#355d46',
  }),
  graphite:Object.freeze({
    id:'graphite',
    label:'Graphite',
    background:'#111318',
    board:'#1b1f27',
    grid:'#5c6370',
    x:'#d8dee9',
    o:'#aab2c0',
    hint:'#69717e',
    last:'#273442',
    win:'#3a5146',
  }),
  paper:Object.freeze({
    id:'paper',
    label:'Paper',
    background:'#ece9e1',
    board:'#f8f6f0',
    grid:'#626262',
    x:'#1f4f67',
    o:'#7a5521',
    hint:'#aaa69d',
    last:'#dbe4e8',
    win:'#d7e7d9',
  }),
  neon:Object.freeze({
    id:'neon',
    label:'Neon',
    background:'#080b12',
    board:'#101726',
    grid:'#334866',
    x:'#55d9ff',
    o:'#ffcf5a',
    hint:'#52647f',
    last:'#17364b',
    win:'#1b513f',
  }),
})

export const TICTACTOE_COLOR_CHOICES = Object.freeze({
  cyan:'#4ca9c9',
  gold:'#c9a84c',
  white:'#f0f0f0',
  blue:'#5d8cff',
  green:'#5dbb84',
  red:'#c95d5d',
  purple:'#9b7ad6',
  orange:'#d8914f',
  gray:'#8a8f98',
})

const HEX_RE = /^#[0-9a-f]{6}$/i

export function normalizeHex(value, fallback) {
  const text = String(value || '').trim()
  return HEX_RE.test(text) ? text.toLowerCase() : fallback
}

export function normalizeTicTacToeTheme(theme = {}) {
  const preset = TICTACTOE_THEME_PRESETS[String(theme?.preset || theme?.id || '').toLowerCase()]
    || TICTACTOE_THEME_PRESETS.night
  return {
    version:1,
    preset:String(theme?.preset || preset.id),
    background:normalizeHex(theme?.background, preset.background),
    board:normalizeHex(theme?.board, preset.board),
    grid:normalizeHex(theme?.grid, preset.grid),
    x:normalizeHex(theme?.x, preset.x),
    o:normalizeHex(theme?.o, preset.o),
    hint:normalizeHex(theme?.hint, preset.hint),
    last:normalizeHex(theme?.last, preset.last),
    win:normalizeHex(theme?.win, preset.win),
  }
}

export function ticTacToeThemeStorageKey(userKey) {
  return `${String(userKey || '').trim()}:tictactoe`
}

export function getTicTacToeTheme(shared, userKey) {
  const saved = shared?.get?.(GAME_THEME_NAMESPACE, ticTacToeThemeStorageKey(userKey))
  return normalizeTicTacToeTheme(saved || TICTACTOE_THEME_PRESETS.night)
}

export function saveTicTacToeTheme(shared, userKey, theme) {
  const normalized = normalizeTicTacToeTheme(theme)
  shared?.set?.(GAME_THEME_NAMESPACE, ticTacToeThemeStorageKey(userKey), normalized)
  return normalized
}

export function resetTicTacToeTheme(shared, userKey) {
  shared?.delete?.(GAME_THEME_NAMESPACE, ticTacToeThemeStorageKey(userKey))
  return normalizeTicTacToeTheme(TICTACTOE_THEME_PRESETS.night)
}

export function applyTicTacToePreset(shared, userKey, presetId) {
  const preset = TICTACTOE_THEME_PRESETS[String(presetId || '').toLowerCase()]
  if (!preset) return null
  return saveTicTacToeTheme(shared, userKey, { ...preset, preset:preset.id })
}

export function setTicTacToeColor(shared, userKey, field, colorId) {
  const allowed = new Set(['x','o','grid'])
  if (!allowed.has(field)) return null
  const color = TICTACTOE_COLOR_CHOICES[String(colorId || '').toLowerCase()]
  if (!color) return null
  const current = getTicTacToeTheme(shared, userKey)
  return saveTicTacToeTheme(shared, userKey, { ...current, preset:'custom', [field]:color })
}


export const CHESS_THEME_PRESETS = Object.freeze({
  night:Object.freeze({
    id:'night',
    label:'NIGHT',
    background:'#0e0e0e',
    light:'#2b2b2b',
    dark:'#161616',
    labelColor:'#888888',
    whitePiece:'#f0f0f0',
    blackPiece:'#c9a84c',
    lastMove:'#4ca9c9',
    check:'#c94c4c',
    preview:'#4ca9c9',
    capture:'#c94c4c',
  }),
  graphite:Object.freeze({
    id:'graphite',
    label:'Graphite',
    background:'#111318',
    light:'#59606b',
    dark:'#252a31',
    labelColor:'#9aa1ac',
    whitePiece:'#f1f3f5',
    blackPiece:'#aeb6c2',
    lastMove:'#6ba7c0',
    check:'#c76868',
    preview:'#6ba7c0',
    capture:'#c76868',
  }),
  classic:Object.freeze({
    id:'classic',
    label:'Classic',
    background:'#17130f',
    light:'#c8b08a',
    dark:'#6f4f32',
    labelColor:'#b7a68f',
    whitePiece:'#f5efe5',
    blackPiece:'#2b2118',
    lastMove:'#557ea3',
    check:'#aa4747',
    preview:'#557ea3',
    capture:'#aa4747',
  }),
})

export function normalizeChessTheme(theme = {}) {
  const preset = CHESS_THEME_PRESETS[String(theme?.preset || theme?.id || '').toLowerCase()]
    || CHESS_THEME_PRESETS.night
  return {
    version:1,
    preset:String(theme?.preset || preset.id),
    background:normalizeHex(theme?.background, preset.background),
    light:normalizeHex(theme?.light, preset.light),
    dark:normalizeHex(theme?.dark, preset.dark),
    labelColor:normalizeHex(theme?.labelColor, preset.labelColor),
    whitePiece:normalizeHex(theme?.whitePiece, preset.whitePiece),
    blackPiece:normalizeHex(theme?.blackPiece, preset.blackPiece),
    lastMove:normalizeHex(theme?.lastMove, preset.lastMove),
    check:normalizeHex(theme?.check, preset.check),
    preview:normalizeHex(theme?.preview, preset.preview),
    capture:normalizeHex(theme?.capture, preset.capture),
  }
}

export function chessThemeStorageKey(userKey) {
  return `${String(userKey || '').trim()}:chess`
}

export function getChessTheme(shared, userKey) {
  const saved = shared?.get?.(GAME_THEME_NAMESPACE, chessThemeStorageKey(userKey))
  return normalizeChessTheme(saved || CHESS_THEME_PRESETS.night)
}

export function saveChessTheme(shared, userKey, theme) {
  const normalized = normalizeChessTheme(theme)
  shared?.set?.(GAME_THEME_NAMESPACE, chessThemeStorageKey(userKey), normalized)
  return normalized
}

export function resetChessTheme(shared, userKey) {
  shared?.delete?.(GAME_THEME_NAMESPACE, chessThemeStorageKey(userKey))
  return normalizeChessTheme(CHESS_THEME_PRESETS.night)
}

export function applyChessPreset(shared, userKey, presetId) {
  const preset = CHESS_THEME_PRESETS[String(presetId || '').toLowerCase()]
  if (!preset) return null
  return saveChessTheme(shared, userKey, { ...preset, preset:preset.id })
}

export function setChessColor(shared, userKey, field, colorId) {
  const allowed = new Set(['whitePiece','blackPiece','preview','lastMove'])
  if (!allowed.has(field)) return null
  const color = TICTACTOE_COLOR_CHOICES[String(colorId || '').toLowerCase()]
  if (!color) return null
  const current = getChessTheme(shared, userKey)
  return saveChessTheme(shared, userKey, { ...current, preset:'custom', [field]:color })
}


export const CHECKERS_THEME_PRESETS = Object.freeze({
  night:Object.freeze({
    id:'night',
    label:'NIGHT',
    background:'#0e0e0e',
    light:'#2b2b2b',
    dark:'#161616',
    blackPiece:'#151515',
    redPiece:'#b51f1f',
    crown:'#c9a84c',
    hint:'#4ca9c9',
    last:'#4ca9c9',
  }),
  graphite:Object.freeze({
    id:'graphite',
    label:'Graphite',
    background:'#111318',
    light:'#565d67',
    dark:'#272b31',
    blackPiece:'#17191d',
    redPiece:'#7f8792',
    crown:'#d7dce3',
    hint:'#6ba7c0',
    last:'#6ba7c0',
  }),
  classic:Object.freeze({
    id:'classic',
    label:'Classic',
    background:'#17130f',
    light:'#c7a66c',
    dark:'#6b4327',
    blackPiece:'#1c1b1a',
    redPiece:'#a32d24',
    crown:'#e0b858',
    hint:'#557ea3',
    last:'#557ea3',
  }),
})

export function normalizeCheckersTheme(theme = {}) {
  const preset = CHECKERS_THEME_PRESETS[String(theme?.preset || theme?.id || '').toLowerCase()]
    || CHECKERS_THEME_PRESETS.night
  return {
    version:1,
    preset:String(theme?.preset || preset.id),
    background:normalizeHex(theme?.background, preset.background),
    light:normalizeHex(theme?.light, preset.light),
    dark:normalizeHex(theme?.dark, preset.dark),
    blackPiece:normalizeHex(theme?.blackPiece, preset.blackPiece),
    redPiece:normalizeHex(theme?.redPiece, preset.redPiece),
    crown:normalizeHex(theme?.crown, preset.crown),
    hint:normalizeHex(theme?.hint, preset.hint),
    last:normalizeHex(theme?.last, preset.last),
  }
}

export function checkersThemeStorageKey(userKey) {
  return `${String(userKey || '').trim()}:checkers`
}

export function getCheckersTheme(shared, userKey) {
  const saved = shared?.get?.(GAME_THEME_NAMESPACE, checkersThemeStorageKey(userKey))
  return normalizeCheckersTheme(saved || CHECKERS_THEME_PRESETS.night)
}

export function saveCheckersTheme(shared, userKey, theme) {
  const normalized = normalizeCheckersTheme(theme)
  shared?.set?.(GAME_THEME_NAMESPACE, checkersThemeStorageKey(userKey), normalized)
  return normalized
}

export function resetCheckersTheme(shared, userKey) {
  shared?.delete?.(GAME_THEME_NAMESPACE, checkersThemeStorageKey(userKey))
  return normalizeCheckersTheme(CHECKERS_THEME_PRESETS.night)
}

export function applyCheckersPreset(shared, userKey, presetId) {
  const preset = CHECKERS_THEME_PRESETS[String(presetId || '').toLowerCase()]
  if (!preset) return null
  return saveCheckersTheme(shared, userKey, { ...preset, preset:preset.id })
}

export function setCheckersColor(shared, userKey, field, colorId) {
  const allowed = new Set(['blackPiece','redPiece','crown','hint','last'])
  if (!allowed.has(field)) return null
  const color = TICTACTOE_COLOR_CHOICES[String(colorId || '').toLowerCase()]
  if (!color) return null
  const current = getCheckersTheme(shared, userKey)
  return saveCheckersTheme(shared, userKey, { ...current, preset:'custom', [field]:color })
}
