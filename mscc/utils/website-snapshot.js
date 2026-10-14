import { lookup } from 'node:dns/promises'
import { isIP } from 'node:net'
import { existsSync } from 'node:fs'

const DEFAULT_TIMEOUT=16000

function publicIpv4(address) {
  const a=address.split('.').map(Number)
  if (a.length!==4 || a.some(x=>!Number.isInteger(x)||x<0||x>255)) return false
  const [x,y,z]=a
  return !(x===0||x===10||x===127||x>=224||x===169&&y===254||
    x===172&&y>=16&&y<=31||x===192&&y===168||
    x===100&&y>=64&&y<=127||x===198&&[18,19].includes(y)||
    x===192&&y===0&&z===0||x===192&&y===0&&z===2||
    x===198&&y===51&&z===100||x===203&&y===0&&z===113||
    x===255)
}

export function isPublicAddress(ip) {
  // IPv6 is not enabled for the screenshot browser: fail closed rather than
  // incorrectly allow mapped IPv4, unique-local or metadata IPv6 endpoints.
  return isIP(ip)===4 && publicIpv4(ip)
}

function allowlisted(host, list) {
  return list.some(domain=>host===domain||host.endsWith('.'+domain))
}

export function validateScreenshotUrl(raw,{allowedHosts=process.env.SSWEB_ALLOWED_HOSTS||''}={}) {
  const input=String(raw||'').trim().slice(0,2000)
  if (!input) throw new Error('Use .ssweb https://example.com.')
  if (input.length>1500) throw new Error('URL too long.')
  const prepared=/^https?:\/\//i.test(input) ? input : 'https://'+input
  let parsed
  try { parsed=new URL(prepared) } catch { throw new Error('Invalid website URL.') }
  if(!['https:','http:'].includes(parsed.protocol) || parsed.username || parsed.password ||
    !parsed.hostname.includes('.') || parsed.hostname.endsWith('.') ||
    isIP(parsed.hostname) || /(?:^|\.)(?:localhost|local|internal|test|invalid|onion)$/.test(parsed.hostname) ||
    (parsed.port && !['80','443'].includes(parsed.port))) {
    throw new Error('Only ordinary public HTTP/HTTPS websites can be captured.')
  }
  const hosts=String(allowedHosts||'').split(',').map(x=>x.trim().toLowerCase()).filter(x=>/^[a-z0-9.-]+\.[a-z]{2,}$/.test(x))
  if(!hosts.length) throw new Error('Website screenshots require SSWEB_ALLOWED_HOSTS to be configured by the Night operator.')
  if (!allowlisted(parsed.hostname.toLowerCase(),hosts)) throw new Error('This website is not on Night’s screenshot allowlist.')
  parsed.hash=''
  return parsed.toString()
}

export async function validatePublicHost(hostname,{resolver=lookup}={}) {
  const addresses=await resolver(hostname,{all:true,family:4})
  if (!Array.isArray(addresses)||!addresses.length||
    addresses.some(x=>!isPublicAddress(String(x.address||'')))) {
    throw new Error('Website resolved to an unsafe or unavailable IP address.')
  }
  return true
}

function browserExecutable() {
  const candidates=[
    process.env.SSWEB_CHROME_PATH,
    process.env.CHROMIUM_PATH,
    '/usr/bin/chromium',
    '/usr/bin/google-chrome',
    '/opt/google/chrome/chrome',
    '/opt/mscc/tools/chromium',
  ].filter(Boolean)
  const match=candidates.find(path=>existsSync(path))
  if(!match) throw new Error('Chromium is not installed. Configure SSWEB_CHROME_PATH on the Night server.')
  return match
}

export async function captureWebsite(raw,{allowedHosts,loadBrowser,resolveHost=validatePublicHost}={}) {
  const site=validateScreenshotUrl(raw,{allowedHosts})
  await resolveHost(new URL(site).hostname)
  const puppeteer=loadBrowser ? await loadBrowser() : (await import('puppeteer-core')).default
  const options={
    headless:true,
    executablePath:browserExecutable(),
    args:['--disable-dev-shm-usage','--disable-background-networking','--disable-extensions',
      ...(process.env.SSWEB_NO_SANDBOX==='1' ? ['--no-sandbox'] : [])],
    timeout:10000,
  }
  let browser
  try {
    browser=await puppeteer.launch(options)
    const page=await browser.newPage()
    await page.setViewport({width:1200,height:800,deviceScaleFactor:1})
    await page.setJavaScriptEnabled(process.env.SSWEB_ENABLE_JS==='1')
    await page.setRequestInterception(true)
    let seen=0
    const mainHost=new URL(site).hostname.toLowerCase()
    page.on('request',request=>{
      void (async()=>{
        const dest=request.url()
        if (++seen > 90 || !['GET','HEAD'].includes(request.method())) return request.abort()
        const url=new URL(dest)
        if (!['https:','http:'].includes(url.protocol)||url.username||url.password||
          isIP(url.hostname)||(!url.hostname.includes('.'))||url.port&& !['80','443'].includes(url.port)) {
          return request.abort()
        }
        // Never follow a top-level redirect off the originally allowlisted host.
        if (request.isNavigationRequest() && request.frame()===page.mainFrame()) {
          const host=url.hostname.toLowerCase()
          if (host!==mainHost && !host.endsWith('.'+mainHost)) return request.abort()
        }
        await resolveHost(url.hostname)
        return request.continue()
      })().catch(()=>request.abort().catch(()=>{}))
    })
    page.on('dialog',dialog=>{void dialog.dismiss().catch(()=>{})})
    await page.goto(site,{waitUntil:'domcontentloaded',timeout:DEFAULT_TIMEOUT})
    const output=Buffer.from(await page.screenshot({type:'jpeg',quality:74,fullPage:false}))
    if(!output.length||output.length>5*1024*1024) throw new Error('Screenshot was empty or too large.')
    return {buffer:output,url:site}
  } finally {
    await browser?.close().catch(()=>{})
  }
}
