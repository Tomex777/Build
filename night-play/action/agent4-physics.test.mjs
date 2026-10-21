// Targeted physical invariants for the Brick Breaker gameplay engine.
// Unlike a screenshot or source regex, this invokes the real functions from
// games.js with deterministic positions to exercise collision boundaries.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createContext,runInContext} from 'node:vm';
const source=readFileSync(new URL('./games.js',import.meta.url),'utf8');
const first=source.indexOf('const BRICK_FINAL_LEVEL=6;');
const last=source.indexOf('\nfunction paintBricks(){',first);
assert.ok(first>0&&last>first,'find the actual Brick Breaker engine');
// Fixed-seed PRNG makes a full six-stage run repeatable in CI.
let seed=7;
const stableMath=Object.create(Math);
stableMath.random=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
const sandbox={
 Math:stableMath,
 W:390,H:550,TAU:Math.PI*2,
 inputs:{left:false,right:false,drag:false,lastX:0},
 reducedEffects:false,
 started:true,state:null,
 clamp:(n,a,b)=>Math.min(b,Math.max(a,n)),
 rand:(a,b)=>a+(b-a)*.5,
 audio:()=>{},updateHud:()=>{},saveBest:()=>{},readBest:()=>0,
 overlay:()=>{},end:()=>{throw new Error('Unexpected game over in physics fixture')},
 $:()=>({textContent:''}),
};
createContext(sandbox);
const bricks=runInContext(source.slice(first,last)+
 '\n({initBricks,makeBrickRow,newBricksLevel,tickBricks,brickSpeed,launchBall})',sandbox);
function fixture(level=6,paddle=195){
 const q=bricks.initBricks();
 sandbox.state=q;
 q.level=level;bricks.newBricksLevel();
 q.paddle=paddle;
 q.bricks=[{x:15,y:66,w:46,h:21,hp:1,maxHp:1}];
 q.attached=false;
 sandbox.inputs.left=sandbox.inputs.right=sandbox.inputs.drag=false;
 return q;
}
assert.equal(bricks.makeBrickRow(1).length,42,'opening board contains 42 bricks');
const six=fixture();
assert.equal(six.paddleWidth,52,'sixth stage truly narrows the paddle');
assert.ok(bricks.brickSpeed(six)>bricks.brickSpeed(fixture(1)),'later stage is faster');

// Spark particles should disappear after their life ends, including while
// the replacement ball is attached. Otherwise ghost sparks linger indefinitely.
{
 const q=fixture();q.attached=true;q.impact=.1;
 q.particles=[{x:32,y:90,vx:55,vy:-20,life:.06,maxLife:.06,color:'#fff',r:2}];
 bricks.tickBricks(.16);
 assert.equal(q.particles.length,0,'expired sparks must be removed');
 assert.equal(q.impact,0,'paddle flash must fade');
}
// Sixth-stage paddle collision must follow its VISIBLE width + ball radius.
{
 const q=fixture();q.ball={x:230,y:477,vx:0,vy:400};
 bricks.tickBricks(.02);
 assert.ok(q.ball.vy>0,'a ball 35px from a 52px paddle must miss');
}
{
 const q=fixture(1);q.ball={x:235,y:477,vx:0,vy:400};
 bricks.tickBricks(.02);
 assert.ok(q.ball.vy<0,'same 40px offset must land on wide first-stage paddle');
}
// A fast diagonal crossing must test impact position, not end-of-frame X.
{
 const q=fixture();
 q.ball={x:146,y:479,vx:650,vy:400};
 bricks.tickBricks(.03);
 assert.ok(q.ball.vy>0,'crossing outside paddle must not be rescued by later X');
}
// Player motion adds steering/spin. A moving paddle must not behave identically
// to a stationary paddle under the same ball-relative contact.
let stationary,moving;
{
 const q=fixture(6,210);q.ball={x:215,y:479,vx:0,vy:400};
 bricks.tickBricks(.02);stationary=q.ball.vx;
}
{
 const q=fixture(6,195);q.ball={x:215,y:479,vx:0,vy:400};
 sandbox.inputs.drag=true;sandbox.inputs.lastX=210;
 bricks.tickBricks(.02);moving=q.ball.vx;
}
assert.ok(moving>stationary+50,'moving the paddle must steer ball direction');
assert.equal(sandbox.state.paddle,210);
// Full progression: this bot manipulates the same paddle INPUT as a player
// and invokes the exact production tick. It cannot remove bricks, skip levels,
// change ball position or inject a win flag. This is a fast physics SIMULATION,
// not a replacement for a browser touch/visual playthrough.
{
 const q=bricks.initBricks();sandbox.state=q;
 sandbox.inputs.left=sandbox.inputs.right=sandbox.inputs.drag=false;
 const stages=new Set([q.level]);
 function mirrorSideWalls(x){
  const period=2*(390-16),r=((x-8)%period+period)%period;
  return 8+(r<=390-16?r:period-r);
 }
 let frames=0;
 for(;frames<120000&&!q.won&&q.lives>0;frames++){
  if(q.attached)bricks.launchBall();
  else if(q.ball.vy>0&&q.ball.y>=270){
   const ball=q.ball,remaining=(482-ball.y)/ball.vy;
   const landing=mirrorSideWalls(ball.x+ball.vx*remaining);
   const target=q.bricks.reduce((best,b)=>
    Math.abs(b.x+b.w/2-landing)<Math.abs(best.x+best.w/2-landing)?b:best);
   const angle=Math.max(-.85,Math.min(.85,Math.atan(
    (target.x+target.w/2-landing)/(482-target.y-target.h/2))));
   sandbox.inputs.drag=true;
   sandbox.inputs.lastX=Math.max(q.paddleWidth/2,
    Math.min(390-q.paddleWidth/2,landing-angle/1.02*(q.paddleWidth/2+3)));
  }
  bricks.tickBricks(.016);
  stages.add(q.level);
 }
 assert.equal(q.won,true,'production physics must allow a complete six-stage clear');
 assert.equal(stages.size,6,'each of the six stages must be reached organically');
 assert.ok(q.score>=4500,'full clear must be scored');
 assert.ok(q.lives>0,'automation did not cheat by bypassing loss accounting');
 console.log('SIMULATION six real stages cleared: '+JSON.stringify({frames,
  simulatedSeconds:Math.round(frames*.016),score:q.score,lives:q.lives}));
}
console.log('PASS Brick Breaker actual physics: 42 bricks, all six stages, shrink, sparks decay, contact timing, collision edges and paddle spin.');
