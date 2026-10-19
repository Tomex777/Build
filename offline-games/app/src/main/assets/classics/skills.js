/* Offline dexterity and reasoning games; touch controls, local scores, no online assets. */
(()=>{'use strict';const G=window.G,rnd=(a,b)=>a+Math.random()*(b-a);
function canvas(w=360,h=520){const c=G.el('canvas','arena');c.width=w;c.height=h;c.style.cssText='width:100%;max-width:440px;height:auto;display:block;margin:0 auto;border-radius:10px;background:#173629;touch-action:none';G.board().append(c);return [c,c.getContext('2d')]}
function coord(e,c,w=360,h=520){const r=c.getBoundingClientRect();return [(e.clientX-r.left)*w/r.width,(e.clientY-r.top)*h/r.height]}
function bg(ctx){ctx.fillStyle='#193b2d';ctx.fillRect(0,0,360,520)}
function title(ctx,s){ctx.fillStyle='#f1f1e6';ctx.font='800 19px system-ui';ctx.textAlign='center';ctx.fillText(s,180,32)}
G.add('mazerunner','Maze Runner','Find the exit of a new carved maze. Swipe to move one step along passages.','Puzzle',()=>{
 const N=13,board=Array(N*N).fill(1),seen=new Set();const key=(x,y)=>y*N+x;
 function carve(x,y){board[key(x,y)]=0;const dirs=G.shuffle([[2,0],[-2,0],[0,2],[0,-2]]);dirs.forEach(([dx,dy])=>{let nx=x+dx,ny=y+dy;if(nx<1||ny<1||nx>N-2||ny>N-2||board[key(nx,ny)]===0)return;board[key(x+dx/2,y+dy/2)]=0;carve(nx,ny)})}carve(1,1);
 let player=[1,1],moves=0,done=false,start=null;
 function move(dx,dy){if(done)return;let x=player[0]+dx,y=player[1]+dy;if(x<0||y<0||x>=N||y>=N||board[key(x,y)]===1)return;player=[x,y];moves++;done=x===N-2&&y===N-2;if(done)G.score('mazerunner',Math.max(1,1000-moves));draw()}
 function draw(){G.status(done?'Maze escaped in '+moves+' steps!':'Moves '+moves+' · Reach the gold tile');G.grid(N,N,board.map((v,i)=>v?'':i===key(...player)?'●':i===key(N-2,N-2)?'◆':''),()=>{},(b,i)=>{b.disabled=true;b.style.fontSize='13px';b.style.borderRadius='1px';b.style.background=board[i]?'#304e3f':'#f8f8f1';if(i===key(...player)){b.style.background='#519275';b.style.color='#fff'}if(i===key(N-2,N-2))b.style.background='#dbc07c'});}
 draw();G.actions();for(const [name,dx,dy] of [['↑',0,-1],['←',-1,0],['↓',0,1],['→',1,0]])G.action(name,()=>move(dx,dy));const b=document.getElementById('board');const onDown=e=>{start=[e.clientX,e.clientY]};const onUp=e=>{if(!start)return;const x=e.clientX-start[0],y=e.clientY-start[1];if(Math.max(Math.abs(x),Math.abs(y))>9){if(Math.abs(x)>Math.abs(y))move(Math.sign(x),0);else move(0,Math.sign(y))}start=null};b.addEventListener('pointerdown',onDown);b.addEventListener('pointerup',onUp);
 function keyboard(e){const map={ArrowUp:[0,-1],ArrowDown:[0,1],ArrowLeft:[-1,0],ArrowRight:[1,0]};if(map[e.key])move(...map[e.key])}window.addEventListener('keydown',keyboard);
 G.note('A depth-first maze is newly generated on every restart. The exit is at bottom-right.');return()=>window.removeEventListener('keydown',keyboard)});
G.add('darts','Darts','Time your throw as the moving crosshair passes over the bullseye. Ten throws.','Sports',()=>{
 const [c,ctx]=canvas();let score=0,throws=0,time=0,raf=0,last=0,finished=false,marks=[];
 function aim(){return [180+Math.sin(time*2.1)*120,270+Math.cos(time*3.2)*116]}
 function throwDart(){if(finished)return;const [x,y]=aim(),d=Math.hypot(x-180,y-270),value=d<18?50:d<48?25:d<77?15:d<108?10:d<135?5:0;marks.push({x,y,value});score+=value;throws++;G.score('darts',score);if(throws>=10)finished=true;G.status(finished?'Round complete · '+score+' points':value+' points · '+throws+'/10 throws')}
 c.addEventListener('pointerdown',throwDart);G.action('Throw dart',throwDart,true);
 function frame(t){const dt=Math.min(.04,(t-last)/1000||.016);last=t;if(!finished&&!document.hidden)time+=dt;bg(ctx);const rings=[135,108,77,48,18],colors=['#a46e5b','#e6e0c4','#607963','#efc97c','#aa5149'];rings.forEach((r,i)=>{ctx.fillStyle=colors[i];ctx.beginPath();ctx.arc(180,270,r,0,7);ctx.fill();ctx.strokeStyle='#efefdc';ctx.lineWidth=2;ctx.stroke()});marks.forEach(m=>{ctx.fillStyle='#0f3125';ctx.beginPath();ctx.arc(m.x,m.y,4,0,7);ctx.fill()});if(!finished){const [x,y]=aim();ctx.strokeStyle='#f8f7ed';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(x-13,y);ctx.lineTo(x+13,y);ctx.moveTo(x,y-13);ctx.lineTo(x,y+13);ctx.stroke()}title(ctx,'SCORE '+score+' · '+throws+'/10');if(!finished)raf=requestAnimationFrame(frame)}
 G.status('Aim carefully · 10 throws');G.note('Tap the board or Throw when the moving crosshair reaches the centre.');raf=requestAnimationFrame(frame);return()=>cancelAnimationFrame(raf)});
G.add('basketball','Basketball Shoot','Flick the ball toward the hoop. Gravity determines whether the shot lands.','Sports',()=>{
 const [c,ctx]=canvas();let ball={x:60,y:443,vx:0,vy:0},shots=0,goals=0,active=false,release=null,raf=0,last=0,crossed=false;
 function reset(){ball={x:60,y:443,vx:0,vy:0};active=false;crossed=false}
 c.addEventListener('pointerdown',e=>{if(shots>=10||active)return;release=coord(e,c);c.setPointerCapture(e.pointerId)});
 c.addEventListener('pointerup',e=>{if(!release||active)return;const p=coord(e,c),dx=release[0]-p[0],dy=release[1]-p[1];ball.vx=Math.max(90,Math.min(410,dx*2.2+215));ball.vy=Math.max(-690,Math.min(-180,dy*2.2-370));shots++;active=true;release=null;G.status('Shot '+shots+'/10 · Goals '+goals)});
 function frame(t){const dt=Math.min(.035,(t-last)/1000||.016);last=t;if(active&&!document.hidden){const py=ball.y;ball.x+=ball.vx*dt;ball.y+=ball.vy*dt;ball.vy+=710*dt;if(!crossed&&py<201&&ball.y>=201&&ball.vy>0&&ball.x>266&&ball.x<315){goals++;crossed=true;G.score('basketball',goals)}if(ball.x>395||ball.y>540||ball.x<0)reset();if(shots>=10&&!active)G.status('Finished · '+goals+'/10 baskets')}
 bg(ctx);ctx.fillStyle='#ceaa72';ctx.fillRect(308,120,7,156);ctx.strokeStyle='#f1d1a0';ctx.lineWidth=6;ctx.strokeRect(265,143,61,46);ctx.strokeStyle='#de8161';ctx.lineWidth=6;ctx.beginPath();ctx.moveTo(266,197);ctx.lineTo(315,197);ctx.stroke();ctx.strokeStyle='#a4b99f';ctx.lineWidth=2;for(let i=0;i<6;i++){ctx.beginPath();ctx.moveTo(269+i*9,200);ctx.lineTo(275+i*7,240);ctx.stroke()}ctx.fillStyle='#d28c53';ctx.beginPath();ctx.arc(ball.x,ball.y,16,0,7);ctx.fill();ctx.strokeStyle='#583e32';ctx.lineWidth=1;ctx.beginPath();ctx.arc(ball.x,ball.y,16,0,7);ctx.moveTo(ball.x-16,ball.y);ctx.lineTo(ball.x+16,ball.y);ctx.stroke();ctx.fillStyle='#415c4e';ctx.fillRect(0,475,360,7);title(ctx,'GOALS '+goals+' · SHOTS '+shots);raf=requestAnimationFrame(frame)}
 G.status('Flick from the court to shoot · 10 attempts');G.note('Drag backward from anywhere and release; horizontal and vertical distance set your shot.');raf=requestAnimationFrame(frame);return()=>cancelAnimationFrame(raf)});
G.add('penalty','Penalty Shootout','Choose your shot placement before the goalkeeper dives. Five kicks.','Sports',()=>{
 const [c,ctx]=canvas();let rounds=0,goals=0,keeper=180,lastTarget=null,ended=false;
 function kick(e){if(ended)return;const [x,y]=coord(e,c);if(y<145||y>344||x<42||x>318){G.status('Tap within the goal');return}keeper=rnd(80,280);const save=Math.abs(x-keeper)<43&&y>205;lastTarget={x,y,save};if(!save){goals++;G.score('penalty',goals)}rounds++;if(rounds===5)ended=true;G.status(ended?'Five kicks · '+goals+' goals':save?'Goalkeeper saved!':'Goal! '+goals+'/'+rounds);draw()}
 function draw(){bg(ctx);ctx.strokeStyle='#e6f0dd';ctx.lineWidth=5;ctx.strokeRect(38,140,284,208);ctx.strokeStyle='#779582';ctx.lineWidth=1;for(let i=1;i<8;i++){ctx.beginPath();ctx.moveTo(38+i*35.5,140);ctx.lineTo(38+i*35.5,348);ctx.stroke()}for(let i=1;i<6;i++){ctx.beginPath();ctx.moveTo(38,140+i*34.6);ctx.lineTo(322,140+i*34.6);ctx.stroke()}ctx.fillStyle='#d2a273';ctx.fillRect(keeper-28,247,56,17);ctx.fillRect(keeper-10,222,20,54);ctx.beginPath();ctx.arc(keeper,211,14,0,7);ctx.fill();if(lastTarget){ctx.fillStyle=lastTarget.save?'#cc6e61':'#f4eee0';ctx.beginPath();ctx.arc(lastTarget.x,lastTarget.y,13,0,7);ctx.fill()}title(ctx,'GOALS '+goals+' / '+rounds);if(ended){ctx.fillStyle='#eff7eb';ctx.textAlign='center';ctx.fillText('Round complete',180,410)}}
 c.addEventListener('pointerdown',kick);G.status('Tap an open corner of the goal · 5 kicks');draw();G.note('The goalkeeper makes an independent dive for each kick. Aim away from the centre.');});
G.add('ski','Ski Slalom','Ski between the gate flags. Missing three gates ends the run.','Sports',()=>{
 const [c,ctx]=canvas();let skier=180,gates=[],spawn=0,score=0,misses=0,seconds=45,raf=0,last=0,active=true;
 c.addEventListener('pointerdown',e=>skier=Math.min(340,Math.max(20,coord(e,c)[0])));c.addEventListener('pointermove',e=>skier=Math.min(340,Math.max(20,coord(e,c)[0])));
 function frame(t){const dt=Math.min(.036,(t-last)/1000||.016);last=t;if(active&&!document.hidden){seconds-=dt;spawn-=dt;if(spawn<0){spawn=1.15;gates.push({x:rnd(100,260),y:-15,checked:false})}gates.forEach(g=>{g.y+=200*dt;if(!g.checked&&g.y>422){g.checked=true;if(Math.abs(skier-g.x)<47){score++;G.score('ski',score)}else misses++}});gates=gates.filter(g=>g.y<540);if(misses>=3||seconds<=0){active=false;G.status('Course finished · '+score+' gates')}else G.status('Gates '+score+' · Misses '+misses+'/3 · '+Math.ceil(seconds)+'s')}
 bg(ctx);ctx.fillStyle='#dbe4dc';ctx.fillRect(30,50,300,470);ctx.strokeStyle='#9aafa0';ctx.setLineDash([6,15]);ctx.beginPath();ctx.moveTo(70,50);ctx.lineTo(70,520);ctx.moveTo(290,50);ctx.lineTo(290,520);ctx.stroke();ctx.setLineDash([]);gates.forEach(g=>{ctx.strokeStyle='#2a6857';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(g.x-65,g.y-14);ctx.lineTo(g.x-65,g.y+14);ctx.moveTo(g.x+65,g.y-14);ctx.lineTo(g.x+65,g.y+14);ctx.stroke();ctx.fillStyle='#c96e64';ctx.fillRect(g.x-65,g.y-14,16,13);ctx.fillRect(g.x+49,g.y-14,16,13)});ctx.fillStyle='#356e59';ctx.beginPath();ctx.moveTo(skier,402);ctx.lineTo(skier-14,440);ctx.lineTo(skier+14,440);ctx.fill();title(ctx,'GATES '+score+' · MISSES '+misses);if(active)raf=requestAnimationFrame(frame)}
 G.status('Ski between the gates · 45 seconds');G.note('Drag horizontally to stay between each gate’s flag pair.');raf=requestAnimationFrame(frame);return()=>cancelAnimationFrame(raf)});
G.add('bomb','Bomb Diffuser','Read the circuit instructions and cut the three required wires in order.','Action',()=>{
 const colors=[['RED','#d45e55'],['BLUE','#609fc4'],['GREEN','#63af83'],['YELLOW','#dfb956'],['PURPLE','#ac85c5']];
 const sequence=G.shuffle(colors).slice(0,3);let step=0,seconds=22,done=false,lost=false,interval=null;
 const board=G.board();const panel=G.el('div','bomb-panel');const timer=G.el('div','bomb-readout');const wires=G.el('div','bomb-wires');const guide=G.el('div','bomb-guide');
 const top=G.el('p','bomb-caption','CIRCUIT SEQUENCE');panel.append(top);
 const line=G.el('div','bomb-sequence');sequence.forEach(([name,color],i)=>{const part=G.el('span','bomb-step',name);part.style.borderBottomColor=color;line.append(part);if(i<2)line.append(G.el('span','bomb-join','→'))});panel.append(line);
 timer.setAttribute('aria-label','Time remaining');panel.append(timer);
 panel.append(G.el('div','bomb-wires-title','CUT ONLY IN THIS ORDER'));panel.append(wires);panel.append(guide);board.append(panel);
 const svg='<svg viewBox="0 0 38 38" width="35" height="35" fill="none" aria-hidden="true"><rect x="2" y="9" width="34" height="20" rx="5" stroke="currentColor" stroke-width="2"/><path d="M8 19h22M13 14v10m12-10v10" stroke="currentColor" stroke-width="1.7"/></svg>';
 const rowMap=new Map();
 function show(){
   timer.textContent=done?'SAFE':lost?'FAULT':String(seconds).padStart(2,'0')+' : 00';
   timer.dataset.state=done?'safe':lost?'fault':seconds<7?'urgent':'ready';
   guide.textContent=done?'Circuit disarmed.':lost?'The wrong cable triggered the circuit.':'Next wire: '+sequence[step][0];
   for(const [name,row] of rowMap){const cut=sequence.slice(0,step).some(item=>item[0]===name);
     row.disabled=done||lost||cut;row.dataset.cut=String(cut);
     const label=row.querySelector('.wire-result');if(label)label.textContent=cut?'CUT':'CONNECT';
   }
 }
 for(const [name,color] of colors){
  const row=G.el('button','wire-cable');row.type='button';row.style.setProperty('--wire-color',color);
  const icon=G.el('span','wire-icon');icon.innerHTML=svg;
  const cable=G.el('span','wire-stripe');const label=G.el('span','wire-name',name);const result=G.el('span','wire-result','CONNECT');
  row.append(icon,cable,label,result);row.setAttribute('aria-label','Cut '+name.toLowerCase()+' wire');
  row.addEventListener('click',()=>{if(done||lost)return;if(name!==sequence[step][0]){lost=true;G.status('Wrong wire · circuit triggered!')}
   else{step++;if(step===3){done=true;G.score('bomb',seconds);G.status('Circuit safely disarmed · '+seconds+' seconds remaining')}else G.status('Correct wire · '+(3-step)+' left')}show()});
  wires.append(row);rowMap.set(name,row);
 }
 show();G.status('Follow the wire sequence · '+seconds+' seconds');
 interval=setInterval(()=>{if(done||lost||document.hidden)return;seconds--;if(seconds<=0){lost=true;G.status('Time expired · circuit triggered!')}show()},1000);
 G.note('This is a precision-and-sequence challenge, not guessing. A single incorrect cable ends the round.');
 return()=>clearInterval(interval);
});
})();