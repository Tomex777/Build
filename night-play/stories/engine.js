(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.NightStory=api})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const clone=x=>JSON.parse(JSON.stringify(x));
const plain=x=>x&&typeof x==='object'&&!Array.isArray(x);
const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
const safe=k=>typeof k==='string'&&!['__proto__','constructor','prototype'].includes(k);
function matches(condition,state){
 if(!condition)return true;
 if(condition.all)return condition.all.every(c=>matches(c,state));
 if(condition.any)return condition.any.some(c=>matches(c,state));
 if(condition.not)return !matches(condition.not,state);
 if(condition.item)return state.inventory.includes(condition.item);
 if(condition.lore)return state.knowledge.includes(condition.lore);
 if(condition.event)return state.events.some(e=>e.id===condition.event);
 if(condition.memory)return (state.memories[condition.character]||[]).some(m=>m.id===condition.memory);
 if(condition.var){
  const v=state.vars[condition.var];
  if(own(condition,'eq'))return v===condition.eq;
  if(own(condition,'gte'))return typeof v==='number'&&v>=condition.gte;
  if(own(condition,'lte'))return typeof v==='number'&&v<=condition.lte;
 }
 return false;
}
function initial(story){return {vars:clone(story.initial||{}),inventory:[],knowledge:[],memories:{},events:[]}}
function apply(state,effects,context){
 const s=clone(state),at=context?.node||'',choice=context?.choice||'';
 for(const effect of effects||[]){
  const {op,key,value}=effect;
  if(op==='set'&&safe(key)&&own(s.vars,key)&&typeof value===typeof s.vars[key])s.vars[key]=value;
  else if(op==='add'&&safe(key)&&typeof s.vars[key]==='number'&&Number.isFinite(value))s.vars[key]=Math.max(-100,Math.min(100,s.vars[key]+value));
  else if(op==='gain'&&typeof key==='string'&&!s.inventory.includes(key))s.inventory.push(key);
  else if(op==='lose')s.inventory=s.inventory.filter(x=>x!==key);
  else if(op==='learn'&&typeof key==='string'&&!s.knowledge.includes(key))s.knowledge.push(key);
  else if(op==='remember'&&safe(effect.character)&&typeof key==='string'){
   if(!own(s.memories,effect.character))s.memories[effect.character]=[];
   if(!s.memories[effect.character].some(m=>m.id===key))s.memories[effect.character].push({id:key,detail:effect.detail||'',at,choice});
  }else if(op==='event'&&typeof key==='string'&&!s.events.some(e=>e.id===key))s.events.push({id:key,detail:effect.detail||'',at,choice});
 }
 return s;
}
function choices(story,run){return (story.nodes[run.node]?.choices||[]).filter(c=>matches(c.when,run.state))}
function beats(story,run){return (story.nodes[run.node]?.beats||[]).filter(b=>matches(b.when,run.state))}
function uid(){return typeof crypto!=='undefined'&&crypto.randomUUID?crypto.randomUUID():'run-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)}
function emptyBook(story){return {schema:3,story:story.id,active:null,playthroughs:[],endings:[]}}
function createRun(story,label){const now=Date.now();return {id:uid(),label:label||'Playthrough',revision:story.revision,node:story.start,state:initial(story),history:[],pages:[],visited:[],chapters:[],checkpoints:[],reading:{revealed:0,ready:false,scroll:0},created:now,updated:now,ending:null}}
function enter(story,run,nodeId){
 const n=story.nodes[nodeId];if(!n)throw Error('Unknown story passage: '+nodeId);
 const r=clone(run),previous=story.nodes[r.node];r.node=nodeId;r.reading={revealed:0,ready:false,scroll:0};r.updated=Date.now();
 if(!r.visited.includes(nodeId)){r.state=apply(r.state,n.effects,{node:nodeId});r.visited.push(nodeId)}
 if(!r.pages.some(p=>p.node===nodeId))r.pages.push({node:nodeId,chapter:n.chapter,title:n.title,text:beats(story,r).map(b=>b.text)});
 if(previous?.chapter&&previous.chapter!==n.chapter&&!r.chapters.includes(previous.chapter))r.chapters.push(previous.chapter);
 if(n.chapter&&!r.checkpoints.some(c=>c.chapter===n.chapter))r.checkpoints.push({chapter:n.chapter,node:nodeId,state:clone(r.state),historyLength:r.history.length,visitedLength:r.visited.length,pagesLength:r.pages.length-1,chapters:clone(r.chapters)});
 if(n.ending){r.ending=n.ending;if(n.chapter&&!r.chapters.includes(n.chapter))r.chapters.push(n.chapter)}
 return r;
}
function start(story,label){const r=createRun(story,label);return enter(story,r,story.start)}
function choose(story,run,choiceId){
 if(run.ending)throw Error('This playthrough is complete. Start a separate run to replay.');
 const c=choices(story,run).find(x=>x.id===choiceId);if(!c)throw Error('That choice is unavailable.');
 let r=clone(run);
 if(!c.continue)r.history.push({node:r.node,choice:c.id,label:c.label,chapter:story.nodes[r.node].chapter});
 r.state=apply(r.state,c.effects,{node:r.node,choice:c.id});
 return enter(story,r,c.to);
}
function fork(story,run,chapter,label){
 const c=run.checkpoints.find(c=>c.chapter===chapter);if(!c)throw Error('That chapter has not been reached.');
 let r=createRun(story,label||'From chapter '+chapter);r.node=c.node;r.state=clone(c.state);r.history=clone(run.history.slice(0,c.historyLength));r.visited=clone(run.visited.slice(0,c.visitedLength));r.chapters=clone(c.chapters);r.pages=clone(run.pages.slice(0,c.pagesLength));r.pages.push({node:c.node,chapter:story.nodes[c.node].chapter,title:story.nodes[c.node].title,text:beats(story,r).map(b=>b.text)});r.checkpoints=clone(run.checkpoints.filter(x=>x.chapter<=chapter));r.parent=run.id;
 return r;
}
function validateStory(story){
 if(!story.id||!story.nodes||!story.nodes[story.start])throw Error('Story needs an id, nodes and a valid start.');
 const ids=new Set();
 for(const [id,n] of Object.entries(story.nodes)){
  if(!n.beats?.length)throw Error('Empty passage '+id);
  if(!n.ending&&!n.choices?.length)throw Error('Dead end '+id);
  for(const c of n.choices||[]){if(ids.has(c.id))throw Error('Repeated choice id '+c.id);ids.add(c.id);if(!story.nodes[c.to])throw Error('Broken choice '+c.id)}
 }
 return true;
}
function validState(story,s){
 if(!plain(s)||!plain(s.vars)||!Array.isArray(s.inventory)||!Array.isArray(s.knowledge)||!plain(s.memories)||!Array.isArray(s.events))return false;
 return Object.entries(story.initial).every(([k,v])=>typeof s.vars[k]===typeof v&&(typeof v!=='number'||Number.isFinite(s.vars[k])))&&s.inventory.every(x=>typeof x==='string')&&s.knowledge.every(x=>typeof x==='string')&&Object.keys(s.memories).every(safe)&&Object.values(s.memories).every(a=>Array.isArray(a)&&a.every(m=>plain(m)&&typeof m.id==='string'))&&s.events.every(e=>plain(e)&&typeof e.id==='string');
}
function load(story,storage){
 const fallback=emptyBook(story);let raw;
 try{raw=storage.getItem('night-story:'+story.id+':v3')}catch{return fallback}
 if(!raw)return fallback;
 try{
  const b=JSON.parse(raw);
  if(b.schema!==3||b.story!==story.id||!Array.isArray(b.playthroughs)||!Array.isArray(b.endings))return {...fallback,recovery:true};
  const accepted=[],rejected=[];
  for(const r of b.playthroughs){
   if(plain(r)&&typeof r.id==='string'&&story.nodes[r.node]&&r.revision===story.revision&&validState(story,r.state)&&Array.isArray(r.pages)&&r.pages.every(p=>plain(p)&&typeof p.node==='string'&&Array.isArray(p.text)&&p.text.every(t=>typeof t==='string'))&&Array.isArray(r.history)&&Array.isArray(r.visited)&&Array.isArray(r.chapters)&&Array.isArray(r.checkpoints)&&plain(r.reading)&&r.checkpoints.every(c=>plain(c)&&story.nodes[c.node]&&validState(story,c.state)&&Number.isInteger(c.historyLength)&&Number.isInteger(c.visitedLength)&&Number.isInteger(c.pagesLength)&&Array.isArray(c.chapters))){
    const clean=clone(r);clean.reading.revealed=Math.max(0,Math.min(beats(story,clean).length,Number(clean.reading.revealed)||0));clean.reading.scroll=Math.max(0,Number(clean.reading.scroll)||0);accepted.push(clean);
   }else rejected.push(r);
  }
  return {...b,playthroughs:accepted,active:accepted.some(r=>r.id===b.active)?b.active:accepted[0]?.id||null,endings:b.endings.filter(e=>story.endings[e]),recovery:rejected.length>0};
 }catch{return {...fallback,recovery:true}}
}
function persist(story,book,storage){
 const clean=clone(book);delete clean.recovery;
 storage.setItem('night-story:'+story.id+':v3',JSON.stringify(clean));
 return true;
}
function storeRun(story,book,run){const b=clone(book),i=b.playthroughs.findIndex(r=>r.id===run.id);if(i<0)b.playthroughs.push(clone(run));else b.playthroughs[i]=clone(run);b.active=run.id;if(run.ending&&!b.endings.includes(run.ending))b.endings.push(run.ending);return b}
function summary(story,run){return (story.recap||[]).filter(x=>matches(x.when,run.state)).map(x=>x.text)}
return {clone,matches,initial,apply,choices,beats,start,enter,choose,fork,validateStory,load,persist,storeRun,summary,emptyBook};
});
