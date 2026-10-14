import assert from 'node:assert/strict'
import { megaVerifyFolder } from './mega-storage.js'

const env={
  MSCC_MEGA_EMAIL:'probe@example.invalid',
  MSCC_MEGA_PASSWORD:'not-secret-test',
  MSCC_MEGA_SHARED_FOLDERS:JSON.stringify([
    {id:'video_a',nodeId:'videoAAA',label:'Video A',mediaTypes:['videos'],uploadEnabled:true},
    {id:'video_b',nodeId:'videoBBB',label:'Video B',mediaTypes:['videos'],uploadEnabled:false},
  ]),
}
let deleted=0, uploaded=0, downloaded=0, closed=0
let corrupt=false
function folder(name,nodeId){
  return {name,nodeId,directory:true,children:[],
    upload({name:filename,size},source){
      uploaded++
      return {complete:(async()=>{
        const data=[]
        for await(const chunk of source)data.push(Buffer.from(chunk))
        const input=Buffer.concat(data)
        assert.equal(size,64)
        assert.equal(input.length,64)
        assert(filename.startsWith('mscc-probe-'))
        return {downloadBuffer:async()=>{downloaded++;return corrupt?Buffer.alloc(64):input},
          delete:async(permanent)=>{assert.equal(permanent,true);deleted++}}
      })()}
    }
  }
}
const primary=folder('MSCC Uploads','primaryaa')
const video=folder('Video A','videoAAA')
const disabled=folder('Video B','videoBBB')
class StorageClass {
  constructor(opts) {
    assert.equal(opts.email,env.MSCC_MEGA_EMAIL)
    this.ready=Promise.resolve()
    this.mounts=[video,disabled]
    this.root={find:()=>primary,mkdir:async()=>primary}
    this.trash={}
    this.inbox={}
  }
  async close(){closed++}
}
const options={env,StorageClass}
const result=await megaVerifyFolder({targetId:'video_a'},options)
assert.equal(result.success,true)
assert.equal(result.donorVerified,false)
assert.equal(result.location,'Video A')
assert.match(result.warning,/contributor|donor/i)
const primaryResult=await megaVerifyFolder({targetId:'primary'},options)
assert.equal(primaryResult.success,true)
await assert.rejects(()=>megaVerifyFolder({targetId:'video_b'},options),/not approved/)
await assert.rejects(()=>megaVerifyFolder({targetId:'unexpected'},options),/not approved/)
corrupt=true
await assert.rejects(()=>megaVerifyFolder({targetId:'video_a'},options),/integrity check/)
assert.equal(uploaded,3)
assert.equal(downloaded,3)
assert.equal(deleted,3,'Even corrupt probes are cleaned up')
assert.equal(closed,3,'MEGA sessions always close')
console.log('MEGA readback verification selftest passed.')
