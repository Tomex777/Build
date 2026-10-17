// A bare WhatsApp Channel URL privately sent to NIGHT is remembered silently.
// No following/admin invitation action occurs until explicit .joinchannel + Yes.
const link=/^https:\/\/(?:www\.)?whatsapp\.com\/channel\/[A-Za-z0-9]{10,64}\/?(?:\?[^#]*)?$/i
const isDm=chat=>/@(?:s\.whatsapp\.net|lid|hosted\.lid)$/.test(String(chat||''))
const namespace='night-channel-link'
const keyOf=(accountId,userKey)=>String(accountId||'')+'|'+String(userKey||'')
export function recordPendingChannelLink(shared,accountId,userKey,chat,text,now=Date.now()){
  const url=String(text||'').trim()
  if(!shared || !accountId || !userKey || !isDm(chat) || !link.test(url))return false
  shared.sharedSet(namespace,keyOf(accountId,userKey),{url,expiresAt:now+15*60000})
  return true
}
export function pendingChannelLink(shared,accountId,userKey,now=Date.now()){
  if(!shared || !accountId || !userKey)return ''
  const key=keyOf(accountId,userKey),value=shared.sharedGet(namespace,key)
  if(!value || !link.test(String(value.url||'')))return ''
  if(!Number.isFinite(value.expiresAt) || value.expiresAt<=now){
    shared.sharedDelete(namespace,key)
    return ''
  }
  return value.url
}
