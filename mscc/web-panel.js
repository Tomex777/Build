import { createHash, createHmac, timingSafeEqual } from 'node:crypto'
import { createServer } from 'node:http'
import { readFileSync } from 'node:fs'

const PAGE = readFileSync(new URL('./panel.html', import.meta.url), 'utf8')
const MAX_BODY = 16 * 1024
const SESSION_AGE = 7 * 24 * 60 * 60
const LOGIN_WINDOW = 10 * 60 * 1000
const MAX_ATTEMPTS = 10

function sendJson(res, status, payload, headers = {}) {
  const body = JSON.stringify(payload)
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'no-referrer',
    ...headers
  })
  res.end(body)
}

function sendHtml(res, status, body) {
  res.writeHead(status, {
    'content-type': 'text/html; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'no-referrer',
    'content-security-policy': "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
  })
  res.end(body)
}

async function readJson(req) {
  const chunks = []
  let total = 0
  for await (const chunk of req) {
    total += chunk.length
    if (total > MAX_BODY) throw new Error('Request too large')
    chunks.push(chunk)
  }
  return chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}
}

function safeEqual(a, b) {
  const x = Buffer.from(String(a))
  const y = Buffer.from(String(b))
  return x.length === y.length && timingSafeEqual(x, y)
}

function getCookie(req, name) {
  for (const part of String(req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=')
    if (i < 0) continue
    if (part.slice(0, i).trim() === name) return decodeURIComponent(part.slice(i + 1).trim())
  }
  return ''
}

function origin(req) {
  const proto = String(req.headers['x-forwarded-proto'] || '').split(',')[0].trim() || 'http'
  const host = String(req.headers['x-forwarded-host'] || req.headers.host || '').split(',')[0].trim()
  return host ? `${proto}://${host}` : ''
}

function mutationAllowed(req) {
  const o = String(req.headers.origin || '').trim()
  return !o || o === origin(req)
}

function ip(req) {
  return String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || 'unknown'
}

export function startWebPanel({ port, password, sessionSecret, localControlPort = 8788, getState, pairAccount, reconnectAccount, repairAccount, setSetting, setDestination, reloadCommands }) {
  const configured = Boolean(password && password !== 'change-this-password' && password !== 'change-me')
  const secret = createHash('sha256').update(`${sessionSecret || ''}\0${password || ''}\0mscc`).digest()
  const token = createHmac('sha256', secret).update('admin').digest('base64url')
  const attempts = new Map()

  function authed(req) {
    return configured && safeEqual(getCookie(req, 'mscc_session'), token)
  }

  function cookie(req, value, maxAge = SESSION_AGE) {
    const secure = String(req.headers['x-forwarded-proto'] || '').includes('https') ? '; Secure' : ''
    return `mscc_session=${encodeURIComponent(value)}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${secure}`
  }

  const server = createServer(async (req, res) => {
    const url = new URL(req.url || '/', 'http://mscc.local')
    try {
      if (req.method === 'GET' && url.pathname === '/health') {
        return sendJson(res, 200, { ok: true, service: 'mscc', panelConfigured: configured })
      }
      if (req.method === 'GET' && url.pathname === '/') return sendHtml(res, 200, PAGE)

      if (req.method === 'POST' && url.pathname === '/api/login') {
        if (!configured) return sendJson(res, 503, { error: 'Set WEB_PASSWORD first.' })
        if (!mutationAllowed(req)) return sendJson(res, 403, { error: 'Origin rejected' })
        const key = ip(req)
        const now = Date.now()
        const list = (attempts.get(key) || []).filter(t => now - t < LOGIN_WINDOW)
        if (list.length >= MAX_ATTEMPTS) return sendJson(res, 429, { error: 'Too many attempts. Try later.' })
        const body = await readJson(req)
        if (!safeEqual(body.password || '', password)) {
          list.push(now)
          attempts.set(key, list)
          return sendJson(res, 401, { error: 'Wrong password' })
        }
        attempts.delete(key)
        return sendJson(res, 200, { ok: true }, { 'set-cookie': cookie(req, token) })
      }

      if (!authed(req)) return sendJson(res, 401, { error: 'Locked' })
      if (req.method !== 'GET' && !mutationAllowed(req)) return sendJson(res, 403, { error: 'Origin rejected' })

      if (req.method === 'POST' && url.pathname === '/api/logout') {
        return sendJson(res, 200, { ok: true }, { 'set-cookie': cookie(req, '', 0) })
      }
      if (req.method === 'GET' && url.pathname === '/api/state') {
        return sendJson(res, 200, await getState())
      }
      if (req.method === 'POST' && url.pathname === '/api/settings') {
        const body = await readJson(req)
        if (!['autoCc', 'replyCc', 'antiDelete'].includes(body.key) || typeof body.value !== 'boolean') {
          return sendJson(res, 400, { error: 'Invalid setting' })
        }
        await setSetting(body.key, body.value)
        return sendJson(res, 200, { ok: true })
      }
      if (req.method === 'POST' && url.pathname === '/api/destination') {
        const body = await readJson(req)
        return sendJson(res, 200, { ok: true, destination: await setDestination(body.account) })
      }
      if (req.method === 'POST' && url.pathname === '/api/commands/reload') {
        return sendJson(res, 200, { ok: true, commands: await reloadCommands() })
      }

      const m = url.pathname.match(/^\/api\/accounts\/(A|B)\/(pair|reconnect|repair)$/)
      if (req.method === 'POST' && m) {
        const [, id, action] = m
        const body = await readJson(req)
        if (action === 'pair') {
          const mode = body.mode === 'qr' ? 'qr' : 'code'
          return sendJson(res, 200, await pairAccount(id, mode))
        }
        if (action === 'reconnect') return sendJson(res, 200, await reconnectAccount(id))
        if (body.confirm !== true) return sendJson(res, 400, { error: 'Re-pair requires confirmation' })
        return sendJson(res, 200, await repairAccount(id, body.mode === 'qr' ? 'qr' : 'code'))
      }

      return sendJson(res, 404, { error: 'Not found' })
    } catch (error) {
      console.error('Web request failed:', error?.message || error)
      return sendJson(res, 500, { error: error?.message || 'Internal error' })
    }
  })

  const localServer = createServer(async (req, res) => {
    const url = new URL(req.url || '/', 'http://mscc.local')
    try {
      if (req.method === 'GET' && url.pathname === '/state') {
        return sendJson(res, 200, await getState())
      }
      if (req.method === 'POST' && url.pathname === '/destination') {
        const body = await readJson(req)
        return sendJson(res, 200, { ok: true, destination: await setDestination(body.account) })
      }
      if (req.method === 'POST' && url.pathname === '/commands/reload') {
        return sendJson(res, 200, { ok: true, commands: await reloadCommands() })
      }
      const m = url.pathname.match(/^\/accounts\/(A|B)\/(pair|reconnect|repair)$/)
      if (req.method === 'POST' && m) {
        const [, id, action] = m
        const body = await readJson(req)
        if (action === 'pair') {
          const mode = body.mode === 'qr' ? 'qr' : 'code'
          return sendJson(res, 200, await pairAccount(id, mode))
        }
        if (action === 'reconnect') return sendJson(res, 200, await reconnectAccount(id))
        return sendJson(res, 200, await repairAccount(id, body.mode === 'qr' ? 'qr' : 'code'))
      }
      return sendJson(res, 404, { error: 'Not found' })
    } catch (error) {
      console.error('Local control request failed:', error?.message || error)
      return sendJson(res, 500, { error: error?.message || 'Internal error' })
    }
  })

  localServer.on('error', error => {
    console.error(`MSCC local control listen error on 127.0.0.1:${localControlPort}:`, error?.message || error)
  })
  localServer.listen(localControlPort, '127.0.0.1', () => {
    console.log(`MSCC local control listening on 127.0.0.1:${localControlPort}`)
  })

  server.on('error', error => {
    console.error(`MSCC panel listen error on 0.0.0.0:${port}:`, error?.message || error)
  })
  server.listen(port, '0.0.0.0', () => {
    console.log(`MSCC panel listening on 0.0.0.0:${port}`)
  })
  return {
    close() {
      server.close()
      localServer.close()
    }
  }
}
