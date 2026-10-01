import { spawnSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const UA = process.env.MSCC_SOURCE_PROBE_UA ||
  'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36'
const TOR_PROXY = process.env.MSCC_TOR_PROXY || 'socks5h://127.0.0.1:9050'
const TIMEOUT = Number(process.env.MSCC_SOURCE_PROBE_TIMEOUT || 35)

const now = Math.floor(Date.now() / 1000)
const probes = [
  {
    id:'tor-check',
    url:'https://check.torproject.org/api/ip',
    expect:'json',
  },
  {
    id:'animepahe-pw-api',
    url:`https://animepahe.pw/api?m=search&q=${encodeURIComponent('Bleach ' + now)}&page=1`,
    expect:'json',
  },
  {
    id:'animepahe-com-api',
    url:`https://animepahe.com/api?m=search&q=${encodeURIComponent('Bleach ' + now)}&page=1`,
    expect:'json',
  },
  {
    id:'animepahe-org-api',
    url:`https://animepahe.org/api?m=search&q=${encodeURIComponent('Bleach ' + now)}&page=1`,
    expect:'json',
  },
  {
    id:'kayoanime-search',
    url:'https://kayoanime.com/?s=Re%3AZERO',
    expect:'html',
  },
  {
    id:'animesogo-home',
    url:'https://animesogo.to/',
    expect:'html',
  },
  {
    id:'animesogo-search',
    url:'https://animesogo.to/filter?keyword=Bleach&page=1',
    expect:'html',
  },
  {
    id:'animeonsen-home',
    url:'https://www.animeonsen.xyz/',
    expect:'html',
  },
  {
    id:'animeonsen-api',
    url:'https://api.animeonsen.xyz/v4/content/index?start=0&limit=1',
    expect:'json',
  },
]

function parseHeaders(raw) {
  const blocks = String(raw || '').split(/\r?\n\r?\n/).filter(Boolean)
  const block = blocks.at(-1) || ''
  const lines = block.split(/\r?\n/)
  const status = Number(/HTTP\/\S+\s+(\d+)/i.exec(lines[0] || '')?.[1] || 0)
  const headers = {}
  for (const line of lines.slice(1)) {
    const cut = line.indexOf(':')
    if (cut < 1) continue
    headers[line.slice(0,cut).trim().toLowerCase()] = line.slice(cut+1).trim()
  }
  return { status, headers }
}

function titleOf(body) {
  return String(body || '').match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]
    ?.replace(/<[^>]+>/g,' ')
    .replace(/\s+/g,' ')
    .trim()
    .slice(0,140) || ''
}

function classify({ status, headers, body, expect }) {
  const ct = String(headers['content-type'] || '')
  const challengeTitle = /just a moment|attention required/i.test(titleOf(body))
  const challengeMarkers = /\/cdn-cgi\/challenge-platform\/|cf-chl-|cf_chl_|challenges\.cloudflare\.com\/turnstile/i.test(body)
  const challenge = challengeTitle
    || ((status === 403 || status === 503) && (challengeMarkers || Boolean(headers['cf-ray'])))
  if (challenge) return 'cloudflare-challenge'
  if (status === 429) return 'rate-limited'
  if (status >= 500) return 'server-error'
  if (status >= 400) return 'http-error'
  if (expect === 'json') {
    if (/json/i.test(ct)) {
      try {
        const value=JSON.parse(body)
        if (value && typeof value === 'object') return 'usable-json'
      } catch {}
    }
    return 'unexpected-non-json'
  }
  return status >= 200 && status < 400 ? 'reachable-html' : 'unknown'
}

