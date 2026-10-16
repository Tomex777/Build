// Word Chain for NIGHT: opt-in multiplayer with untimed default and optional time trials.
// No gameplay money, artwork, or AI generation is involved.
import { randomUUID } from 'node:crypto'

export const WORD_CHAIN_NS = 'night-wordchain'
const SCORE_NS = 'night-wordchain-scores'
export const ROUND_MS = 5 * 60_000
export const TURN_MS = 20_000
const WAIT_MS = 120_000
const LOBBY_MS = 10 * 60_000
const MAX_PLAYERS = 50
const timers = new Map()
const locks = new Map()
const wordCache = new Map()

export const normalizeWord = input => {
  const word = String(input || '').trim().normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '').toLowerCase()
  return /^[a-z]{2,24}$/.test(word) ? word : ''
}
export const validWordInput = input => /^[a-zA-Z]{2,24}$/.test(String(input || '').trim())
export const chainKey = (accountId, chat) => String(accountId || '') + '|' + String(chat || '')
export const playerName = ctx => String(ctx.message?.pushName || 'Player').trim().slice(0,40) || 'Player'

export function wordChainInputDestination({shared, accountId, chat, text, userKey = '', now = Date.now()} = {}) {
  if (!shared || !String(chat || '').endsWith('@g.us') || !userKey) return false
  const game = shared.get(WORD_CHAIN_NS, chainKey(accountId, chat))
  if (!game) return false
  // Lobby participation is explicit: 'join' is accepted as an ordinary message.
  if (game.state === 'WAITING') return now < game.expiresAt &&
    String(text || '').trim().toLowerCase() === 'join' &&
    !game.players?.some(player => player.id === userKey) &&
    (game.players?.length || 0) < (game.mode === 'duel' ? 2 : MAX_PLAYERS)
  if (game.state !== 'PLAYING') return false
  if (game.timed && now >= Math.min(game.expiresAt, game.turnDeadline)) return false
  if (!game.players?.some(player => player.id === userKey)) return false
  if (game.lastUser === userKey) return false
  if (game.mode === 'duel' && !game.lastUser && game.host !== userKey) return false
  return validWordInput(text)
}
const remaining = ms => Math.max(0, Math.ceil(ms / 1000))

function withLock(key, fn) {
  const previous = locks.get(key) || Promise.resolve()
  const pending = previous.catch(() => {}).then(fn)
  locks.set(key, pending)
  return pending.finally(() => { if (locks.get(key) === pending) locks.delete(key) })
}
async function requestExists(url, signalMs = 2000) {
  const response = await fetch(url, {signal:AbortSignal.timeout(signalMs), headers:{accept:'application/json'}})
  if (response.status === 404) return false
  if (!response.ok) throw new Error('Word verification temporarily unavailable')
  const body = await response.json()
  return body
}
export async function checkWord(raw, {allowNames = true, lookup = requestExists} = {}) {
  const word = normalizeWord(raw)
  if (!word) return {ok:false, reason:'Use one English word (2–24 letters).'}
  const cacheKey = word + ':' + (allowNames ? 'names' : 'strict')
  const cached = wordCache.get(cacheKey)
  if (cached && cached.expiresAt > Date.now()) return {ok:cached.ok, reason:cached.ok?'':'That word is not recognized.'}
  let dictionaryUnavailable = false
  try {
    const found = await lookup('https://api.dictionaryapi.dev/api/v2/entries/en/' + encodeURIComponent(word))
    if (Array.isArray(found) && found.some(item => Array.isArray(item?.meanings) && item.meanings.length)) {
      wordCache.set(cacheKey, {ok:true, expiresAt:Date.now()+86_400_000})
      return {ok:true}
    }
  } catch { dictionaryUnavailable = true }
  // Independent, free fallback for words that the first dictionary misses.
  // Accept exact matches only; near-spellings and suggestions cannot pass.
  try {
    const alternatives = await lookup('https://api.datamuse.com/words?sp=' + encodeURIComponent(word) + '&max=8')
    if (Array.isArray(alternatives) && alternatives.some(row => String(row?.word || '').toLowerCase() === word)) {
      wordCache.set(cacheKey, {ok:true, expiresAt:Date.now()+86_400_000})
      return {ok:true}
    }
  } catch { dictionaryUnavailable = true }
  // Proper names, including "Patrick", are permitted when verified against Wikipedia.
  // A capital initial alone is not proof that an invented name exists.
  if (allowNames && /^[A-Za-z]{2,24}$/.test(String(raw).trim())) {
    try {
      const found = await lookup('https://en.wikipedia.org/w/api.php?action=query&format=json&origin=*&titles=' +
        encodeURIComponent(word[0].toUpperCase() + word.slice(1)))
      const pages = Object.values(found?.query?.pages || {})
      if (pages.some(page => page && !('missing' in page))) {
        wordCache.set(cacheKey,{ok:true,expiresAt:Date.now()+86_400_000})
        return {ok:true}
      }
    } catch { dictionaryUnavailable = true }
  }
  if (dictionaryUnavailable) return {ok:false, unavailable:true, reason:'Dictionary unavailable. Try your word again in a moment.'}
  if (wordCache.size > 3000) wordCache.clear()
  wordCache.set(cacheKey, {ok:false, expiresAt:Date.now()+3_600_000})
  return {ok:false, reason:'That word is not recognized. Try another.'}
}

