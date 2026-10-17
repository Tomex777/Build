import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { validTikTokMediaUrl, extractTikTokMedia, tryPublicTikTokFallback } from './utils/tiktok-fallback.js'

const link = 'https://vt.tiktok.com/ZSg2yBaXt/'
const request = { platform:'TikTok', url:link }
for (const bad of [
  'http://www.tikwm.com/file.mp4', 'https://127.0.0.1/file.mp4', 'https://localhost/file.mp4',
  'https://www.tikwm.com.evil.example/file.mp4', 'https://user:pass@www.tikwm.com/file.mp4',
  'https://www.tikwm.com:8443/file.mp4', 'file:///etc/passwd', 'javascript:alert(1)',
]) assert.equal(validTikTokMediaUrl(bad),null,bad)
assert.equal(validTikTokMediaUrl('/video/media/play/a.mp4','https://www.tikwm.com'),'https://www.tikwm.com/video/media/play/a.mp4')
assert.equal(extractTikTokMedia('siputzx',{status:true,data:{urls:['https://x.tiktokcdn.com/x.mp4']}})[0].ext,'mp4')
assert.equal(extractTikTokMedia('tikwm',{code:2,data:{play:'https://www.tikwm.com/media/a.mp4'}}).length,0)

const mp4 = Buffer.concat([Buffer.from([0,0,0,20]),Buffer.from('ftypisom'),Buffer.alloc(200)])
const requestLog = []
const mock = async url => {
  const addr = String(url)
  requestLog.push(addr)
  if (addr.includes('api.siputzx.my.id')) return new Response(JSON.stringify({status:false}),{headers:{'content-type':'application/json'}})
  if (addr.includes('tikwm.com/api/')) return new Response(JSON.stringify({code:0,data:{play:'/video/media/play/test.mp4'}}),{headers:{'content-type':'application/json'}})
  if (addr.includes('/video/media/play/test.mp4')) return new Response(mp4,{headers:{'content-type':'video/mp4'}})
  throw Error('unexpected network call')
}
const dir = await mkdtemp(join(tmpdir(),'night-tikwm-test-'))
try {
  const files = await tryPublicTikTokFallback(request,dir,{fetchImpl:mock})
  assert.equal(files.length,1)
  assert.equal(files[0].mime,'video/mp4')
  assert.deepEqual(await readFile(files[0].file),mp4)
  assert.equal(requestLog.filter(u=>u.includes('/api/')).length,2)
} finally { await rm(dir,{force:true,recursive:true}) }

const badDir = await mkdtemp(join(tmpdir(),'night-tikwm-bad-'))
try {
  const hostile = async url => {
    if (String(url).includes('/api/')) return new Response(JSON.stringify({code:0,status:true,data:{play:'https://127.0.0.1/internal',urls:['https://127.0.0.1/internal']}}))
    throw Error('should not request unknown media host')
  }
  await assert.rejects(tryPublicTikTokFallback(request,badDir,{fetchImpl:hostile}),/all public extractors/)
} finally { await rm(badDir,{force:true,recursive:true}) }
console.log('PASS Nexia-style public TikTok fallback and private-network blocking')
