'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const html=fs.readFileSync(__dirname+'/index.html','utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(script);
const data=script.slice(script.indexOf('const scenes=')+13,script.indexOf(';\n\nconst reduced='));
const scenes=JSON.parse(data);
const flags=['ally','truth','mercy','seal'];
const key=(id,state)=>id+':'+flags.map(f=>state[f]?'1':'0').join('');
const nodes=new Map(),queue=[['start',{}]],endings=new Set();
while(queue.length){
 const [id,state]=queue.shift(),k=key(id,state);
 if(nodes.has(k))continue;
 const s=scenes[id];assert.ok(s,'destination exists: '+id);
 assert.ok(s.beats.length&&s.beats.every(b=>typeof b.text==='string'&&b.text.trim()),'nonempty narration: '+id);
 const opts=s.options.filter(o=>!o[3]||state[o[3]]);
 assert.ok(opts.length,'every state has a choice: '+k);
 const edges=[];
 for(const [label,dest,effects,requirement] of opts){
  assert.ok(label&&scenes[dest],'valid choice: '+id+' → '+dest);
  assert.ok(!requirement||flags.includes(requirement));
  const next=dest==='start'?{}:{...state,...effects};
  edges.push(key(dest,next));
  if(dest!=='start')queue.push([dest,next]);
 }
 nodes.set(k,{id,edges,ending:!!s.ending});
 if(s.ending)endings.add(id);
}
assert.deepEqual([...endings].sort(),['home','keeper','severed']);
assert.equal(new Set([...nodes.values()].map(n=>n.id)).size,Object.keys(scenes).length,'all authored scenes reachable');
let finish=new Set([...nodes].filter(([,n])=>n.ending).map(([k])=>k));
let changed=true;
while(changed){changed=false;for(const [k,n] of nodes)if(!finish.has(k)&&n.edges.some(e=>finish.has(e))){finish.add(k);changed=true}}
for(const k of nodes.keys())assert.ok(finish.has(k),'route must reach a real ending: '+k);
for(const flag of flags){
 assert.ok(Object.values(scenes).some(s=>s.options.some(o=>o[2]?.[flag])),'flag can be earned: '+flag);
 assert.ok(Object.values(scenes).some(s=>s.options.some(o=>o[3]===flag)),'flag has consequences: '+flag);
}
console.log('PASS: '+Object.keys(scenes).length+' scenes, '+nodes.size+' reachable scene/flag states, 3 reachable endings, all states can finish; all four choice flags affect later routes; renderer JavaScript parses.');
