/* NIGHT Play original Action Arcade — five complete canvas games.
 * Runner physics and obstacle categories adapted from the MIT-licensed
 * gamelabz/html5-game-endless-runner (2026); see THIRD_PARTY_NOTICES.md.
 * All rendering, responsive control layout and other four modes are new.
 */
(()=>{'use strict';
const $=id=>document.getElementById(id),canvas=$('screen'),ctx=canvas.getContext('2d',{alpha:false});
const W=390,H=550,PI=Math.PI,TAU=PI*2;
// Render at real device resolution instead of blurring a 390px canvas on
// high-density phones. All gameplay coordinates remain in logical pixels.
const dpr=Math.min(2.5,Math.max(1,window.devicePixelRatio||1));
canvas.width=Math.round(W*dpr);canvas.height=Math.round(H*dpr);
ctx.setTransform(dpr,0,0,dpr,0,0);
const defs={
 slice:{title:'Fruit Slice',subtitle:'Slice the fruit. Never touch the bombs.',rules:'Drag a finger across fruit to slice it. Avoid black bombs and do not miss five pieces of fruit. Mouse dragging works on desktop.',label:'LIVES'},
 jump:{title:'Sky Jumper',subtitle:'Climb higher. Land every jump.',rules:'Guide the jumper left and right with the controls, arrow keys, A/D or by dragging horizontally across the screen. Jumping is automatic. Fall below the screen and the run ends.',label:'HEIGHT'},
 runner:{title:'Endless Runner',subtitle:'Jump, slide and survive the run.',rules:'Tap or press Space to jump. Swipe down or hold Slide to duck underneath flying obstacles. On desktop, use Up/Space to jump and Down to duck. The pace gets faster over time.',label:'SPEED'},
 shooter:{title:'Space Shooter',subtitle:'Defend the skies against endless waves.',rules:'Drag your ship left and right; it fires automatically. Dodge enemy shots, collect green shields and survive increasingly difficult waves. Use arrows or A/D on desktop.',label:'HULL'},
 bricks:{title:'Brick Breaker',subtitle:'Break them all. Control the bounce.',rules:'Drag the paddle or use left/right arrows to keep the ball in play. Tap the game board to launch the ball. Clear every brick to reach the next level.',label:'LIVES'}
};
const soundKey='night-play-action-sfx-v1',bestKey=id=>'night-play-v1-action-'+id;
const clamp=(n,a,b)=>Math.min(b,Math.max(a,n)),rand=(a,b)=>a+Math.random()*(b-a);
let game='slice',state,started=false,over=false,paused=false,frame=0,raf=0,last=0,sfx=true,ctxAudio=null;
try{sfx=localStorage.getItem(soundKey)!=='off'}catch{}
const inputs={left:false,right:false,duck:false,drag:false,lastX:0,lastY:0,slicePointerId:null};
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
 $('stat').parentElement.classList.toggle('action-stat-hidden',game==='jump');
 $('score').textContent=String(Math.floor(state?.score||0));
 $('best').textContent=String(Math.floor(Math.max(readBest(),state?.score||0)));
 $('statLabel').textContent=defs[game].label;
 let value=0;
 if(state){if(game==='slice')value=Math.max(0,5-state.misses);
 if(game==='jump')value=Math.floor(state.score);
 if(game==='runner')value=(state.speed/220).toFixed(1)+'×';
 if(game==='shooter')value=state.lives;
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
 updateHud();overlay('GAME OVER',text,'Score '+Math.floor(state.score)+' · Best '+Math.floor(readBest()),'Play again');audio('bad');
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

// The launch arc is solved from a target apex, rather than increasing velocity
// blindly. Nearly every throw crosses the centre and upper-middle touch area.
const SLICE_GRAVITY=980;
function initSlice(){return {score:0,misses:0,spawn:.55,fruits:[],halves:[],splashes:[],trail:[],popups:[],clock:0,combo:0,comboClock:0};}
function addFruit(){
 const level=Math.min(8,Math.floor(state.score/100));
 const count=Math.random()<Math.min(.59,.26+level*.045)?(level>=3&&Math.random()<.22?3:2):1;
 const bombChance=Math.min(.19,.075+level*.014);
 for(let i=0;i<count;i++){
  const kind=Math.random()<bombChance?'bomb':Math.floor(rand(0,5));
  // Distinct launch lanes keep two/three fruit visible as separate targets.
  // Independent random X used to stack fruit ON bombs, creating unavoidable
  // deaths and invisible multi-fruit combos on high-density phone screens.
  const lanes=count===2?[.28,.72]:count===3?[.22,.5,.78]:null;
  const x=lanes?W*lanes[i]+rand(-17,17):rand(65,W-65);
  const y=H+24+rand(0,26),apex=rand(137,233);
  const rise=Math.sqrt(2*SLICE_GRAVITY*(y-apex)),timeToApex=rise/SLICE_GRAVITY;
  // Prevent the apex from being outside the screen, even for edge launches.
  const vx=clamp(rand(-65,65)+(W*.5-x)*.16,
   (51-x)/timeToApex,(W-51-x)/timeToApex);
  state.fruits.push({x,y,vx,vy:-rise,r:kind==='bomb'?24:rand(24,28),kind,
   rotation:rand(-.32,.32),spin:rand(-1.6,1.6),hit:false,apex});
 }
}
// A swipe must actually travel THROUGH a fruit. Merely touching its edge
// or tapping/hovering at one coordinate cannot award points.
function bladeCutsFruit(f,x1,y1,x2,y2){
 const dx=x2-x1,dy=y2-y1,len2=dx*dx+dy*dy;
 if(len2<14*14)return false;
 const fx=x1-f.x,fy=y1-f.y,r=f.r*.87;
 const b=2*(fx*dx+fy*dy),c=fx*fx+fy*fy-r*r;
 const disc=b*b-4*len2*c;
 if(disc<=0)return false;
 const root=Math.sqrt(disc),enter=Math.max(0,(-b-root)/(2*len2));
 const exit=Math.min(1,(-b+root)/(2*len2));
 return (exit-enter)*Math.sqrt(len2)>=Math.max(10,f.r*.4);
}
function sliceMove(x1,y1,x2,y2){
 if(!started||paused||over)return;
 state.trail.push({x:x2,y:y2,a:1});
 if(state.trail.length>28)state.trail.splice(0,state.trail.length-28);
 const dx=x2-x1,dy=y2-y1,length=Math.hypot(dx,dy);
 if(length<14)return;
 const nx=-dy/length,ny=dx/length;
 // Resolve the whole swept segment before scoring: a bomb and fruit can
 // overlap visually, but a lethal stroke must NEVER award incidental points
 // merely because the fruit happened to be earlier in the array.
 const crossed=state.fruits.filter(f=>!f.hit&&bladeCutsFruit(f,x1,y1,x2,y2));
 const bomb=crossed.find(f=>f.kind==='bomb');
 if(bomb){bomb.hit=true;end('Bomb hit!');return;}
 const previousCombo=state.comboClock>0?state.combo:0;
 let sliced=0;
 for(const f of crossed){
  f.hit=true;sliced++;
  const points=10+5*Math.min(4,previousCombo+sliced-1);
  const angle=Math.atan2(dy,dx),juice=['#ffbf50','#e94750','#86ca64','#b0e38d','#f64e51'][f.kind];
  // Each clipped piece keeps its own original cut edge as it rotates.
  for(const side of [-1,1])state.halves.push({x:f.x+nx*side*7,y:f.y+ny*side*7,
   vx:f.vx+nx*side*170,vy:f.vy+ny*side*140-25,r:f.r,kind:f.kind,
   angle,cutRotation:f.rotation,rotation:f.rotation,spin:side*rand(2.7,4.5),
   side,age:0});
  for(let i=0;i<20;i++){
   const a=rand(0,TAU),speed=rand(50,270);
   state.splashes.push({x:f.x,y:f.y,vx:Math.cos(a)*speed,vy:Math.sin(a)*speed-45,
    age:0,life:rand(.29,.63),r:rand(1.5,4),color:juice});
  }
  state.popups.push({x:f.x,y:f.y-34,age:0,points});
  state.score+=points;audio('point');
 }
 if(sliced){
  state.combo=previousCombo+sliced;state.comboClock=.52;
  // Reward consecutive true cuts with one readable, short-lived callout.
  if(state.combo>=2)state.popups.push({x:W*.5,y:78,age:0,label:state.combo+'× COMBO'});
  updateHud();
 }
}

const fruitColors=['#fcae39','#e84456','#66a34e','#89b05e','#ee4e4e'];
function fruitShape(kind,r){
 ctx.beginPath();
 if(kind===1){ // strawberry: broad shoulders and a natural pointed tip
  ctx.moveTo(0,-r*.75);
  ctx.bezierCurveTo(-r*.9,-r*1.2,-r*1.25,-r*.25,-r*.72,r*.42);
  ctx.quadraticCurveTo(-r*.36,r*.94,0,r*1.13);
  ctx.quadraticCurveTo(r*.39,r*.94,r*.73,r*.4);
  ctx.bezierCurveTo(r*1.27,-r*.25,r*.95,-r*1.2,0,-r*.75);
 }else if(kind===4){
  ctx.moveTo(0,-r*.8);ctx.bezierCurveTo(-r*.6,-r*1.33,-r*1.18,-r*.72,-r,r*.03);
  ctx.bezierCurveTo(-r*1.05,r*.85,-r*.41,r*1.11,0,r*.9);
  ctx.bezierCurveTo(r*.41,r*1.11,r*1.05,r*.85,r,r*.03);
  ctx.bezierCurveTo(r*1.18,-r*.72,r*.6,-r*1.33,0,-r*.8);
 }else ctx.ellipse(0,0,r,r*(kind===2?.96:1),0,0,TAU);
 ctx.closePath();
}
function fruitFlesh(kind,r){
 const color=fruitColors[kind],light=['#ffe391','#ffa3ac','#c9e9a2','#c8e878','#ffb59c'][kind];
 const gradient=ctx.createRadialGradient(-r*.4,-r*.48,r*.08,r*.12,r*.1,r*1.65);
 gradient.addColorStop(0,light);gradient.addColorStop(.4,color);
 gradient.addColorStop(1,['#bd511b','#9d1934','#386e2b','#3d7030','#ac222c'][kind]);
 fruitShape(kind,r);ctx.fillStyle=gradient;ctx.fill();
 ctx.save();fruitShape(kind,r);ctx.clip();
 if(kind===0){ // orange pores + light segment arcs
  ctx.strokeStyle='#ffde8c83';ctx.lineWidth=1.3;
  for(let i=0;i<7;i++){const a=i*TAU/7;
   ctx.beginPath();ctx.moveTo(Math.cos(a)*r*.24,Math.sin(a)*r*.24);
   ctx.quadraticCurveTo(Math.cos(a+.26)*r*.72,Math.sin(a+.26)*r*.72,Math.cos(a)*r*.82,Math.sin(a)*r*.82);ctx.stroke();
  }
  for(let i=0;i<18;i++)disk(Math.sin(i*11)*r*.8,Math.cos(i*19)*r*.75,.65,'#fff1b080');
 }else if(kind===1){
  for(let i=0;i<24;i++){const x=Math.sin(i*19)*r*.72,y=Math.cos(i*11)*r*.78;
    ctx.save();ctx.translate(x,y);ctx.rotate(.3);ctx.fillStyle='#ffe9a6';
    ctx.beginPath();ctx.ellipse(0,0,1.4,2.4,0,0,TAU);ctx.fill();ctx.restore();}
 }else if(kind===2){
  ctx.strokeStyle='#32652a78';ctx.lineWidth=3;
  for(let i=0;i<8;i++){const a=i*TAU/8;ctx.beginPath();ctx.moveTo(Math.cos(a)*r*.25,Math.sin(a)*r*.25);
    ctx.lineTo(Math.cos(a+.22)*r*.88,Math.sin(a+.22)*r*.88);ctx.stroke();}
 }else if(kind===3){ // green striped melon skin
  ctx.strokeStyle='#3369419c';ctx.lineWidth=4;
  for(let i=-2;i<=2;i++){ctx.beginPath();ctx.moveTo(i*r*.36,-r);
   ctx.bezierCurveTo(i*r*.43-r*.2,-r*.38,i*r*.48+r*.21,r*.42,i*r*.38,r);ctx.stroke();}
 }else{
  ctx.strokeStyle='#fbc39a59';ctx.lineWidth=2;
  ctx.beginPath();ctx.moveTo(-r*.49,-r*.58);ctx.quadraticCurveTo(-r*.65,-r*.1,-r*.43,r*.4);ctx.stroke();
 }
 const sheen=ctx.createLinearGradient(-r,-r,r,r);
 sheen.addColorStop(0,'#ffffff83');sheen.addColorStop(.28,'#ffffff08');sheen.addColorStop(.75,'#ffffff00');
 ctx.fillStyle=sheen;fruitShape(kind,r);ctx.fill();
 ctx.restore();
 ctx.strokeStyle=kind===2?'#4b6739':kind===3?'#346e44':'#ffffff30';
 ctx.lineWidth=kind===3?2.3:1.4;fruitShape(kind,r);ctx.stroke();
 if(kind!==3&&kind!==2){
  ctx.strokeStyle='#547d3a';ctx.lineWidth=3;ctx.lineCap='round';
  ctx.beginPath();ctx.moveTo(0,-r*.8);ctx.quadraticCurveTo(1,-r*1.1,4,-r*1.19);ctx.stroke();
  ctx.fillStyle='#458f4e';ctx.beginPath();ctx.ellipse(r*.21,-r*1.1,r*.31,r*.13,-.42,0,TAU);ctx.fill();
 }
}
function drawFruit(f){
 ctx.save();ctx.translate(f.x,f.y);ctx.rotate(f.rotation);
 if(f.kind==='bomb'){
  ctx.shadowColor='#101a2088';ctx.shadowBlur=8;
  const g=ctx.createRadialGradient(-9,-10,1,0,0,f.r);
  g.addColorStop(0,'#667078');g.addColorStop(1,'#1c2731');
  disk(0,0,f.r,g);ctx.shadowBlur=0;
  fill(-4,-f.r-7,8,12,'#b9ad8b',3);disk(-7,-8,4,'#ffffff70');text('!',0,8,22,'#f2d3c0',900);
 }else{
  ctx.shadowColor='#1d39264a';ctx.shadowBlur=7;fruitFlesh(f.kind,f.r);
 }
 ctx.restore();
}
function drawHalf(h){
 const r=h.r,R=r*1.65,a=h.angle-h.cutRotation,tx=Math.cos(a),ty=Math.sin(a),nx=-ty,ny=tx;
 ctx.save();ctx.translate(h.x,h.y);ctx.rotate(h.rotation);
 ctx.globalAlpha=Math.min(1,(1.25-h.age)*2.8);
 // Clip each independent physical piece against the plane of the actual swipe.
 ctx.beginPath();ctx.moveTo(-tx*R,-ty*R);ctx.lineTo(tx*R,ty*R);
 ctx.lineTo(tx*R+nx*R*h.side,ty*R+ny*R*h.side);
 ctx.lineTo(-tx*R+nx*R*h.side,-ty*R+ny*R*h.side);
 ctx.closePath();ctx.clip();
 fruitFlesh(h.kind,r);
 // Exposed cut surface moves with each half instead of leaving a slash over an intact fruit.
 ctx.strokeStyle=['#ffe7a8','#ffc0b9','#e6f6a3','#ff7d81','#ffe1c0'][h.kind];
 ctx.lineWidth=5;ctx.beginPath();ctx.moveTo(-tx*r,-ty*r);ctx.lineTo(tx*r,ty*r);ctx.stroke();
 ctx.restore();
}
function tickSlice(dt){
 state.clock+=dt;state.spawn-=dt;
 if(state.spawn<0){addFruit();state.spawn=rand(.72,1.02)*Math.max(.54,1-state.score/1100);}
 for(const f of state.fruits){
  f.x+=f.vx*dt;f.y+=f.vy*dt;f.vy+=SLICE_GRAVITY*dt;f.rotation+=f.spin*dt;
  if(f.vy>0&&f.y>H+f.r+4&&!f.hit&&f.kind!=='bomb'){f.hit=true;state.misses++;audio('bad')}
 }
 state.fruits=state.fruits.filter(f=>!f.hit&&f.y<H+95);
 for(const h of state.halves){h.x+=h.vx*dt;h.y+=h.vy*dt;h.vy+=SLICE_GRAVITY*dt;h.rotation+=h.spin*dt;h.age+=dt}
 state.halves=state.halves.filter(h=>h.age<1.25&&h.y<H+95);
 for(const p of state.splashes){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=560*dt;p.age+=dt}
 state.splashes=state.splashes.filter(p=>p.age<p.life&&p.y<H+20);
 state.trail=state.trail.filter(p=>(p.a-=dt*4.7)>0);
 state.comboClock=Math.max(0,state.comboClock-dt);
 for(const p of state.popups){p.y-=dt*42;p.age+=dt}
 state.popups=state.popups.filter(p=>p.age<.65);
 if(state.misses>=5)end('Too many missed fruit');
}
function paintSlice(){
 bg('#f0f4e5','#a4d1b8');
 fill(0,H-37,W,37,'#638e75');fill(0,H-38,W,2,'#92bea0');
 for(const f of state.fruits)drawFruit(f);
 for(const h of state.halves)drawHalf(h);
 for(const p of state.splashes){ctx.globalAlpha=1-p.age/p.life;disk(p.x,p.y,p.r,p.color);ctx.globalAlpha=1}
 if(state.trail.length>1){
  ctx.save();ctx.lineCap='round';ctx.lineJoin='round';
  for(let i=1;i<state.trail.length;i++){
   const a=state.trail[i-1],b=state.trail[i],alpha=Math.min(a.a,b.a);
   if(Math.hypot(a.x-b.x,a.y-b.y)>130)continue;
   ctx.strokeStyle='rgba(255,255,255,'+(alpha*.24)+')';ctx.lineWidth=12;
   ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();
   ctx.strokeStyle='rgba(255,249,206,'+(alpha*.87)+')';ctx.lineWidth=4.6;
   ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();
   ctx.strokeStyle='rgba(255,255,255,'+alpha+')';ctx.lineWidth=1.5;
   ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke();
  }
  ctx.restore();
 }
 for(const p of state.popups){
  ctx.save();ctx.globalAlpha=Math.max(0,1-p.age/.65);
  // High-contrast forest ink stays readable against the pale playing field.
  text(p.label||'+'+p.points,p.x,p.y,p.label?17:19,'#244b38',850);ctx.restore();
 }
 for(let i=0;i<5;i++)disk(30+i*21,30,7,i<5-state.misses?'#d87761':'#d7dcd3');
}

/* SKY JUMPER: permanent landing platforms, automatic bouncing, and
 * camera-based culling only after a platform moves off screen. Platforms
 * never break or disappear on contact; difficulty is in aiming each jump.
 * 560px/s jump, 1230px/s² gravity (~127 logical pixels of ascent).
 */
function jumpPlatform(x,y,w,moving=false){
 // Keep the entire oscillation inside the playable board. Clamping each
 // frame would pin a moving platform against an edge for long intervals.
 const range=rand(19,35);
 const center=moving?clamp(x,8+range,W-w-8-range):x;
 return {x:center,y,w,moving,baseX:center,phase:rand(0,TAU),
   range,pace:rand(.8,1.45)};
}
function initJump(){
 const platforms=[jumpPlatform(120,505,133)];
 let last=platforms[0];
 for(let y=428,i=0;y>=-90;y-=rand(65,78),i++){
   const w=i<4?rand(84,106):rand(69,96);
   // Keep a reachable stepping route rather than independent random Xs.
   const earlyStep=i<4?88:112;
   const x=clamp(last.x+rand(-earlyStep,earlyStep),12,W-w-12);
   // Ease in with four permanent wide platforms before moving targets.
   const plat=jumpPlatform(x,y,w,i===4||(i>4&&Math.random()<.19));
   platforms.push(plat);last=plat;
 }
 return {score:0,elapsed:0,bounce:0,player:{x:180,y:460,vy:-575,vx:0},
   platforms,scroll:0};
}
function tickJump(dt){
 const p=state.player,prev=p.y;
 state.elapsed+=dt;state.bounce=Math.max(0,state.bounce-dt);
 const desired=inputs.left?-285:inputs.right?285:
   inputs.drag?(inputs.lastX-p.x)*10:0;
 p.vx+=(clamp(desired,-310,310)-p.vx)*Math.min(1,dt*14);
 p.x+=p.vx*dt;
 if(p.x>W+18)p.x=-17;if(p.x< -18)p.x=W+17;
 p.vy+=1230*dt;p.y+=p.vy*dt;
 // A moving platform's actual collision area follows its rendered position.
 for(const plat of state.platforms)if(plat.moving){
   plat.x=clamp(plat.baseX+
     Math.sin(state.elapsed*plat.pace+plat.phase)*plat.range,8,W-plat.w-8);
 }
 if(p.vy>0)for(const plat of state.platforms){
   if(!(prev+17<=plat.y+4&&p.y+17>=plat.y&&
       p.x+15>plat.x&&p.x-15<plat.x+plat.w))continue;
   p.y=plat.y-18;p.vy=-575;state.bounce=.12;
   // Platforms always remain solid, no matter how many landings.
   audio('hit');break;
 }
 if(p.y<185){
   const shift=185-p.y;p.y=185;state.scroll+=shift;
   state.score=Math.max(state.score,Math.floor(state.scroll));
   for(const plat of state.platforms)plat.y+=shift;
 }
 // Scrolling out of sight is the only reason to cull a platform.
 state.platforms=state.platforms.filter(plat=>plat.y<H+30);
 const difficulty=Math.min(1,state.score/4000);
 while(state.platforms.length<11){
   const last=state.platforms.reduce((top,plat)=>plat.y<top.y?plat:top);
   const gap=rand(67+5*difficulty,81+9*difficulty);
   const w=rand(70-12*difficulty,92-11*difficulty);
   const x=clamp(last.baseX+rand(-110-18*difficulty,110+18*difficulty),12,W-w-12);
   const moving=Math.random()<.16+.20*difficulty;
   state.platforms.push(jumpPlatform(x,last.y-gap,w,moving));
 }
 if(p.y>H+35)end('You fell!');
 updateHud();
}
function paintJump(){
 bg('#bedcef','#f2f0d8');
 // Subtle distant cloud layers: intentionally soft, not repeated white blobs.
 for(let i=0;i<8;i++){
   const x=(i*127+46)%W,y=((i*121+30)+state.scroll*.12)%(H+110)-65;
   ctx.fillStyle='#ffffff76';ctx.beginPath();
   ctx.ellipse(x,y,30,13,0,0,TAU);ctx.fill();
   ctx.beginPath();ctx.ellipse(x+11,y-8,17,13,0,0,TAU);ctx.fill();
 }
 ctx.fillStyle='#789c9e24';ctx.beginPath();ctx.moveTo(0,H);
 for(let x=0;x<=W;x+=12)ctx.lineTo(x,H-52-15*Math.sin(x*.03+state.scroll*.0005));
 ctx.lineTo(W,H);ctx.closePath();ctx.fill();
 for(const plat of state.platforms){
   ctx.save();
   if(plat.moving){ctx.shadowColor='#6472a244';ctx.shadowBlur=8}
   else {ctx.shadowColor='#427a5248';ctx.shadowBlur=6}
   fill(plat.x,plat.y+4,plat.w,11,'#244e42a8',6);
   fill(plat.x,plat.y,plat.w,12,
     plat.moving?'#7989d0':'#4b9f73',6);
   ctx.shadowBlur=0;
   fill(plat.x+5,plat.y+2,plat.w-10,2,'#ffffff75',1);
   if(plat.moving){disk(plat.x+9,plat.y+6,2,'#e3e9ff');disk(plat.x+plat.w-9,plat.y+6,2,'#e3e9ff')}
   ctx.restore();
 }
 const p=state.player;
 ctx.save();ctx.translate(p.x,p.y);ctx.rotate(clamp(p.vx/1400,-.18,.18));
 // Two-direction scarf tail makes steering visible at a glance.
 ctx.fillStyle='#f0c253';ctx.beginPath();ctx.moveTo(-9,-15);
 ctx.lineTo(-9-clamp(p.vx/17,-15,15),-8);
 ctx.lineTo(-11-clamp(p.vx/22,-12,12),-18);ctx.closePath();ctx.fill();
 fill(-14,-19,28,29,'#f2a47c',9);
 fill(-17,-24,34,12,'#315f56',5);
 fill(-12,-28,24,5,'#467f6a',3);
 fill(-17,-11,5,17,'#d78165',3);
 fill(12,-11,5,17,'#d78165',3);
 disk(-6,-10,2.4,'#20362e');disk(6,-10,2.4,'#20362e');
 fill(-8,11,11,9,'#315c55',3);fill(3,11,11,9,'#315c55',3);
 ctx.restore();
 if(state.bounce>0){
   ctx.fillStyle='#ffffff80';ctx.beginPath();
   ctx.ellipse(p.x,p.y+23,18*(1-state.bounce/.15),3,0,0,TAU);ctx.fill();
 }
}

function initRunner(){return {score:0,speed:220,player:{x:88,y:442,vy:0,duck:false},obstacles:[],spawn:1.25,distance:0,passed:0,duckTimer:0,clock:0,nearMisses:0};}
function jumpRunner(){
 const p=state.player;if(!started||paused||over)return;
 if(p.y>=441){p.vy=-685;p.y=440;audio('hit')}
}
function duckRunner(on){if(!state||game!=='runner')return;inputs.duck=on;state.player.duck=on||state.duckTimer>0;}
function runnerBounds(o){
 // Art and collision are attached to identical physical heights.
 if(o.kind==='flying')return {top:396,bottom:438};
 if(o.kind==='glider')return {top:347,bottom:403};
 return {top:442-o.h,bottom:442};
}
function tickRunner(dt){
 const p=state.player;
 state.clock+=dt;
 state.duckTimer=Math.max(0,state.duckTimer-dt);
 p.duck=inputs.duck||state.duckTimer>0;
 p.vy+=1690*dt;p.y=Math.min(442,p.y+p.vy*dt);if(p.y>=442)p.vy=0;
 state.distance+=state.speed*dt;state.score=Math.floor(state.distance/12);
 state.speed=Math.min(530,state.speed+7*dt);
 state.spawn-=dt;
 if(state.spawn<=0){
  const roll=Math.random(),kind=roll<.36?'flying':roll<.52?'glider':roll<.74?'pillar':roll<.91?'stone':'barrel';
  const o={x:W+25,w:kind==='flying'?48:kind==='glider'?51:kind==='pillar'?29:kind==='barrel'?36:43,
   h:kind==='pillar'?54:kind==='stone'?28:kind==='barrel'?39:kind==='flying'?48:58,kind};
  state.obstacles.push(o);
  state.spawn=rand(1.12,1.48)*(250/state.speed)**.23;
 }
 const standingHeight=p.duck&&p.y>=441?24:48;
 const playerLeft=p.x-12,playerRight=p.x+12,playerTop=p.y-standingHeight+3,playerBottom=p.y-3;
 for(const o of state.obstacles){
  const prevX=o.x;o.x-=state.speed*dt;
  const bounds=runnerBounds(o);
  // No phantom safe flying hazards: standing and sliding into a low flyer
  // both collide. High gliders require ducking; obstacles above a jump count.
  if(prevX+o.w-5>playerLeft&&o.x+5<playerRight&&
     playerTop<bounds.bottom-3&&playerBottom>bounds.top+3){
   end('An obstacle caught you');break;
  }
  if(o.x+o.w<playerLeft&&!o.passed){o.passed=true;state.passed++;
    if((p.duck&&o.kind==='glider')||(p.y<413&&o.kind!=='glider'))state.nearMisses++}
 }
 state.obstacles=state.obstacles.filter(o=>o.x+o.w>-10);
 updateHud();
}
function paintRunner(){
 bg('#bfdce7','#deede1');
 // Separate layers travel at different speeds, so the sprint has real depth.
 for(let i=0;i<6;i++){
  const x=((i*123-state.distance*.055)%(W+160)+(W+160))%(W+160)-75;
  const y=68+(i%3)*61;
  ctx.fillStyle='#ffffff86';ctx.beginPath();
  ctx.ellipse(x,y,29,10,0,0,TAU);
  ctx.ellipse(x+17,y-7,22,13,0,0,TAU);ctx.fill();
 }
 ctx.fillStyle='#96bab22c';ctx.beginPath();ctx.moveTo(0,440);
 for(let x=0;x<=W+12;x+=12){
  ctx.lineTo(x,375+19*Math.sin(x*.015+state.distance*.00025)+
    11*Math.sin(x*.037+1.5));
 }
 ctx.lineTo(W,440);ctx.closePath();ctx.fill();
 const offset=state.distance*.23;
 for(let i=0;i<12;i++){
  const x=((i*95-offset*.5)%490+490)%490-40;
  const roof=300+(i%4)*13;
  fill(x,roof,52,143,'#a6bfc3',6);
  fill(x+12,roof+15,7,13,'#edf1eb');
  fill(x+31,roof+31,6,12,'#edf1ebad');
 }
 fill(0,442,W,108,'#426956');fill(0,440,W,5,'#98c8a6');
 for(let i=0;i<22;i++){const x=((i*41-state.distance)%W+W)%W;fill(x,463+(i%3)*21,25,2,'#6d9e7a')}
 for(const o of state.obstacles){
  if(o.kind==='flying'){ // A low flying dinosaur sweeps its wings and talons across the runner's lane.
   ctx.save();ctx.translate(o.x,0);
   const wing=Math.sin(state.clock*18+o.x*.014)*7;
   ctx.fillStyle='#a25c61';ctx.beginPath();ctx.moveTo(7,412);ctx.lineTo(19,396+wing);
   ctx.lineTo(27,413);ctx.lineTo(42,395-wing);ctx.lineTo(39,422);
   ctx.lineTo(24,431);ctx.lineTo(12,427);ctx.closePath();ctx.fill();
   disk(29,411,11,'#ce8e75');disk(37,408,2,'#253a37');
   ctx.strokeStyle='#654344';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(13,427);ctx.lineTo(10,437);
   ctx.moveTo(27,427);ctx.lineTo(24,437);ctx.stroke();ctx.restore();
  }else if(o.kind==='glider'){
   ctx.save();ctx.translate(o.x,0);
   const wing=Math.sin(state.clock*15+o.x*.012)*7;
   ctx.fillStyle='#815a78';ctx.beginPath();ctx.moveTo(4,383);ctx.lineTo(12,349+wing);
   ctx.lineTo(25,381);ctx.lineTo(43,348-wing);ctx.lineTo(47,392);
   ctx.lineTo(28,397);ctx.lineTo(12,393);ctx.closePath();ctx.fill();
   disk(29,389,9,'#a5738b');disk(35,384,2,'#fff4e3');ctx.restore();
  }else if(o.kind==='pillar'){
   fill(o.x,442-o.h,o.w,o.h,'#93676c',5);
   fill(o.x+6,442-o.h+9,7,8,'#d3a198');
  }else if(o.kind==='barrel'){
    fill(o.x,442-o.h,o.w,o.h,'#9f6b56',7);
    fill(o.x+3,442-o.h+7,o.w-6,5,'#ddb083',2);
    fill(o.x+3,442-11,o.w-6,5,'#ddb083',2);
    disk(o.x+o.w*.5,442-o.h*.5,7,'#bd9171');
   }else{fill(o.x,442-o.h,o.w,o.h,'#aa8170',10);
   fill(o.x+7,442-o.h+6,19,7,'#e1b7a1',4)}
 }
 const p=state.player,ducking=p.duck&&p.y>=441,airborne=p.y<441;
 const stride=Math.sin(state.clock*21);
 // The shadow separates the playable character from the ground while jumping.
 ctx.fillStyle='#1b40362b';ctx.beginPath();
 ctx.ellipse(p.x,443,airborne?11:15,3,0,0,TAU);ctx.fill();
 ctx.save();ctx.translate(p.x,p.y);
 if(airborne)ctx.rotate(-.1);
 if(ducking){
  // Distinct crouch pose: body below the glider's physical collision height.
  fill(-14,-17,29,14,'#32725b',7);
  fill(-16,-7,34,6,'#c28b62',3);
  fill(-13,-4,11,5,'#28463e',2);fill(5,-4,11,5,'#28463e',2);
  disk(7,-15,10,'#e9b18c');
  fill(0,-26,17,7,'#254f49',4);
  disk(11,-15,1.6,'#263f37');
 }else{
  // Arms and staggered legs turn the former tall rectangle into a runner.
  ctx.lineWidth=5;ctx.lineCap='round';ctx.strokeStyle='#e7ae89';
  ctx.beginPath();ctx.moveTo(-8,-29);ctx.lineTo(-15,-22+stride*4);
  ctx.moveTo(8,-29);ctx.lineTo(14,-22-stride*4);ctx.stroke();
  const swing=airborne?5:stride*6;
  ctx.lineWidth=7;ctx.strokeStyle='#2b5448';ctx.beginPath();
  ctx.moveTo(-5,-16);ctx.lineTo(-7+swing,-6);
  ctx.moveTo(5,-16);ctx.lineTo(7-swing,-6);ctx.stroke();
  fill(-13+swing,-6,13,6,'#f4e9d4',3);
  fill(2-swing,-6,13,6,'#f4e9d4',3);
  fill(-11,-34,22,21,'#32725b',6);
  fill(-9,-27,18,4,'#8ac0a2',2);
  fill(-10,-16,20,7,'#be885e',3);
  disk(0,-41,10,'#e9b18c');
  fill(-11,-51,22,8,'#254f49',5);
  fill(-11,-44,22,4,'#386f5b',2);
  disk(4,-40,1.5,'#253a37');
 }
 ctx.restore();
}

/* SPACE SHOOTER — bounded particles, real hull damage, timed bosses and
 * collision between ships (not just projectiles). The player controls X. */
function initShooter(){
 return {score:0,wave:1,lives:3,ship:{x:195,y:488,shield:0,invincible:0},
  shots:[],enemies:[],enemyShots:[],pickups:[],particles:[],
  spawn:.45,kills:0,clock:0,shoot:0,breaches:0,lastBossWave:0,notice:0};
}
function shooterBurst(q,x,y,color,count=10){
 for(let i=0;i<count;i++){
  const angle=rand(0,TAU),speed=rand(35,190),life=rand(.22,.58);
  q.particles.push({x,y,vx:Math.cos(angle)*speed,vy:Math.sin(angle)*speed,life,maxLife:life,color,r:rand(1.5,3.4)});
 }
 if(q.particles.length>100)q.particles.splice(0,q.particles.length-100);
}
function shooterDamage(q,x,y){
 const ship=q.ship;
 if(ship.invincible>0)return false;
 shooterBurst(q,x,y,'#f6a28c',16);
 if(ship.shield>0){ship.shield=0;ship.invincible=.45;audio('hit');}
 else{q.lives--;ship.invincible=1.1;q.notice=1.2;audio('bad');}
 return true;
}
function tickShooter(dt){
 const q=state,ship=q.ship;q.clock+=dt;
 ship.invincible=Math.max(0,ship.invincible-dt);
 ship.shield=Math.max(0,ship.shield-dt);q.notice=Math.max(0,q.notice-dt);
 const control=inputs.drag?clamp((inputs.lastX-ship.x)*14,-560,560):(inputs.left?-365:inputs.right?365:0);
 ship.x=clamp(ship.x+control*dt,24,W-24);
 q.shoot-=dt;
 if(q.shoot<=0){q.shots.push({x:ship.x,y:ship.y-21,vy:-625});q.shoot=Math.max(.115,.19-q.wave*.006);}
 q.spawn-=dt;
 if(q.spawn<=0){
  const boss=q.wave>=3&&q.wave%3===0&&q.lastBossWave!==q.wave;
  if(boss)q.lastBossWave=q.wave;
  const heavy=!boss&&q.wave>1&&Math.random()<Math.min(.4,.15+q.wave*.035);
  const size=boss?70:heavy?43:31;
  q.enemies.push({x:rand(size/2+9,W-size/2-9),y:-50,w:size,h:boss?52:heavy?38:29,
   hp:boss?8+q.wave:heavy?3:1,maxHp:boss?8+q.wave:heavy?3:1,boss,
   vx:rand(-65,65),speed:(boss?29:rand(54,90))+q.wave*5,
   shot:rand(.7,1.65),escaped:false});
  q.spawn=Math.max(.32,.92-q.wave*.055);
 }
 for(const bullet of q.shots)bullet.y+=bullet.vy*dt;
 for(const bullet of q.enemyShots)bullet.y+=bullet.vy*dt;
 for(const enemy of q.enemies){
  if(enemy.hp<=0)continue;
  enemy.y+=enemy.speed*dt;enemy.x+=enemy.vx*dt;
  if(enemy.x<enemy.w/2+6){enemy.x=enemy.w/2+6;enemy.vx=Math.abs(enemy.vx);}
  if(enemy.x>W-enemy.w/2-6){enemy.x=W-enemy.w/2-6;enemy.vx=-Math.abs(enemy.vx);}
  enemy.shot-=dt;
  if(enemy.shot<=0&&enemy.y>20&&enemy.y<ship.y-52){
   q.enemyShots.push({x:enemy.x,y:enemy.y+enemy.h/2,vy:205+q.wave*12});
   enemy.shot=rand(Math.max(.7,1.55-q.wave*.045),2.25);
  }
  // Touching an enemy is dangerous even if no projectile lands.
  if(enemy.y+enemy.h/2>ship.y-17&&enemy.y-enemy.h/2<ship.y+16&&
     Math.abs(enemy.x-ship.x)<(enemy.w+32)/2){
   if(shooterDamage(q,ship.x,ship.y)){enemy.hp=0;shooterBurst(q,enemy.x,enemy.y,'#d9afbd',14);}
  }
  if(enemy.y>H+enemy.h/2&&!enemy.escaped){
   enemy.escaped=true;enemy.hp=0;q.breaches++;
   // A missed enemy is a breach; every third breach costs hull.
   if(q.breaches%3===0)shooterDamage(q,ship.x,ship.y);
   q.notice=1.1;
  }
 }
 for(const bullet of q.shots){
  if(bullet.y<-20)continue;
  for(const enemy of q.enemies){
   if(enemy.hp<=0||Math.abs(bullet.x-enemy.x)>enemy.w/2+3||
      Math.abs(bullet.y-enemy.y)>enemy.h/2+8)continue;
   bullet.y=-999;enemy.hp--;shooterBurst(q,bullet.x,enemy.y,'#d9b7c8',3);
   if(enemy.hp<=0){
    q.kills++;q.score+=enemy.boss?200:enemy.w>35?45:20;
    shooterBurst(q,enemy.x,enemy.y,enemy.boss?'#f1b083':'#d9b7c8',enemy.boss?27:12);
    audio('point');
    if(q.kills%10===0)q.wave++;
    if(Math.random()<.12)q.pickups.push({x:enemy.x,y:enemy.y});
   }
   break;
  }
 }
 for(const pickup of q.pickups){
  pickup.y+=100*dt;
  if(Math.abs(pickup.x-ship.x)<23&&Math.abs(pickup.y-ship.y)<24){
   pickup.y=999;ship.shield=7;shooterBurst(q,ship.x,ship.y,'#7be9b7',10);audio('point');
  }
 }
 if(ship.invincible===0){
  for(const bullet of q.enemyShots){
   if(Math.abs(bullet.x-ship.x)<19&&Math.abs(bullet.y-ship.y)<19){
    bullet.y=999;shooterDamage(q,ship.x,ship.y);break;
   }
  }
 }
 for(const p of q.particles){p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=100*dt;p.life-=dt;}
 q.particles=q.particles.filter(p=>p.life>0);
 q.shots=q.shots.filter(b=>b.y>-18);
 q.enemyShots=q.enemyShots.filter(b=>b.y<H+15);
 q.enemies=q.enemies.filter(e=>e.hp>0&&e.y<H+65);
 q.pickups=q.pickups.filter(p=>p.y<H+10);
 if(q.lives<=0){end('Your ship was lost');return;}
 updateHud();
}
function paintShooter(){
 bg('#0b1c2b','#244558');
 for(let i=0;i<53;i++){
  const x=(i*71)%W,y=(i*127+state.clock*(18+(i%4)*9))%H;
  disk(x,y,i%7===0?1.8:.8,i%3===0?'#d6e8ee':'#739bb0');
 }
 for(const enemy of state.enemies){
  ctx.save();ctx.translate(enemy.x,enemy.y);
  const w=enemy.w,h=enemy.h;
  ctx.fillStyle=enemy.boss?'#b76469':'#8c769e';
  ctx.strokeStyle='#e3bed0';ctx.lineWidth=1.5;
  ctx.beginPath();ctx.moveTo(-w*.5,-h*.18);ctx.lineTo(-w*.3,-h*.5);
  ctx.lineTo(-w*.11,-h*.3);ctx.lineTo(w*.11,-h*.3);
  ctx.lineTo(w*.3,-h*.5);ctx.lineTo(w*.5,-h*.18);
  ctx.lineTo(w*.37,h*.41);ctx.lineTo(0,h*.19);ctx.lineTo(-w*.37,h*.41);
  ctx.closePath();ctx.fill();ctx.stroke();
  fill(-w*.24,-h*.12,w*.48,h*.22,enemy.boss?'#efc18c':'#d8b2c5',4);
  disk(-w*.17,-h*.01,2.5,'#203040');disk(w*.17,-h*.01,2.5,'#203040');
  if(enemy.maxHp>1){
   fill(-w*.43,h*.49,w*.86,3,'#213c48',1);
   fill(-w*.43,h*.49,w*.86*enemy.hp/enemy.maxHp,3,'#f1bd95',1);
  }
  ctx.restore();
 }
 for(const b of state.shots){fill(b.x-2,b.y-11,4,14,'#a6f9cc',2);disk(b.x,b.y-8,3,'#f5fff7');}
 for(const b of state.enemyShots){disk(b.x,b.y,5,'#ee9c89');disk(b.x,b.y,2,'#ffe2cd');}
 for(const p of state.pickups){disk(p.x,p.y,12,'#73cf9e');disk(p.x,p.y,8,'#2e936d');text('+',p.x,p.y+5,17,'#fff',850);}
 for(const p of state.particles){ctx.globalAlpha=Math.min(1,p.life/p.maxLife);disk(p.x,p.y,p.r,p.color);}
 ctx.globalAlpha=1;
 const p=state.ship;ctx.save();
 ctx.globalAlpha=p.invincible>0?(.45+.3*Math.sin(state.clock*30)):1;
 ctx.translate(p.x,p.y);
 ctx.fillStyle='#dcece8';ctx.strokeStyle='#6eacb0';ctx.lineWidth=2;
 ctx.beginPath();ctx.moveTo(0,-27);ctx.lineTo(16,7);ctx.lineTo(22,18);
 ctx.lineTo(9,14);ctx.lineTo(0,7);ctx.lineTo(-9,14);ctx.lineTo(-22,18);
 ctx.lineTo(-16,7);ctx.closePath();ctx.fill();ctx.stroke();
 fill(-7,-1,14,11,'#428e98',5);
 fill(-9,17,6,10,'#f3b873',3);fill(3,17,6,10,'#f3b873',3);
 ctx.restore();
 if(p.shield>0){ctx.strokeStyle='#75f6cb';ctx.lineWidth=2.5;ctx.beginPath();ctx.arc(p.x,p.y,29,0,TAU);ctx.stroke();}
 text('WAVE '+state.wave,15,27,13,'#d9f2e6',850,'left');
 if(state.notice>0)text('DANGER',W-15,27,13,'#ffb39e',850,'right');
 if(state.breaches>0)text('BREACHES '+state.breaches,15,H-16,11,'#abcad5',750,'left');
}

/* BRICK BREAKER — six distinct stages, controlled bounce angles and
 * bounded hit effects. Brick collisions remain swept (single earliest face). */
const BRICK_FINAL_LEVEL=6;
function makeBrickRow(level){
 const grid=[],rows=Math.min(8,6+Math.floor((level-1)/2));
 for(let row=0;row<rows;row++)for(let col=0;col<7;col++){
  // Stage 1 stays a complete 42-brick board. Later stages introduce
  // corridors and angled pockets, not identical copies of the first board.
  const gap=level>=2&&(
   level===2?(row%3===1&&(col===1||col===5)):
   level===3?((row===2||row===5)&&col===3):
   level===4?((row+col)%6===0):
   level===5?(row%2===0&&col===3):
   ((row===0||row===rows-1)&&(col===1||col===5)));
  if(gap)continue;
  const hp=row<Math.min(rows,2+Math.floor(level/3))?
   (level>=4&&row===0?3:2):
   (level>=3&&row===rows-2&&col%3===0?2:1);
  grid.push({x:15+col*52,y:66+row*27,w:46,h:21,hp,maxHp:hp});
 }
 return grid;
}
function brickSpeed(q){return Math.min(690,390+(q.level-1)*30+q.hits*3.3);}
function initBricks(){
 return {score:0,level:1,lives:3,hits:0,paddle:195,paddleWidth:72,
  ball:{x:195,y:479,vx:0,vy:-390},attached:true,bricks:makeBrickRow(1),
  trail:[],particles:[],impact:0,won:false};
}
function newBricksLevel(){
 state.bricks=makeBrickRow(state.level);state.hits=0;
 state.paddleWidth=Math.max(52,72-(state.level-1)*4);
 state.ball={x:state.paddle,y:479,vx:0,vy:-brickSpeed(state)};
 state.attached=true;state.trail=[];state.particles=[];state.impact=0;
}
function launchBall(){
 if(state?.attached&&started){
  state.attached=false;
  // Launches must not travel vertically forever after a lucky centre hit.
  const direction=Math.random()<.5?-1:1,angle=direction*rand(.15,.38);
  const speed=brickSpeed(state);
  state.ball.vx=Math.sin(angle)*speed;state.ball.vy=-Math.cos(angle)*speed;
  audio('hit');
 }
}
function sweptBrick(x,y,dx,dy,brick){
 // Point versus expanded AABB. Choose only the first touched brick.
 const minX=brick.x-8,maxX=brick.x+brick.w+8,minY=brick.y-8,maxY=brick.y+brick.h+8;
 let enter=-Infinity,exit=Infinity,nx=0,ny=0;
 if(Math.abs(dx)<.00001){if(x<=minX||x>=maxX)return null;}
 else{
  const a=(minX-x)/dx,b=(maxX-x)/dx,t=Math.min(a,b),f=Math.max(a,b);
  if(t>enter){enter=t;nx=a<b?-1:1;ny=0;}exit=Math.min(exit,f);
 }
 if(Math.abs(dy)<.00001){if(y<=minY||y>=maxY)return null;}
 else{
  const a=(minY-y)/dy,b=(maxY-y)/dy,t=Math.min(a,b),f=Math.max(a,b);
  if(t>enter){enter=t;nx=0;ny=a<b?-1:1;}exit=Math.min(exit,f);
 }
 return enter>=0&&enter<=1&&exit>=enter?{t:enter,nx,ny}:null;
}
function brickSparks(q,x,y,color){
 for(let i=0;i<9;i++){
  const a=rand(0,TAU),speed=rand(35,125),life=rand(.18,.43);
  q.particles.push({x,y,vx:Math.cos(a)*speed,vy:Math.sin(a)*speed,life,maxLife:life,color,r:rand(1.2,3)});
 }
 if(q.particles.length>75)q.particles.splice(0,q.particles.length-75);
}
function completeBricks(){
 const q=state;q.won=true;over=true;paused=false;started=false;
 saveBest(Math.floor(q.score));updateHud();
 overlay('VICTORY','All six stages cleared','Final score '+Math.floor(q.score)+' · Best '+Math.floor(readBest()),'Play again');
 $('message').textContent='Arcade cleared!';audio('point');
}
function tickBricks(dt){
 const q=state,b=q.ball,previousPaddle=q.paddle;
 if(inputs.left)q.paddle-=365*dt;if(inputs.right)q.paddle+=365*dt;
 if(inputs.drag)q.paddle=inputs.lastX;
 q.paddle=clamp(q.paddle,q.paddleWidth/2,W-q.paddleWidth/2);
 // A brick's sparks and paddle flash are short-lived; they must continue
 // fading even while a replacement ball waits on the paddle.
 q.impact=Math.max(0,q.impact-dt);
 for(const p of q.particles){
  p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=180*dt;p.life-=dt;
 }
 q.particles=q.particles.filter(p=>p.life>0);
 if(q.attached){b.x=q.paddle;q.trail=[];return}
 const oldX=b.x,oldY=b.y,dx=b.vx*dt,dy=b.vy*dt;
 b.x+=dx;b.y+=dy;
 if(b.x<8){b.x=16-b.x;b.vx=Math.abs(b.vx)}
 if(b.x>W-8){b.x=2*(W-8)-b.x;b.vx=-Math.abs(b.vx)}
 if(b.y<8){b.y=16-b.y;b.vy=Math.abs(b.vy)}
 // Calculate the actual contact point at the top of the paddle, not the
 // frame's final X. At later levels the visible paddle shrinks; its hitbox
 // must shrink as well (with the ball's 8px radius), or it catches thin air.
 const surfaceY=490-8;
 const crossesPaddle=dy>0&&oldY<=surfaceY&&b.y>=surfaceY;
 const contactT=crossesPaddle?(surfaceY-oldY)/dy:0;
 const contactX=oldX+dx*contactT;
 if(crossesPaddle&&Math.abs(contactX-q.paddle)<=q.paddleWidth/2+8){
  b.x=contactX;b.y=surfaceY;
  const offset=clamp((contactX-q.paddle)/(q.paddleWidth/2+3),-1,1);
  const paddleVelocity=(q.paddle-previousPaddle)/Math.max(dt,.008);
  const spin=clamp(paddleVelocity/1500,-.25,.25);
  let angle=clamp(offset*1.02+spin,-1.12,1.12);
  // Never deadlock into a straight vertical lane on a perfect centre hit.
  if(Math.abs(angle)<.1)angle=Math.sign(b.vx||1)*.1;
  const speed=brickSpeed(q);
  b.vx=Math.sin(angle)*speed;b.vy=-Math.cos(angle)*speed;
  q.impact=.16;audio('hit');
 }
 let impacted=null;
 for(const brick of q.bricks){
  if(brick.hp<=0)continue;
  const hit=sweptBrick(oldX,oldY,dx,dy,brick);
  if(hit&&(!impacted||hit.t<impacted.t))impacted={...hit,brick};
 }
 if(impacted){
  const {brick,t,nx,ny}=impacted;
  b.x=oldX+dx*Math.max(0,t-.0005)+nx*.2;
  b.y=oldY+dy*Math.max(0,t-.0005)+ny*.2;
  if(nx)b.vx=-b.vx;else b.vy=-b.vy;
  brick.hp--;q.hits++;
  q.score+=brick.hp===0?15:5;
  audio('point');q.impact=.28;
  brickSparks(q,b.x,b.y,brick.hp?'#f3dcb8':'#fff6c4');
  const speed=brickSpeed(q),mag=Math.hypot(b.vx,b.vy)||1;
  let vx=b.vx/mag*speed,vy=b.vy/mag*speed;
  if(Math.abs(vy)<speed*.46){
   vy=Math.sign(vy||-1)*speed*.46;
   vx=Math.sign(vx||1)*Math.sqrt(speed*speed-vy*vy);
  }
  if(Math.abs(vx)<speed*.16){
   vx=Math.sign(vx||1)*speed*.16;
   vy=Math.sign(vy||-1)*Math.sqrt(speed*speed-vx*vx);
  }
  b.vx=vx;b.vy=vy;
  q.bricks=q.bricks.filter(item=>item.hp>0);
  if(!q.bricks.length){
   if(q.level>=BRICK_FINAL_LEVEL){completeBricks();return;}
   q.level++;newBricksLevel();audio('point');updateHud();return;
  }
 }
 q.trail.push({x:b.x,y:b.y});
 if(q.trail.length>7)q.trail.shift();
 if(b.y>H+14){
  q.lives--;audio('bad');
  if(q.lives<=0){end('Out of balls');return;}
  else{
   q.ball={x:q.paddle,y:479,vx:0,vy:-brickSpeed(q)};
   q.attached=true;q.trail=[];q.particles=[];
  }
 }
 updateHud();
}
function paintBricks(){
 bg('#e6eee4','#b8d4c3');
 ctx.strokeStyle='#ffffff24';ctx.lineWidth=1;
 for(let y=57;y<475;y+=25){ctx.beginPath();ctx.moveTo(8,y);ctx.lineTo(W-8,y);ctx.stroke();}
 fill(0,521,W,29,'#9ebda9');
 const colors=['#d97f70','#dba55d','#639d87','#759abb','#a18bb7','#5397a0','#bd9470','#709e7f'];
 const q=state;
 for(const b of q.bricks){
  const row=Math.round((b.y-66)/27),col=colors[row%colors.length];
  fill(b.x,b.y,b.w,b.h,col,5);
  fill(b.x+3,b.y+3,b.w-6,3,'#ffffff70',2);
  if(b.maxHp>1){
   fill(b.x+5,b.y+b.h-7,b.w-10,2,'#17362c44',1);
   fill(b.x+5,b.y+b.h-7,(b.w-10)*b.hp/b.maxHp,2,'#fff2db',1);
  }
 }
 for(const p of q.particles){
  ctx.globalAlpha=Math.min(1,p.life/p.maxLife);
  disk(p.x,p.y,p.r,p.color);
 }
 ctx.globalAlpha=1;
 for(let i=0;i<q.trail.length;i++){
  const p=q.trail[i];disk(p.x,p.y,2+i*.72,'#ffffff'+(Math.round(10+i*7)).toString(16).padStart(2,'0'));
 }
 const p=q.paddle,half=q.paddleWidth/2;
 fill(p-half,490,q.paddleWidth,12,q.impact>0?'#45896d':'#205c49',6);
 fill(p-half+7,492,q.paddleWidth-14,3,'#c1ebd3',2);
 const ball=q.ball;
 disk(ball.x,ball.y,8,'#ffffff');disk(ball.x-2,ball.y-2,3,'#d3e5df');
 text('LEVEL '+q.level+' / '+BRICK_FINAL_LEVEL,15,28,13,'#3e6655',850,'left');
 text(q.bricks.length+' LEFT',W-15,28,12,'#3e6655',750,'right');
 if(q.attached&&started)text('TAP TO LAUNCH',W/2,353,16,'#4a7960',850);
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
 inputs.left=inputs.right=inputs.drag=inputs.duck=false;inputs.runnerGesture=null;inputs.slicePointerId=null;
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
 if(game==='slice'&&inputs.drag)return;
 const p=point(e);inputs.drag=true;inputs.lastX=p.x;inputs.lastY=p.y;inputs.dragStartY=p.y;
 if(game==='slice')inputs.slicePointerId=e.pointerId;
 if(game==='runner')inputs.runnerGesture=null;
 if(started&&!over&&!paused){
  if(game==='bricks')launchBall();
  if(game==='slice'){state.trail.length=0;state.trail.push({x:p.x,y:p.y,a:1})}
 }
 try{canvas.setPointerCapture(e.pointerId)}catch{}
}
function move(e){
 if(!inputs.drag||(game==='slice'&&e.pointerId!==inputs.slicePointerId))return;
 const p=point(e);
 if(game==='slice'){
  // Coalesced pointer samples preserve the actual blade path on 120Hz+
  // screens instead of jumping straight across a curved finger gesture.
  // Keep sub-14px motion until enough distance forms a real stroke.
  const coalesced=typeof e.getCoalescedEvents==='function'?e.getCoalescedEvents():[];
  const samples=coalesced.length?[...coalesced,e]:[e];
  for(const sample of samples){
   const q=point(sample);
   if(Math.hypot(q.x-inputs.lastX,q.y-inputs.lastY)>=14){
    sliceMove(inputs.lastX,inputs.lastY,q.x,q.y);
    inputs.lastX=q.x;inputs.lastY=q.y;
   }
  }
 }else{
  if(game==='runner'&&started&&!paused&&!over){
  if(p.y-inputs.dragStartY>26&&inputs.runnerGesture!=='jump'){
   inputs.runnerGesture='slide';state.duckTimer=.58;state.player.duck=true;
  }else if(inputs.dragStartY-p.y>27&&inputs.runnerGesture!=='slide'&&inputs.runnerGesture!=='jump'){
   inputs.runnerGesture='jump';jumpRunner();
  }
 }
  inputs.lastX=p.x;inputs.lastY=p.y;
 }
}
function up(e){
 if(game==='slice'&&e.pointerId!==inputs.slicePointerId)return;
 if(game==='slice'&&e.type==='pointerup'&&inputs.drag){
  // Android/browser scheduling can coalesce the last touch movement into
  // pointerup. Do not discard the final real blade segment on finger lift.
  const p=point(e);
  if(Math.hypot(p.x-inputs.lastX,p.y-inputs.lastY)>=14)
   sliceMove(inputs.lastX,inputs.lastY,p.x,p.y);
 }
 if(game==='runner'&&e.type!=='pointercancel'&&started&&!paused&&!over&&inputs.runnerGesture===null)jumpRunner();
 inputs.runnerGesture=null;inputs.drag=false;inputs.slicePointerId=null;
 try{canvas.releasePointerCapture(e.pointerId)}catch{}
}
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
