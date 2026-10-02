import {
  NATIVE_FLOW_MAX_ROWS,
  sendNativeFlowSelectors,
  sendSingleSelect,
} from './native-flow.js'

function actionRow(action = {}, fallbackTitle = 'Choose') {
  const title = String(action.title || action.label || fallbackTitle).trim()
  const description = String(action.description || action.subtitle || '').trim()
  const id = String(action.id || action.command || '').trim()
  if (!title || !id) return null
  return { title, description, id }
}

function rowsFromActions(actions = []) {
  return (Array.isArray(actions) ? actions : [])
    .map(action => actionRow(action))
    .filter(Boolean)
}

function clampPage(value, pageCount) {
  const n = Math.floor(Number(value) || 1)
  return Math.max(1, Math.min(Math.max(1, pageCount), n))
}

export function createWhatsAppUi({
  sock,
  chat,
  quoted = null,
  prefix = '.',
} = {}) {
  if (!sock || !chat) throw new Error('WhatsApp UI target is unavailable')

  const base = {
    sock,
    chat,
    quoted,
  }

  const bottomSheet = options => sendSingleSelect({
    ...base,
    ...(options || {}),
  })

  const menu = options => bottomSheet(options)

  const quickActions = ({
    actions = [],
    rows = [],
    buttonText = 'Actions',
    ...rest
  } = {}) => bottomSheet({
    ...rest,
    buttonText,
    rows:rows.length ? rows : rowsFromActions(actions),
  })

  const actionPair = ({
    primary,
    secondary,
    buttonText = 'Choose',
    rows = [],
    ...rest
  } = {}) => {
    const pairRows = rows.length
      ? rows
      : [actionRow(primary), actionRow(secondary)].filter(Boolean)
    if (pairRows.length < 2) throw new Error('Action pair requires two choices')
    return bottomSheet({
      ...rest,
      buttonText,
      rows:pairRows,
    })
  }

  const confirmCancel = ({
    confirmId,
    cancelId,
    confirmText = '✓ Confirm',
    cancelText = '✕ Cancel',
    confirmDescription = '',
    cancelDescription = '',
    ...rest
  } = {}) => actionPair({
    ...rest,
    buttonText:rest.buttonText || 'Confirm',
    primary:{
      title:confirmText,
      description:confirmDescription,
      id:confirmId,
    },
    secondary:{
      title:cancelText,
      description:cancelDescription,
      id:cancelId,
    },
  })

  const acceptDecline = ({
    acceptId,
    declineId,
    acceptText = '✓ Accept',
    declineText = '✕ Decline',
    acceptDescription = '',
    declineDescription = '',
    ...rest
  } = {}) => actionPair({
    ...rest,
    buttonText:rest.buttonText || 'Respond',
    primary:{
      title:acceptText,
      description:acceptDescription,
      id:acceptId,
    },
    secondary:{
      title:declineText,
      description:declineDescription,
      id:declineId,
    },
  })

  const joinCancel = ({
    joinId,
    cancelId,
    joinText = '♟️ Join',
    cancelText = '✕ Cancel',
    joinDescription = '',
    cancelDescription = '',
    ...rest
  } = {}) => actionPair({
    ...rest,
    buttonText:rest.buttonText || 'Choose',
    primary:{
      title:joinText,
      description:joinDescription,
      id:joinId,
    },
    secondary:{
      title:cancelText,
      description:cancelDescription,
      id:cancelId,
    },
  })

  const pagedPicker = ({
    rows = [],
    items = [],
    page = 1,
    pageSize = 20,
    previousId = '',
    nextId = '',
    previousText = '← Previous',
    nextText = 'Next →',
    ...rest
  } = {}) => {
    const sourceRows = rows.length ? rows : rowsFromActions(items)
    const safePageSize = Math.max(1, Math.min(100, Number(pageSize) || 20))
    const pageCount = Math.max(1, Math.ceil(sourceRows.length / safePageSize))
    const currentPage = clampPage(page, pageCount)
    const start = (currentPage - 1) * safePageSize
    const pageRows = sourceRows.slice(start, start + safePageSize)

    if (currentPage > 1 && previousId) {
      pageRows.push({ title:previousText, description:`Page ${currentPage - 1} of ${pageCount}`, id:previousId })
    }
    if (currentPage < pageCount && nextId) {
      pageRows.push({ title:nextText, description:`Page ${currentPage + 1} of ${pageCount}`, id:nextId })
    }

    return bottomSheet({
      ...rest,
      footer:rest.footer || (pageCount > 1 ? `Page ${currentPage} of ${pageCount}` : ''),
      rows:pageRows,
    })
  }

  return Object.freeze({
    prefix:String(prefix || '.'),
    bottomSheet,
    singleSelect:bottomSheet,
    menu,
    quickActions,
    actionPair,
    confirmCancel,
    acceptDecline,
    joinCancel,
    pagedPicker,
    native:options => sendNativeFlowSelectors({
      ...base,
      ...(options || {}),
    }),
  })
}

export const WHATSAPP_UI_MAX_ROWS = NATIVE_FLOW_MAX_ROWS