function curlProbe(url, { tor }) {
  const args = [
    '--silent','--show-error','--location',
    '--max-time',String(TIMEOUT),
    '--connect-timeout','12',
    '--compressed',
    '--user-agent',UA,
    '--header','Accept: application/json,text/html;q=0.9,*/*;q=0.8',
    '--dump-header','-',
    '--output','-',
    '--write-out','\n__MSCC_META__%{http_code}|%{time_starttransfer}|%{time_total}|%{remote_ip}|%{url_effective}',
  ]
  if (tor) args.push('--proxy',TOR_PROXY)
  args.push(url)
  const began=Date.now()
  const result=spawnSync('curl',args,{encoding:'utf8',timeout:(TIMEOUT+8)*1000,maxBuffer:3*1024*1024})
  const stdout=String(result.stdout || '')
  const marker='\n__MSCC_META__'
  const cut=stdout.lastIndexOf(marker)
  const payload=cut >= 0 ? stdout.slice(0,cut) : stdout
  const meta=cut >= 0 ? stdout.slice(cut+marker.length).trim() : ''
  const sep=payload.lastIndexOf('\r\n\r\n') >= 0 ? '\r\n\r\n' : '\n\n'
  const headCut=payload.lastIndexOf(sep)
  const headersRaw=headCut >= 0 ? payload.slice(0,headCut) : ''
  const body=headCut >= 0 ? payload.slice(headCut+sep.length) : payload
  const parsed=parseHeaders(headersRaw)
  const [httpCode,ttfb,total,remoteIp,effectiveUrl]=meta.split('|')
  return {
    ok:result.status === 0,
    curlExit:result.status,
    signal:result.signal || '',
    stderr:String(result.stderr || '').trim().slice(-500),
    status:parsed.status || Number(httpCode || 0),
    contentType:parsed.headers['content-type'] || '',
    server:parsed.headers.server || '',
    cfRay:Boolean(parsed.headers['cf-ray']),
    ttfbSeconds:Number(ttfb || 0),
    totalSeconds:Number(total || ((Date.now()-began)/1000)),
    remoteIp:remoteIp || '',
    effectiveUrl:effectiveUrl || url,
    title:titleOf(body),
    bodyBytes:Buffer.byteLength(body),
    bodyPreview:String(body).replace(/\s+/g,' ').slice(0,220),
    rawBody:body,
    headers:parsed.headers,
  }
}

const modes = process.argv.includes('--direct-only')
  ? [{name:'direct',tor:false}]
  : process.argv.includes('--tor-only')
    ? [{name:'tor',tor:true}]
    : [{name:'direct',tor:false},{name:'tor',tor:true}]

const report = {
  at:new Date().toISOString(),
  torProxy:TOR_PROXY.replace(/\/\/[^@]+@/,'//***@'),
  userAgent:UA,
  modes:{},
}

for (const mode of modes) {
  report.modes[mode.name]=[]
  for (const probe of probes) {
    const result=curlProbe(probe.url,{tor:mode.tor})
    const classification=classify({...result,body:result.rawBody,expect:probe.expect})
    let summary=null
    if (classification === 'usable-json') {
      try {
        const j=JSON.parse(result.rawBody)
        summary={
          keys:Object.keys(j).slice(0,12),
          itemCount:Array.isArray(j?.data) ? j.data.length
            : Array.isArray(j?.items) ? j.items.length
              : Array.isArray(j?.content) ? j.content.length
                : null,
          isTor:typeof j?.IsTor === 'boolean' ? j.IsTor : undefined,
          ip:probe.id === 'tor-check' ? j?.IP : undefined,
        }
      } catch {}
    }
    const clean={...result,classification,summary}
    delete clean.rawBody
    delete clean.headers
    report.modes[mode.name].push({id:probe.id,url:probe.url,...clean})
    console.log([
      mode.name.padEnd(6),
      probe.id.padEnd(22),
      String(clean.status).padEnd(4),
      classification.padEnd(24),
      ('ttfb=' + clean.ttfbSeconds.toFixed(2) + 's').padEnd(13),
      clean.title || clean.contentType || clean.stderr || '',
    ].join(' | '))
  }
}

const out=process.env.MSCC_SOURCE_PROBE_OUTPUT
if (out) {
  writeFileSync(out,JSON.stringify(report,null,2))
  console.log('wrote '+out)
}

const torCheck=report.modes.tor?.find(x=>x.id==='tor-check')
if (report.modes.tor && torCheck?.classification !== 'usable-json') {
  process.exitCode=2
}
