import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { validTikTokMediaUrl, canonicalTikTokUrl, resolveTikTokShortUrl, extractTikTokMedia, extractTikTokMediaVariants, getRuhendTikTokData, tryPublicTikTokFallback } from './utils/tiktok-fallback.js'

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
assert.deepEqual(extractTikTokMediaVariants('ruhend',{data:{video_hd:'https://v16.tiktokcdn-us.com/hd.mp4',video:'https://v16.tiktokcdn-us.com/sd.mp4',video_wm:'https://v16.tiktokcdn-us.com/wm.mp4'}}).map(group=>group[0].url),[
  'https://v16.tiktokcdn-us.com/hd.mp4',
  'https://v16.tiktokcdn-us.com/sd.mp4',
  'https://v16.tiktokcdn-us.com/wm.mp4',
])
assert.equal(extractTikTokMediaVariants('ruhend',{data:{video_hd:'https://127.0.0.1/secret.mp4'}}).length,0)
assert.equal((await getRuhendTikTokData(link,{ttdl:async url=>({video:url})})).video,link)
const noScraper={ttdl:async()=>({})}

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
  const files = await tryPublicTikTokFallback(request,dir,{fetchImpl:mock,scraperImpl:noScraper})
  assert.equal(files.length,1)
  assert.equal(files[0].mime,'video/mp4')
  assert.deepEqual(await readFile(files[0].file),mp4)
  assert.equal(requestLog.filter(u=>u.includes('/api/')).length,2)
} finally { await rm(dir,{force:true,recursive:true}) }

const retryDir = await mkdtemp(join(tmpdir(),'night-tikwm-retry-'))
try {
  let triedPost = false
  let triedGet = false
  const retryFetch = async (url, init = {}) => {
    const addr = String(url)
    if (addr.includes('api.siputzx.my.id')) return new Response(JSON.stringify({status:false}))
    if (addr.includes('tikwm.com/api/')) {
      if (init.method === 'POST') {
        triedPost = true
        return new Response('blocked',{status:403})
      }
      triedGet = true
      return new Response(JSON.stringify({code:0,data:{play:'/video/media/play/test.mp4'}}))
    }
    if (addr.includes('/video/media/play/test.mp4')) return new Response(mp4,{headers:{'content-type':'video/mp4'}})
    throw Error('unexpected request')
  }
  const files = await tryPublicTikTokFallback(request,retryDir,{fetchImpl:retryFetch,scraperImpl:noScraper})
  assert.equal(files.length,1)
  assert.ok(triedPost && triedGet,'TikWM POST must degrade to GET')
} finally { await rm(retryDir,{force:true,recursive:true}) }

const badDir = await mkdtemp(join(tmpdir(),'night-tikwm-bad-'))
try {
  const hostile = async url => {
    if (String(url).includes('/api/')) return new Response(JSON.stringify({code:0,status:true,data:{play:'https://127.0.0.1/internal',urls:['https://127.0.0.1/internal']}}))
    throw Error('should not request unknown media host')
  }
  await assert.rejects(tryPublicTikTokFallback(request,badDir,{fetchImpl:hostile,scraperImpl:noScraper}),/all public extractors/)
} finally { await rm(badDir,{force:true,recursive:true}) }
const restrictedDir=await mkdtemp(join(tmpdir(),'night-tiktok-badrequest-'))
try {
  await assert.rejects(tryPublicTikTokFallback({platform:'TikTok',url:'https://example.com/video/1'},restrictedDir,{
    fetchImpl:async()=>{throw Error('must not send a request')},scraperImpl:noScraper,
  }),/official HTTPS TikTok/)
} finally {await rm(restrictedDir,{force:true,recursive:true})}
console.log('PASS Nexia-style public TikTok fallback and private-network blocking')

const newerLink = 'https://vt.tiktok.com/ZSgMh8so3/'
const canonical = 'https://www.tiktok.com/@night/video/7630000000000000000'
assert.equal(canonicalTikTokUrl(canonical+'?utm_source=share'),canonical)
assert.equal(canonicalTikTokUrl('https://www.tiktok.com/login?next=/@night/video/7630000000000000000'),null)
assert.equal(validTikTokMediaUrl('https://v16.tiktokcdn-us.com/path/video.mp4'),'https://v16.tiktokcdn-us.com/path/video.mp4')
assert.equal(extractTikTokMediaVariants('tikwm',{code:0,data:{hdplay:'https://www.tikwm.com/bad.mp4',play:'https://www.tikwm.com/good.mp4'}}).length,2)

