import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import {
  DisconnectReason,
  useMultiFileAuthState,
} from '@itsliaaa/baileys'
import { AccountRegistry } from './account-registry.js'
import { canStartScheduledReconnect, classifyDisconnect, disconnectOrigin, jidPhoneNumber, reconnectDelay, serializeSessionOperation, sessionStatus } from './session-policy.js'

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

  assert.equal(disconnectOrigin({ message:'Stream Errored (401): private@lid' }), 'stream-error')
  assert.equal(disconnectOrigin({ message:'Connection Failure' }), 'server-failure')
  assert.equal(disconnectOrigin({ message:'Intentional Logout' }), 'intentional-logout')
  assert.equal(disconnectOrigin({ message:'Connection Terminated by Server' }), 'transport-close')
  assert.equal(disconnectOrigin({ message:'unknown details 2348012345678' }), 'other')
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

  const session = { enabled: true, connected: false, paused: false, invalid: false, sock: null, pairingMode: '', reconnectTimer: null }
  assert.equal(sessionStatus(session), 'offline')
  session.reconnectTimer = {}
  assert.equal(sessionStatus(session), 'reconnecting', 'Retry delay must not be mislabelled offline')
  session.reconnectTimer = null
  session.paused = true
  assert.equal(sessionStatus(session), 'paused')
  session.invalid = true
  assert.equal(sessionStatus(session), 'auth-invalid', 'Auth failures must stay repairable')
  session.invalid = false
  session.paused = false
  session.pairingMode = 'code'
  assert.equal(sessionStatus(session), 'pairing')
  session.pairingMode = ''
  session.sock = {}
  assert.equal(sessionStatus(session), 'connecting')
  session.connected = true
  assert.equal(sessionStatus(session), 'connected')

  // A stale timer or an already-live socket must never re-open the same
  // Baileys auth directory, even if a reconnect callback fires late.
  const guarded = { enabled:true, generation:3, paused:false, invalid:false, sock:null, op:Promise.resolve() }
  assert.equal(canStartScheduledReconnect(guarded, 3), true)
  guarded.sock = {}
  assert.equal(canStartScheduledReconnect(guarded, 3), false, 'Do not create a second socket')
  guarded.sock = null
  guarded.paused = true
  assert.equal(canStartScheduledReconnect(guarded, 3), false)
  guarded.paused = false
  guarded.invalid = true
  assert.equal(canStartScheduledReconnect(guarded, 3), false)
  guarded.invalid = false
  assert.equal(canStartScheduledReconnect(guarded, 2), false, 'Old generation is stale')

  // Reconnection must wait for any in-flight per-account operation rather
  // than racing a manual pause, repair or credential update.
  let releaseFirst
  const firstGate = new Promise(resolve => { releaseFirst = resolve })
  const sequence = []
  const firstTask = serializeSessionOperation(guarded, async () => {
    sequence.push('first-start')
    await firstGate
    sequence.push('first-end')
  })
  const secondTask = serializeSessionOperation(guarded, async () => {
    sequence.push('second-start')
  })
  await new Promise(resolve => setTimeout(resolve, 10))
  assert.deepEqual(sequence, ['first-start'])
  releaseFirst()
  await Promise.all([firstTask, secondTask])
  assert.deepEqual(sequence, ['first-start', 'first-end', 'second-start'])
  await assert.rejects(serializeSessionOperation(guarded, async () => {
    throw new Error('transient auth reload error')
  }), /transient auth reload error/)
  await serializeSessionOperation(guarded, async () => sequence.push('third-after-failure'))
  assert.equal(sequence.at(-1), 'third-after-failure', 'One failed reload cannot poison subsequent retries')

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

  // User-requested disconnect must survive process restarts.
  const registry = new AccountRegistry({
    file: join(root, 'data', 'paused-accounts.json'),
    authRoot: join(root, 'managed-auth'),
    maxAccounts: 4,
  })
  await registry.load()
  const owner = await registry.create({ phoneNumber:'234000000003', displayName:'Main' })
  const linked = await registry.create({ phoneNumber:'234000000004', displayName:'Nami' })
  assert.equal(owner.paused, false)
  assert.equal(linked.paused, false)
  await registry.setPaused(linked.id, true)
  assert.equal(registry.get(linked.id).paused, true)
  const reloaded = new AccountRegistry({
    file: join(root, 'data', 'paused-accounts.json'),
    authRoot: join(root, 'managed-auth'),
    maxAccounts: 4,
  })
  await reloaded.load()
  assert.equal(reloaded.get(linked.id).paused, true, 'Manual disconnect must persist across restart')
  assert.equal(reloaded.get('A').paused, false, 'Other sessions must not be affected')
  await reloaded.setPaused(linked.id, false)
  const resumed = new AccountRegistry({
    file: join(root, 'data', 'paused-accounts.json'),
    authRoot: join(root, 'managed-auth'),
    maxAccounts: 4,
  })
  await resumed.load()
  assert.equal(resumed.get(linked.id).paused, false, 'Reconnect must persist after restart')
  assert.equal(resumed.get('A').paused, false)

  console.log('MSCC Baileys session isolation and lifecycle policy OK')
} finally {
  await rm(root, { recursive:true, force:true })
}
