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
