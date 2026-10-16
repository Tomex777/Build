'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs');
const E=require('./engine.js'),story=require('./last-call.json');
E.validateStory(story);
const seen=new Set();function walk(id){if(seen.has(id))return;seen.add(id);for(const c of story.nodes[id].choices||[])walk(c.to)}
walk(story.start);for(const id of story.interludes)walk(id);for(const def of Object.values(story.rules.resources))walk(def.failureNode);
assert.equal(seen.size,Object.keys(story.nodes).length,'all authored passages connected through choices, interludes or resource failure');
const visiting=new Set(),finished=new Set();function dag(id){assert.ok(!visiting.has(id),'no accidental loop '+id);if(finished.has(id))return;visiting.add(id);for(const c of story.nodes[id].choices||[])dag(c.to);visiting.delete(id);finished.add(id)}for(const id of seen)dag(id);
// Every nonterminal passage has an unconditional edge in a finite graph: every state has an ending route.
for(const [id,n] of Object.entries(story.nodes))if(!n.ending)assert.ok(n.choices.some(c=>!c.when&&!c.require),'no stat deadlock '+id);
const defaultBuild={observation:2,magic:2};
function route(overrides={},ending='release',stopAtDecision=false,build=defaultBuild,{light=false,random}={}){
 let r=E.start(story,'Test',{firstName:'Zoë Ana',surname:'O’Neill-Mansour'},build),count=0,words=0;const picked=new Set();
 while(!r.ending&&count++<650){
  words+=E.beats(story,r).reduce((n,b)=>n+b.text.split(/\s+/).length,0);
  const cs=E.choices(story,r);assert.ok(cs.length,'reachable state has choices '+r.node);
  if(cs.some(c=>c.id==='c29:sever')){if(stopAtDecision)return {r,words,picked};const c=cs.find(c=>c.id==='c29:'+ending)||cs.find(c=>c.id==='c29:sever');picked.add(c.id);r=E.choose(story,r,c.id);continue;}
  const c=cs.find(c=>Object.values(overrides).includes(c.id))||(random?cs[Math.floor(random()*cs.length)]:cs[0]);picked.add(c.id);r=E.choose(story,r,c.id);
  if(light){r.pages=[];r.checkpoints=[];r.history=[];}
 }
 assert.ok(r.ending,'route finishes '+r.node);return {r,words,picked};
}
const first={};for(const ending of story.mainEndings){const {r,words}=route({},ending);assert.equal(r.ending,ending);assert.ok(words>30000,'novel length on a full reading');first[ending]=r}
const truth=route({},'release',true).r;
const betrayed=route({a:'c10:hide',b:'c20:attack',c:'c22:press'},'release',true).r;
assert.ok(!E.choices(story,betrayed).some(c=>c.id==='c29:release'));
assert.ok(betrayed.state.memories.Imani.some(m=>m.id==='lied_voss'));assert.ok(betrayed.state.memories.Wren.some(m=>m.id==='attacked'));
function chapterText(run,key){return Object.keys(story.nodes).filter(k=>k===key||k.startsWith(key+'~')).flatMap(node=>E.beats(story,{...run,node,presentation:[]})).map(b=>b.text).join('\n')}
assert.ok(chapterText(betrayed,'c23').includes('You told me Voss'));
assert.ok(!chapterText(truth,'c23').includes('You told me Voss'));
const injured=route({a:'c04:force',b:'c13:component',c:'c16a:leave',d:'c27:force'},'release',true,{strength:2,speed:2}).r;
assert.ok(injured.state.vars.injury>=3);assert.ok(!E.choices(story,injured).some(c=>c.id==='c29:release'));
const original=first.release,encoded=JSON.stringify(original),fork=E.fork(story,original,2,'Replay');
assert.notEqual(fork.id,original.id);assert.equal(fork.ending,null);assert.equal(JSON.stringify(original),encoded);assert.equal(fork.state.memories.Imani,undefined);assert.ok(fork.pages.some(p=>p.node===fork.node));assert.ok(!fork.pages.some(p=>p.chapter>2));assert.deepEqual(fork.state.identity,original.state.identity);
const map=new Map(),storage={getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v)};
let book=E.storeRun(story,E.emptyBook(story),original);book=E.storeRun(story,book,fork);fork.reading={revealed:1,ready:false,scroll:183};book=E.storeRun(story,book,fork);E.persist(story,book,storage);
const reloaded=E.load(story,storage);assert.equal(reloaded.playthroughs[1].reading.scroll,183);assert.equal(reloaded.playthroughs[1].reading.revealed,1);assert.equal(reloaded.playthroughs[0].ending,'release');assert.equal(reloaded.recovery,false);
const raw=storage.getItem('night-story:the-last-call:v3');storage.setItem('night-story:the-last-call:v3','{bad');assert.ok(E.load(story,storage).recovery);assert.equal(storage.getItem('night-story:the-last-call:v3'),'{bad');storage.setItem('night-story:the-last-call:v3',raw);
assert.throws(()=>E.choose(story,fork,'c29:release'),/unavailable/);
// Identity, text safety and point budgets.
for(const who of [{firstName:'Éloïse Ana',surname:"D’Ávila-MacLeod"},{firstName:'山田',surname:'太郎'},{firstName:'Zoë',surname:"O'Neill"}]){const r=E.start(story,'Unicode',who,defaultBuild);assert.deepEqual(r.state.identity,who);assert.ok(E.text('{{fullName}}',r).includes(who.firstName));}
assert.equal(E.identity(story,{firstName:'E\u0301loïse',surname:'  van   Dijk  '}).firstName,'Éloïse');
assert.throws(()=>E.identity(story,{firstName:'<img src=x onerror=alert(1)>',surname:'X'}));
assert.equal(E.text('{{firstName}}',{identity:{firstName:'<b>text</b>',surname:'X'}}),'<b>text</b>','templates produce text, never markup');
assert.throws(()=>E.start(story,'Bad',{}, {magic:5}),/limit|points/);assert.throws(()=>E.allocate(story,E.start(story),{magic:-1}));
assert.equal(E.start(story,'Unallocated').state.points,4);assert.equal(E.start(story,'Allocated',{},defaultBuild).state.points,0);
assert.ok(first.release.state.points<=3);assert.ok(Object.values(first.release.state.attributes).reduce((a,b)=>a+b,0)+first.release.state.points<30,'normal route cannot maximise everything');
const second=E.start(story,'Other',{firstName:'Amélie',surname:'Le Roy'},{strength:4});book=E.storeRun(story,book,second);assert.notDeepEqual(book.playthroughs[0].state.identity,book.playthroughs[2].state.identity);
// No power, ancestry disclosure or true cadence in the opening/journal/menus.
const opening=E.start(story);assert.deepEqual(opening.state.abilities,{});assert.ok(!opening.state.knowledge.includes('heritage'));
for(const id of Object.keys(story.nodes).filter(k=>story.nodes[k].chapter<story.nodes['heritage-record'].chapter)){
 const t=story.nodes[id].beats.map(b=>b.text).join(' ');assert.ok(!/I am your father|Thoth is my (?:divine )?father/i.test(t),'heritage hidden in '+id);
}
assert.ok(first.release.state.knowledge.includes('heritage'));assert.match(chapterText(first.release,'mother-answer'),/She believed the copy/);
assert.ok(!chapterText(first.release,'c32-release').includes('Sam'),'custom identity in ending');
// Six attributes each unlock a distinct authored solution.
const statGates=['c04:cunning','c13:strength','c20:speed','c27:nerve','archive:ledger','water:seal'];
const builds={observation:{observation:2,magic:2},nerve:{nerve:2,magic:2},speed:{speed:2,magic:2},strength:{strength:2,magic:2},cunning:{cunning:2,magic:2},magic:{magic:2,observation:2}};
const gatePlans={nerve:{a:'archive:dennis'},magic:{a:'lesson:seal'}};
for(const [attr,id] of [['cunning',statGates[0]],['strength',statGates[1]],['speed',statGates[2]],['nerve',statGates[3]],['observation',statGates[4]],['magic',statGates[5]]]){
 const result=route({pick:id,...gatePlans[attr]},'sever',false,builds[attr],{light:true});assert.ok(result.picked.has(id),'available real '+attr+' solution');
}
const scribe=route({},'release',true).r;assert.ok(E.choices(story,scribe).some(c=>c.id==='c29:seal'));assert.ok(!E.choices(story,opening).some(c=>c.id==='c29:seal'));
const spirit=route({a:'lesson:sight',b:'archive:spirit',c:'training:sight'},'release',true).r;assert.ok(E.choices(story,spirit).some(c=>c.id==='c29:spirit'));
const chaos=route({a:'lesson:ordinary',b:'training:fracture',c:'archive:dennis'},'sever',false,{cunning:2,nerve:2}).r;assert.ok(chaos.state.abilities.fracture);assert.ok(!chaos.state.abilities.seal);
for(const r of [scribe,spirit,chaos]){assert.ok(Object.keys(r.state.abilities).length<=2);for(const a of Object.values(r.state.abilities))assert.ok(a.level<=3)}
// Hearts are capped, persist, warn before spending, and pay once per authored opportunity.
let heart=E.start(story);const effects=[{op:'resource',key:'hearts',value:-1,once:'critical'}];heart.state=E.apply(heart.state,effects,{story});assert.equal(heart.state.resources.hearts,2);heart.state=E.apply(heart.state,effects,{story});assert.equal(heart.state.resources.hearts,2);
heart=E.enter(story,heart,'field-return~1'); // last page contains the once-only repair
const fieldLast=Object.keys(story.nodes).filter(k=>k==='field-return'||k.startsWith('field-return~')).at(-1);heart=E.enter(story,heart,fieldLast);assert.equal(heart.state.resources.hearts,3);heart.state=E.apply(heart.state,[{op:'resource',key:'hearts',value:-1,once:'another'}],{story});heart=E.enter(story,heart,fieldLast);assert.equal(heart.state.resources.hearts,2,'cannot farm repair by re-entry');
const doomed=route({a:'water:risk',b:'c27:force',c:'ren:offer'},'sever',false,defaultBuild).r;
// The field repair permits one more mistake; spending all returns is separately tested with one left.
let one=E.clone(truth);one.state.resources.hearts=1;one=E.enter(story,one,'ren-boundary');one=E.choose(story,one,'ren:offer');assert.equal(one.node,'last-return');assert.equal(one.state.resources.hearts,0);assert.ok(E.beats(story,one).some(b=>b.text.includes('last knot')));while(!one.ending)one=E.choose(story,one,E.choices(story,one)[0].id);assert.equal(one.ending,'lost');
for(const n of Object.values(story.nodes))for(const c of n.choices||[])if(c.effects?.some(e=>e.op==='resource'&&e.value<0))assert.ok(c.risk,'critical cost stated '+c.id);
let failureBook=E.storeRun(story,E.emptyBook(story),one);assert.deepEqual(failureBook.endings,[]);E.persist(story,failureBook,storage);assert.equal(E.load(story,storage).playthroughs[0].state.resources.hearts,0);
// Actual v3 fixture migrates identity, typed defaults, ability lessons and historical conditional pages.
const legacy=require('./fixtures/v3-save.json');storage.setItem('night-story:the-last-call:v3',JSON.stringify(legacy));const migrated=E.load(story,storage);assert.equal(migrated.playthroughs.length,1);assert.equal(migrated.recovery,false);const old=migrated.playthroughs[0];assert.deepEqual(old.state.identity,{firstName:'Sam',surname:'Mansour'});assert.equal(old.state.resources.hearts,3);assert.ok(old.state.abilities.seal);assert.deepEqual(old.state.memories,legacy.playthroughs[0].state.memories);assert.ok(old.pages.flatMap(p=>p.text).some(t=>t.includes('my')));
let past=E.clone(old);past.node='c31-release';past.visited.push('c24');past.revision=3;past.ending='release';storage.setItem('night-story:the-last-call:v3',JSON.stringify({...legacy,playthroughs:[past]}));past=E.load(story,storage).playthroughs[0];assert.ok(past.legacyHeritagePending);const bookmark=past.node;past=E.interlude(story,past,'heritage-late');for(let i=0;i<20&&!past.ending;i++)past=E.choose(story,past,E.choices(story,past)[0].id);assert.equal(past.node,bookmark);assert.equal(past.ending,'release');assert.ok(past.state.knowledge.includes('heritage'));
// Exercise every decision that can be reached in an ordinary route, then all 126 starting builds.
let variants=0;for(const n of Object.values(story.nodes))for(const c of n.choices||[])if(!c.continue&&!c.id.startsWith('c29:')){route({pick:c.id},'sever',false,defaultBuild,{light:true});variants++}
const keys=Object.keys(story.rules.attributes),allocations=[];function distribute(i,left,a){if(i===keys.length-1){allocations.push({...a,[keys[i]]:left});return}for(let n=0;n<=left;n++)distribute(i+1,left-n,{...a,[keys[i]]:n})}distribute(0,4,{});
let seed=274;const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296};for(const allocation of allocations)route({},'sever',false,allocation,{light:true,random});
E.persist(story,book,storage);
const minWords=Math.min(...Object.values(first).map(r=>r.pages.reduce((n,p)=>n+p.text.reduce((m,t)=>m+t.split(/\s+/).length,0),0)));
console.log('PASS: '+seen.size+' passages, '+story.chapters.length+' chapters; three novel routes; '+variants+' decision variants; '+allocations.length+' starting builds with seeded choices; all nodes have a finite unconditional ending route.');
console.log('PASS: Unicode identities, safe templates, points/gates, distinct disciplines/mastery, one-shot hearts/zero failure, historical v3 migration, delayed memories, independent saves/replays and spoiler-safe canon.');
console.log('WORDS:',story.wordCount,'across routes;',minWords,'on the shortest complete tested reading.');
fs.writeFileSync('/tmp/night-story-test-runs.json',JSON.stringify({endingRuns:first,decision:truth,spirit,chaos,book}));
module.exports={route,story,first};
