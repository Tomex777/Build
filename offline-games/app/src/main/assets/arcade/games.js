/* NIGHT Arcade: 4 original mobile-browser arcade games, local scores only. */
(()=>{"use strict";
const $=id=>document.getElementById(id);const c=$("screen"),ctx=c.getContext("2d");const board=$("mineboard"),touch=$("touchbar"),extra=$("extra");
const palette={dark:"#173e2c",light:"#dbe9ce",gold:"#e7b24c",danger:"#e6534b",blue:"#61a0bf"};
const metadata={
bird:["Wing Hop","Tap to fly, glide through the gaps.","Tap anywhere or press Space to flap. Pass through pipe gaps without touching the pipes or ground."],
blocks:["Block Drop","Stack, clear lines, keep going.","Tap the board to rotate. Drag sideways to move; drag down to soft drop; flick down quickly to hard drop. Or use the grouped buttons and arrow keys (Space hard-drops)."],
mines:["Minesweeper","One tap at a time, clear the field.","Reveal every safe square. Numbers count the mines in neighbouring squares. Switch to Flag to mark suspected mines. Your first square and its neighbours are protected."],
maze:["Maze Chase","Collect every dot. Avoid the chasers.","Swipe on the maze, use the arrows, or press WASD. Eat the dots; dodge the red ghosts. Collect all dots to win."]
};
let type="bird",frame=0,raf=null,interval=null,life=null,state=null,finished=false,paused=false,points=0,last=0;
let gameStart=0,blockDirty=true,blockPaintCount=0,wingBackdrop=null,mazeBackdrop=null,mazeDots=null,mazeDotsCtx=null;
function stop(){if(raf!==null){cancelAnimationFrame(raf);raf=null;}if(interval!==null){clearInterval(interval);interval=null;}}
function best(id){try{return Math.max(0,Number(localStorage.getItem("night-play-v1-"+id))||0);}catch{return 0;}}
function record(id,n){const score=Math.round(Math.max(0,n));try{const key="night-play-v1-"+id;if(score>best(id))localStorage.setItem(key,String(score));const histKey="night-play-history-v1-"+id;let arr=[];try{arr=JSON.parse(localStorage.getItem(histKey)||"[]");}catch{}if(!Array.isArray(arr))arr=[];arr.push(score);localStorage.setItem(histKey,JSON.stringify(arr.slice(-12)));const countKey="night-play-history-count-v1-"+id;const count=Math.max(0,Number(localStorage.getItem(countKey))||0);localStorage.setItem(countKey,String(count+1));}catch{}}
function score(n){points=Math.max(0,Math.floor(n));$("score").textContent=String(points);$("best").textContent=String(Math.max(best(type),points));}
function message(t){$("message").textContent=t;}
function end(text){if(finished)return;finished=true;stop();record(type,points);message(text+" · Tap Restart to play again.");}
function drawText(text,y,size=24){ctx.fillStyle="#fff";ctx.font="900 "+size+"px system-ui,Arial";ctx.textAlign="center";ctx.fillText(text,c.width/2,y);}
function pick(id){if(!metadata[id])id="bird";type=id;const [title,sub,rules]=metadata[id];$("title").textContent=title;$("subtitle").textContent=sub;$("rules").textContent=rules;document.title=title+" · NIGHT Play";[...$("picks").querySelectorAll("button")].forEach(b=>b.setAttribute("aria-current",String(b.dataset.game===id)));const url=new URL(location.href);url.searchParams.set("game",id);history.replaceState({},"",url.pathname+url.search);if($("settingsDialog").open)$("settingsDialog").close();restart();}
function restart(){stop();resetBlockInputs();$("blockPause").hidden=type!=="blocks";$("wingSkin").hidden=type!=="bird";
if(type!=="blocks"){c.style.removeProperty("--block-fit-height");c.style.removeProperty("--block-fit-width");}
if(type!=="bird"){c.style.removeProperty("--wing-fit-height");c.style.removeProperty("--wing-fit-width");}
last=0;gameStart=Date.now();finished=false;paused=false;syncBlockPauseButton();score(0);message("Ready to play.");extra.replaceChildren();board.hidden=type!=="mines";c.hidden=type==="mines";c.className="mode-"+type;
if(type==="bird")setupBird();if(type==="blocks")setupBlocks();if(type==="mines")setupMines();if(type==="maze")setupMaze();}
function button(label,action,aria=label){return '<button type="button" data-control="'+action+'" aria-label="'+aria+'">'+label+'</button>';}
function setControls(html){touch.innerHTML=html;}
function frameLoop(cb){let prev=0;function tick(t){if(finished)return;const dt=Math.min(42,prev?t-prev:16);prev=t;cb(dt,t);if(!finished)raf=requestAnimationFrame(tick);}raf=requestAnimationFrame(tick);}
/* Offline-embedded CC0 1.0 sprites from Kenney (Animal Pack Remastered and
   Background Elements Remastered). Source: https://kenney.nl/; bundled locally
   as data URIs; no network or hotlinked artwork required during gameplay. */
const wingArt={};
const wingArtSources={"parrot":"data:image/png;base64,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","chick":"data:image/png;base64,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","cloud1":"data:image/png;base64,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","bush1":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHgAAAA8CAMAAACac46aAAAABGdBTUEAALGPC/xhBQAAAGNQTFRFILxzHbtxIL10IbxzIr11Ibx0ILxzGbVrIr1zHLpxIbhyIb50EapmIL10I710AAAAIbdxH69sHqloILRvIrt0H61rIbhyILBtHqppIbVwIrpzILJuIr11H6xqHqdnI792HqZmA9mCeAAAABB0Uk5Tb0+P36/PXx+fPy/vD3+/AP+1SzwAAAKlSURBVFjDxZfXtoMgEEUxsYOaorEz/P9XXgvJtYAKpJyXPETWXsDhzAxKTOVZWsuQKTfE0U/AFoafgF2An4BD+A04xL8Bk3PPBR/58ajIIl8Bn2AtO/g8OAChnPDT4FgMBux+FuyCVP5HwbEcfMTo2mAPtuS+A+yfhAs3wRAag4kDQBRPutOZGIJ7Loi2DDvyzcADd9iyF0TPgOoiCrl7YPCMwD7PIxuU1R9T4GiCEegLJ54NZz1wACbqTynWAvPqY6KTFjgy5m4ECVLJpuZSZzfG1eb3ysDa6HgYV1fKFirKZpPr6DynhbMuLROJ5o1eZKNjG67E2AFdy18UUQOTVwP51J2yDbWpRmwKwAT3pWWSVU3LtkVL9RKFRK0FthIy4RZsV0KyrZjVffPoTt7wdZ/LaKVaJgTgoSDh1/KcHRFtFAsj2qu0JTumolGwtBA8szOk9CCY5WptF9oJjpYd1mO2ME6MwOVxLrvNTzpUBc9q0k0BzOo9sucfBatseOXstbusSX1eg63J2kIJzO47ARJNLLd5x5Ual93qucECQd0JjzynO1MWvVZyZ/etlEMOBEjLdJQ1ktAk0zZMFpmDmJ7+i5Ud2XwEsCPveYmBDOzrXrE8xfg8xX9k4JetH9rgpb8FYYpCIr3kWh/Myq0Zo0ei6Pw0uBc58w9MwHSr9e2PGcWAgzHOVv+bgNdlcnrZPdgZD93C8F7wmNxpzp9kkT/myTLOgyfheGYG7pK7yma5dpk2J8tBNC2ztlfWzScPZrblnMqiBS/Bs3GBZuzdKv7PegpO309a6voaX9H/DFxR9nnVz/KBYpW+/Q3i5u4CxKwUKavl0w0K9WuvyZYRSsZLTr/F5f6KEG/u6q+B6egu1E2lyn2smYZp2kFjhlTf4/JL/gMD8qKcrLGLPgAAAABJRU5ErkJggg==","bush2":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADUAAAAsCAMAAADy4QARAAAABGdBTUEAALGPC/xhBQAAAYBQTFRFILVvHqRkFqNiHqRkHaVlH6xpHqNjHaNjHKZlGqVhHqlmHKdlHqZmHqtpGKZlGqtqHqRmHKdmH6tpHrFqH7JsH61pH6lnH7RvH7RuHrFrH7FtH7RvHatoHKtoHrdwHq1qHq9rH6xpHa5qH7RvHLZtH7FuHrFuIL10ILlwIrpyHq9rH6xqIrlyIbNsH7dwEapVHrRuHrlxIbtzH7FtHq9qILlyHKVkGZxaH6lnFqhiHqRjHrdvH69sHrZuILpxIbx0ILxzAJlVHK5pHbtxGa1jIbhyIL10GaRjGbVrIr11Ir1zI710Ib50ILxzIbxzHLpxEapmAAAAHqppHqlnILZwIbtzH7BsIbpzHqdmH6xpH61rIbpyIrxzHqhnHqZmILBtIbdwIrt0HqloH69rHqdnHqpoIbVwILJuH6tpH69sIbhyILJtH61qIrpyH6xqH65rILFtIr11IbVvILBsIr10IrpzIbhxIbdxIblyILNuILRvIrtzIbZwIrx0Ir51I792NDIecAAAAFJ0Uk5Tv88v75+/b5+vT8+/X+8/T99/33/fr7+/z1/v359/j++fz0/vP89fj4+fj8+vL+8Pj1/fv2+Pfx+vL1+f71/vz28PP08fL38fH6+fv+9f3z8PAOkksk8AAAKlSURBVEjHjdRVl+owEABgrru7u7utu3GBpSRXV3EtUKFt6uSvbwoFtkrnrefkOzOZziQGI0VmaSOVSsUm7c/YeAHSiU1sRW4CRlU/k32CC7+OwKhqwzZYKmePRlTri7bB1XJ2AkZTq4NE2NjKHluOptJ4iPb3y2dgJLU+zCRXisVzMJIC1AAp2/V65WI0lRggfafFMFMwkhpeSvxXYtlWPJIa1acJQqN0H7rV+RqPcSqxlHH83WEnmhwnCHGPgjPlXKF3IjmCg/6ZmqrWmo+hV8GT2XKO7x2i0sBxq261oxG34qfgqa3yb9Q/R2UON/B/3qh2Oi+gr4Kni8WK0E+HN8ig24hHSM4bxssAtXy2XmdaUv9sEgwKlBSFRvIzEKDg9AWmxbJ2lcmknUrXpbaizMIgBS+xbEMQjC4eRVc0Tcs9D1bwRkPguNofB7NcYQGGqLU5rqmqGjrMiOPFN2EKXqupmtYxEMZOtxqq4HViDBn9dTIMwtWdqiHLSGmLDkTBcAXvEaNIuu64WtJ63jJWAH81TyttSTdFh6JSw6VZfLXuo4BlTL6Lg4Na8m7l+4IrkU9sZtzqi8iPQyQSwFbAui25dagxJcNaNubqSk+lU4OLBwtaK7Gt7Xpl//JMr8JValxJOlJrnNBgW0y9csV6uGMgMYZ0d5HR0dQmJ5R2mJv9Zye2coIhm1XqyLq/2WsrslHVeskePRj95cnjxQpZZJbLe3vBm7qkINlKxgnv4o7ZiE8R1CCLQvPuVGJBUuiear71zOH0k0auqZKZ33XlE029raB8Vbv71G96v89ZSKbbrmR2ibfXAnb5G0FI2XOXSJLR8i0QtCmfPhMk8X79mAUh+zX/UdLdfbRKfBi6lfDDgulpvmi+Dt9lMr5fvf/5h/cFOADLOHc59xXmwgAAAABJRU5ErkJggg==","cloud3":"data:image/png;base64,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"};
for(const [name,src] of Object.entries(wingArtSources)){const img=new Image();img.src=src;wingArt[name]=img}
function readyArt(name){const img=wingArt[name];return img&&img.complete&&img.naturalWidth>0?img:null}
function drawWingBackground(s){
 const h=c.height,ground=h-42;
 // Sky and sun are cached until the backing canvas changes size.
 if(wingBackdrop)ctx.drawImage(wingBackdrop,0,0);
 for(let i=0;i<6;i++){
  const img=readyArt(i%2===0?"cloud1":"cloud3");
  const x=((i*141-s.distance*.14)%475+475)%475-95;
  const y=34+(i%3)*Math.max(60,(ground-115)/5);
  if(img){ctx.globalAlpha=.9;ctx.drawImage(img,x,y,i%2?109:132,i%2?59:65);ctx.globalAlpha=1;}
 }
 ctx.fillStyle="#89bba4";ctx.beginPath();ctx.moveTo(0,ground-29);
 for(let i=0;i<=360;i+=30)ctx.lineTo(i,ground-69+Math.sin((i+s.distance*.18)/53)*19);
 ctx.lineTo(360,ground+7);ctx.lineTo(0,ground+7);ctx.fill();
 ctx.fillStyle="#539579";ctx.beginPath();ctx.moveTo(0,ground-9);
 for(let i=0;i<=360;i+=25)ctx.lineTo(i,ground-35+Math.sin((i+s.distance*.27)/34)*12);
 ctx.lineTo(360,ground+7);ctx.lineTo(0,ground+7);ctx.fill();
 const bush=readyArt("bush1"),bush2=readyArt("bush2");
 for(let i=0;i<7;i++){
  const x=((i*87-s.distance*.39)%520+520)%520-98;
  const sprite=i%2?bush:bush2;
  if(sprite)ctx.drawImage(sprite,x,ground-32+(i%3)*7,115,56);
 }
}
function wingPipe(x,edge,isTop){
 const cap=18;
 const ground=c.height-42;
 if(edge<=0)return;
 const start=isTop?0:edge,bottom=isTop?edge:ground,h=Math.max(0,bottom-start);
 const grad=ctx.createLinearGradient(x,0,x+62,0);
 grad.addColorStop(0,"#174d44");grad.addColorStop(.18,"#397f68");grad.addColorStop(.38,"#7ac699");
 grad.addColorStop(.65,"#428e72");grad.addColorStop(1,"#205d50");
 ctx.fillStyle=grad;ctx.fillRect(x,start,62,h);
 ctx.strokeStyle="#244e44";ctx.lineWidth=3;ctx.strokeRect(x+1,start,60,h);
 const capY=isTop?Math.max(0,edge-cap):edge;
 const lip=ctx.createLinearGradient(x-5,0,x+67,0);
 lip.addColorStop(0,"#204b46");lip.addColorStop(.32,"#75c69a");
 lip.addColorStop(.64,"#3f916f");lip.addColorStop(1,"#1c5148");
 ctx.fillStyle=lip;
 ctx.beginPath();ctx.roundRect(x-6,capY,74,Math.min(cap,h),5);ctx.fill();
 ctx.strokeStyle="#244e44";ctx.lineWidth=3;ctx.stroke();
 ctx.fillStyle="#c0efc277";
 if(h>cap+3)ctx.fillRect(x+9,start+(isTop?2:cap),5,h-cap-3);
}
function wingBird(s){
 const sprite=readyArt(s.skin||"parrot");
 ctx.save();ctx.translate(s.x,s.y);
 ctx.rotate(Math.max(-.3,Math.min(.65,s.vy/16)));
 // A soft illustrated wing motion sits behind the actual sprite.
 const lift=Math.sin(performance.now()*.018)*6;
 ctx.fillStyle=s.skin==="chick"?"#e8b12e":"#ac381f";
 ctx.beginPath();ctx.ellipse(-14,9+lift*.28,14,9,-.35+lift*.04,0,Math.PI*2);ctx.fill();
 if(sprite){ctx.imageSmoothingEnabled=true;ctx.drawImage(sprite,-26,-26,52,52);}
 else{ctx.fillStyle="#ebc63e";ctx.beginPath();ctx.arc(0,0,20,0,Math.PI*2);ctx.fill();}
 ctx.restore();
}
/* WING HOP — frame-rate independent motion, real bundled art and fast touch. */
/* WING HOP — variable-height world keeps sprites and physics proportional.
   Resize updates actual gameplay space without restarting or stretching art. */
let wingFitFrame=0,wingCssWidth=0,wingCssHeight=0;
function wingGap(height){return Math.max(122,Math.min(175,(height-42)*.33));}
function wingPipeCenter(s){
 const min=s.gap/2+34,max=c.height-42-s.gap/2-34;
 return Math.max(min,min+Math.random()*Math.max(0,max-min));
}
function rebuildWingBackdrop(){
 wingBackdrop=document.createElement("canvas");
 wingBackdrop.width=360;wingBackdrop.height=c.height;
 const bg=wingBackdrop.getContext("2d");
 const sky=bg.createLinearGradient(0,0,0,c.height-42);
 sky.addColorStop(0,"#81c9d3");sky.addColorStop(.6,"#c5eeed");sky.addColorStop(1,"#e8f4ce");
 bg.fillStyle=sky;bg.fillRect(0,0,360,c.height);
 bg.fillStyle="#fbf5cd7d";bg.beginPath();bg.arc(292,Math.min(95,c.height*.17),46,0,Math.PI*2);bg.fill();
}
function fitWingBoard(){
 wingFitFrame=0;
 if(type!=="bird"||c.hidden||!state)return;
 const box=c.getBoundingClientRect(),shell=$("canvasShell").getBoundingClientRect(),
       main=document.querySelector("main").getBoundingClientRect();
 const viewport=window.visualViewport;
 const visibleBottom=viewport?viewport.height+viewport.offsetTop:window.innerHeight;
 // Measure everything already below the game (including Restart), not a
 // hardcoded phone height. The board owns the freed-up flap-button space.
 const lower=Math.max(8,main.bottom-box.bottom);
 const available=Math.max(215,visibleBottom-box.top-lower-8);
 const fullWidth=Math.max(180,Math.min(shell.width,520));
 const height=Math.round(Math.min(available,fullWidth*1.78));
 // Portrait keeps the full phone width; extreme short windows trade a little
 // width to keep the bird artwork recognisable instead of squashed.
 const width=Math.round(Math.min(fullWidth,Math.max(180,height/1.2)));
 if(Math.abs(width-wingCssWidth)<2&&Math.abs(height-wingCssHeight)<2&&
    Math.abs(box.width-width)<2&&Math.abs(box.height-height)<2)return;
 const old=c.height,newLogicalHeight=Math.max(300,Math.round(360*(height-6)/(width-6)));
 wingCssWidth=width;wingCssHeight=height;
 c.style.setProperty("--wing-fit-width",width+"px");
 c.style.setProperty("--wing-fit-height",height+"px");
 if(newLogicalHeight!==old){
  // Remap by playable height. Keep pipes, progress, skin, and the living bird.
  // Avoid spurious death when switching portrait/landscape mid-flight.
  const oldPlay=Math.max(1,old-42),newPlay=newLogicalHeight-42;
  state.y=Math.max(20,Math.min(newPlay-21,state.y/oldPlay*newPlay));
  state.gap=wingGap(newLogicalHeight);
  const min=state.gap/2+34,max=newPlay-state.gap/2-34;
  for(const p of state.pipes)p.y=Math.max(min,Math.min(max,p.y/oldPlay*newPlay));
  c.height=newLogicalHeight;rebuildWingBackdrop();if(finished)birdFrame(0);
 }
}
function scheduleWingFit(){
 if(type!=="bird"||wingFitFrame)return;
 wingFitFrame=requestAnimationFrame(fitWingBoard);
}
window.addEventListener("resize",scheduleWingFit,{passive:true});
window.visualViewport?.addEventListener("resize",scheduleWingFit,{passive:true});
window.visualViewport?.addEventListener("scroll",scheduleWingFit,{passive:true});
if(typeof ResizeObserver==="function"){
 const wingLayoutObserver=new ResizeObserver(scheduleWingFit);
 wingLayoutObserver.observe($("canvasShell"));
 wingLayoutObserver.observe($("restart"));
}
function setupBird(){
 wingCssWidth=0;wingCssHeight=0;
 c.width=360;c.height=600;
 rebuildWingBackdrop();
 let skin="parrot";
 try{skin=localStorage.getItem("night-play-wing-hop-skin")==="chick"?"chick":"parrot"}catch{}
 state={x:95,y:260,vy:0,gap:wingGap(600),pipes:[],distance:0,started:false,dead:false,skin};
 setControls("");
 message("Tap anywhere inside the game to fly.");
 scheduleWingFit();frameLoop(birdFrame);
}
function flap(){
 if(type!=="bird"||finished)return;
 state.started=true;state.vy=-6.8;
 message("Keep flying!");
}
function switchBird(){
 if(type!=="bird")return;
 state.skin=state.skin==="parrot"?"chick":"parrot";
 try{localStorage.setItem("night-play-wing-hop-skin",state.skin)}catch{}
 message((state.skin==="parrot"?"Parrot":"Chick")+" selected.");
}
function birdFrame(dt){
 const s=state,step=dt/16.666,ground=c.height-42;
 if(s.started){
  s.vy+=.33*step;s.y+=s.vy*step;s.distance+=2.4*step;
  if(!s.pipes.length||s.pipes[s.pipes.length-1].x<165){
   const prev=s.pipes[s.pipes.length-1];
   if(!prev||prev.x<150)s.pipes.push({
    x:prev?prev.x+216:365,y:wingPipeCenter(s),scored:false
   });
  }
  for(const p of s.pipes){
   p.x-=2.4*step;
   if(!p.scored&&p.x+62<s.x){p.scored=true;score(points+1);}
   if(s.x+16>p.x&&s.x-16<p.x+62&&
      (s.y-15<p.y-s.gap/2||s.y+15>p.y+s.gap/2)){end("You hit a pipe!");}
  }
  s.pipes=s.pipes.filter(p=>p.x>-75);
  if(s.y<17||s.y>ground-19)end("Bird down!");
 }
 drawWingBackground(s);
 for(const p of s.pipes){
  wingPipe(p.x,p.y-s.gap/2,true);
  wingPipe(p.x,p.y+s.gap/2,false);
 }
 ctx.fillStyle="#236e5a";ctx.fillRect(0,ground,360,42);
 ctx.fillStyle="#8cc670";ctx.fillRect(0,ground,360,9);
 ctx.fillStyle="#d7d19a";
 for(let i=0;i<14;i++){
  const x=((i*31-s.distance*.7)%434+434)%434-35;
  ctx.fillRect(x,ground+19,12,2);
 }
 wingBird(s);
 if(!s.started){
  // Keep instructions high and away from the bird's starting position.
  // One compact in-world cue, not a large panel covering the character.
  const top=Math.max(27,Math.min(ground*.14,88));
  ctx.fillStyle="#173c31b9";ctx.beginPath();
  ctx.roundRect(65,top,230,57,13);ctx.fill();
  drawText("TAP TO FLY",top+36,23);
 }
 if(finished){
  const top=Math.max(45,ground*.5-65);
  ctx.fillStyle="#163c31c5";ctx.beginPath();
  ctx.roundRect(25,top,310,130,15);ctx.fill();
  drawText("ROUND OVER",top+48,27);
  drawText("Your score: "+points,top+85,20);
 }
}
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
function setupBlocks(){c.width=300;c.height=600;blockDirty=true;state={grid:Array.from({length:20},()=>Array(10).fill(null)),cur:null,bag:[],time:0,lines:0,level:1,canHold:true};nextPiece(state);
 setControls('<div class="block-control-layout"><div class="block-move-group" role="group" aria-label="Move falling piece">'+
  button("◀","left","Move left; hold to repeat")+button("▶","right","Move right; hold to repeat")+
  '</div><div class="block-action-group" role="group" aria-label="Rotate and drop">'+
  button("⟳","rotate","Rotate clockwise")+button("▼","down","Soft drop one row")+
  '<button type="button" class="block-hard-drop" data-control="drop" aria-label="Hard drop piece instantly">⬇</button>'+
  '</div></div>');
 message("Tap to rotate · Drag to move · Flick down to drop");scheduleBlockFit();frameLoop(blockFrame);}
function rotate(s){const old=s.cur.cells,turn=old.map(([x,y])=>[3-y,x]);for(const shift of [0,-1,1,-2,2])if(!collision(s,shift,0,turn)){s.cur.cells=turn;s.cur.x+=shift;return;}}
function blockMove(move){const s=state;if(type!=="blocks"||finished)return;if(move==="pause"){resetBlockInputs();paused=!paused;syncBlockPauseButton();blockDirty=true;message(paused?"Paused":"Back in the game");return;}if(paused)return;blockDirty=true;if(move==="left"&&!collision(s,-1,0))s.cur.x--;if(move==="right"&&!collision(s,1,0))s.cur.x++;if(move==="rotate")rotate(s);if(move==="down"){if(!collision(s,0,1))s.cur.y++;else lockBlock(s);}if(move==="drop"){while(!collision(s,0,1))s.cur.y++;lockBlock(s);}}
function lockBlock(s){for(const [x,y] of s.cur.cells){let xx=x+s.cur.x,yy=y+s.cur.y;if(yy<0){end("Stack too high!");return;}if(yy<20)s.grid[yy][xx]=s.cur.idx;}let count=0;for(let y=19;y>=0;y--){if(s.grid[y].every(k=>k!==null)){s.grid.splice(y,1);s.grid.unshift(Array(10).fill(null));count++;y++;}}
if(count){const n=[0,100,300,500,800][count]*s.level;s.lines+=count;s.level=1+Math.floor(s.lines/10);score(points+n);message("Cleared "+count+" line"+(count===1?"":"s")+"!");}
nextPiece(s);}
function blockFrame(dt){const s=state;if(!paused&&!finished){s.time+=dt;if(s.time>Math.max(115,640-(s.level-1)*44)){s.time=0;blockDirty=true;if(!collision(s,0,1))s.cur.y++;else lockBlock(s);}}
if(!blockDirty)return;blockDirty=false;blockPaintCount++;
ctx.fillStyle="#15252a";ctx.fillRect(0,0,300,600);for(let y=0;y<20;y++)for(let x=0;x<10;x++){drawBlock(x,y,s.grid[y][x]);}
if(s.cur&&!finished){
 // Soft landing projection makes the board more legible without extra frames.
 let drop=0;while(!collision(s,0,drop+1))drop++;
 if(drop){ctx.save();ctx.globalAlpha=.18;for(const [x,y] of s.cur.cells)drawBlock(x+s.cur.x,y+s.cur.y+drop,s.cur.idx);ctx.restore();}
 for(const [x,y] of s.cur.cells)drawBlock(x+s.cur.x,y+s.cur.y,s.cur.idx);
}
ctx.strokeStyle="#596a5b";ctx.lineWidth=5;ctx.strokeRect(1,1,298,598);
const info=extra;info.textContent="Lines: "+s.lines+" · Level: "+s.level;
if(paused){ctx.fillStyle="#0b161bd6";ctx.fillRect(0,0,300,600);drawText("PAUSED",290);}
if(finished){ctx.fillStyle="#0b1519c8";ctx.fillRect(0,0,300,600);drawText("GAME OVER",285);drawText("Score: "+points,318,19);}}
function drawBlock(x,y,idx){const px=x*30,py=y*30;if(idx===null){ctx.strokeStyle="#32474b";ctx.lineWidth=.55;ctx.strokeRect(px+.5,py+.5,29,29);return;}
 ctx.fillStyle=pieceColors[idx];ctx.fillRect(px+1,py+1,28,28);
 ctx.fillStyle="#ffffff5e";ctx.fillRect(px+2,py+2,26,3);ctx.fillRect(px+2,py+5,3,19);
 ctx.fillStyle="#071a2c4c";ctx.fillRect(px+2,py+25,26,3);ctx.fillRect(px+25,py+4,3,21);
}
/* MINESWEEPER — generated safe on first opening. */
const minesDir=[-1,0,1];
function setupMines(){c.width=360;c.height=360;state={cells:Array(81).fill(0),open:Array(81).fill(false),flags:Array(81).fill(false),started:false,flagMode:false,mines:10,timer:0};setControls("");message("Reveal a square to begin.");drawMines();}
function neighbours(i){const r=Math.floor(i/9),c=i%9,out=[];for(const dr of minesDir)for(const dc of minesDir){if(!dr&&!dc)continue;const rr=r+dr,cc=c+dc;if(rr>=0&&rr<9&&cc>=0&&cc<9)out.push(rr*9+cc);}return out;}
function plant(first){const s=state,banned=new Set([first,...neighbours(first)]);const positions=Array.from({length:81},(_,i)=>i).filter(i=>!banned.has(i));for(let i=positions.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[positions[i],positions[j]]=[positions[j],positions[i]];}
for(const at of positions.slice(0,s.mines))s.cells[at]=-1;for(let i=0;i<81;i++)if(s.cells[i]!==-1)s.cells[i]=neighbours(i).filter(j=>s.cells[j]===-1).length;s.started=true;gameStart=Date.now();}
function revealAt(start){const s=state;if(finished||s.flags[start]||s.open[start])return;if(!s.started)plant(start);if(s.cells[start]===-1){s.open[start]=true;drawMines();end("Boom! You hit a mine");drawMines();return;}
const todo=[start];const seen=new Set();while(todo.length){const i=todo.pop();if(seen.has(i)||s.flags[i]||s.open[i])continue;seen.add(i);s.open[i]=true;if(s.cells[i]===0)todo.push(...neighbours(i));}
score(s.open.filter(Boolean).length);
if(s.open.filter(Boolean).length===81-s.mines){end("You cleared the whole field!");}
drawMines();}
function mineTap(i){if(finished)return;const s=state;if(s.flagMode){if(!s.open[i])s.flags[i]=!s.flags[i];drawMines();return;}revealAt(i);}
function drawMines(){
 const s=state;
 // Keep the 81 buttons mounted across reveals and flags, no mass DOM rebuild.
 if(board.children.length!==81){
  const fragment=document.createDocumentFragment();
  for(let i=0;i<81;i++){const b=document.createElement("button");b.type="button";b.dataset.tile=String(i);fragment.appendChild(b)}
  board.replaceChildren(fragment);
 }
 for(let i=0;i<81;i++){
  const b=board.children[i],open=s.open[i],flagged=s.flags[i];
  const klass=open?"revealed"+(s.cells[i]===-1?" mine":""):flagged?"flag":"";
  const label="Square "+(i+1)+(flagged?", flagged":open?", revealed":"");
  const value=open?(s.cells[i]===-1?"✹":s.cells[i]>0?String(s.cells[i]):""):flagged?"⚑":"";
  if(b.className!==klass)b.className=klass;
  if(b.textContent!==value)b.textContent=value;
  if(b.getAttribute("aria-label")!==label)b.setAttribute("aria-label",label);
  const color=open&&s.cells[i]>0?["","#2868b1","#328455","#bc4242","#663a8d","#b27026","#257a7d","#424a4b","#111"][s.cells[i]]:"";
  if(b.style.color!==color)b.style.color=color;
 }
 if(!finished)message(s.flagMode?"Flag mode — mark suspected mines.":"Tap a square to uncover it.");
 if(!extra.querySelector(".flag-row"))extra.innerHTML='<div class="flag-row"><button type="button" data-control="reveal">Reveal</button><button type="button" data-control="flag">Flag ⚑</button></div><p></p>';
 extra.querySelector('[data-control="reveal"]').setAttribute("aria-pressed",String(!s.flagMode));
 extra.querySelector('[data-control="flag"]').setAttribute("aria-pressed",String(s.flagMode));
 const line="Flags: "+s.flags.filter(Boolean).length+"/"+s.mines+" · Safe squares: "+s.open.filter(Boolean).length+"/"+(81-s.mines);
 const p=extra.querySelector("p");if(p.textContent!==line)p.textContent=line;
 if(s.started&&!finished&&points)score(s.open.filter(Boolean).length);
}
function flagMode(f){state.flagMode=f;drawMines();}
/* MAZE CHASE — 19x21 authored corridors, collectible dots and pursuing ghosts. */
function setupMaze(){c.width=456;c.height=504;const walls=[];for(let y=0;y<21;y++){const row=[];for(let x=0;x<19;x++){let w=x===0||x===18||y===0||y===20;
if(!w&&y>1&&y<19){if((y===4||y===8||y===12||y===16)&&x>=2&&x<=16&&x!==5&&x!==13)w=true;
if((x===4||x===9||x===14)&&y>1&&y<19&&y!==3&&y!==7&&y!==11&&y!==15&&y!==18)w=true;}row.push(w?1:0);}walls.push(row);}
for(let y of [3,7,11,15,18])for(let x of [4,9,14])walls[y][x]=0;
const pills=walls.map((row,y)=>row.map((cell,x)=>cell?0:1));pills[1][1]=0;pills[18][17]=0;
state={walls,pills,p:{x:1,y:1,dir:[1,0]},ghosts:[{x:17,y:18,t:0},{x:17,y:1,t:0}],lives:3,total:pills.flat().reduce((a,b)=>a+b,0),elapsed:0,invincible:0};
 // Draw the maze walls and pills once; only entities and removed dots change.
 mazeBackdrop=document.createElement("canvas");mazeBackdrop.width=456;mazeBackdrop.height=504;
 mazeDots=document.createElement("canvas");mazeDots.width=456;mazeDots.height=504;
 mazeDotsCtx=mazeDots.getContext("2d");
 const wallsCtx=mazeBackdrop.getContext("2d");
 wallsCtx.fillStyle="#101f23";wallsCtx.fillRect(0,0,456,504);
 for(let y=0;y<21;y++)for(let x=0;x<19;x++){
  const px=x*24,py=y*24;
  if(walls[y][x]){
   wallsCtx.fillStyle="#31577a";wallsCtx.fillRect(px,py,24,24);
   wallsCtx.fillStyle="#668eaa";wallsCtx.fillRect(px+3,py+3,18,3);
  }else if(pills[y][x]){
   mazeDotsCtx.fillStyle="#e8d0a0";mazeDotsCtx.beginPath();
   mazeDotsCtx.arc(px+12,py+12,2.5,0,Math.PI*2);mazeDotsCtx.fill();
  }
 }
setControls(button("◀","left","Left")+button("▲","up","Up")+button("▼","down","Down")+button("▶","right","Right")+button("Ⅱ","pause","Pause"));message("Collect the dots. Avoid the ghosts.");interval=setInterval(mazeTick,160);drawMaze();}
function mazeMove(dx,dy){const s=state;if(type!=="maze"||finished)return;s.p.dir=[dx,dy];}
function moveable(s,x,y){return y>=0&&y<21&&x>=0&&x<19&&s.walls[y][x]===0;}
function ghostStep(g,s){const dirs=[[1,0],[-1,0],[0,1],[0,-1]].filter(([dx,dy])=>moveable(s,g.x+dx,g.y+dy));if(!dirs.length)return;let picks=dirs.map(([dx,dy])=>({x:g.x+dx,y:g.y+dy,cost:Math.abs(g.x+dx-s.p.x)+Math.abs(g.y+dy-s.p.y)+Math.random()*8}));picks.sort((a,b)=>a.cost-b.cost);const chosen=picks[0];g.x=chosen.x;g.y=chosen.y;}
function mazeTick(){const s=state;if(paused||finished)return;s.elapsed++;if(s.invincible>0)s.invincible--;const [dx,dy]=s.p.dir;if(moveable(s,s.p.x+dx,s.p.y+dy)){s.p.x+=dx;s.p.y+=dy;}if(s.pills[s.p.y][s.p.x]){s.pills[s.p.y][s.p.x]=0;
 if(mazeDotsCtx)mazeDotsCtx.clearRect(s.p.x*24+5,s.p.y*24+5,14,14);
 score(points+10);s.total--;}
if(s.elapsed%3===0)s.ghosts.forEach(g=>ghostStep(g,s));if(s.invincible===0&&s.ghosts.some(g=>g.x===s.p.x&&g.y===s.p.y)){s.lives--;s.invincible=8;s.p={x:1,y:1,dir:[1,0]};if(s.lives===0){end("The ghosts caught you!");}}
if(s.total===0)end("You collected every dot!");drawMaze();}
function drawMaze(){const s=state,ts=24;
 ctx.drawImage(mazeBackdrop,0,0);ctx.drawImage(mazeDots,0,0);
s.ghosts.forEach((g,i)=>{const px=g.x*ts+12,py=g.y*ts+12;ctx.fillStyle=i===0?"#e85253":"#ec95aa";ctx.beginPath();ctx.arc(px,py,10,Math.PI,0);ctx.lineTo(px+10,py+9);ctx.lineTo(px-10,py+9);ctx.closePath();ctx.fill();ctx.fillStyle="#fff";ctx.fillRect(px-6,py-2,5,6);ctx.fillRect(px+2,py-2,5,6);ctx.fillStyle="#252525";ctx.fillRect(px-5,py,2,3);ctx.fillRect(px+3,py,2,3);});
if(s.invincible%2===0){ctx.fillStyle="#f6d461";ctx.beginPath();ctx.arc(s.p.x*ts+12,s.p.y*ts+12,10,.22*Math.PI,1.78*Math.PI);ctx.lineTo(s.p.x*ts+12,s.p.y*ts+12);ctx.closePath();ctx.fill();}
extra.textContent="Lives: "+s.lives+" · Dots left: "+s.total+(paused?" · PAUSED":"");if(paused){ctx.fillStyle="#112329bc";ctx.fillRect(0,0,456,504);drawText("PAUSED",250,34);}if(finished){ctx.fillStyle="#112329bc";ctx.fillRect(0,0,456,504);drawText("GAME OVER",250,31);}}

/* Block Drop touch controls. One pointer = one committed gesture.
   Lock horizontal/vertical intent before moving; never rotate after a drag.
   Work in rendered cell units, so full-width but vertically compressed boards
   have the same predictable finger travel. Gestures don't affect other games. */
function syncBlockPauseButton(){
 const b=$("blockPause");
 b.textContent=paused?"▶":"Ⅱ";
 b.setAttribute("aria-label",paused?"Resume Block Drop":"Pause Block Drop");
 b.setAttribute("aria-pressed",String(paused));
}
let blockFitFrame=0,blockHeight=0,blockWidth=0,blockGesture=null,blockSizeSignature="",blockInputResets=0;
const blockPointers=new Set(),blockSuppressedClicks=new WeakSet();
function fitBlockBoard(){
 blockFitFrame=0;
 if(type!=="blocks"||c.hidden)return;
 const box=c.getBoundingClientRect(),shell=$("canvasShell").getBoundingClientRect(),
       main=document.querySelector("main").getBoundingClientRect();
 const viewport=window.visualViewport;
 const visibleBottom=viewport?viewport.height+viewport.offsetTop:window.innerHeight;
 const sideControls=window.matchMedia("(min-width:651px) and (max-height:610px)").matches;
 // Below-board controls share the same column on portrait, but live beside
 // the board on short landscape windows. main.bottom-board.bottom measures
 // the actual UI below the canvas, avoiding magic viewport subtractions.
 const below=sideControls?9:Math.max(8,main.bottom-box.bottom);
 const available=visibleBottom-box.top-below-5;
 const fullWidth=Math.max(1,Math.min(shell.width,465));
 const target=Math.round(Math.max(90,Math.min(fullWidth*2,available)));
 // Preserve the requested edge-to-edge board on normal portrait phones.
 // When a window becomes very short, full width would squash tiles into
 // paper-thin strips; trade some width for recognisable piece proportions.
 const targetWidth=Math.round(Math.min(fullWidth,Math.max(180,target/1.15)));
 const sizeSignature=[Math.round(window.innerWidth),Math.round(visibleBottom),
                      Math.round(shell.width)].join("/");
 // Only *real* viewport/container resizes cancel a gesture. The board's
 // ResizeObserver can also report its own height updates, which must never
 // silently cancel a finger already moving across the board.
 if(blockSizeSignature&&blockSizeSignature!==sizeSignature)resetBlockInputs();
 blockSizeSignature=sizeSignature;
 if(Math.abs(target-blockHeight)<2&&Math.abs(box.height-target)<2&&
    Math.abs(targetWidth-blockWidth)<2&&Math.abs(box.width-targetWidth)<2)return;
 blockHeight=target;blockWidth=targetWidth;
 c.style.setProperty("--block-fit-height",target+"px");
 c.style.setProperty("--block-fit-width",targetWidth+"px");
}
function scheduleBlockFit(){
 if(type!=="blocks"||blockFitFrame)return;
 blockFitFrame=requestAnimationFrame(fitBlockBoard);
}
window.addEventListener("resize",scheduleBlockFit,{passive:true});
window.visualViewport?.addEventListener("resize",scheduleBlockFit,{passive:true});
window.visualViewport?.addEventListener("scroll",scheduleBlockFit,{passive:true});
if(typeof ResizeObserver==="function"){
 const blockLayoutObserver=new ResizeObserver(scheduleBlockFit);
 blockLayoutObserver.observe($("canvasShell"));
 blockLayoutObserver.observe($("touchbar"));
}

let blockHold=null;
function endBlockHold(){
 if(!blockHold)return;
 clearTimeout(blockHold.timeout);clearInterval(blockHold.interval);
 blockHold=null;
}
function resetBlockInputs(){blockInputResets++;blockGesture=null;blockPointers.clear();endBlockHold();}
function blockTouchMove(e){
 const g=blockGesture;
 if(!g||g.id!==e.pointerId||type!=="blocks"||finished||paused||!state||g.piece!==state.cur)return;
 const dx=e.clientX-g.startX,dy=e.clientY-g.startY;
 // Track the *last moving segment*, not time since the finger first
 // touched down. A flick can follow a stationary hold. A release at the
 // same coordinates must not zero out the last actual movement velocity.
 const sampledAt=performance.now(),deltaT=sampledAt-g.lastMoveAt;
 const deltaY=e.clientY-g.lastY;
 if(deltaY!==0||e.clientX!==g.lastX){
  if(deltaY>6&&deltaT>=0&&deltaT<170){
   // Synthetic and very fast real pointer events can share one timer tick.
   g.lastDownVelocity=deltaY/Math.max(1,deltaT);
   g.lastDownAt=sampledAt;
  }else if(deltaY<=0){g.lastDownVelocity=0;g.lastDownAt=0;}
  g.lastMoveAt=sampledAt;g.lastY=e.clientY;g.lastX=e.clientX;
 }
 const bounds=c.getBoundingClientRect();
 const col=Math.max(12,bounds.width/10),row=Math.max(10,bounds.height/20);
 const threshold=Math.max(10,Math.min(15,col*.35));
 if(!g.axis){
  if(Math.max(Math.abs(dx),Math.abs(dy))<threshold)return;
  if(Math.abs(dx)>Math.abs(dy)*1.16)g.axis="horizontal";
  else if(Math.abs(dy)>Math.abs(dx)*1.16)g.axis="vertical";
  else return; // diagonal ambiguity: wait for a clear dominant direction
 }
 if(g.axis==="horizontal"){
  const step=col*.76;
  const target=Math.abs(dx)<threshold?0:Math.sign(dx)*(1+Math.floor(Math.max(0,Math.abs(dx)-threshold)/step));
  const difference=target-g.steps;
  for(let i=0;i<Math.min(16,Math.abs(difference));i++)blockMove(difference>0?"right":"left");
  g.steps=target;
 }else if(g.axis==="vertical"&&dy>threshold){
  const target=1+Math.floor((dy-threshold)/row);
  // Do not move the NEXT piece if this one touched the stack mid-drag.
  for(let i=g.steps;i<Math.min(target,g.steps+20);i++){
   if(g.piece!==state.cur)break;
   blockMove("down");
  }
  g.steps=target;
 }
}
c.addEventListener("pointerdown",e=>{
 if(type!=="blocks"||paused||finished||e.button!==0)return;
 blockPointers.add(e.pointerId);
 if(blockPointers.size!==1){blockGesture=null;return;} // reject multi-finger ambiguity
 blockGesture={id:e.pointerId,startX:e.clientX,startY:e.clientY,startAt:performance.now(),
              piece:state.cur,axis:null,steps:0,lastY:e.clientY,lastX:e.clientX,
              lastMoveAt:performance.now(),lastDownVelocity:0,lastDownAt:0};
 try{c.setPointerCapture(e.pointerId);}catch{}
 if(e.cancelable)e.preventDefault();
});
c.addEventListener("pointermove",e=>{
 if(type!=="blocks"||!blockGesture||blockGesture.id!==e.pointerId)return;
 blockTouchMove(e);
 if(e.cancelable)e.preventDefault();
});
c.addEventListener("pointerup",e=>{
 if(type!=="blocks"||!blockPointers.has(e.pointerId))return;
 blockPointers.delete(e.pointerId);
 const g=blockGesture;
 if(!g||g.id!==e.pointerId)return;
 blockTouchMove(e);
 const dx=e.clientX-g.startX,dy=e.clientY-g.startY;
 const duration=Math.max(1,performance.now()-g.startAt);
 const recentSpeed=performance.now()-g.lastDownAt<140?g.lastDownVelocity:0;
 blockGesture=null;
 if(finished||paused||!state||g.piece!==state.cur)return;
 if(g.axis==="vertical"&&dy>Math.max(30,c.getBoundingClientRect().height/20*2.25)&&
    Math.abs(dx)<dy*.62&&recentSpeed>.44){
  blockMove("drop"); // quick downward flick, not a slow drag
 }else if(!g.axis&&Math.hypot(dx,dy)<13&&duration<500){
  blockMove("rotate"); // a clean tap; never rotate at end of a swipe
 }
 if(e.cancelable)e.preventDefault();
});
c.addEventListener("pointercancel",e=>{
 blockPointers.delete(e.pointerId);
 if(blockGesture?.id===e.pointerId)blockGesture=null; // never rotate or drop on cancellation
});
c.addEventListener("lostpointercapture",e=>{
 if(blockGesture?.id===e.pointerId){blockGesture=null;blockPointers.delete(e.pointerId);}
});
c.addEventListener("contextmenu",e=>{if(type==="blocks")e.preventDefault();});
/* Direction keys act on finger down and repeat while held, but suppress the
   follow-on click so short presses never move twice. Other controls stay taps. */
touch.addEventListener("pointerdown",e=>{
 if(type!=="blocks"||paused||finished||e.button!==0)return;
 const b=e.target.closest('[data-control="left"],[data-control="right"]');
 if(!b||!touch.contains(b))return;
 endBlockHold();
 const move=b.dataset.control;
 blockSuppressedClicks.add(b);
 blockMove(move);
 const hold={id:e.pointerId,button:b,timeout:null,interval:null};
 blockHold=hold;
 hold.timeout=setTimeout(()=>{
  if(blockHold!==hold||paused||finished||type!=="blocks")return;
  blockMove(move);
  hold.interval=setInterval(()=>{
   if(blockHold!==hold||paused||finished||type!=="blocks"){endBlockHold();return;}
   blockMove(move);
  },74);
 },225);
 try{b.setPointerCapture(e.pointerId);}catch{}
});
for(const eventName of ["pointerup","pointercancel","lostpointercapture"])
 touch.addEventListener(eventName,e=>{
  if(!blockHold||blockHold.id!==e.pointerId)return;
  const b=blockHold.button;
  endBlockHold();
  if(eventName==="pointercancel")blockSuppressedClicks.delete(b);
  else setTimeout(()=>blockSuppressedClicks.delete(b),380);
 });
function action(control){if(type==="bird"&&control==="flap")flap();if(type==="bird"&&control==="bird-skin")switchBird();if(type==="blocks")blockMove(control);if(type==="maze"){if(control==="pause"){paused=!paused;message(paused?"Paused":"Back in the maze");drawMaze();}else{const dirs={left:[-1,0],right:[1,0],up:[0,-1],down:[0,1]};if(dirs[control])mazeMove(...dirs[control]);}}if(type==="mines"&&(control==="flag"||control==="reveal"))flagMode(control==="flag");}
$("picks").addEventListener("click",e=>{const target=e.target.closest("[data-game]");if(target)pick(target.dataset.game);});
$("settingsButton").addEventListener("click",()=>$("settingsDialog").showModal());
$("settingsClose").addEventListener("click",()=>$("settingsDialog").close());
$("restart").addEventListener("click",restart);
document.addEventListener("click",e=>{const control=e.target.closest("[data-control]");if(!control)return;if(type==="blocks"&&blockSuppressedClicks.has(control)){blockSuppressedClicks.delete(control);return;}action(control.dataset.control);});
$("blockPause").addEventListener("click",()=>action("pause"));
board.addEventListener("click",e=>{if(type==="mines"){const b=e.target.closest("[data-tile]");if(b){if(held){held=false;return;}mineTap(Number(b.dataset.tile));}}});
let holdTimer=null,held=false,longPressTile=-1,holdOrigin=null;
function stopHold(){if(holdTimer!==null)clearTimeout(holdTimer);holdTimer=null;holdOrigin=null;}
board.addEventListener("contextmenu",e=>{
 if(type!=="mines")return;const b=e.target.closest("[data-tile]");if(!b)return;
 e.preventDefault();stopHold();
 const index=Number(b.dataset.tile);
 // Android may send contextmenu immediately after a long-press flag event.
 if(held&&longPressTile===index)return;
 if(finished||state.open[index])return;
 state.flags[index]=!state.flags[index];drawMines();
 held=true;longPressTile=index;
});
board.addEventListener("pointerdown",e=>{
 if(type!=="mines"||e.button!==0)return;
 const b=e.target.closest("[data-tile]");if(!b)return;
 stopHold();held=false;longPressTile=-1;
 const at=Number(b.dataset.tile),cx=e.clientX,cy=e.clientY;
 holdOrigin={x:cx,y:cy};
 holdTimer=setTimeout(()=>{
  holdTimer=null;if(type!=="mines"||finished||state.open[at])return;
  state.flags[at]=!state.flags[at];drawMines();
  held=true;longPressTile=at;
 },550);
});
board.addEventListener("pointermove",e=>{
 if(holdTimer!==null&&holdOrigin&&Math.hypot(e.clientX-holdOrigin.x,e.clientY-holdOrigin.y)>10)stopHold();
});
board.addEventListener("pointerup",stopHold);
board.addEventListener("pointercancel",stopHold);
c.addEventListener("pointerdown",e=>{if(type==="bird"&&e.isPrimary&&e.button===0)flap();});
$("wingSkin").addEventListener("click",switchBird);
let startTouch=null;
c.addEventListener("touchstart",e=>{if(type==="maze"&&e.touches.length===1)startTouch=[e.touches[0].clientX,e.touches[0].clientY];},{passive:true});
c.addEventListener("touchend",e=>{if(type!=="maze"||!startTouch||!e.changedTouches.length)return;const dx=e.changedTouches[0].clientX-startTouch[0],dy=e.changedTouches[0].clientY-startTouch[1];if(Math.abs(dx)>18||Math.abs(dy)>18)mazeMove(...(Math.abs(dx)>Math.abs(dy)?[Math.sign(dx),0]:[0,Math.sign(dy)]));startTouch=null;},{passive:true});
document.addEventListener("keydown",e=>{if(e.ctrlKey||e.metaKey||e.altKey||e.target.closest("input,textarea,select"))return;const k=e.key.toLowerCase();let cmd={arrowleft:"left",arrowright:"right",arrowup:"rotate",arrowdown:"down",w:"up",a:"left",s:"down",d:"right"," ":"drop",p:"pause"}[k];if(type==="bird"&&(k===" "||k==="arrowup")){e.preventDefault();flap();return;}if(type==="maze"){cmd={arrowleft:"left",arrowright:"right",arrowup:"up",arrowdown:"down",w:"up",a:"left",s:"down",d:"right",p:"pause"}[k];}if(cmd){e.preventDefault();action(cmd);}});
document.addEventListener("visibilitychange",()=>{if(document.hidden&&type==="blocks"&&!finished){resetBlockInputs();paused=true;syncBlockPauseButton();blockDirty=true;message("Paused");}if(document.hidden&&type==="maze"&&!finished){paused=true;message("Paused");}});
window.addEventListener("pagehide",stop);
window.addEventListener("popstate",()=>pick(new URL(location.href).searchParams.get("game")||"bird"));
if(location.hostname==="localhost"||location.hostname==="127.0.0.1")window.__nightArcadeTest=Object.freeze({snapshot:()=>JSON.parse(JSON.stringify(state)),flags:()=>({finished,paused,points}),input:()=>({active:Boolean(blockGesture),gesture: blockGesture && {id:blockGesture.id,axis:blockGesture.axis,steps:blockGesture.steps},resets:blockInputResets,fit:blockHeight,width:blockWidth,signature:blockSizeSignature}),perf:()=>({blockPaints:blockPaintCount,wingCached:Boolean(wingBackdrop),mazeCached:Boolean(mazeBackdrop&&mazeDots)})});
pick(new URL(location.href).searchParams.get("game")||"bird");
})();
