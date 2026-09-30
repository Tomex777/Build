import { normalizeMessageContent, proto } from '@itsliaaa/baileys'

const VIEW_ONCE_MEDIA_KEYS = ['imageMessage', 'videoMessage', 'audioMessage']
const MESSAGE_WRAPPERS = [
  'viewOnceMessage',
  'viewOnceMessageV2',
  'viewOnceMessageV2Extension',
  'ephemeralMessage',
  'documentWithCaptionMessage',
  'editedMessage',
  'associatedChildMessage',
]

export const normalizedContent = message => normalizeMessageContent(message) || message || {}

export function nativeFlowSelection(value) {
  if (!value || typeof value !== 'object') return ''

  const keys = [
    'id',
    'selectedId',
    'selected_id',
    'rowId',
    'row_id',
    'selectedRowId',
    'selected_row_id',
    'responseId',
    'response_id',
  ]

  for (const key of keys) {
    if (typeof value[key] === 'string' && value[key].trim()) return value[key].trim()
  }

  for (const child of Object.values(value)) {
    if (child && typeof child === 'object') {
      const nested = nativeFlowSelection(child)
      if (nested) return nested
    }
  }

  return ''
}

export function commandText(message) {
  const content = normalizedContent(message)

  const direct = String(
    content?.conversation ||
    content?.extendedTextMessage?.text ||
    content?.imageMessage?.caption ||
    content?.videoMessage?.caption ||
    content?.documentMessage?.caption ||
    content?.listResponseMessage?.singleSelectReply?.selectedRowId ||
    content?.buttonsResponseMessage?.selectedButtonId ||
    content?.templateButtonReplyMessage?.selectedId ||
    ''
  ).trim()

  if (direct) return direct

  const params = content?.interactiveResponseMessage?.nativeFlowResponseMessage?.paramsJson
  if (!params) return ''

  try {
    return nativeFlowSelection(JSON.parse(params))
  } catch {
    return ''
  }
}

export function contextInfo(message) {
  const content = normalizedContent(message)
  for (const value of Object.values(content || {})) {
    if (value && typeof value === 'object' && value.contextInfo) return value.contextInfo
  }
  return null
}

export function quotedMessage(msg, context, chat) {
  if (!context?.stanzaId || !context?.quotedMessage) return null

  return {
    key: {
      remoteJid: context.remoteJid || chat || msg?.key?.remoteJid,
      id: context.stanzaId,
      participant: context.participant || undefined,
      fromMe: false,
    },
    message: context.quotedMessage,
  }
}

export function findViewOnceMedia(message) {
  let current = message

  for (let i = 0; i < 8 && current; i++) {
    for (const key of VIEW_ONCE_MEDIA_KEYS) {
      const media = current?.[key]
      if (media?.viewOnce === true) return { key, media }
    }

    let next = null
    for (const wrapper of MESSAGE_WRAPPERS) {
      if (!current?.[wrapper]?.message) continue

      next = current[wrapper].message

      if (wrapper.startsWith('viewOnceMessage')) {
        for (const key of VIEW_ONCE_MEDIA_KEYS) {
          const media = next?.[key]
          if (media) return { key, media: { ...media, viewOnce: true } }
        }
      }

      break
    }

    if (!next) break
    current = next
  }

  const content = normalizedContent(message)
  for (const key of VIEW_ONCE_MEDIA_KEYS) {
    if (content?.[key]?.viewOnce === true) return { key, media: content[key] }
  }

  return null
}

export function unlockViewOnce(source) {
  const found = findViewOnceMedia(source?.message)
  if (!found) return null

  return {
    ...source,
    key: { ...source.key },
    message: {
      [found.key]: {
        ...found.media,
        viewOnce: false,
      },
    },
  }
}

export const encodeMessage = msg =>
  Buffer.from(proto.WebMessageInfo.encode(msg).finish()).toString('base64')

export const decodeMessage = data =>
  proto.WebMessageInfo.decode(Buffer.from(data, 'base64'))

export function messageMedia(message) {
  const content = normalizedContent(message)

  for (const key of ['imageMessage', 'videoMessage', 'audioMessage', 'documentMessage', 'stickerMessage']) {
    if (content?.[key]) return { key, media: content[key] }
  }

  return null
}
