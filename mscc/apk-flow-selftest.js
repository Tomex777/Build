import { runApkCommand } from './apk-flow.js'

const calls=[]
const replies=[]
let session=null
let input=''

const ctx={
  publicPrefix:'.',
  listSources:()=>[{id:'apk-src',name:'APK Source'}],
  setCommandReplySession:value=>{session=value},
  getCommandReplySession:()=>session,
  clearCommandReplySession:()=>{session=null},
  get commandReplyInput(){return input},
  reply:async value=>{replies.push(String(value));return value},
  replyList:async value=>value,
  executeSource:async ({payload})=>{
    calls.push(payload)
    if(payload.action==='search') return {status:'ok',source:{id:'apk-src',name:'APK Source'},result:{items:[
      {id:'app1',title:'Example App',packageName:'com.example.app',developer:'Example'},
      {id:'app2',title:'Example App Lite',packageName:'com.example.lite'},
    ]}}
    if(payload.action==='versions') return {status:'ok',source:{id:'apk-src'},result:{versions:[
      {id:'v1',version:'2.0',versionCode:20},
      {id:'v0',version:'1.0',versionCode:10},
    ]}}
    if(payload.action==='variants') return {status:'ok',source:{id:'apk-src'},result:{variants:[
      {id:'arm64',title:'arm64-v8a',architecture:'arm64-v8a',minSdk:26,size:'40 MB'},
      {id:'uni',title:'Universal',architecture:'universal',minSdk:26,size:'60 MB'},
    ]}}
    if(payload.action==='download') return {status:'ok',source:{id:'apk-src'},result:{text:`OK:${payload.itemId}:${payload.versionId}:${payload.variantId}`}}
    throw new Error('Unexpected '+payload.action)
  },
}

await runApkCommand(ctx,{args:['Example']})
if(session?.stage!=='app'||!replies.at(-1)?.includes('Reply with the app number')) throw new Error('APK app selection stage missing')
const appLines=replies.at(-1).split('\n')
if(appLines.some(line=>line.includes('com.example.app'))) throw new Error('APK result line still exposes long package metadata')
if(!appLines.some(line=>line==='1. Example App')) throw new Error('APK result title line is not compact')
input='1'
await runApkCommand(ctx,{args:['~numbers']})
if(session?.stage!=='version'||!replies.at(-1)?.includes('Reply with the version number')) throw new Error('APK version stage missing')
input='1'
await runApkCommand(ctx,{args:['~numbers']})
if(session?.stage!=='variant'||!replies.at(-1)?.includes('Reply with the variant number')) throw new Error('APK variant stage missing')
input='1'
await runApkCommand(ctx,{args:['~numbers']})
if(!replies.some(x=>x.includes('OK:app1:v1:arm64'))) throw new Error('APK download stage missing')

console.log('PASS APK result/version/variant flow')
