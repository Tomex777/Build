import assert from 'node:assert/strict'
import {
  DEFAULT_STICKER_PACK_NAME,
  DEFAULT_STICKER_PUBLISHER,
  buildStickerExif,
  stickerMetadata,
} from './utils/sticker-metadata.js'

assert.equal(DEFAULT_STICKER_PACK_NAME, 'MSCC')
assert.equal(DEFAULT_STICKER_PUBLISHER, '『N I G H T』')
assert.equal(DEFAULT_STICKER_PUBLISHER.includes('༺'), false)
assert.equal(DEFAULT_STICKER_PUBLISHER.includes('༻'), false)

const inside = DEFAULT_STICKER_PUBLISHER.slice(1, -1)
assert.equal(inside, 'N I G H T')
const separators = [...inside].filter(ch => ch === ' ')
assert.equal(separators.length, 4)
assert.equal(' '.codePointAt(0), 0x2002)

const metadata = stickerMetadata({
  packName: 'Teletubis',
  packId: 'test-pack-id',
  emojis: ['🙂'],
})
assert.deepEqual(metadata, {
  'sticker-pack-id': 'test-pack-id',
  'sticker-pack-name': 'Teletubis',
  'sticker-pack-publisher': '『N I G H T』',
  emojis: ['🙂'],
})

const exif = buildStickerExif({
  packName: 'Filename Pack',
  packId: 'fixture-id',
  emojis: ['🖼️'],
})
const jsonLength = exif.readUIntLE(14, 4)
const decoded = JSON.parse(exif.subarray(22, 22 + jsonLength).toString('utf8'))
assert.equal(decoded['sticker-pack-name'], 'Filename Pack')
assert.equal(decoded['sticker-pack-publisher'], '『N I G H T』')
assert.deepEqual(decoded.emojis, ['🖼️'])

console.log('sticker metadata self-test passed')
