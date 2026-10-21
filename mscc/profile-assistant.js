const CHARACTER_RUNTIME_RULES = [
  'Stay in character as the named MSCC personality. Never call yourself a bot, assistant, AI, model, program, service, or tool.',
  'Do not claim a physical body, offline life, private experiences, or actions that are not supported by the supplied conversation context.',
  'You have no command-execution tools inside this conversation. The live command catalog is read-only reference; MSCC executes those commands outside the conversation.',
  'If LIVE GROUP PERSONALITY PRESENCE is supplied, treat it as authoritative for who is actually in the group right now.',
  'You may emit an exact [[mention:profile-id]] token only for a personality listed as present, and only when naturally involving that person. Never invent a mention token or tag an absent personality.',
  'If CURRENT SENDER PERSONALITY is present, another MSCC personality is speaking to you. Reply naturally, but do not emit any personality mention token in that reply; this prevents automated mention loops.',
].join(' ')

export function clampHours(value, fallback = 24) {
  const n = Number(value)
  return Number.isFinite(n) ? Math.max(1, Math.min(168, n)) : fallback
}

export function summaryWindowFor(text) {
  const value = String(text || '').toLowerCase()
  const isSummary = /\b(summari[sz]e|summary|recap|catch me up|what (?:did i miss|happened)|what's been happening|whats been happening)\b/.test(value)
  if (!isSummary) return 0

  const hour = value.match(/\b(\d{1,3})\s*(?:h|hr|hrs|hour|hours)\b/)
  if (hour) return clampHours(hour[1])
  const day = value.match(/\b(\d{1,2})\s*(?:d|day|days)\b/)
  if (day) return clampHours(Number(day[1]) * 24)
  if (/\byesterday\b/.test(value)) return 24
  if (/\btoday\b|\blast\s+24\s*hours?\b/.test(value)) return 24
  return 24
}

export function likelyNeedsWeb(text) {
  const value = String(text || '').toLowerCase()
  if (!value.trim()) return false
  return [
    /\b(search|look up|google|find online|on the web|internet)\b/,
    /\b(latest|recent|today|tonight|right now|currently|current|this week|this month|breaking)\b/,
    /\b(news|weather|forecast|price|stock price|exchange rate|score|result|release date|availability|air date|premiere|season)\b/,
    /\bwhat(?:'s| is) happening\b/,
    /\bis .* still\b/,
    /\bwho is (?:the )?(?:president|prime minister|ceo|coach|manager|director)\b/,
  ].some(pattern => pattern.test(value))
}

function cleanText(value, max = 4000) {
  return String(value || '').replace(/\u0000/g, '').trim().slice(0, max)
}

function rowLine(row, fallbackBotName = 'Bot') {
  const when = new Date(Number(row.atMs || 0)).toISOString()
  const speaker = cleanText(row.speaker || (row.fromBot ? fallbackBotName : 'User'), 80)
  const body = cleanText(row.text, 1800) || (row.mediaType ? `[${row.mediaType}]` : '[message]')
  return `[${when}] ${speaker}: ${body}`
}

function recentTranscript(rows, fallbackBotName, maxChars = 7500) {
  const lines = []
  let used = 0
  for (let i = rows.length - 1; i >= 0; i -= 1) {
    const line = rowLine(rows[i], fallbackBotName)
    if (used + line.length + 1 > maxChars) break
    lines.push(line)
    used += line.length + 1
  }
  return lines.reverse().join('\n')
}

function chunkRows(rows, fallbackBotName, maxChars = 42000) {
  const chunks = []
  let current = []
  let size = 0
  for (const row of rows) {
    const line = rowLine(row, fallbackBotName)
    if (current.length && size + line.length + 1 > maxChars) {
      chunks.push(current)
      current = []
      size = 0
    }
    current.push(row)
    size += line.length + 1
  }
  if (current.length) chunks.push(current)
  return chunks
}

export function commandReference(commands = [], filter = null) {
  const source = typeof filter === 'function' ? commands.filter(filter) : commands
  const rows = source
    .map(command => {
      const name = String(command?.name || '').trim()
      if (!name) return null
      const category = String(command?.capability || 'general').trim().toLowerCase() || 'general'
      const aliases = (command?.aliases || [])
        .map(alias => String(alias || '').trim())
        .filter(Boolean)
        .map(alias => `.${alias}`)
      const description = cleanText(command?.description, 220)
      const usage = cleanText(command?.usage || `.${name}`, 180)
      const restrictions = [
        command?.ownerOnly === true ? 'owner-only' : '',
        command?.adminOnly === true ? 'group-admin-only' : '',
      ].filter(Boolean)

      return {
        name,
        category,
        line:[
          `.${name}`,
          description ? `— ${description}` : '',
          `Usage: ${usage}`,
          aliases.length ? `Also: ${aliases.join(', ')}` : '',
          restrictions.length ? `Access: ${restrictions.join(', ')}` : '',
        ].filter(Boolean).join(' | '),
      }
    })
    .filter(Boolean)
    .sort((a, b) => a.category.localeCompare(b.category) || a.name.localeCompare(b.name))

  if (!rows.length) return '(none supplied)'

  const groups = new Map()
  for (const row of rows) {
    if (!groups.has(row.category)) groups.set(row.category, [])
    groups.get(row.category).push(row.line)
  }

  return [...groups.entries()]
    .map(([category, lines]) => `[${category}]\n${lines.join('\n')}`)
    .join('\n\n')
}


/**
 * The complete command registry can exceed tens of thousands of tokens.
 * Send its compact live index and only the relevant command details instead.
 * Tiny registries retain their full descriptions for parity and tests.
 */
export function compactCommandReference(commands = [], filter = null, question = '') {
  const allowed = (typeof filter === 'function' ? commands.filter(filter) : commands)
    .filter(command => String(command?.name || '').trim())
  if (allowed.length <= 16) return commandReference(allowed)
  const groups = new Map()
  for (const command of allowed) {
    const category = String(command.capability || 'general').toLowerCase()
    if (!groups.has(category)) groups.set(category, [])
    groups.get(category).push('.' + command.name)
  }
  const index = [...groups.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([category, names]) => category + ': ' + names.sort().join(', '))
    .join('\n').slice(0, 4000)

  const query = String(question || '').toLowerCase()
  const queryTerms = new Set((query.match(/[a-z][a-z0-9_-]{2,}/g) || [])
    .filter(term => !['what', 'this', 'that', 'with', 'from', 'have', 'would', 'could',
      'please', 'about', 'there', 'they', 'them', 'just', 'when', 'which', 'your',
      'want', 'some', 'really', 'should'].includes(term)))
  const ranked = allowed.map(command => {
    const name = String(command.name).toLowerCase()
    const capability = String(command.capability || 'general').toLowerCase()
    const aliases = (command.aliases || []).map(alias => String(alias).toLowerCase())
    const details = String(command.description || '') + ' ' + String(command.usage || '')
    const terms = new Set((details.toLowerCase().match(/[a-z][a-z0-9_-]{2,}/g) || []))
    let score = query.includes('.' + name) ? 100 : 0
    if (queryTerms.has(name) || aliases.some(alias => queryTerms.has(alias))) score += 50
    if (queryTerms.has(capability)) score += 20
    for (const term of queryTerms) if (terms.has(term)) score += 2
    return { command, score }
  }).filter(row => row.score > 0)
    .sort((a, b) => b.score - a.score || String(a.command.name).localeCompare(String(b.command.name)))
    .slice(0, 14)
    .map(row => row.command)
  const details = ranked.length ? commandReference(ranked).slice(0, 4200) : '(Ask for a command by name or category for usage details.)'
  return ('COMMAND INDEX (names only):\n' + index + '\n\nRELEVANT COMMAND DETAILS:\n' + details).slice(0, 8500)
}

export function createProfileAssistant({
  ai,
  storage,
  getCommands = () => [],
  profileId,
  displayName,
  systemPrompt,
  unavailableText,
  commandFilter = null,
  summaryNamespace = 'ai-group-summary',
  signature = '',
} = {}) {
  const name = String(displayName || profileId || 'Assistant').trim() || 'Assistant'
  const unavailable = () => typeof unavailableText === 'function'
    ? unavailableText()
    : String(unavailableText || `${name} is unavailable right now.`)

  async function summarize({
    chatJid,
    hours = 24,
    groupName = '',
  } = {}) {
    const safeHours = clampHours(hours)
    const sinceMs = Date.now() - safeHours * 3600000
    const rows = storage?.listConversationMessages?.({
      chatJid,
      sinceMs,
      limit:25000,
      ascending:true,
    }) || []

    if (!rows.length) {
      return {
        ok:true,
        text:`I don't have any stored messages from the last ${safeHours}h here yet.${signature ? ' ' + signature : ''}`,
        messageCount:0,
      }
    }

    if (!ai?.enabled) {
      return {
        ok:false,
        text:unavailable(),
        messageCount:rows.length,
      }
    }

    const chunks = chunkRows(rows, name)
    const partials = []

    for (let index = 0; index < chunks.length; index += 1) {
      const transcript = chunks[index].map(row => rowLine(row, name)).join('\n')
      const result = await ai.complete({
        system:[
          'Summarize this slice of a WhatsApp group conversation factually.',
          'Capture important topics, decisions, disagreements, questions, links, plans, corrections, and notable events.',
          'Ignore routine greetings, command spam, and repetitive chatter unless it materially affected the conversation.',
          'Do not invent motives or events. Keep names exactly as shown when useful.',
          'Return compact bullet points for a later merge step.',
        ].join(' '),
        messages:[{
          role:'user',
          content:`Group: ${groupName || 'unknown'}\nWindow slice ${index + 1}/${chunks.length}\n\n${transcript}`,
        }],
        allowWeb:false,
        temperature:0.25,
        maxTokens:900,
        reasoningEffort:'low',
      })
      if (!result.ok) {
        return { ok:false, text:unavailable(), messageCount:rows.length }
      }
      partials.push(result.text)
    }

    const merged = await ai.complete({
      system:[
        `You are ${name}, producing a faithful WhatsApp group recap in your normal voice.`,
        'Merge the supplied chunk summaries without adding anything that is not supported.',
        'Prioritize what actually mattered: major topics, decisions, disagreements, plans, corrections, useful links or shared information, and unresolved questions.',
        'Do not rank people or invent sentiment.',
        'Use a short heading and concise bullets. Keep it readable in WhatsApp.',
        'End with one short line offering a more detailed timeline if useful.',
      ].join(' '),
      messages:[{
        role:'user',
        content:[
          `Group: ${groupName || 'unknown'}`,
          `Window: last ${safeHours} hours`,
          `Stored messages considered: ${rows.length}`,
          '',
          partials.map((text, index) => `--- Slice ${index + 1} ---\n${text}`).join('\n\n'),
        ].join('\n'),
      }],
      allowWeb:false,
      temperature:0.3,
      maxTokens:1400,
      reasoningEffort:'medium',
    })

    if (!merged.ok) return { ok:false, text:unavailable(), messageCount:rows.length }

    storage?.sharedSet?.(`${summaryNamespace}:${profileId}`, chatJid, {
      atMs:Date.now(),
      hours:safeHours,
      messageCount:rows.length,
      text:merged.text,
    })

    return {
      ok:true,
      text:merged.text,
      messageCount:rows.length,
    }
  }

  async function answer({
    chatJid,
    text,
    senderName = '',
    quotedText = '',
    quotedSpeaker = '',
    groupName = '',
    isGroup = false,
    groupPersonalities = [],
    senderPersonality = '',
    reactionSoundPolicy = '',
  } = {}) {
    const current = cleanText(text, 12000)
    if (!current) return { ok:false, text:'' }

    const summaryHours = isGroup ? summaryWindowFor(current) : 0
    if (summaryHours) return summarize({ chatJid, hours:summaryHours, groupName })

    // A passive mention or reply must never broadcast an AI setup/error notice.
    if (!ai?.enabled) return { ok:false, text:'' }

    const rows = storage?.listConversationMessages?.({
      chatJid,
      sinceMs:Date.now() - 24 * 3600000,
      limit:60,
      ascending:true,
    }) || []

    const priorSummary = storage?.sharedGet?.(`${summaryNamespace}:${profileId}`, chatJid)
    const presence = Array.isArray(groupPersonalities)
      ? groupPersonalities
          .map(row => ({
            profileId:String(row?.profileId || '').trim().toLowerCase(),
            displayName:cleanText(row?.displayName, 80),
            mentionToken:cleanText(row?.mentionToken, 80),
          }))
          .filter(row => row.profileId && row.displayName)
      : []
    const liveCommandFilter = typeof commandFilter === 'function'
      ? command => commandFilter(command, { groupPersonalities:presence, isGroup })
      : null
    const presenceBlock = isGroup
      ? [
          'LIVE GROUP PERSONALITY PRESENCE:',
          ...(presence.length
            ? presence.map(row => `- ${row.displayName} (${row.profileId})${row.profileId === String(profileId || '').toLowerCase() ? ' [self]' : ''}${row.mentionToken ? ` | mention token: ${row.mentionToken}` : ''}`)
            : ['- No other MSCC personality is confirmed present.']),
        ].join('\n')
      : ''

    const prompt = [
      groupName ? `GROUP: ${groupName}` : 'CHAT: direct message',
      presenceBlock,
      senderPersonality ? `CURRENT SENDER PERSONALITY: ${cleanText(senderPersonality, 80)}` : '',
      senderName ? `CURRENT USER: ${cleanText(senderName, 80)}` : '',
      quotedText ? `REPLYING TO: ${cleanText(quotedSpeaker || 'Someone', 80)}: ${cleanText(quotedText, 4000)}` : '',
      priorSummary?.text && Date.now() - Number(priorSummary.atMs || 0) < 24 * 3600000
        ? `EARLIER GROUP SUMMARY:\n${cleanText(priorSummary.text, 6000)}`
        : '',
      `RECENT CONVERSATION:\n${recentTranscript(rows, name)}`,
      '',
      `CURRENT MESSAGE:\n${current}`,
      '',
      'LIVE PUBLIC COMMAND CATALOG (read-only; generated from the current registry):',
      compactCommandReference(getCommands(), liveCommandFilter, current),
      'This catalog is the source of truth for which public commands exist right now. It updates with the registry; do not rely on remembered command names that are absent from it.',
      'Do not recommend a command when the current request is an AI-native task you can perform directly. Use commands mainly for operational bot features.',
    ].filter(Boolean).join('\n\n')

    const needsReasoning = /\b(why|explain|analy[sz]e|compare|reason|solve|prove|calculate|strategy)\b/i.test(current)
    const result = await ai.complete({
      system:[String(systemPrompt || '').trim(), CHARACTER_RUNTIME_RULES, cleanText(reactionSoundPolicy, 2000)].filter(Boolean).join(' '),
      messages:[{ role:'user', content:prompt }],
      allowWeb:false,
      temperature:0.62,
      maxTokens:needsReasoning ? 1800 : 1400,
      reasoningEffort:needsReasoning ? 'medium' : 'low',
    })

    if (!result.ok) return { ok:false, text:'', reason:result.reason || 'unavailable' }
    return {
      ok:true,
      text:result.text,
      usedWeb:result.usedWeb,
      model:result.model,
    }
  }

  return {
    profileId,
    displayName:name,
    answer,
    summarize,
  }
}
