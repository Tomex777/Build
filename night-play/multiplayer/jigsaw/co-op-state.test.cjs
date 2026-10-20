'use strict';
const assert=require('node:assert/strict');
const {createPuzzle,applyCommand,publicSnapshot,LEASE_MS}=require('./co-op-state.cjs');
const HOST='verifiedHost001',A='guestAlice0001',B='guestBob000002';
let state=createPuzzle({photoId:'photo-alpine-lake',hostId:HOST,hostName:'Host',seed:17},1000);
function command(cmd,at=2000,accepted=true){
 const prior=state,result=applyCommand(state,{roundId:state.roundId,...cmd},at);
 assert.equal(result.accepted,accepted,result.error||cmd.type);
 if(accepted)state=result.state;else assert.equal(result.state,prior,'rejected op must be atomic');
 return result;
}
command({type:'join',actor:A,name:'Alice'},1001);
command({type:'join',actor:B,name:'Bob'},1002);
assert.equal(state.players.length,3);
assert.equal(command({type:'join',actor:B,name:'Someone else'},1003).duplicate,true);
assert.equal(state.players[2].name,'Bob');
assert.equal(publicSnapshot(state,A,1005).players.length,3);
const view=publicSnapshot(state,A,1005);
assert.equal(JSON.stringify(view).includes(HOST),false,'never expose actor/session IDs');
assert.equal(JSON.stringify(view).includes('opLog'),false,'never expose operation log');
const id='0-0',start=state.pieces.find(p=>p.id===id);
command({type:'claim',actor:A,pieceId:id,expectedVersion:0},2000);
command({type:'claim',actor:B,pieceId:id,expectedVersion:0},2001,false);
command({type:'drop',actor:B,pieceId:id,expectedVersion:0,x:0,y:0},2002,false);
assert.equal(publicSnapshot(state,A,2002).pieces[0].heldByMe,true);
assert.equal(publicSnapshot(state,B,2002).pieces[0].heldByMe,false);
const moveId='moving-piece-once-0001';
command({type:'drop',actor:A,pieceId:id,expectedVersion:0,x:start.x+.5,y:start.y+.25,mutationId:moveId},2100);
assert.equal(state.moves,1);
assert.equal(command({type:'drop',actor:A,pieceId:id,expectedVersion:0,x:3,y:3,mutationId:moveId},2101).duplicate,true);
assert.equal(state.moves,1,'replayed uncertain POST must not count twice');
command({type:'claim',actor:B,pieceId:id,expectedVersion:0},2200,false);
const version=state.pieces[0].version;
command({type:'claim',actor:B,pieceId:id,expectedVersion:version},2201);
command({type:'drop',actor:B,pieceId:id,expectedVersion:version,x:0,y:0},2202);
assert.equal(state.pieces[0].placed,true,'valid near-board drop snaps');
assert.equal(state.pieces[0].x,0);
command({type:'claim',actor:A,pieceId:id,expectedVersion:state.pieces[0].version},2203,false);
const p=state.pieces.find(x=>x.id==='0-1');
command({type:'claim',actor:A,pieceId:p.id,expectedVersion:0},2300);
command({type:'drop',actor:A,pieceId:p.id,expectedVersion:0,x:1.02,y:0.02},2301);
assert.equal(state.pieces.find(x=>x.id==='0-1').placed,true);
const moveOne=state.pieces.find(x=>x.id==='2-2');
const moveTwo=state.pieces.find(x=>x.id==='2-3');
command({type:'claim',actor:A,pieceId:moveTwo.id,expectedVersion:0},2400);
command({type:'drop',actor:A,pieceId:moveTwo.id,expectedVersion:0,x:5,y:5},2401);
command({type:'claim',actor:A,pieceId:moveOne.id,expectedVersion:0},2402);
command({type:'drop',actor:A,pieceId:moveOne.id,expectedVersion:0,x:4.03,y:5.02},2403);
assert.equal(state.pieces.find(x=>x.id==='2-2').group,state.pieces.find(x=>x.id==='2-3').group,'nearby neighbors should merge');
const combined=state.pieces.find(x=>x.id==='2-2');
assert.equal(state.pieces.filter(x=>x.group===combined.group).length,2);
command({type:'claim',actor:B,pieceId:combined.id,expectedVersion:combined.version},2500);
command({type:'drop',actor:B,pieceId:combined.id,expectedVersion:combined.version,x:2,y:2},2501);
assert.equal(state.pieces.find(x=>x.id==='2-2').placed,true);
assert.equal(state.pieces.find(x=>x.id==='2-3').placed,true);
const p3=state.pieces.find(x=>x.id==='3-1');
command({type:'claim',actor:A,pieceId:p3.id,expectedVersion:0},3000);
command({type:'claim',actor:B,pieceId:p3.id,expectedVersion:0},3000+LEASE_MS+1);
command({type:'drop',actor:A,pieceId:p3.id,expectedVersion:0,x:1,y:3},3000+LEASE_MS+2,false);
command({type:'drop',actor:B,pieceId:p3.id,expectedVersion:0,x:1,y:3},3000+LEASE_MS+3);
assert.equal(state.pieces.find(x=>x.id==='3-1').placed,true);
const p4=state.pieces.find(x=>x.id==='3-2');
command({type:'claim',actor:A,pieceId:p4.id,expectedVersion:0},22000);
command({type:'leave',actor:A},22001);
assert.equal(state.leases.some(l=>l.actor===A),false,'leaving releases leases');
command({type:'claim',actor:B,pieceId:p4.id,expectedVersion:0},22002);
command({type:'drop',actor:B,pieceId:p4.id,expectedVersion:0,x:NaN,y:2},22003,false);
command({type:'release',actor:B,pieceId:p4.id},22004);
command({type:'rename',actor:HOST,name:'New Host Name'},22005);
assert.equal(publicSnapshot(state,B,22005).players[0].name,'New Host Name');
const before=state.revision;
command({type:'leave',actor:HOST},22006,false);
assert.equal(state.revision,before);
for(const count of [24,48,144]){
 const round=createPuzzle({photoId:'photo-alpine-lake',hostId:HOST,hostName:'Host',count,seed:4});
 assert.equal(round.pieces.length,count);
 assert.equal(new Set(round.pieces.map(p=>p.id)).size,count);
 assert.deepEqual(round.pieces,createPuzzle({photoId:'photo-alpine-lake',hostId:HOST,hostName:'Host',count,seed:4}).pieces);
}
assert.equal(applyCommand(state,{roundId:'00000000-0000-0000-0000-000000000000',type:'heartbeat',actor:HOST}).accepted,false,'old round cannot mutate current board');
assert.equal(publicSnapshot(state,HOST).roundId,state.roundId);
assert.throws(()=>createPuzzle({photoId:'https://evil.example/x',hostId:HOST,hostName:'Host'}));
assert.throws(()=>createPuzzle({photoId:'photo-alpine-lake',count:12,hostId:HOST,hostName:'Host'}));
assert.throws(()=>createPuzzle({photoId:'photo-alpine-lake',count:'24',hostId:HOST,hostName:'Host'}));
const outsider=applyCommand(state,{roundId:state.roundId,type:'rename',actor:'unjoinedStranger001',name:'Impersonated'},22007);
assert.equal(outsider.accepted,false,'unjoined actor must be denied');
assert.equal(outsider.state,state,'unauthorized change must be atomic');
const roomWithCap=createPuzzle({photoId:'photo-alpine-lake',hostId:HOST,hostName:'Host'});
let full=roomWithCap;
for(let i=0;i<15;i++){
 const joined=applyCommand(full,{roundId:full.roundId,type:'join',actor:'guestForCapacity'+String(i).padStart(3,'0'),name:'Guest '+i});
 assert.equal(joined.accepted,true);full=joined.state;
}
assert.equal(full.players.length,16);
assert.equal(applyCommand(full,{roundId:full.roundId,type:'join',actor:'extraGuestBeyondCap001',name:'Extra'}).accepted,false);
for(const count of [24,48,144]){
 const puzzle=createPuzzle({photoId:'photo-alpine-lake',count,hostId:HOST,hostName:'Host',seed:188});
 const map=new Map(puzzle.pieces.map(p=>[p.id,p]));
 for(const p of puzzle.pieces){
  assert.ok([-1,0,1].includes(p.edges.left)&&[-1,0,1].includes(p.edges.right));
  assert.equal(p.col===0,p.edges.left===0);
  assert.equal(p.row===0,p.edges.top===0);
  if(p.col<puzzle.cols-1)assert.equal(p.edges.right,-map.get(p.row+'-'+(p.col+1)).edges.left);
  if(p.row<puzzle.rows-1)assert.equal(p.edges.bottom,-map.get((p.row+1)+'-'+p.col).edges.top);
 }
 assert.deepEqual(publicSnapshot(puzzle,HOST).pieces[0].edges,puzzle.pieces[0].edges);
}
console.log('PASS: authoritative co-op Jigsaw 24/48/144, independent clients, grouping, stale claims, leases, replay, privacy, rename, and validation');
