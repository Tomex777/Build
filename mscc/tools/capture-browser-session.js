import { mkdir, writeFile, chmod } from 'node:fs/promises'
import { resolve } from 'node:path'

const hosts = process.argv.slice(2).map(value => String(value || '').trim().toLowerCase()).filter(Boolean)
if (!hosts.length) {
  console.error('Usage: node tools/capture-browser-session.js <host> [host ...]')
  process.exit(2)
}

const cdpBase = process.env.MSCC_CDP_HTTP || 'http://127.0.0.1:9222'
const stateRoot = process.env.MSCC_SOURCE_SESSION_DIR || '/var/lib/mscc/source-sessions'
const targets = await fetch(cdpBase + '/json/list', { signal:AbortSignal.timeout(5000) }).then(r => r.json())
const pages = targets.filter(target => target.type === 'page' && target.webSocketDebuggerUrl)

function hostMatches(domain, host) {
  const clean=String(domain || '').replace(/^\./,'').toLowerCase()
  return clean === host || clean.endsWith('.' + host) || host.endsWith('.' + clean)
}

async function connect(url) {
  const ws = new WebSocket(url)
  await new Promise((resolveOpen,reject) => {
    const timer=setTimeout(()=>reject(new Error('CDP WebSocket open timed out')),5000)
    ws.addEventListener('open',()=>{ clearTimeout(timer); resolveOpen() },{once:true})
    ws.addEventListener('error',event=>{ clearTimeout(timer); reject(new Error('CDP WebSocket error: '+String(event?.message || 'unknown'))) },{once:true})
  })
  let nextId=1
  const pending=new Map()
  ws.addEventListener('message',event=>{
    let msg
    try { msg=JSON.parse(String(event.data || '')) } catch { return }
    if (!msg.id || !pending.has(msg.id)) return
    const {resolve,reject}=pending.get(msg.id)
    pending.delete(msg.id)
    if (msg.error) reject(new Error(msg.error.message || JSON.stringify(msg.error)))
    else resolve(msg.result || {})
  })
  const send=(method,params={})=>new Promise((resolve,reject)=>{
    const id=nextId++
    pending.set(id,{resolve,reject})
    ws.send(JSON.stringify({id,method,params}))
    setTimeout(()=>{
      if (!pending.has(id)) return
      pending.delete(id)
      reject(new Error('CDP command timed out: '+method))
    },5000)
  })
  return {ws,send}
}

await mkdir(stateRoot,{recursive:true,mode:0o700})

for (const host of hosts) {
  const page = pages.find(target => {
    try { return hostMatches(new URL(target.url).hostname,host) } catch { return false }
  })
  if (!page) {
    console.error('No open Chromium page found for '+host)
    process.exitCode=3
    continue
  }

  const {ws,send}=await connect(page.webSocketDebuggerUrl)
  try {
    await send('Network.enable')
    const cookieResult=await send('Network.getAllCookies')
    const uaResult=await send('Runtime.evaluate',{expression:'navigator.userAgent',returnByValue:true})
    const cookies=(cookieResult.cookies || []).filter(cookie => hostMatches(cookie.domain,host))
    const userAgent=String(uaResult?.result?.value || '')
    const clearance=cookies.find(cookie=>cookie.name === 'cf_clearance')
    const payload={
      host,
      capturedAt:new Date().toISOString(),
      pageUrl:page.url,
      userAgent,
      cookies,
    }
    const file=resolve(stateRoot,host.replace(/[^a-z0-9.-]+/g,'_')+'.json')
    await writeFile(file,JSON.stringify(payload,null,2),{mode:0o600})
    await chmod(file,0o600)
    console.log(JSON.stringify({
      host,
      file,
      cookieNames:cookies.map(cookie=>cookie.name),
      hasCfClearance:Boolean(clearance),
      cfClearanceExpires:clearance?.expires > 0 ? new Date(clearance.expires*1000).toISOString() : null,
      userAgent,
    },null,2))
  } finally {
    ws.close()
  }
}
