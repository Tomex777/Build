export const GAME_SESSION_NAMESPACES = Object.freeze([
  { namespace:'chess-game', label:'Chess' },
  { namespace:'tictactoe-game', label:'Tic-Tac-Toe' },
  { namespace:'checkers-game', label:'Checkers' },
  { namespace:'ludo-game', label:'Ludo' },
])

export function findOtherActiveGame(ctx, currentNamespace) {
  const chat = String(ctx?.message?.key?.remoteJid || '').trim()
  if (!chat || !ctx?.shared?.get) return null
  const now = Date.now()

  for (const item of GAME_SESSION_NAMESPACES) {
    if (item.namespace === currentNamespace) continue
    const record = ctx.shared.get(item.namespace, chat)
    if (!record) continue

    const expiresAt = Number(record.expiresAt || 0)
    if (expiresAt && expiresAt <= now) {
      ctx.shared?.delete?.(item.namespace, chat)
      continue
    }

    if (record.state === 'WAITING' || record.state === 'PLAYING') {
      return { ...item, record }
    }
  }

  return null
}

export function otherGameMessage(conflict) {
  return conflict
    ? `There is already a ${conflict.label} game or challenge active in this chat. Finish or cancel it first.`
    : ''
}


// Keep an existing game/lobby attached to its original verified player key
// when a participant transitions between LID and PN during the game.
export function userForStoredGame(ctx = {}, record = null) {
  const current = String(ctx.userKey || '')
  if (!record || !current) return current
  const known = new Set([current, ...(Array.isArray(ctx.identityKeys) ? ctx.identityKeys : [])])
  const participants = [
    record.createdBy,
    record.game?.playerWhite, record.game?.playerBlack,
    record.game?.playerX, record.game?.playerO, record.game?.playerRed,
    ...(Array.isArray(record.game?.players) ? record.game.players.map(player => player?.id) : []),
    ...(Array.isArray(record.players) ? record.players.map(player => player?.id) : []),
  ].map(value => String(value || '')).filter(Boolean)
  return participants.find(key => known.has(key)) || current
}
