const bags = new Map()

function refill(key, values) {
  const items = [...values]
  for (let i = items.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[items[i], items[j]] = [items[j], items[i]]
  }
  bags.set(key, items)
  return items
}

export function pickLine(key, values) {
  const source = Array.isArray(values) ? values.filter(Boolean) : []
  if (!source.length) return ''
  let bag = bags.get(key)
  if (!bag?.length) bag = refill(key, source)
  return bag.pop()
}

export function josiahPing(name = 'Josiah') {
  return pickLine('josiah:ping', [
    `🏓 ${name} is here.`,
    `Still here. ◇`,
    `You called? I'm here. ◇`,
    `Alive and listening. ◇`,
    `Right here.`,
    `Didn't go anywhere. ◇`,
    `I'm listening.`,
    `Present. Happy now? ◇`,
    `Here. What do you need?`,
    `Yep. Still with you. ◇`,
    `Online and paying attention.`,
    `I'm here. Try me. ◇`,
  ])
}

export function josiahUptime(value) {
  return pickLine('josiah:uptime', [
    `⏱️ *${value}* and counting. ◇`,
    `Still running — *${value}*.`,
    `I've been up for *${value}*.`,
    `You checking up on me? *${value}* online. ◇`,
    `*Uptime:* ${value}`,
    `No nap yet. *${value}*. ◇`,
    `Running fine for *${value}*.`,
    `* ${value}* online.`.replace('* ', '*'),
    `Clock says *${value}*. ◇`,
    `Still standing after *${value}*.`,
  ])
}

export function josiahCalc(expression, result) {
  const options = [
    `*${result}* ◇`,
    `${expression} = *${result}*`,
    `That gives you *${result}*.`,
    `Answer: *${result}* ◇`,
    `*${result}*. There you go.`,
    `I get *${result}*.`,
    `Math says *${result}*. ◇`,
    `*${result}*. Next one?`,
    `Done — *${result}*.`,
    `You needed me for that? 😭\n\n*${result}*`,
  ]
  return pickLine('josiah:calc', options)
}

export function josiahCalcError(kind = 'invalid') {
  if (kind === 'zero') {
    return pickLine('josiah:calc-zero', [
      `Division by zero? Nice try. ◇`,
      `Can't divide by zero.`,
      `Zero isn't playing along with that division. ◇`,
      `That one breaks at division by zero.`,
    ])
  }
  return pickLine('josiah:calc-error', [
    `I can't make sense of that expression.`,
    `Give me numbers and +, -, *, /, %, ^ or parentheses. ◇`,
    `That doesn't look like a calculation I can safely evaluate.`,
    `Try the expression again — something in it doesn't parse. ◇`,
  ])
}

export function josiahQrSuccess() {
  return pickLine('josiah:qr-success', [
    `There you go. ◇`,
    `Done.`,
    `QR ready. ◇`,
    `Made it.`,
    `Here you are. ◇`,
    `That's your QR.`,
    `Ready when you are. ◇`,
    `Got it.`,
  ])
}

export function josiahUsage(usage) {
  return pickLine('josiah:usage', [
    `Try: ${usage}`,
    `Use it like this:\n${usage}`,
    `You're missing something. Try ${usage}`,
    `${usage} ◇`,
  ])
}

export function josiahAiUnavailable() {
  return pickLine('josiah:ai-unavailable', [
    `My brain's not answering right now. Give me a moment and try again. ◇`,
    `I lost the thinking part for a second. Try that again shortly.`,
    `Something interrupted me before I could answer. Try again. ◇`,
    `I can't get a proper answer out right now. One more try in a moment.`,
  ])
}


export function namiAiUnavailable() {
  return pickLine('nami:ai-unavailable', [
    `Give me a second. My brain just buffered. ✦`,
    `I lost the thread for a moment. Try that again.`,
    `That one didn't reach the thinking part. One more time? ✦`,
    `I can't give you a proper answer right now. Try again in a moment.`,
  ])
}


export function namiUsage(usage) {
  return pickLine('nami:usage', [
    `Try it like this: ${usage} ✦`,
    `Use ${usage}`,
    `Give me something to work with 😭\n\n${usage}`,
    `${usage} ✦`,
  ])
}

export function namiNoSources(capability) {
  const label = String(capability || 'anime')
  return pickLine(`nami:no-sources:${label}`, [
    `No ${label} sources are installed yet. I can't pull titles out of thin air. ✦`,
    `I've got no ${label} source connected right now.`,
    `No ${label} sources yet. Once one is installed, we're good. ✦`,
    `Nothing to search with yet — there isn't a ${label} source installed.`,
  ])
}

export function namiNoResults(capability, query = '') {
  const label = String(capability || 'anime')
  const target = query ? ` for “${query}”` : ''
  return pickLine(`nami:no-results:${label}`, [
    `Couldn't find any ${label} results${target}. Try another title or spelling. ✦`,
    `Nothing came back${target}. Got another name for it?`,
    `No match${target}. Try the English title, romaji, or a shorter search. ✦`,
    `That search came up empty${target}. Give me another version of the title.`,
  ])
}

export function namiSourceFailure(capability, sourceName = '', all = false) {
  const label = String(capability || 'anime')
  if (all) {
    return pickLine(`nami:all-failed:${label}`, [
      `Every configured ${label} source failed that request. That's impressive in the worst way. 😭`,
      `All the ${label} sources struck out. Try again in a bit. ✦`,
      `No luck — every ${label} source failed this one.`,
    ])
  }
  const source = String(sourceName || 'That source')
  return pickLine(`nami:source-failed:${label}`, [
    `${source} flaked out on that request. Try again or switch sources. ✦`,
    `${source} couldn't finish that one.`,
    `That request died at ${source}. Another source might behave better. 😭`,
  ])
}

export function namiExpiredSelection() {
  return pickLine('nami:expired-selection', [
    `That selection expired. Run the anime command again and pick from the fresh list. ✦`,
    `Old menu. Start the anime search again and I'll rebuild it.`,
    `That button is stale now. Re-run the command and choose again. ✦`,
  ])
}

export function namiDownloadStarted({ title, episode = '', range = '', quality = '', delivery = '' } = {}) {
  const target = episode
    ? `${title} — Episode ${episode}`
    : `${title} — Episodes ${range}`
  const suffix = [quality, delivery].filter(Boolean).join(', ')
  return pickLine('nami:download-started', [
    `Got it. *${target}* is starting${suffix ? ` (${suffix})` : ''}. ✦`,
    `Starting *${target}*${suffix ? ` — ${suffix}` : ''}.`,
    `On it. *${target}*${suffix ? ` (${suffix})` : ''}. ✦`,
    `Download started: *${target}*${suffix ? ` — ${suffix}` : ''}.`,
  ])
}
