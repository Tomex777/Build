import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

// Run the real engine with a deterministic clock, including every delayed hop.
let now=0,nextId=0,seed=4173;
const jobs=new Map(),random=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
function schedule(fn,ms,repeat=0){const id=++nextId;jobs.set(id,{fn,time:now+ms,repeat});return id;}
const sandbox={location:{hostname:'localhost'},Math:Object.assign(Object.create(Math),{random}),matchMedia:()=>({matches:true}),
 setTimeout:(fn,ms)=>schedule(fn,ms),setInterval:(fn,ms)=>schedule(fn,ms,ms),clearTimeout:id=>jobs.delete(id),clearInterval:id=>jobs.delete(id),
 crypto:{getRandomValues:a=>{a[0]=Math.floor(random()*4294967296);return a;}}};
sandbox.window=sandbox;
vm.runInNewContext(readFileSync(new URL('./naija-ludo.js',import.meta.url),'utf8'),sandbox);
const a=sandbox.NightLudo;
function tick(){const [id,j]=[...jobs].sort((x,y)=>x[1].time-y[1].time||x[0]-y[0])[0]||[];if(!j)return false;now=j.time;if(j.repeat)j.time+=j.repeat;else jobs.delete(id);j.fn();return true;}
function flush(){for(let n=0;tick();n++)assert.ok(n<1000,'a turn must finish without an infinite timer loop');}
const copy=s=>JSON.parse(JSON.stringify(s));
function draw(s){a.normalize(s);}
function pending(s,values){s.rolls=values.map(value=>({value,used:false}));s.last=[...values];s.selectedDie=0;s.die=values[0];s.rolled=true;}
function doRoll(s,values){a.setTestRolls(values);a.roll(s,()=>draw(s));flush();}
function doMove(s,pi,ti,di=s.selectedDie){a.selectDie(s,di,()=>draw(s));assert.ok(a.testLegal(s,pi,ti,di));a.move(s,pi,ti,()=>draw(s));flush();}

let s=a.create(2,2);s.sound=false;
doRoll(s,[6,2]);doMove(s,0,0,0);
assert.equal(s.players[0].tokens[0],0);assert.equal(s.rolls[0].used,true);assert.equal(s.selectedDie,1);
doMove(s,0,0,1);assert.equal(s.players[0].tokens[0],2);assert.equal(s.turn,0);assert.equal(s.rolls.length,0);
doRoll(s,[2,3]);doMove(s,0,0,1);doMove(s,0,0,0);assert.equal(s.players[0].tokens[0],7);assert.equal(s.turn,1);
assert.equal(a.testLegal(s,-1,0,0),false);assert.equal(a.testLegal(s,1,999,0),false);

// Cancellation rolls back the whole transaction, not just its timer.
s=a.create(2,1);s.sound=false;s.players[0].tokens[0]=10;pending(s,[4]);const before=copy(s);
a.move(s,0,0,()=>draw(s));while(s.players[0].tokens[0]===10)tick();assert.equal(s.players[0].tokens[0],11);
a.cancel();flush();assert.deepEqual(copy(s),before);doMove(s,0,0);assert.equal(s.players[0].tokens[0],14);assert.equal(s.turn,1);
const beforeRoll=copy(s);a.setTestRolls([6]);a.roll(s,()=>draw(s));a.cancel();flush();assert.deepEqual(copy(s),beforeRoll);

// Duplicate taps and setup changes cannot spend a die twice or reset an action.
s=a.create(2,1);s.sound=false;s.players[0].tokens[0]=12;pending(s,[3]);a.move(s,0,0,()=>draw(s));
a.move(s,0,0,()=>draw(s));assert.equal(a.changePlayers(s,4),false);assert.equal(a.changeDice(s,2),false);assert.equal(a.changeDifficulty(s,'hard'),false);
flush();assert.equal(s.players[0].tokens[0],15);assert.equal(s.turn,1);

// Safe squares, captures, exact home entry, and the configured capture bonus.
s=a.create(2,1);s.sound=false;s.players[0].tokens[0]=14;s.players[1].tokens[0]=2;pending(s,[1]);s.options.bonusOnCapture=true;
doMove(s,0,0);assert.equal(s.players[0].tokens[0],56);assert.equal(s.players[1].tokens[0],-1);assert.equal(s.turn,0);assert.match(s.msg,/Capture bonus/);assert.doesNotMatch(s.msg,/Six!/);
s=a.create(2,1);s.sound=false;s.players[0].tokens[0]=20;s.players[1].tokens[0]=8;pending(s,[1]);doMove(s,0,0);assert.equal(s.players[1].tokens[0],8);
s=a.create(2,1);s.sound=false;s.players[0].tokens[0]=55;pending(s,[2]);assert.equal(a.testLegal(s,0,0,0),false);
pending(s,[1]);doMove(s,0,0);assert.equal(s.players[0].tokens[0],56);
s=a.create(2,1);s.sound=false;s.players[0].tokens.fill(56);s.players[0].tokens[0]=55;pending(s,[1]);doMove(s,0,0);
assert.equal(s.winner,0);assert.equal(s.rolls.length,0);assert.equal(s.rolled,false);assert.equal(s.bonus,false);
const won=copy(s);a.roll(s,()=>draw(s));flush();assert.deepEqual(copy(s),won);
for(const color of ['green','yellow','blue','red'])for(let pos=0;pos<=56;pos++){const xy=a.testCell(color,pos);assert.ok(xy&&xy.every(n=>n>=0&&n<15),`${color} has a valid board square at ${pos}`);}

