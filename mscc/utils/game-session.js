export const GAME_SESSION_NAMESPACES = Object.freeze([
  { namespace:'chess-game', label:'Chess' },
  { namespace:'tictactoe-game', label:'Tic-Tac-Toe' },
  { namespace:'checkers-game', label:'Checkers' },
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
