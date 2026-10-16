'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const E=require('./engine.js');
const story=require('./last-call.json');
E.validateStory(story);
const seen=new Set();function walk(id){if(seen.has(id))return;seen.add(id);for(const c of story.nodes[id].choices||[])walk(c.to)}walk(story.start);
assert.equal(seen.size,Object.keys(story.nodes).length,'all authored passages are connected');
const visiting=new Set(),finished=new Set();function dag(id){assert.ok(!visiting.has(id),'no accidental infinite loop '+id);if(finished.has(id))return;visiting.add(id);for(const c of story.nodes[id].choices||[])dag(c.to);visiting.delete(id);finished.add(id)}dag(story.start);
const first={};
function route(overrides={},ending='release',stopAtDecision=false){
 let r=E.start(story,'Test');let count=0,words=0;
 while(!r.ending&&count++<600){
  words+=E.beats(story,r).reduce((n,b)=>n+b.text.split(/\s+/).length,0);
  const cs=E.choices(story,r);assert.ok(cs.length,'reachable state has choices '+r.node);
  if(cs.some(c=>c.id==='c29:sever')){
   if(stopAtDecision)return {r,words};
   const c=cs.find(c=>c.id==='c29:'+ending);if(!c)return {r,words,locked:true};r=E.choose(story,r,c.id);continue;
  }
  const c=cs.find(c=>Object.values(overrides).includes(c.id))||cs[0];r=E.choose(story,r,c.id);
 }
 assert.ok(r.ending,'route finishes');return {r,words};
}
for(const ending of Object.keys(story.endings)){const {r,words}=route({},ending);assert.equal(r.ending,ending);assert.ok(words>25000,'a complete ordinary route contains substantial prose, not a scene-count proxy');first[ending]=r}
const truth=route({},'release',true).r;
assert.ok(E.choices(story,truth).some(c=>c.id==='c29:release'));
const betrayed=route({a:'c10:hide',b:'c20:attack',c:'c22:press'},'release',true).r;
assert.ok(!E.choices(story,betrayed).some(c=>c.id==='c29:release'),'attack and pressure can lose the cooperation needed for release');
assert.ok(betrayed.state.memories.Imani.some(m=>m.id==='lied_voss'));
assert.ok(betrayed.state.memories.Wren.some(m=>m.id==='attacked'));
const c23=E.enter(story,E.clone(betrayed),'c23');assert.ok(Object.keys(story.nodes).filter(k=>k==='c23'||k.startsWith('c23~')).flatMap(node=>E.beats(story,{...c23,node})).some(b=>b.text.includes('You told me Voss')),'later chapter confronts the actual lie');
const truthful=E.enter(story,E.clone(truth),'c23');assert.ok(!Object.keys(story.nodes).filter(k=>k==='c23'||k.startsWith('c23~')).flatMap(node=>E.beats(story,{...truthful,node})).some(b=>b.text.includes('You told me Voss')),'false actions are not attributed');
const injured=route({a:'c04:force',b:'c13:component',c:'c16a:leave',d:'c27:force'},'release',true).r;
assert.ok(injured.state.vars.injury>=3);assert.ok(!E.choices(story,injured).some(c=>c.id==='c29:release'),'injury prevents unsupported precision work');
const noSeal=route({a:'c04:refuse',b:'c11:keep'},'release',true).r;assert.ok(!E.choices(story,noSeal).some(c=>c.id==='c29:release'),'missing safeguards affect outcome');
const original=first.release,encoded=JSON.stringify(original),fork=E.fork(story,original,2,'Replay');
assert.notEqual(fork.id,original.id);assert.equal(fork.ending,null);assert.equal(JSON.stringify(original),encoded,'fork does not change original');
assert.equal(fork.state.memories.Imani,undefined,'later relationship history is not carried back');
assert.ok(fork.pages.some(p=>p.node===fork.node),'first chapter page remains readable after a fork');
assert.ok(!fork.pages.some(p=>p.chapter>2),'future pages are not exposed by replay');
let book=E.emptyBook(story);book=E.storeRun(story,book,original);book=E.storeRun(story,book,fork);
assert.equal(book.playthroughs.length,2);assert.deepEqual(book.endings,['release']);
const map=new Map(),storage={getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v)};
fork.reading={revealed:1,ready:false,scroll:183};book=E.storeRun(story,book,fork);E.persist(story,book,storage);
const reloaded=E.load(story,storage);assert.equal(reloaded.playthroughs[1].reading.scroll,183);assert.equal(reloaded.playthroughs[1].reading.revealed,1);
assert.equal(reloaded.playthroughs[0].ending,'release','other run preserved');
const raw=storage.getItem('night-story:the-last-call:v3');storage.setItem('night-story:the-last-call:v3','{bad');assert.ok(E.load(story,storage).recovery);assert.equal(storage.getItem('night-story:the-last-call:v3'),'{bad','loading never deletes unreadable data');storage.setItem('night-story:the-last-call:v3',raw);
assert.throws(()=>E.choose(story,fork,'c29:release'),/unavailable/,'arbitrary locked destinations cannot be selected');
const once=E.apply(E.initial(story),[{op:'gain',key:'chalk'},{op:'gain',key:'chalk'},{op:'remember',character:'Imani',key:'test',detail:'A witnessed choice.'},{op:'remember',character:'Imani',key:'test',detail:'Again'}]);assert.equal(once.inventory.length,1);assert.equal(once.memories.Imani.length,1);
assert.ok(E.summary(story,original).some(t=>t.includes('brought your mother home')));assert.ok(!E.summary(story,original).some(t=>t.includes('stayed to receive')));
// Exercise every authored decision in a complete route, including non-optimal paths.
let variants=0;for(const n of Object.values(story.nodes))for(const c of n.choices||[])if(!c.continue&&!c.id.startsWith('c29:')){const r=route({pick:c.id},'sever').r;assert.equal(r.ending,'sever');variants++}
const minWords=Math.min(...Object.values(first).map(r=>r.pages.reduce((n,p)=>n+p.text.reduce((m,t)=>m+t.split(/\s+/).length,0),0)));
console.log('PASS: '+seen.size+' passages, '+story.chapters.length+' chapters; three complete routes; '+variants+' authored decision variants; delayed memory, trust, injury and knowledge gates; isolated saves, replay snapshots, recovery and recap.');
console.log('WORDS:',story.wordCount,'across routes;',minWords,'on the shortest of the three tested full routes. Save size with two runs:',raw.length,'bytes.');
fs.writeFileSync('/tmp/night-story-test-runs.json',JSON.stringify({endingRuns:first,decision:truth,book}));
module.exports={route,story};
