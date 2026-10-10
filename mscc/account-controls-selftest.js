import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createServer } from 'node:net'
import { AccountRegistry } from './account-registry.js'
import { openSharedStorage } from './shared-storage.js'
import { startWebPanel } from './web-panel.js'

async function freePort() {
  const server = createServer()
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

const root = await mkdtemp(join(tmpdir(), 'mscc-account-control-test-'))
let panel
let storage
try {
  const registry = new AccountRegistry({
    file: join(root, 'accounts.json'),
    authRoot: join(root, 'auth'),
    maxAccounts: 4,
  })
  await registry.load()
  const main = await registry.create({ phoneNumber: '234000000001', displayName: 'Main' })
  const linked = await registry.create({ phoneNumber: '234000000002', displayName: 'Josia' })
  const authFile = join(linked.authDir, 'creds.json')
  await writeFile(authFile, JSON.stringify({ sentinel: 'DO NOT CHANGE DURING RENAME OR PROFILE ASSIGNMENT' }))
  const authBefore = await readFile(authFile, 'utf8')

  storage = await openSharedStorage({
    file: join(root, 'profiles.sqlite'),
    ttlMs: 86400000,
    maxMessagesPerAccount: 100,
  })
  storage.assignProfile(main.id, 'control')
  storage.assignProfile(linked.id, 'josiah')
  storage.setGroupRoute('some-group@g.us', 'general', linked.id)
  const renameAccount = async (id, name) => {
    const row = await registry.rename(id, name)
    return { ok: true, account: row.id, displayName: row.displayName }
  }
  const assignAccountProfile = async (id, profileId) => {
    if (!registry.get(id)) throw new Error('Unknown account')
    const requested = String(profileId || '').trim().toLowerCase()
    if (!/^[a-z0-9][a-z0-9._-]{0,63}$/.test(requested)) throw new Error('Invalid bot profile ID')
    const assigned = storage.assignProfile(id, requested)
    storage.clearGroupRoutes()
    return { ok: true, account: id, profile: assigned.id }
  }
  const port = await freePort()
  const localControlPort = await freePort()
  panel = startWebPanel({
    port, host: '127.0.0.1', password: 'selftest-not-a-real-password',
    localControlPort, getState: () => ({ accounts: registry.list() }),
    renameAccount, assignAccountProfile,
  })
  const target = `http://127.0.0.1:${localControlPort}`
  async function request(method, pathname, body) {
    let response
    for (let attempt = 0; attempt < 30; attempt++) {
      try {
        response = await fetch(target + pathname, {
          method, headers: { 'content-type': 'application/json' },
          body: JSON.stringify(body || {}),
        })
        break
      } catch (error) {
        if (attempt === 29) throw error
        await new Promise(resolve => setTimeout(resolve, 50))
      }
    }
    return { status: response.status, data: await response.json() }
  }

  const renamed = await request('PATCH', `/accounts/${linked.id}`, { displayName: 'Night Backup' })
  assert.equal(renamed.status, 200)
  assert.equal(registry.get(linked.id).displayName, 'Night Backup')
  assert.equal(storage.profileForAccount(linked.id).id, 'josiah', 'Rename must not switch bot profile')

  const changed = await request('POST', `/accounts/${linked.id}/profile`, { profileId: 'nami' })
  assert.equal(changed.status, 200)
  assert.equal(storage.profileForAccount(linked.id).id, 'nami')
  assert.equal(storage.getGroupRoute('some-group@g.us', 'general'), '', 'Routing must be invalidated')
  assert.equal(registry.get(linked.id).displayName, 'Night Backup', 'Profile changes must not rename account')
  assert.equal(await readFile(authFile, 'utf8'), authBefore, 'Credentials were modified')

  const badRequest = await request('POST', `/accounts/${linked.id}/profile`, {})
  assert.equal(badRequest.status, 400)
  const forbidden = await request('POST', `/accounts/${main.id}/profile`, { profileId: 'nami' })
  assert.notEqual(forbidden.status, 200, 'Main control must retain control personality')
  assert.equal(storage.profileForAccount(main.id).id, 'control')
  const missing = await request('POST', '/accounts/unknown/profile', { profileId: 'nami' })
  assert.notEqual(missing.status, 200)
  assert.equal(await readFile(authFile, 'utf8'), authBefore)

  console.log('PASS MSCC Cortex account rename and profile control APIs preserve WhatsApp sessions')
} finally {
  panel?.close()
  storage?.close()
  await rm(root, { recursive: true, force: true })
}
