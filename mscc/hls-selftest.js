import { mkdtemp, readFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { parseM3u8, resolveM3u8, downloadHlsSegments } from './utils/media/hls.js'

const master = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720\n720/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1920x1080\n1080/index.m3u8\n'
const media = '#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:7\n#EXTINF:6,\nseg7.ts\n#EXTINF:6,\nseg8.ts\n'

const parsed = parseM3u8(master, 'https://example.test/master.m3u8')
if (parsed.variants.length !== 2 || parsed.variants[1].height !== 1080) throw new Error('variant parse failed')

const calls=[]
const resolved=await resolveM3u8({
  url:'https://example.test/master.m3u8',
  fetchText:async url => {
    calls.push(url)
    return url.endsWith('master.m3u8') ? master : media
  },
})
if (resolved.segments.length !== 2) throw new Error('segment resolve failed')
if (resolved.segments[0].sequence !== 7) throw new Error('media sequence failed')
if (!resolved.segments[0].url.endsWith('/1080/seg7.ts')) throw new Error('relative segment URL failed')

const root=await mkdtemp(join(tmpdir(),'mscc-hls-'))
const fetched=[]
const first=await downloadHlsSegments({
  segments:resolved.segments,
  directory:root,
  concurrency:2,
  fetchBytes:async url => {
    fetched.push(url)
    return Buffer.from(url.endsWith('seg7.ts') ? 'SEG7' : 'SEG8')
  },
})
if (first.completed !== 2 || first.resumed !== 0 || fetched.length !== 2) throw new Error('segment download failed')
if ((await readFile(join(root,'000000.ts'),'utf8')) !== 'SEG7') throw new Error('segment content mismatch')

const second=await downloadHlsSegments({
  segments:resolved.segments,
  directory:root,
  concurrency:2,
  fetchBytes:async () => { throw new Error('resume fetched existing segment') },
})
if (second.resumed !== 2 || second.completed !== 2) throw new Error('resume failed')

console.log('PASS generic HLS resolver + segment resume')
