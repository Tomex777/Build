// NIGHT party challenges. Questions and answers live on the bot, not a paid API.
import { randomUUID } from 'node:crypto'

export const CHALLENGE_TYPES = Object.freeze(['guesssong','guessanime','speedtype'])
export const GAME_LABELS = Object.freeze({
  guesssong:'Guess the Song', guessanime:'Guess the Anime', speedtype:'Speed Typing',
})
export const GAME_DURATION = Object.freeze({guesssong:90_000,guessanime:90_000,speedtype:35_000})
export const NAMESPACE = type => 'night-party-' + type
const SCORE_NS = type => 'night-party-scores-' + type
const HISTORY_NS = type => 'night-party-history-' + type

export const SONGS = Object.freeze([
  {id:'fur-elise',title:'Für Elise',aliases:['fur elise'],hint:'A famous piano melody by Beethoven.',hint2:'The title starts with F.',notes:'E5 D#5 E5 D#5 E5 B4 D5 C5 A4'},
  {id:'ode-to-joy',title:'Ode to Joy',hint:'A melody in Beethoven’s Ninth Symphony.',hint2:'It begins with O.',notes:'E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4 D4 D4'},
  {id:'jingle-bells',title:'Jingle Bells',hint:'A holiday song about winter sleigh rides.',hint2:'Its title starts with J.',notes:'E4 E4 E4 E4 E4 E4 E4 G4 C4 D4 E4'},
  {id:'twinkle',title:'Twinkle Twinkle Little Star',aliases:['twinkle twinkle'],hint:'A childhood tune about the night sky.',hint2:'Its first word is Twinkle.',notes:'C4 C4 G4 G4 A4 A4 G4 F4 F4 E4 E4 D4 D4 C4'},
  {id:'mary-lamb',title:'Mary Had a Little Lamb',hint:'A nursery rhyme about a pet.',hint2:'Its title starts with Mary.',notes:'E4 D4 C4 D4 E4 E4 E4 D4 D4 D4 E4 G4 G4'},
  {id:'frere-jacques',title:'Frère Jacques',aliases:['frere jacques','brother john'],hint:'A French nursery song often sung in a round.',hint2:'The first word is Frère.',notes:'C4 D4 E4 C4 C4 D4 E4 C4 E4 F4 G4 E4 F4 G4'},
  {id:'row-boat',title:'Row Row Row Your Boat',aliases:['row your boat'],hint:'A children’s song about travelling on water.',hint2:'It begins with Row.',notes:'C4 C4 C4 D4 E4 E4 D4 E4 F4 G4 C5 C5 C5 G4 G4 G4 E4 E4 E4 C4'},
  {id:'london-bridge',title:'London Bridge Is Falling Down',aliases:['london bridge'],hint:'A nursery rhyme about an English landmark.',hint2:'The first word is London.',notes:'G4 A4 G4 F4 E4 F4 G4 D4 E4 F4 E4 F4 G4'},
  {id:'happy-birthday',title:'Happy Birthday to You',aliases:['happy birthday'],hint:'People sing it at an annual celebration.',hint2:'Its first word is Happy.',notes:'G4 G4 A4 G4 C5 B4 G4 G4 A4 G4 D5 C5'},
  {id:'old-macdonald',title:'Old MacDonald Had a Farm',aliases:['old macdonald'],hint:'A nursery rhyme featuring animal sounds.',hint2:'Its first word is Old.',notes:'G4 G4 G4 D4 E4 E4 D4 B4 B4 A4 A4 G4'},
  {id:'we-wish',title:'We Wish You a Merry Christmas',aliases:['we wish you a merry christmas'],hint:'A festive song traditionally sung in December.',hint2:'Its title begins with We.',notes:'D4 G4 G4 A4 G4 F#4 E4 E4 E4 A4 A4 B4 A4 G4 F#4 D4'},
  {id:'deck-the-halls',title:'Deck the Halls',hint:'A festive carol with repeated fa-la-la phrases.',hint2:'Its title begins with Deck.',notes:'G4 F4 E4 D4 C4 D4 E4 C4 D4 E4 F4 D4 E4 D4 C4 B3 C4'},
])

