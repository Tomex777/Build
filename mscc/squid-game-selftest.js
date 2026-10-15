import assert from 'node:assert/strict'
import { createSquidGame } from './squid-game.js'

const CHAT = '120363000000000000@g.us'
const ADMIN = '11111111111@s.whatsapp.net'
const PLAYER_A = '22222222222@s.whatsapp.net'
const PLAYER_B = '33333333333@s.whatsapp.net'
const PLAYER_C = '44444444444@s.whatsapp.net'
let clock = Date.now() + 1000000
let counter = 0

function harness() {
  const store = new Map()
  const state = {
    announce:false,
    participants:[
      { id:ADMIN, admin:'superadmin' },
      { id:PLAYER_A, admin:null },
      { id:PLAYER_B, admin:null },
      { id:PLAYER_C, admin:null },
    ],
    sent:[], actions:[],
  }
  const sock = {
    async sendMessage(group, payload) {
      assert.equal(group, CHAT)
      state.sent.push(payload)
      return { key:{ id:'game-message-' + ++counter } }
    },
    async groupMetadata(group) {
      assert.equal(group, CHAT)
      return { id:group, announce:state.announce, participants:state.participants.map(p => ({ ...p })) }
    },
    async groupSettingUpdate(group, type) {
      assert.equal(group, CHAT)
      state.announce = type === 'announcement'
      state.actions.push(type)
      return true
    },
    async groupParticipantsUpdate(group, ids, operation) {
      assert.equal(group, CHAT)
      state.actions.push(operation + ':' + ids[0])
      const p = state.participants.find(x => x.id === ids[0])
      if (!p) return [{ status:404 }]
      if (operation === 'remove') state.participants = state.participants.filter(x => x.id !== ids[0])
      if (operation === 'promote') p.admin = 'admin'
      if (operation === 'demote') p.admin = null
      return [{ status:200 }]
    },
  }
  const account = { id:'A', sock, connected:true }
  const storage = {
    sharedGet:(ns,key) => store.get(ns + ':' + key) ?? null,
    sharedSet:(ns,key,value) => store.set(ns + ':' + key,value),
    sharedDelete:(ns,key) => store.delete(ns + ':' + key),
  }
  const engine = createSquidGame({
    storage:() => storage, accountFor:() => account,
    now:() => clock, randomDuration:(min) => min,
  })
  const adminContext = {
    account, groupKey:CHAT, args:[], message:{ key:{ remoteJid:CHAT, participant:ADMIN }},
    isBotGroupAdmin:async () => true,
    isPublicOwner:false,
    isSupremeOwner:false,
    reply:async text => state.sent.push({ text }),
  }
  const react = async (user, emoji = '✅') => {
    const game = engine.get(CHAT)
    assert.ok(game)
    return engine.onReaction(account, {
      key:{ id:game.registrationId, remoteJid:CHAT },
      reaction:{ key:{ participant:user, fromMe:false }, text:emoji },
    })
  }
  const talk = async (user,text,{ forward = false } = {}) => {
    const content = forward
      ? { extendedTextMessage:{text, contextInfo:{isForwarded:true, forwardingScore:1}} }
      : { conversation:text }
    return engine.onMessage(account, {
      key:{remoteJid:CHAT, participant:user, fromMe:false, id:'incoming-' + ++counter},
      messageTimestamp:Math.floor(clock / 1000),
      message:content,
    })
  }
  return { state, account, storage, engine, adminContext, react, talk }
}

// Kick mode: registered participants only, admin never targeted, three original
// distinct messages, previous-round duplicate detection and green cut-off.
{
  const {state,engine,adminContext,react,talk} = harness()
  await engine.start(adminContext,'kick')
  assert.match(state.sent[0].text,/3 different/)
  await react(PLAYER_A)
  await react(PLAYER_B)
  await react(ADMIN)
  assert.equal(Object.keys(engine.get(CHAT).players).length,2)
  await react(PLAYER_C)
  await react(PLAYER_C,'')
  assert.equal(Object.keys(engine.get(CHAT).players).length,2)
  clock += 60001
  await engine.tick()
  assert.equal(engine.get(CHAT).phase,'GREEN')
  assert.equal(await talk(PLAYER_A,'hi'),true)
  await talk(PLAYER_A,'HI!!')
  await talk(PLAYER_A,'hello')
  await talk(PLAYER_A,'good morning')
  await talk(PLAYER_B,'hello')
  await talk(PLAYER_B,'forwarded',{forward:true})
  await talk(PLAYER_B,'hello')
  assert.equal(engine.get(CHAT).players[PLAYER_A].roundTexts.length,3)
  assert.equal(engine.get(CHAT).players[PLAYER_B].roundTexts.length,1)
  clock += 23000
  await engine.tick()
  assert.equal(engine.get(CHAT).phase,'RED')
  clock += 1900
  await engine.tick()
  assert.ok(state.actions.includes('remove:' + PLAYER_B))
  assert.ok(!state.actions.some(a => a.includes('remove:' + ADMIN)))
  assert.equal(engine.get(CHAT),null) // champion after first green elimination
  assert.match(state.sent.at(-1).text,/CHAMPION/)
}

// CG mode: opted-in players become temporary admins, after elimination
// they lose privileges, while the original admin and group state stay intact.
{
  clock += 100000
  const {state,engine,adminContext,react,talk} = harness()
  await engine.start(adminContext,'cg')
  await react(PLAYER_A)
  await react(PLAYER_B)
  await react(PLAYER_C)
  clock += 60001
  await engine.tick()
  assert.equal(state.announce,true)
  assert.equal(state.participants.filter(p=>p.admin==='admin').length,3)
  await talk(PLAYER_A,'alpha')
  await talk(PLAYER_A,'beta')
  await talk(PLAYER_A,'gamma')
  await talk(PLAYER_B,'delta')
  await talk(PLAYER_B,'epsilon')
  await talk(PLAYER_B,'zeta')
  await talk(PLAYER_C,'theta')
  await talk(PLAYER_C,'iota')
  await talk(PLAYER_C,'kappa')
  clock += 23000
  await engine.tick()
  clock += 1900
  await engine.tick()
  assert.equal(engine.get(CHAT).phase,'RED')
  await talk(PLAYER_B,'WHOOPS')
  assert.ok(state.actions.includes('demote:' + PLAYER_B))
  assert.equal(engine.get(CHAT).phase,'RED')
  await talk(PLAYER_C,'WHOOPS AGAIN')
  assert.equal(engine.get(CHAT),null)
  assert.equal(state.announce,false)
  assert.equal(state.participants.find(p=>p.id===ADMIN).admin,'superadmin')
  assert.ok(state.participants.every(p=>p.id===ADMIN || p.admin===null))
}

// CG stop is restricted to original admins, not newly promoted players.
{
  clock += 100000
  const {state,engine,adminContext,react} = harness()
  await engine.start(adminContext,'cg')
  await react(PLAYER_A)
  await react(PLAYER_B)
  clock += 60001
  await engine.tick()
  await engine.stop({...adminContext,message:{key:{remoteJid:CHAT,participant:PLAYER_A}}})
  assert.equal(engine.get(CHAT).phase,'GREEN')
  await engine.stop(adminContext)
  assert.equal(engine.get(CHAT),null)
  assert.equal(state.announce,false)
}

console.log('Squid Game selftest passed')
