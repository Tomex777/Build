const { createHash, randomBytes, sign, createPrivateKey } = require('node:crypto')
const COOKIE = '__Host-night_play'
const ACTIONS = new Set(['status','link','cancel','logout','profile','delete'])
const RPC_PATH = '/api/night-play/rpc'
const hash = value => createHash('sha256').update(value).digest('hex')
const message = (timestamp, nonce, body) => ['night-play-v1',timestamp,nonce,'POST',RPC_PATH,hash(body)].join('\n')

function createHandler({ env = process.env, fetcher = fetch, now = Date.now } = {}) {
  return async (req, res) => {
    const send = (status, body) => {
      res.setHeader('Content-Type','application/json; charset=utf-8')
      res.setHeader('Cache-Control','no-store')
      res.setHeader('X-Content-Type-Options','nosniff')
      res.statusCode = status
      res.end(JSON.stringify(body))
    }
    if (!['GET','POST'].includes(req.method)) { res.setHeader('Allow','GET, POST'); return send(405,{error:'Method not allowed.'}) }
    const origin = env.NIGHT_PLAY_ORIGIN
    if (!origin || !env.NIGHT_PLAY_MSCC_URL || !env.NIGHT_PLAY_SIGNING_KEY) return send(503,{error:'NIGHT connection is being updated. Please try again shortly.'})
    // Mutations accept only the actual site's browser origin, including logout.
    // Neither forwarded host headers nor a browser-supplied backend URL are trusted.
    if (req.method === 'POST' && (req.headers.origin !== origin || String(req.headers['sec-fetch-site'] || 'same-origin') === 'cross-site')) {
      return send(403,{error:'Open NIGHT Play directly to connect your account.'})
    }
    let action = 'status', displayName
    if (req.method === 'POST') {
      if (!String(req.headers['content-type'] || '').toLowerCase().startsWith('application/json')) return send(415,{error:'JSON request required.'})
      try {
        let body = req.body
        if (body === undefined) {
          const chunks=[]; let size=0
          for await (const chunk of req) {
            size+=Buffer.byteLength(chunk)
            if (size>2048) return send(413,{error:'Request too large.'})
            chunks.push(Buffer.from(chunk))
          }
          body=Buffer.concat(chunks).toString('utf8')
        }
        if (typeof body === 'string' || Buffer.isBuffer(body)) {
          if (Buffer.byteLength(body)>2048) return send(413,{error:'Request too large.'})
          body=JSON.parse(String(body))
        }
        if (!body || typeof body !== 'object' || Array.isArray(body) || JSON.stringify(body).length>2048) return send(400,{error:'Invalid request.'})
        action=body.action; displayName=typeof body.displayName==='string'?body.displayName.slice(0,28):undefined
      } catch { return send(400,{error:'Invalid request.'}) }
    }
    if (!ACTIONS.has(action) || (req.method==='POST' && action==='status')) return send(400,{error:'Unknown account action.'})
    const match=String(req.headers.cookie || '').match(/(?:^|;\s*)__Host-night_play=([A-Za-z0-9_-]{43})(?:;|$)/)
    let session=match?.[1] || ''
    if (!session && action==='link') session=randomBytes(32).toString('base64url')
    if (!session && action==='status') {
      // A cheap health check avoids minting anonymous database rows on page views.
      action='health'
    }
    try {
      const backend = new URL(env.NIGHT_PLAY_MSCC_URL)
      if (backend.protocol!=='https:' || backend.pathname!=='/' || backend.username || backend.password || backend.search || backend.hash) throw new Error('Invalid backend configuration')
      const client = hash(String(req.headers['x-vercel-forwarded-for'] || req.headers['x-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim())
      const raw=JSON.stringify({action,session,client,displayName})
      const timestamp=String(now()), nonce=randomBytes(24).toString('base64url')
      const signature=sign(null,Buffer.from(message(timestamp,nonce,raw)),createPrivateKey(env.NIGHT_PLAY_SIGNING_KEY)).toString('base64url')
      const response=await fetcher(new URL(RPC_PATH,backend),{
        method:'POST',redirect:'error',signal:AbortSignal.timeout(12_000),
        headers:{'content-type':'application/json','x-night-timestamp':timestamp,'x-night-nonce':nonce,'x-night-signature':signature},body:raw,
      })
      if (!response.ok && ![400,401,409,429].includes(response.status)) throw new Error('Backend unavailable')
      let result=await response.json()
      if (action==='health' && response.ok) {
        if (result?.service!=='night-play' || result?.version!==1) throw new Error('Wrong backend')
        result={linked:false,profile:null}
      }
      if (response.ok && (action==='link' || ['logout','delete','cancel'].includes(action))) {
        const clear=action==='logout' || action==='delete' || (action==='cancel' && !result.linked)
        res.setHeader('Set-Cookie',`${COOKIE}=${clear?'':session}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=${clear?0:30*86400}`)
      }
      return send(response.status,result)
    } catch {
      return send(503,{error:'NIGHT connection is being updated. Please try again shortly.'})
    }
  }
}
module.exports=createHandler()
module.exports.createHandler=createHandler
