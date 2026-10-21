// A WhatsApp image/sticker/voice note without a caption is not a new text
// question. A quoted bot response may activate the personality, but it must
// never turn a blank attachment into a synthetic "respond naturally" prompt.
export function assistantInputPolicy({ text = '', mediaType = '' } = {}) {
  const value = String(text || '').trim()
  if (!value) return { respond:false, text:'', attachmentType:'' }
  // Captions contain usable text; pass only the declared media kind to the
  // text-only provider. It cannot see image pixels, stickers or hear audio.
  const attachmentType = String(mediaType || '').trim().slice(0, 32)
  return { respond:true, text:value, attachmentType }
}