const seen = []
const expansionFetch = async (url, init={}) => {
  const address = String(url)
  seen.push(address)
  if (address === newerLink) return new Response('',{status:302,headers:{location:canonical+'?share_item_id=1'}})
  if (address.includes('api.siputzx.my.id')) return new Response(JSON.stringify({status:false}))
  if (address.includes('tikwm.com/api/')) {
    return new Response(JSON.stringify({code:0,data:{
      hdplay:'https://www.tikwm.com/video/bad.mp4',
      play:'https://v16.tiktokcdn-us.com/video/good.mp4',
    }}))
  }
  if (address.includes('/video/bad.mp4')) return new Response('blocked',{status:403})
  if (address.includes('/video/good.mp4')) return new Response(mp4,{headers:{'content-type':'video/mp4'}})
  throw Error('unexpected request: '+address)
}
const normalizedDir = await mkdtemp(join(tmpdir(),'night-tiktok-short-'))
try {
  assert.equal(await resolveTikTokShortUrl(newerLink,expansionFetch),canonical)
  const files = await tryPublicTikTokFallback({platform:'TikTok',url:newerLink},normalizedDir,{fetchImpl:expansionFetch,scraperImpl:noScraper})
  assert.equal(files.length,1,'HD fail must fall back to SD, not send two video variants')
  assert.deepEqual(await readFile(files[0].file),mp4)
  assert.ok(seen.some(u => u.includes(encodeURIComponent(canonical))),'API must receive canonical video URL')
} finally { await rm(normalizedDir,{force:true,recursive:true}) }

const unsafeSeen = []
const unsafeResolver = async url => {
  unsafeSeen.push(String(url))
  return new Response('',{status:302,headers:{location:'https://127.0.0.1/private'}})
}
assert.equal(await resolveTikTokShortUrl(newerLink,unsafeResolver),null)
assert.deepEqual(unsafeSeen,[newerLink],'short-link expansion must not follow redirects to private hosts')
// Nexia's real third fallback: yt-dlp (elsewhere) -> Siputzx ->
// ruhend-scraper ttdl flat result. HD is preferred, then SD, then WM.
const nexiaDir=await mkdtemp(join(tmpdir(),'night-nexia-parity-'))
try {
  const used=[]
  const nexiaFetch=async address=>{
    const url=String(address)
    used.push(url)
    if (url.includes('api.siputzx.my.id')) return new Response(JSON.stringify({status:false}))
    if (url.endsWith('/nexia-hd.mp4')) return new Response('expired',{status:403})
    if (url.endsWith('/nexia-sd.mp4')) return new Response(mp4,{headers:{'content-type':'video/mp4'}})
    throw Error('TikWM must not be used if Nexia extractor provides valid media')
  }
  const files=await tryPublicTikTokFallback(request,nexiaDir,{fetchImpl:nexiaFetch,scraperImpl:{
    ttdl:async url=>{
      assert.equal(url,link)
      return {title:'Nexia parity',video_hd:'https://v16.tiktokcdn-us.com/nexia-hd.mp4',
        video:'https://v16.tiktokcdn-us.com/nexia-sd.mp4',
        video_wm:'https://v16.tiktokcdn-us.com/nexia-wm.mp4'}
    }
  }})
  assert.equal(files.length,1,'quality variants are retries, not separate WhatsApp videos')
  assert.equal(files[0].title,'Nexia parity')
  assert.deepEqual(await readFile(files[0].file),mp4)
  assert.ok(used.some(x=>x.endsWith('/nexia-hd.mp4')))
  assert.ok(used.some(x=>x.endsWith('/nexia-sd.mp4')))
  assert.equal(used.some(x=>x.includes('tikwm.com')),false)
} finally {await rm(nexiaDir,{force:true,recursive:true})}

console.log('PASS TikTok share-link expansion, video quality retry and CDN allowlist')
