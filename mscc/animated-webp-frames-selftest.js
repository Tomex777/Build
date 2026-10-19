import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { mediaToSticker, stickerToGif, stickerToVideo } from './utils/media-conversion.js'
import { extractAnimatedWebpFrames } from './utils/animated-webp-frames.js'
import { runMediaProcess } from './utils/extended-media.js'

const dir=await mkdtemp(join(tmpdir(),'night-animated-sticker-test-'))
try {
  const source=join(dir,'source.mp4')
  await runMediaProcess(process.env.FFMPEG_PATH||'ffmpeg',[
    '-hide_banner','-loglevel','error','-y',
    '-f','lavfi','-i','testsrc2=size=320x180:rate=6:duration=2',
    '-an','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p',source,
  ],{timeoutMs:18000})
  const animated=await mediaToSticker(await readFile(source),{
    animated:true,packName:'NIGHT TEST',publisher:'NIGHT',
  })
  assert.equal(animated.toString('ascii',0,4),'RIFF')
  assert.equal(animated.toString('ascii',8,12),'WEBP')
  const frames=await extractAnimatedWebpFrames(animated,dir)
  assert(frames.frames>=2,'Animated fixture must actually have multiple frames')
  assert.equal(frames.width,512)
  assert.equal(frames.height,512)
  const gif=await stickerToGif(animated)
  assert.match(gif.toString('ascii',0,6),/^GIF8[79]a$/)
  const video=await stickerToVideo(animated)
  assert.equal(video.toString('ascii',4,8),'ftyp')
  const converted=join(dir,'converted.mp4')
  const fs=await import('node:fs/promises')
  await fs.writeFile(converted,video)
  const json=await runMediaProcess(process.env.FFPROBE_PATH||'ffprobe',[
    '-v','error','-show_entries','format=duration:stream=codec_type,width,height',
    '-of','json',converted,
  ],{timeoutMs:18000})
  const info=JSON.parse(json)
  assert(info.streams.some(row=>row.codec_type==='video' && row.width===512 && row.height===512))
  assert(Number(info.format.duration)>0)
  assert(Number(info.format.duration)<10)
  await assert.rejects(extractAnimatedWebpFrames(Buffer.alloc(50),dir))
  console.log('PASS real animated WebP to multi-frame GIF + playable MP4 via composited frames')
} finally {await rm(dir,{recursive:true,force:true})}
