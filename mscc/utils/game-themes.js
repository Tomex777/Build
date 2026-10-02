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
