import { runBookCommand } from './book-flow.js'

const replies=[]
const lists=[]
const images=[]
let savedDelivery=null
let session=null
let input=''

const ctx={
  publicPrefix:'.',
  getDeliveryDefault:()=>savedDelivery,
  listSources:()=>[{id:'book-src',name:'Book Source'}],
  setCommandReplySession:value=>{session=value},
  getCommandReplySession:()=>session,
  clearCommandReplySession:()=>{session=null},
  get commandReplyInput(){return input},
  reply:async value=>{replies.push(String(value));return value},
  replyList:async value=>{lists.push(value);return value},
  sendImageUrl:async (url,caption)=>{images.push({url:String(url),caption:String(caption)});return true},
  resolveBookScreens:async ()=>[
    {title:'Dune',tmdbMovieId:438631,tmdbTvId:0},
  ],
  executeSource:async ({payload})=>{
    if(payload.action==='search') return {status:'ok',source:{id:'book-src',name:'Book Source'},result:{items:[
      {id:'dune',title:'Dune',author:'Frank Herbert',year:'1965',cover:'https://example.test/dune.jpg'},
    ]}}
    if(payload.action==='editions') return {status:'ok',source:{id:'book-src'},result:{editions:[
      {id:'epub',title:'EPUB',format:'EPUB',language:'English'},
      {id:'pdf',title:'PDF',format:'PDF',language:'English'},
    ]}}
    if(payload.action==='download') return {status:'ok',source:{id:'book-src'},result:{text:`OK:${payload.itemId}:${payload.editionId}`}}
    throw new Error('Unexpected '+payload.action)
  },
}

await runBookCommand(ctx,{args:['Dune']})
if(session?.stage!=='book') throw new Error('Book selection stage missing')
input='1'
await runBookCommand(ctx,{args:['~numbers']})
if(session?.stage!=='edition') throw new Error('Book edition stage missing')
if(images.length!==1 || images[0].url!=='https://example.test/dune.jpg' || !images[0].caption.includes('Frank Herbert')) {
  throw new Error('Selected book cover/author preview missing')
}
if(!lists.at(-1)?.rows?.some(row=>row.id==='.movie ~tmdb 438631')) throw new Error('Book-to-movie instant reply missing')
input='2'
await runBookCommand(ctx,{args:['~numbers']})
if(!replies.some(x=>x.includes('OK:dune:pdf'))) throw new Error('Book edition download missing')

savedDelivery={quality:'epub',delivery:'document'}
replies.length=0
lists.length=0
session=null
await runBookCommand(ctx,{args:['Dune']})
input='1'
await runBookCommand(ctx,{args:['~numbers']})
if(!replies.some(x=>x.includes('OK:dune:epub'))) throw new Error('Saved EPUB preference was not applied')
if(session!==null) throw new Error('Saved unique book format should skip the edition picker')
if(!lists.some(list=>list.rows?.some(row=>row.id==='.movie ~tmdb 438631'))) {
  throw new Error('Book adaptation link disappeared when saved format was used')
}

console.log('PASS book edition preference and movie relation flow')

const novelDownloads=[]
const novelReplies=[]
let novelSession=null
let novelInput=''
const novelCtx={
  publicPrefix:'.',
  getDeliveryDefault:()=>null,
  listSources:()=>[{id:'novelbuddy',name:'NovelBuddy'}],
  setCommandReplySession:value=>{novelSession=value},
  getCommandReplySession:()=>novelSession,
  clearCommandReplySession:()=>{novelSession=null},
  get commandReplyInput(){return novelInput},
  reply:async value=>{novelReplies.push(String(value));return value},
  sendImageUrl:async ()=>true,
  executeSource:async ({payload})=>{
    if(payload.action==='search') return {
      status:'ok',
      source:{id:'novelbuddy',name:'NovelBuddy'},
      result:{items:[{id:'novel-1',title:'Example Novel',author:'Example Author',cover:'https://example.test/novel.jpg'}]},
    }
    if(payload.action==='editions') return {
      status:'ok',
      source:{id:'novelbuddy',name:'NovelBuddy'},
      result:{
        novel:{id:'novel-1',title:'Example Novel',author:'Example Author'},
        chapters:Array.from({length:20},(_,i)=>({id:'ch-'+(i+1),name:'Chapter '+(i+1)})),
      },
    }
    if(payload.action==='download-chapters'){
      novelDownloads.push(payload)
      return {status:'ok',source:{id:'novelbuddy'},result:{delivered:true}}
    }
    throw new Error('Unexpected novel action '+payload.action)
  },
}

await runBookCommand(novelCtx,{args:['Example','Novel']})
if(novelSession?.stage!=='book') throw new Error('Novel search did not enter book selection stage')
novelInput='1'
await runBookCommand(novelCtx,{args:['~numbers']})
if(novelSession?.stage!=='chapters' || novelSession.entries?.length!==20) {
  throw new Error('Novel did not enter chapter-number selection stage')
}
if(!novelReplies.at(-1)?.includes('1-10') || !novelReplies.at(-1)?.includes('1,3,4,7')) {
  throw new Error('Novel chapter range examples are missing')
}
novelInput='1,3-4,7'
await runBookCommand(novelCtx,{args:['~numbers']})
if(novelDownloads.length!==1) throw new Error('Novel chapter download was not requested')
const selectedIds=novelDownloads[0].chapters.map(chapter=>chapter.id).join('|')
if(selectedIds!=='ch-1|ch-3|ch-4|ch-7') {
  throw new Error('Novel numeric/range selection was not preserved: '+selectedIds)
}
if(novelSession!==null) throw new Error('Novel chapter session was not cleared after download')

console.log('PASS novel typed chapter number/range download flow')
