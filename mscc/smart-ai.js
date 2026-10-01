const DEFAULT_ENDPOINT = 'https://api.groq.com/openai/v1/chat/completions'
const DEFAULT_MODELS = ['openai/gpt-oss-120b', 'openai/gpt-oss-20b']

function boolEnv(value, fallback = false) {
  const raw = String(value ?? '').trim().toLowerCase()
  if (!raw) return fallback
  return !['0', 'false', 'off', 'no'].includes(raw)
}

function intEnv(value, fallback, min, max) {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback
}

function csv(value) {
  return String(value || '')
    .split(/[\s,;]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function unique(values) {
  return [...new Set(values.filter(Boolean))]
}

function apiKeys(env) {
  return unique([
    String(env.GROQ_API_KEY || '').trim(),
    ...csv(env.GROQ_API_KEYS),
  ])
}

function modelList(env) {
  const configured = csv(env.GROQ_SMART_MODELS)
  return configured.length ? unique(configured) : DEFAULT_MODELS
}

function parseRetryAfter(response) {
  const raw = response?.headers?.get?.('retry-after')
  if (!raw) return 0
  const seconds = Number(raw)
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000)
  const at = Date.parse(raw)
  return Number.isFinite(at) ? Math.max(0, at - Date.now()) : 0
}

function bodyFor({
  model,
  messages,
  temperature,
  maxTokens,
  reasoningEffort,
  allowWeb,
}) {
  const body = {
    model,
    messages,
    temperature,
    max_completion_tokens: maxTokens,
    top_p: 1,
    stream: false,
  }
  if (String(model).includes('gpt-oss')) {
    body.reasoning_effort = reasoningEffort
  }
  if (allowWeb) {
    body.tools = [{ type:'browser_search' }]
    body.tool_choice = 'auto'
  }
  return body
}

export function createSmartAI({
  env = process.env,
  fetchImpl = globalThis.fetch,
  now = () => Date.now(),
} = {}) {
  const keys = apiKeys(env)
  const models = modelList(env)
  const endpoint = String(env.GROQ_API_URL || DEFAULT_ENDPOINT).trim() || DEFAULT_ENDPOINT
  const enabled = boolEnv(env.MSCC_SMART_AI_ENABLED, true) && keys.length > 0 && typeof fetchImpl === 'function'
  const timeoutMs = intEnv(env.MSCC_AI_TIMEOUT_MS, 12000, 800, 60000)
  const keyStates = keys.map(key => ({
    key,
    disabled: false,
    cooldownUntil: 0,
    failures: 0,
  }))
  let cursor = 0

  function healthyKeyIndexes() {
    const t = now()
    const order = []
    for (let offset = 0; offset < keyStates.length; offset += 1) {
      const index = (cursor + offset) % keyStates.length
      const state = keyStates[index]
      if (state.disabled || state.cooldownUntil > t) continue
      order.push(index)
    }
    return order
  }

  function markFailure(state, response) {
    state.failures += 1
    const status = Number(response?.status || 0)
    if (status === 401 || status === 403) {
      state.disabled = true
      return
    }
    if (status === 429) {
      state.cooldownUntil = now() + Math.max(parseRetryAfter(response), 60000)
      return
    }
    if (status >= 500 || status === 0) {
      state.cooldownUntil = now() + Math.min(30000, 1000 * (2 ** Math.min(5, state.failures)))
    }
  }

  function markSuccess(state) {
    state.failures = 0
    state.cooldownUntil = 0
  }

  async function complete({
    system = '',
    messages = [],
    allowWeb = false,
    temperature = 0.55,
    maxTokens = 1400,
    reasoningEffort = 'medium',
  } = {}) {
    if (!enabled) return { ok:false, reason:'disabled', text:'' }

    const input = []
    if (String(system || '').trim()) input.push({ role:'system', content:String(system).trim() })
    for (const message of messages || []) {
      const role = ['system','assistant','user'].includes(message?.role) ? message.role : 'user'
      const content = String(message?.content || '').trim()
      if (content) input.push({ role, content })
    }
    if (!input.length) return { ok:false, reason:'empty', text:'' }

    let lastReason = 'unavailable'
    for (const model of models) {
      const order = healthyKeyIndexes()
      if (!order.length) {
        lastReason = 'no-healthy-keys'
        break
      }

      for (const index of order) {
        const state = keyStates[index]
        cursor = (index + 1) % keyStates.length
        let response = null
        try {
          response = await fetchImpl(endpoint, {
            method:'POST',
            headers:{
              authorization:`Bearer ${state.key}`,
              'content-type':'application/json',
            },
            body:JSON.stringify(bodyFor({
              model,
              messages:input,
              temperature,
              maxTokens,
              reasoningEffort,
              allowWeb,
            })),
            signal:AbortSignal.timeout(timeoutMs),
          })

          if (!response.ok) {
            markFailure(state, response)
            lastReason = `http-${response.status}`
            if (response.status === 400 || response.status === 404) break
            continue
          }

          const payload = await response.json()
          const text = String(payload?.choices?.[0]?.message?.content || '').trim()
          if (!text) {
            markFailure(state, { status:500 })
            lastReason = 'empty-response'
            continue
          }

          markSuccess(state)
          return {
            ok:true,
            text,
            model,
            usedWeb:Boolean(payload?.choices?.[0]?.message?.executed_tools?.length),
          }
        } catch {
          markFailure(state, response)
          lastReason = 'network'
        }
      }
    }

    return { ok:false, reason:lastReason, text:'' }
  }

  return {
    enabled,
    provider: enabled ? 'groq' : 'fallback',
    models:[...models],
    complete,
    health() {
      const t = now()
      return keyStates.map((state, index) => ({
        index,
        disabled:state.disabled,
        coolingDown:state.cooldownUntil > t,
        failures:state.failures,
      }))
    },
  }
}
