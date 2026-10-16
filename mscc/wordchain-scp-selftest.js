import assert from 'node:assert/strict'
import { normalizeWord, validWordInput, wordChainInputDestination, wordChainCommand, wordChainStats, WORD_CHAIN_NS, chainKey, checkWord } from './utils/word-chain.js'
import { normalizeScpId, parseScpPage, fetchScp, scpMessage } from './utils/scp-lookup.js'
import wordchainCommand from './commands/games/wordchain.js'
import scpCommand from './commands/search/scp.js'

const data = new Map(), replies = [], sent = []
const shared = {
  get:(ns,key)=>data.get(ns+'|'+key) ?? null,
  set:(ns,key,value)=>data.set(ns+'|'+key,structuredClone(value)),
  delete:(ns,key)=>data.delete(ns+'|'+key),
}
const ctx = (user='alice', chat='room@g.us', account='botA')=>({
  groupKey:chat.endsWith('@g.us')?chat:'', userKey:user, identityKeys:[user],
  message:{key:{remoteJid:chat},pushName:user}, account:{id:account,sock:{sendMessage:async(id,msg)=>{sent.push([id,msg]);return true}}},
  shared, args:[], commandReplyInput:'', reply:async x=>{replies.push(String(x));return true}
})
const run = async (c,args=[],input='',verify=async()=>({ok:true}))=>{
  c.args=args;c.commandReplyInput=input
  return wordChainCommand(c,{verify})
}
assert.equal(normalizeWord(' KÉEP '),'keep')
assert.equal(normalizeWord('keep!'),'')
assert.equal(validWordInput('hello there'),false)
assert.equal(normalizeScpId('scp-682'),'SCP-682')
assert.equal(normalizeScpId('49'),'SCP-049')
assert.equal(normalizeScpId('javascript:alert(1)'),'')
assert.ok(!wordChainInputDestination({shared,chat:'room@g.us',accountId:'botA',text:'BOOK'}))
const a=ctx(),b=ctx('bob'),outsider=ctx('eve')
await run(a)
assert.ok(replies.at(-1).includes('WORD CHAIN STARTED'))
assert.ok(wordChainInputDestination({shared,accountId:'botA',chat:'room@g.us',text:'BOOK'}))
assert.equal(wordChainInputDestination({shared,accountId:'botB',chat:'room@g.us',text:'BOOK'}),false)
assert.equal(wordChainInputDestination({shared,accountId:'botA',chat:'room@g.us',text:'what are you doing'}),false)
await run(a,['~input'],'BOOK')
assert.ok(replies.at(-1).includes('→ *K*'))
await run(a,['~input'],'KEEP')
assert.match(replies.at(-1),/Somebody else/)
await run(b,['~input'],'KEEP')
assert.ok(replies.at(-1).includes('→ *P*'))
await run(a,['~input'],'PATRICK')
assert.ok(replies.at(-1).includes('→ *K*'))
await run(b,['~input'],'BOOK')
assert.ok(replies.at(-1).includes('Start with *K*'))
await run(b,['~input'],'KEEP')
assert.ok(replies.at(-1).includes('Already used'))
await run(b,['~input'],'KITE')
assert.ok(replies.at(-1).includes('→ *E*'))
await run(outsider,['stop'])
assert.ok(replies.at(-1).includes('Only the host'))
await run(a,['stop'])
assert.ok(replies.at(-1).includes('4 words'))
assert.equal(wordChainStats(a).best,4)
assert.equal(wordChainInputDestination({shared,accountId:'botA',chat:'room@g.us',text:'ECHO'}),false)
await run(a,['duel'])
assert.ok(replies.at(-1).includes('WORD CHAIN DUEL'))
await run(b,['join'])
const active=shared.get(WORD_CHAIN_NS,chainKey('botA','room@g.us'))
assert.equal(active.host,'alice')
assert.equal(active.opponent,'bob')
await run(outsider,['~input'],'BOOK')
assert.ok(replies.at(-1).includes('two players'))
await run(b,['~input'],'BOOK')
assert.ok(replies.at(-1).includes('challenger takes the first turn'))
await run(a,['~input'],'BOOK')
assert.ok(replies.at(-1).includes('→ *K*'))
await run(b,['~input'],'KEEP')
assert.ok(replies.at(-1).includes('→ *P*'))
await run(a,['stop'])
const stale=ctx('jane','new-room@g.us')
await run(stale)
const record=shared.get(WORD_CHAIN_NS,chainKey('botA','new-room@g.us'))
record.turnDeadline=Date.now()-1000;shared.set(WORD_CHAIN_NS,chainKey('botA','new-room@g.us'),record)
await run(stale,['status'])
assert.ok(replies.at(-1).includes('turn timer expired'))
const dict=await checkWord('book',{lookup:async()=>[{meanings:[{partOfSpeech:'noun'}]}]})
assert.equal(dict.ok,true)
const unknown=await checkWord('abcdefghijklmnopqrstu',{lookup:async()=>false,allowNames:false})
assert.equal(unknown.ok,false)
const failure=await checkWord('abcdefghijklmnopqrstuv',{lookup:async()=>{throw Error('offline')},allowNames:false})
assert.equal(failure.unavailable,true)

const html=`<html><div id="page-content"><p><strong>Item #:</strong> SCP-682</p>
<p><strong>Object Class:</strong> Keter</p><p><strong>Special Containment Procedures:</strong> Restricted.</p>
<p><strong>Description:</strong> A large reptile-like anomalous creature, capable of regeneration.</p>
<p><strong>Addendum:</strong> Further classified information.</p></div></html>`
const parsed=parseScpPage(html,'SCP-682')
assert.equal(parsed.objectClass,'Keter')
assert.match(parsed.description,/large reptile-like/)
assert.doesNotMatch(parsed.description,/Further classified/)
const mockFetch=async()=>({ok:true,status:200,headers:{get:()=>null},text:async()=>html})
const fetched=await fetchScp('682',{request:mockFetch})
assert.equal(fetched.id,'SCP-682')
assert.match(scpMessage(fetched),/CC BY-SA 3.0/)
assert.match(scpMessage(fetched),/scp-wiki.wikidot.com/)
assert.equal(wordchainCommand.name,'wordchain')
assert.equal(scpCommand.name,'scp')
assert.equal(typeof wordchainCommand.run,'function')
assert.equal(typeof scpCommand.run,'function')
console.log('PASS Word Chain: last-letter, no repeats, chat/tenant isolation, duel, timer, scores, dictionary; SCP: parsing, provenance, input safety')
