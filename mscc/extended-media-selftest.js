import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createCanvas } from '@napi-rs/canvas'
import { loadCommands } from './command-registry.js'
import {
  runMediaProcess, emojiPairFromText, twemojiCodepoint, formatSafeExif,
  validateMediaSize, bestVideoFrames, reverseClip, stabilizeVideo, combinedEmojiSticker,
} from './utils/extended-media.js'
import {
  isPublicAddress, validatePublicHost, validateScreenshotUrl,
} from './utils/website-snapshot.js'

const registry=await loadCommands(new URL('./commands/',import.meta.url),{capabilityFromDirectory:true})
const names=['removebg','bestframe','reverse','stabilize','emojimix','ssweb','getexif']
for(const name of names) {
  const command=registry.commands.get(name)
  assert.ok(command,'Missing command '+name)
  assert.equal(command.name,name)
  assert.equal(command.capability,'media')
  assert.notEqual(command.ownerOnly,true)
  assert.notEqual(command.adminOnly,true)
}

assert.deepEqual(emojiPairFromText('😍 + 🔥'),['😍','🔥'])
assert.deepEqual(emojiPairFromText('❤️ 💀'),['❤️','💀'])
assert.throws(()=>emojiPairFromText('hello there'),/exactly two emojis/)
assert.throws(()=>emojiPairFromText('🐱'),/exactly two emojis/)
assert.equal(twemojiCodepoint('❤️'),'2764')
assert.equal(twemojiCodepoint('👨‍👩‍👧'),'1f468-200d-1f469-200d-1f467')
assert.throws(()=>validateMediaSize(Buffer.alloc(22*1024*1024)),/too large/)
const metadata=formatSafeExif({
  Model:'Safe camera',Make:'Brand',ImageWidth:1200,GPSLatitude:'2.3333',
  SerialNumber:'SECRET',DateTimeOriginal:'2026:01:01 10:00:00',
})
assert.match(metadata,/Safe camera/)
assert.doesNotMatch(metadata,/SECRET|2\.3333|GPS/)

assert.equal(isPublicAddress('8.8.8.8'),true)
for(const address of ['127.0.0.1','10.0.0.1','169.254.169.254',
  '172.17.0.1','192.168.1.1','100.65.0.1','::1','::ffff:127.0.0.1']) {
  assert.equal(isPublicAddress(address),false,address)
}
assert.equal(validateScreenshotUrl('github.com',{allowedHosts:'github.com'}),'https://github.com/')
assert.throws(()=>validateScreenshotUrl('http://127.0.0.1',{allowedHosts:'127.0.0.1'}),/public/)
assert.throws(()=>validateScreenshotUrl('https://evil.com',{allowedHosts:'github.com'}),/allowlist/)
assert.throws(()=>validateScreenshotUrl('https://github.com@evil.com',{allowedHosts:'github.com'}),/public/)
assert.throws(()=>validateScreenshotUrl('https://github.com',{allowedHosts:''}),/SSWEB_ALLOWED_HOSTS/)
await validatePublicHost('github.com',{resolver:async()=>[{address:'8.8.8.8'}]})
await assert.rejects(validatePublicHost('github.com',{resolver:async()=>[
  {address:'8.8.8.8'},{address:'10.0.0.2'}
]}),/unsafe/)

const canvas=createCanvas(72,72)
const ctx=canvas.getContext('2d')
ctx.fillStyle='red';ctx.fillRect(5,5,62,62)
const testPng=canvas.toBuffer('image/png')
const sticker=await combinedEmojiSticker(['❤️','🔥'],{
  fetcher:async url=>({ok:true,arrayBuffer:async()=>testPng}),
})
assert.ok(sticker.length>200)
assert.match(sticker.toString('ascii',0,4),/RIFF/)

const dir=await mkdtemp(join(tmpdir(),'night-extra-test-'))
try {
  const video=join(dir,'sample.mp4')
  await runMediaProcess(process.env.FFMPEG_PATH||'ffmpeg',[
    '-hide_banner','-loglevel','error','-y','-f','lavfi',
    '-i','testsrc2=size=320x180:rate=12:duration=7',
    '-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p',video,
  ],{timeoutMs:30000})
  const bytes=await readFile(video)
  const frames=await bestVideoFrames(bytes,2)
  assert.ok(frames.length>=1,'At least one frame extracted')
  assert.ok(frames.every(x=>x.buffer.length>1000))
  const reversed=await reverseClip(bytes,{kind:'video'})
  assert.ok(reversed.length>1000)
  const stable=await stabilizeVideo(bytes)
  assert.ok(stable.buffer.length>1000)
  assert.ok(['vidstab','deshake'].includes(stable.method))
} finally {
  await rm(dir,{recursive:true,force:true})
}

console.log('PASS seven extended-media commands, bounded FFmpeg processing, emoji art, EXIF privacy and screenshot URL safety')
