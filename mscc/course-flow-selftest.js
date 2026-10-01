import { runCourseCommand } from './course-flow.js'

const replies=[]
const calls=[]
let session=null
let input=''

const ctx={
  publicPrefix:'.',
  listSources:()=>[{id:'course-src',name:'Course Source'}],
  setCommandReplySession:value=>{session=value},
  getCommandReplySession:()=>session,
  clearCommandReplySession:()=>{session=null},
  get commandReplyInput(){return input},
  reply:async value=>{replies.push(String(value));return value},
  replyList:async value=>value,
  executeSource:async ({payload})=>{
    calls.push(payload)
    if(payload.action==='search') return {status:'ok',source:{id:'course-src',name:'Course Source'},result:{items:[
      {id:'js',title:'JavaScript Course',instructor:'Tutor'},
    ]}}
    if(payload.action==='contents') return {status:'ok',source:{id:'course-src'},result:{contents:[
      {id:'p1',title:'Intro',section:'Module 1',type:'video'},
      {id:'p2',title:'Variables',section:'Module 1',type:'video'},
      {id:'p3',title:'Functions',section:'Module 2',type:'video'},
    ]}}
    if(payload.action==='download') return {status:'ok',source:{id:'course-src'},result:{text:'OK:'+payload.contentId}}
    throw new Error('Unexpected '+payload.action)
  },
}

await runCourseCommand(ctx,{args:['javascript']})
if(session?.stage!=='course') throw new Error('Course selection stage missing')
input='1'
await runCourseCommand(ctx,{args:['~numbers']})
if(session?.stage!=='content'||!replies.at(-1)?.includes('1-4')) throw new Error('Course content stage missing')
input='1,3'
await runCourseCommand(ctx,{args:['~numbers']})
const downloads=calls.filter(x=>x.action==='download').map(x=>x.contentId)
if(downloads.join('|')!=='p1|p3') throw new Error('Course numeric selection failed')

console.log('PASS course numbered-content flow')
