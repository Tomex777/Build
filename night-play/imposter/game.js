/* NIGHT / IMPOSTER — Illustrated 2.5D playable prototype.
 * Canvas game and local simulated operatives. No online matchmaking or WhatsApp functionality is claimed.
 */
(()=>{
'use strict';
const $=id=>document.getElementById(id),canvas=$('scene'),ctx=canvas.getContext('2d');
const WORLD={w:1770,h:1100},TAU=Math.PI*2;
const palettes=['#97ecd3','#ffd17b','#ee95ba','#b5a1ff','#7ccbf4','#ff977d','#c0e484'];
const names=['ASTRA','BECK','LYRA','NOVA','PIP','SOL'];
const panels=[{id:'engine',x:293,y:514,title:'REACTOR',type:'sabotage',color:'#ff9e77'},
 {id:'transmission',x:1453,y:432,title:'TRANSMITTER',type:'task',color:'#7de9ed'},
 {id:'navigation',x:773,y:420,title:'STAR CHART',type:'task',color:'#b5c6ff'},
 {id:'specimens',x:991,y:894,title:'RESEARCH',type:'task',color:'#c6f4a2'},
 {id:'archive',x:651,y:372,title:'SECURITY CAM',type:'evidence',color:'#ffe4a4'}];
const props=[{x:356,y:692,r:45},{x:638,y:659,r:34},{x:1059,y:587,r:42},{x:1290,y:618,r:36},{x:887,y:918,r:32},{x:1616,y:599,r:38}];
const stations=[{name:'THE REACTOR',x:70,y:252,w:478,h:552,c:'#30646f'},{name:'THE ATRIUM',x:495,y:160,w:728,h:655,c:'#395d7c'},{name:'COMMUNICATIONS',x:1179,y:206,w:530,h:570,c:'#436277'},{name:'RESEARCH',x:722,y:768,w:574,h:296,c:'#447b75'}];
const S={mode:'lobby',name:'HEX',role:'crew',room:'',player:null,bots:[],bodies:[],taskCount:0,meetings:0,killCount:0,time:245,kcd:0,warning:0,aiTimer:0,discussion:[],selected:null,currentTask:null,source:null,solved:new Set(),shade:0,camera:{x:0,y:0},frame:0,last:0,pointer:null,joy:{x:0,y:0},keys:new Set(),goal:null,dead:false,overlayBack:'lobby',noticeT:0,elapsed:0,showLabels:true,emergencyLeft:2,scene:'',pendingMeeting:null,gameWin:false};
function rand(a,b){return a+Math.random()*(b-a)}
function choose(arr){return arr[Math.floor(Math.random()*arr.length)]}
function clamp(n,a,b){return Math.min(b,Math.max(a,n))}
function distance(a,b){return Math.hypot(a.x-b.x,a.y-b.y)}
function escapeHTML(v){return String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
function rect(x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(x,y,w,h)}
function round(x,y,w,h,r,fill,stroke,lw=1){ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill()}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lw;ctx.stroke()}}
function ellipse(x,y,rx,ry,fill){ctx.beginPath();ctx.ellipse(x,y,rx,ry,0,0,TAU);ctx.fillStyle=fill;ctx.fill()}
function line(points,stroke,w=1){ctx.beginPath();ctx.moveTo(...points[0]);for(let i=1;i<points.length;i++)ctx.lineTo(...points[i]);ctx.strokeStyle=stroke;ctx.lineWidth=w;ctx.lineCap='round';ctx.lineJoin='round';ctx.stroke()}
function polygon(points,fill,stroke,width=1){ctx.beginPath();ctx.moveTo(...points[0]);for(let i=1;i<points.length;i++)ctx.lineTo(...points[i]);ctx.closePath();if(fill){ctx.fillStyle=fill;ctx.fill()}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=width;ctx.stroke()}}
function text(str,x,y,size=12,color='#e8f0f6',weight=800,spacing=0){ctx.save();ctx.fillStyle=color;ctx.font=`${weight} ${size}px Inter,system-ui,sans-serif`;ctx.textAlign='center';ctx.textBaseline='middle';if(spacing&&'letterSpacing' in ctx)ctx.letterSpacing=spacing+'px';ctx.fillText(str,x,y);ctx.restore()}
function tint(hex,n){const v=parseInt(hex.slice(1),16),r=clamp((v>>16)+n,0,255),g=clamp(((v>>8)&255)+n,0,255),b=clamp((v&255)+n,0,255);return 'rgb('+r+','+g+','+b+')'}
function gradient(x,y,r,col){const g=ctx.createRadialGradient(x,y,1,x,y,r);g.addColorStop(0,col);g.addColorStop(1,'#0000');return g}
function toast(msg){$('status').textContent=msg;$('status').classList.add('show');clearTimeout(S.noticeT);S.noticeT=setTimeout(()=>$('status').classList.remove('show'),3100)}
function hideScreens(){['lobby','how','taskScreen','evidenceScreen','meetingScreen','endScreen'].forEach(id=>$(id).hidden=true)}
function reveal(id){hideScreens();$(id).hidden=false}
function resize(){const r=canvas.getBoundingClientRect(),d=Math.min(devicePixelRatio||1,2);canvas.width=Math.round(r.width*d);canvas.height=Math.round(r.height*d);ctx.setTransform(d,0,0,d,0,0);S.w=r.width;S.h=r.height}
window.addEventListener('resize',resize);
function drawBackground(){
 const w=S.w,h=S.h;
 rect(0,0,w,h,'#071321');
 const g=ctx.createRadialGradient(w*.5,h*.45,15,w*.5,h*.5,Math.max(w,h)*.8);g.addColorStop(0,'#24485a');g.addColorStop(.7,'#0b1f31');g.addColorStop(1,'#070f20');ctx.fillStyle=g;ctx.fillRect(0,0,w,h);
 // Subtle starfield is outside the illustrated station silhouette.
 for(let i=0;i<80;i++){const x=((i*793+181)%1729)/1729*w,y=((i*521+99)%1187)/1187*h;ellipse(x,y,i%7?1:.65,i%7?1:.65,i%3?'#ffffff16':'#a9defb55')}
}
function drawStation(){
 // Intentionally illustrated *top-down 2.5D*, not isometric wireframe diamonds.
 ctx.save();ctx.shadowBlur=42;ctx.shadowColor='#000a';round(40,154,1688,898,86,'#080d1b');ctx.restore();
 round(48,135,1674,923,76,'#132d3e','#416072',11);
 round(65,157,1640,870,62,'#223e50','#76a5a5',4);
 // Strong chunky, bevelled walls and coloured floor spaces.
 stations.forEach(room=>{
  round(room.x+3,room.y+17,room.w,room.h,38,'#102a36');
  round(room.x,room.y,room.w,room.h,34,room.c,'#85b1b3',4);
  const floor=ctx.createLinearGradient(room.x,room.y,room.x+room.w,room.y+room.h);
  floor.addColorStop(0,'#ffffff10');floor.addColorStop(.5,'#00000004');floor.addColorStop(1,'#02192739');round(room.x+11,room.y+10,room.w-22,room.h-22,29,floor);
  ctx.save();ctx.beginPath();ctx.roundRect(room.x+15,room.y+12,room.w-30,room.h-24,28);ctx.clip();
  for(let x=Math.floor(room.x/68)*68;x<room.x+room.w;x+=68)line([[x,room.y],[x,room.y+room.h]],'#ffffff0a',1.7);
  for(let y=Math.floor(room.y/68)*68;y<room.y+room.h;y+=68)line([[room.x,y],[room.x+room.w,y]],'#ffffff0a',1.7);
  for(let x=room.x+48;x<room.x+room.w;x+=143)for(let y=room.y+50;y<room.y+room.h;y+=134){round(x,y,18,5,2,'#bbdfdf12');round(x+6,y+10,4,13,2,'#bbdfdf12')}
  ctx.restore();
 });
 // Dark partitions with wide glowing doorways allow natural exploration.
 function wall(x1,y1,x2,y2){line([[x1,y1+11],[x2,y2+11]],'#092330',24);line([[x1,y1],[x2,y2]],'#102b3b',20);line([[x1,y1-6],[x2,y2-6]],'#91acaf',4)}
 wall(534,270,534,420);wall(534,603,534,777);
 wall(1187,255,1187,391);wall(1187,549,1187,733);
 wall(764,779,920,779);wall(1100,779,1250,779);
 // Door indicators and floor light strips.
 for(const [x,y,r] of [[535,511,1],[1187,469,1],[1006,779,0]]){
  if(r){line([[x-11,y-77],[x-11,y+76]],'#67ffb166',5);line([[x+13,y-77],[x+13,y+76]],'#67ffb166',5)}
  else {line([[x-77,y-9],[x+77,y-9]],'#71dfce8a',5);line([[x-77,y+14],[x+77,y+14]],'#71dfce8a',5)}
 }
 // Room-specific floor graphics and floor labels, including curved motif.
 function floorLabel(str,x,y){text(str,x,y,22,'#aedfdc59',950,3)}
 floorLabel('ECHO // 09',857,270);floorLabel('REACTOR',288,353);floorLabel('COMM LINK',1441,306);floorLabel('BIO LAB',1005,1005);
 // Atrium central hub, couches and circular lighted meeting table.
 ellipse(887,515,193,123,'#203945');ellipse(887,510,184,118,'#4a7b85');ellipse(887,504,158,94,'#345667');
 ctx.save();ctx.shadowBlur=24;ctx.shadowColor='#97e9db88';ctx.strokeStyle='#99ded066';ctx.lineWidth=7;ctx.beginPath();ctx.ellipse(887,502,154,90,0,0,TAU);ctx.stroke();ctx.restore();
 ellipse(887,512,79,51,'#172a3a');ellipse(887,505,77,47,'#c3c5b2');ellipse(887,501,62,35,'#e9d7aa');
 round(867,476,38,41,11,'#263d49');ellipse(887,488,12,12,'#a1e9c7');text('MEET',887,545,13,'#102332',950,1);
 for(let i=0;i<7;i++){const a=i*TAU/7;const x=887+Math.cos(a)*125,y=507+Math.sin(a)*78;ellipse(x,y+10,22,10,'#06192377');round(x-21,y-14,42,28,11,i%2?'#b5c59e':'#b5a48b','#50636a',3)}
 // Reactor tanks, conduits and visual hazards.
 round(118,408,110,315,34,'#1f4b5b','#74adb2',5);round(129,421,88,289,31,'#253d4f');
 for(let y=451;y<700;y+=57){round(137,y,73,25,9,'#75b2ab','#192d3c',3);round(144,y+6,58,12,5,'#bde8c6')}
 round(423,415,73,306,17,'#1b3743','#769497',4);for(let y=450;y<698;y+=46)round(434,y,50,20,6,'#435b67','#b2c2ba',2);
 for(const [x,y] of [[233,405],[308,406],[380,404],[240,751],[375,750]]){polygon([[x,y],[x+17,y-9],[x+37,y],[x+17,y+10]],'#e5bd75','#3f5865',3)}
 // Communications command consoles, rails and monitors.
 round(1245,342,377,102,25,'#0c2638','#71949f',5);round(1265,334,338,72,16,'#284b61','#7dd0d5',3);
 for(let i=0;i<4;i++){round(1275+i*82,341,68,53,8,'#102739','#588391',2);round(1282+i*82,350,54,33,5,i%2?'#70b8d2':'#5caead');for(let j=0;j<3;j++)line([[1289+i*82,359+j*8],[1326+i*82,354+j*8]],'#ceeff266',2)}
 for(let i=0;i<3;i++){round(1265+i*112,675,76,50,10,'#122f3c','#759ba2',4);round(1274+i*112,681,57,29,4,'#b2c8b5')}
 // Research lush greenhouse panels + glass enclosure.
 round(780,831,155,148,18,'#174239','#87bfa5',5);round(793,841,128,116,12,'#255e4d','#b4d8ae',3);
 for(let i=0;i<9;i++){const x=803+(i%3)*42,y=851+Math.floor(i/3)*36;ellipse(x,y,19,10,i%2?'#9be58b':'#3e9e8d');ellipse(x+6,y-5,9,16,'#69bf91')}
 round(1113,834,123,153,17,'#164058','#9baabb',4);for(let i=0;i<4;i++){round(1125,853+i*27,99,16,5,'#4f8d8d','#b0e5c2',1)}
 // Outside hull chunky edge and small light modules.
 for(let x=158;x<1650;x+=200){round(x,146,73,15,6,'#39566a');round(x+6,144,60,6,3,x%400?'#8ee5bd':'#9cdae7')}
 for(let y=304;y<948;y+=146){round(56,y,16,67,5,'#2c5260');round(52,y+10,7,42,2,'#b9efd1')}
 // Interactive object lights and world fixtures.
 panels.forEach(p=>drawConsole(p));
}
function drawConsole(p){
 const done=S.solved.has(p.id),near=S.player&&distance(S.player,p)<100;
 ctx.save();ctx.translate(p.x,p.y);
 if(near&&S.mode==='playing'){ctx.shadowBlur=22;ctx.shadowColor='#d3ff9b';ctx.strokeStyle='#d4ffaf';ctx.lineWidth=4;ctx.beginPath();ctx.ellipse(0,25,68,26,0,0,TAU);ctx.stroke();ctx.shadowBlur=0}
 ellipse(0,38,68,20,'#05192788');
 round(-58,-32,115,68,16,'#153546','#071b2b',5);
 polygon([[-54,-32],[23,-59],[61,-40],[-14,-6]],'#466a74','#91b0ae',3);
 round(-38,-24,81,35,9,done?'#55866b':p.type==='sabotage'?'#7e514f':'#285e68','#92cfc8',2);
 ctx.save();ctx.shadowColor=done?'#9affac':p.color;ctx.shadowBlur=16;round(-28,-18,58,22,5,done?'#8feba5':p.color);ctx.restore();
 for(let i=0;i<4;i++)round(-42+i*28,21,14,4,2,i%2?'#98ded1':'#d9cc95');
 text(done?'RESTORED':p.title,0,-77,13,done?'#a6f5b4':'#e2f6ef',940,1.1);
 if(near&&S.mode==='playing'){round(-54,-112,108,25,8,'#0f2531e8','#8eb3b9',1);text('E  •  INTERACT',0,-100,11,'#e9ffd0',900)}
 ctx.restore();
}
function chibi(a,now){
 const walking=(Math.abs(a.vx||0)+Math.abs(a.vy||0))>.1;
 const step=walking?Math.sin(now*.014+(a.seed||0))*4:0;
 ctx.save();ctx.translate(a.x,a.y);
 // Soft grounding shadow and bounce produce visual 2.5D volume rather than 3D polygons.
 ellipse(1,23,25,11,'#05111ab0');ctx.translate(0,walking?-Math.abs(step)*.32:Math.sin(now*.0018+a.seed)*.8);
 if(a.dead){ctx.globalAlpha=.38}
 // backpack and jointed feet
 round(-21,-7,15,26,7,tint(a.color,-58),'#132b32',3);
 round(-18,15+step,16,17,7,'#263b48','#172b35',3);round(4,15-step,16,17,7,'#263b48','#172b35',3);
 // bright customizable parka outfit
 round(-19,-23,41,48,14,tint(a.color,-24),'#152e3d',3);polygon([[-16,-11],[20,-9],[16,5],[-13,5]],a.color);
 line([[0,-8],[0,14]],'#344f5e',3);round(-26,-15,11,33,6,tint(a.color,13),'#2e4a54',2);round(17,-14,12,34,6,tint(a.color,13),'#2e4a54',2);
 // neck scarf / collar, round expressive face and glossy hair/helmet
 round(-12,-32,26,13,5,'#e9f5e1');
 ellipse(0,-43,22,23,a.skin||'#f0c39d');
 // hair in hood, distinct from Among Us astronaut design
 ctx.beginPath();ctx.arc(0,-47,22,Math.PI*1.05,Math.PI*1.99);ctx.lineTo(23,-43);ctx.quadraticCurveTo(10,-53,0,-52);ctx.quadraticCurveTo(-13,-42,-22,-43);ctx.closePath();ctx.fillStyle=a.hair||'#23394b';ctx.fill();
 ctx.fillStyle='#243844';ellipse(-8,-41,1.8,2.6,'#283d47');ellipse(9,-41,1.8,2.6,'#283d47');
 line([[-4,-34],[4,-34]],'#ae7d79',1.7);
 // soft reflective outline on dome
 ctx.strokeStyle='#ffffff52';ctx.lineWidth=2;ctx.beginPath();ctx.arc(-6,-49,16,Math.PI*1.06,Math.PI*1.45);ctx.stroke();
 ctx.restore();
 if(!a.dead){
  round(a.x-38,a.y-89,76,19,7,'#0d2336d8','#81bbc666',1);
  text(a.name,a.x,a.y-79,10,a===S.player?'#dffdb8':'#e6f4f2',880,.3);
 }
}
function drawBody(b){ctx.save();ctx.translate(b.x,b.y);ellipse(0,8,31,13,'#051421aa');ctx.rotate(-.48);round(-26,-10,52,21,11,tint(b.color,-25),'#182834',3);ellipse(-32,-1,14,14,b.skin||'#f0c39d');ctx.restore();ctx.strokeStyle='#ff9ca4';ctx.lineWidth=2;ctx.beginPath();ctx.ellipse(b.x,b.y,44,24,0,0,TAU);ctx.stroke();text('REPORT',b.x,b.y-39,10,'#ffafb6',930,1)}
function drawFurniture(){
 // Floor lighting, street-style signage and crates embedded inside illustrated rooms.
 props.forEach((p,i)=>{
  ellipse(p.x,p.y+16,p.r+7,19,'#061b2699');
  if(i===0||i===3||i===5){round(p.x-p.r,p.y-p.r*.7,p.r*2,p.r*1.3,12,'#274456','#6f97a2',4);round(p.x-p.r+8,p.y-p.r*.7-12,p.r*2-16,24,7,'#62858b','#94bdbb',2);line([[p.x-p.r+16,p.y-5],[p.x+p.r-16,p.y-5]],'#d0e2bd',4)}
  else {round(p.x-p.r,p.y-p.r*.75,p.r*2,p.r*1.5,18,i===4?'#5a7d72':'#436b78','#a3c5be',4);round(p.x-p.r+9,p.y-p.r*.75-10,p.r*2-18,20,5,'#b9d6c3')}
 });
 // Glass and foliage give scale to the environment.
 for(const [x,y,s] of [[1088,292,1],[621,329,.75],[1520,585,1],[694,721,.85]]){
  ctx.save();ctx.translate(x,y);ctx.scale(s,s);ellipse(0,20,20,11,'#092d37');round(-16,2,32,20,7,'#c9bd9e');for(let i=-1;i<=1;i++){ctx.strokeStyle='#386c61';ctx.lineWidth=5;ctx.beginPath();ctx.moveTo(0,4);ctx.quadraticCurveTo(21*i,-27,13*i,-48+Math.abs(i)*8);ctx.stroke();ellipse(13*i,-45+Math.abs(i)*8,8,14,i?'#79c9a0':'#b0df9d')}ctx.restore()
 }
}
function drawWorld(now){
 ctx.save();
 // Whole scene is an illustrated overhead game, with chibi characters and stacked room depth.
 drawBackground();
 const scale=Math.min(Math.max(S.w/985,.58),1.09)*(S.w<670?.77:1);
 // Auto-fit a playable 900px width on mobile while keeping enough detail to see characters.
 const zoom=S.w<670?Math.max(.68,Math.min(.85,S.w/475)):(S.w>1300?1.02:.93);
 S.zoom=zoom;
 const targetX=S.player?S.player.x:884,targetY=S.player?S.player.y:520;
 const tx=S.w*.5-targetX*zoom,ty=S.h*.53-targetY*zoom;
 if(!S.camera.ready){S.camera.x=tx;S.camera.y=ty;S.camera.ready=true}
 S.camera.x+=(tx-S.camera.x)*.105;S.camera.y+=(ty-S.camera.y)*.105;
 ctx.translate(S.camera.x,S.camera.y);ctx.scale(zoom,zoom);
 drawStation();drawFurniture();
 if(S.path?.length){ctx.save();ctx.strokeStyle='#d4f8ba9a';ctx.lineWidth=3;ctx.setLineDash([4,14]);ctx.beginPath();ctx.moveTo(S.player.x,S.player.y+6);for(const p of S.path)ctx.lineTo(p.x,p.y+6);ctx.stroke();ctx.setLineDash([]);const p=S.path[S.path.length-1];ellipse(p.x,p.y,9,4,'#b9ffc6');ctx.restore()}
 const all=[...S.bodies.map(e=>({...e,isBody:true})),...S.bots.filter(a=>!a.dead),...(S.player?[S.player]:[])];
 all.sort((a,b)=>a.y-b.y);for(const a of all){if(a.isBody)drawBody(a);else chibi(a,now)}
 if(S.flashEffect&&S.flashEffect.t>0){const fx=S.flashEffect,age=1-fx.t/.7;ctx.save();ctx.globalAlpha=Math.max(0,1-age);ctx.strokeStyle='#ffb3a1';ctx.lineWidth=7-4*age;ctx.beginPath();ctx.ellipse(fx.x,fx.y,35+90*age,17+35*age,0,0,TAU);ctx.stroke();ctx.strokeStyle='#fff2d1';ctx.lineWidth=2;ctx.beginPath();ctx.ellipse(fx.x,fx.y,22+52*age,13+28*age,0,0,TAU);ctx.stroke();ctx.restore()}
 ctx.restore();
 if(S.mode==='playing'&&S.warning>0){ctx.save();ctx.fillStyle='#ec38411c';ctx.fillRect(0,0,S.w,S.h);ctx.restore()}
 // Vignette + film grain made with gradients rather than a solid black overlay.
 const rad=ctx.createRadialGradient(S.w*.5,S.h*.44,S.h*.28,S.w*.5,S.h*.45,Math.max(S.w,S.h)*.77);
 rad.addColorStop(0,'#0000');rad.addColorStop(1,'#02091666');ctx.fillStyle=rad;ctx.fillRect(0,0,S.w,S.h);
}
function validMove(x,y,r=22){
 if(x<89+r||x>1681-r||y<189+r||y>1008-r)return false;
 // Ground layout consists of 4 intersecting large rooms/corridors.
 const inFloor=stations.some(room=>x>room.x+15&&x<room.x+room.w-15&&y>room.y+15&&y<room.y+room.h-15);
 if(!inFloor)return false;
 for(const p of props)if(Math.hypot(x-p.x,y-p.y)<p.r*.65+r)return false;
 // Solid-partition collision except where a doorway exists.
 if(Math.abs(x-535)<r+7&&((y>266&&y<420)||(y>602&&y<775)))return false;
 if(Math.abs(x-1188)<r+7&&((y>252&&y<386)||(y>551&&y<729)))return false;
 if(Math.abs(y-779)<r+7&&((x>766&&x<914)||(x>1100&&x<1250)))return false;
 return true
}
function changePosition(entity,x,y){
 if(validMove(x,entity.y))entity.x=x;
 if(validMove(entity.x,y))entity.y=y;
}
function placeOf(){const p=S.player;if(!p)return'THE ATRIUM';if(p.x<535)return'THE REACTOR';if(p.x>1186)return'COMMUNICATIONS';if(p.y>784)return'RESEARCH LAB';return'THE ATRIUM'}
function nearest(targets,range){if(!S.player)return null;return targets.filter(x=>!x.dead&&distance(x,S.player)<range).sort((a,b)=>distance(a,S.player)-distance(b,S.player))[0]||null}
function updateUI(){
 const alive=S.bots.filter(a=>!a.dead).length+(!S.player?.dead?1:0);
 $('place').innerHTML='<span class="signal"></span> '+placeOf();
 $('time').textContent=String(Math.max(0,Math.floor(S.time/60))).padStart(2,'0')+':'+String(Math.max(0,Math.floor(S.time%60))).padStart(2,'0');
 const nearPanel=nearest(panels,91),nearBody=nearest(S.bodies,80),nearBot=nearest(S.bots,78);
 $('use').classList.toggle('ready',Boolean(nearPanel));$('report').classList.toggle('ready',Boolean(nearBody));$('kill').classList.toggle('ready',Boolean(nearBot)&&S.kcd<=0);
 $('kill').disabled=S.kcd>0||!nearBot;
 const old=$('kill').querySelector('.cooldown');if(old)old.remove();if(S.kcd>.2&&!$('kill').hidden){const b=document.createElement('span');b.className='cooldown';b.textContent=Math.ceil(S.kcd)+'s';$('kill').appendChild(b)}
 $('brief').textContent=S.mode==='playing'?(S.role==='imposter'?'CLASSIFIED • KEEP YOUR COVER  /  '+alive+' ALIVE':'INVESTIGATOR • '+S.taskCount+'/3 SIGNALS RESTORED  /  '+alive+' ALIVE'):'LOCAL PRACTICE ROOM';
 $('progress').innerHTML=[0,1,2].map(i=>'<span class="'+(i<(S.role==='crew'?S.taskCount:S.killCount)?'complete':'')+'"></span>').join('')+'<b>'+(S.role==='crew'?S.taskCount:S.killCount)+'/3</b>';
}
function roleSet(role){S.role=role;document.querySelectorAll('.roles button').forEach(btn=>btn.classList.toggle('chosen',btn.dataset.role===role))}
document.querySelectorAll('.roles button').forEach(btn=>btn.addEventListener('click',()=>roleSet(btn.dataset.role)));
$('create').onclick=()=>{S.room=Math.random().toString(36).slice(2,8).toUpperCase().padEnd(6,'9');$('roomcode').textContent=S.room;$('lobbyRoom').hidden=false;toast('Local demo lobby created.')};
$('joinToggle').onclick=()=>{$('joinSection').hidden=!$('joinSection').hidden};
$('tryJoin').onclick=()=>{$('joinInfo').textContent=S.room&&$('joincode').value.toUpperCase()===S.room?'Found your local room. Choose a role and start.':'This preview cannot join another device. Live room codes need a multiplayer server.';if(S.room&&$('joincode').value.toUpperCase()===S.room)$('lobbyRoom').hidden=false};
$('howBtn').onclick=()=>{S.overlayBack='lobby';reveal('how')};
$('howClose').onclick=()=>{if(S.overlayBack==='pause'){hideScreens();S.mode='playing';S.last=performance.now()}else reveal('lobby')};
$('pauseBtn').onclick=()=>{if(S.mode==='playing'){S.mode='pause';S.overlayBack='pause';reveal('how')}else if(S.mode==='pause'){S.mode='playing';hideScreens()}};
function start(){
 S.name=($('name').value.trim().slice(0,16)||'AGENT').toUpperCase();
 S.player={name:S.name,x:891,y:643,vx:0,vy:0,color:'#97ecd3',hair:'#324c49',skin:'#eed0ad',seed:11,dead:false};
 const slots=[[754,609],[1050,433],[1314,494],[1122,688],[700,489],[958,887]];
 S.bots=names.map((name,i)=>({name,x:slots[i][0],y:slots[i][1],vx:0,vy:0,color:palettes[i+1],hair:['#263742','#5e4548','#38455c','#393e46','#695145','#2e504c'][i],skin:['#d6a57e','#f1ca9d','#ba806a','#e6ad8d','#f5d0ad','#af735f'][i],seed:i*1.7,dead:false,imposter:S.role==='crew'&&i===3,walk:0,target:null}));
 S.bodies=[];S.taskCount=0;S.killCount=0;S.path=[];S.flashEffect=null;S.meetings=0;S.time=245;S.kcd=0;S.warning=0;S.aiTimer=0;S.elapsed=0;S.solved.clear();S.emergencyLeft=2;S.dead=false;S.discussion=[];S.evidenceReads=0;S.goal=null;S.camera.ready=false;S.selected=null;
 $('missionTitle').textContent=S.role==='crew'?'RESTORE THREE SYSTEMS':'STAY ABOVE SUSPICION';
 $('missionBody').textContent=S.role==='crew'?'Visit the Star Chart, Transmitter and Research Lab. Match the coloured wires.':'Take down operatives, fake repairs and sabotage the Reactor. Survive the meetings.';
 $('kill').hidden=S.role!=='imposter';$('bottomhud').hidden=false;S.mode='playing';hideScreens();updateUI();toast(S.role==='crew'?'INVESTIGATOR — Repair the station.':'IMPOSTER — Your identity is classified.');
}
$('start').onclick=start;
function use(){if(S.mode!=='playing'||S.dead)return;const p=nearest(panels,93);if(!p){toast('Move closer to a glowing console.');return}
 if(p.type==='evidence'){
  S.evidenceReads++;S.mode='evidence';
  $('evidenceContent').innerHTML=S.role==='crew'?'<b>OBSERVATION '+S.evidenceReads+'</b><br>UNAUTHORIZED ACCESS: 23:41<br>ACTIVITY SEEN: NOVA, BECK, SOL<br>FEED RELIABILITY: PARTIAL'+(S.evidenceReads>1?'<br><br><b>ADDITIONAL RECORD:</b> NOVA made an unscheduled visit to restricted storage. Investigate before accusing.':'<br><br>Return to the terminal later for a second log.'): '<b>ARCHIVE COMPROMISED</b><br>Recent access records are incomplete. You may use this ambiguity to mislead the crew.';
  reveal('evidenceScreen');return
 }
 if(p.type==='sabotage'){
  if(S.role==='imposter'){S.warning=15;toast('REACTOR SABOTAGED — Warning lights activated!')}else{S.warning=0;toast('Reactor inspected. No outstanding faults.')}return
 }
 if(S.solved.has(p.id)){toast('System already restored.');return}
 S.currentTask=p;S.source=null;reveal('taskScreen');S.mode='task';$('taskName').textContent=p.title;$('taskDescription').textContent='Match each colour to the corresponding receiver. The receiver positions are shuffled.';drawWires();
}
const colors=[['CORAL','#e99391'],['MINT','#70bba0'],['BLUE','#79b4ee']];
function drawWires(){const left=$('leftwires'),right=$('rightwires');left.innerHTML='';right.innerHTML='';S.wireOrder=[1,2,0];S.wireMatches=new Set();
 colors.forEach(([name,color],i)=>{const b=document.createElement('button');b.className='wirebtn';b.style.setProperty('--wire',color);b.innerHTML='<span class="dot"></span>'+name;b.dataset.wire=i;b.onclick=()=>{if(S.wireMatches.has(i))return;S.source=i;left.querySelectorAll('button').forEach(x=>x.classList.toggle('chosen',x===b));$('taskFeedback').textContent='Now connect '+name+' to its receiver.'};left.appendChild(b)});
 S.wireOrder.forEach(i=>{const b=document.createElement('button');b.className='wirebtn';b.style.setProperty('--wire',colors[i][1]);b.innerHTML='<span class="dot"></span>PORT '+(right.children.length+1);b.dataset.wire=i;b.onclick=()=>{
 if(S.source===null){$('taskFeedback').textContent='Select a coloured source first.';return}
 if(S.source!==i){$('taskFeedback').textContent='Wrong port. Follow the colour!';return}
 S.wireMatches.add(i);right.querySelectorAll('button').forEach(x=>{if(Number(x.dataset.wire)===i)x.classList.add('matched')});left.querySelectorAll('button').forEach(x=>{if(Number(x.dataset.wire)===i)x.classList.add('matched')});S.source=null;$('taskFeedback').textContent=S.wireMatches.size+'/3 circuits connected.';
 if(S.wireMatches.size===3){S.solved.add(S.currentTask.id);S.taskCount++;setTimeout(()=>{if(S.mode==='task'){hideScreens();S.mode='playing';toast(S.currentTask.title+' — SYSTEM RESTORED');updateUI();if(S.taskCount===3)finish(true,'All three signals are repaired. The crew secured Echo-9.')}},400)}
 };right.appendChild(b)});
 $('taskFeedback').textContent='Choose a colour on the left, then its matching receiver.';
}
$('taskClose').onclick=()=>{if(S.mode==='task'){hideScreens();S.mode='playing'}};
$('evidenceClose').onclick=()=>{if(S.mode==='evidence'){hideScreens();S.mode='playing'}};
function kill(){if(S.mode!=='playing'||S.role!=='imposter'||S.dead)return;if(S.kcd>0){toast('Ability recharging: '+Math.ceil(S.kcd)+'s');return}const n=nearest(S.bots,82);if(!n){toast('An operative must be within reach.');return}
 n.dead=true;S.killCount++;S.kcd=12;S.flashEffect={x:n.x,y:n.y,t:.7};S.bodies.push({name:n.name,color:n.color,x:n.x,y:n.y,skin:n.skin,dead:false});toast(n.name+' ELIMINATED — Find an alibi.');S.warning=Math.max(S.warning,2);
 if(S.killCount>=3){finish(true,'Three operatives eliminated. Nobody stopped your mission.');return}updateUI();
}
function report(){if(S.mode!=='playing'||S.dead)return;const body=nearest(S.bodies,82);if(!body){toast('No evidence in range to report.');return}S.bodies.splice(S.bodies.indexOf(body),1);meeting(S.name+' reported '+body.name+' down in '+placeOf()+'.')}
function say(name,phrase){const item=document.createElement('p');item.className='talk';item.innerHTML='<b>'+escapeHTML(name)+':</b> '+escapeHTML(phrase);$('dialogue').appendChild(item);$('dialogue').scrollTop=$('dialogue').scrollHeight}
function meeting(reason){if(S.mode!=='playing'||S.dead)return;if(S.meetings>=3){toast('No meeting calls remaining.');return}S.meetings++;S.mode='meeting';S.selected=null;$('cast').disabled=true;$('meetingReason').textContent=reason;$('dialogue').innerHTML='';
 const active=S.bots.filter(a=>!a.dead);let guesses=['I was restoring the transmitter.','I did not see anything unusual.','Someone was near the reactor.','Can we check the research wing?','We need more than guesses.'];
 say('SYSTEM','Emergency session. All players are gathered at the atrium.');
 active.slice(0,4).forEach((a,i)=>say(a.name,guesses[(i+S.meetings)%guesses.length]));
 if(S.role==='imposter'&&S.killCount>0)say(choose(active)?.name||'SYSTEM','I saw movement near the last reported location.');
 $('votes').innerHTML='';active.forEach(a=>{const b=document.createElement('button');b.innerHTML='<span class="swatch" style="--color:'+a.color+'"></span>'+escapeHTML(a.name);b.onclick=()=>{S.selected=a.name;$('votes').querySelectorAll('button').forEach(x=>x.classList.toggle('selected',x===b));$('cast').disabled=false};$('votes').appendChild(b)});
 reveal('meetingScreen');
}
$('quickTalk').addEventListener('click',e=>{const b=e.target.closest('button');if(!b||S.mode!=='meeting')return;say(S.name,b.dataset.talk);const answer=choose(['I was near the transmitter.','I think the timings do not add up.','That is not enough to vote someone out.','Where did you see that happen?']);say(choose(S.bots.filter(a=>!a.dead))?.name||'SYSTEM',answer)});
function resolveVote(skip){if(S.mode!=='meeting')return;
 const target=S.bots.find(a=>a.name===S.selected&&!a.dead);
 const active=S.bots.filter(a=>!a.dead),votePool=[S.player,...active];const votes=new Map();
 function add(name){votes.set(name,(votes.get(name)||0)+1)}
 if(!skip&&target)add(target.name);
 active.forEach(a=>{
  const options=votePool.filter(o=>o!==a);
  if(S.role==='imposter'&&S.killCount>0&&Math.random()<.2+S.killCount*.12)add(S.name);
  else if(S.role==='crew'&&a.imposter&&Math.random()<.55)add(S.name);
  else if(S.role==='crew'&&Math.random()<.48){const i=active.find(x=>x.imposter);add(i?i.name:choose(options).name)}
  else add(choose(options).name);
 });
 let winner=null,best=0,top=0;votes.forEach((v,k)=>{if(v>best){best=v;winner=k;top=1}else if(v===best)top++});if(top>1||best<2)winner=null;
 hideScreens();S.mode='playing';S.goal=null;S.player.x=889;S.player.y=605;
 if(winner===S.name){finish(false,'The crew voted you out of Echo-9. Your disguise was uncovered.');return}
 if(winner){const b=S.bots.find(x=>x.name===winner);if(b){b.dead=true;if(S.role==='crew'&&b.imposter){finish(true,'The hidden impostor was identified and removed from the station.');return}toast(winner+' EJECTED — '+(S.role==='imposter'?'Your cover still holds.':'Investigation continues.'))}}
 else toast('TIED VOTE — Nobody was ejected.');
 if(S.role==='imposter'&&S.meetings===3){finish(true,'You survived three meetings. Nobody uncovered your identity.');return}
 if(S.role==='crew'&&S.meetings===3){finish(false,'The crew ran out of meetings. The traitor escaped.');return}
 updateUI();
}
$('cast').onclick=()=>resolveVote(false);$('skip').onclick=()=>resolveVote(true);
$('use').onclick=use;$('kill').onclick=kill;$('report').onclick=report;$('meet').onclick=()=>{if(S.mode==='playing')meeting(S.name+' called an emergency meeting.');};
function finish(win,message){S.gameWin=win;S.mode='ended';hideScreens();$('endTitle').textContent=win?'MISSION COMPLETE.':'MISSION FAILED.';$('endCopy').textContent=message;$('endStats').textContent=(S.role==='crew'?'SIGNALS '+S.taskCount+'/3':'ELIMINATIONS '+S.killCount) +'    ·    MEETINGS '+S.meetings;reveal('endScreen');$('bottomhud').hidden=true}
$('again').onclick=()=>{S.mode='lobby';S.player=null;S.bots=[];S.bodies=[];S.camera.ready=false;$('bottomhud').hidden=true;reveal('lobby');$('lobbyRoom').hidden=false;updateUI()};
function findPath(start,target){
 // Local A* for touch-to-move: prevents taps from getting stuck behind partitions.
 const cell=35,cols=Math.ceil(WORLD.w/cell),rows=Math.ceil(WORLD.h/cell);
 const pos=(x,y)=>({x:(x+.5)*cell,y:(y+.5)*cell});
 function closest(point){const ci=clamp(Math.floor(point.x/cell),0,cols-1),cj=clamp(Math.floor(point.y/cell),0,rows-1);let best=null,min=Infinity;for(let r=0;r<6;r++){for(let y=cj-r;y<=cj+r;y++)for(let x=ci-r;x<=ci+r;x++){if(x<0||y<0||x>=cols||y>=rows)continue;const p=pos(x,y);if(!validMove(p.x,p.y))continue;const d=Math.hypot(p.x-point.x,p.y-point.y);if(d<min){best={x,y};min=d}}if(best)break}return best}
 const a=closest(start),b=closest(target);if(!a||!b)return[];
 const index=(x,y)=>x+y*cols,source=index(a.x,a.y),end=index(b.x,b.y);
 const nodes=new Map([[source,{index:source,x:a.x,y:a.y,g:0,f:0,parent:null}]]),open=[nodes.get(source)],closed=new Set();
 const dirs=[[1,0],[-1,0],[0,1],[0,-1],[1,1],[-1,1],[1,-1],[-1,-1]];
 let steps=0;while(open.length&&steps++<2800){open.sort((u,v)=>u.f-v.f);const curr=open.shift();if(closed.has(curr.index))continue;if(curr.index===end){const path=[];let n=curr;while(n&&n.parent!==null){path.push(pos(n.x,n.y));n=nodes.get(n.parent)}path.reverse();if(validMove(target.x,target.y))path.push(target);return path}closed.add(curr.index);
  for(const [dx,dy] of dirs){const x=curr.x+dx,y=curr.y+dy,k=index(x,y);if(x<0||y<0||x>=cols||y>=rows||closed.has(k))continue;
   const p=pos(x,y),prev=pos(curr.x,curr.y);
   if(!validMove(p.x,p.y)||!validMove((p.x+prev.x)/2,(p.y+prev.y)/2))continue;
   if(dx&&dy){if(!validMove(pos(curr.x+dx,curr.y).x,pos(curr.x+dx,curr.y).y)||!validMove(pos(curr.x,curr.y+dy).x,pos(curr.x,curr.y+dy).y))continue}
   const g=curr.g+Math.hypot(dx,dy),candidate=nodes.get(k);if(candidate&&candidate.g<=g)continue;
   const node={index:k,x,y,g,f:g+Math.hypot(x-b.x,y-b.y),parent:curr.index};nodes.set(k,node);open.push(node)
  }
 }
 return[];
}
function movement(dt){if(!S.player)return;
 let dx=0,dy=0;const k=S.keys;
 if(k.has('w')||k.has('ArrowUp'))dy--;
 if(k.has('s')||k.has('ArrowDown'))dy++;
 if(k.has('a')||k.has('ArrowLeft'))dx--;
 if(k.has('d')||k.has('ArrowRight'))dx++;
 if(Math.abs(S.joy.x)+Math.abs(S.joy.y)>.08){dx=S.joy.x;dy=S.joy.y}
 if(dx||dy){S.goal=null;S.path=[];const length=Math.hypot(dx,dy);dx/=length;dy/=length}
 else if(S.path?.length){const step=S.path[0];const a=step.x-S.player.x,b=step.y-S.player.y,len=Math.hypot(a,b);if(len<13)S.path.shift();else{dx=a/len;dy=b/len}}
 else if(S.goal){const a=S.goal.x-S.player.x,b=S.goal.y-S.player.y,length=Math.hypot(a,b);if(length<12)S.goal=null;else{dx=a/length;dy=b/length}}
 const speed=215;S.player.vx=dx;S.player.vy=dy;
 if(dx||dy){changePosition(S.player,S.player.x+dx*dt*speed,S.player.y+dy*dt*speed)}
 S.bots.forEach(b=>{
 if(b.dead)return;b.walk-=dt;if(!b.target||b.walk<=0||distance(b,b.target)<22){b.walk=rand(2.5,6);const area=choose(stations);b.target={x:rand(area.x+80,area.x+area.w-80),y:rand(area.y+80,area.y+area.h-80)}}
 const vx=b.target.x-b.x,vy=b.target.y-b.y,len=Math.hypot(vx,vy),f=len>20?50/len:0;
 b.vx=vx*f/50;b.vy=vy*f/50;
 if(len>20)changePosition(b,b.x+vx*f*dt,b.y+vy*f*dt)
 });
}
function tick(now){if(!S.last)S.last=now;const dt=Math.min(.05,(now-S.last)/1000);S.last=now;S.frame++;
 if(S.mode==='playing'){
  S.time=Math.max(0,S.time-dt);S.kcd=Math.max(0,S.kcd-dt);if(S.flashEffect)S.flashEffect.t-=dt;S.warning=Math.max(0,S.warning-dt);S.elapsed+=dt;S.aiTimer+=dt;
  movement(dt);
  if(S.role==='crew'&&S.aiTimer>=23){S.aiTimer=0;const murderer=S.bots.find(b=>b.imposter&&!b.dead),victims=S.bots.filter(b=>!b.imposter&&!b.dead);
   if(murderer&&victims.length){const v=choose(victims);v.dead=true;S.bodies.push({name:v.name,color:v.color,skin:v.skin,x:v.x,y:v.y,dead:false});toast('A DISTRESS SIGNAL WAS DETECTED');}
  }
  if(S.role==='crew'&&S.bots.filter(x=>!x.dead&&!x.imposter).length<=1)finish(false,'The traitor eliminated too many operatives.');
  if(S.time===0)finish(S.role==='imposter',S.role==='imposter'?'The countdown expired. The impostor remained undiscovered.':'You ran out of time before restoring the station.');
  if(S.frame%8===0)updateUI();
 }
 if(!S.w||!S.h)resize();drawWorld(now);requestAnimationFrame(tick)
}
window.addEventListener('keydown',e=>{
 const tag=document.activeElement?.tagName;if(['INPUT','TEXTAREA','SELECT'].includes(tag))return;
 const key=e.key.length===1?e.key.toLowerCase():e.key;
 if(['w','a','s','d','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(key)){S.keys.add(key);e.preventDefault()}
 if(e.repeat||S.mode!=='playing')return;
 if(key==='e')use();if(key==='r')report();if(key==='k')kill();if(key==='m')meeting(S.name+' called an emergency meeting.');
});
window.addEventListener('keyup',e=>S.keys.delete(e.key.length===1?e.key.toLowerCase():e.key));
window.addEventListener('blur',()=>{S.keys.clear();S.joy={x:0,y:0};$('knob').style.transform='translate(0,0)'});
const stick=$('joystick'),knob=$('knob');let stickId=null;
stick.addEventListener('pointerdown',e=>{stickId=e.pointerId;stick.setPointerCapture(stickId);joyMove(e);e.preventDefault()});
stick.addEventListener('pointermove',e=>{if(e.pointerId===stickId)joyMove(e)});
function joyMove(e){const r=stick.getBoundingClientRect(),size=r.width*.29;let dx=e.clientX-r.left-r.width/2,dy=e.clientY-r.top-r.height/2;const length=Math.hypot(dx,dy);if(length>size){dx=dx/length*size;dy=dy/length*size}S.joy={x:dx/size,y:dy/size};knob.style.transform=`translate(${dx}px,${dy}px)`}
function joyEnd(e){if(e.pointerId!==stickId)return;stickId=null;S.joy={x:0,y:0};knob.style.transform='translate(0,0)'}
stick.addEventListener('pointerup',joyEnd);stick.addEventListener('pointercancel',joyEnd);
canvas.addEventListener('pointerdown',e=>{if(S.mode!=='playing')return;const r=canvas.getBoundingClientRect();S.goal={x:(e.clientX-r.left-S.camera.x)/S.zoom,y:(e.clientY-r.top-S.camera.y)/S.zoom};S.goal.x=clamp(S.goal.x,80,WORLD.w-80);S.goal.y=clamp(S.goal.y,164,WORLD.h-85);S.path=findPath(S.player,S.goal);if(!S.path.length)toast('No safe route found. Try a closer floor tile.');S.goal=null});
resize();updateUI();requestAnimationFrame(tick);
})();