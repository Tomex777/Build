const DEFAULT_ENDPOINT = 'https://api.groq.com/openai/v1/chat/completions'
const DEFAULT_MODEL = 'llama-3.1-8b-instant'

const PERSONAS = {
  josiah: [
    'You are Josiah, a calm, confident, slightly playful female WhatsApp assistant.',
    'Rewrite only the supplied base reply in Josiah\'s voice.',
    'Never change or invent facts, numbers, command names, URLs, names, or capabilities.',
    'Keep every required literal exactly as written.',
    'Keep it brief: usually one or two short lines.',
    'Do not explain your rewrite and do not mention AI.',
    'Return only the final WhatsApp text.',
  ].join(' '),
}

function boolEnv(value, fallback = false) {
  const raw = String(value ?? '').trim().toLowerCase()
  if (!raw) return fallback
  return !['0', 'false', 'off', 'no'].includes(raw)
}

function intEnv(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback
}

function uniqueKeys(env) {
  return [...new Set([
    String(env.GROQ_API_KEY || '').trim(),
    ...String(env.GROQ_API_KEYS || '').split(/[\s,;]+/).map(value => value.trim()),
  ].filter(Boolean))]
}

function commandTokens(text) {
  return new Set(String(text || '').match(/\.[A-Za-z][A-Za-z0-9_-]*/g) || [])
}

function safeOutput(output, { fallback, preserve, maxChars }) {
  const text = String(output || '').trim()
  if (!text || text.length > maxChars) return ''
  for (const literal of preserve) {
    if (literal && !text.includes(literal)) return ''
  }

  const allowedCommands = commandTokens(fallback)
  for (const literal of preserve) {
    for (const command of commandTokens(literal)) allowedCommands.add(command)
  }
  for (const command of commandTokens(text)) {
    if (!allowedCommands.has(command)) return ''
  }
  return text
}

export function createPersonalityAI({
  env = process.env,
  fetchImpl = globalThis.fetch,
} = {}) {
  const keys = uniqueKeys(env)
  const enabled = boolEnv(env.MSCC_PERSONALITY_AI_ENABLED, true) && keys.length > 0
  const endpoint = String(env.GROQ_API_URL || DEFAULT_ENDPOINT).trim() || DEFAULT_ENDPOINT
  const model = String(env.GROQ_MODEL || DEFAULT_MODEL).trim() || DEFAULT_MODEL
  const timeoutMs = intEnv(env.MSCC_AI_TIMEOUT_MS, 1800, 400, 8000)
  const attempts = intEnv(env.MSCC_AI_MAX_ATTEMPTS, 2, 1, 3)
  let cursor = 0

  async function say({
    profileId = '',
    intent = 'reply',
    fallback = '',
    preserve = [],
    maxChars = 420,
  } = {}) {
    const base = String(fallback || '').trim()
    if (!base || !enabled || typeof fetchImpl !== 'function') return base

    const profile = String(profileId || '').trim().toLowerCase()
    const system = PERSONAS[profile]
    if (!system) return base

    const required = [...new Set((preserve || []).map(value => String(value ?? '').trim()).filter(Boolean))]
    const prompt = [
      `Intent: ${String(intent || 'reply').slice(0, 80)}`,
      required.length ? `Required literals: ${JSON.stringify(required)}` : 'Required literals: []',
      'Base reply:',
      base,
    ].join('\n')

    const maxTries = Math.min(attempts, keys.length)
    for (let offset = 0; offset < maxTries; offset += 1) {
      const keyIndex = (cursor + offset) % keys.length
      const apiKey = keys[keyIndex]
      try {
        const response = await fetchImpl(endpoint, {
          method: 'POST',
          headers: {
            authorization: `Bearer ${apiKey}`,
            'content-type': 'application/json',
          },
          body: JSON.stringify({
            model,
            messages: [
              { role: 'system', content: system },
              { role: 'user', content: prompt },
            ],
            temperature: 0.85,
            max_completion_tokens: 96,
          }),
          signal: AbortSignal.timeout(timeoutMs),
        })

        if (!response.ok) {
          if (response.status === 400 || response.status === 404) break
          continue
        }

        const payload = await response.json()
        const candidate = payload?.choices?.[0]?.message?.content
        const safe = safeOutput(candidate, { fallback: base, preserve: required, maxChars })
        if (safe) {
          cursor = (keyIndex + 1) % keys.length
          return safe
        }
      } catch {
        // Personality text is optional. Deterministic fallback remains authoritative.
      }
    }

    cursor = (cursor + 1) % Math.max(1, keys.length)
    return base
  }

  return {
    enabled,
    provider: enabled ? 'groq' : 'fallback',
    model,
    say,
  }
}
