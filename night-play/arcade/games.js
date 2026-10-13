/* NIGHT Arcade: 4 original mobile-browser arcade games, local scores only. */
(()=>{"use strict";
const $=id=>document.getElementById(id);const c=$("screen"),ctx=c.getContext("2d");const board=$("mineboard"),touch=$("touchbar"),extra=$("extra");
const palette={dark:"#173e2c",light:"#dbe9ce",gold:"#e7b24c",danger:"#e6534b",blue:"#61a0bf"};
const metadata={
bird:["Wing Hop","Tap to fly, glide through the gaps.","Tap anywhere or press Space to flap. Pass through pipe gaps without touching the pipes or ground."],
blocks:["Block Drop","Stack, clear lines, keep going.","Arrange the seven falling shapes. Rotate, move and drop to clear lines. Use on-screen controls or arrow keys; Space hard-drops."],
mines:["Minesweeper","One tap at a time, clear the field.","Reveal every safe square. Numbers count the mines in neighbouring squares. Switch to Flag to mark suspected mines. Your first square and its neighbours are protected."],
maze:["Maze Chase","Collect every dot. Avoid the chasers.","Swipe on the maze, use the arrows, or press WASD. Eat the dots; dodge the red ghosts. Collect all dots to win."]
};
let type="bird",frame=0,raf=null,interval=null,life=null,state=null,finished=false,paused=false,points=0,last=0;
let gameStart=0;
function stop(){if(raf!==null){cancelAnimationFrame(raf);raf=null;}if(interval!==null){clearInterval(interval);interval=null;}}
function best(id){try{return Math.max(0,Number(localStorage.getItem("night-play-v1-"+id))||0);}catch{return 0;}}
function record(id,n){const score=Math.round(Math.max(0,n));try{const key="night-play-v1-"+id;if(score>best(id))localStorage.setItem(key,String(score));const histKey="night-play-history-v1-"+id;let arr=[];try{arr=JSON.parse(localStorage.getItem(histKey)||"[]");}catch{}if(!Array.isArray(arr))arr=[];arr.push(score);localStorage.setItem(histKey,JSON.stringify(arr.slice(-12)));const countKey="night-play-history-count-v1-"+id;const count=Math.max(0,Number(localStorage.getItem(countKey))||0);localStorage.setItem(countKey,String(count+1));}catch{}}
function score(n){points=Math.max(0,Math.floor(n));$("score").textContent=String(points);$("best").textContent=String(Math.max(best(type),points));}
function message(t){$("message").textContent=t;}
function end(text){if(finished)return;finished=true;stop();record(type,points);message(text+" · Tap Restart to play again.");}
function drawText(text,y,size=24){ctx.fillStyle="#fff";ctx.font="900 "+size+"px system-ui,Arial";ctx.textAlign="center";ctx.fillText(text,c.width/2,y);}
function pick(id){if(!metadata[id])id="bird";type=id;const [title,sub,rules]=metadata[id];$("title").textContent=title;$("subtitle").textContent=sub;$("rules").textContent=rules;[...$("picks").querySelectorAll("button")].forEach(b=>b.setAttribute("aria-current",String(b.dataset.game===id)));const url=new URL(location.href);url.searchParams.set("game",id);history.replaceState({},"",url.pathname+url.search);restart();}
function restart(){stop();last=0;gameStart=Date.now();finished=false;paused=false;score(0);message("Ready to play.");extra.replaceChildren();board.hidden=type!=="mines";c.hidden=type==="mines";c.className="mode-"+type;
if(type==="bird")setupBird();if(type==="blocks")setupBlocks();if(type==="mines")setupMines();if(type==="maze")setupMaze();}
function button(label,action,aria=label){return '<button type="button" data-control="'+action+'" aria-label="'+aria+'">'+label+'</button>';}
function setControls(html){touch.innerHTML=html;}
function frameLoop(cb){let prev=0;function tick(t){if(finished)return;const dt=Math.min(42,prev?t-prev:16);prev=t;cb(dt,t);if(!finished)raf=requestAnimationFrame(tick);}raf=requestAnimationFrame(tick);}
/* WING HOP — fixed timestep-style physics at high refresh rates. */
function setupBird(){c.width=360;c.height=600;state={x:95,y:260,vy:0,gap:175,pipes:[],distance:0,started:false,dead:false};setControls(button("FLAP ↥","flap","Flap wings"));message("Tap to flap and begin.");frameLoop(birdFrame);}
function flap(){if(type!=="bird"||finished)return;state.started=true;state.vy=-6.8;message("Keep flying!");}
function birdFrame(dt){const s=state,step=dt/16.666;if(s.started){s.vy+=0.33*step;s.y+=s.vy*step;s.distance+=2.4*step;if(!s.pipes.length||s.pipes[s.pipes.length-1].x<165){const prev=s.pipes[s.pipes.length-1];if(!prev||prev.x<150)s.pipes.push({x:prev?prev.x+216:365,y:120+Math.random()*280,scored:false});}for(const p of s.pipes){p.x-=2.4*step;if(!p.scored&&p.x+62<s.x){p.scored=true;score(points+1);}if(s.x+16>p.x&&s.x-16<p.x+62&&(s.y-15<p.y-s.gap/2||s.y+15>p.y+s.gap/2)){end("You hit a pipe!");}}s.pipes=s.pipes.filter(p=>p.x>-70);if(s.y<14||s.y>558)end("Bird down!");}
ctx.fillStyle="#bce8e2";ctx.fillRect(0,0,360,600);
ctx.fillStyle="#d7f1e9";for(let i=0;i<6;i++){const x=((i*113-s.distance*.25)%470+470)%470-60;ctx.beginPath();ctx.ellipse(x,120+i%3*80,51,15,0,0,Math.PI*2);ctx.fill();}
ctx.fillStyle="#71a88f";for(let i=0;i<6;i++){const x=(i*95-(s.distance*.4%95));ctx.beginPath();ctx.moveTo(x,550);ctx.lineTo(x+60,455+i%2*24);ctx.lineTo(x+155,550);ctx.closePath();ctx.fill();}
for(const p of s.pipes){ctx.fillStyle="#318364";ctx.strokeStyle="#205643";ctx.lineWidth=5;ctx.fillRect(p.x,0,62,p.y-s.gap/2);ctx.strokeRect(p.x+3,0,56,p.y-s.gap/2);ctx.fillRect(p.x,p.y+s.gap/2,62,600-p.y);ctx.strokeRect(p.x+3,p.y+s.gap/2,56,600-p.y);ctx.fillStyle="#55b789";ctx.fillRect(p.x+6,0,10,p.y-s.gap/2);ctx.fillRect(p.x+6,p.y+s.gap/2,10,600-p.y);}
ctx.fillStyle="#78553a";ctx.fillRect(0,566,360,34);ctx.fillStyle="#91c266";ctx.fillRect(0,558,360,9);
ctx.save();ctx.translate(s.x,s.y);ctx.rotate(Math.max(-.45,Math.min(.75,s.vy/14)));ctx.fillStyle="#f9c849";ctx.beginPath();ctx.ellipse(0,0,22,16,0,0,Math.PI*2);ctx.fill();ctx.fillStyle="#eab031";ctx.beginPath();ctx.ellipse(-6,7,12,7,-.4,0,Math.PI*2);ctx.fill();ctx.fillStyle="#292e26";ctx.beginPath();ctx.arc(10,-6,4,0,Math.PI*2);ctx.fill();ctx.fillStyle="#e07937";ctx.beginPath();ctx.moveTo(21,-1);ctx.lineTo(33,3);ctx.lineTo(21,7);ctx.fill();ctx.restore();
if(!s.started){ctx.fillStyle="#173c31bc";ctx.fillRect(45,240,270,95);drawText("TAP TO FLY",290,28);drawText("Dodge the pipes",319,13);}if(finished){ctx.fillStyle="#163c31a9";ctx.fillRect(25,225,310,130);drawText("ROUND OVER",273,27);drawText("Your score: "+points,310,20);}}
/* BLOCK DROP — seven-bag, wall kicks, lock, line scoring. */
const shapes=[
[[0,1],[1,1],[2,1],[3,1]],
[[1,0],[2,0],[1,1],[2,1]],
[[1,0],[0,1],[1,1],[2,1]],
[[0,0],[0,1],[1,1],[2,1]],
[[2,0],[0,1],[1,1],[2,1]],
[[0,0],[1,0],[1,1],[2,1]],
[[1,0],[2,0],[0,1],[1,1]]];
const pieceColors=["#51bce4","#f8cb4d","#b47bdd","#e76d45","#4888d7","#55bb85","#e75b6c"];
function pieces(){return [...Array(7).keys()].sort(()=>Math.random()-.5);}
function nextPiece(s){if(!s.bag.length)s.bag=pieces();const idx=s.bag.pop();s.cur={idx,cells:shapes[idx].map(([x,y])=>[x,y]),x:3,y:0};if(collision(s,0,0)){end("No space left!");}s.canHold=true;}
function collision(s,dx,dy,cells=s.cur.cells){return cells.some(([x,y])=>{const xx=x+s.cur.x+dx,yy=y+s.cur.y+dy;return xx<0||xx>=10||yy>=20||(yy>=0&&s.grid[yy][xx]!==null);});}
function setupBlocks(){c.width=300;c.height=600;state={grid:Array.from({length:20},()=>Array(10).fill(null)),cur:null,bag:[],time:0,lines:0,level:1,canHold:true};nextPiece(state);setControls(button("◀","left","Move left")+button("⟳","rotate","Rotate piece")+button("▶","right","Move right")+button("▼","down","Soft drop")+button("⬇","drop","Hard drop")+button("Ⅱ","pause","Pause"));message("Clear complete rows to score.");frameLoop(blockFrame);}
function rotate(s){const old=s.cur.cells,turn=old.map(([x,y])=>[3-y,x]);for(const shift of [0,-1,1,-2,2])if(!collision(s,shift,0,turn)){s.cur.cells=turn;s.cur.x+=shift;return;}}
function blockMove(move){const s=state;if(type!=="blocks"||finished)return;if(move==="pause"){paused=!paused;message(paused?"Paused":"Back in the game");return;}if(paused)return;if(move==="left"&&!collision(s,-1,0))s.cur.x--;if(move==="right"&&!collision(s,1,0))s.cur.x++;if(move==="rotate")rotate(s);if(move==="down"){if(!collision(s,0,1))s.cur.y++;else lockBlock(s);}if(move==="drop"){while(!collision(s,0,1))s.cur.y++;lockBlock(s);}}
function lockBlock(s){for(const [x,y] of s.cur.cells){let xx=x+s.cur.x,yy=y+s.cur.y;if(yy<0){end("Stack too high!");return;}if(yy<20)s.grid[yy][xx]=s.cur.idx;}let count=0;for(let y=19;y>=0;y--){if(s.grid[y].every(k=>k!==null)){s.grid.splice(y,1);s.grid.unshift(Array(10).fill(null));count++;y++;}}
if(count){const n=[0,100,300,500,800][count]*s.level;s.lines+=count;s.level=1+Math.floor(s.lines/10);score(points+n);message("Cleared "+count+" line"+(count===1?"":"s")+"!");}
nextPiece(s);}
function blockFrame(dt){const s=state;if(!paused&&!finished){s.time+=dt;if(s.time>Math.max(115,640-(s.level-1)*44)){s.time=0;if(!collision(s,0,1))s.cur.y++;else lockBlock(s);}}
ctx.fillStyle="#172826";ctx.fillRect(0,0,300,600);for(let y=0;y<20;y++)for(let x=0;x<10;x++){drawBlock(x,y,s.grid[y][x]);}if(s.cur&&!finished)for(const [x,y] of s.cur.cells)drawBlock(x+s.cur.x,y+s.cur.y,s.cur.idx);
ctx.strokeStyle="#596a5b";ctx.lineWidth=5;ctx.strokeRect(1,1,298,598);
const info=extra;info.textContent="Lines: "+s.lines+" · Level: "+s.level;
if(paused){ctx.fillStyle="#0b161bd6";ctx.fillRect(0,0,300,600);drawText("PAUSED",290);}
if(finished){ctx.fillStyle="#0b1519c8";ctx.fillRect(0,0,300,600);drawText("GAME OVER",285);drawText("Score: "+points,318,19);}}
function drawBlock(x,y,idx){const px=x*30,py=y*30;if(idx===null){ctx.strokeStyle="#233b38";ctx.lineWidth=.75;ctx.strokeRect(px,py,30,30);return;}ctx.fillStyle=pieceColors[idx];ctx.fillRect(px+1,py+1,28,28);ctx.fillStyle="#fff4";ctx.fillRect(px+3,py+3,24,4);ctx.fillStyle="#10202755";ctx.fillRect(px+3,py+24,24,3);}
/* MINESWEEPER — generated safe on first opening. */
const minesDir=[-1,0,1];
function setupMines(){c.width=360;c.height=360;state={cells:Array(81).fill(0),open:Array(81).fill(false),flags:Array(81).fill(false),started:false,flagMode:false,mines:10,timer:0};setControls(button("⚑","flag","Switch flag mode")+button("✓","reveal","Switch reveal mode"));message("Reveal a square to begin.");drawMines();}
function neighbours(i){const r=Math.floor(i/9),c=i%9,out=[];for(const dr of minesDir)for(const dc of minesDir){if(!dr&&!dc)continue;const rr=r+dr,cc=c+dc;if(rr>=0&&rr<9&&cc>=0&&cc<9)out.push(rr*9+cc);}return out;}
function plant(first){const s=state,banned=new Set([first,...neighbours(first)]);const positions=Array.from({length:81},(_,i)=>i).filter(i=>!banned.has(i));for(let i=positions.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[positions[i],positions[j]]=[positions[j],positions[i]];}
for(const at of positions.slice(0,s.mines))s.cells[at]=-1;for(let i=0;i<81;i++)if(s.cells[i]!==-1)s.cells[i]=neighbours(i).filter(j=>s.cells[j]===-1).length;s.started=true;gameStart=Date.now();}
function revealAt(start){const s=state;if(finished||s.flags[start]||s.open[start])return;if(!s.started)plant(start);if(s.cells[start]===-1){s.open[start]=true;drawMines();end("Boom! You hit a mine");drawMines();return;}
const todo=[start];const seen=new Set();while(todo.length){const i=todo.pop();if(seen.has(i)||s.flags[i]||s.open[i])continue;seen.add(i);s.open[i]=true;if(s.cells[i]===0)todo.push(...neighbours(i));}
score(s.open.filter(Boolean).length);
if(s.open.filter(Boolean).length===81-s.mines){end("You cleared the whole field!");}
drawMines();}
function mineTap(i){if(finished)return;const s=state;if(s.flagMode){if(!s.open[i])s.flags[i]=!s.flags[i];drawMines();return;}revealAt(i);}
function drawMines(){const s=state;board.innerHTML="";const fragment=document.createDocumentFragment();for(let i=0;i<81;i++){const b=document.createElement("button");b.type="button";b.dataset.tile=String(i);b.setAttribute("aria-label","Square "+(i+1)+(s.flags[i]?", flagged":s.open[i]?", revealed":""));if(s.open[i]){b.className="revealed"+(s.cells[i]===-1?" mine":"");if(s.cells[i]===-1)b.textContent="✹";else if(s.cells[i]>0){b.textContent=s.cells[i];b.style.color=["","#2868b1","#328455","#bc4242","#663a8d","#b27026","#257a7d","#424a4b","#111"][s.cells[i]];}}
else if(s.flags[i]){b.textContent="⚑";b.className="flag";}fragment.appendChild(b);}board.append(fragment);if(!finished)message(s.flagMode?"Flag mode — mark suspected mines.":"Tap a square to uncover it.");
extra.innerHTML='<div class="flag-row"><button type="button" data-control="reveal" aria-pressed="'+!s.flagMode+'">Reveal</button><button type="button" data-control="flag" aria-pressed="'+s.flagMode+'">Flag ⚑</button></div><p>Flags: '+s.flags.filter(Boolean).length+'/'+s.mines+' · Safe squares: '+s.open.filter(Boolean).length+'/'+(81-s.mines)+'</p>';if(s.started&&!finished&&points)score(s.open.filter(Boolean).length);}
function flagMode(f){state.flagMode=f;drawMines();}
/* MAZE CHASE — 19x21 authored corridors, collectible dots and pursuing ghosts. */
function setupMaze(){c.width=456;c.height=504;const walls=[];for(let y=0;y<21;y++){const row=[];for(let x=0;x<19;x++){let w=x===0||x===18||y===0||y===20;
if(!w&&y>1&&y<19){if((y===4||y===8||y===12||y===16)&&x>=2&&x<=16&&x!==5&&x!==13)w=true;
if((x===4||x===9||x===14)&&y>1&&y<19&&y!==3&&y!==7&&y!==11&&y!==15&&y!==18)w=true;}row.push(w?1:0);}walls.push(row);}
for(let y of [3,7,11,15,18])for(let x of [4,9,14])walls[y][x]=0;
const pills=walls.map((row,y)=>row.map((cell,x)=>cell?0:1));pills[1][1]=0;pills[18][17]=0;
state={walls,pills,p:{x:1,y:1,dir:[1,0]},ghosts:[{x:17,y:18,t:0},{x:17,y:1,t:0}],lives:3,total:pills.flat().reduce((a,b)=>a+b,0),elapsed:0,invincible:0};
setControls(button("◀","left","Left")+button("▲","up","Up")+button("▼","down","Down")+button("▶","right","Right")+button("Ⅱ","pause","Pause"));message("Collect the dots. Avoid the ghosts.");interval=setInterval(mazeTick,160);drawMaze();}
function mazeMove(dx,dy){const s=state;if(type!=="maze"||finished)return;s.p.dir=[dx,dy];}
function moveable(s,x,y){return y>=0&&y<21&&x>=0&&x<19&&s.walls[y][x]===0;}
function ghostStep(g,s){const dirs=[[1,0],[-1,0],[0,1],[0,-1]].filter(([dx,dy])=>moveable(s,g.x+dx,g.y+dy));if(!dirs.length)return;let picks=dirs.map(([dx,dy])=>({x:g.x+dx,y:g.y+dy,cost:Math.abs(g.x+dx-s.p.x)+Math.abs(g.y+dy-s.p.y)+Math.random()*8}));picks.sort((a,b)=>a.cost-b.cost);const chosen=picks[0];g.x=chosen.x;g.y=chosen.y;}
function mazeTick(){const s=state;if(paused||finished)return;s.elapsed++;if(s.invincible>0)s.invincible--;const [dx,dy]=s.p.dir;if(moveable(s,s.p.x+dx,s.p.y+dy)){s.p.x+=dx;s.p.y+=dy;}if(s.pills[s.p.y][s.p.x]){s.pills[s.p.y][s.p.x]=0;score(points+10);s.total--;}
if(s.elapsed%3===0)s.ghosts.forEach(g=>ghostStep(g,s));if(s.invincible===0&&s.ghosts.some(g=>g.x===s.p.x&&g.y===s.p.y)){s.lives--;s.invincible=8;s.p={x:1,y:1,dir:[1,0]};if(s.lives===0){end("The ghosts caught you!");}}
if(s.total===0)end("You collected every dot!");drawMaze();}
function drawMaze(){const s=state,ts=24;ctx.fillStyle="#101f23";ctx.fillRect(0,0,c.width,c.height);
for(let y=0;y<21;y++)for(let x=0;x<19;x++){const px=x*ts,py=y*ts;if(s.walls[y][x]){ctx.fillStyle="#31577a";ctx.fillRect(px,py,24,24);ctx.fillStyle="#668eaa";ctx.fillRect(px+3,py+3,18,3);}else if(s.pills[y][x]){ctx.fillStyle="#e8d0a0";ctx.beginPath();ctx.arc(px+12,py+12,2.5,0,Math.PI*2);ctx.fill();}}
s.ghosts.forEach((g,i)=>{const px=g.x*ts+12,py=g.y*ts+12;ctx.fillStyle=i===0?"#e85253":"#ec95aa";ctx.beginPath();ctx.arc(px,py,10,Math.PI,0);ctx.lineTo(px+10,py+9);ctx.lineTo(px-10,py+9);ctx.closePath();ctx.fill();ctx.fillStyle="#fff";ctx.fillRect(px-6,py-2,5,6);ctx.fillRect(px+2,py-2,5,6);ctx.fillStyle="#252525";ctx.fillRect(px-5,py,2,3);ctx.fillRect(px+3,py,2,3);});
if(s.invincible%2===0){ctx.fillStyle="#f6d461";ctx.beginPath();ctx.arc(s.p.x*ts+12,s.p.y*ts+12,10,.22*Math.PI,1.78*Math.PI);ctx.lineTo(s.p.x*ts+12,s.p.y*ts+12);ctx.closePath();ctx.fill();}
extra.textContent="Lives: "+s.lives+" · Dots left: "+s.total+(paused?" · PAUSED":"");if(paused){ctx.fillStyle="#112329bc";ctx.fillRect(0,0,456,504);drawText("PAUSED",250,34);}if(finished){ctx.fillStyle="#112329bc";ctx.fillRect(0,0,456,504);drawText("GAME OVER",250,31);}}
function action(control){if(type==="bird"&&control==="flap")flap();if(type==="blocks")blockMove(control);if(type==="maze"){if(control==="pause"){paused=!paused;message(paused?"Paused":"Back in the maze");drawMaze();}else{const dirs={left:[-1,0],right:[1,0],up:[0,-1],down:[0,1]};if(dirs[control])mazeMove(...dirs[control]);}}if(type==="mines"&&(control==="flag"||control==="reveal"))flagMode(control==="flag");}
$("picks").addEventListener("click",e=>{const target=e.target.closest("[data-game]");if(target)pick(target.dataset.game);});
$("restart").addEventListener("click",restart);
document.addEventListener("click",e=>{const control=e.target.closest("[data-control]");if(control)action(control.dataset.control);});
board.addEventListener("click",e=>{if(type==="mines"){const b=e.target.closest("[data-tile]");if(b){if(held){held=false;return;}mineTap(Number(b.dataset.tile));}}});
board.addEventListener("contextmenu",e=>{if(type!=="mines")return;const b=e.target.closest("[data-tile]");if(!b)return;e.preventDefault();const s=state,i=Number(b.dataset.tile);if(!finished&&!s.open[i]){s.flags[i]=!s.flags[i];drawMines();}});
let holdTimer=null,held=false;
board.addEventListener("pointerdown",e=>{if(type!=="mines")return;const b=e.target.closest("[data-tile]");if(!b)return;held=false;const at=Number(b.dataset.tile);holdTimer=setTimeout(()=>{if(type==="mines"&&!finished&&!state.open[at]){state.flags[at]=!state.flags[at];drawMines();held=true;}},550);});
board.addEventListener("pointerup",()=>{if(holdTimer!==null)clearTimeout(holdTimer);holdTimer=null;});
board.addEventListener("pointercancel",()=>{if(holdTimer!==null)clearTimeout(holdTimer);holdTimer=null;});
c.addEventListener("pointerdown",e=>{if(type==="bird")flap();});
let startTouch=null;
c.addEventListener("touchstart",e=>{if(type==="maze"&&e.touches.length===1)startTouch=[e.touches[0].clientX,e.touches[0].clientY];},{passive:true});
c.addEventListener("touchend",e=>{if(type!=="maze"||!startTouch||!e.changedTouches.length)return;const dx=e.changedTouches[0].clientX-startTouch[0],dy=e.changedTouches[0].clientY-startTouch[1];if(Math.abs(dx)>18||Math.abs(dy)>18)mazeMove(...(Math.abs(dx)>Math.abs(dy)?[Math.sign(dx),0]:[0,Math.sign(dy)]));startTouch=null;},{passive:true});
document.addEventListener("keydown",e=>{if(e.ctrlKey||e.metaKey||e.altKey||e.target.closest("input,textarea,select"))return;const k=e.key.toLowerCase();let cmd={arrowleft:"left",arrowright:"right",arrowup:"rotate",arrowdown:"down",w:"up",a:"left",s:"down",d:"right"," ":"drop",p:"pause"}[k];if(type==="bird"&&(k===" "||k==="arrowup")){e.preventDefault();flap();return;}if(type==="maze"){cmd={arrowleft:"left",arrowright:"right",arrowup:"up",arrowdown:"down",w:"up",a:"left",s:"down",d:"right",p:"pause"}[k];}if(cmd){e.preventDefault();action(cmd);}});
document.addEventListener("visibilitychange",()=>{if(document.hidden&&type==="blocks"&&!finished){paused=true;message("Paused");}if(document.hidden&&type==="maze"&&!finished){paused=true;message("Paused");}});
window.addEventListener("pagehide",stop);
window.addEventListener("popstate",()=>pick(new URL(location.href).searchParams.get("game")||"bird"));
if(location.hostname==="localhost"||location.hostname==="127.0.0.1")window.__nightArcadeTest=Object.freeze({snapshot:()=>JSON.parse(JSON.stringify(state)),flags:()=>({finished,paused,points})});
pick(new URL(location.href).searchParams.get("game")||"bird");
})();
