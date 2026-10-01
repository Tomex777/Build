import assert from 'node:assert/strict'
import { looksLikeNumberSelection, parseNumberSelection } from './number-selection.js'

const entries = Array.from({ length:20 }, (_, index) => ({
  id:'e' + (index + 1),
  number:String(index + 1),
}))

assert.equal(looksLikeNumberSelection('1-10'), true)
assert.equal(looksLikeNumberSelection('1,3,4,7'), true)
assert.equal(looksLikeNumberSelection('1-10,13,15-18'), true)
assert.equal(looksLikeNumberSelection('episode 1'), false)

let parsed = parseNumberSelection('1-10,13,15-18', entries)
assert.equal(parsed.ok, true)
assert.deepEqual(
  parsed.selected.map(item => Number(item.number)),
  [1,2,3,4,5,6,7,8,9,10,13,15,16,17,18],
)

parsed = parseNumberSelection('5-3', entries)
assert.equal(parsed.ok, true)
assert.deepEqual(parsed.selected.map(item => Number(item.number)), [3,4,5])

const decimalEntries = [
  { id:'a', number:'12' },
  { id:'b', number:'12.5' },
  { id:'c', number:'13' },
]
parsed = parseNumberSelection('12-13', decimalEntries)
assert.equal(parsed.ok, true)
assert.deepEqual(parsed.selected.map(item => item.number), ['12','12.5','13'])

assert.equal(parseNumberSelection('500', entries).ok, false)
assert.equal(parseNumberSelection('1,a,3', entries).ok, false)

console.log('PASS numeric media selection parser')
