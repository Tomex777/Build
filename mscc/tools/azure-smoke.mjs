import { pathToFileURL } from 'node:url'

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function loopback(urlText) {
  const url = new URL(urlText)
  assert(url.protocol === 'http:', 'Smoke probes must use HTTP on the VM loopback interface')
  assert(['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname), 'Refusing to query a non-loopback address')
  return url.href
}

async function json(url, fetcher) {
  const response = await fetcher(loopback(url), {
    method: 'GET', headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(8_000),
  })
  assert(response.ok, 'Local MSCC endpoint failed its HTTP health check')
  return response.json()
}

/**
 * Read-only Azure post-deploy checks. Never prints account names,
 * numbers, activity contents, pairing codes or credential data.
 */
export async function checkAzureLoopback({
  panelPort = 8787,
  localPort = 8788,
  fetcher = fetch,
} = {}) {
  const health = await json(`http://127.0.0.1:${panelPort}/health`, fetcher)
  assert(health?.ok === true && health?.service === 'mscc', 'MSCC health response is invalid')
  assert(health.panelConfigured === true, 'MSCC web control panel is not configured')

  const state = await json(`http://127.0.0.1:${localPort}/state`, fetcher)
  assert(Array.isArray(state?.accounts), 'MSCC local account registry was not returned')
  assert(state?.capabilities?.fixedCcDestination === true, 'Fixed Account A CC routing is not active')
  assert(state?.capabilities?.changeCcDestination === false, 'Unexpected mutable CC destination capability')
  const ids = new Set()
  for (const account of state.accounts) {
    assert(typeof account?.id === 'string' && account.id.length > 0, 'Account missing stable ID')
    assert(!ids.has(account.id), 'Duplicate account IDs in MSCC state')
    ids.add(account.id)
    assert(account.role === (account.id === 'A' ? 'owner' : 'linked'), 'Account A ownership is invalid')
    assert(typeof account.profile === 'string' && account.profile.length > 0, 'Account profile is not explicit')
    assert(typeof account.status === 'string', 'Account state is missing status')
    if (account.id !== 'A') {
      assert(account.profile !== 'control', 'Linked session has Account A control profile')
    }
  }
  if (state.accounts.length) {
    assert(ids.has('A'), 'Nonempty account registry is missing permanent Account A')
    assert(state.destination === 'A', 'CC destination is not Account A')
  }

  const activity = await json(`http://127.0.0.1:${localPort}/activity?limit=1`, fetcher)
  assert(Array.isArray(activity?.entries), 'Local account activity feed is not available')

  return {
    ok: true,
    accounts: state.accounts.length,
    connected: state.accounts.filter(account => account.connected === true).length,
    paused: state.accounts.filter(account => account.paused === true).length,
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  checkAzureLoopback({
    panelPort: Number(process.env.MSCC_PANEL_PORT || 8787),
    localPort: Number(process.env.MSCC_LOCAL_CONTROL_PORT || 8788),
  }).then(({ accounts, connected, paused }) => {
    console.log(`MSCC Azure loopback smoke PASS: ${accounts} accounts, ${connected} connected, ${paused} paused`)
  }).catch(error => {
    console.error('MSCC Azure loopback smoke FAILED:', error.message)
    process.exitCode = 1
  })
}