function load(ctx) {
  return ctx.shared?.get(WORD_CHAIN_NS, chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)) || null
}
function store(ctx, game) {
  ctx.shared?.set(WORD_CHAIN_NS, chainKey(ctx.account?.id, ctx.message?.key?.remoteJid), game)
}
function clear(ctx) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  const timer = timers.get(key)
  if (timer) clearTimeout(timer)
  timers.delete(key)
  ctx.shared?.delete(WORD_CHAIN_NS, key)
}
function canStop(ctx, game) {
  return String(ctx.userKey || '') === game.host ||
    (Array.isArray(ctx.identityKeys) && ctx.identityKeys.includes(game.host)) ||
    ctx.isPublicOwner === true || ctx.isSessionOwner === true || ctx.isSupremeOwner === true
}
function score(ctx, game) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  const prior = ctx.shared?.get(SCORE_NS, key) || {best:0, games:0, total:0}
  const result = {
    best:Math.max(prior.best || 0, game.words.length),
    games:(prior.games || 0)+1,
    total:(prior.total || 0)+game.words.length,
  }
  ctx.shared?.set(SCORE_NS, key, result)
  return result
}
export const wordChainStats = ctx => ctx.shared?.get(SCORE_NS,
  chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)) || {best:0,games:0,total:0}

