import assert from 'node:assert/strict'
import { Readable } from 'node:stream'
import {
  megaConfig, megaRecentFiles, megaStorageStatus, safeMegaName,
  uploadMegaMedia, validateMegaSize,
} from './mega-storage.js'

const env = {
  MSCC_MEGA_EMAIL:'test@example.invalid',
  MSCC_MEGA_PASSWORD:'test-secret',
  MSCC_MEGA_FOLDER:'Archive',
}
let closeCount = 0
let transferred = ''
let folderExists = false
class FakeStorage {
  constructor(options) {
    assert.equal(options.keepalive, false)
    assert.equal(options.email, env.MSCC_MEGA_EMAIL)
    this.ready = Promise.resolve()
    this.folder = {
      directory:true,
      children:[],
      upload:({ name, size }, source) => {
        assert.equal(name,'media.png')
        assert.equal(size,3)
        return { complete:(async () => {
          for await (const chunk of source) transferred += Buffer.from(chunk).toString('utf8')
          this.folder.children.push({ name, size, timestamp:123 })
          return { name, nodeId:'test-node' }
        })() }
      },
    }
    this.root = {
      find:name => name === 'Archive' && folderExists ? this.folder : undefined,
      mkdir:async name => {
        assert.equal(name,'Archive')
        folderExists = true
        return this.folder
      },
    }
  }
  async getAccountInfo() { return { spaceUsed:1048576, spaceTotal:20971520, type:0 } }
  async close() { closeCount++ }
}

assert.equal(megaConfig({}).configured, false)
assert.equal(megaConfig(env).folder, 'Archive')
assert.equal(safeMegaName('../path\\evil?.png'),'.._path_evil?.png')
assert.throws(() => validateMegaSize(0), /known/)
assert.equal(validateMegaSize(1536 * 1024 * 1024), 1536 * 1024 * 1024)
assert.equal(validateMegaSize(2 * 1024 * 1024 * 1024), 2 * 1024 * 1024 * 1024)
assert.throws(() => validateMegaSize(2 * 1024 * 1024 * 1024 + 1), /2 GiB/)
await assert.rejects(() => megaStorageStatus({ env:{}, StorageClass:FakeStorage }), /not configured/)
assert.equal(closeCount, 0)

const stored = await uploadMegaMedia({
  name:'media.png', size:3, source:async () => Readable.from([Buffer.from('abc')]),
}, { env, StorageClass:FakeStorage })
assert.equal(stored.name, 'media.png')
assert.equal(transferred, 'abc')
const stats = await megaStorageStatus({ env, StorageClass:FakeStorage })
assert.equal(stats.total, 20971520)
const recent = await megaRecentFiles({}, { env, StorageClass:FakeStorage })
assert.deepEqual(recent,[])
assert.equal(closeCount, 3)
console.log('MEGA storage self-test passed.')
