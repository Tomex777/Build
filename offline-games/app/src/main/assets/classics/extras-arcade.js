/* NIGHT offline arcade expansion. Fully bundled original Canvas mini-games. */
(()=>{'use strict';
const G=window.G,TAU=Math.PI*2,rand=(a,b)=>a+Math.random()*(b-a),int=n=>Math.floor(Math.random()*n);
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
function arena(width=360,height=520){const c=G.el('canvas','arena');c.width=width;c.height=height;Object.assign(c.style,{width:'100%',maxWidth:'410px',aspectRatio:width+'/'+height,borderRadius:'12px',display:'block',margin:'0 auto',background:'#142c25',touchAction:'none'});G.board().append(c);return [c,c.getContext('2d')]}
function pos(e,c){const r=c.getBoundingClientRect();return {x:(e.clientX-r.left)/r.width*c.width,y:(e.clientY-r.top)/r.height*c.height}}
function rect(ctx,x,y,w,h,fill){ctx.fillStyle=fill;ctx.fillRect(x,y,w,h)}
function circle(ctx,x,y,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill()}
function title(ctx,t){ctx.fillStyle='#dfeee2';ctx.font='700 16px system-ui';ctx.textAlign='center';ctx.fillText(t,180,31)}
function loop(update,paint){let active=true,last=0,raf=0;function step(now){if(!active)return;const dt=last?Math.min(.045,(now-last)/1000):.016;last=now;if(!document.hidden)update(dt,now);paint();raf=requestAnimationFrame(step)}raf=requestAnimationFrame(step);return()=>{active=false;cancelAnimationFrame(raf)}}
function finish(id,score,message,stop){G.score(id,score);G.status(message);stop.finished=true}
function lost(stop){return !!stop.finished}
G.add('car','Car Dodge','Change lanes to avoid traffic. Your score grows with every vehicle passed.','Racing',()=>{
 const [c,ctx]=arena(),lanes=[72,180,288];let player=1,cars=[],meter=0,score=0,spawn=.9,dead={finished:false};
 c.addEventListener('pointerdown',e=>{const p=pos(e,c);player=clamp(Math.floor(p.x/120),0,2)});
 const keys=e=>{if(['ArrowLeft','a'].includes(e.key))player=clamp(player-1,0,2);if(['ArrowRight','d'].includes(e.key))player=clamp(player+1,0,2)};document.addEventListener('keydown',keys);
 const stop=loop(dt=>{if(lost(dead))return;meter+=dt;spawn-=dt;if(spawn<=0){spawn=Math.max(.43,1.05-meter*.004);cars.push({lane:int(3),y:-65,speed:190+Math.min(210,meter*3),counted:false})}cars.forEach(v=>{v.y+=v.speed*dt;if(!v.counted&&v.y>530){v.counted=true;score++;G.status('Traffic passed '+score)}if(v.lane===player&&v.y>420&&v.y<510){finish('car',score,'Collision · '+score+' cars passed',dead)}});cars=cars.filter(v=>v.y<580)},()=>{
 rect(ctx,0,0,360,520,'#1b352a');for(let x=0;x<360;x+=120){rect(ctx,x+4,0,112,520,'#2b3c37');ctx.fillStyle='#a1aa90';for(let y=(meter*170)%110-110;y<520;y+=110)rect(ctx,x+58,y,4,62,'#a1aa90')}cars.forEach(v=>{rect(ctx,lanes[v.lane]-30,v.y,60,55,'#be796b');rect(ctx,lanes[v.lane]-23,v.y+10,46,19,'#8fa7ad')});
 rect(ctx,lanes[player]-30,445,60,58,'#72b79a');rect(ctx,lanes[player]-22,457,44,24,'#c0dcd3');title(ctx,'PASSED '+score);if(lost(dead))title(ctx,'CRASH!')});
 G.status('Dodge traffic · 0 passed');G.note('Tap a lane or use left/right arrow keys. Traffic speeds up as you survive.');return()=>{stop();document.removeEventListener('keydown',keys)}});
G.add('zigzag','Zigzag Path','Change direction on each tap to follow a twisting path.','Endless',()=>{
 const [c,ctx]=arena(),size=28;let snake=[{x:6,y:17}],dir={x:1,y:-1},turns=0,steps=0,segments=[],time=0,over={finished:false};
 // A corridor advances diagonally; its path turns at generated checkpoints.
 let path=[{x:6,y:17}],head={x:6,y:17};for(let i=0;i<70;i++){const d=i%9<4?{x:1,y:-1}:{x:1,y:1};head={x:head.x+d.x,y:clamp(head.y+d.y,3,19)};path.push(head)}
 function turn(){if(lost(over))return;dir=dir.y===-1?{x:1,y:1}:{x:1,y:-1};turns++}
 c.addEventListener('pointerdown',turn);G.action('Turn',turn,true);
 const stop=loop(dt=>{if(lost(over))return;time+=dt;if(time<.18)return;time-=.18;const p=snake[0],next={x:p.x+dir.x,y:p.y+dir.y};steps++;const expected=path[steps];if(!expected||Math.abs(next.y-expected.y)>1){finish('zigzag',steps-1,'Off the path · '+(steps-1)+' steps',over);return}snake.unshift(next);if(snake.length>12)snake.pop();G.status('Steps '+steps+' · Turns '+turns)},()=>{
 rect(ctx,0,0,360,520,'#152f2a');const lead=snake[0],base=lead.x-6;
 path.forEach(p=>{const x=180+(p.x-lead.x)*25,y=260+(p.y-lead.y)*19;if(x>-35&&x<400&&y>-35&&y<550)rect(ctx,x-14,y-11,27,23,'#355c47')});
 snake.forEach((p,i)=>{const x=180+(p.x-lead.x)*25,y=260+(p.y-lead.y)*19;if(x>-35&&x<400&&y>-35&&y<550)circle(ctx,x,y,Math.max(4,11-i*.6),i?'#6ba885':'#e5d3a1')});
 title(ctx,'STEPS '+steps);if(lost(over))title(ctx,'PATH LOST')});
 G.status('Tap to change direction');G.note('Keep your moving marker on the path. Anticipate where the corridor bends.');return stop});
G.add('laser','Laser Dodge','Survive sweeping horizontal and vertical laser beams.','Action',()=>{
 const [c,ctx]=arena();let player={x:180,y:260},beams=[],elapsed=0,spawn=1,dead={finished:false};
 const steer=e=>{const p=pos(e,c);player.x=clamp(p.x,15,345);player.y=clamp(p.y,15,505)};c.addEventListener('pointerdown',steer);c.addEventListener('pointermove',steer);
 const stop=loop(dt=>{if(lost(dead))return;elapsed+=dt;spawn-=dt;if(spawn<=0){spawn=Math.max(.7,1.9-elapsed*.007);beams.push({p:Math.random()<.5?0:360,axis:int(2),v:rand(110,180)*(Math.random()<.5?1:-1),life:0})}beams.forEach(b=>{b.life+=dt;if(b.life>.75){b.p+=b.v*dt;if(b.axis===0&&Math.abs(b.p-player.x)<13||b.axis===1&&Math.abs(b.p-player.y)<13)finish('laser',Math.floor(elapsed),'Laser hit · '+Math.floor(elapsed)+' seconds',dead)}});beams=beams.filter(b=>b.life<3);if(!lost(dead))G.status('Survived '+Math.floor(elapsed)+'s')},()=>{
 rect(ctx,0,0,360,520,'#112a29');for(const b of beams){ctx.globalAlpha=b.life<.75?.22:.95;rect(ctx,b.axis===0?b.p-3:0,b.axis===1?b.p-3:0,b.axis===0?6:360,b.axis===1?6:520,'#e6846c');ctx.globalAlpha=1}
 circle(ctx,player.x,player.y,11,'#b8e0b0');title(ctx,'SURVIVAL '+Math.floor(elapsed)+'s')});
 G.status('Survive · 0s');G.note('Drag your marker in any direction. Faint lines warn you before lasers activate.');return stop});

})();