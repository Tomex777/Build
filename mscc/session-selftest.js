import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import {
  DisconnectReason,
  useMultiFileAuthState,
} from '@itsliaaa/baileys'
import { AccountRegistry } from './account-registry.js'
import { classifyDisconnect, jidPhoneNumber, reconnectDelay } from './session-policy.js'

const root = await mkdtemp(join(tmpdir(), 'mscc-session-test-'))

try {
  const authA = join(root, 'auth-A')
  const authB = join(root, 'auth-B')

  const firstA = await useMultiFileAuthState(authA)
  const firstB = await useMultiFileAuthState(authB)
  assert.notStrictEqual(firstA.state.creds, firstB.state.creds)

  firstA.state.creds.registered = true
  firstA.state.creds.me = { id: '234000000001:7@s.whatsapp.net', name: 'A' }
  firstB.state.creds.registered = true
  firstB.state.creds.me = { id: '234000000002:2@s.whatsapp.net', name: 'B' }
  await Promise.all([firstA.saveCreds(), firstB.saveCreds()])

  const reopenedA = await useMultiFileAuthState(authA)
  const reopenedB = await useMultiFileAuthState(authB)
  assert.equal(jidPhoneNumber(reopenedA.state.creds.me?.id), '234000000001')
  assert.equal(jidPhoneNumber(reopenedB.state.creds.me?.id), '234000000002')

  const credsA = JSON.parse(await readFile(join(authA, 'creds.json'), 'utf8'))
  const credsB = JSON.parse(await readFile(join(authB, 'creds.json'), 'utf8'))
  assert.notDeepEqual(credsA.me, credsB.me)

  assert.equal(classifyDisconnect(DisconnectReason.loggedOut).action, 'repair')
  assert.equal(classifyDisconnect(DisconnectReason.badSession).action, 'repair')
  assert.equal(classifyDisconnect(DisconnectReason.multideviceMismatch).action, 'repair')
  assert.equal(classifyDisconnect(DisconnectReason.forbidden).action, 'repair')
  assert.equal(classifyDisconnect(DisconnectReason.connectionReplaced).action, 'halt')
  assert.deepEqual(classifyDisconnect(DisconnectReason.restartRequired), { action:'reconnect', delayMs:500 })
  assert.equal(classifyDisconnect(DisconnectReason.connectionLost).action, 'reconnect')
  assert.equal(reconnectDelay(1), 2000)
  assert.equal(reconnectDelay(2), 4000)
  assert.equal(reconnectDelay(5), 30000)
  assert.equal(reconnectDelay(99), 30000)

  const duplicateRegistryFile = join(root, 'data', 'duplicate-accounts.json')
  await import('node:fs/promises').then(({ mkdir }) => mkdir(join(root, 'data'), { recursive:true }))
  await writeFile(duplicateRegistryFile, JSON.stringify({
    version: 2,
    accounts: [
      { id:'A', phoneNumber:'234000000001', displayName:'Main', authDir:authA, role:'owner' },
      { id:'account-2', phoneNumber:'234000000002', displayName:'Second', authDir:authA, role:'linked' },
    ],
  }))

  const duplicateRegistry = new AccountRegistry({
    file: duplicateRegistryFile,
    authRoot: join(root, 'accounts'),
    maxAccounts: 5,
  })
  await assert.rejects(
    () => duplicateRegistry.load(),
    /share the same auth directory/,
  )

  console.log('MSCC Baileys session isolation and lifecycle policy OK')
} finally {
  await rm(root, { recursive:true, force:true })
}
