import assert from 'node:assert/strict'
import { configuredClipdropKeys, targetDimensions, upscaleFactor, upscaleImage } from './utils/upscale-media.js'
import upscaleCommand from './commands/media/upscale.js'

assert.equal(upscaleFactor(),2)
assert.equal(upscaleFactor('4'),4)
assert.throws(() => upscaleFactor(8),/2x or 4x/)
assert.deepEqual(targetDimensions(500,250,2),{ width:1000,height:500,actualScale:2 })
assert.deepEqual(targetDimensions(2048,1024,4),{ width:4096,height:2048,actualScale:2 })
assert.throws(() => targetDimensions(4200,1024,2),/too large/)
assert.deepEqual(configuredClipdropKeys({ CLIPDROP_API_KEYS:'one, two one' }),['one','two'])
assert.deepEqual(configuredClipdropKeys({}),[])
await assert.rejects(upscaleImage(Buffer.alloc(0)),/smaller than 10 MB/)
await assert.rejects(upscaleImage(Buffer.from('hello')),/JPG, PNG, or WebP/)
assert.equal(upscaleCommand.name,'upscale')
assert(upscaleCommand.aliases.includes('hd'))
const replies=[]
await upscaleCommand.run({ args:['8'], reply:x=>replies.push(String(x)) })
assert(replies[0].includes('2x or 4x'))
console.log('PASS MSCC upscale input validation, sizes, and safe fallback contract')