export const ANIME = Object.freeze([
  {id:'naruto',title:'Naruto',aliases:['naruto shippuden'],clue:'A young ninja dreams of leading his village and carries a dangerous fox spirit.',hints:['Hidden Leaf Village','The hero loves ramen.']},
  {id:'one-piece',title:'One Piece',clue:'A rubber-bodied pirate captain searches for a legendary treasure.',hints:['The Straw Hat Pirates','Monkey D. Luffy']},
  {id:'aot',title:'Attack on Titan',aliases:['shingeki no kyojin','aot'],clue:'Humanity hides behind walls to survive enormous man-eating beings.',hints:['The Survey Corps','Eren Yeager']},
  {id:'death-note',title:'Death Note',clue:'A student discovers a notebook that can kill people whose names are written inside.',hints:['A mysterious detective named L','Light Yagami']},
  {id:'demon-slayer',title:'Demon Slayer',aliases:['kimetsu no yaiba'],clue:'A swordsman fights monsters while trying to cure his younger sister.',hints:['The Hashira','Tanjiro and Nezuko']},
  {id:'jjk',title:'Jujutsu Kaisen',aliases:['jjk'],clue:'A teenager becomes the vessel for an ancient curse after swallowing a dangerous object.',hints:['Jujutsu High','Yuji Itadori and Sukuna']},
  {id:'dragon-ball',title:'Dragon Ball',aliases:['dragon ball z','dragon ball super','dbz'],clue:'Powerful martial artists transform and protect Earth from increasingly formidable enemies.',hints:['Saiyans and seven magical orbs','Goku']},
  {id:'bleach',title:'Bleach',clue:'A high-schooler gains a spirit reaper’s powers and fights supernatural threats.',hints:['Soul Society','Ichigo Kurosaki']},
  {id:'fma',title:'Fullmetal Alchemist: Brotherhood',aliases:['fullmetal alchemist','fmab'],clue:'Two brothers seek a way to restore their bodies after a forbidden alchemical ritual.',hints:['Equivalent exchange','Edward and Alphonse Elric']},
  {id:'mha',title:'My Hero Academia',aliases:['boku no hero academia','bnha'],clue:'A boy born without powers trains to become a superhero.',hints:['U.A. High School','Deku']},
  {id:'hxh',title:'Hunter x Hunter',aliases:['hunter hunter','hunter x hunter 2011'],clue:'A young boy sets out to find his father by becoming a licensed adventurer.',hints:['The Hunter Exam','Gon Freecss']},
  {id:'tokyo-ghoul',title:'Tokyo Ghoul',clue:'After an accident, a college student becomes half-human and half flesh-eating creature.',hints:['Anteiku café','Ken Kaneki']},
  {id:'chainsaw-man',title:'Chainsaw Man',clue:'A debt-ridden hunter merges with his pet devil and gains a brutal new form.',hints:['Public Safety Devil Hunters','Denji and Pochita']},
  {id:'spy-family',title:'Spy x Family',aliases:['spy family'],clue:'A secret agent forms a pretend family with an assassin and a telepath.',hints:['Operation Strix','Anya Forger']},
  {id:'one-punch',title:'One Punch Man',clue:'An overpowered hero defeats nearly every villain with one blow.',hints:['Hero Association','Saitama']},
  {id:'code-geass',title:'Code Geass',clue:'An exiled prince gains the ability to command anyone to obey a single order.',hints:['The Black Knights','Lelouch']},
  {id:'mob',title:'Mob Psycho 100',clue:'A shy young psychic tries to control his emotions and supernatural abilities.',hints:['A fake psychic mentor named Reigen','Shigeo Kageyama']},
  {id:'haikyuu',title:'Haikyuu!!',aliases:['haikyu','haikyuu'],clue:'An undersized student pursues greatness on a high-school volleyball team.',hints:['Karasuno High','Shoyo Hinata']},
  {id:'blue-lock',title:'Blue Lock',clue:'Young strikers compete in an intense program to create the ultimate football forward.',hints:['The Egoist League','Yoichi Isagi']},
  {id:'frieren',title:'Frieren: Beyond Journey’s End',aliases:['frieren','sousou no frieren'],clue:'An elf mage explores what friendship and mortality mean after her party’s adventure is over.',hints:['The Hero Himmel','Frieren']},
  {id:'solo-leveling',title:'Solo Leveling',aliases:['ore dake level up na ken'],clue:'A weak dungeon hunter gains a unique power to become stronger like a game character.',hints:['Shadow soldiers','Sung Jinwoo']},
  {id:'pokemon',title:'Pokémon',aliases:['pokemon'],clue:'A young trainer travels with creature companions and aims to become a champion.',hints:['Team Rocket','Ash and Pikachu']},
  {id:'black-clover',title:'Black Clover',clue:'A magicless orphan wants to become the Wizard King.',hints:['Five-leaf grimoire','Asta']},
  {id:'dr-stone',title:'Dr. Stone',aliases:['dr stone'],clue:'After humanity is petrified, a science prodigy revives civilization using inventions.',hints:['Kingdom of Science','Senku']},
  {id:'vinland',title:'Vinland Saga',clue:'A young Viking follows a ruthless warrior band while seeking revenge.',hints:['Askeladd','Thorfinn']},
])

