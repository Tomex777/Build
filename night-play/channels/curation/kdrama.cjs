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
const SPOILER=/\b(?:spoilers?|finale? (?:explained|reveals|twist)|ending (?:explained|revealed)|dies? in episode|episode \d+ (?:recap|twist))\b/i;
// Conservative additional gate: source metadata cannot override speculative/spoiler wording.
function classifyHeadline(title){
 const text=String(title||'');
 return Object.freeze({rumor:RUMOR.test(text),spoiler:SPOILER.test(text)});
}
function quoteShow(title){
 const match=/(?:“([^”]{2,90})”|"([^"]{2,90})"|'([^']{2,90})')/.exec(title);
 return (match?.[1]||match?.[2]||match?.[3]||'').trim();
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
 const confirmed=confirmation?.verified===true&&!flags.rumor;
 return {show:String(confirmation?.show||findShow(title,Array.isArray(showAliases)?showAliases:[])),event:confirmation?.event||event,
 tags,rumor:flags.rumor,spoiler:flags.spoiler,confirmed,
 storyKey:confirmation?.verified===true&&typeof confirmation?.storyKey==='string'?confirmation.storyKey:null,eventDate:confirmation?.eventDate||null,
  // Only server-verified metadata can assert where/when a show streams.
  genre:confirmation?.verified===true?confirmation.genre||'':'',
  platform:confirmation?.verified===true?confirmation.platform||'':'',
  availabilityRegion:confirmation?.verified===true?confirmation.availabilityRegion||'':''};
}
module.exports={interpretKDramaEntry,EVENT_PATTERNS,classifyHeadline};
