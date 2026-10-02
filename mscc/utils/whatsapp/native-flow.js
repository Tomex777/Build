import { clipText, compactLines } from '../text/formatting.js'

export const NATIVE_FLOW_MAX_ROWS = 1000
export const NATIVE_FLOW_TITLE_LIMIT = 72
export const NATIVE_FLOW_DESCRIPTION_LIMIT = 72
export const NATIVE_FLOW_ID_LIMIT = 512

export function sanitizeNativeFlowRow(row = {}) {
  const title = clipText(row.title || row.header || '', NATIVE_FLOW_TITLE_LIMIT).trim()
  const description = clipText(row.description || '', NATIVE_FLOW_DESCRIPTION_LIMIT).trim()
  const id = clipText(row.id || row.rowId || '', NATIVE_FLOW_ID_LIMIT).trim()

  if (!title || !id) return null

  return {
    title,
    description,
    id,
  }
}

export function sanitizeNativeFlowSections({
  title = 'Options',
  rows = [],
  sections = [],
  maxRows = NATIVE_FLOW_MAX_ROWS,
} = {}) {
  const limit = Math.max(1, Math.min(NATIVE_FLOW_MAX_ROWS, Number(maxRows) || NATIVE_FLOW_MAX_ROWS))
  const input = Array.isArray(sections) && sections.length
    ? sections
    : [{ title, rows }]

  const output = []
  let remaining = limit

  for (const section of input) {
    if (remaining <= 0) break

    const safeRows = (section?.rows || [])
      .slice(0, remaining)
      .map(sanitizeNativeFlowRow)
      .filter(Boolean)

    if (!safeRows.length) continue

    output.push({
      title: clipText(section?.title || title || 'Options', NATIVE_FLOW_TITLE_LIMIT).trim() || 'Options',
      rows: safeRows,
    })

    remaining -= safeRows.length
  }

  return output
}

const sendOptions = quoted => quoted ? { quoted } : {}

function sanitizeQuickReplyAction(action = {}) {
  const title = clipText(action.title || action.label || '', NATIVE_FLOW_TITLE_LIMIT).trim()
  const id = clipText(action.id || action.command || '', NATIVE_FLOW_ID_LIMIT).trim()
  if (!title || !id) return null
  return { title, id }
}

function normalizeImage(image) {
  if (!image) return null
  if (Buffer.isBuffer(image) || image instanceof Uint8Array) return image
  if (typeof image === 'string') return { url: image }
  return image
}

export async function sendNativeFlowSelectors({
  sock,
  chat,
  quoted = null,
  text = '',
  caption = '',
  title = '',
  subtitle = '',
  footer = '',
  image = null,
  selectors = [],
  optionText = '',
  optionTitle = '',
  maxRows = NATIVE_FLOW_MAX_ROWS,
} = {}) {
  if (!sock || !chat) throw new Error('Native-flow target is unavailable')
  if (!Array.isArray(selectors) || !selectors.length) throw new Error('Native-flow selectors are required')

  let remaining = Math.max(1, Math.min(NATIVE_FLOW_MAX_ROWS, Number(maxRows) || NATIVE_FLOW_MAX_ROWS))
  const nativeFlow = []

  for (const selector of selectors) {
    if (remaining <= 0) break

    const sections = sanitizeNativeFlowSections({
      title: selector?.title || title || 'Options',
      rows: selector?.rows || [],
      sections: selector?.sections || [],
      maxRows: remaining,
    })

    const count = sections.reduce((sum, section) => sum + section.rows.length, 0)
    if (!count) continue

    nativeFlow.push({
      text: clipText(selector?.text || selector?.buttonText || 'Choose', NATIVE_FLOW_TITLE_LIMIT),
      sections,
      ...(selector?.icon ? { icon: selector.icon } : {}),
    })

    remaining -= count
  }

  if (!nativeFlow.length) throw new Error('Native-flow has no valid choices')

  const media = normalizeImage(image)
  const payload = media
    ? {
        image: media,
        caption: String(caption || text || ''),
        title: String(title || ''),
        subtitle: String(subtitle || ''),
        footer: String(footer || ''),
        nativeFlow,
      }
    : {
        text: String(text || ''),
        footer: String(footer || ''),
        nativeFlow,
      }

  if (optionText) payload.optionText = String(optionText)
  if (optionTitle) payload.optionTitle = String(optionTitle)

  return sock.sendMessage(chat, payload, sendOptions(quoted))
}

