import assert from 'node:assert/strict'
import { Readable } from 'node:stream'
import {
  megaSharedFolders, megaMediaType, routeMegaTarget, megaStorageDashboard,
  megaRecentFiles, uploadMegaMedia,
} from './mega-storage.js'

const env = {
  MSCC_MEGA_EMAIL:'test@example.invalid',
  MSCC_MEGA_PASSWORD:'not-a-real-password',
  MSCC_MEGA_FOLDER:'Main Archive',
  MSCC_MEGA_SHARED_FOLDERS:JSON.stringify([
    { id:'alex', label:'Photos bucket', nodeId:'exampleS', mediaTypes:['images', 'stickers'], uploadEnabled:true },
    { id:'riley', label:'Documents bucket', nodeId:'exampleT', mediaTypes:['documents'], uploadEnabled:false },
    { id:'missing', label:'Archives bucket', nodeId:'notthere', mediaTypes:['archives'], uploadEnabled:true },
  ]),
}
assert.equal(megaSharedFolders(env).length, 3)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'{broken' }), /valid JSON/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"primary","nodeId":"ABCDEFGH"}]' }), /short identifiers/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"alex","nodeId":"ABCDEFGH"},{"id":"alex","nodeId":"XYZ01234"}]' }), /Duplicate/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"foo","nodeId":"bad/path"}]' }), /valid MEGA node ID/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"foo","nodeId":"ABCDEFGH","mediaTypes":["images","images"]}]' }), /duplicate MEGA mediaTypes/)
assert.throws(() => megaSharedFolders({ MSCC_MEGA_SHARED_FOLDERS:'[{"id":"foo","nodeId":"ABCDEFGH","mediaTypes":["unknown"]}]' }), /Invalid/)

assert.equal(megaMediaType({ kind:'sticker',mimeType:'image/webp' }), 'stickers')
assert.equal(megaMediaType({ mimeType:'image/png' }), 'images')
assert.equal(megaMediaType({ mimeType:'video/mp4' }), 'videos')
assert.equal(megaMediaType({ name:'album.flac' }), 'audio')
assert.equal(megaMediaType({ name:'archive.zip',mimeType:'application/zip' }), 'archives')
assert.equal(megaMediaType({ name:'report.pdf' }), 'documents')

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
      this.children.push({ name:fileName, size, timestamp:Date.now() })
      return { name:fileName, nodeId:'uploaded' }
    })() }
  },
})
const primary = upload('Main Archive')
const alex = upload('Photos bucket')
alex.nodeId = 'exampleS'
const riley = upload('Documents bucket')
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
assert.deepEqual(dashboard.targets[1].mediaTypes,['images','stickers'])
assert.equal(dashboard.targets[1].quotaBytes,null)
assert.equal(dashboard.targets[2].uploadEnabled,false)
assert.equal(dashboard.targets[3].detected,false)
assert.equal(dashboard.total,10_000_000)
assert.match(dashboard.note,/not summed/)

const routes=megaSharedFolders(env)
assert.equal(routeMegaTarget({ mediaType:'videos',shares:routes,mounts:[alex,riley] }).id,'primary')
assert.equal(routeMegaTarget({ mediaType:'images',shares:routes,mounts:[alex,riley] }).id,'alex')
assert.throws(() => routeMegaTarget({ mediaType:'documents',shares:routes,mounts:[alex,riley] }),/No connected writable/)
assert.throws(() => routeMegaTarget({ mediaType:'archives',shares:routes,mounts:[alex,riley] }),/No connected writable/)

const stream = () => { streamCount++; return Readable.from([Buffer.from('test')]) }
const photo=await uploadMegaMedia({ name:'saved.png',size:4,kind:'image',source:stream },opts)
assert.equal(photo.targetId,'alex')
assert.equal(photo.mediaType,'images')
assert.equal(lastTarget,'Photos bucket')
const sticker=await uploadMegaMedia({ name:'sticker.webp',size:4,kind:'sticker',source:stream },opts)
assert.equal(sticker.targetId,'alex')
const recent=await megaRecentFiles({},opts)
assert.equal(recent.length,2)
assert(recent.every(file=>file.location==='Photos bucket'))
const video=await uploadMegaMedia({ name:'video.mp4', size:4,kind:'video',source:stream },opts)
assert.equal(video.targetId,'primary')
assert.equal(lastTarget,'Main Archive')

await assert.rejects(() => uploadMegaMedia({ name:'report.pdf',size:4,kind:'document',source:stream },opts),/No connected writable/)
await assert.rejects(() => uploadMegaMedia({ name:'backup.zip',size:4,kind:'document',source:stream },opts),/No connected writable/)
await assert.rejects(() => uploadMegaMedia({ name:'video.mp4',size:20_000_000,kind:'video',source:stream },opts),/Not enough/)
assert.equal(streamCount,3,'Rejected uploads never download source media')
assert.equal(sessionCount,closeCount,'Every connection is closed')
console.log('MEGA media-type auto routing and contributed folder self-test passed.')
