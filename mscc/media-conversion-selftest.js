import assert from 'node:assert/strict'
import {
  STICKER_CANVAS,
  STICKER_FIT_FILTER,
  classifyMessageMedia,
  mediaIsAnimated,
} from './utils/media-conversion.js'

assert.equal(STICKER_CANVAS, 512)
assert.match(STICKER_FIT_FILTER, /scale=512:512:force_original_aspect_ratio=decrease/)
assert.match(STICKER_FIT_FILTER, /pad=512:512:\(ow-iw\)\/2:\(oh-ih\)\/2:color=0x00000000/)
assert.doesNotMatch(STICKER_FIT_FILTER, /crop=/)

assert.equal(classifyMessageMedia({ key:'imageMessage', media:{ mimetype:'image/jpeg' } }), 'image')
assert.equal(classifyMessageMedia({ key:'videoMessage', media:{ mimetype:'video/mp4' } }), 'video')
assert.equal(classifyMessageMedia({ key:'stickerMessage', media:{ mimetype:'image/webp' } }), 'sticker')
assert.equal(classifyMessageMedia({ key:'documentMessage', media:{ mimetype:'image/gif' } }), 'video')
assert.equal(classifyMessageMedia({ key:'documentMessage', media:{ mimetype:'image/png' } }), 'image')
assert.equal(classifyMessageMedia({ key:'audioMessage', media:{ mimetype:'audio/ogg' } }), '')

assert.equal(mediaIsAnimated({ key:'videoMessage', media:{ mimetype:'video/mp4' } }), true)
assert.equal(mediaIsAnimated({ key:'stickerMessage', media:{ mimetype:'image/webp', isAnimated:true } }), true)
assert.equal(mediaIsAnimated({ key:'stickerMessage', media:{ mimetype:'image/webp', isAnimated:false } }), false)

console.log('media conversion self-test passed')