export const SPEED_LINES = Object.freeze([
  'The fastest fingers do not always win the race.',
  'Purple clouds float quietly across a golden evening sky.',
  'Never trust a penguin wearing a tiny black backpack.',
  'I can type faster than my shadow can dance.',
  'Seven sleepy dragons are guarding a silver sandwich.',
  'Winning is easy when your keyboard is on fire.',
  'The little robot forgot where it parked its spaceship.',
  'Every champion starts with a single correct letter.',
  'Three dancing kittens stole my favourite blue headphones.',
  'Please do not wake the dinosaur before sunrise.',
  'A clever fox discovered the secret pizza recipe.',
  'The moon is having a party without the sun.',
  'My phone thinks I am secretly a superhero.',
  'Nobody expected the chicken to win the marathon.',
  'A thousand tiny stars filled the midnight sky.',
  'The green turtle was surprisingly good at basketball.',
  'Practice makes progress even on a slow keyboard.',
  'Somewhere a sleepy owl is reading a comic book.',
  'I heard that the toaster joined a rock band.',
  'The last slice of pizza belongs to the winner.',
  'Do not challenge a ninja to a typing contest.',
  'Sometimes the shortest message is the hardest to type.',
  'A blue whale is not a very good dancer.',
  'My imaginary dragon needs a brand new bicycle.',
])