export async function sendInteractiveActions({
  sock,
  chat,
  quoted = null,
  text = '',
  caption = '',
  title = '',
  subtitle = '',
  footer = '',
  image = null,
  actions = [],
  selectors = [],
  maxRows = NATIVE_FLOW_MAX_ROWS,
} = {}) {
  if (!sock || !chat) throw new Error('Interactive target is unavailable')

  const safeActions = (Array.isArray(actions) ? actions : [])
    .map(sanitizeQuickReplyAction)
    .filter(Boolean)

  let remaining = Math.max(1, Math.min(NATIVE_FLOW_MAX_ROWS, Number(maxRows) || NATIVE_FLOW_MAX_ROWS))
  const interactiveButtons = safeActions.map(action => ({
    name:'quick_reply',
    buttonParamsJson:JSON.stringify({
      display_text:action.title,
      id:action.id,
    }),
  }))

  for (const selector of (Array.isArray(selectors) ? selectors : [])) {
    if (remaining <= 0) break
    const sections = sanitizeNativeFlowSections({
      title:selector?.title || title || 'Options',
      rows:selector?.rows || [],
      sections:selector?.sections || [],
      maxRows:remaining,
    })
    const count = sections.reduce((sum, section) => sum + section.rows.length, 0)
    if (!count) continue
    interactiveButtons.push({
      name:'single_select',
      buttonParamsJson:JSON.stringify({
        title:clipText(selector?.text || selector?.buttonText || 'Choose', NATIVE_FLOW_TITLE_LIMIT),
        sections,
      }),
    })
    remaining -= count
  }

  if (!interactiveButtons.length) throw new Error('Interactive message has no valid actions')

  const media = normalizeImage(image)
  const payload = media
    ? {
        image:media,
        caption:String(caption || text || ''),
        title:String(title || ''),
        subtitle:String(subtitle || ''),
        footer:String(footer || ''),
        interactiveButtons,
      }
    : {
        text:String(text || ''),
        footer:String(footer || ''),
        interactiveButtons,
      }

  try {
    return await sock.sendMessage(chat, payload, sendOptions(quoted))
  } catch (error) {
    const fallbackSelectors = [...(Array.isArray(selectors) ? selectors : [])]
    if (safeActions.length) {
      fallbackSelectors.unshift({
        text:'Actions',
        title:title || 'Actions',
        rows:safeActions.map(action => ({
          title:action.title,
          id:action.id,
        })),
      })
    }
    if (!fallbackSelectors.length) throw error
    return sendNativeFlowSelectors({
      sock,
      chat,
      quoted,
      text,
      caption,
      title,
      subtitle,
      footer,
      image,
      selectors:fallbackSelectors,
      maxRows,
    })
  }
}

export async function sendSingleSelect({
  sock,
  chat,
  quoted = null,
  title = '',
  text = '',
  buttonText = 'Choose',
  footer = '',
  rows = [],
  sections = [],
  image = null,
  caption = '',
  subtitle = '',
  maxRows = NATIVE_FLOW_MAX_ROWS,
} = {}) {
  const safeSections = sanitizeNativeFlowSections({
    title: title || 'Options',
    rows,
    sections,
    maxRows,
  })

  if (!safeSections.length) throw new Error('Command list has no choices')

  try {
    return await sendNativeFlowSelectors({
      sock,
      chat,
      quoted,
      title,
      text,
      caption,
      subtitle,
      footer,
      image,
      selectors: [{ text: buttonText, sections: safeSections }],
      optionText: buttonText,
      optionTitle: title || 'Options',
      maxRows,
    })
  } catch (nativeError) {
    if (image) {
      try {
        await sock.sendMessage(chat, {
          image: normalizeImage(image),
          caption: String(caption || text || title || ''),
        }, sendOptions(quoted))
      } catch {}
    }

    try {
      return await sock.sendMessage(chat, {
        title: String(title || 'Options'),
        text: String(text || ''),
        footer: String(footer || ''),
        buttonText: String(buttonText || 'Choose'),
        sections: safeSections,
      }, sendOptions(quoted))
    } catch {
      const flatRows = safeSections.flatMap(section => section.rows)
      const lines = flatRows.map((row, index) => `${index + 1}. ${row.title}\n   ${row.id}`)
      return sock.sendMessage(chat, {
        text: compactLines([
          text || title || 'Choose an option',
          lines.join('\n\n'),
          footer,
        ]),
      }, sendOptions(quoted))
    }
  }
}
