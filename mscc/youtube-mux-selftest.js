import { mkdir, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { muxMediaFiles } from './youtube-video.js'

function run(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio:['ignore','ignore','pipe'] })
    let errorText = ''
    child.stderr.on('data', chunk => { errorText += String(chunk || '') })
    child.once('error', reject)
    child.once('close', code => code === 0 ? resolve() : reject(new Error(errorText || command + ' failed')))
  })
}

const dir = join(tmpdir(), 'mscc-youtube-mux-test-' + randomUUID())
await mkdir(dir, { recursive:true })
const video = join(dir, 'video.mp4')
const audio = join(dir, 'audio.m4a')
const output = join(dir, 'output.mp4')

try {
  await run('ffmpeg', [
    '-hide_banner','-loglevel','error','-y',
    '-f','lavfi','-i','color=c=black:s=160x90:r=25:d=1',
    '-an','-c:v','libx264','-pix_fmt','yuv420p',
    video,
  ])
  await run('ffmpeg', [
    '-hide_banner','-loglevel','error','-y',
    '-f','lavfi','-i','sine=frequency=440:duration=1',
    '-vn','-c:a','aac',
    audio,
  ])

  await muxMediaFiles(video, audio, output)
  const info = await stat(output)
  if (!info.size) throw new Error('YouTube mux helper produced an empty output file')
  console.log('PASS YouTube FFmpeg mux helper: ' + info.size + ' bytes')
} finally {
  await rm(dir, { recursive:true, force:true })
}
