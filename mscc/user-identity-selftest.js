import assert from 'node:assert/strict'
import { canonicalUserKey, identityKeyForJid, readAuthLidPhone, recordVerifiedLidPhone,
  storedLidPhone, verifiedIdentityKeys, verifiedPhoneNumberFromJid,
  hasConflictingLidMapping } from './user-identity.js'
import { jidPhoneNumber } from './session-policy.js'
import { userForStoredGame } from './utils/game-session.js'
import { addScore, scoreText } from './utils/party-games.js'
const PN='2348012345678@s.whatsapp.net', LID='112233445566@lid', PHONE='2348012345678'
const storageData=new Map()
const storage={
  sharedGet:(ns,key)=>storageData.get(ns+'|'+key)??null,
  sharedSet:(ns,key,val)=>storageData.set(ns+'|'+key,structuredClone(val)),
  sharedDelete:(ns,key)=>storageData.delete(ns+'|'+key),
}
assert.equal(jidPhoneNumber(LID),'')
assert.equal(jidPhoneNumber(PHONE+':2@s.whatsapp.net'),PHONE)
assert.equal(jidPhoneNumber(PHONE),'')
assert.equal(verifiedPhoneNumberFromJid(LID),'')
assert.equal(identityKeyForJid(LID),'jid:'+LID)
assert.equal(canonicalUserKey(storage,LID),'jid:'+LID)
assert.equal(await readAuthLidPhone({get:async(type,ids)=>({[ids[0]]:PHONE})},LID),PN)
recordVerifiedLidPhone(storage,LID,PN)
assert.equal(storedLidPhone(storage,LID),PN)
assert.equal(canonicalUserKey(storage,LID),PHONE)
assert.deepEqual(verifiedIdentityKeys(storage,PN),[PHONE,'jid:'+LID])
const ctx={userKey:PHONE,identityKeys:verifiedIdentityKeys(storage,PN),
  message:{key:{remoteJid:'group@g.us'}},account:{id:'a'},
  shared:{get:(ns,key)=>storage.sharedGet(ns,key),set:(ns,key,val)=>storage.sharedSet(ns,key,val)}}
assert.equal(userForStoredGame(ctx,{game:{playerWhite:'jid:'+LID}}),'jid:'+LID)
const oldCtx={...ctx,userKey:'jid:'+LID,identityKeys:['jid:'+LID]}
addScore(oldCtx,'guesssong','jid:'+LID,'Player',50)
addScore(ctx,'guesssong',PHONE,'Player',25)
assert.match(scoreText(ctx,'guesssong'),/75 pts/)
assert.equal(recordVerifiedLidPhone(storage,LID,'2348999999999@s.whatsapp.net'),'')
assert.equal(hasConflictingLidMapping(storage,LID),true)
assert.equal(storedLidPhone(storage,LID),'')
console.log('PASS verified identity and alias continuity')
