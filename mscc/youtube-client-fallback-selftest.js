import assert from 'node:assert/strict'
import { mkdtemp, writeFile, chmod, mkdir, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { resolveYouTubeWithDlp, downloadYouTubeWithDlp } from './youtube-dlp.js'

const dir = await mkdtemp(join(tmpdir(), 'yt-dlp-client-fallback-'))
const binary = join(dir, 'fake-dlp')
const trace = join(dir, 'trace')
const script = `#!/usr/bin/env node
const {appendFileSync, writeFileSync} = require('node:fs')
const args = process.argv.slice(2)
const value = args[args.indexOf('--extractor-args') + 1] || ''
const profile = value.startsWith('youtube:player_client=') ? value.split('=')[1] : 'default'
appendFileSync(process.env.TEST_YT_TRACE, profile + '\\n')
if (profile !== 'tv,web_safari') {
  process.stderr.write("ERROR: Sign in to confirm you're not a bot.\\n")
  process.exit(1)
}
if (args.includes('--dump-single-json')) {
  console.log(JSON.stringify({
    title:'Test title',channel:'Test channel',
    formats:[{height:360,vcodec:'avc1',ext:'mp4',protocol:'https'}],
  }))
} else {
  const pattern = args[args.indexOf('-o') + 1]
  const dest = pattern.replace('%(ext)s', 'mp4')
  writeFileSync(dest, Buffer.alloc(2048,1))
  console.log(dest)
}
`
try {
  await writeFile(binary, script, {mode:0o700})
  await chmod(binary, 0o700)
  process.env.MSCC_YOUTUBE_YTDLP_BIN = binary
  process.env.TEST_YT_TRACE = trace
  const id = 'XEuaRjnuHlc'
  const resolved = await resolveYouTubeWithDlp(id)
  assert.equal(resolved.candidates[0].extractorProfile, 'tv,web_safari')
  const outputDir = join(dir, 'output')
  await mkdir(outputDir)
  const video = await downloadYouTubeWithDlp(id, 360, outputDir, 4096, resolved.candidates[0].extractorProfile)
  assert.equal(video.size, 2048)
  assert.deepEqual((await readFile(trace,'utf8')).trim().split('\\n'),
    ['default','tv,web_safari','tv,web_safari'])
  console.log('PASS YouTube bot challenge retries TV/Safari and preserves working client for download')
} finally {
  delete process.env.MSCC_YOUTUBE_YTDLP_BIN
  delete process.env.TEST_YT_TRACE
  await rm(dir,{recursive:true,force:true})
}