// Resume repairs unavailable selections, spent rolls, invalid saves and winners.
s=a.create(2,2);pending(s,[2,6]);s=a.resume(s);assert.equal(s.selectedDie,1);assert.equal(s.die,6);
s.rolls.forEach(d=>d.used=true);s=a.resume(s);assert.equal(s.rolls.length,0);assert.equal(s.turn,1);
s=a.create();s.turn=99;s.selectedDie=55;s.last=[null,-7];s=a.resume(s);assert.equal(s.turn,0);assert.equal(s.selectedDie,null);assert.deepEqual([...s.last],[1,1]);
s=a.create();s.players[0].tokens[0]=NaN;assert.equal(a.resume(s).players[0].tokens[0],-1);
assert.equal(a.resume({version:2,players:{length:2}}).players.length,2);
s=a.create(2,1);s.players[0].tokens.fill(56);s.moving=true;pending(s,[1]);assert.equal(a.resume(s).winner,0);

// A crowded square renders every counter and offers a large touch picker.
s=a.create(2,1);s.players[0].tokens.fill(0,0,4);s.players[0].tokens.fill(26,4);pending(s,[1]);
const ui={board:{innerHTML:''},controls:{innerHTML:''},status:{},rules(){},scoreList(){return '';},choosePlayers(){return '';},save(){}};
a.render(s,ui);assert.equal((ui.board.innerHTML.match(/data-piece=/g)||[]).length,16);assert.match(ui.board.innerHTML,/nj-many/);assert.match(ui.controls.innerHTML,/nj-counter-picker/);

// Play complete deterministic matches with every player/dice configuration.
let matches=0,totalActions=0;
for(const count of [2,3,4])for(const diceCount of [1,2])for(const finishes of [false,true])for(let run=0;run<8;run++){
 a.cancel();s=a.create(count,diceCount);s.sound=false;s.options.captureFinishes=finishes;s.options.directCount=run%2===0;s.options.safeHouse=run%3!==0;s.options.barrier=run%2===0;
 const counters=s.players.reduce((n,p)=>n+p.tokens.length,0);
 let actions=0;
 while(s.winner===null&&actions++<16000){
  if(!s.rolls.length)doRoll(s,Array.from({length:diceCount},()=>1+Math.floor(random()*6)));
  else{const choices=[];s.rolls.forEach((d,di)=>s.players[s.turn].tokens.forEach((at,ti)=>{if(a.testLegal(s,s.turn,ti,di))choices.push({di,ti,at});}));assert.ok(choices.length,'pending dice must offer a playable move');
   choices.sort((x,y)=>y.at-x.at);const choice=random()<.75?choices[0]:choices[Math.floor(random()*choices.length)];doMove(s,s.turn,choice.ti,choice.di);}
  assert.equal(s.moving,false);assert.equal(s.rolling,false);assert.equal(s.players.reduce((n,p)=>n+p.tokens.length,0),counters);
  assert.ok(s.players.every(p=>p.tokens.every(n=>Number.isInteger(n)&&n>=-1&&n<=56)));
  if(s.rolls.length)assert.ok(s.rolls[s.selectedDie]&&!s.rolls[s.selectedDie].used,'selected die is available');
  if(s.winner===null&&random()<.07){const saved=JSON.parse(JSON.stringify(s));s=a.resume(saved);}
 }
 assert.notEqual(s.winner,null,`a ${count}-player, ${diceCount}-die match must finish`);matches++;totalActions+=actions;
}
for(const difficulty of ['easy','normal','hard','advanced']){
 a.cancel();s=a.create(2,2);s.sound=false;s.opponent='computer';s.difficulty=difficulty;
 const redraw=()=>{a.normalize(s);a.testScheduleComputer(s,redraw);};
 let actions=0;
 while(s.winner===null&&actions++<16000){
  assert.equal(s.turn,0,'CPU completes its own turn before returning control');
  if(!s.rolls.length)a.roll(s,redraw);
  else{const choices=[];s.rolls.forEach((d,di)=>s.players[0].tokens.forEach((at,ti)=>{if(a.testLegal(s,0,ti,di))choices.push({di,ti,at});}));assert.ok(choices.length);choices.sort((x,y)=>y.at-x.at);s.selectedDie=choices[0].di;a.move(s,0,choices[0].ti,redraw);}
  flush();assert.equal(s.moving,false);assert.equal(s.rolling,false);
 }
 assert.notEqual(s.winner,null,`${difficulty} CPU match finishes`);matches++;totalActions+=actions;
}
console.log(`PASS NIGHT Ludo logic: ${matches} full matches / ${totalActions} actions, four CPU difficulties, dice order, captures, exact finishes, interruption rollback, and saved-state recovery`);
