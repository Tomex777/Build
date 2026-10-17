import assert from 'node:assert/strict'
import { recordPendingChannelLink,pendingChannelLink } from './night-channel-links.js'
const values=new Map(),shared={
  sharedGet:(ns,id)=>values.get(ns+'|'+id),
  sharedSet:(ns,id,v)=>values.set(ns+'|'+id,v),
  sharedDelete:(ns,id)=>values.delete(ns+'|'+id),
}
const url='https://whatsapp.com/channel/0029VExampleTestingCode'
const t=Date.now()
assert.equal(recordPendingChannelLink(shared,'A','user-A','friend@g.us',url,t),false)
assert.equal(recordPendingChannelLink(shared,'A','user-A','friend@s.whatsapp.net','this is not a link',t),false)
assert.equal(recordPendingChannelLink(shared,'A','user-A','friend@s.whatsapp.net',url,t),true)
assert.equal(pendingChannelLink(shared,'A','user-A',t),url)
assert.equal(pendingChannelLink(shared,'B','user-A',t),'','No cross-bot account link leakage')
assert.equal(pendingChannelLink(shared,'A','user-B',t),'','No cross-user link leakage')
assert.equal(pendingChannelLink(shared,'A','user-A',t+15*60000+1),'','Expire old link silently')
assert.equal(recordPendingChannelLink(shared,'A','user-A','user@lid',url,t),true)
assert.equal(pendingChannelLink(shared,'A','user-A',t),url)
console.log('PASS NIGHT channel private-link-first flow: silent DM, scope isolation and short expiry')
