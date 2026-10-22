'use strict';
// Server-facing projection for Agent A. This never fetches external media and never grants rights.
function toReviewCard(item,{previewUrl,previewData}={}){
 if(!item||item.kind!=='meme'||typeof item.id!=='string')throw new TypeError('Normalized meme required.');
 // Accept opaque same-origin asset paths, not encoded slash/dot traversal or
 // attacker-controlled URLs. A proxy route must independently check owner/JID.
 const allowedUrl=typeof previewUrl==='string'&&/^\/api\/channel-(?:media|assets)\/(?:[A-Za-z0-9_-]{1,100}\/){0,5}[A-Za-z0-9_-]{1,100}(?:\.(?:png|jpg|jpeg|webp))?$/.test(previewUrl);
 const allowedData=typeof previewData==='string'&&/^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/i.test(previewData)&&previewData.length<=1900000;
 if(!allowedUrl&&!allowedData)throw new Error('Server-owned allowed preview required: no external hotlink.');
 const reviewOnly=item.publishable!==true;
 return Object.freeze({
  id:item.id,category:item.category,caption:item.caption,
  title:item.caption?String(item.caption).slice(0,100):'Meme',
  alt:'Meme from '+String(item.source?.provider||'approved source'),
  sourceName:item.source?.provider==='reddit'?'Reddit · r/'+String(item.source?.id||'').split('/').pop():String(item.source?.provider||'Source'),
  sourceUrl:item.source?.url||'',
  ...(allowedUrl?{previewUrl}:{previewData}),
  // Agent A specifically accepts the `verified` enum, not the curation
  // internals' `cleared` enum. This is only an advisory UI flag; C rechecks.
  rightsStatus:reviewOnly?'unverified':'verified',
  sourceRights:item.source?.rights||'unknown',
  reviewOnly,publishable:!reviewOnly,
  publishBlockedReason:reviewOnly?'rights_unverified':null,
  // Must be enforced by C at review, enqueue and send, never only by this flag.
  provenance:{sourceId:item.source?.id||'',externalId:item.source?.externalId||'',attributionRequired:item.source?.attributionRequired===true}
 });
}
// The UI's existing validBatch contract requires EXACTLY four distinct cards.
// Reject incomplete, duplicate or uncached sets here instead of returning a
// partial success that would erase review context on Next four.
function toReviewBatch(items,previewsById){
 if(!Array.isArray(items)||items.length!==4)throw new Error('Exactly four candidate records are required for a review batch.');
 if(!previewsById||typeof previewsById!=='object')throw new Error('Server-authorized preview mapping is required.');
 const seen=new Set();
 return Object.freeze(items.map(item=>{
  if(!item||typeof item.id!=='string'||seen.has(item.id))throw new Error('Review batch candidate IDs must be distinct.');
  seen.add(item.id);
  if(!Object.hasOwn(previewsById,item.id))throw new Error('Review batch candidate is missing a server-owned preview.');
  return toReviewCard(item,previewsById[item.id]);
 }));
}
module.exports={toReviewCard,toReviewBatch};
