import assert from 'node:assert/strict'
import {
  parseSearchImageArgs, imageBufferDetails, isUsableSearchImage,
  sourceImageUrls, fetchQualityImage, qualityImagePayload,
} from './utils/search-image-quality.js'
import { pinterestImageUrls } from './utils/pinterest-search.js'
import { loadCommands } from './command-registry.js'

function png(width, height, suffix = 0) {
  const bytes = Buffer.alloc(32)
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]).copy(bytes)
  bytes.write('IHDR', 12, 'ascii')
  bytes.writeUInt32BE(width, 16)
  bytes.writeUInt32BE(height, 20)
  bytes[31] = suffix
  return bytes
}
function jpeg(width, height) {
  return Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x04, 0x00, 0x00,
    0xff, 0xc0, 0x00, 0x0b, 0x08,
    (height >>> 8) & 255, height & 255,
    (width >>> 8) & 255, width & 255,
    0x01, 0x01, 0x11, 0x00, 0xff, 0xd9,
  ])
}
assert.deepEqual(
  parseSearchImageArgs(['gold', 'fantasy', '6', 'hd']),
  { query:'gold fantasy', count:6, original:true },
)
assert.deepEqual(
  parseSearchImageArgs(['gold', 'hd', '6']),
  { query:'gold', count:6, original:true },
)
assert.deepEqual(
  parseSearchImageArgs(['hd', 'character', '--original']),
  { query:'hd character', count:6, original:true },
)
assert.deepEqual(
  parseSearchImageArgs(['blue', 'art', '999']),
  { query:'blue art', count:20, original:false },
)
assert.deepEqual(
  parseSearchImageArgs(['blue', 'art']),
  { query:'blue art', count:6, original:false },
)

assert.deepEqual(imageBufferDetails(png(900, 1600)), {
  width:900, height:1600, extension:'png', mimetype:'image/png',
})
assert.deepEqual(imageBufferDetails(jpeg(800, 1200)), {
  width:800, height:1200, extension:'jpg', mimetype:'image/jpeg',
})
assert.equal(isUsableSearchImage(imageBufferDetails(png(236, 354))), false)
assert.equal(isUsableSearchImage(imageBufferDetails(png(564, 1000))), false)
assert.equal(isUsableSearchImage(imageBufferDetails(png(736, 1104))), true)
assert.equal(imageBufferDetails(Buffer.from('not an image')), null)

const thumb = 'https://thumbnail.example/picture.jpg'
const full = 'https://source.example/picture.png'
assert.deepEqual(sourceImageUrls({ imageUrl:full, thumbnailUrl:thumb }), [full])
assert.deepEqual(sourceImageUrls({ imageUrl:'https://i.pinimg.com/236x/aa/bb/cc/pic.jpg' }), [
  'https://i.pinimg.com/originals/aa/bb/cc/pic.jpg',
  'https://i.pinimg.com/236x/aa/bb/cc/pic.jpg',
])
assert.deepEqual(sourceImageUrls({ imageUrl:'http://127.0.0.1/private.png' }), [
  'http://127.0.0.1/private.png',
]) // Downloader's SSRF/DNS guard remains authoritative.

const pinImage = pinterestImageUrls({
  images: {
    '236x': { url:'https://i.pinimg.com/236x/example.jpg', width:236, height:350 },
    '736x': { url:'https://i.pinimg.com/736x/example.jpg', width:736, height:1104 },
    orig: { url:'https://i.pinimg.com/originals/example.jpg', width:1400, height:2100 },
  },
})
assert.deepEqual(pinImage, [
  'https://i.pinimg.com/originals/example.jpg',
  'https://i.pinimg.com/736x/example.jpg',
  'https://i.pinimg.com/236x/example.jpg',
])

const calls = []
const seen = new Set()
const getImage = async url => {
  calls.push(url)
  if (url === 'https://source.example/fail.png') throw new Error('source blocked')
  return { buffer:url === full ? png(1024, 1600) : png(236, 354) }
}
const image = await fetchQualityImage({
  imageUrl:full, thumbnailUrl:thumb,
}, { download:getImage, seen })
assert.equal(image.details.width, 1024)
assert.deepEqual(calls, [full], 'Bing thumbnail must not be fetched')
assert.equal(seen.size, 1)
assert.equal(await fetchQualityImage({ imageUrl:full }, { download:getImage, seen }), null, 'Duplicate image should be skipped')
assert.equal(await fetchQualityImage({ imageUrl:'https://source.example/small.png', thumbnailUrl:thumb }, {
  download:getImage,
}), null, 'Tiny preview must be rejected')
const badCalls = []
assert.equal(await fetchQualityImage({
  imageUrl:'https://source.example/fail.png', thumbnailUrl:thumb,
}, {
  download:async url => {
    badCalls.push(url)
    if (url.includes('fail')) throw new Error('HTTP error')
    return { buffer:png(236, 354) }
  },
}), null)
assert.deepEqual(badCalls, ['https://source.example/fail.png'])

const standard = qualityImagePayload(image, { caption:'Full original', index:2 })
assert.equal(standard.image, image.buffer)
assert.equal(standard.caption, 'Full original')
assert.equal(standard.document, undefined)

const hd = qualityImagePayload(image, { original:true, caption:'Uncompressed', index:2 })
assert.equal(hd.document, image.buffer)
assert.equal(hd.mimetype, 'image/png')
assert.equal(hd.fileName, 'MSCC-image-2.png')
assert.equal(hd.caption, 'Uncompressed')
assert.equal(hd.image, undefined)

const commands = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})
for (const name of ['image', 'img', 'pin', 'pinterest']) {
  const command = commands.commands.get(name)
  assert.ok(command, `Missing image search command ${name}`)
  assert.match(command.usage, /hd/, `Missing HD option on ${name}`)
}
console.log('PASS image search: source resolution, thumbnail exclusion, Pinterest variants, HD documents, duplicate rejection')
