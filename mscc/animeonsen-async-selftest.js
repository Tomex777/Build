import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import animeOnsen from './sources/anime/animeonsen.js'

// Regression for a real-world deadlock: the source curl child requests an HTTP
// resource supplied by the *same Node process*. spawnSync freezes the event
// loop, so the local server cannot respond; asynchronous curl completes.
const server = createServer((req, res) => {
  if (req.url === '/slow') return
  const value = req.url === '/large'
    ? JSON.stringify({ result:'x'.repeat(2048) })
    : JSON.stringify({ result:'ok' })
  setTimeout(() => {
    if (res.destroyed || res.writableEnded) return
    res.setHeader('content-type', 'application/json')
    res.end(value)
  }, 100)
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
try {
  const url = 'http://127.0.0.1:' + server.address().port
  const config = pathname => [
    'silent',
    'show-error',
    'fail-with-body',
    'noproxy = "*"',
    'max-time = 5',
    'url = ' + JSON.stringify(url + pathname),
    '',
  ].join('\n')
  let heartbeat = false
  const clock = setTimeout(() => { heartbeat = true }, 30)
  const result = await animeOnsen._test.runCurlConfig(config('/ok'), { timeoutMs:2000 })
  clearTimeout(clock)
  assert.equal(result.status, 0)
  assert.equal(JSON.parse(result.stdout).result, 'ok')
  assert.equal(heartbeat, true, 'curl must allow WhatsApp event loop to handle other messages')
  await assert.rejects(
    () => animeOnsen._test.runCurlConfig(config('/slow'), { timeoutMs:200 }),
    /timed out/,
  )
  await assert.rejects(
    () => animeOnsen._test.runCurlConfig(config('/large'), { timeoutMs:2000, maxBytes:128 }),
    /size limit/,
  )
  console.log('PASS AnimeOnsen curl is asynchronous, bounded and cancellable')
} finally {
  server.closeAllConnections()
  await new Promise(resolve => server.close(resolve))
}
