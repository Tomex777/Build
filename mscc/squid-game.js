import { findGroupParticipant, participantIds, updateSucceeded } from './group-member-identity.js'
import { commandText, contextInfo, normalizedContent } from './utils/whatsapp/messages.js'

const NS = 'night-squid-game'
const INDEX = 'night-squid-active'
const BOOT = Date.now()
const GRACE_MS = 1800
const REG_MS = 15 * 60 * 1000
const MAX_REG_MS = 24 * 60 * 60 * 1000
const WAT_OFFSET_MS = 60 * 60 * 1000
const formatTime = ms => new Intl.DateTimeFormat('en-GB', {
  timeZone:'Africa/Lagos', day:'2-digit', month:'short', hour:'2-digit',
  minute:'2-digit', hour12:false,
}).format(new Date(ms)) + ' WAT'
const timeLeft = ms => {
  const minutes = Math.ceil(Math.max(0, ms) / 60000)
  return minutes >= 60 ? Math.floor(minutes / 60) + 'h ' + minutes % 60 + 'm' : minutes + 'm'
}
// Times without a UTC offset are always interpreted as WAT (Nigeria time).
function registrationWindow(parts, at = Date.now()) {
  const values = (Array.isArray(parts) ? parts : [parts]).map(v => String(v || '').trim().toLowerCase()).filter(Boolean)
  if (!values.length) return { ms:REG_MS, description:'15 minutes' }
  if (values[0] === 'in') return registrationWindow(values.slice(1), at)
  if (values[0] === 'at') {
    if (values.length !== 2 || !/^([01]?\d|2[0-3]):[0-5]\d$/.test(values[1])) {
      throw new Error('Use .squidgame at HH:MM (24-hour WAT / Nigeria time).')
    }
    const [hours, minutes] = values[1].split(':').map(Number)
    const watNow = at + WAT_OFFSET_MS
    const date = new Date(watNow)
    let deadline = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate(), hours, minutes) - WAT_OFFSET_MS
    if (deadline <= at) deadline += MAX_REG_MS
    return { ms:deadline - at, description:'until ' + formatTime(deadline) }
  }
  if (values.length !== 1) throw new Error('Use 30m, 2h, or at 20:00 (WAT).')
  const match = /^(\d+)(m|min|mins|h|hr|hrs)$/.exec(values[0])
  if (!match) throw new Error('Use 30m, 2h, or at 20:00 (WAT).')
  const amount = Number(match[1])
  const ms = amount * (match[2].startsWith('h') ? 3600000 : 60000)
  if (!Number.isSafeInteger(ms) || ms < 60000 || ms > MAX_REG_MS) {
    throw new Error('Registration must last between 1 minute and 24 hours.')
  }
  return { ms, description:timeLeft(ms) }
}
const rand = (min, max) => Math.floor(min + Math.random() * (max - min + 1))
const jid = value => String(value || '').trim().replace(/:\d+(?=@)/, '')
const label = value => '@' + jid(value).split('@')[0]
const uniqueText = value => String(value || '').normalize('NFKC').toLowerCase()
  .replace(/[\u200b-\u200d\ufeff]/gu, '').replace(/\p{P}+/gu, ' ')
  .replace(/\s+/gu, ' ').trim()
const livePlayers = game => Object.values(game.players || {}).filter(player => player.alive)
const isAdmin = member => ['admin', 'superadmin'].includes(member?.participant?.admin)
const forwarded = message => {
  const info = contextInfo(message?.message)
  return info?.isForwarded === true || Number(info?.forwardingScore || 0) > 0
}

