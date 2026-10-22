const { createHash, randomBytes, sign, createPrivateKey } = require('node:crypto')
const COOKIE = '__Host-night_play'
const PARTY_COOKIE = '__Host-night_party'
const ACTIONS = new Set(['status','link','cancel','logout','profile','delete',
  'match:create','match:random','match:join','match:state','match:list','match:move','match:leave','channel:list','channel:remove','channel:verify','anonymous:start',
  'party:create','party:join','party:state','party:start','party:vote','party:advance',
  'draw:create','draw:join','draw:state','draw:start','draw:stroke','draw:clear','draw:guess','draw:advance'])
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
    let action = 'status', displayName, matchId, code, cell, channelId, mode, questions, guestName, answer, stroke, guess, since, mutationId
    if (req.method === 'POST') {
      if (!String(req.headers['content-type'] || '').toLowerCase().startsWith('application/json')) return send(415,{error:'JSON request required.'})
      try {
        let body = req.body
        if (body === undefined) {
          const chunks=[]; let size=0
          for await (const chunk of req) {
            size+=Buffer.byteLength(chunk)
            if (size>8192) return send(413,{error:'Request too large.'})
            chunks.push(Buffer.from(chunk))
          }
          body=Buffer.concat(chunks).toString('utf8')
        }
        if (typeof body === 'string' || Buffer.isBuffer(body)) {
          if (Buffer.byteLength(body)>8192) return send(413,{error:'Request too large.'})
          body=JSON.parse(String(body))
        }
        if (!body || typeof body !== 'object' || Array.isArray(body) || JSON.stringify(body).length>8192) return send(400,{error:'Invalid request.'})
        action=body.action; displayName=typeof body.displayName==='string'?body.displayName.slice(0,28):undefined
        matchId=typeof body.matchId==='string'?body.matchId:undefined
        code=typeof body.code==='string'?body.code:undefined
        cell=body.cell
        channelId=typeof body.channelId==='string'?body.channelId:undefined
        mode=typeof body.mode==='string'?body.mode:undefined
        questions=body.questions
        guestName=typeof body.guestName==='string'?body.guestName.slice(0,24):undefined
        answer=body.answer
        stroke=body.stroke; guess=typeof body.guess==='string'?body.guess.slice(0,24):undefined
        since=body.since; mutationId=typeof body.mutationId==='string'?body.mutationId.slice(0,64):undefined
      } catch { return send(400,{error:'Invalid request.'}) }
    }
    if (!ACTIONS.has(action) || (req.method==='POST' && action==='status')) return send(400,{error:'Unknown account action.'})
    if (matchId!==undefined && !/^[0-9a-f-]{36}$/i.test(matchId)) return send(400,{error:'Invalid match.'})
    if (code!==undefined && !/^[A-HJ-NP-Z2-9\s-]{1,24}$/i.test(code)) return send(400,{error:'Invalid invitation.'})
    if (channelId!==undefined && !/^\d{8,25}@newsletter$/.test(channelId)) return send(400,{error:'Invalid channel.'})
    if (mode!==undefined && !['wyr','mlt','tot','nhie','trivia','quiz'].includes(mode)) return send(400,{error:'Invalid game.'})
    if (answer!==undefined && (!Number.isInteger(answer)||answer<0||answer>16)) return send(400,{error:'Invalid answer.'})
    if (cell!==undefined && (!Number.isInteger(cell)||cell<0||cell>8)) return send(400,{error:'Invalid square.'})
    const match=String(req.headers.cookie || '').match(/(?:^|;\s*)__Host-night_play=([A-Za-z0-9_-]{43})(?:;|$)/)
    let session=match?.[1] || ''
    const guestMatch=String(req.headers.cookie || '').match(/(?:^|;\s*)__Host-night_party=([A-Za-z0-9_-]{43})(?:;|$)/)
    let guestSession=guestMatch?.[1] || ''
    if (['party:join','draw:join'].includes(action) && !guestSession) guestSession=randomBytes(32).toString('base64url')
    if (!session && action==='link') session=randomBytes(32).toString('base64url')
    if (!session && action==='status') {
      // A cheap health check avoids minting anonymous database rows on page views.
      action='health'
    }
    try {
      const backend = new URL(env.NIGHT_PLAY_MSCC_URL)
      if (backend.protocol!=='https:' || backend.pathname!=='/' || backend.username || backend.password || backend.search || backend.hash) throw new Error('Invalid backend configuration')
      const client = hash(String(req.headers['x-vercel-forwarded-for'] || req.headers['x-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim())
      const raw=JSON.stringify({action,session,client,displayName,matchId,code,cell,channelId,mode,questions,guestName,guestSession,answer,stroke,guess,since,mutationId})
      const timestamp=String(now()), nonce=randomBytes(24).toString('base64url')
      const signature=sign(null,Buffer.from(message(timestamp,nonce,raw)),createPrivateKey(env.NIGHT_PLAY_SIGNING_KEY)).toString('base64url')
      const response=await fetcher(new URL(RPC_PATH,backend),{
        method:'POST',redirect:'error',signal:AbortSignal.timeout(12_000),
        headers:{'content-type':'application/json','x-night-timestamp':timestamp,'x-night-nonce':nonce,'x-night-signature':signature},body:raw,
      })
      if (!response.ok && ![400,401,403,404,409,410,429].includes(response.status)) throw new Error('Backend unavailable')
      let result=await response.json()
      if (!response.ok && (action==='health' || (response.status===401 && result?.error!=='Connect WhatsApp to continue.' && !['party:','draw:'].some(prefix=>String(action).startsWith(prefix))))) throw new Error('Bridge not ready')
      if (action==='health' && response.ok) {
        if (result?.service!=='night-play' || result?.version!==1) throw new Error('Wrong backend')
        result={linked:false,profile:null,...(result.features?.draw===true?{features:{draw:true}}:{})}
      }
      if (response.ok && ['party:join','draw:join'].includes(action) && !guestMatch) {
        res.setHeader('Set-Cookie',`${PARTY_COOKIE}=${guestSession}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=86400`)
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
