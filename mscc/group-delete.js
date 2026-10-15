/**
 * Nexia-style reply deletion: .delete on a quoted group message revokes the
 * quoted message first, then removes the command without a success reply.
 * Keep this separate from count-based cleanup so each mode can be tested.
 */
export async function deleteQuotedGroupMessage({
  chat,
  commandKey,
  quote,
  cached,
  accountId,
  isOwnParticipant = async () => false,
  sendDelete,
  suppressDelete = () => {},
} = {}) {
  const quotedId = String(quote?.stanzaId || '').trim()
  if (!quotedId) return { deleted:0, error:'🗑️ Reply to a message or use .delete <count>.' }
  if (!chat || !commandKey?.id || typeof sendDelete !== 'function') {
    return { deleted:0, error:'I could not identify the message to delete.' }
  }
  // A forwarded/foreign quote must not result in deletion in this group.
  if (quote?.remoteJid && String(quote.remoteJid).endsWith('@g.us') && quote.remoteJid !== chat) {
    return { deleted:0, error:'Reply to a message from this group.' }
  }

  const cachedKey = cached?.message?.key
  const participant = String(quote.participant || cachedKey?.participant || '').trim()
  const ownMessage = (cached?.accountId === accountId && cachedKey?.fromMe === true)
    || (participant ? Boolean(await isOwnParticipant(participant)) : false)
  if (!participant && !ownMessage) {
    return { deleted:0, error:'I could not identify the quoted message sender. Try replying again.' }
  }

  const quotedKey = {
    remoteJid:chat,
    id:quotedId,
    fromMe:Boolean(ownMessage),
    ...(participant ? { participant } : {}),
  }
  try {
    suppressDelete(chat, quotedKey.id)
    await sendDelete(quotedKey)
  } catch (error) {
    return { deleted:0, error:'I could not delete that message.', cause:error }
  }

  // Nexia removes the command only after the quoted message was deleted.
  // Do not claim the quoted message failed if only command cleanup fails.
  try {
    suppressDelete(chat, commandKey.id)
    await sendDelete({ ...commandKey, remoteJid:chat })
    return { deleted:1, commandDeleted:true }
  } catch (error) {
    return { deleted:1, commandDeleted:false, cause:error }
  }
}
