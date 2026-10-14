import assert from 'node:assert/strict'
import { Readable } from 'node:stream'
import {
  megaSharedFolders, megaStorageDashboard, megaRecentFiles, uploadMegaMedia,
} from './mega-storage.js'

const env = {
  MSCC_MEGA_EMAIL:'test@example.invalid',
  MSCC_MEGA_PASSWORD:'not-a-real-password',
  MSCC_MEGA_FOLDER:'Main Archive',
  MSCC_MEGA_SHARED_FOLDERS:JSON.stringify([
    { id:'alex', label:'Alex donated folder', nodeId:'exampleS', uploadEnabled:true },
    { id:'riley', label:'Riley contributed folder', nodeId:'exampleT', uploadEnabled:false },
    { id:'missing', label:'Unavailable', nodeId:'notthere' },
  ]),
}
assert.equal(megaSharedFolders(env).length, 3)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'{broken' }), /valid JSON/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"primary","nodeId":"ABCDEFGH"}]' }), /short identifiers/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"alex","nodeId":"ABCDEFGH"},{"id":"alex","nodeId":"XYZ01234"}]' }), /Duplicate/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"foo","nodeId":"bad/path"}]' }), /valid MEGA node ID/)

let lastTarget = ''
let streamCount = 0
let sessionCount = 0
let closeCount = 0
const upload = name => ({
  directory:true, name,
  children:[],
  upload({ name:fileName, size }, source) {
    lastTarget = name
    return { complete:(async () => {
      let length = 0
      for await (const chunk of source) length += chunk.length
      assert.equal(length,size)
      this.children.push({ name:fileName, size, timestamp:1 })
      return { name:fileName, nodeId:'uploaded' }
    })() }
  },
})
const primary = upload('Main Archive')
const alex = upload('Alex donated folder')
alex.nodeId = 'exampleS'
const riley = upload('Riley contributed folder')
riley.nodeId = 'exampleT'
const unrelated = upload('Unregistered folder')
unrelated.nodeId = 'notRegistered'
class FakeStorage {
  constructor({ email }) {
    assert.equal(email,env.MSCC_MEGA_EMAIL)
    sessionCount++
    this.ready=Promise.resolve()
    this.root={
      find:name=>name==='Main Archive'?primary:undefined,
      mkdir:async()=>primary,
    }
    this.trash = {}
    this.inbox = {}
    this.mounts=[this.root,alex,riley,unrelated]
  }
  async getAccountInfo() { return { spaceUsed:1024, spaceTotal:10_000_000 } }
  async close() { closeCount++ }
}
const opts={ env,StorageClass:FakeStorage }
const dashboard=await megaStorageDashboard(opts)
assert.equal(dashboard.targets.length,4)
assert.equal(dashboard.targets[0].type,'primary')
assert.equal(dashboard.targets[1].detected,true)
assert.equal(dashboard.targets[1].quotaBytes,null)
assert.equal(dashboard.targets[2].uploadEnabled,false)
assert.equal(dashboard.targets[3].detected,false)
assert.equal(dashboard.total,10_000_000)
assert.match(dashboard.note,/not summed/)

const file = { name:'saved.txt', size:4, targetId:'alex', source: async () => { streamCount++; return Readable.from([Buffer.from('test')]) } }
const result=await uploadMegaMedia(file,opts)
assert.equal(result.targetId,'alex')
assert.equal(lastTarget,'Alex donated folder')
assert.equal((await megaRecentFiles({ targetId:'alex' },opts))[0].name,'saved.txt')

await assert.rejects(() => uploadMegaMedia({ ...file,targetId:'riley' },opts),/not enabled/)
await assert.rejects(() => uploadMegaMedia({ ...file,targetId:'missing' },opts),/not mounted/)
await assert.rejects(() => uploadMegaMedia({ ...file,targetId:'unknown' },opts),/Unknown/)
await assert.rejects(() => uploadMegaMedia({ ...file,size:20_000_000,targetId:'primary' },opts),/Not enough/)
assert.equal(streamCount,1,'Rejected uploads never download source media')
assert.equal(sessionCount,closeCount,'Every connection is closed')
console.log('MEGA contributed folder self-test passed.')
