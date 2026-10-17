import assert from 'node:assert/strict'
import { mkdtemp, writeFile, readFile, rm, chmod } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { parseSocialRequest, socialExtractorArgs, galleryExtractorArgs, prepareSocialDownload } from './utils/social-download.js'

const samples = [
  ['https://vm.tiktok.com/ZMabc/', 'TikTok'],
  ['https://vt.tiktok.com/ZSg2yBaXt/', 'TikTok'],
  ['https://www.tiktok.com/@test/video/763000000000000', 'TikTok'],
  ['https://www.instagram.com/reel/ABC123/?igsh=hello', 'Instagram'],
  ['https://www.instagram.com/p/ABC123/', 'Instagram'],
  ['https://fb.watch/abc123/', 'Facebook'],
  ['https://www.facebook.com/reel/1234', 'Facebook'],
  ['https://x.com/someone/status/123456789', 'X'],
  ['https://twitter.com/a/status/12345', 'X'],
  ['https://www.reddit.com/r/cats/comments/abc/a/', 'Reddit'],
  ['https://pin.it/abC123', 'Pinterest'],
  ['https://www.threads.net/@abc/post/Xyz123', 'Threads'],
  ['https://vimeo.com/123456', 'Vimeo'],
]
assert.equal(parseSocialRequest('Look at this https://www.instagram.com/p/abc123/ 😊').platform,'Instagram')
for (const [url,platform] of samples) assert.equal(parseSocialRequest(url).platform,platform)
for (const url of [
  'http://127.0.0.1/secret', 'https://localhost/path',
  'https://evil.com/?next=https://tiktok.com/post/111',
  'https://tiktok.com.evil.com/@u/video/123',
  'https://user:pass@instagram.com/p/abc',
  'https://instagram.com:8443/p/abc',
  'https://x.com/someone',
  'https://instagram.com/someone',
  'https://www.instagram.com/',
  'https://www.facebook.com/l.php?u=http://127.0.0.1',
  'https://www.tiktok.com/redirect/?target=http://127.0.0.1',
]) assert.throws(() => parseSocialRequest(url),url)

const sample = parseSocialRequest('https://www.instagram.com/p/abC12/')
const args = socialExtractorArgs(sample,'/tmp/night',1000)
assert.ok(args.includes('--ignore-config'))
assert.ok(args.includes('--playlist-items'))
assert.ok(args.includes('1:6'))
assert.ok(args.includes('--no-remote-components'))
assert.deepEqual(args.slice(-2),['--',sample.url])
assert.ok(!socialExtractorArgs(parseSocialRequest(samples[0][0]),'/tmp/night').includes('--yes-playlist'))
assert.ok(galleryExtractorArgs(sample,'/tmp/night').includes('--config-ignore'))
assert.ok(galleryExtractorArgs(sample,'/tmp/night').includes('1-6'))

// A fake local downloader validates the complete child-process/file lifecycle, no network required.
const dir = await mkdtemp(join(tmpdir(),'night-social-test-'))
const fake = join(dir,'fake-dlp')
await writeFile(fake, `#!/usr/bin/env node
const {writeFileSync}=require('node:fs');
const args=process.argv.slice(2);
const template=args[args.indexOf('-o')+1];
if(process.env.NIGHT_DL_FAKE_FAIL==='1') {console.error('login required');process.exit(1)}
if(args.includes('-D')) { const out=args[args.indexOf('-D')+1];writeFileSync(out+'/night-example.jpg',Buffer.from('TEST_JPEG'));process.exit(0) }
const name=template.replace('%(playlist_index|0)s','0').replace('%(id|media)s','test').replace('%(ext)s','mp4');
writeFileSync(name,Buffer.from('TEST_MP4'));
`,{mode:0o755})
await chmod(fake,0o755)
try {
  const result = await prepareSocialDownload('https://vm.tiktok.com/ZMabc/',{binary:fake})
  assert.equal(result.files.length,1)
  assert.equal(result.files[0].mime,'video/mp4')
  assert.equal((await readFile(result.files[0].file)).toString(),'TEST_MP4')
  const file = result.files[0].file
  await result.cleanup()
  await assert.rejects(readFile(file))
  const album = await prepareSocialDownload('https://www.instagram.com/p/ABC123/',{binary:fake})
  assert.equal(album.files.length,1)
  assert.equal(album.files[0].mime,'image/jpeg')
  await album.cleanup()
  await assert.rejects(prepareSocialDownload('https://www.reddit.com/r/cats/comments/abc/a/',{binary:'/not-a-real-dlp'}),/not installed/)
  await assert.rejects(prepareSocialDownload('https://vm.tiktok.com/ZMabc/',{binary:'/not-a-real-dlp',fetchImpl:async () => { throw new Error('offline test') }}),/all public extractors/)
  const previous = process.env.NIGHT_DL_FAKE_FAIL
  process.env.NIGHT_DL_FAKE_FAIL = '1'
  try { await assert.rejects(prepareSocialDownload('https://vm.tiktok.com/ZMabc/',{binary:fake,fetchImpl:async () => { throw new Error('offline test') }}),/all public extractors/) }
  finally { if (previous === undefined) delete process.env.NIGHT_DL_FAKE_FAIL; else process.env.NIGHT_DL_FAKE_FAIL = previous }
} finally { await rm(dir,{ recursive:true,force:true }) }
console.log('PASS NIGHT public social downloader parsing, safety and lifecycle')
