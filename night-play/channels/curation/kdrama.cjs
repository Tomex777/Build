'use strict';
const EVENT_PATTERNS=Object.freeze({
 casting:/\b(?:cast(?:ing)?|joins? (?:the )?(?:cast|series)|set to star|in talks|lead role|stars? in)\b/i,
 releases:/\b(?:premier(?:e|es|ing)|release date|air date|debut|episodes? (?:arrive|air))\b/i,
 trailers:/\b(?:trailer|teaser|first look|preview clip)\b/i,
 renewals:/\b(?:renewed|renewal|season (?:two|three|four|2|3|4) (?:announced|confirmed))\b/i,
 platforms:/\b(?:netflix|disney\+|tving|viki|wavve|coupang play|kocowa|prime video)\b/i,
 romance:/\b(?:romance|romantic|love story|rom-com|romcom)\b/i
});
const RUMOR=/\b(?:rumou?r(?:ed|s)?|reportedly|unconfirmed|speculation|in talks|allegedly|may (?:star|join|return))\b/i;
const SPOILER=/\b(?:spoilers?|finale\b|finals? episode|the ending\b|ending (?:explained|revealed)|dies? in episode|episode\s+\d+\b|who (?:dies|survives)|plot twist|post[- ]credits)\b/i;
// Conservative additional gate: source metadata cannot override speculative/spoiler wording.
function classifyHeadline(title){
 const text=String(title||'');
 return Object.freeze({rumor:RUMOR.test(text),spoiler:SPOILER.test(text)});
}
function quoteShow(title){
 const match=/(?:“([^”]{2,90})”|‘([^’]{2,90})’|"([^"]{2,90})"|(?:^|[\s(])'(.{2,90}?)'(?=\s|[.,:;!?)]|$))/u.exec(title);
 return (match?.[1]||match?.[2]||match?.[3]||match?.[4]||'').trim();
}
function findShow(title,aliases){
 for(const raw of aliases){
  if(typeof raw!=='string'||raw.length<2||raw.length>100)continue;
  const at=title.toLocaleLowerCase().indexOf(raw.toLocaleLowerCase());
  if(at>=0){const before=title[at-1]||' ',after=title[at+raw.length]||' ';
   if(!/[\p{L}\p{N}]/u.test(before)&&!/[\p{L}\p{N}]/u.test(after))return raw;}
 }
 return quoteShow(title);
}
function interpretKDramaEntry(row,{showAliases=[],confirmedBySource={}}={}){
 if(!row||typeof row.title!=='string')return null;
 const title=row.title.replace(/\s+/g,' ').trim();if(title.length<8||title.length>240)return null;
 const tags=Object.entries(EVENT_PATTERNS).filter(([,re])=>re.test(title)).map(([tag])=>tag);
 const feedCategories=(Array.isArray(row.categories)?row.categories:[]).slice(0,30);
 // Editorial drama-labelled show features can enter manual review even if the
 // headline has no casting/date/trailer keyword; never upgrade them to official.
 if(!tags.length&&feedCategories.some(c=>typeof c==='string'&&/^(?:k[- ]?drama|korean dramas?|dramas?|drama preview|tv drama)$/i.test(c.trim())))tags.push('other');
 const event=tags.find(x=>['casting','releases','trailers','renewals'].includes(x))||'update';
 const approved=confirmedBySource&&typeof confirmedBySource==='object'?confirmedBySource:{};
 const guid=typeof row.guid==='string'&&row.guid.trim()&&row.guid.length<=256?row.guid:null;
 const confirmation=guid&&Object.hasOwn(approved,guid)?approved[guid]:null;
 const flags=classifyHeadline(title);
 // RSS GUIDs can be recycled or collide. A verified "Aurora" event must still
 // visibly identify Aurora in the headline (or drop to review-only), otherwise
 // one publisher's stale ledger would promote an unrelated news story.
 const ledgerShow=typeof confirmation?.show==='string'?confirmation.show.trim():'';
 const showMatches=!ledgerShow||!!findShow(title,[ledgerShow]);
 const confirmed=confirmation?.verified===true&&!flags.rumor&&showMatches;
 return {show:String(showMatches&&ledgerShow?ledgerShow:findShow(title,Array.isArray(showAliases)?showAliases:[])),event:showMatches&&confirmation?.event||event,
 tags,rumor:flags.rumor,spoiler:flags.spoiler,confirmed,
 storyKey:confirmed&&typeof confirmation?.storyKey==='string'?confirmation.storyKey:null,eventDate:confirmed?confirmation?.eventDate||null:null,
  // Never assert streaming availability based on a mismatched or speculative record.
  genre:confirmed?confirmation.genre||'':'',
  platform:confirmed?confirmation.platform||'':'',
  availabilityRegion:confirmed?confirmation.availabilityRegion||'':''};
}
module.exports={interpretKDramaEntry,EVENT_PATTERNS,classifyHeadline};
