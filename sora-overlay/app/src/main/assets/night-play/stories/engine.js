(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.NightStory=api})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const clone=x=>JSON.parse(JSON.stringify(x));
const plain=x=>x&&typeof x==='object'&&!Array.isArray(x);
const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
const safe=k=>typeof k==='string'&&!['__proto__','constructor','prototype'].includes(k);
const clamp=(n,min,max)=>Math.max(min,Math.min(max,n));
function name(value,fallback){
 const v=typeof value==='string'?value.normalize('NFC').trim().replace(/\s+/gu,' '):fallback;
 if(!v||[...v].length>60||! /^[\p{L}\p{M}][\p{L}\p{M}\p{N} .'’\-‐‑]*$/u.test(v))throw Error('Use a name of 1–60 characters, with letters, spaces, apostrophes or hyphens.');
 return v;
}
function identity(story,input={}){const d=story.identity||{firstName:'Sam',surname:'Mansour'};return {firstName:name(input.firstName,d.firstName),surname:name(input.surname,d.surname)}}
function text(value,state){
 const who=state?.state?.identity||state?.identity||{firstName:'Sam',surname:'Mansour'},values={...who,fullName:who.firstName+' '+who.surname};
 return String(value??'').replace(/\{\{(firstName|surname|fullName)(\|upper)?\}\}/g,(_,k,upper)=>upper?values[k].toLocaleUpperCase():values[k]);
}
function compare(v,c){if(own(c,'eq'))return v===c.eq;if(own(c,'gte'))return typeof v==='number'&&v>=c.gte;if(own(c,'lte'))return typeof v==='number'&&v<=c.lte;return Boolean(v)}
function matches(c,s){
 if(!c)return true;
 if(c.all)return c.all.every(x=>matches(x,s));if(c.any)return c.any.some(x=>matches(x,s));if(c.not)return !matches(c.not,s);
 if(c.item)return s.inventory.includes(c.item);if(c.lore)return s.knowledge.includes(c.lore);if(c.event)return s.events.some(e=>e.id===c.event);
 if(c.memory)return (s.memories[c.character]||[]).some(m=>m.id===c.memory);
 if(c.attribute)return compare(s.attributes?.[c.attribute],c);
 if(c.ability)return compare(s.abilities?.[c.ability]?.level||0,own(c,'gte')||own(c,'eq')||own(c,'lte')?c:{gte:1});
 if(c.resource)return compare(s.resources?.[c.resource],c);if(c.abilitySlots)return Object.keys(s.abilities||{}).length<c.abilitySlots;
 if(c.var)return compare(s.vars[c.var],c);return false;
}
function initial(story,input){const rules=story.rules||{};return {identity:identity(story,input),vars:clone(story.initial||{}),attributes:Object.fromEntries(Object.entries(rules.attributes||{}).map(([k,v])=>[k,v.start||1])),points:rules.startingPoints||0,resources:Object.fromEntries(Object.entries(rules.resources||{}).map(([k,v])=>[k,v.start])),abilities:{},ledger:[],inventory:[],knowledge:[],memories:{},events:[]}}
function apply(state,effects,context={}){
 const s=clone(state),at=context.node||'',choice=context.choice||'',rules=context.story?.rules||{},notices=context.notices||[];
 const notify=(kind,label,detail)=>notices.push({type:'notification',kind,text:label,detail:detail||'',pause:320});
 for(const e of effects||[]){
  const {op,key,value}=e;if(!safe(key)||!matches(e.when,s)||(e.once&&s.ledger.includes(e.once)))continue;let changed=true;
  if(op==='set'&&own(s.vars,key)&&typeof value===typeof s.vars[key])s.vars[key]=value;
  else if(op==='add'&&typeof s.vars[key]==='number'&&Number.isFinite(value))s.vars[key]=clamp(s.vars[key]+value,-100,100);
  else if(op==='gain'&&!s.inventory.includes(key))s.inventory.push(key);
  else if(op==='lose')s.inventory=s.inventory.filter(x=>x!==key);
  else if(op==='learn'&&!s.knowledge.includes(key)){s.knowledge.push(key);if(e.announce)notify('knowledge','Knowledge acquired',context.story?.knowledge[key]?.title||key)}
  else if(op==='remember'&&safe(e.character)){if(!own(s.memories,e.character))s.memories[e.character]=[];if(!s.memories[e.character].some(m=>m.id===key))s.memories[e.character].push({id:key,detail:e.detail||'',at,choice});else changed=false;}
  else if(op==='event'&&!s.events.some(x=>x.id===key))s.events.push({id:key,detail:e.detail||'',at,choice});
  else if(op==='points'&&Number.isInteger(value)&&value>0){s.points+=value;notify('attribute','Attribute point earned',e.detail||'Choose where to improve in Character.');}
  else if(op==='resource'&&own(s.resources,key)&&Number.isInteger(value)){const def=rules.resources?.[key],before=s.resources[key];s.resources[key]=clamp(before+value,0,def?.max??before);if(before!==s.resources[key])notify(value<0?'loss':'restore',value<0?(def?.lossText||'Resource lost'):(def?.restoreText||'Resource restored'),e.detail);else changed=false;}
  else if(op==='ability'&&rules.abilities?.[key]&&!s.abilities[key]&&Object.keys(s.abilities).length<(rules.abilityLimit??Infinity)){s.abilities[key]={level:1,uses:0};notify('ability','Ability discovered',rules.abilities[key].title);}
  else if(op==='practice'&&s.abilities[key]){const a=s.abilities[key],def=rules.abilities?.[key]||{},before=a.level;a.uses++;a.level=Math.min(def.maxLevel||3,1+(def.thresholds||[2,4]).filter(n=>a.uses>=n).length);if(a.level>before)notify('ability','Ability improved',def.title+' · Level '+a.level);}
  else if(op==='announce')notify(e.kind||'knowledge',e.label||key,e.detail);else changed=false;
  if(changed&&e.once)s.ledger.push(e.once);
 }
 return s;
}
function choices(story,run){return (story.nodes[run.node]?.choices||[]).filter(c=>matches(c.when,run.state)&&matches(c.require,run.state)).map(c=>({...c,label:text(c.label,run)}))}
function options(story,run){return (story.nodes[run.node]?.choices||[]).filter(c=>matches(c.when,run.state)).map(c=>({...c,label:text(c.label,run),locked:!matches(c.require,run.state)}))}
function beats(story,run){return [...(run.presentation||[]),...(story.nodes[run.node]?.beats||[]).filter(b=>matches(b.when,run.state))].map(b=>({...b,text:text(b.text,run),detail:text(b.detail,run),speaker:b.speaker?text(b.speaker,run):undefined}))}
function hash(text){let h=2166136261;for(let i=0;i<text.length;i++)h=Math.imul(h^text.charCodeAt(i),16777619)>>>0;return String(h)}
function uid(){return typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():'run-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)}
function emptyBook(story){return {schema:3,story:story.id,active:null,playthroughs:[],endings:[]}}
function createRun(story,label,input){const now=Date.now();return {id:uid(),label:label||'Playthrough',revision:story.revision,node:story.start,state:initial(story,input),history:[],pages:[],visited:[],chapters:[],checkpoints:[],presentation:[],reading:{revealed:0,ready:false,scroll:0},created:now,updated:now,ending:null}}
function failure(story,state){return Object.entries(story.rules?.resources||{}).find(([key,v])=>v.failureNode&&state.resources[key]===0)?.[1].failureNode}
function enter(story,run,nodeId,carry=[]){
 const failedBefore=failure(story,run.state),destination=failedBefore&&story.nodes[nodeId]?.failureGroup!==failedBefore?failedBefore:nodeId,n=story.nodes[destination];if(!n)throw Error('Unknown story passage: '+destination);
 const r=clone(run),previous=story.nodes[r.node];r.node=destination;r.presentation=clone(carry);r.reading={revealed:0,ready:false,scroll:0};r.updated=Date.now();
 if(!r.visited.includes(destination)){r.state=apply(r.state,n.effects,{node:destination,story,notices:r.presentation});r.visited.push(destination)}
 const failed=failure(story,r.state);if(failed&&failed!==destination&&n.failureGroup!==failed)return enter(story,r,failed,r.presentation);
 if(!r.pages.some(p=>p.node===destination))r.pages.push({node:destination,chapter:n.chapter,title:n.title,text:beats(story,r).map(b=>b.speaker?b.speaker+': '+b.text:b.text)});
 if(previous?.chapter&&previous.chapter!==n.chapter&&!r.chapters.includes(previous.chapter))r.chapters.push(previous.chapter);
 if(n.chapter&&!r.checkpoints.some(c=>c.chapter===n.chapter))r.checkpoints.push({chapter:n.chapter,node:destination,state:clone(r.state),historyLength:r.history.length,visitedLength:r.visited.length,pagesLength:r.pages.length-1,chapters:clone(r.chapters)});
 if(n.ending){r.ending=n.ending;if(n.chapter&&!r.chapters.includes(n.chapter))r.chapters.push(n.chapter)}return r;
}
function allocate(story,run,allocation){
 const r=clone(run),defs=story.rules?.attributes||{};r.presentation=[];let spent=0;
 for(const [key,n] of Object.entries(allocation)){if(!safe(key)||!own(defs,key)||!Number.isInteger(n)||n<0)throw Error('Invalid attribute allocation.');if(r.state.attributes[key]+n>defs[key].max)throw Error('Attribute limit reached.');spent+=n;}
 if(spent>r.state.points)throw Error('Not enough attribute points.');
 for(const [key,n] of Object.entries(allocation)){r.state.attributes[key]+=n;if(n)r.presentation.push({type:'notification',kind:'attribute',text:'Attribute improved',detail:defs[key].title+' · Level '+r.state.attributes[key],pause:240})}r.state.points-=spent;
 if(!r.history.length&&r.node===story.start)for(const c of r.checkpoints)c.state=clone(r.state);return r;
}
function start(story,label,input,allocation){let r=createRun(story,label,input);if(allocation)r=allocate(story,r,allocation);r.presentation=[];return enter(story,r,story.start)}
function choose(story,run,choiceId){
 if(run.ending)throw Error('This playthrough is complete. Start a separate run to replay.');const c=choices(story,run).find(x=>x.id===choiceId);if(!c)throw Error('That choice is unavailable.');let r=clone(run);const notices=[];
 if(!c.continue)r.history.push({node:r.node,choice:c.id,label:c.label,chapter:story.nodes[r.node].chapter});r.state=apply(r.state,c.effects,{node:r.node,choice:c.id,story,notices});if(c.returnToBookmark&&r.bookmark){const bookmark=r.bookmark;delete r.bookmark;r=enter(story,r,bookmark.node,notices);r.ending=bookmark.ending;r.reading=bookmark.reading;return r}return enter(story,r,c.to,notices);
}
function interlude(story,run,node){if(!story.interludes?.includes(node))throw Error('Unknown interlude.');const r=clone(run);r.bookmark={node:r.node,ending:r.ending,reading:clone(r.reading)};r.ending=null;r.legacyHeritagePending=false;return enter(story,r,node)}
function fork(story,run,chapter,label){
 const c=run.checkpoints.find(x=>x.chapter===chapter);if(!c)throw Error('That chapter has not been reached.');let r=createRun(story,label||'From chapter '+chapter,run.state.identity);r.node=c.node;r.state=clone(c.state);r.history=clone(run.history.slice(0,c.historyLength));r.visited=clone(run.visited.slice(0,c.visitedLength));r.chapters=clone(c.chapters);r.pages=clone(run.pages.slice(0,c.pagesLength));r.pages.push({node:c.node,chapter:story.nodes[c.node].chapter,title:story.nodes[c.node].title,text:beats(story,r).map(b=>b.text)});r.checkpoints=clone(run.checkpoints.filter(x=>x.chapter<=chapter));r.parent=run.id;return r;
}
// Reading backwards is a read-only view of pages actually visited, never a state rollback.
// The first passage after the latest committed decision is the hard rewind boundary.
function readingWindow(story,run){
  const pages=Array.isArray(run?.pages)?run.pages:[];
  const current=pages.findLastIndex(p=>p.node===run.node);
  if(current<0)return {indices:[],current:-1};
  let floor=0;
  const committed=new Set((run.history||[]).map(h=>h.node));
  for(let i=0;i<current;i++){
    const source=story.nodes[pages[i].node];
    // The authored choice check also guards imported saves missing old decision records.
    if(committed.has(pages[i].node)||(source?.choices||[]).some(c=>!c.continue))floor=i+1;
  }
  const indices=[];
  for(let i=floor;i<=current;i++)if(Array.isArray(pages[i].text)&&pages[i].text.length)indices.push(i);
  return {indices,current};
}
function validateStory(story){
 if(!story.id||!story.nodes||!story.nodes[story.start])throw Error('Story needs an id, nodes and a valid start.');const ids=new Set();
 for(const [id,n] of Object.entries(story.nodes)){if(!n.beats?.length)throw Error('Empty passage '+id);if(!n.ending&&!n.choices?.length)throw Error('Dead end '+id);if(n.ending&&!story.endings[n.ending])throw Error('Unknown ending '+id);for(const c of n.choices||[]){if(ids.has(c.id))throw Error('Repeated choice id '+c.id);ids.add(c.id);if(!story.nodes[c.to])throw Error('Broken choice '+c.id)}if(!n.ending&&!(n.choices||[]).some(c=>!c.when&&!c.require))throw Error('Passage needs an unconditional way forward: '+id);}
 for(const r of Object.values(story.rules?.resources||{}))if(r.failureNode&&!story.nodes[r.failureNode])throw Error('Missing resource failure passage.');return true;
}
function validLegacy(story,s){
 if(!plain(s)||!plain(s.vars)||!Array.isArray(s.inventory)||!Array.isArray(s.knowledge)||!plain(s.memories)||!Array.isArray(s.events))return false;
 return Object.entries(story.legacyInitial||story.initial).every(([k,v])=>typeof s.vars[k]===typeof v&&(typeof v!=='number'||Number.isFinite(s.vars[k])))&&s.inventory.every(x=>typeof x==='string')&&s.knowledge.every(x=>typeof x==='string')&&Object.keys(s.memories).every(safe)&&Object.values(s.memories).every(a=>Array.isArray(a)&&a.every(m=>plain(m)&&typeof m.id==='string'))&&s.events.every(e=>plain(e)&&typeof e.id==='string');
}
function migrateState(story,state,legacy){
 if(!validLegacy(story,state))throw Error('Invalid narrative state.');const base=initial(story),s={...base,...clone(state),vars:{...base.vars,...state.vars}};
 if(legacy){s.identity=identity(story);s.attributes=base.attributes;s.points=base.points;s.resources=base.resources;s.abilities={};s.ledger=[];for(const grant of story.migration?.abilities||[])if(matches(grant.when,s))s.abilities[grant.key]={level:1,uses:0};}
 else{ s.identity=identity(story,s.identity);if(!plain(s.attributes)||!plain(s.resources)||!plain(s.abilities)||!Array.isArray(s.ledger)||!Number.isInteger(s.points)||s.points<0)throw Error('Invalid progression.');for(const [key,v] of Object.entries(story.rules?.attributes||{}))if(!Number.isInteger(s.attributes[key])||s.attributes[key]<1||s.attributes[key]>v.max)throw Error('Invalid attribute.');for(const [key,v] of Object.entries(story.rules?.resources||{}))if(!Number.isInteger(s.resources[key])||s.resources[key]<0||s.resources[key]>v.max)throw Error('Invalid resource.');for(const [key,v] of Object.entries(s.abilities))if(!safe(key)||!story.rules?.abilities[key]||!plain(v)||!Number.isInteger(v.level)||v.level<1||v.level>story.rules.abilities[key].maxLevel||!Number.isInteger(v.uses)||v.uses<0)throw Error('Invalid ability.');}
 return s;
}
function load(story,storage){
 const fallback=emptyBook(story);let raw;try{raw=storage.getItem('night-story:'+story.id+':v3')}catch{return fallback}if(!raw)return fallback;
 try{const b=JSON.parse(raw);if(b.schema!==3||b.story!==story.id||!Array.isArray(b.playthroughs)||!Array.isArray(b.endings))return {...fallback,recovery:true};const accepted=[],rejected=[];
 for(const old of b.playthroughs){try{
 const r=clone(old),legacy=(story.migration?.revisions||[]).includes(r.revision);if(!plain(r)||typeof r.id!=='string'||!story.nodes[r.node]||(!legacy&&r.revision!==story.revision)||!Array.isArray(r.pages)||!Array.isArray(r.history)||!Array.isArray(r.visited)||!Array.isArray(r.chapters)||!Array.isArray(r.checkpoints)||!plain(r.reading))throw Error('Invalid save.');r.state=migrateState(story,r.state,legacy);
 for(const c of r.checkpoints){if(!plain(c)||!story.nodes[c.node]||!Number.isInteger(c.historyLength)||!Number.isInteger(c.visitedLength)||!Number.isInteger(c.pagesLength)||!Array.isArray(c.chapters))throw Error('Invalid checkpoint.');c.state=migrateState(story,c.state,legacy);}
 for(const p of r.pages){if(!plain(p)||typeof p.node!=='string'||!Array.isArray(p.text)||!p.text.every(t=>typeof t==='string'))throw Error('Invalid page.');if(legacy)p.text=p.text.map(t=>text(story.legacyText?.[hash(t)]||t,r.state));}

 r.presentation=legacy?[]:(Array.isArray(r.presentation)?r.presentation:[]);r.revision=story.revision;if(legacy){r.migratedFrom=old.revision;r.legacyHeritagePending=!r.state.knowledge.includes('heritage')&&r.visited.includes(story.migration?.revelationNode)&&(Boolean(r.ending)||story.nodes[r.node].chapter>story.nodes[story.migration.revelationNode].chapter);}
 r.reading.revealed=clamp(Number(r.reading.revealed)||0,0,beats(story,r).length);r.reading.scroll=Math.max(0,Number(r.reading.scroll)||0);accepted.push(r);
 }catch{rejected.push(old)}}
 return {...b,playthroughs:accepted,active:accepted.some(r=>r.id===b.active)?b.active:accepted[0]?.id||null,endings:b.endings.filter(e=>story.endings[e]),recovery:rejected.length>0};
 }catch{return {...fallback,recovery:true}}
}
function persist(story,book,storage){const clean=clone(book);delete clean.recovery;storage.setItem('night-story:'+story.id+':v3',JSON.stringify(clean));return true}
function storeRun(story,book,run){const b=clone(book),i=b.playthroughs.findIndex(r=>r.id===run.id);if(i<0)b.playthroughs.push(clone(run));else b.playthroughs[i]=clone(run);b.active=run.id;if(run.ending&&!(story.failureEndings||[]).includes(run.ending)&&!b.endings.includes(run.ending))b.endings.push(run.ending);return b}
function summary(story,run){return (story.recap||[]).filter(x=>matches(x.when,run.state)).map(x=>text(x.text,run))}
return {clone,matches,text,identity,initial,apply,choices,options,beats,start,enter,choose,fork,interlude,allocate,readingWindow,validateStory,load,persist,storeRun,summary,emptyBook};
});
