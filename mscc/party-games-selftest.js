import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import { SONGS, ANIME, SPEED_LINES, NAMESPACE, isCorrectGuess, partyInputDestination, newRound,
  readRound, scoreText } from './utils/party-games.js'
import { synthesizeMelody, noteFrequency, melodyAudio } from './utils/party-melodies.js'
import { renderSpeedTypeChallenge } from './utils/speedtype-renderer.js'

const registry=await loadCommands(new URL('./commands/', import.meta.url),{
  capabilityFromDirectory:true,
})
for(const name of ['guesssong','guessanime','speedtype']) {
  assert.ok(registry.commands.has(name),'Missing public challenge: '+name)
}
const memory=new Map(),sent=[],replies=[]
const shared={
  get:(ns,key)=>memory.get(ns+'|'+key)??null,
  set:(ns,key,value)=>memory.set(ns+'|'+key,structuredClone(value)),
  delete:(ns,key)=>memory.delete(ns+'|'+key),
}
function ctxFor(user='alice',chat='room@g.us',account='a') {
  return {
    args:[], commandReplyInput:'',
    userKey:user, groupKey:chat, shared,
    message:{key:{remoteJid:chat,id:'message-'+user},pushName:user},
    account:{id:account,sock:{sendMessage:async(to,msg)=>{sent.push({to,msg});return {key:{id:'sent'}}}}},
    reply:async text=>{replies.push(String(text));return true},
  }
}
async function invoke(type,ctx,args=[],input='') {
  ctx.args=args
  ctx.commandReplyInput=input
  return registry.commands.get(type).run(ctx)
}
const ctxA=ctxFor()
const ctxB=ctxFor('bob')

// All original prompts unique, recognizable and contain no paid-provider dependency.
assert.equal(new Set(SONGS.map(x=>x.id)).size,SONGS.length)
assert.equal(new Set(ANIME.map(x=>x.id)).size,ANIME.length)
assert.ok(SONGS.length>=10 && ANIME.length>=20 && SPEED_LINES.length>=20)
assert.ok(Math.abs(noteFrequency('A4')-440)<.001)
const wav=synthesizeMelody(['E4','G4'])
assert.equal(wav.toString('ascii',0,4),'RIFF')
assert.ok(wav.length>20000)
assert.ok((await melodyAudio(SONGS[0].id)).length>1000)

const typingCard=renderSpeedTypeChallenge(SPEED_LINES[0])
assert.ok(typingCard.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))
// A newly created song round sends audio and a clue, without publishing answer.
await invoke('guesssong',ctxA)
let song=readRound(ctxA,'guesssong')
assert.ok(song)
assert.ok(sent.some(x=>x.msg.audio?.length>1000&&x.msg.mimetype==='audio/mpeg'))
assert.ok(replies.at(-1).includes('GUESS THE SONG'))
assert.equal(partyInputDestination({shared,accountId:'a',chat:'room@g.us',text:'ordinary group chat'}),null)
assert.equal(partyInputDestination({shared,accountId:'b',chat:'room@g.us',text:song.answer}),null)
assert.equal(partyInputDestination({shared,accountId:'a',chat:'other@g.us',text:song.answer}),null)
assert.equal(partyInputDestination({shared,accountId:'a',chat:'room@g.us',text:song.answer}),'guesssong')
assert.equal(isCorrectGuess(song,'wrong answer'),false)
await invoke('guessanime',ctxB)
assert.ok(replies.at(-1).includes('Finish the current Guess the Song'))
await invoke('guesssong',ctxA,['hint'])
song=readRound(ctxA,'guesssong')
assert.equal(song.hints,1)
await invoke('guesssong',ctxB,['~input'],song.answer)
assert.equal(readRound(ctxA,'guesssong'),null)
assert.ok(replies.at(-1).includes('bob wins Guess the Song'))
assert.ok(scoreText(ctxA,'guesssong').includes('75 pts'))
// Subsequent answer cannot award more points.
await invoke('guesssong',ctxB,['answer',song.answer])
assert.ok(replies.at(-1).includes('No active'))

await invoke('guessanime',ctxA)
let anime=readRound(ctxA,'guessanime')
assert.ok(anime && replies.at(-1).includes('GUESS THE ANIME'))
const sample=ANIME.find(x=>x.id===anime.selectedId)
assert.ok(!replies.at(-1).includes('Answer: '+anime.answer))
await invoke('guessanime',ctxB,['hint'])
anime=readRound(ctxA,'guessanime')
assert.equal(anime.hints,1)
await invoke('guessanime',ctxB,['answer',...(sample.aliases?.[0]||sample.title).split(' ')])
assert.equal(readRound(ctxA,'guessanime'),null)
assert.ok(scoreText(ctxA,'guessanime').includes('75 pts'))

await invoke('speedtype',ctxA)
let typing=readRound(ctxA,'speedtype')
assert.ok(sent.at(-1).msg.image?.length>1000)
assert.ok(!sent.at(-1).msg.caption.includes(typing.answer))
assert.equal(partyInputDestination({shared,accountId:'a',chat:'room@g.us',text:'hello everyone'}),null)
assert.equal(partyInputDestination({shared,accountId:'a',chat:'room@g.us',text:typing.answer}),'speedtype')
await invoke('speedtype',ctxB,['~input'],typing.answer+' TYPO')
assert.ok(readRound(ctxA,'speedtype'))
await invoke('speedtype',ctxB,['~input'],typing.answer)
assert.equal(readRound(ctxA,'speedtype'),null)
assert.ok(scoreText(ctxA,'speedtype').includes('bob'))
// Host-only cancel authorization.
await invoke('guessanime',ctxA)
await invoke('guessanime',ctxB,['stop'])
assert.ok(readRound(ctxA,'guessanime'))
await invoke('guessanime',ctxA,['stop'])
assert.equal(readRound(ctxA,'guessanime'),null)
// Expired rounds aren't winnable.
const selected=ANIME[0]
newRound(ctxA,'guessanime',selected,Date.now()-130000)
assert.equal(readRound(ctxA,'guessanime'),null)
await invoke('guessanime',ctxB,['answer','Naruto'])
assert.ok(replies.at(-1).includes('No active'))
assert.equal(partyInputDestination({shared,accountId:'a',chat:'room@g.us',text:'Naruto'}),null)

console.log('PASS guesssong, guessanime and speedtype: media, hints, aliases, scores, isolation and expiry')
