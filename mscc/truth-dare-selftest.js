import assert from 'node:assert/strict'
import { TRUTH_QUESTIONS, DARE_QUESTIONS } from './utils/truth-dare-bank.js'
import {
  TRUTH_DARE_REPEAT_WINDOW_MS,
  pickTruthOrDare,
  selectUnusedQuestion,
  truthDareScopeKey,
} from './utils/truth-dare-engine.js'

function assertBank(name, bank, expected) {
  assert.equal(bank.length, expected, `${name} must contain exactly ${expected} entries`)
  assert.equal(new Set(bank.map(item => item.id)).size, expected, `${name} IDs must be unique`)
  assert.equal(new Set(bank.map(item => item.text)).size, expected, `${name} wording must be unique`)
  for (const item of bank) {
    assert.match(item.id, new RegExp(`^${name}-\\d{4}$`))
    assert.ok(item.text.length >= 20, `${item.id} is too short`)
  }
}

assertBank('truth', TRUTH_QUESTIONS, 1000)
assertBank('dare', DARE_QUESTIONS, 1000)
assert.equal(TRUTH_DARE_REPEAT_WINDOW_MS, 72 * 60 * 60 * 1000)

const storage = new Map()
const shared = {
  get(namespace, key) { return storage.get(`${namespace}|${key}`) ?? null },
  set(namespace, key, value) { storage.set(`${namespace}|${key}`, structuredClone(value)); return value },
}

const groupA = { groupKey:'123@g.us', userKey:'111', shared }
const groupB = { groupKey:'456@g.us', userKey:'222', shared }
const now = 1_800_000_000_000

assert.equal(truthDareScopeKey(groupA), 'group:123@g.us')

const first = pickTruthOrDare(groupA, 'truth', { now, randomIndex:() => 0 })
const second = pickTruthOrDare(groupA, 'truth', { now:now + 1000, randomIndex:() => 0 })
assert.ok(first.question)
assert.ok(second.question)
assert.notEqual(second.question.id, first.question.id, 'same chat repeated a truth inside 72 hours')

const otherChat = pickTruthOrDare(groupB, 'truth', { now:now + 2000, randomIndex:() => 0 })
assert.equal(otherChat.question.id, first.question.id, 'another chat should have its own repeat history')

const firstDare = pickTruthOrDare(groupA, 'dare', { now:now + 3000, randomIndex:() => 0 })
assert.equal(firstDare.question.id, DARE_QUESTIONS[0].id, 'truth history must not block dare history')

const afterWindow = selectUnusedQuestion(
  TRUTH_QUESTIONS,
  [{ id:first.question.id, at:now }],
  { now:now + TRUTH_DARE_REPEAT_WINDOW_MS + 1, randomIndex:() => 0 },
)
assert.equal(afterWindow.question.id, first.question.id, 'question should become eligible again after 72 hours')

const tiny = [
  { id:'x-1', text:'First test question long enough.' },
  { id:'x-2', text:'Second test question long enough.' },
]
const exhausted = selectUnusedQuestion(
  tiny,
  [
    { id:'x-1', at:now },
    { id:'x-2', at:now },
  ],
  { now:now + 1, randomIndex:() => 0 },
)
assert.equal(exhausted.exhausted, true)
assert.equal(exhausted.question, null)

console.log('truth/dare 1000+1000 anti-repeat self-test passed')
