/* Two classic offline shooters, original visuals and movement engines. */
(()=>{'use strict';const G=window.G,T=Math.PI*2,R=(a,b)=>a+Math.random()*(b-a),clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
function arena(){const c=G.el('canvas','arena');c.width=360;c.height=520;c.style.cssText='display:block;width:100%;max-width:410px;aspect-ratio:9/13;margin:auto;border-radius:12px;background:#192e2d;touch-action:none';G.board().append(c);return[c,c.getContext('2d')]}
function coords(e,c){const r=c.getBoundingClientRect();return{x:(e.clientX-r.left)*360/r.width,y:(e.clientY-r.top)*520/r.height}}
function rect(ctx,x,y,w,h,col){ctx.fillStyle=col;ctx.fillRect(x,y,w,h)}
function disc(ctx,x,y,r,col){ctx.fillStyle=col;ctx.beginPath();ctx.arc(x,y,r,0,T);ctx.fill()}
function text(ctx,s){ctx.textAlign='center';ctx.fillStyle='#edf4e3';ctx.font='bold 18px system-ui';ctx.fillText(s,180,37)}
function frame(update,render){let prior=0,raf=0,done=false;function tick(t){if(done)return;const dt=prior?Math.min(.04,(t-prior)/1000):.016;prior=t;if(!document.hidden)update(dt);render();raf=requestAnimationFrame(tick)}raf=requestAnimationFrame(tick);return()=>{done=true;cancelAnimationFrame(raf)}}
G.add('centipede','Centipede','Shoot the descending centipede and mushrooms before they overwhelm the garden.','Arcade',()=>{
 const[c,ctx]=arena(),W=18,H=26,cell=20;let chain=[],mushrooms=[],bullets=[],shooter=180,tick=.1,elapsed=0,score=0,lives=3,wave=1,over=false,direction=1;
 function reset(){chain=Array.from({length:12},(_,i)=>({x:17-i,y:2}));direction=-1;tick=0}
 for(let i=0;i<32;i++){const x=Math.floor(R(1,17)),y=Math.floor(R(4,21));if(!mushrooms.some(m=>m.x===x&&m.y===y))mushrooms.push({x,y,hp:2})}reset();
 const move=e=>shooter=clamp(coords(e,c).x,14,346);
 c.addEventListener('pointermove',move);c.addEventListener('pointerdown',e=>{move(e);if(over)return;bullets.push({x:shooter,y:457})});
 const stop=frame(dt=>{if(over)return;elapsed+=dt;tick+=dt;
 if(tick>=Math.max(.065,.18-wave*.015)){tick=0;const old=chain.map(p=>({...p})),head=old[0];if(!head)return;let x=head.x+direction,y=head.y;
 if(x<0||x>=W||mushrooms.some(m=>m.x===x&&m.y===y)){direction*=-1;x=head.x;y++}
 chain[0]={x,y};for(let i=1;i<chain.length;i++)chain[i]=old[i-1];
 if(y>=23){lives--;reset();if(lives<=0){over=true;G.score('centipede',score);G.status('Garden overrun · '+score+' points')}}}
 bullets.forEach(b=>b.y-=430*dt);
 for(const b of bullets){const segment=chain.findIndex(s=>Math.hypot(s.x*20+10-b.x,s.y*20+10-b.y)<13);if(segment>=0){const seg=chain.splice(segment,1)[0];mushrooms.push({x:seg.x,y:seg.y,hp:1});b.y=-100;score+=15;G.status('Score '+score+' · Wave '+wave);continue}
 const hit=mushrooms.find(m=>Math.abs(m.x*20+10-b.x)<12&&Math.abs(m.y*20+10-b.y)<12);if(hit){hit.hp--;b.y=-100}}
 mushrooms=mushrooms.filter(m=>m.hp>0);bullets=bullets.filter(b=>b.y>0);
 if(!chain.length){wave++;reset();G.status('Wave '+wave+' · Score '+score)}
 },()=>{
 rect(ctx,0,0,360,520,'#1b3629');for(const m of mushrooms){disc(ctx,m.x*20+10,m.y*20+10,8,m.hp===2?'#bba979':'#817f68')}
 chain.forEach((p,i)=>{disc(ctx,p.x*20+10,p.y*20+10,9,i?'#8eb487':'#e1c983');if(i===0){disc(ctx,p.x*20+7,p.y*20+8,2,'#274b3a');disc(ctx,p.x*20+13,p.y*20+8,2,'#274b3a')}});bullets.forEach(b=>rect(ctx,b.x-2,b.y-8,4,14,'#e9e5b3'));
 rect(ctx,shooter-16,470,32,22,'#b8d7a6');rect(ctx,shooter-3,461,6,14,'#f7d9a1');text(ctx,'WAVE '+wave+' · SCORE '+score+' · ♥ '+lives)});
 G.status('Touch to fire, slide to move');G.note('Move your shooter along the bottom of the garden. Tap to fire through mushrooms and centipede segments.');return stop});
G.add('galaga','Galaga Blitz','Survive diving attack formations and shoot all enemies through five waves.','Arcade',()=>{
 const[c,ctx]=arena();let player=180,enemies=[],shots=[],hostile=[],cool=.2,dive=.9,fire=.1,score=0,lives=3,wave=1,inv=0,elapsed=0,over=false;
 function build(){enemies=Array.from({length:28},(_,i)=>({originX:45+i%7*45,originY:80+Math.floor(i/7)*31,x:45+i%7*45,y:80+Math.floor(i/7)*31,diving:false,phase:0,alive:true}))}build();
 const move=e=>player=clamp(coords(e,c).x,25,335);c.addEventListener('pointerdown',move);c.addEventListener('pointermove',move);
 const stop=frame(dt=>{if(over)return;elapsed+=dt;inv=Math.max(0,inv-dt);cool-=dt;fire-=dt;dive-=dt;
 if(cool<=0){cool=.20;shots.push({x:player,y:455})}
 if(dive<=0){dive=Math.max(.55,1.3-wave*.1);const candidates=enemies.filter(e=>e.alive&&!e.diving);if(candidates.length){const e=candidates[Math.floor(R(0,candidates.length))];e.diving=true;e.phase=0;hostile.push({x:e.x,y:e.y,vx:R(-30,30),vy:210})}}
 enemies.forEach(e=>{if(!e.alive)return;if(e.diving){e.phase+=dt;e.x+=Math.sin(e.phase*4)*155*dt;e.y+=(175+wave*20)*dt;if(e.y>550){e.diving=false;e.x=e.originX;e.y=e.originY}}else{e.x=e.originX+Math.sin(elapsed*1.3+e.originY)*8;e.y=e.originY+Math.cos(elapsed)*4}});
 shots.forEach(s=>s.y-=410*dt);hostile.forEach(b=>{b.x+=b.vx*dt;b.y+=b.vy*dt});
 for(const s of shots){for(const e of enemies){if(e.alive&&Math.abs(e.x-s.x)<15&&Math.abs(e.y-s.y)<16){e.alive=false;s.y=-100;score+=10;G.status('Score '+score+' · Wave '+wave);break}}}
 if(inv<=0){for(const b of hostile){if(Math.abs(b.x-player)<19&&b.y>445&&b.y<490){b.y=600;lives--;inv=1;if(lives<=0){over=true;G.score('galaga',score);G.status('Ship lost · '+score+' points')}break}}}
 shots=shots.filter(s=>s.y>-10);hostile=hostile.filter(s=>s.y<540);
 if(enemies.every(e=>!e.alive)&&!over){wave++;if(wave>5){over=true;G.score('galaga',score);G.status('All five formations defeated! '+score+' points')}else{build();G.status('Wave '+wave+'/5')}}
 },()=>{
 rect(ctx,0,0,360,520,'#142b2e');for(let i=0;i<36;i++)disc(ctx,(i*71)%360,(i*137)%520,1,'#6d9a9f');
 enemies.forEach(e=>{if(!e.alive)return;ctx.fillStyle=e.diving?'#e6b981':'#81b5a8';ctx.beginPath();ctx.moveTo(e.x,e.y+12);ctx.lineTo(e.x-14,e.y-10);ctx.lineTo(e.x,e.y-3);ctx.lineTo(e.x+14,e.y-10);ctx.closePath();ctx.fill()});
 shots.forEach(s=>rect(ctx,s.x-2,s.y,4,13,'#f0d398'));hostile.forEach(s=>disc(ctx,s.x,s.y,5,'#e38a68'));if(inv===0||Math.floor(inv*10)%2===0){ctx.fillStyle='#d3e8c7';ctx.beginPath();ctx.moveTo(player,451);ctx.lineTo(player-20,494);ctx.lineTo(player+20,494);ctx.closePath();ctx.fill()}text(ctx,'WAVE '+wave+'/5 · SCORE '+score+' · ♥ '+lives)});
 G.status('Drag ship to dodge diving enemies');G.note('Move sideways to dodge enemy fire. Your ship automatically shoots. Clear five attack formations.');return stop});
})();