async function finish(ctx, game, reason, {announce = true} = {}) {
  clear(ctx)
  const stats = game.state === 'PLAYING' ? score(ctx, game) : wordChainStats(ctx)
  const text = [
    '🔗 *WORD CHAIN — ENDED*',
    reason,
    `Chain: *${game.words?.length || 0} words*` +
      (game.lastWord ? ` · Last: *${game.lastWord.toUpperCase()}*` : ''),
    `🏆 Group best: ${stats.best} words`,
    'Play again: .wordchain',
  ].join('\n')
  if (!announce) return text
  const chat = String(ctx.message?.key?.remoteJid || '')
  if (ctx.account?.sock?.sendMessage) {
    await ctx.account.sock.sendMessage(chat, {text}).catch(() => {})
    return text
  }
  return ctx.reply(text)
}
function deadline(game) {
  if (game.state === 'WAITING') return game.expiresAt
  if (game.state === 'PLAYING' && game.timed) return Math.min(game.expiresAt, game.turnDeadline)
  return null // Untimed sessions have no background timer or turn deadline.
}
function expired(game, now) {
  const due = deadline(game)
  return due != null && now >= due
}
function expiredText(game, now) {
  if (game.state === 'WAITING') return '⌛ The Word Chain lobby expired before the game started.'
  return now >= game.expiresAt
    ? '⏰ The 5-minute timed game is over.'
    : `⏰ Turn time expired! Expected a word starting with *${game.expected || 'ANY'}*.`
}
function schedule(ctx, game) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  const old = timers.get(key)
  if (old) clearTimeout(old)
  timers.delete(key)
  const due = deadline(game)
  if (due == null) return
  const task = setTimeout(() => {
    withLock(key, async () => {
      const latest = load(ctx)
      if (!latest || latest.id !== game.id) return
      const at = Date.now()
      if (!expired(latest, at)) return schedule(ctx, latest)
      await finish(ctx, latest, expiredText(latest, at))
    }).catch(error => console.warn('[wordchain] timeout:', error?.message))
  }, Math.max(25, due - Date.now() + 10))
  task.unref?.()
  timers.set(key, task)
}
function newLobby(ctx, mode = 'open', timed = false, at = Date.now()) {
  return {
    id:randomUUID(),state:'WAITING',mode,timed,host:String(ctx.userKey || ''),
    hostName:playerName(ctx),createdAt:at,expiresAt:at+(mode === 'duel' ? WAIT_MS : LOBBY_MS),
    turnDeadline:null,words:[],expected:'',lastWord:'',lastUser:'',
    players:[{id:String(ctx.userKey || ''),name:playerName(ctx)}],
    allowNames:true,
  }
}
function begin(game, at = Date.now()) {
  game.state = 'PLAYING'
  game.startedAt = at
  game.expiresAt = game.timed ? at+ROUND_MS : null
  game.turnDeadline = game.timed ? at+TURN_MS : null
  return game
}
function labelMode(game) {
  return game.timed ? '⏱️ Timed: 20 seconds per turn, 5 minutes total.' : '♾️ Untimed: no turn countdown and no overall limit.'
}
function status(game, at = Date.now()) {
  const count = game.players?.length || 0
  if (game.state === 'WAITING') return [
    game.mode === 'duel' ? '⚔️ *WORD CHAIN DUEL LOBBY*' : '🔗 *WORD CHAIN LOBBY*',
    `👥 ${count} joined · ${labelMode(game)}`,
    'Type *join* or .wordchain join to register.',
    game.mode === 'duel' ? 'Starts automatically when the opponent joins.' :
      count >= 2 ? 'Host: .wordchain start to begin.' : 'Waiting for another player.',
    `Lobby closes in ${remaining(game.expiresAt - at)}s if not started.`,
  ].join('\n')
  return [
    '🔗 *WORD CHAIN*',
    game.lastWord ? `Last: *${game.lastWord.toUpperCase()}* → *${game.expected.toUpperCase()}*` :
      'First player: start with any English word!',
    `👥 ${count} players · 🔗 ${game.words.length} words`,
    labelMode(game),
    ...(game.timed ? [`⏳ ${remaining(game.turnDeadline-at)}s left this turn · ${remaining(game.expiresAt-at)}s overall`] : []),
    'Players must alternate. Only registered players may answer.',
  ].join('\n')
}
export async function wordChainCommand(ctx, {verify = checkWord, now = () => Date.now()} = {}) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  return withLock(key, async () => {
    if (!ctx.groupKey) return ctx.reply('Word Chain is a group game. Start it in a WhatsApp group.')
    if (!ctx.shared || !String(ctx.userKey || '')) return ctx.reply('Word Chain storage or identity unavailable.')
    const args = Array.isArray(ctx.args) ? ctx.args : []
    const action = String(args[0] || '').toLowerCase()
    const inbound = action === '~input' ? String(ctx.commandReplyInput || '').trim() : ''
    let game = load(ctx)
    if (game && expired(game, now())) {
      const msg = await finish(ctx, game, expiredText(game, now()), {announce:false})
      if (['','new','timed','duel'].includes(action)) game = null
      else return ctx.reply(msg)
    }
    if (action === 'help') return ctx.reply([
      '🔗 *WORD CHAIN* — use the last letter to start the next word.',
      '.wordchain — Open a group lobby (untimed by default)',
      '.wordchain timed — Open a timed group lobby (20s per turn / 5min)',
      '.wordchain duel [timed] — Challenge one player',
      'Type join or .wordchain join to enter the lobby.',
      '.wordchain start — Host starts a group lobby with 2+ players',
      '.wordchain word <word> — Play a word manually',
      '.wordchain status / best / stop',
      '.wordchain names on|off — Verified names allowed (host only)',
      'Example: BOOK → KEEP → PATRICK → KITE.',
      'Once started, registered players simply type words in the chat.',
    ].join('\n'))
    if (['best','score','record'].includes(action)) {
      const stats = wordChainStats(ctx)
      return ctx.reply(`🏆 *Word Chain record*\nLongest chain: *${stats.best} words*\nGames: ${stats.games} · Total words: ${stats.total}`)
    }
    if (['stop','cancel'].includes(action)) {
      if (!game) return ctx.reply('No Word Chain game or lobby is running.')
      if (!canStop(ctx, game)) return ctx.reply('Only the host or bot owner can stop this game.')
      return ctx.reply(await finish(ctx, game, `🛑 Stopped by ${playerName(ctx)}.`, {announce:false}))
    }
    if (action === 'status') return ctx.reply(game ? status(game,now()) : 'No Word Chain running. Type .wordchain to open a lobby.')
    if (action === 'join' || (action === '~input' && inbound.toLowerCase() === 'join')) {
      if (!game) return ctx.reply('No lobby open. Use .wordchain to create one.')
      if (game.state !== 'WAITING') return ctx.reply('This round already started. Wait for the next lobby.')
      if (game.players.some(p => p.id === ctx.userKey)) return ctx.reply('✅ You already joined this Word Chain lobby.')
      if (game.players.length >= (game.mode === 'duel' ? 2 : MAX_PLAYERS)) return ctx.reply('This Word Chain lobby is full.')
      game.players.push({id:String(ctx.userKey),name:playerName(ctx)})
      if (game.mode === 'duel') {
        begin(game,now());store(ctx,game);schedule(ctx,game)
        return ctx.reply(`⚔️ *WORD CHAIN DUEL STARTED*\n${game.hostName} goes first.\n${labelMode(game)}\nNo repeat words; reply with the next word.`)
      }
      store(ctx,game)
      return ctx.reply(`✅ *${playerName(ctx)} joined Word Chain!*\n👥 ${game.players.length} players ready.\nHost: .wordchain start when everyone's in.`)
    }
    if (action === 'start' || action === 'go') {
      if (!game) return ctx.reply('Open a lobby with .wordchain first.')
      if (game.state !== 'WAITING' || game.mode !== 'open') return ctx.reply('No group lobby is waiting to start.')
      if (!canStop(ctx,game)) return ctx.reply('Only the host can start this lobby.')
      if (game.players.length < 2) return ctx.reply('Need at least two players. Ask them to type join.')
      begin(game,now());store(ctx,game);schedule(ctx,game)
      return ctx.reply([
        '🔗 *WORD CHAIN STARTED!*',
        `👥 ${game.players.length} players · ${labelMode(game)}`,
        'Any joined player: send an English word to begin.',
        'Each new word must start with the previous word’s final letter.',
        'No repeats. Alternate players. End with .wordchain stop.',
      ].join('\n'))
    }
    if (action === 'names') {
      if (!game || game.state !== 'PLAYING') return ctx.reply('Start a Word Chain game first.')
      if (!canStop(ctx,game)) return ctx.reply('Only the host can change name rules.')
      const toggle = String(args[1] || '').toLowerCase()
      if (!['on','off'].includes(toggle)) return ctx.reply('Usage: .wordchain names on|off')
      game.allowNames = toggle === 'on';store(ctx,game)
      return ctx.reply('Verified proper names are now '+(game.allowNames?'allowed.':'disabled.'))
    }
    if (['','new','timed','duel'].includes(action)) {
      if (game) return ctx.reply(status(game,now()))
      const timed = action === 'timed' || (action === 'duel' && String(args[1] || '').toLowerCase() === 'timed')
      game = newLobby(ctx,action === 'duel' ? 'duel' : 'open',timed,now())
      store(ctx,game);schedule(ctx,game)
      return ctx.reply(status(game,now()))
    }
    if (!game || game.state !== 'PLAYING') return ctx.reply('Start a game using .wordchain, then ask players to join.')
    if (!game.players.some(p => p.id === ctx.userKey)) return ctx.reply('Only joined players can play. Join the next lobby.')
    const typed = action === '~input' ? inbound : action === 'word' ? args.slice(1).join(' ') : args.join(' ')
    const word = normalizeWord(typed)
    if (!word) return ctx.reply('Send one English word (2–24 letters).')
    if (game.mode === 'duel' && !game.lastUser && ctx.userKey !== game.host)
      return ctx.reply('The challenger takes the first turn.')
    if (game.lastUser === ctx.userKey) return ctx.reply('Somebody else must play the next word.')
    if (game.expected && word[0] !== game.expected) return ctx.reply(
      `❌ Start with *${game.expected.toUpperCase()}* (last word: ${game.lastWord.toUpperCase()}).`)
    if (game.words.includes(word)) return ctx.reply('♻️ Already used! Try a new word.')
    const result = await verify(typed,{allowNames:game.allowNames})
    if (!result.ok) {
      if (result.unavailable && game.timed) {
        // Do not penalize a valid attempt when an external word-check provider stalls.
        game.turnDeadline = Math.min(game.expiresAt,Math.max(game.turnDeadline, now()+8000))
        store(ctx,game);schedule(ctx,game)
      }
      return ctx.reply('❌ '+(result.reason || 'Unknown word.'))
    }
    if (game.timed && expired(game,now())) {
      return ctx.reply(await finish(ctx,game,expiredText(game,now()),{announce:false}))
    }
    const current = load(ctx)
    if (!current || current.id !== game.id) return ctx.reply('This round ended while verifying the word.')
    game.words.push(word)
    game.lastWord=word;game.expected=word.at(-1);game.lastUser=String(ctx.userKey)
    if (game.timed) game.turnDeadline=Math.min(game.expiresAt,now()+TURN_MS)
    store(ctx,game);schedule(ctx,game)
    const count = game.words.length
    return ctx.reply(`✅ *${word.toUpperCase()}* → *${game.expected.toUpperCase()}*\n🔗 ${count} word${count === 1 ? '' : 's'}${game.timed ? ' · ⏱️ '+remaining(game.turnDeadline-now())+'s' : ''}`)
  })
}
