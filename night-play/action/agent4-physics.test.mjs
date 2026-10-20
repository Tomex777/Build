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
const sandbox={
 W:390,H:550,TAU:Math.PI*2,
 inputs:{left:false,right:false,drag:false,lastX:0},
 started:true,state:null,
 clamp:(n,a,b)=>Math.min(b,Math.max(a,n)),
 rand:(a,b)=>a+(b-a)*.5,
 audio:()=>{},updateHud:()=>{},saveBest:()=>{},readBest:()=>0,
 overlay:()=>{},end:()=>{throw new Error('Unexpected game over in physics fixture')},
 $:()=>({textContent:''}),
};
createContext(sandbox);
const bricks=runInContext(source.slice(first,last)+
 '\n({initBricks,makeBrickRow,newBricksLevel,tickBricks,brickSpeed})',sandbox);
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
console.log('PASS Brick Breaker actual physics: 42 bricks, stage 6 shrink, sparks decay, contact timing, collision edges and real paddle spin.');
