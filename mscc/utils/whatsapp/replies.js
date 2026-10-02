import { chunkText } from '../text/formatting.js'

const sendOptions = quoted => quoted ? { quoted } : {}

export async function sendText(sock, chat, value, { quoted = null, mentions = [] } = {}) {
  if (!sock || !chat) throw new Error('Reply target is unavailable')
  const chunks = chunkText(String(value ?? ''))
  const mentionJids = [...new Set(
    (Array.isArray(mentions) ? mentions : [])
      .map(jid => String(jid || '').trim())
      .filter(Boolean)
  )]
  let sent = null

  for (const text of chunks) {
    const chunkMentions = mentionJids.filter(jid => {
      const user = String(jid).split('@')[0].split(':')[0]
      return user && text.includes('@' + user)
    })
    sent = await sock.sendMessage(
      chat,
      chunkMentions.length ? { text, mentions:chunkMentions } : { text },
      sendOptions(quoted),
    )
  }

  return sent
}

export async function sendImageDataUrl(sock, chat, dataUrl, caption = '', { quoted = null } = {}) {
  if (!sock || !chat) throw new Error('Image target is unavailable')

  const match = String(dataUrl || '').match(/^data:image\/[a-zA-Z0-9.+-]+;base64,(.+)$/)
  if (!match) throw new Error('Image data URL is invalid')

  return sock.sendMessage(chat, {
    image: Buffer.from(match[1], 'base64'),
    caption: String(caption || ''),
  }, sendOptions(quoted))
}

export async function editText(sock, chat, key, value) {
  if (!sock || !chat || !key) throw new Error('Editable message is unavailable')
  return sock.sendMessage(chat, {
    text: String(value ?? ''),
    edit: key,
  })
}

export async function startProgress(sock, chat, initialText, { quoted = null } = {}) {
  let current = await sendText(sock, chat, initialText, { quoted })
  let key = current?.key || null

  const update = async value => {
    const text = String(value ?? '')

    if (key) {
      try {
        return await editText(sock, chat, key, text)
      } catch {}
    }

    current = await sendText(sock, chat, text, { quoted })
    key = current?.key || key
    return current
  }

  return {
    get key() {
      return key
    },
    update,
    done: update,
    fail: update,
  }
}
