import { pathToFileURL } from 'node:url'

function check(value, message) {
  if (!value) throw new Error(message)
}

function secureOrigin(text) {
  const url = new URL(text)
  check(url.protocol === 'https:', 'Cortex Agent URL must use HTTPS')
  check(url.username === '' && url.password === '' && !url.search && !url.hash, 'Cortex Agent URL must not contain credentials or query parameters')
  check(url.pathname === '/' || url.pathname === '', 'Cortex Agent URL must point to the origin, not an API route')
  return url.origin
}

async function query(origin, path, token, fetcher) {
  let response
  try {
    response = await fetcher(origin + path, {
      method: 'GET',
      headers: { authorization: 'Bearer ' + token, accept: 'application/json' },
      signal: AbortSignal.timeout(12_000),
    })
  } catch {
    throw new Error('Remote Cortex Agent endpoint is unreachable over trusted HTTPS')
  }
  check(response?.ok, 'Remote Cortex Agent readiness endpoint rejected the authenticated probe')
  try {
    return await response.json()
  } catch {
    throw new Error('Remote Cortex Agent readiness response is not valid JSON')
  }
}

/**
 * Read-only production verification. Never prints or returns contact numbers,
 * account names, pairing codes, tokens, event details or authentication files.
 * Also requires that the unauthenticated health route is rejected.
 */
export async function checkRemoteCortex({ url, token, fetcher = fetch }) {
  const origin = secureOrigin(url)
  check(typeof token === 'string' && token.length >= 24, 'A Cortex Agent bearer token is required')

  let anonymous
  try {
    anonymous = await fetcher(origin + '/api/cortex/host/status', {
      method: 'GET', headers: { accept: 'application/json' },
      signal: AbortSignal.timeout(12_000),
    })
  } catch {
    throw new Error('Remote Cortex Agent endpoint is unreachable over trusted HTTPS')
  }
  check(anonymous.status === 401, 'Cortex Agent status endpoint is exposed without required bearer authorization')

  const host = await query(origin, '/api/cortex/host/status', token, fetcher)
  check(host?.state === 'active', 'MSCC systemd service is not active')
  check(host?.runtime?.runtime === 'Node.js', 'Cortex Agent host status runtime is unexpected')

  const pairing = await query(origin, '/api/cortex/mscc/pairing', token, fetcher)
  check(Array.isArray(pairing?.accounts), 'MSCC account state was not returned via Cortex Agent')
  check(pairing?.capabilities?.fixedCcDestination === true, 'Account A fixed destination policy is not active')
  check(pairing?.capabilities?.changeCcDestination === false, 'MSCC destination policy allows unexpected changes')

  const ids = new Set()
  for (const account of pairing.accounts) {
    check(typeof account?.id === 'string' && account.id.length > 0, 'MSCC account has no stable ID')
    check(!ids.has(account.id), 'MSCC contains duplicate stable account IDs')
    ids.add(account.id)
    check(account.role === (account.id === 'A' ? 'owner' : 'linked'), 'MSCC account ownership role is incorrect')
    check(typeof account.profile === 'string' && account.profile.length > 0, 'MSCC account has no explicit bot profile')
    check(typeof account.status === 'string', 'MSCC account is missing its lifecycle state')
    if (account.id !== 'A') check(account.profile !== 'control', 'A linked account has the reserved control profile')
  }
  if (pairing.accounts.length > 0) {
    check(ids.has('A'), 'MSCC account registry is missing permanent Account A')
    check(pairing.destination === 'A', 'MSCC CC destination is not Account A')
  }
  return Object.freeze({
    ok: true,
    accounts: pairing.accounts.length,
    connected: pairing.accounts.filter(a => a.connected === true).length,
    paused: pairing.accounts.filter(a => a.paused === true).length,
  })
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  checkRemoteCortex({
    url: process.env.CORTEX_AGENT_URL || '',
    token: process.env.CORTEX_AGENT_TOKEN || '',
  }).then(({ accounts, connected, paused }) => {
    console.log(`Cortex remote readiness PASS: ${accounts} accounts, ${connected} connected, ${paused} paused`)
  }).catch(() => {
    // Never echo a server response body: it could contain private account data.
    console.error('Cortex remote readiness FAILED: check HTTPS, token, MSCC service, and account contract on the VM')
    process.exitCode = 1
  })
}
