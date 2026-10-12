import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile, chmod, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import {
  isYouTubeAccessChallenge,
  youtubeChallengeMessage,
  resolveYouTubeWithDlp,
  downloadYouTubeWithDlp,
} from './youtube-dlp.js'

const id = 'dQw4w9WgXcQ'
assert.equal(isYouTubeAccessChallenge(new Error("Sign in to confirm you’re not a bot")), true)
assert.equal(isYouTubeAccessChallenge(new Error('Video unavailable')), false)
assert.match(youtubeChallengeMessage(id), /MSCC server/)
assert.match(youtubeChallengeMessage(id), /youtube.com\/watch\?v=dQw4w9WgXcQ/)

const folder = await mkdtemp(join(tmpdir(), 'mscc-yt-dlp-test-'))
try {
  const binary = join(folder, 'fake-yt-dlp')
  const mock = `#!/usr/bin/env node
const { writeFileSync } = require('node:fs')
const { resolve } = require('node:path')
const args = process.argv.slice(2)
if (args.includes('--dump-single-json')) {
  console.log(JSON.stringify({title:'Test Video',channel:'Test Channel',formats:[
    {height:360,vcodec:'avc1'},{height:720,vcodec:'avc1'},{height:720,vcodec:'none'}
  ]}))
} else {
  const pattern = args[args.indexOf('-o') + 1]
  const path = resolve(pattern.replace('%(ext)s','mp4'))
  writeFileSync(path, Buffer.alloc(1024, 1))
  console.log(path)
}
`
  await writeFile(binary, mock, { mode:0o700 })
  await chmod(binary, 0o700)
  process.env.MSCC_YOUTUBE_YTDLP_BIN = binary

  const result = await resolveYouTubeWithDlp(id)
  assert.equal(result.details.channel, 'Test Channel')
  assert.deepEqual(result.candidates.map(item => item.height), [360, 720])
  assert(result.candidates.every(item => item.source === 'yt-dlp'))

  const output = join(folder, 'output')
  await mkdir(output)
  const downloaded = await downloadYouTubeWithDlp(id, 720, output, 2000)
  assert.equal(downloaded.extension, 'mp4')
  assert.equal(downloaded.size, 1024)
  await assert.rejects(downloadYouTubeWithDlp(id, 720, output, 500), /exceeds the MSCC download limit/)
  await assert.rejects(downloadYouTubeWithDlp('bad', 720, output, 2000), /Invalid YouTube video ID/)
  console.log('PASS isolated yt-dlp metadata, quality, download, cap and challenge handling')
} finally {
  delete process.env.MSCC_YOUTUBE_YTDLP_BIN
  await rm(folder, { recursive:true, force:true })
}
