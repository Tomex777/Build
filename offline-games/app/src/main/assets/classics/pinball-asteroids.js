/* Distinct offline physics arcade games — no online resources. */
(()=>{'use strict';const G=window.G,T=Math.PI*2,R=(a,b)=>a+Math.random()*(b-a),clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
function setup(){const c=G.el('canvas','arena');c.width=360;c.height=520;c.style.cssText='display:block;margin:auto;width:100%;max-width:410px;aspect-ratio:9/13;border-radius:13px;background:#182b2c;touch-action:none';G.board().append(c);return[c,c.getContext('2d')]}
function point(e,c){const r=c.getBoundingClientRect();return{x:(e.clientX-r.left)*360/r.width,y:(e.clientY-r.top)*520/r.height}}
function disc(ctx,x,y,r,col){ctx.fillStyle=col;ctx.beginPath();ctx.arc(x,y,r,0,T);ctx.fill()}
function box(ctx,x,y,w,h,col){ctx.fillStyle=col;ctx.fillRect(x,y,w,h)}
function hud(ctx,str){ctx.fillStyle='#eaf0df';ctx.textAlign='center';ctx.font='bold 19px system-ui';ctx.fillText(str,180,41)}
function frameLoop(update,draw){let id=0,prev=0,stop=false;function step(t){if(stop)return;const dt=prev?Math.min(.038,(t-prev)/1000):.016;prev=t;if(!document.hidden)update(dt);draw();id=requestAnimationFrame(step)}id=requestAnimationFrame(step);return()=>{stop=true;cancelAnimationFrame(id)}}
G.add('pinball','Pinball Wizard','Keep the silver ball alive with two responsive flippers. Hit bumpers for points.','Arcade',()=>{
 const[c,ctx]=setup();let ball={x:245,y:95,vx:R(-110,110),vy:55},score=0,lives=3,pressed=[false,false],bumps=[{x:140,y:190,r:29},{x:229,y:220,r:27},{x:184,y:314,r:26}],cool=[0,0,0],over=false;
 const control=e=>{const p=point(e,c);pressed[p.x<180?0:1]=true;c.setPointerCapture(e.pointerId)};c.addEventListener('pointerdown',control);c.addEventListener('pointerup',()=>{pressed=[false,false]});c.addEventListener('pointercancel',()=>{pressed=[false,false]});
 const key=e=>{if(e.key==='ArrowLeft')pressed[0]=true;if(e.key==='ArrowRight')pressed[1]=true},keyUp=e=>{if(e.key==='ArrowLeft')pressed[0]=false;if(e.key==='ArrowRight')pressed[1]=false};document.addEventListener('keydown',key);document.addEventListener('keyup',keyUp);
 const reset=()=>{ball={x:246,y:95,vx:R(-120,120),vy:40}};
 const stop=frameLoop(dt=>{if(over)return;ball.vy+=670*dt;ball.x+=ball.vx*dt;ball.y+=ball.vy*dt;
 if(ball.x<24||ball.x>336){ball.x=clamp(ball.x,24,336);ball.vx*=-.86}if(ball.y<65){ball.y=65;ball.vy=Math.abs(ball.vy)*.84}
 for(let i=0;i<bumps.length;i++){cool[i]-=dt;const b=bumps[i],dx=ball.x-b.x,dy=ball.y-b.y,d=Math.hypot(dx,dy);if(d<b.r+9&&cool[i]<=0){const nx=dx/(d||1),ny=dy/(d||1),v=ball.vx*nx+ball.vy*ny;ball.vx-=2*v*nx;ball.vy-=2*v*ny;ball.vx+=nx*75;ball.vy+=ny*75;ball.x=b.x+nx*(b.r+10);ball.y=b.y+ny*(b.r+10);score+=25;cool[i]=.18;G.status('Score '+score+' · Balls '+lives)}}
 for(let i=0;i<2;i++){const pivot=i?230:130,centerX=i?264:96;if(Math.abs(ball.x-centerX)<84&&ball.y>429&&ball.y<469){if(pressed[i]){ball.vy=-Math.max(400,Math.abs(ball.vy)*.95);ball.vx+=(i?-1:1)*R(80,160);ball.y=428;score+=1}else if(Math.abs(ball.x-centerX)<35){ball.vy=-Math.abs(ball.vy)*.72;ball.y=430}}}
 if(ball.y>550){lives--;if(lives===0){over=true;G.score('pinball',score);G.status('Three balls drained · '+score+' points')}else reset()}},()=>{
 box(ctx,0,0,360,520,'#192c2f');box(ctx,13,70,334,450,'#234c47');ctx.strokeStyle='#b9a782';ctx.lineWidth=6;ctx.strokeRect(16,73,328,440);
 for(const b of bumps){disc(ctx,b.x,b.y,b.r+4,'#deb875');disc(ctx,b.x,b.y,b.r-6,'#417b77');disc(ctx,b.x,b.y,8,'#e6c879')}
 for(let i=0;i<2;i++){const px=i?230:130,py=464;ctx.strokeStyle=pressed[i]?'#e5db9c':'#b0cba9';ctx.lineWidth=15;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(px,py);ctx.lineTo(px+(i?59:-59),py-(pressed[i]?25:0));ctx.stroke()}
 disc(ctx,ball.x,ball.y,10,'#f2f2e9');hud(ctx,'SCORE '+score+' · BALLS '+lives);if(over)hud(ctx,'GAME OVER')});
 G.status('Tap left/right to control flippers');G.note('Hold the lower left or lower right side to activate a flipper. Each bumper hit earns points.');return()=>{stop();document.removeEventListener('keydown',key);document.removeEventListener('keyup',keyUp)}});
G.add('asteroids','Asteroids','Aim the ship, thrust into clear space and break drifting asteroids.','Arcade',()=>{
 const[c,ctx]=setup(),ship={x:180,y:260,a:-Math.PI/2,vx:0,vy:0};let bullets=[],rocks=[],spawn=.2,score=0,lives=3,elapsed=0,dead=false,invincible=0;
 const aim=e=>{const p=point(e,c);ship.a=Math.atan2(p.y-ship.y,p.x-ship.x)};c.addEventListener('pointerdown',aim);c.addEventListener('pointermove',aim);
 G.action('THRUST',()=>{if(dead)return;ship.vx+=Math.cos(ship.a)*53;ship.vy+=Math.sin(ship.a)*53});
 G.action('FIRE',()=>{if(dead)return;bullets.push({x:ship.x,y:ship.y,vx:ship.vx+Math.cos(ship.a)*350,vy:ship.vy+Math.sin(ship.a)*350,life:1.2})},true);
 const stop=frameLoop(dt=>{if(dead)return;elapsed+=dt;invincible=Math.max(0,invincible-dt);spawn-=dt;if(spawn<=0){spawn=Math.max(.47,1.15-elapsed*.005);const a=R(0,T),dist=235;rocks.push({x:180+Math.cos(a)*dist,y:260+Math.sin(a)*dist,vx:-Math.cos(a)*R(20,85),vy:-Math.sin(a)*R(20,85),r:R(18,36)})}
 ship.x=(ship.x+ship.vx*dt+360)%360;ship.y=(ship.y+ship.vy*dt+520)%520;ship.vx*=.995;ship.vy*=.995;
 bullets.forEach(b=>{b.x+=b.vx*dt;b.y+=b.vy*dt;b.life-=dt});bullets=bullets.filter(b=>b.life>0&&b.x>-15&&b.x<375&&b.y>-15&&b.y<535);
 for(const rock of rocks){rock.x+=rock.vx*dt;rock.y+=rock.vy*dt;for(const b of bullets){if(b.life>0&&Math.hypot(b.x-rock.x,b.y-rock.y)<rock.r){b.life=0;rock.r-=16;score+=5;G.status('Asteroids '+score+' · Lives '+lives);break}}
 if(invincible<=0&&Math.hypot(rock.x-ship.x,rock.y-ship.y)<rock.r+13){lives--;invincible=1.5;if(lives<=0){dead=true;G.score('asteroids',score);G.status('Ship destroyed · '+score+' points')}}}
 rocks=rocks.filter(r=>r.r>8&&r.x>-70&&r.x<430&&r.y>-70&&r.y<590);if(elapsed>90){dead=true;G.score('asteroids',score);G.status('Mission complete · '+score+' points')}},()=>{
 box(ctx,0,0,360,520,'#142b33');for(let i=0;i<38;i++)disc(ctx,(i*89)%360,(i*171)%520,1.3,'#7faba4');
 rocks.forEach(r=>{disc(ctx,r.x,r.y,r.r,'#957f6f');disc(ctx,r.x-r.r*.2,r.y,Math.max(2,r.r*.17),'#665950')});
 bullets.forEach(b=>disc(ctx,b.x,b.y,3,'#e9ce8d'));if(invincible===0||Math.floor(invincible*8)%2===0){ctx.save();ctx.translate(ship.x,ship.y);ctx.rotate(ship.a);ctx.fillStyle='#b3dfba';ctx.beginPath();ctx.moveTo(17,0);ctx.lineTo(-13,-11);ctx.lineTo(-8,0);ctx.lineTo(-13,11);ctx.closePath();ctx.fill();ctx.restore()}
 hud(ctx,'SCORE '+score+' · LIVES '+lives);if(dead)hud(ctx,'MISSION ENDED')});
 G.status('Aim by dragging, then thrust or fire');G.note('Drag to aim the ship. THRUST adds momentum; FIRE shoots in the current direction.');return stop});
})();