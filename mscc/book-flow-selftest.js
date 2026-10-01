import { runBookCommand } from './book-flow.js'

const replies=[]
const lists=[]
let session=null
let input=''

const ctx={
  publicPrefix:'.',
  listSources:()=>[{id:'book-src',name:'Book Source'}],
  setCommandReplySession:value=>{session=value},
  getCommandReplySession:()=>session,
  clearCommandReplySession:()=>{session=null},
  get commandReplyInput(){return input},
  reply:async value=>{replies.push(String(value));return value},
  replyList:async value=>{lists.push(value);return value},
  resolveBookScreens:async ()=>[
    {title:'Dune',tmdbMovieId:438631,tmdbTvId:0},
  ],
  executeSource:async ({payload})=>{
    if(payload.action==='search') return {status:'ok',source:{id:'book-src',name:'Book Source'},result:{items:[
      {id:'dune',title:'Dune',author:'Frank Herbert',year:'1965'},
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
if(!lists.at(-1)?.rows?.some(row=>row.id==='.movie ~tmdb 438631')) throw new Error('Book-to-movie instant reply missing')
input='2'
await runBookCommand(ctx,{args:['~numbers']})
if(!replies.some(x=>x.includes('OK:dune:pdf'))) throw new Error('Book edition download missing')

console.log('PASS book edition and movie relation flow')
