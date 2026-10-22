// A music picker is a short-lived command interaction, not an invitation for
// a language model to invent an audio file or repeat vague questions.
export function musicFollowupReply(text, session, { prefix = '.' } = {}) {
  if (session?.kind !== 'number-selection' || session?.command !== 'song') return ''
  const input = String(text || '').trim().toLowerCase()
  if (!input || input.length > 160) return ''
  if (/^(?:where(?:\s+is|'s)\s+(?:my\s+|the\s+)?(?:song|music|audio)|(?:i\s+)?(?:didn'?t|did not|haven'?t|have not)\s+(?:get|receive)\s+(?:my\s+|the\s+)?(?:song|music|audio)|no\s+(?:song|music|audio))(?:[?!\.]+)?$/i.test(input)) {
    return 'I cannot confirm the audio arrived on your phone. Reply with the same song number to retry the send. If it still fails, try a different result.'
  }
  if (/^(?:the\s+)?(?:original|official|original\s+version|official\s+version)(?:[?!\.]+)?$/i.test(input)) {
    return 'For the original recording, pick an official artist upload from the numbered results, or search by title and singer with ' + prefix + 'song.'
  }
  if (/\bfleshlight\b.*\bpitch\s+perfect\s*2\b/i.test(input)) {
    return 'The song is Flashlight by Jessie J (Pitch Perfect 2). Search with ' + prefix + 'song Flashlight Jessie J Pitch Perfect 2.'
  }
  return ''
}
