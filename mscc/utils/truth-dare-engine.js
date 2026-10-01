import { randomInt } from 'node:crypto'
import { TRUTH_QUESTIONS, DARE_QUESTIONS } from './truth-dare-bank.js'

export const TRUTH_DARE_REPEAT_WINDOW_MS = 72 * 60 * 60 * 1000
export const TRUTH_DARE_HISTORY_NAMESPACE = 'truth-dare-history'

const BANKS = Object.freeze({
  truth: TRUTH_QUESTIONS,
  dare: DARE_QUESTIONS,
})

function normalizeHistory(value, now) {
  const cutoff = now - TRUTH_DARE_REPEAT_WINDOW_MS
  if (!Array.isArray(value)) return []
  return value
    .map(item => ({
      id:String(item?.id || '').trim(),
      at:Number(item?.at || 0),
    }))
    .filter(item => item.id && Number.isFinite(item.at) && item.at > cutoff && item.at <= now)
}

export function truthDareScopeKey(ctx = {}) {
  const group = String(ctx.groupKey || '').trim()
  if (group) return `group:${group}`

  const user = String(ctx.userKey || '').trim()
  if (user) return `dm:${user}`

  const chat = String(ctx.message?.key?.remoteJid || '').trim()
  if (chat) return `chat:${chat}`

  return 'chat:unknown'
}

export function questionBank(kind) {
  const key = String(kind || '').trim().toLowerCase()
  const bank = BANKS[key]
  if (!bank) throw new Error(`Unknown truth/dare bank: ${kind}`)
  return bank
}

export function selectUnusedQuestion(bank, history, {
  now = Date.now(),
  randomIndex = length => randomInt(length),
} = {}) {
  const active = normalizeHistory(history, now)
  const used = new Set(active.map(item => item.id))
  const eligible = bank.filter(item => !used.has(item.id))

  if (!eligible.length) {
    return {
      question:null,
      history:active,
      exhausted:true,
      remaining:0,
    }
  }

  const rawIndex = Number(randomIndex(eligible.length))
  const index = Number.isInteger(rawIndex) && rawIndex >= 0 && rawIndex < eligible.length
    ? rawIndex
    : 0
  const question = eligible[index]
  const nextHistory = [...active, { id:question.id, at:now }]

  return {
    question,
    history:nextHistory,
    exhausted:false,
    remaining:eligible.length - 1,
  }
}

export function pickTruthOrDare(ctx, kind, options = {}) {
  const bank = questionBank(kind)
  const scope = truthDareScopeKey(ctx)
  const key = `${scope}:${String(kind).toLowerCase()}`

  const stored = ctx?.shared?.get?.(TRUTH_DARE_HISTORY_NAMESPACE, key) ?? []
  const result = selectUnusedQuestion(bank, stored, options)

  if (result.question) {
    ctx?.shared?.set?.(TRUTH_DARE_HISTORY_NAMESPACE, key, result.history)
  }

  return {
    ...result,
    scope,
    key,
  }
}
