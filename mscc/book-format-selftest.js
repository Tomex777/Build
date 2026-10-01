import bookFormatCommand from './commands/books/bookformat.js'

const store=new Map()
const replies=[]
const ctx={
  publicPrefix:'.',
  userKey:'2341',
  args:[],
  shared:{
    get:(namespace,key)=>store.get(namespace+'|'+key) ?? null,
    set:(namespace,key,value)=>{store.set(namespace+'|'+key,value);return value},
    delete:(namespace,key)=>store.delete(namespace+'|'+key),
  },
  reply:async value=>{replies.push(String(value));return value},
}

ctx.args=['epub']
await bookFormatCommand.run(ctx)
if(store.get('book-format-default|2341')?.format!=='epub') throw new Error('EPUB preference was not saved')

ctx.args=[]
await bookFormatCommand.run(ctx)
if(!replies.at(-1)?.includes('EPUB')) throw new Error('Saved book format was not reported')

ctx.args=['pdf']
await bookFormatCommand.run(ctx)
if(store.get('book-format-default|2341')?.format!=='pdf') throw new Error('PDF preference did not replace EPUB')

ctx.args=['clear']
await bookFormatCommand.run(ctx)
if(store.has('book-format-default|2341')) throw new Error('Book format preference was not cleared')

console.log('PASS saved book format command')