export function normalizeGuess(input) {
  return String(input || '').normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/&/g,' and ').replace(/\bx\b/g,'x')
    .replace(/[^a-z0-9]+/g,' ').trim().replace(/\s+/g,' ')
}
export function isCorrectGuess(record, raw) {
  if (!record || !raw || !record.answer) return false
  const guess = normalizeGuess(raw)
  if (!guess || guess.length < 3) return false
  const answers = [record.answer, ...(record.aliases || [])]
  return answers.some(value => guess === normalizeGuess(value))
}
export function isCorrectSpeedline(record, raw) {
  return record?.type === 'speedtype' &&
    normalizeGuess(raw).length >= 18 && normalizeGuess(raw) === normalizeGuess(record.answer)
}
export function chatKey(ctx) {
  const chat = String(ctx.message?.key?.remoteJid || '')
  const account = String(ctx.account?.id || '')
  if (!chat) throw new Error('Chat unavailable.')
  return account + '|' + chat
}
export function readRound(ctx,type,now=Date.now()) {
  const key = chatKey(ctx)
  const game = ctx.shared?.get(NAMESPACE(type),key) || null
  if (!game) return null
  if (game.expiresAt <= now || game.state !== 'PLAYING') {
    ctx.shared?.delete(NAMESPACE(type),key)
    return null
  }
  return game
}
export function newRound(ctx, type, selected, now=Date.now()) {
  const key=chatKey(ctx)
  const history = ctx.shared?.get(HISTORY_NS(type),key) || []
  const bank = type==='guesssong'?SONGS:type==='guessanime'?ANIME:SPEED_LINES.map((line,i)=>({id:'line-'+i,title:line}))
  const choice = selected || (() => {
    const fresh = bank.filter(row => !history.includes(row.id))
    const pool = fresh.length ? fresh : bank
    return pool[Math.floor(Math.random()*pool.length)]
  })()
  ctx.shared?.set(HISTORY_NS(type),key,[...history, choice.id].slice(-Math.min(6,bank.length-1)))
  const record = {
    id:randomUUID(),type,answer:choice.title,
    aliases:choice.aliases || [],selectedId:choice.id,
    host:String(ctx.userKey || ''),hostName:String(ctx.message?.pushName || 'Host').slice(0,50),
    startedAt:now,expiresAt:now+GAME_DURATION[type],state:'PLAYING',hints:0
  }
  ctx.shared?.set(NAMESPACE(type),key,record)
  return {record,choice}
}
export function endRound(ctx,type) {
  ctx.shared?.delete(NAMESPACE(type),chatKey(ctx))
}
export function canManage(ctx,record) {
  return Boolean(record && (record.host === String(ctx.userKey || '') ||
    ctx.isPublicOwner === true || ctx.isSessionOwner === true || ctx.isSupremeOwner === true))
}
export function addScore(ctx,type,user,name,points) {
  const key=chatKey(ctx)
  const data=ctx.shared?.get(SCORE_NS(type),key) || {}
  const prior=data[user] || {name:'Player',score:0,wins:0}
  data[user]={name:String(name || prior.name).slice(0,45),score:prior.score+points,wins:prior.wins+1}
  // Bound the retained scoreboard to prevent unlimited group state growth.
  const ordered=Object.entries(data).sort((a,b)=>b[1].score-a[1].score).slice(0,75)
  ctx.shared?.set(SCORE_NS(type),key,Object.fromEntries(ordered))
  return data[user]
}
export function scoreText(ctx,type) {
  const data=ctx.shared?.get(SCORE_NS(type),chatKey(ctx)) || {}
  const rows=Object.values(data).sort((a,b)=>b.score-a.score).slice(0,10)
  return rows.length
    ? '🏆 *'+GAME_LABELS[type]+' leaderboard*\n'+rows.map((row,i)=>
      (i+1)+'. '+row.name+' — '+row.score+' pts ('+row.wins+' wins)').join('\n')
    : 'No '+GAME_LABELS[type]+' scores yet. Start a round!'
}
export function scoreForRound(record, now=Date.now()) {
  if (record.type==='speedtype') {
    return Math.max(100,Math.round(100+Math.max(0,record.expiresAt-now)/1000*3))
  }
  return Math.max(40,100-record.hints*25)
}
export function remaining(record,now=Date.now()) {
  return Math.max(0,Math.ceil((record.expiresAt-now)/1000))
}
export function partyInputDestination({shared, accountId,chat,text, now=Date.now()}={}) {
  if (!shared || !chat || !text || String(text).trim().startsWith('.')) return null
  const key=String(accountId || '')+'|'+chat
  // Plain text is intercepted only if it is EXACTLY correct: ordinary group
  // conversation and unrelated bot features must keep functioning.
  for (const type of CHALLENGE_TYPES) {
    const record=shared.get(NAMESPACE(type),key)
    if (!record || record.state!=='PLAYING' || record.expiresAt<=now) continue
    if (type==='speedtype' ? isCorrectSpeedline(record,text) : isCorrectGuess(record,text)) return type
  }
  return null
}
