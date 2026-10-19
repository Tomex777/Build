/* NIGHT offline skills: touch-first sports, precision and reaction games. */
(()=>{'use strict';
const G=window.G,TAU=Math.PI*2,rand=(a,b)=>a+Math.random()*(b-a),clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
function canvas(){const c=G.el('canvas','arena');c.width=360;c.height=520;Object.assign(c.style,{width:'100%',maxWidth:'410px',aspectRatio:'9/13',borderRadius:'13px',display:'block',margin:'auto',background:'#19342d',touchAction:'none'});G.board().append(c);return[c,c.getContext('2d')]}
function position(e,c){const r=c.getBoundingClientRect();return{x:(e.clientX-r.left)/r.width*360,y:(e.clientY-r.top)/r.height*520}}
function fill(ctx,x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(x,y,w,h)}
function dot(ctx,x,y,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill()}
function words(ctx,s,y,size=18){ctx.fillStyle='#eff1de';ctx.textAlign='center';ctx.font='700 '+size+'px system-ui';ctx.fillText(s,180,y)}
function animate(update,render){let id=0,last=0,stopped=false;function tick(t){if(stopped)return;const dt=Math.min((t-last)/1000||.016,.042);last=t;if(!document.hidden)update(dt);render();id=requestAnimationFrame(tick)}id=requestAnimationFrame(tick);return()=>{stopped=true;cancelAnimationFrame(id)}}
G.add('archery','Archery','Adjust for crosswind and shoot ten arrows at the moving target.','Sports',()=>{
 const [c,ctx]=canvas();let shots=0,score=0,wind=rand(-28,28),target={x:180,y:214},phase=0,impact=null;
 c.addEventListener('pointerdown',e=>{if(shots>=10)return;const p=position(e,c);const ix=p.x+wind,iy=p.y+rand(-10,10),d=Math.hypot(ix-target.x,iy-target.y),points=d<14?10:d<32?8:d<52?5:d<74?2:0;impact={x:ix,y:iy};score+=points;shots++;wind=rand(-28,28);G.status('Arrows '+shots+'/10 · Score '+score+' · +'+points);if(shots===10)G.score('archery',score)});
 const stop=animate(dt=>{phase+=dt*.9;if(shots<10)target.x=180+Math.sin(phase)*55},()=>{fill(ctx,0,0,360,520,'#1d382f');fill(ctx,0,305,360,215,'#36523d');for(const [radius,color] of [[76,'#e9e4d4'],[56,'#c96c60'],[37,'#749f7b'],[19,'#e6c565']])dot(ctx,target.x,target.y,radius,color);if(impact)dot(ctx,impact.x,impact.y,5,'#1b4433');words(ctx,'ARROWS '+shots+'/10  SCORE '+score,52);words(ctx,'WIND '+(wind>0?'→ ':wind<0?'← ':'')+Math.abs(Math.round(wind)),445,16)});
 G.status('Aim and shoot · 0/10');G.note('Crosswind shifts arrows left or right; the target moves. Aim away from the wind.');return stop});
G.add('golfputt','Golf Putt','Putt into the cup across three increasingly tricky greens. Fewer strokes is better.','Sports',()=>{
 const [c,ctx]=canvas();let hole={x:180,y:84},ball={x:180,y:442,vx:0,vy:0},stage=1,strokes=0,start=null,finished=false,obstacle={x:155,y:270,w:50,h:12};
 c.addEventListener('pointerdown',e=>{if(finished||Math.hypot(ball.vx,ball.vy)>3)return;start=position(e,c);c.setPointerCapture(e.pointerId)});
 c.addEventListener('pointerup',e=>{if(!start||finished)return;const p=position(e,c),dx=start.x-p.x,dy=start.y-p.y;ball.vx=clamp(dx*2.1,-500,500);ball.vy=clamp(dy*2.1,-500,500);strokes++;start=null;G.status('Hole '+stage+'/3 · Strokes '+strokes)});
 const stop=animate(dt=>{if(finished)return;ball.x+=ball.vx*dt;ball.y+=ball.vy*dt;const f=Math.max(0,1-dt*1.8);ball.vx*=f;ball.vy*=f;if(ball.x<14||ball.x>346){ball.x=clamp(ball.x,14,346);ball.vx*=-.55}if(ball.y<16||ball.y>504){ball.y=clamp(ball.y,16,504);ball.vy*=-.55}
 if(stage>1&&ball.x>obstacle.x-12&&ball.x<obstacle.x+obstacle.w+12&&ball.y>obstacle.y-12&&ball.y<obstacle.y+obstacle.h+12){ball.vy*=-.7;ball.y=ball.y<obstacle.y?obstacle.y-13:obstacle.y+obstacle.h+13}
 if(Math.hypot(ball.x-hole.x,ball.y-hole.y)<17&&Math.hypot(ball.vx,ball.vy)<200){if(stage===3){finished=true;G.status('Three holes in '+strokes+' strokes');G.score('golfputt',Math.max(1,40-strokes));return}stage++;ball={x:180,y:442,vx:0,vy:0};hole={x:stage===2?285:70,y:84};G.status('Hole '+stage+'/3 · '+strokes+' strokes')}},()=>{
 fill(ctx,0,0,360,520,'#276647');ctx.strokeStyle='#8fbc91';ctx.lineWidth=4;for(let x=37;x<360;x+=60){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,520);ctx.stroke()}
 dot(ctx,hole.x,hole.y,18,'#143e30');fill(ctx,hole.x+12,hole.y-40,3,42,'#e8eed8');fill(ctx,hole.x+15,hole.y-40,29,18,'#efc179');if(stage>1)fill(ctx,obstacle.x,obstacle.y,obstacle.w,obstacle.h,'#cdb282');dot(ctx,ball.x,ball.y,11,'#f6f7e8');words(ctx,'HOLE '+stage+'/3 · STROKES '+strokes,37,17)});
 G.status('Hole 1/3 · Strokes 0');G.note('Swipe backward from a stationary ball to set shot direction and power. Avoid the bunker.');return stop});
G.add('bowling','Bowling','Complete ten frames. Strikes and spares earn bonus points.','Sports',()=>{
 const [c,ctx]=canvas();let frame=0,frames=Array.from({length:10},()=>[]),pins=[],aim=180,phase='aim',flight=0,ballX=180,notice='',end=false;
 function resetPins(){pins=Array.from({length:10},(_,i)=>{let row=0;while((row+1)*(row+2)/2<=i)row++;return{x:180+(i-row*(row+1)/2-row/2)*27,y:103+row*27,up:true}})}
 function allRollsAfter(frameIndex){return frames.slice(frameIndex+1).flat()}
 function total(){let total=0;for(let f=0;f<10;f++){const r=frames[f],next=allRollsAfter(f);if(f===9){total+=r.reduce((a,b)=>a+b,0);break}if(r[0]===10){total+=10+next.slice(0,2).reduce((a,b)=>a+b,0)}else if(r.length===2&&r[0]+r[1]===10){total+=10+(next[0]||0)}else total+=r.reduce((a,b)=>a+b,0)}return total}
 function finishedFrame(){const rolls=frames[frame];if(frame<9)return rolls[0]===10||rolls.length===2;if(rolls.length<2)return false;if(rolls[0]===10||rolls[0]+rolls[1]===10)return rolls.length===3;return true}
 function scoreHit(){const standing=pins.filter(p=>p.up);const offset=aim-180,accuracy=rand(-9,9);let hit=0;for(const p of standing){const dx=Math.abs(p.x-offset-180-accuracy);const chance=dx<20?.98:dx<36?.83:dx<58?.58:dx<80?.31:.04;if(Math.random()<chance){p.up=false;hit++}}
 const rolls=frames[frame];rolls.push(hit);notice=hit===10?'STRIKE!':hit===0?'Gutter!':hit+' pins';
 if(frame===9){if(rolls.length===1&&hit===10){resetPins()}else if(rolls.length===2&&rolls[0]===10&&rolls[1]===10){resetPins()}else if(rolls.length===2&&(rolls[0]+rolls[1]===10)){resetPins()}}
 if(finishedFrame()){if(frame===9){end=true;G.score('bowling',total());notice='Game complete!'}else {frame++;resetPins()}}
 phase='aim';G.status(notice+' · Total '+total())}
 resetPins();
 const steer=e=>{if(phase!=='aim'||end)return;aim=clamp(position(e,c).x,65,295)};
 c.addEventListener('pointermove',steer);
 c.addEventListener('pointerdown',e=>{if(phase!=='aim'||end)return;aim=clamp(position(e,c).x,65,295);phase='flight';flight=0;ballX=180});
 const stop=animate(dt=>{if(phase==='flight'){flight+=dt*1.5;ballX=180+(aim-180)*Math.min(1,flight);if(flight>=1){phase='resolve';scoreHit()}}},()=>{
 fill(ctx,0,0,360,520,'#39423b');fill(ctx,54,80,252,410,'#c9a779');for(const x of [54,306])fill(ctx,x-3,80,6,410,'#e5d1a1');for(const p of pins)if(p.up){dot(ctx,p.x,p.y,10,'#f2f3e5');dot(ctx,p.x,p.y,4,'#c65f59')}
 if(phase==='flight')dot(ctx,ballX,444-330*Math.min(1,flight),20,'#314558');else if(!end){dot(ctx,180,447,20,'#314558');fill(ctx,aim-2,250,4,174,'#94bea3')}
 words(ctx,'FRAME '+(frame+1)+'/10 · '+total()+' PTS',50,18);if(end)words(ctx,'GAME COMPLETE',473,17);else if(notice)words(ctx,notice.toUpperCase(),476,13)});
 G.status('Frame 1/10 · Tap a lane to roll');G.note('Aim along the lane, then tap to roll. Ten-pin scoring includes spare and strike bonuses.');return stop});
G.add('swimming','Swimming Race','Tap rhythmically to swim 50 meters before the computer finishes.','Sports',()=>{
 const [c,ctx]=canvas();let progress=0,computer=0,stamina=100,elapsed=0,end=false,lastTap=-10;
 function stroke(){if(end||stamina<5)return;const now=performance.now()/1000,interval=now-lastTap;lastTap=now;const boost=interval>.25&&interval<.9?2.6:1.3;progress=clamp(progress+boost,0,50);stamina=clamp(stamina-7,0,100);if(progress>=50){end=true;G.score('swimming',Math.max(1,100-Math.floor(elapsed)));G.status('You win in '+elapsed.toFixed(1)+' seconds!')}}
 c.addEventListener('pointerdown',stroke);G.action('Stroke',stroke,true);
 const stop=animate(dt=>{if(end)return;elapsed+=dt;computer=clamp(computer+3.9*dt,0,50);stamina=clamp(stamina+19*dt,0,100);if(computer>=50){end=true;G.status('Computer wins · '+progress.toFixed(1)+'m')};if(!end)G.status('You '+progress.toFixed(1)+'m · CPU '+computer.toFixed(1)+'m')},()=>{
 fill(ctx,0,0,360,520,'#1e586f');for(let y=100;y<420;y+=80){fill(ctx,0,y,360,4,'#d0cfa2');for(let x=12;x<360;x+=47)dot(ctx,x,y+30,4,'#5399a4')}
 rect(ctx,30,140,300,26,'#387b90');rect(ctx,30,140,progress*6,26,'#b7e0cc');rect(ctx,30,263,300,26,'#387b90');rect(ctx,30,263,computer*6,26,'#ddb28d');words(ctx,'YOU '+progress.toFixed(1)+'m',122,18);words(ctx,'CPU '+computer.toFixed(1)+'m',244,18);words(ctx,'STAMINA '+Math.round(stamina)+'%',425,16);words(ctx,'TIME '+elapsed.toFixed(1)+'s',55,19)});
 G.status('Swim 50m before the CPU');G.note('Tap with a steady rhythm. Very rapid taps drain stamina without adding much speed.');return stop});

})();