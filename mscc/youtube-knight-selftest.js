import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import {
  isPublicIpv4,
  checkedMediaUrl,
  downloadFromKnightProviders,
  probeKnightProviders,
} from './youtube-knight-providers.js'

const id = 'XEuaRjnuHlc'
for (const address of ['127.0.0.1','10.1.2.3','172.16.5.7','192.168.1.22',
  '169.254.169.254','100.64.0.1','0.0.0.0','203.0.113.1','198.51.100.1']) {
  assert.equal(isPublicIpv4(address),false,'private IP accepted: '+address)
}
for (const address of ['8.8.8.8','1.1.1.1','142.250.190.78']) {
  assert.equal(isPublicIpv4(address),true,'public IP rejected: '+address)
}
for (const url of [
  'http://cdn.example.com/media.mp4',
  'https://127.0.0.1/file.mp4',
  'https://169.254.169.254/latest/meta-data',
  'https://user:pass@example.com/file',
  'https://media.local/file',
  'https://media.example.com:8080/file',
  'file:///etc/passwd',
]) {
  assert.throws(()=>checkedMediaUrl(url),/unsafe|Invalid/i, 'unsafe URL accepted: '+url)
}
assert.equal(checkedMediaUrl('https://cdn.example.com/123.mp4').protocol,'https:')

const dir = await mkdtemp(join(tmpdir(),'knight-source-selftest-'))
const providers = [
  {name:'eliteprotech', endpoint:'https://eliteprotech-apis.zone.id/ytdown',
    params: url => ({url,format:'mp4'}),
    parse: data => data?.downloadURL ? {url:data.downloadURL,title:data.title} : null},
  {name:'yupra', endpoint:'https://api.yupra.my.id/api/downloader/ytmp4',
    params: url => ({url}),
    parse: data => data?.data?.download_url ? {url:data.data.download_url,title:data.data.title} : null},
]
try {
  const calls=[]
  const result = await downloadFromKnightProviders(id,dir,4096,{
    providers,
    requestJson: async url => {
      calls.push(['metadata',url])
      if (url.includes('eliteprotech')) return { downloadURL:'http://localhost/malicious' }
      return {data:{download_url:'https://cdn.example.com/approved.mp4',title:'Museum floor'}}
    },
    downloadFile: async (url,file,cap) => {
      calls.push(['download',url,file,cap])
      return 1024
    },
    inspectHeight:async()=>360,
  })
  assert.equal(result.provider,'yupra')
  assert.equal(result.height,360)
  assert.equal(result.title,'Museum floor')
  assert.equal(result.size,1024)
  assert.equal(calls.filter(([type])=>type==='metadata').length,2)
  assert.equal(calls.filter(([type])=>type==='download').length,1)
  assert.match(calls[1][1],/watch%3Fv%3DXEuaRjnuHlc/)
  assert.equal(calls[2][1],'https://cdn.example.com/approved.mp4')

  await assert.rejects(downloadFromKnightProviders(id,dir,4096,{
    providers,
    requestJson:async()=>({data:{download_url:'https://127.0.0.1/private'}}),
  }),/no external media source returned/)

  const diagnostics = await probeKnightProviders(id,{
    providers,
    requestJson:async url => url.includes('eliteprotech')
      ? {downloadURL:'https://cdn.example.com/signed-token-secret',title:'Secret title'}
      : {data:{download_url:'https://127.0.0.1/private'}},
  })
  assert.deepEqual(diagnostics,[
    {provider:'eliteprotech',result:'link_resolved'},
    {provider:'yupra',result:'unavailable'},
  ])
  assert.equal(JSON.stringify(diagnostics).includes('signed-token-secret'),false)

  process.env.MSCC_YOUTUBE_KNIGHT_FALLBACK = '0'
  await assert.rejects(downloadFromKnightProviders(id,dir,4096,{
    providers,requestJson:async()=>{ throw new Error('should never call API') },
  }),/disabled/)
  delete process.env.MSCC_YOUTUBE_KNIGHT_FALLBACK
  console.log('PASS KnightBot provider fallback, input/SSRF protection, provider failover, external height, opt-out')
} finally {
  delete process.env.MSCC_YOUTUBE_KNIGHT_FALLBACK
  await rm(dir,{recursive:true,force:true})
}
