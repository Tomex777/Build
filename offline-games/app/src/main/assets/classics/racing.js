/* Original, fully offline racing mini-games. No asset fetches. */
(()=>{'use strict';const G=window.G,T=Math.PI*2,rand=(a,b)=>a+Math.random()*(b-a),clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
function stage(){const c=G.el('canvas','arena');c.width=360;c.height=520;Object.assign(c.style,{width:'100%',maxWidth:'410px',aspectRatio:'9/13',borderRadius:'12px',display:'block',margin:'auto',touchAction:'none'});G.board().append(c);return[c,c.getContext('2d')]}
function pos(e,c){const b=c.getBoundingClientRect();return{x:(e.clientX-b.left)*360/b.width,y:(e.clientY-b.top)*520/b.height}}
function rect(ctx,x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(x,y,w,h)}
function circle(ctx,x,y,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.arc(x,y,r,0,T);ctx.fill()}
function text(ctx,s,y,size=19){ctx.fillStyle='#eaf4e6';ctx.font='700 '+size+'px system-ui';ctx.textAlign='center';ctx.fillText(s,180,y)}
function run(tick,draw){let last=0,id=0,stopped=false;function frame(t){if(stopped)return;const dt=last?Math.min((t-last)/1000,.04):.016;last=t;if(!document.hidden)tick(dt);draw();id=requestAnimationFrame(frame)}id=requestAnimationFrame(frame);return()=>{stopped=true;cancelAnimationFrame(id)}}
G.add('drag','Drag Race','Race 402 meters. Shift gears near the green power band to beat the CPU.','Racing',()=>{
 const [c,ctx]=stage();let gear=1,rpm=1200,speed=0,m=0,cpu=0,time=0,done=false,shiftQuality=0;
 const shift=()=>{if(done||gear>=5)return;if(rpm>=5400&&rpm<=7200){shiftQuality=1.2;G.status('Perfect shift!')}else{shiftQuality=-.6;G.status(rpm<5400?'Early shift':'Over-rev shift')}gear++;rpm=Math.max(2600,rpm*.52)};
 G.action('SHIFT GEAR',shift,true);
 const stop=run(dt=>{if(done)return;time+=dt;rpm=clamp(rpm+dt*(1550+gear*280),1000,9300);const efficiency=rpm<4600?.67:rpm<7400?1.0:.73;const acceleration=(15.2/gear+4)*efficiency+(shiftQuality>0?shiftQuality:shiftQuality<0?-1.9:0);shiftQuality*=.92;speed=clamp(speed+acceleration*dt,0,76);m+=speed*dt;cpu+=Math.min(69,time*5.9)*dt;if(m>=402||cpu>=402){done=true;const you=m>=402&&m>=cpu;G.status((you?'You won!':'CPU wins')+' · '+time.toFixed(2)+' s');if(you)G.score('drag',Math.max(1,40-Math.floor(time)))}else G.status('Gear '+gear+' · '+Math.round(speed*3.6)+' km/h · '+Math.floor(m)+'/402m')},()=>{
 rect(ctx,0,0,360,520,'#21392e');rect(ctx,35,0,290,520,'#39403b');for(let y=(time*120)%90-90;y<520;y+=90)rect(ctx,178,y,5,42,'#bfc1a5');
 rect(ctx,125,358,110,82,'#598d81');rect(ctx,137,364,87,33,'#b7d4c5');
 rect(ctx,34,95,292,16,'#546a5b');rect(ctx,34,95,Math.min(292,m/402*292),16,'#8bd5a2');rect(ctx,34,123,292,10,'#546a5b');rect(ctx,34,123,Math.min(292,cpu/402*292),10,'#d9a479');
 text(ctx,'402 METERS',61,24);text(ctx,'GEAR '+gear+' · '+Math.round(rpm)+' RPM',207,20);rect(ctx,39,241,282,23,'#1a2826');rect(ctx,39,241,clamp(rpm/9300*282,0,282),23,rpm>=5400&&rpm<=7200?'#6ebf80':rpm>8000?'#cc665e':'#d2a45d');text(ctx,Math.round(speed*3.6)+' km/h',320,25);if(done)text(ctx,'FINISH',488,23)});
 G.status('Gear 1 · Shift in the green RPM range');G.note('The car accelerates automatically. Shift only when the RPM meter is green.');return stop});
G.add('biketrail','Bike Trail','Jump over rocky obstacles and land cleanly for a higher distance.','Racing',()=>{
 const [c,ctx]=stage();let x=83,y=422,vy=0,ground=422,obstacles=[],spawn=1.1,m=0,dead=false,jumps=0;
 function jump(){if(dead||y<ground-2)return;vy=-430;jumps++}c.addEventListener('pointerdown',jump);
 const stop=run(dt=>{if(dead)return;m+=(190+m*.04)*dt;spawn-=dt;if(spawn<=0){spawn=rand(.85,1.65);obstacles.push({x:385,w:rand(25,46),h:rand(25,58)})}
 vy+=1150*dt;y=Math.min(ground,y+vy*dt);if(y===ground)vy=0;
 obstacles.forEach(o=>{o.x-=(190+m*.04)*dt;if(o.x<x+23&&o.x+o.w>x-22&&y+22>ground-o.h){dead=true;G.status('Crash · '+Math.floor(m)+' meters');G.score('biketrail',Math.floor(m))}});obstacles=obstacles.filter(o=>o.x+o.w>-20);if(!dead)G.status('Distance '+Math.floor(m)+'m · Jumps '+jumps)},()=>{
 rect(ctx,0,0,360,520,'#82b6ad');rect(ctx,0,437,360,83,'#466c4f');rect(ctx,0,458,360,12,'#8a7759');
 obstacles.forEach(o=>{rect(ctx,o.x,ground-o.h+22,o.w,o.h,'#a3886c');rect(ctx,o.x+5,ground-o.h+20,o.w-10,7,'#d6bb93')});
 circle(ctx,x-22,y+22,19,'#1b3f36');circle(ctx,x+23,y+22,19,'#1b3f36');ctx.strokeStyle='#e4ece3';ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(x-22,y+22);ctx.lineTo(x,y-2);ctx.lineTo(x+23,y+22);ctx.lineTo(x-22,y+22);ctx.moveTo(x,y-2);ctx.lineTo(x+10,y-20);ctx.stroke();circle(ctx,x+9,y-29,9,'#eac99d');ctx.fillStyle='#1f4439';ctx.textAlign='center';ctx.font='bold 18px system-ui';ctx.fillText('DISTANCE '+Math.floor(m)+'m',180,50);if(dead)text(ctx,'CRASH',235,30)});
 G.status('Tap to jump');G.note('Touch anywhere to leap over rocks. Jump only when the bike is on the ground.');return stop});
G.add('boat','Boat Race','Steer through twelve floating gates while currents push your boat sideways.','Racing',()=>{
 const [c,ctx]=stage();let boatX=180,gates=[],spawn=.8,passed=0,missed=0,elapsed=0,done=false;
 const steer=e=>boatX=clamp(pos(e,c).x,22,338);c.addEventListener('pointerdown',steer);c.addEventListener('pointermove',steer);
 const stop=run(dt=>{if(done)return;elapsed+=dt;boatX=clamp(boatX+Math.sin(elapsed*2.5)*12*dt,22,338);spawn-=dt;if(spawn<=0&&passed+missed+gates.length<12){spawn=1.25;gates.push({x:rand(82,278),y:-32,tested:false})}
 gates.forEach(g=>{g.y+=200*dt;if(!g.tested&&g.y>408){g.tested=true;if(Math.abs(boatX-g.x)<55)passed++;else missed++;G.status('Passed '+passed+'/12 · Missed '+missed+'/3')}});gates=gates.filter(g=>g.y<550);if(missed>=3||passed+missed>=12){done=true;G.score('boat',passed);G.status('Race finished · '+passed+' gates passed')}},()=>{
 rect(ctx,0,0,360,520,'#286c8b');for(let y=0;y<520;y+=67){ctx.strokeStyle='#448aa0';ctx.beginPath();ctx.moveTo(0,(y+elapsed*40)%520);ctx.bezierCurveTo(95,y+18,210,y-18,360,(y+elapsed*40)%520);ctx.stroke()}
 gates.forEach(g=>{circle(ctx,g.x-57,g.y,12,'#d5a45e');circle(ctx,g.x+57,g.y,12,'#d5a45e');ctx.strokeStyle='#e2e8db';ctx.beginPath();ctx.moveTo(g.x-48,g.y);ctx.lineTo(g.x+48,g.y);ctx.stroke()});
 ctx.fillStyle='#f5e9d5';ctx.beginPath();ctx.moveTo(boatX,382);ctx.lineTo(boatX-22,437);ctx.lineTo(boatX,460);ctx.lineTo(boatX+22,437);ctx.closePath();ctx.fill();rect(ctx,boatX-10,419,20,24,'#c2765c');text(ctx,'GATES '+passed+'/12 · MISS '+missed,49,19)});
 G.status('Steer through each pair of buoys');G.note('Drag left and right. The current is constantly moving, so adjust your line.');return stop});
G.add('drifthunt','Drift Hunt','Slide a drifting car around the circuit and build your drifting combo.','Racing',()=>{
 const [c,ctx]=stage();let car={x:180,y:400,a:-Math.PI/2,vx:0,vy:0},steer=0,score=0,combo=0,elapsed=0,live=true;
 c.addEventListener('pointerdown',e=>steer=pos(e,c).x<180?-1:1);c.addEventListener('pointermove',e=>steer=pos(e,c).x<180?-1:1);c.addEventListener('pointerup',()=>steer=0);c.addEventListener('pointercancel',()=>steer=0);
 const stop=run(dt=>{if(!live)return;elapsed+=dt;const heading={x:Math.cos(car.a),y:Math.sin(car.a)};car.a+=steer*2.3*dt;car.vx=(car.vx+heading.x*175*dt)*Math.pow(.96,dt*60);car.vy=(car.vy+heading.y*175*dt)*Math.pow(.96,dt*60);car.x+=car.vx*dt;car.y+=car.vy*dt;const speed=Math.hypot(car.vx,car.vy),slip=Math.abs(car.vx*Math.sin(car.a)-car.vy*Math.cos(car.a));if(slip>45&&speed>65){combo+=dt;score+=Math.floor(combo*2*dt*60)}else combo=0;
 const distance=Math.hypot((car.x-180)/145,(car.y-260)/195);if(distance<.6||distance>1.22){car.x=180;car.y=425;car.vx=0;car.vy=0;combo=0;G.status('Off track · combo reset')}else G.status('Drift '+score+' · Combo '+combo.toFixed(1)+'s');
 if(elapsed>60){live=false;G.score('drifthunt',score);G.status('60 seconds · '+score+' drift points')}},()=>{
 rect(ctx,0,0,360,520,'#416b45');ctx.fillStyle='#464948';ctx.beginPath();ctx.ellipse(180,260,174,242,0,0,T);ctx.fill();ctx.fillStyle='#416b45';ctx.beginPath();ctx.ellipse(180,260,112,170,0,0,T);ctx.fill();ctx.save();ctx.translate(car.x,car.y);ctx.rotate(car.a+Math.PI/2);rect(ctx,-14,-26,28,52,'#d0a16a');rect(ctx,-10,-20,20,22,'#8ca8a4');ctx.restore();ctx.fillStyle='#eff5e6';ctx.textAlign='center';ctx.font='bold 18px system-ui';ctx.fillText('DRIFT '+score,180,46);ctx.fillText(Math.ceil(Math.max(0,60-elapsed))+' SECONDS',180,71)});
 G.status('Touch left/right to steer');G.note('Hold the left or right half of the track to steer. Slide for a drifting combo; avoid grass.');return stop});
G.add('horse','Horse Derby','Race the CPU over 600 meters. Alternate your left and right strides to keep speed.','Racing',()=>{
 const [c,ctx]=stage();let you=0,cpu=0,stamina=100,elapsed=0,leg=0,mistakes=0,done=false;
 const stride=side=>{if(done)return;if(side===leg){mistakes++;stamina=clamp(stamina-12,0,100);return}leg=side;you+=clamp(stamina/18,1,5);stamina=clamp(stamina-3,0,100)};
 const left=G.action('LEFT STRIDE',()=>stride(1),true);const right=G.action('RIGHT STRIDE',()=>stride(0));
 const stop=run(dt=>{if(done)return;elapsed+=dt;stamina=clamp(stamina+14*dt,0,100);cpu+=19.5*dt;if(cpu>=600||you>=600){done=true;const victory=you>=600&&you>cpu;G.status(victory?'You win the derby!':'CPU wins · '+Math.round(you)+'m');if(victory)G.score('horse',Math.max(1,60-Math.floor(elapsed)))}else G.status('You '+Math.round(you)+'m · CPU '+Math.round(cpu)+'m · Stamina '+Math.round(stamina)+'%')},()=>{
 rect(ctx,0,0,360,520,'#326047');for(const y of [140,260])rect(ctx,20,y,320,78,'#987c5f');
 for(const [progress,lane,color] of [[you,175,'#e7c18a'],[cpu,295,'#cda39f']]){const x=30+clamp(progress/600,0,1)*290;circle(ctx,x,lane,21,color);rect(ctx,x-23,lane+13,46,7,'#2a4e3b');rect(ctx,x-8,lane-32,17,25,'#f1ded1')};
 text(ctx,'600m DERBY',60,24);text(ctx,'STAMINA '+Math.round(stamina)+'%',400,20);text(ctx,'YOU '+Math.round(you)+'m   CPU '+Math.round(cpu)+'m',460,17)});
 G.status('Alternate LEFT and RIGHT');G.note('Alternating taps make faster, stronger strides. Repeating the same leg drains stamina.');return stop});
})();