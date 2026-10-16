// Word Chain for NIGHT: single chat/tenant state, dictionary-verified turns, and real timeouts.
// No gameplay money, artwork, or AI generation is involved.
import { randomUUID } from 'node:crypto'

export const WORD_CHAIN_NS = 'night-wordchain'
const SCORE_NS = 'night-wordchain-scores'
export const ROUND_MS = 5 * 60_000
export const TURN_MS = 20_000
const WAIT_MS = 90_000
const MAX_WORDS = 400
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

export function wordChainInputDestination({shared, accountId, chat, text, now = Date.now()} = {}) {
  if (!shared || !String(chat).endsWith('@g.us') || !validWordInput(text)) return false
  const game = shared.get(WORD_CHAIN_NS, chainKey(accountId, chat))
  return Boolean(game?.state === 'PLAYING' && now < game.expiresAt && now < game.turnDeadline)
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
  if (allowNames && /^[A-Z][a-z]{2,23}$/.test(String(raw).trim())) {
    try {
      const found = await lookup('https://en.wikipedia.org/w/api.php?action=query&format=json&origin=*&titles=' +
        encodeURIComponent(String(raw).trim()))
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
function schedule(ctx, game) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  const old = timers.get(key)
  if (old) clearTimeout(old)
  const until = Math.min(game.expiresAt, game.turnDeadline)
  const task = setTimeout(() => {
    withLock(key, async () => {
      const latest = load(ctx)
      if (!latest || latest.id !== game.id) return
      const now = Date.now()
      if (now < Math.min(latest.expiresAt, latest.turnDeadline)) return schedule(ctx, latest)
      await finish(ctx, latest, now >= latest.expiresAt
        ? '⏰ The 5-minute marathon is over.'
        : `⏰ Time! No word starting with *${latest.expected || 'ANY'}* arrived in 20 seconds.`)
    }).catch(error => console.warn('[wordchain] timeout:', error?.message))
  }, Math.max(25, until - Date.now() + 10))
  task.unref?.()
  timers.set(key, task)
}
function startRound(ctx, mode, opponent = null, now = Date.now()) {
  return {
    id:randomUUID(),state:'PLAYING',mode,host:String(ctx.userKey || ''),
    hostName:playerName(ctx),opponent,startedAt:now,
    expiresAt:now+ROUND_MS,turnDeadline:now+TURN_MS,
    words:[],expected:'',lastWord:'',lastUser:'',participants:[],
    allowNames:true,
  }
}
function status(game) {
  if (game.state === 'WAITING') return '⚔️ *Word Chain duel invitation*\nWaiting for another player: .wordchain join\nExpires in ' +
    remaining(game.expiresAt - Date.now()) + 's.'
  return [
    '🔗 *WORD CHAIN*',
    game.lastWord ? `Last: *${game.lastWord.toUpperCase()}* → *${game.expected.toUpperCase()}*`
      : 'Start with any English word!',
    `🔗 ${game.words.length} chained · ⏳ ${remaining(game.turnDeadline - Date.now())}s for next turn`,
    `⌛ ${remaining(game.expiresAt - Date.now())}s remaining overall`,
    game.mode === 'duel' ? '⚔️ Duel: players alternate.' : '👥 Anybody can play; no consecutive turns by one player.',
  ].join('\n')
}
export async function wordChainCommand(ctx, {verify = checkWord, now = () => Date.now()} = {}) {
  const key = chainKey(ctx.account?.id, ctx.message?.key?.remoteJid)
  return withLock(key, async () => {
    if (!ctx.groupKey) return ctx.reply('Word Chain is a group game. Start it in a WhatsApp group.')
    if (!ctx.shared || !String(ctx.userKey || '')) return ctx.reply('Word Chain storage or identity unavailable.')
    const args = Array.isArray(ctx.args) ? ctx.args : []
    const action = String(args[0] || '').toLowerCase()
    let game = load(ctx)
    if (game && now() >= Math.min(game.expiresAt, game.turnDeadline)) {
      const ended = await finish(ctx, game, game.state === 'WAITING'
        ? '⌛ Nobody joined the duel.'
        : now() >= game.expiresAt ? '⏰ The 5-minute marathon is over.' : '⏰ The turn timer expired.',
      {announce:false})
      if (action !== 'start' && action !== 'duel' && action !== 'new') return ctx.reply(ended)
      game = null
    }
    if (action === 'help') return ctx.reply([
      '🔗 *WORD CHAIN* — connect words using the final letter.',
      '.wordchain — Start a 5-minute group marathon',
      '.wordchain duel — Challenge somebody (join with .wordchain join)',
      '.wordchain join — Accept a duel challenge',
      '.wordchain word <word> — Play a word manually',
      '.wordchain status / best / stop',
      '.wordchain names on|off — Allow verified proper names (host only)',
      'Example: BOOK → KEEP → PATRICK → KITE.',
      '⏱️ 20 seconds per word; no repeats or consecutive plays by one person.',
      'You can simply type a word in the group during a round.',
    ].join('\n'))
    if (action === 'best' || action === 'score' || action === 'record') {
      const s = wordChainStats(ctx)
      return ctx.reply(`🏆 *Word Chain group record*\nLongest chain: *${s.best} words*\nGames: ${s.games} · Total words: ${s.total}`)
    }
    if (action === 'stop' || action === 'cancel') {
      if (!game) return ctx.reply('No Word Chain round is running.')
      if (!canStop(ctx, game)) return ctx.reply('Only the host or bot owner can end this round.')
      return ctx.reply(await finish(ctx, game, `🛑 Stopped by ${playerName(ctx)}.`, {announce:false}))
    }
    if (action === 'status') return ctx.reply(game ? status(game) : 'No game running. Use .wordchain to start.')
    if (action === 'join') {
      if (!game || game.state !== 'WAITING') return ctx.reply('No duel waiting. Start one with .wordchain duel.')
      if (game.host === ctx.userKey) return ctx.reply('You cannot join your own challenge.')
      game = startRound(ctx, 'duel', String(ctx.userKey))
      // The original challenger takes the opening turn.
      game.host = load(ctx).host; game.hostName = load(ctx).hostName
      store(ctx, game);schedule(ctx,game)
      return ctx.reply('⚔️ *WORD CHAIN DUEL STARTED!*\n' +
        game.hostName + ' starts with any word. Then take turns.\n⏳ 20s each · 5-minute marathon.')
    }
    if (action === 'names') {
      if (!game || game.state !== 'PLAYING') return ctx.reply('Start a Word Chain round first.')
      if (!canStop(ctx,game)) return ctx.reply('Only the host can change the name rules.')
      const setting = String(args[1] || '').toLowerCase()
      if (!['on','off'].includes(setting)) return ctx.reply('Usage: .wordchain names on|off')
      game.allowNames = setting === 'on'
      store(ctx,game);schedule(ctx,game)
      return ctx.reply('Proper names ' + (game.allowNames?'enabled (verified names only).':'disabled.') )
    }
    if (['start','new','duel',''].includes(action)) {
      if (game) return ctx.reply(status(game))
      if (action === 'duel') {
        const invitation = {id:randomUUID(),state:'WAITING',mode:'duel',host:ctx.userKey,
          hostName:playerName(ctx),expiresAt:now()+WAIT_MS,turnDeadline:now()+WAIT_MS,
          words:[],lastWord:''}
        store(ctx,invitation);schedule(ctx,invitation)
        return ctx.reply('⚔️ *WORD CHAIN DUEL*\n'+playerName(ctx)+' challenged the group!\n' +
          'Another player: .wordchain join\n⏳ Accept within 90 seconds.')
      }
      game = startRound(ctx,'open',null,now());store(ctx,game);schedule(ctx,game)
      return ctx.reply('🔗 *WORD CHAIN STARTED!*\nSay any English word to begin.\n' +
        '⏱️ 20s per word • 5 minutes total\nNo repeats. Alternate players.\n' +
        'Example: BOOK → KEEP → PATRICK.\nEnd the game with .wordchain stop.')
    }
    if (!game || game.state !== 'PLAYING') return ctx.reply('Start with .wordchain, or .wordchain duel.')
    const typed = action === '~input' ? String(ctx.commandReplyInput || '') :
      action === 'word' ? args.slice(1).join(' ') : args.join(' ')
    const word = normalizeWord(typed)
    if (!word) return ctx.reply('Send one English word (2–24 letters).')
    if (game.mode === 'duel' && ![game.host, game.opponent].includes(ctx.userKey)) return ctx.reply('This duel is for its two players.')
    if (game.mode === 'duel' && !game.lastUser && ctx.userKey !== game.host) return ctx.reply('The challenger takes the first turn.')
    if (game.lastUser === ctx.userKey) return ctx.reply('⏳ Somebody else must play the next word.')
    if (game.expected && word[0] !== game.expected) {
      return ctx.reply(`❌ Start with *${game.expected.toUpperCase()}* (last: ${game.lastWord.toUpperCase()}).`)
    }
    if (game.words.includes(word)) return ctx.reply('♻️ Already used! Try another word.')
    const result = await verify(typed,{allowNames:game.allowNames})
    if (!result.ok) {
      if (result.unavailable) {
        // Don't penalize players for a stalled third-party dictionary.
        game.turnDeadline = Math.min(game.expiresAt, Math.max(game.turnDeadline, now()+8000))
        store(ctx,game);schedule(ctx,game)
      }
      return ctx.reply('❌ ' + (result.reason || 'Unknown word.'))
    }
    const current = load(ctx)
    if (!current || current.id !== game.id) return ctx.reply('This round has already ended.')
    game.words.push(word)
    if (game.words.length > MAX_WORDS) return ctx.reply(await finish(ctx,game,'🏁 Maximum chain reached.',{announce:false}))
    game.lastWord=word;game.expected=word.at(-1);game.lastUser=ctx.userKey
    game.participants=[...new Set([...(game.participants || []),String(ctx.userKey)])].slice(0,100)
    const finished = now() >= game.expiresAt
    if (finished) return ctx.reply(await finish(ctx,game,'🏁 5-minute marathon finished!',{announce:false}))
    game.turnDeadline=Math.min(game.expiresAt, now()+TURN_MS)
    store(ctx,game);schedule(ctx,game)
    return ctx.reply(`✅ *${word.toUpperCase()}* → *${game.expected.toUpperCase()}*\n🔗 ${game.words.length} word${game.words.length === 1 ? '' : 's'} · ⏱️ ${remaining(game.turnDeadline - now())}s`)
  })
}