export function createSquidGame({ storage, accountFor, resolvePhoneJid = async (_account, value) => value, now = () => Date.now(), randomDuration = rand } = {}) {
  const locks = new Map()
  const db = () => storage?.()
  const load = group => db()?.sharedGet(NS, group) || null
  const active = () => db()?.sharedGet(INDEX, 'groups') || []
  function save(group, state) {
    if (!db()) throw new Error('Squid Game storage unavailable.')
    const next = new Set(active())
    if (state) { db().sharedSet(NS, group, state); next.add(group) }
    else { db().sharedDelete(NS, group); next.delete(group) }
    db().sharedSet(INDEX, 'groups', [...next])
  }
  function locked(group, task) {
    const previous = locks.get(group) || Promise.resolve()
    const pending = previous.catch(() => {}).then(task)
    locks.set(group, pending)
    return pending.finally(() => { if (locks.get(group) === pending) locks.delete(group) })
  }
  const account = game => accountFor?.(game.accountId)
  async function send(game, text, mentions = []) {
    const socket = account(game)?.sock
    if (!socket || !account(game)?.connected) throw new Error('Game bot disconnected')
    await socket.sendMessage(game.group, { text, mentions:[...new Set(mentions.filter(Boolean))] })
  }
  async function metadata(game) {
    const socket = account(game)?.sock
    if (!socket || !account(game)?.connected) throw new Error('Game bot disconnected')
    const result = await socket.groupMetadata(game.group)
    if (!result?.participants) throw new Error('Group participants unavailable')
    return result
  }
  async function member(game, candidate, meta = null) {
    if (!candidate) return null
    const result = meta || await metadata(game)
    return findGroupParticipant(result.participants, candidate, id => resolvePhoneJid(account(game), id))
  }
  function initialAdmin(game, row) {
    return participantIds(row?.participant).some(id => game.originalAdmins.includes(id))
  }
  function playerFor(game, row) {
    if (!row) return null
    const ids = participantIds(row.participant)
    return Object.values(game.players).find(p => p.aliases.some(alias => ids.includes(alias))) || null
  }
  function duration(game) {
    const left = livePlayers(game).length
    const ratio = left / Math.max(1, game.initialPlayers || left)
    const pressure = Math.min(7, Math.floor(game.round / 2)) + (ratio < 0.4 ? 2 : 0)
    const ease = game.lastEliminations >= Math.max(2, Math.floor(left / 3)) ? 5 : 0
    return game.phase === 'GREEN'
      ? randomDuration(Math.max(12, 22 - pressure + ease), Math.max(19, 35 - pressure + ease))
      : randomDuration(6, 17)
  }
  async function changeParticipant(game, player, action) {
    const meta = await metadata(game)
    const row = await member(game, player.jid, meta)
    if (!row) return action === 'remove'
    if (initialAdmin(game, row)) return false
    if (action === 'remove' && isAdmin(row)) return false
    if (action === 'demote' && !isAdmin(row)) return true
    const response = await account(game).sock.groupParticipantsUpdate(game.group, [row.jid], action)
    const status = updateSucceeded(response)
    if (status === false) return false
    if (status === true) return true
    const refreshed = await metadata(game)
    const check = await member(game, row.jid, refreshed)
    return action === 'remove' ? !check : action === 'promote' ? isAdmin(check) : !isAdmin(check)
  }
  async function restore(game) {
    if (game.mode === 'kick') {
      let done = true
      for (const player of Object.values(game.players)) {
        if (!player.pendingAction) continue
        try {
          const row = await member(game, player.jid)
          if (!row || isAdmin(row)) {
            // A human promoted this player, so do NOT kick a new admin.
            // Respect manual intervention and do not lock the group forever.
            player.pendingAction = false
            continue
          }
          if (await changeParticipant(game, player, 'remove')) player.pendingAction = false
          else done = false
        } catch { done = false }
      }
      save(game.group, game)
      return done
    }
    if (!game.settingsChanged) return true
    const socket = account(game)?.sock
    if (!socket || !account(game)?.connected) return false
    let done = true
    for (const player of Object.values(game.players)) {
      if (!player.promoted) continue
      try {
        if (!(await changeParticipant(game, player, 'demote'))) done = false
      } catch { done = false }
    }
    try {
      // The original group state is saved before promotions. Restore it even on cancellation.
      await socket.groupSettingUpdate(game.group, game.originalAnnouncement ? 'announcement' : 'not_announcement')
    } catch { done = false }
    return done
  }
  async function complete(game, title) {
    game.phase = 'CLEANUP'
    game.finishedText = title
    game.nextAt = 0
    save(game.group, game)
    try { await send(game, title) } catch {}
    if (await restore(game)) save(game.group, null)
    else save(game.group, game)
  }
  async function eliminate(game, eliminated, reason) {
    const victims = [...new Set(eliminated)].filter(Boolean).filter(player => player.alive)
    if (!victims.length) return
    for (const player of victims) {
      player.alive = false
      // Original group admins may join, but can never be kicked or demoted.
      player.pendingAction = !player.protected
    }
    game.lastEliminations = victims.length
    save(game.group, game)
    const verb = game.mode === 'cg' ? 'demote' : 'remove'
    const failed = []
    for (const player of victims) {
      if (player.protected) continue
      try {
        if (await changeParticipant(game, player, verb)) player.pendingAction = false
        else failed.push(player)
      } catch { failed.push(player) }
    }
    save(game.group, game)
    const others = livePlayers(game)
    const names = victims.map(p => label(p.jid)).join(', ')
    const text = victims.length === 1
      ? `💀 ${names} eliminated! ${reason}\n🦑 ${others.length} remain.`
      : `💀 *${victims.length} PLAYERS ELIMINATED!*\n${names}\n🦑 ${others.length} remain.`
    try { await send(game, text + (failed.length ? '\n⚠️ Removal/demotion retry pending.' : ''), victims.map(p => p.jid)) } catch {}
    if (others.length <= 1) {
      // Two almost-simultaneous RED violations must result in a draw, not
      // a premature winner announcement based only on socket event order.
      if (others.length === 1 && reason === 'Spoke during RED LIGHT.') {
        game.phase = 'FINALIZING'
        game.nextAt = now() + 2500
        save(game.group, game)
        return
      }
      await complete(game, others.length
        ? `🏆 *SQUID GAME CHAMPION*\n👑 ${label(others[0].jid)}\n🦑 Last player standing after ${game.round} rounds!`
        : '🦑 *SQUID GAME OVER*\n💀 All remaining players were eliminated. No winner.')
    }
  }
  async function begin(game) {
    const meta = await metadata(game)
    const waiting = Object.values(game.players)
    game.players = {}
    for (const player of waiting) {
      const row = await member(game, player.jid, meta)
      if (!row) continue
      game.players[row.jid] = { ...player, jid:row.jid, aliases:[...new Set([...player.aliases, ...participantIds(row.participant)])], alive:true, seen:[], roundTexts:[], promoted:false, pendingAction:false, protected:initialAdmin(game, row) }
    }
    if (livePlayers(game).length < 2) return complete(game, `🦑 Squid Game cancelled — ${livePlayers(game).length} of 2 required players registered and still in the group. Start a new session with a longer registration time.`)
    game.initialPlayers = livePlayers(game).length
    if (game.mode === 'cg') {
      if (meta.announce === true) return complete(game, '🦑 CG cancelled — this group was already admin-only.')
      // Save the change marker before any privilege mutation so a restart can undo it.
      game.settingsChanged = true
      save(game.group, game)
      try {
        await account(game).sock.groupSettingUpdate(game.group, 'announcement')
        for (const player of livePlayers(game)) {
          if (player.protected) continue // Existing admins keep their original permissions.
          // Mark ahead of promotion: cleanup will never demote original admins.
          player.promoted = true
          save(game.group, game)
          if (!(await changeParticipant(game, player, 'promote'))) throw new Error('Promotion failed')
        }
      } catch {
        return complete(game, '🦑 CG cancelled — NIGHT could not safely prepare all players. Restoring group settings.')
      }
    }
    game.phase = 'GREEN'
    game.round = 1
    game.lastEliminations = 0
    game.nextAt = now() + duration(game) * 1000
    save(game.group, game)
    await send(game, `🦑 ${game.initialPlayers} players. One survivor.\n🎬 *THE GAME BEGINS!*\n\n🟢 *GREEN LIGHT!*`)
  }
  async function advance(game) {
    if (game.phase === 'REGISTERING') return begin(game)
    if (game.phase === 'GREEN') {
      const meta = await metadata(game)
      if ((game.mode === 'kick' && meta.announce) || (game.mode === 'cg' && !meta.announce)) {
        return complete(game, '🛑 Squid Game cancelled because group messaging permissions changed.')
      }
      game.phase = 'RED'
      game.boundaryAt = now()
      game.graceUntil = now() + GRACE_MS
      game.nextAt = now() + duration(game) * 1000
      save(game.group, game)
      await send(game, '🔴 *RED LIGHT!*')
      return
    }
    if (game.phase !== 'RED') return
    if (game.graceUntil) {
      if (now() < game.graceUntil) return
      game.graceUntil = 0
      const silent = livePlayers(game).filter(p => p.roundTexts.length < 3)
      if (silent.length) await eliminate(game, silent, 'Not enough messages.')
      if (game.phase !== 'RED') return
    }
    if (now() < game.nextAt) return
    game.round += 1
    for (const player of livePlayers(game)) player.roundTexts = []
    game.phase = 'GREEN'
    game.nextAt = now() + duration(game) * 1000
    save(game.group, game)
    const taunts = ['👁️ The doll is watching.', '🎭 Nobody is safe.', '🩸 Still standing?', '🦑 Stay sharp.']
    const flavor = game.round > 2 && Math.random() < 0.24 ? '\n' + taunts[rand(0, taunts.length - 1)] : ''
    await send(game, '🟢 *GREEN LIGHT!*' + flavor)
  }
  async function start(ctx, mode = 'kick', timing = []) {
    const group = ctx.groupKey
    if (!group) return ctx.reply('Squid Game is only available in groups.')
    if (!await ctx.isBotGroupAdmin?.()) return ctx.reply('NIGHT must be an admin to run Squid Game.')
    const window = registrationWindow(timing, now())
    return locked(group, async () => {
      if (load(group)) return ctx.reply('🦑 A Squid Game session is already open. Use .squidgame status.')
      const meta = await ctx.account.sock.groupMetadata(group)
      if (meta.announce) return ctx.reply('🦑 Open group messaging before Squid Game. Players must be able to talk during GREEN LIGHT.')
      const originalAdmins = meta.participants.filter(p => isAdmin({ participant:p })).flatMap(participantIds)
      const game = {
        group, accountId:ctx.account.id, mode, phase:'REGISTERING',
        createdAt:now(), nextAt:now() + window.ms, registrationId:'', players:{},
        originalAdmins:[...new Set(originalAdmins)], originalAnnouncement:Boolean(meta.announce),
        settingsChanged:false, round:0, lastEliminations:0,
      }
      const consequences = mode === 'cg'
        ? '👑 CG mode: survivors become temporary group admins; eliminated players are demoted and silenced. Temporary admins get REAL admin powers.'
        : '🚪 Dedicated group mode: eliminated players are KICKED from this WhatsApp group.'
      const sent = await ctx.account.sock.sendMessage(group, { text:[
        '🦑 *NIGHT — SQUID GAME*',
        '',
        `React ✅ to *this message* to enter. Closes *${formatTime(game.nextAt)}* (${timeLeft(window.ms)}).`,
        '🕘 Admin can begin early with *.squidgame launch*.',
        '',
        '🟢 GREEN: Send at least *3 different, original messages* every round.',
        '🔴 RED: Send *nothing*. Any sent message eliminates you.',
        '❌ Repeated, reused and forwarded messages do not count.',
        '🎲 Light changes are unpredictable. Miss the green quota or talk during red = eliminated.',
        consequences,
        '🛡️ Group admins may play, but cannot be kicked/demoted; elimination only removes them from this game. NIGHT cannot compete.',
        '👥 At least *2 players* must react to begin. You may remove your reaction to withdraw.',
        '',
        'By reacting ✅ you agree to the elimination consequences above.',
      ].join('\n') }, { quoted:ctx.message })
      if (!sent?.key?.id) throw new Error('WhatsApp did not confirm the registration message.')
      game.registrationId = sent.key.id
      save(group, game)
      return null
    })
  }
  async function status(ctx) {
    const game = load(ctx.groupKey)
    if (!game) return ctx.reply('🦑 No active Squid Game.')
    return ctx.reply(game.phase === 'REGISTERING'
      ? '🦑 Registration: ' + Object.keys(game.players).length + ' of 2 minimum registered.'
        + '\n🕘 Starts ' + formatTime(game.nextAt) + ' (' + timeLeft(game.nextAt - now()) + ' remaining).'
        + '\nReact ✅ to the original announcement. Admin: .squidgame launch / extend 30m'
      : '🦑 ' + game.phase + ' • Round ' + game.round + ' • ' + livePlayers(game).length + ' survivors.')
  }
  async function launch(ctx) {
    const group = ctx.groupKey
    return locked(group, async () => {
      const game = load(group)
      if (!game || game.phase !== 'REGISTERING') return ctx.reply('🦑 No registration to launch.')
      if (game.accountId !== ctx.account?.id) return ctx.reply('🦑 Launch this game through the NIGHT account that opened registration.')
      if (Object.keys(game.players).length < 2) return ctx.reply('🦑 Need at least 2 registered players. Currently ' + Object.keys(game.players).length + '.')
      await begin(game)
      return null
    })
  }
  async function extend(ctx, values = []) {
    const group = ctx.groupKey
    const window = registrationWindow(values, now())
    if (!values.length) return ctx.reply('🦑 Usage: .squidgame extend 30m (1m–24h).')
    return locked(group, async () => {
      const game = load(group)
      if (!game || game.phase !== 'REGISTERING') return ctx.reply('🦑 No open registration to extend.')
      if (game.accountId !== ctx.account?.id) return ctx.reply('🦑 Extend this game through the NIGHT account that opened registration.')
      // Extending is relative to the current deadline, not from now, unless overdue.
      const nextAt = Math.max(now(), game.nextAt) + window.ms
      if (nextAt - now() > MAX_REG_MS) return ctx.reply('🦑 Registration cannot be scheduled more than 24 hours ahead.')
      game.nextAt = nextAt
      save(group, game)
      return ctx.reply('🦑 Registration extended. Start: ' + formatTime(game.nextAt)
        + '. ' + Object.keys(game.players).length + ' players registered.')
    })
  }
  async function stop(ctx) {
    const group = ctx.groupKey
    return locked(group, async () => {
      const game = load(group)
      if (!game) return ctx.reply('No active Squid Game.')
      const requester = jid(ctx.message?.key?.participant)
      const meta = await metadata(game)
      const requesterRow = await member(game, requester, meta)
      if (!ctx.isPublicOwner && !ctx.isSupremeOwner && !initialAdmin(game, requesterRow)) {
        return ctx.reply('Only an original group admin can stop Squid Game.')
      }
      await complete(game, '🛑 *SQUID GAME STOPPED*\nThe group has been restored.')
      return null
    })
  }
  async function onReaction(accountValue, event) {
    const group = jid(event?.key?.remoteJid)
    if (!group.endsWith('@g.us')) return
    return locked(group, async () => {
      const game = load(group)
      if (!game || game.phase !== 'REGISTERING' || game.accountId !== accountValue.id || event?.key?.id !== game.registrationId) return
      const reactionKey = event?.reaction?.key || {}
      const from = jid(reactionKey.participant || reactionKey.participantAlt || event?.reaction?.participant || event?.reaction?.senderJid)
      if (!from || reactionKey.fromMe) return
      let meta
      try { meta = await metadata(game) } catch { return }
      const row = await member(game, from, meta)
      if (!row) return
      const id = row.jid
      if (event.reaction.text === '✅') {
        const aliases = participantIds(row.participant)
        const existing = playerFor(game, row)
        if (existing && existing.jid !== id) delete game.players[existing.jid]
        game.players[id] = { jid:id, aliases, alive:true }
      } else {
        // WhatsApp sends an empty reaction when someone unreacts.
        const existing = playerFor(game, row)
        if (existing) delete game.players[existing.jid]
      }
      save(group, game)
    })
  }
  async function onMessage(accountValue, message) {
    const group = jid(message?.key?.remoteJid)
    if (!group.endsWith('@g.us') || message?.key?.fromMe) return
    const contents = normalizedContent(message?.message)
    if (!contents || contents.reactionMessage || contents.protocolMessage || contents.pollUpdateMessage || message?.message?.editedMessage) return
    return locked(group, async () => {
      const game = load(group)
      if (!game || !['GREEN','RED','FINALIZING'].includes(game.phase)) return
      if (game.accountId !== accountValue.id) {
        // In multi-account groups only the hosting NIGHT account adjudicates;
        // the other NIGHT personalities stay silent during play.
        const text = commandText(message.message).trim()
        return !/^[.!/]((squidgame)|(sg))\s+(status|stop|cancel)\b/i.test(text)
      }
      if (game.createdAt < BOOT) return // no eliminations for unobserved offline time
      // Hot path: resolve known participant aliases locally. WhatsApp groupMetadata
      // is a network call and must not run for every Green Light message.
      const rawSender = jid(message?.key?.participant)
      let player = Object.values(game.players).find(p => p.aliases.includes(rawSender))
      if (!player) {
        let row
        try { row = await member(game, rawSender) } catch { return }
        player = playerFor(game, row)
      }
      if (!player?.alive) return
      const id = String(message?.key?.id || '')
      if (!id || player.lastMessageId === id) return true
      player.lastMessageId = id
      const receiptAt = now()
      const rawTimestamp = Number(message.messageTimestamp)
      const sentAt = Number.isFinite(rawTimestamp) && rawTimestamp > 1000000000
        ? rawTimestamp * 1000 : receiptAt
      if (game.phase === 'RED' || game.phase === 'FINALIZING') {
        // Avoid punishing a green-light send that arrived around the switch.
        if (receiptAt <= game.graceUntil) {
          if (sentAt <= game.boundaryAt && !forwarded(message)) {
            const value = uniqueText(commandText(message.message))
            if (value && !player.seen.includes(value)) {
              player.seen.push(value)
              player.roundTexts.push(value)
            }
          }
          save(group, game)
          return true
        }
        await eliminate(game, [player], 'Spoke during RED LIGHT.')
        return true
      }
      const value = uniqueText(commandText(message.message))
      if (!value || forwarded(message) || player.seen.includes(value)) return true
      player.seen.push(value)
      player.roundTexts.push(value)
      save(group, game)
      return true
    })
  }
  async function tick() {
    for (const group of active()) {
      await locked(group, async () => {
        const game = load(group)
        if (!game) { save(group, null); return }
        if (!account(game)?.connected) return
        try {
          if (game.phase === 'CLEANUP') {
            if (await restore(game)) save(group, null)
            return
          }
          if (game.createdAt < BOOT && game.phase !== 'REGISTERING') {
            await complete(game, '🛑 Squid Game cancelled after NIGHT restarted. Restoring original settings.')
            return
          }
          if (now() - (game.nextAt || now()) > (game.phase === 'REGISTERING' ? 120000 : 15000)) {
            await complete(game, '🛑 Squid Game cancelled after a connection interruption.')
            return
          }
          if (game.phase === 'FINALIZING' && now() >= game.nextAt) {
            const survivor = livePlayers(game)[0]
            await complete(game, survivor
              ? `🏆 *SQUID GAME CHAMPION*\n👑 ${label(survivor.jid)}\n🦑 Last player standing after ${game.round} rounds!`
              : '🦑 *SQUID GAME OVER*\n💀 No survivors. No winner.')
            return
          }
          if (game.phase === 'RED' && game.graceUntil && now() >= game.graceUntil) {
            await advance(game)
          }
          if (load(group)?.phase === 'CLEANUP') return
          const current = load(group)
          if (current && now() >= current.nextAt && (!current.graceUntil || now() >= current.graceUntil)) {
            await advance(current)
          }
        } catch (error) {
          console.error('Squid Game tick failed:', error?.message || error)
        }
      })
    }
  }
  return { start, status, stop, launch, extend, onReaction, onMessage, tick, get: load }
}
