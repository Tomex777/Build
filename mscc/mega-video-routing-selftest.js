import assert from 'node:assert/strict'
import { megaMediaType, routeMegaTarget, megaSharedFolders } from './mega-storage.js'

const env = { MSCC_MEGA_SHARED_FOLDERS:JSON.stringify([
  { id:'video_a', label:'Video A', nodeId:'videoAAA', mediaTypes:['videos'], uploadEnabled:true },
  { id:'video_b', label:'Video B', nodeId:'videoBBB', mediaTypes:['videos'], uploadEnabled:true },
  { id:'video_c', label:'Video C', nodeId:'videoCCC', mediaTypes:['videos'], uploadEnabled:true },
  { id:'photos', label:'Images & Stickers', nodeId:'photoAAA', mediaTypes:['images','stickers'], uploadEnabled:true },
]) }
const shares = megaSharedFolders(env)
const mounts = [
  {nodeId:'videoAAA',directory:true,children:[{size:2500,name:'film.mp4'}]},
  {nodeId:'videoBBB',directory:true,children:[{size:70},{size:130}]},
  {nodeId:'videoCCC',directory:true,children:[{size:250}]},
  {nodeId:'photoAAA',directory:true,children:[]},
]
const selected = type => routeMegaTarget({mediaType:type,shares,mounts}).id
assert.equal(shares.filter(s => s.mediaTypes.includes('videos')).length,3)
assert.equal(selected('videos'),'video_b','choose fewer bytes, not fewer files')
assert.equal(selected('images'),'photos')
assert.equal(selected('stickers'),'photos')
assert.equal(selected('audio'),'primary')
assert.equal(selected('documents'),'primary')
assert.equal(selected('archives'),'primary')
assert.equal(megaMediaType({kind:'video',mimeType:'video/mp4'}),'videos')

// Simulate one more video arriving: the next upload should go to a different
// account without needing a donor ID in the WhatsApp command.
mounts[1].children.push({size:100})
assert.equal(selected('videos'),'video_c')
mounts[2].children.push({size:100})
assert.equal(selected('videos'),'video_b')
assert.throws(
  () => routeMegaTarget({mediaType:'videos',shares,mounts:[mounts[3]]}),
  /No connected writable MEGA folder/
)
assert.throws(
  () => routeMegaTarget({mediaType:'videos',shares:shares.map(s=>({...s,uploadEnabled:false})),mounts}),
  /No connected writable MEGA folder/
)
console.log('PASS 3 video MEGA accounts balance automatically by visible bytes; other media stays separate.')
