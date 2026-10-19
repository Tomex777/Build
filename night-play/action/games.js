/* NIGHT Play original Action Arcade — five complete canvas games.
 * Runner physics and obstacle categories adapted from the MIT-licensed
 * gamelabz/html5-game-endless-runner (2026); see THIRD_PARTY_NOTICES.md.
 * All rendering, responsive control layout and other four modes are new.
 */
(()=>{'use strict';
const $=id=>document.getElementById(id),canvas=$('screen'),ctx=canvas.getContext('2d',{alpha:false});
const W=390,H=550,PI=Math.PI,TAU=PI*2;
const defs={
 slice:{title:'Fruit Slice',subtitle:'Slice the fruit. Never touch the bombs.',rules:'Drag a finger across fruit to slice it. Avoid black bombs and do not miss three pieces of fruit. Mouse dragging works on desktop.',label:'MISSES'},
 jump:{title:'Sky Jumper',subtitle:'Climb higher. Land every jump.',rules:'Guide the jumper left and right with the controls, arrow keys, A/D or by dragging horizontally across the screen. Jumping is automatic. Fall below the screen and the run ends.',label:'HEIGHT'},
 runner:{title:'Endless Runner',subtitle:'Jump, slide and survive the run.',rules:'Tap or press Space to jump. Swipe down or hold Slide to duck underneath flying obstacles. On desktop, use Up/Space to jump and Down to duck. The pace gets faster over time.',label:'SPEED'},
 shooter:{title:'Space Shooter',subtitle:'Defend the skies against endless waves.',rules:'Drag your ship left and right; it fires automatically. Dodge enemy shots, collect green shields and survive increasingly difficult waves. Use arrows or A/D on desktop.',label:'WAVE'},
 bricks:{title:'Brick Breaker',subtitle:'Break them all. Control the bounce.',rules:'Drag the paddle or use left/right arrows to keep the ball in play. Tap the game board to launch the ball. Clear every brick to reach the next level.',label:'LIVES'}
};
const soundKey='night-play-action-sfx-v1',bestKey=id=>'night-play-v1-action-'+id;
const clamp=(n,a,b)=>Math.min(b,Math.max(a,n)),rand=(a,b)=>a+Math.random()*(b-a);
let game='slice',state,started=false,over=false,paused=false,frame=0,raf=0,last=0,sfx=true,ctxAudio=null;
try{sfx=localStorage.getItem(soundKey)!=='off'}catch{}
const inputs={left:false,right:false,duck:false,drag:false,lastX:0,lastY:0};
function audio(type='tap'){
 if(!sfx)return;
 try{ctxAudio=ctxAudio||new (window.AudioContext||window.webkitAudioContext)();
  if(ctxAudio.state==='suspended')ctxAudio.resume();
  const osc=ctxAudio.createOscillator(),gain=ctxAudio.createGain();
  osc.type=type==='hit'?'triangle':'sine';
  const t=ctxAudio.currentTime;
  osc.frequency.setValueAtTime(type==='bad'?170:type==='hit'?330:type==='point'?610:410,t);
  osc.frequency.exponentialRampToValueAtTime(type==='bad'?100:type==='hit'?470:type==='point'?960:300,t+.095);
  gain.gain.setValueAtTime(.045,t);gain.gain.exponentialRampToValueAtTime(.0001,t+.13);
  osc.connect(gain);gain.connect(ctxAudio.destination);osc.start(t);osc.stop(t+.14);
 }catch{}
}
function readBest(){try{return Number(localStorage.getItem(bestKey(game)))||0}catch{return 0}}
function saveBest(n){try{if(n>readBest())localStorage.setItem(bestKey(game),String(n))}catch{}}
function updateHud(){
 $('score').textContent=String(Math.floor(state?.score||0));
 $('best').textContent=String(Math.floor(Math.max(readBest(),state?.score||0)));
 $('statLabel').textContent=defs[game].label;
 let value=0;
 if(state){if(game==='slice')value=Math.max(0,3-state.misses);
 if(game==='jump')value=Math.floor(state.score);
 if(game==='runner')value=(state.speed/220).toFixed(1)+'×';
 if(game==='shooter')value=state.wave;
 if(game==='bricks')value=state.lives}
 $('stat').textContent=String(value);
}
function overlay(tag,title,copy,action){
 $('overlayTag').textContent=tag;$('overlayTitle').textContent=title;$('overlayText').textContent=copy;
 $('overlayStart').textContent=action;$('overlay').hidden=false;
}
function hideOverlay(){$('overlay').hidden=true}
function end(text){
 if(over)return;over=true;paused=false;started=false;saveBest(Math.floor(state.score));
 updateHud();overlay('ROUND COMPLETE',text,'Score '+Math.floor(state.score)+' · Best '+Math.floor(readBest()),'Play again');audio('bad');
 $('message').textContent=text;
}
function rounded(x,y,w,h,r=9){ctx.beginPath();ctx.roundRect(x,y,w,h,r)}
function fill(x,y,w,h,color,r=0){ctx.fillStyle=color;if(r){rounded(x,y,w,h,r);ctx.fill()}else ctx.fillRect(x,y,w,h)}
function disk(x,y,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill()}
function text(str,x,y,size=15,color='#1f382d',weight=700,align='center'){
 ctx.font=weight+' '+size+'px system-ui,Arial';ctx.fillStyle=color;ctx.textAlign=align;ctx.fillText(str,x,y);
}
function bg(top,bottom){
 const grad=ctx.createLinearGradient(0,0,0,H);grad.addColorStop(0,top);grad.addColorStop(1,bottom);
 ctx.fillStyle=grad;ctx.fillRect(0,0,W,H);
}
function initSlice(){return {score:0,misses:0,spawn:.55,fruits:[],trail:[],clock:0,combo:0};}
function addFruit(){
 const multi=Math.random()<.35?2:1;
 for(let i=0;i<multi;i++){
 const kind=Math.random()<.12?'bomb':Math.floor(rand(0,5));
 const x=rand(58,W-58),vY=rand(-690,-550);
 state.fruits.push({x,y:H+30+rand(0,35),vx:rand(-90,90),vy:vY,r:kind==='bomb'?25:rand(21,26),kind,hit:false,rotation:rand(0,TAU)});
 }
}
function segDist(x,y,x1,y1,x2,y2){
 const dx=x2-x1,dy=y2-y1,len=dx*dx+dy*dy;
 const t=len?clamp(((x-x1)*dx+(y-y1)*dy)/len,0,1):0;
 return Math.hypot(x-(x1+t*dx),y-(y1+t*dy));
}
function sliceMove(x1,y1,x2,y2){
 if(!started||paused||over)return;
 state.trail.push({x:x2,y:y2,a:1});
 for(const f of state.fruits)if(!f.hit&&segDist(f.x,f.y,x1,y1,x2,y2)<f.r){
  if(f.kind==='bomb'){f.hit=true;end('Bomb hit!');return}
  f.hit=true;state.score+=10;state.combo++;audio('point');
 }
 updateHud();
}
const fruitColors=['#f5ad46','#ec6260','#88c96a','#a37dc6','#e7d454'];
function drawFruit(f){
 ctx.save();ctx.translate(f.x,f.y);ctx.rotate(f.rotation);
 if(f.kind==='bomb'){
  disk(0,0,f.r,'#24313b');disk(-8,-8,6,'#6e7f80');
  fill(-4,-f.r-7,8,12,'#b9ad8b',3);text('!',0,8,24,'#f2d3c0',900);
 }else{
  const color=fruitColors[f.kind];
  disk(0,0,f.r,color);
  ctx.beginPath();ctx.ellipse(-f.r*.3,-f.r*.35,f.r*.35,f.r*.2,-.5,0,TAU);ctx.fillStyle='#ffffff64';ctx.fill();
  fill(-2,-f.r-8,4,12,'#3e6844',2);ctx.beginPath();ctx.ellipse(7,-f.r-4,10,5,-.7,0,TAU);ctx.fillStyle='#41855b';ctx.fill();
  if(f.hit){ctx.globalAlpha=.5;ctx.beginPath();ctx.moveTo(-f.r*.8,f.r*.8);ctx.lineTo(f.r*.8,-f.r*.8);ctx.strokeStyle='#fff9';ctx.lineWidth=3;ctx.stroke();}
 }
 ctx.restore();
}
function tickSlice(dt){
 state.clock+=dt;state.spawn-=dt;if(state.spawn<0){addFruit();state.spawn=rand(.7,1.05)*Math.max(.65,1-state.score/2500);}
 for(const f of state.fruits){f.x+=f.vx*dt;f.y+=f.vy*dt;f.vy+=1010*dt;f.rotation+=dt*.8;
  if(f.y>H+f.r+4&&!f.hit&&f.kind!=='bomb'){f.hit=true;state.misses++;audio('bad')}
 }
 state.fruits=state.fruits.filter(f=>f.y<H+95);
 state.trail=state.trail.filter(p=>(p.a-=dt*4)>0);
 if(state.misses>=3)end('Too many missed fruit');
}
function paintSlice(){
 bg('#e3f1da','#bcdcc4');
 for(let i=0;i<16;i++){disk((i*81)%W,(i*177+28)%H,3+(i%3),'#ffffff43')}
 fill(0,H-37,W,37,'#84b19c');fill(0,H-38,W,2,'#5e9e81');
 for(const f of state.fruits)drawFruit(f);
 if(state.trail.length){ctx.lineWidth=5;ctx.lineCap='round';
  for(let i=1;i<state.trail.length;i++){const a=state.trail[i-1],b=state.trail[i];ctx.strokeStyle='rgba(255,255,255,'+b.a+')';ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();}}
 for(let i=0;i<3;i++)disk(30+i*21,30,7,i<3-state.misses?'#db836d':'#e8e4db');
}
function initJump(){
 const platforms=[{x:120,y:500,w:154,moving:false}];
 for(let y=424;y>=-90;y-=75){platforms.push({x:rand(30,255),y,w:rand(72,100),moving:false})}
 return {score:0,player:{x:180,y:460,vy:-520,vx:0},platforms,scroll:0};
}
function tickJump(dt){
 const p=state.player,prev=p.y;
 const desired=inputs.left?-230:inputs.right?230:inputs.drag?(inputs.lastX-p.x)*9:0;
 p.vx=clamp(desired,-290,290);p.x+=p.vx*dt;
 if(p.x>W+17)p.x=-15;if(p.x< -18)p.x=W+14;
 p.vy+=1230*dt;p.y+=p.vy*dt;
 if(p.vy>0)for(const plat of state.platforms)if(prev+17<=plat.y+4&&p.y+17>=plat.y&&p.x+15>plat.x&&p.x-15<plat.x+plat.w){
  p.y=plat.y-18;p.vy=-560;audio('hit');break;
 }
 if(p.y<185){
  const shift=185-p.y; p.y=185;state.scroll+=shift;state.score=Math.max(state.score,Math.floor(state.scroll));
  for(const plat of state.platforms)plat.y+=shift;
 }
 state.platforms=state.platforms.filter(plat=>plat.y<H+30);
 while(state.platforms.length<11){
  const high=Math.min(...state.platforms.map(q=>q.y));
  const x=rand(25,W-125);
  state.platforms.push({x,y:high-rand(63,79),w:rand(66,107),moving:state.score>600&&Math.random()<.14});
 }
 if(p.y>H+35)end('You fell!');
 updateHud();
}
function paintJump(){
 bg('#d2e8f4','#f0f6e7');
 for(let i=0;i<8;i++){
  const x=(i*117+30)%W,y=((i*106+35)+state.scroll*.22)%670-90;
  disk(x,y,13,'#ffffffac');disk(x+16,y+3,18,'#ffffffac');disk(x+37,y,12,'#ffffffac');
 }
 for(const p of state.platforms){
  fill(p.x,p.y,p.w,11,p.moving?'#a1a5d4':'#6ab38a',7);
  fill(p.x+4,p.y+9,p.w-8,4,'#407d62',2);
 }
 const p=state.player;ctx.save();ctx.translate(p.x,p.y);
 fill(-13,-22,26,33,'#df9269',9);fill(-17,-26,34,13,'#32574c',6);
 disk(-5,-8,2,'#243328');disk(6,-8,2,'#243328');
 fill(-12,9,10,10,'#435c56',3);fill(4,9,10,10,'#435c56',3);ctx.restore();
 text('HEIGHT  '+Math.floor(state.score),W-15,26,13,'#417c68',800,'right');
}
function initRunner(){return {score:0,speed:220,player:{x:88,y:442,vy:0,duck:false},obstacles:[],spawn:1.35,distance:0,duckTimer:0};}
function jumpRunner(){
 const p=state.player;
 if(!started||paused||over)return;
 if(p.y>=442-1){p.vy=-685;p.y=440;audio('hit')}
}
function duckRunner(on){if(!state||game!=='runner')return;state.player.duck=on;}
function tickRunner(dt){
 // MIT reference: jump gravity, duck hitbox, ramping speed and periodic three-class obstacles.
 const p=state.player;
 p.vy+=1690*dt;p.y=Math.min(442,p.y+p.vy*dt);if(p.y>=442)p.vy=0;
 state.distance+=state.speed*dt;state.score=Math.floor(state.distance/12);
 state.speed=Math.min(525,state.speed+5*dt);
 state.spawn-=dt;if(state.spawn<=0){
  const kind=Math.random()<.25?'flying':Math.random()<.55?'pillar':'stone';
  state.obstacles.push({x:W+25,w:kind==='pillar'?29:kind==='flying'?37:43,h:kind==='pillar'?54:kind==='flying'?24:28,kind});
  state.spawn=rand(1.1,1.65)*(220/state.speed)**.36;
 }
 const height=p.duck&&p.y>=442?24:48,left=p.x-13,right=p.x+13,top=p.y-height;
 for(const o of state.obstacles){
  o.x-=state.speed*dt;
  const obTop=o.kind==='flying'?442-76:442-o.h,obBottom=o.kind==='flying'?442-44:442;
  if(left<o.x+o.w-6&&right>o.x+6&&top<obBottom-4&&p.y>obTop+5){end('An obstacle caught you');break;}
 }
 state.obstacles=state.obstacles.filter(o=>o.x>-70);
 updateHud();
}
function paintRunner(){
 bg('#d7e7ef','#d9edd8');
 const offset=state.distance*.23;
 for(let i=0;i<12;i++){
  const x=((i*95-offset*.5)%490+490)%490-40;
  fill(x,300+(i%4)*13,52,143,'#9bb6bd',6);
  fill(x+12,315+(i%4)*13,7,13,'#eaf1e6');
 }
 fill(0,442,W,108,'#486f5a');fill(0,440,W,5,'#9bcfac');
 for(let i=0;i<22;i++){const x=((i*41-state.distance)%W+W)%W;fill(x,463+(i%3)*21,25,2,'#6d9e7a')}
 for(const o of state.obstacles){
  if(o.kind==='flying'){disk(o.x+12,388,13,'#b26c76');fill(o.x,396,o.w,7,'#e1ab7f',3);}
  else if(o.kind==='pillar'){fill(o.x,442-o.h,o.w,o.h,'#915f65',5);fill(o.x+6,442-o.h+9,7,8,'#d19c94')}
  else{fill(o.x,442-o.h,o.w,o.h,'#a77f6f',10);fill(o.x+7,442-o.h+6,19,7,'#e1b7a1',4)}
 }
 const p=state.player,h=p.duck&&p.y>=442?24:48;
 fill(p.x-12,p.y-h,24,h,'#315c51',8);disk(p.x+1,p.y-h+9,5,'#ead2b1');fill(p.x-15,p.y-4,34,5,'#bd835e',3);
}
function initShooter(){return {score:0,wave:1,lives:3,ship:{x:195,y:488,shield:0,invincible:0},shots:[],enemies:[],enemyShots:[],pickups:[],spawn:.4,kills:0,clock:0,shoot:0};}
function tickShooter(dt){
 const q=state,ship=q.ship;q.clock+=dt;ship.invincible=Math.max(0,ship.invincible-dt);ship.shield=Math.max(0,ship.shield-dt);
 const dx=inputs.left?-270:inputs.right?270:inputs.drag?(inputs.lastX-ship.x)*10:0;
 ship.x=clamp(ship.x+clamp(dx,-360,360)*dt,23,W-23);
 q.shoot-=dt;if(q.shoot<=0){q.shots.push({x:ship.x,y:ship.y-15,vy:-590});q.shoot=.18;audio(q.clock%1<.18?'tap':'silence');}
 q.spawn-=dt;if(q.spawn<=0){
  const heavy=q.wave>2&&Math.random()<.21;
  const boss=q.kills>0&&q.kills%24===0;
  q.enemies.push({x:rand(28,W-28),y:-45,w:boss?70:heavy?42:30,h:boss?50:heavy?37:28,hp:boss?9:heavy?3:1,boss,
   vx:rand(-55,55),speed:rand(48,85)+q.wave*5,shot:rand(.8,2)});
  q.spawn=Math.max(.3,.87-q.wave*.04);
 }
 for(const bullet of q.shots)bullet.y+=bullet.vy*dt;
 for(const bullet of q.enemyShots)bullet.y+=bullet.vy*dt;
 for(const enemy of q.enemies){
  enemy.y+=enemy.speed*dt;enemy.x+=enemy.vx*dt;
  if(enemy.x<enemy.w/2||enemy.x>W-enemy.w/2)enemy.vx*=-1;
  enemy.shot-=dt;
  if(enemy.shot<=0&&enemy.y>20&&enemy.y<370){q.enemyShots.push({x:enemy.x,y:enemy.y+enemy.h/2,vy:185+q.wave*5});enemy.shot=rand(1.35,2.6);}
  if(enemy.y>H+25){enemy.hp=0;}
 }
 for(const bullet of q.shots)for(const enemy of q.enemies)if(enemy.hp>0&&Math.abs(bullet.x-enemy.x)<enemy.w/2+3&&Math.abs(bullet.y-enemy.y)<enemy.h/2+4){
  bullet.y=-999;enemy.hp--;if(enemy.hp<=0){q.kills++;q.score+=enemy.boss?175:enemy.w>35?40:20;audio('point');if(q.kills%12===0){q.wave++;}if(Math.random()<.085)q.pickups.push({x:enemy.x,y:enemy.y});}break;
 }
 for(const pickup of q.pickups){pickup.y+=90*dt;if(Math.abs(pickup.x-ship.x)<24&&Math.abs(pickup.y-ship.y)<23){pickup.y=999;ship.shield=7;audio('point')}}
 if(ship.invincible===0)for(const bullet of q.enemyShots)if(Math.abs(bullet.x-ship.x)<18&&Math.abs(bullet.y-ship.y)<17){
  bullet.y=999;if(ship.shield<=0){q.lives--;ship.invincible=1.25;audio('bad')}else ship.shield=0;break;
 }
 q.shots=q.shots.filter(b=>b.y>-10);q.enemyShots=q.enemyShots.filter(b=>b.y<H+10);
 q.enemies=q.enemies.filter(e=>e.hp>0&&e.y<H+25);q.pickups=q.pickups.filter(p=>p.y<H+8);
 if(q.lives<=0)end('Your ship was lost');
 updateHud();
}
function paintShooter(){
 bg('#0d2030','#25465a');
 for(let i=0;i<58;i++){const x=(i*71)%W,y=(i*127+Math.floor(state.clock*25))%H;disk(x,y,i%7===0?2:1,i%3===0?'#dfecfb':'#698ea7')}
 for(const enemy of state.enemies){ctx.save();ctx.translate(enemy.x,enemy.y);
  fill(-enemy.w/2,-enemy.h/2,enemy.w,enemy.h,enemy.boss?'#ce8376':'#a689a5',8);
  fill(-enemy.w/2-7,-4,enemy.w+14,8,'#c9a7ac',3);
  disk(-enemy.w*.18,0,3,'#e4d7a1');disk(enemy.w*.18,0,3,'#e4d7a1');ctx.restore();
 }
 for(const b of state.shots)fill(b.x-2,b.y-11,4,13,'#abf4c3',2);
 for(const b of state.enemyShots)disk(b.x,b.y,4,'#ec9e8b');
 for(const p of state.pickups){disk(p.x,p.y,11,'#73cf9e');text('+',p.x,p.y+5,15,'#fff',900)}
 const p=state.ship;ctx.save();ctx.globalAlpha=p.invincible>0?(.35+Math.sin(state.clock*25)*.25):1;
 ctx.translate(p.x,p.y);ctx.beginPath();ctx.moveTo(0,-22);ctx.lineTo(23,21);ctx.lineTo(0,11);ctx.lineTo(-23,21);ctx.closePath();ctx.fillStyle='#d2d9d5';ctx.fill();
 fill(-7,-4,14,10,'#659d9b',5);fill(-4,17,8,10,'#eda873',3);ctx.restore();
 if(p.shield>0){ctx.strokeStyle='#72eac7';ctx.lineWidth=3;ctx.beginPath();ctx.arc(p.x,p.y,29,0,TAU);ctx.stroke()}
 text('WAVE '+state.wave,15,26,13,'#bde2d4',800,'left');
}
function initBricks(){
 const grid=[];for(let row=0;row<6;row++)for(let col=0;col<7;col++)grid.push({x:15+col*52,y:66+row*27,w:46,h:21,hp:row<2?2:1});
 return {score:0,level:1,lives:3,paddle:195,ball:{x:195,y:479,vx:rand(-140,140),vy:-290},attached:true,bricks:grid};
}
function newBricksLevel(){
 const level=state.level;state.bricks=[];
 for(let row=0;row<Math.min(8,5+Math.floor(level/2));row++)for(let col=0;col<7;col++)state.bricks.push({x:15+col*52,y:66+row*27,w:46,h:21,hp:row<(level%3+1)?2:1});
 state.ball={x:state.paddle,y:479,vx:rand(-170,170),vy:-310};state.attached=true;
}
function launchBall(){
 if(state?.attached&&started){state.attached=false;state.ball.vx=rand(-170,170);state.ball.vy=-310;audio('hit')}
}
function tickBricks(dt){
 const q=state,b=q.ball;
 if(inputs.left)q.paddle-=325*dt;if(inputs.right)q.paddle+=325*dt;
 if(inputs.drag)q.paddle=inputs.lastX;q.paddle=clamp(q.paddle,47,W-47);
 if(q.attached){b.x=q.paddle;return}
 let oldX=b.x,oldY=b.y;b.x+=b.vx*dt;b.y+=b.vy*dt;
 if(b.x<10){b.x=10;b.vx=Math.abs(b.vx)}
 if(b.x>W-10){b.x=W-10;b.vx=-Math.abs(b.vx)}
 if(b.y<10){b.y=10;b.vy=Math.abs(b.vy)}
 if(b.vy>0&&oldY+9<=492&&b.y+9>=490&&Math.abs(b.x-q.paddle)<54){
  b.y=480;b.vy=-Math.max(270,Math.abs(b.vy));b.vx=clamp((b.x-q.paddle)*4.9,-310,310);audio('hit');
 }
 for(const brick of q.bricks)if(brick.hp>0&&b.x+8>brick.x&&b.x-8<brick.x+brick.w&&b.y+8>brick.y&&b.y-8<brick.y+brick.h){
  brick.hp--;q.score+=brick.hp===0?15:4;audio('point');
  const oldHoriz=oldX<brick.x||oldX>brick.x+brick.w;
  if(oldHoriz)b.vx*=-1;else b.vy*=-1;
  break;
 }
 q.bricks=q.bricks.filter(brick=>brick.hp>0);
 if(!q.bricks.length){q.level++;newBricksLevel();audio('point')}
 if(b.y>H+12){q.lives--;if(q.lives<=0)end('Out of balls');else{q.ball={x:q.paddle,y:479,vx:rand(-130,130),vy:-300};q.attached=true;audio('bad')}}
 updateHud();
}
function paintBricks(){
 bg('#e6ece1','#d0e4d9');fill(0,520,W,30,'#b6d1c2');
 for(let i=0;i<state.bricks.length;i++){const b=state.bricks[i];
  const colors=['#de947b','#e0bb74','#78ab91','#8da4ca','#ac99b9','#69aab1'];const row=Math.round((b.y-66)/27);
  fill(b.x,b.y,b.w,b.h,colors[row%colors.length],5);
  if(b.hp>1)fill(b.x+3,b.y+4,b.w-6,3,'#ffffffaa',2)
 }
 const p=state.paddle;fill(p-48,490,96,13,'#244f3c',7);fill(p-27,492,54,4,'#9fc9af',2);
 const ball=state.ball;disk(ball.x,ball.y,9,'#fff');disk(ball.x-2,ball.y-2,4,'#b0bec8');
 text('LEVEL '+state.level,15,28,13,'#54785c',800,'left');
 if(state.attached&&started)text('TAP TO LAUNCH',W/2,353,17,'#4a7960',850);
}
const INIT={slice:initSlice,jump:initJump,runner:initRunner,shooter:initShooter,bricks:initBricks};
const TICK={slice:tickSlice,jump:tickJump,runner:tickRunner,shooter:tickShooter,bricks:tickBricks};
const PAINT={slice:paintSlice,jump:paintJump,runner:paintRunner,shooter:paintShooter,bricks:paintBricks};
function render(){
 ctx.save();ctx.clearRect(0,0,W,H);PAINT[game]();ctx.restore();
}
function tick(timestamp){
 const dt=Math.min(.033,Math.max(0,(timestamp-(last||timestamp))/1000));last=timestamp;
 if(started&&!paused&&!over){TICK[game](dt);frame++;}
 render();raf=requestAnimationFrame(tick);
}
function reset(){
 state=INIT[game]();over=false;paused=false;started=false;frame=0;
 $('message').textContent='';$('pauseButton').setAttribute('aria-label','Pause game');$('pauseButton').querySelector('use').setAttribute('href','#icon-pause');
 inputs.left=inputs.right=inputs.drag=inputs.duck=false;
 updateHud();overlay('READY TO PLAY',defs[game].title,defs[game].rules,'Start game');
 render();
}
function start(){
 if(over){reset()}
 started=true;paused=false;hideOverlay();$('message').textContent='';
 if(game==='runner')state.spawn=1.5;
 if(game==='slice')state.spawn=.5;
 audio('tap');
}
function pause(){
 if(over||!started)return;
 paused=!paused;
 const btn=$('pauseButton');btn.setAttribute('aria-label',paused?'Resume game':'Pause game');btn.querySelector('use').setAttribute('href',paused?'#icon-play':'#icon-pause');
 if(paused)overlay('PAUSED',defs[game].title,'Your round is waiting.','Resume');
 else hideOverlay();
}
function selectGame(type){
 if(!defs[type])type='slice';game=type;
 const url=new URL(location.href);url.searchParams.set('game',game);history.replaceState({},'',url.pathname+url.search);
 const def=defs[game];$('title').textContent=def.title;$('subtitle').textContent=def.subtitle;
 $('rules').textContent=def.rules;document.title=def.title+' · NIGHT Play';
 for(const el of $('gamePicks').querySelectorAll('[data-game]'))el.setAttribute('aria-current',String(el.dataset.game===game));
 $('touchControls').replaceChildren();$('touchControls').hidden=true;
 if(game==='jump'||game==='runner'){
  $('touchControls').hidden=false;
  const buttons=game==='jump'?[['left','◀'],['right','▶']]:[['jump','JUMP'],['slide','SLIDE']];
  for(const [command,label] of buttons){const btn=document.createElement('button');btn.type='button';btn.dataset.control=command;btn.textContent=label;btn.setAttribute('aria-label',command);$('touchControls').append(btn)}
 }
 reset();if($('settingsDialog').open)$('settingsDialog').close();
}
function point(e){const r=canvas.getBoundingClientRect();return {x:clamp((e.clientX-r.left)/r.width*W,0,W),y:clamp((e.clientY-r.top)/r.height*H,0,H)}}
function down(e){
 if(e.button!==undefined&&e.button!==0)return;
 const p=point(e);inputs.drag=true;inputs.lastX=p.x;inputs.lastY=p.y;inputs.dragStartY=p.y;
 if(started&&!over&&!paused){if(game==='runner')jumpRunner();if(game==='bricks')launchBall();if(game==='slice')state.trail.push({x:p.x,y:p.y,a:1})}
 try{canvas.setPointerCapture(e.pointerId)}catch{}
}
function move(e){
 if(!inputs.drag)return;
 const p=point(e);if(game==='slice')sliceMove(inputs.lastX,inputs.lastY,p.x,p.y);
 if(game==='runner'&&p.y-inputs.dragStartY>28){duckRunner(true);state.duckTimer=.45}
 inputs.lastX=p.x;inputs.lastY=p.y;
}
function up(e){inputs.drag=false;if(game==='runner'){duckRunner(false)}try{canvas.releasePointerCapture(e.pointerId)}catch{}}
canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',up);
$('touchControls').addEventListener('pointerdown',e=>{
 const b=e.target.closest('[data-control]');if(!b)return;e.preventDefault();
 const id=b.dataset.control;if(id==='left'||id==='right')inputs[id]=true;
 if(id==='jump')jumpRunner();if(id==='slide')duckRunner(true);
 try{b.setPointerCapture(e.pointerId)}catch{}
});
$('touchControls').addEventListener('pointerup',e=>{const b=e.target.closest('[data-control]');if(!b)return;const id=b.dataset.control;if(id==='left'||id==='right')inputs[id]=false;if(id==='slide')duckRunner(false)});
$('touchControls').addEventListener('pointercancel',()=>{inputs.left=inputs.right=false;if(game==='runner')duckRunner(false)});
window.addEventListener('keydown',e=>{
 if($('settingsDialog').open)return;
 const k=e.key.toLowerCase();
 if(['arrowleft','a','arrowright','d','arrowup',' ','arrowdown','s','p'].includes(k))e.preventDefault();
 if(k==='arrowleft'||k==='a')inputs.left=true;
 if(k==='arrowright'||k==='d')inputs.right=true;
 if(k==='arrowup'||k===' '){if(started&&game==='runner')jumpRunner();if(started&&game==='bricks')launchBall();}
 if(k==='arrowdown'||k==='s'){if(game==='runner')duckRunner(true)}
 if(k==='p')pause();
});
window.addEventListener('keyup',e=>{const k=e.key.toLowerCase();if(k==='arrowleft'||k==='a')inputs.left=false;if(k==='arrowright'||k==='d')inputs.right=false;if(k==='arrowdown'||k==='s')duckRunner(false)});
$('overlayStart').addEventListener('click',()=>{if(paused){pause();return}start()});
$('pauseButton').addEventListener('click',pause);
$('restart').addEventListener('click',reset);
$('settingsButton').addEventListener('click',()=>{$('settingsDialog').showModal();if(started&&!paused)pause()});
$('settingsClose').addEventListener('click',()=>$('settingsDialog').close());
$('settingsDialog').addEventListener('click',e=>{if(e.target===$('settingsDialog'))$('settingsDialog').close()});
$('gamePicks').addEventListener('click',e=>{const b=e.target.closest('[data-game]');if(b)selectGame(b.dataset.game)});
$('soundToggle').addEventListener('click',()=>{sfx=!sfx;$('soundToggle').setAttribute('aria-pressed',String(sfx));$('soundToggle').textContent='Sound effects · '+(sfx?'On':'Off');try{localStorage.setItem(soundKey,sfx?'on':'off')}catch{}});
$('soundToggle').setAttribute('aria-pressed',String(sfx));$('soundToggle').textContent='Sound effects · '+(sfx?'On':'Off');
document.addEventListener('visibilitychange',()=>{if(document.hidden&&started&&!paused)pause()});
window.__nightActionTest={snapshot:()=>JSON.parse(JSON.stringify({game,started,paused,over,frame,...state})),selectGame,reset,start};
selectGame(new URLSearchParams(location.search).get('game')||'slice');
raf=requestAnimationFrame(tick);
})();