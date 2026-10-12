import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, readdir, realpath, rm, stat, symlink, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { tmpdir } from 'node:os'
import { useMultiFileAuthState } from '@itsliaaa/baileys'
import { assertSeparateAuthDirectories, useDurableMultiFileAuthState } from './account-auth-state.js'

const root = await mkdtemp(join(tmpdir(), 'mscc-atomic-auth-'))
const A = join(root, 'A')
const B = join(root, 'B')
try {
  // Existing production creds.json must remain readable without re-pairing.
  const legacy = await useMultiFileAuthState(A)
  legacy.state.creds.registered = true
  legacy.state.creds.me = { id: '2341000000001:5@s.whatsapp.net', name: 'Main' }
  await legacy.state.keys.set({ session: { 'one': { registration: Buffer.from([1, 2, 3]) } } })
  await legacy.saveCreds()

  const durable = await useDurableMultiFileAuthState(A)
  const second = await useDurableMultiFileAuthState(B)
  assert.equal(durable.state.creds.me.id, '2341000000001:5@s.whatsapp.net')
  assert.equal(second.state.creds.registered, false)
  assert.notStrictEqual(durable.state.creds, second.state.creds)
  assert.deepEqual((await durable.state.keys.get('session', ['one'])).one.registration, Buffer.from([1,2,3]))

  // Simultaneous mutations on one file must finish in order, without torn JSON.
  await Promise.all(Array.from({ length: 12 }, (_, i) =>
    durable.state.keys.set({ session: { one: { value: i, payload: Buffer.from('test') } } })
  ))
  const newest = (await durable.state.keys.get('session', ['one'])).one
  assert.equal(newest.value, 11)
  assert.equal(newest.payload.toString(), 'test')

  await durable.state.keys.set({ session: { one: null } })
  assert.equal((await durable.state.keys.get('session', ['one'])).one, null)
  durable.state.creds.accountSyncCounter = 7
  await durable.saveCreds()

  const legacyAfter = await useMultiFileAuthState(A)
  assert.equal(legacyAfter.state.creds.registered, true)
  assert.equal(legacyAfter.state.creds.accountSyncCounter, 7)
  assert.equal((await stat(join(A, 'creds.json'))).mode & 0o077, 0,
    'Auth files must not be readable by other users')
  assert.equal((await readdir(A)).filter(name => name.endsWith('.tmp')).length, 0)

  // Invalid JSON must be surfaced; never silently load a fresh auth identity.
  await writeFile(join(A, 'creds.json'), '{corrupted')
  await assert.rejects(() => useDurableMultiFileAuthState(A), /authentication file creds.json is invalid/)
  assert.equal(await readFile(join(A, 'creds.json'), 'utf8'), '{corrupted')
  await writeFile(join(A, 'creds.json'), 'null')
  await assert.rejects(() => useDurableMultiFileAuthState(A), /creds.json is incomplete/)
  await writeFile(join(A, 'creds.json'), '{}')
  await assert.rejects(() => useDurableMultiFileAuthState(A), /creds.json is incomplete/)

  // A separate account's key store cannot be read from a corrupt A directory.
  second.state.creds.registered = true
  second.state.creds.me = { id: '2341000000002:8@s.whatsapp.net' }
  await second.saveCreds()
  assert.equal((await useDurableMultiFileAuthState(B)).state.creds.registered, true)

  await assertSeparateAuthDirectories([{ id: 'A', authDir: A }, { id: 'B', authDir: B }])
  const alias = join(root, 'alias-A')
  await symlink(await realpath(A), alias)
  await assert.rejects(
    () => assertSeparateAuthDirectories([{ id: 'A', authDir: A }, { id: 'duplicate-A', authDir: alias }]),
    /share or nest/,
  )
  await mkdir(join(A, 'nested'))
  await assert.rejects(
    () => assertSeparateAuthDirectories([{ id: 'A', authDir: A }, { id: 'nested', authDir: join(A, 'nested') }]),
    /share or nest/,
  )
  console.log('PASS atomic auth persistence, legacy compatibility, corrupt-file guard, and multi-account path isolation')
} finally {
  await rm(root, { recursive: true, force: true })
}
