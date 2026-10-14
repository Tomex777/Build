/* NIGHT Ludo motion: one timing scale, real coordinates and cancellable effects. */
(function(){
"use strict";
const reduced=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
const flights=new Set();
function timing(s){
 if(reduced())return {hop:20,release:30,capture:30,settle:0,ui:0};
 return s.options.speed==='slow'?{hop:260,release:480,capture:620,settle:230,ui:320}:
 s.options.speed==='fast'?{hop:75,release:190,capture:260,settle:120,ui:160}:
 {hop:150,release:320,capture:440,settle:180,ui:240};
}
function animate(el,frames,options){if(!el||!el.animate||reduced())return null;return el.animate(frames,options);}
function capture(root){
 const pieces=new Map();
 root.querySelectorAll('[data-piece]').forEach(el=>{
  const piece=el.matches('.nj-piece')?el:el.querySelector('.nj-piece');
  if(piece)pieces.set(el.dataset.piece,{rect:piece.getBoundingClientRect(),node:piece.cloneNode(true),progress:Number(el.dataset.progress)});
 });
 return {pieces,exists:!!root.querySelector('.nj-board'),rolling:!!root.querySelector('.is-rolling'),
  dice:[...document.querySelectorAll('.nj-dice-center .nj-cube,.nj-dice-controls .nj-cube')].map(el=>({transform:getComputedStyle(el).transform,used:!!el.closest('.is-used'),selected:!!el.closest('.is-selected')})),status:document.getElementById('status')?.textContent};
}
function flight(old,target,piece,kind,duration){
 const layer=document.createElement('div');layer.className='nj-motion-layer';layer.setAttribute('aria-hidden','true');
 const ghost=old.node;ghost.classList.remove('eligible');ghost.classList.add('nj-motion-counter');ghost.removeAttribute('data-piece');ghost.removeAttribute('data-progress');ghost.tabIndex=-1;
 Object.assign(ghost.style,{position:'absolute',left:old.rect.left+'px',top:old.rect.top+'px',width:old.rect.width+'px',height:old.rect.height+'px',margin:'0',opacity:'1',visibility:'visible'});
 layer.dataset.motion=kind;layer.appendChild(ghost);document.body.appendChild(layer);
 if(piece)piece.style.visibility='hidden';
 const dx=target.left-old.rect.left,dy=target.top-old.rect.top,ratio=target.width/old.rect.width;
 const lift=kind==='hop'?5:kind==='release'?28:kind==='capture'?40:18;
 const frames=[{transform:'translate(0,0) scale(1)',opacity:1},
  {transform:`translate(${dx*.5}px,${dy*.5-lift}px) scale(${(1+ratio)*.52})`,opacity:1,offset:.5},
  {transform:`translate(${dx}px,${dy}px) scale(${ratio})`,opacity:kind==='finish'?0:1}];
 const animation=ghost.animate(frames,{duration,easing:'cubic-bezier(.22,.6,.32,1)',fill:'forwards'});
 const cleanup=()=>{if(piece?.isConnected)piece.style.visibility='';layer.remove();flights.delete(animation);};
 flights.add(animation);animation.finished.then(cleanup,cleanup);
}
function sparks(root,count=8){
 const center=root.querySelector('.nj-dice-center');if(!center||reduced())return;
 const box=center.getBoundingClientRect(),layer=document.createElement('div');layer.className='nj-motion-layer';layer.setAttribute('aria-hidden','true');
 document.body.appendChild(layer);
 for(let i=0;i<count;i++){
  const spark=document.createElement('span');spark.className='nj-motion-spark';spark.style.left=box.left+box.width/2+'px';spark.style.top=box.top+box.height/2+'px';layer.appendChild(spark);
  const angle=i/count*Math.PI*2,dist=count>8?85:34;
  const a=spark.animate([{transform:'translate(0,0) scale(.3)',opacity:1},{transform:`translate(${Math.cos(angle)*dist}px,${Math.sin(angle)*dist}px) scale(1)`,opacity:0}],{duration:550,easing:'ease-out',fill:'forwards'});
  a.finished.then(()=>spark.remove(),()=>spark.remove());
 }
 setTimeout(()=>layer.remove(),600);
}
function play(root,before,s){
 if(reduced())return;
 const t=timing(s),now=new Map();
 root.querySelectorAll('[data-piece]').forEach(el=>{
  const piece=el.matches('.nj-piece')?el:el.querySelector('.nj-piece');if(piece)now.set(el.dataset.piece,{piece,progress:Number(el.dataset.progress)});
 });
 for(const [key,old] of before.pieces){
  const current=now.get(key);
  if(current){
   const box=current.piece.getBoundingClientRect();
   if(old.progress===current.progress&&Math.abs(old.rect.left-box.left)+Math.abs(old.rect.top-box.top)<1)continue;
   const kind=current.progress===-1&&old.progress>=0?'capture':old.progress===-1?'release':'hop';
   flight(old,box,current.piece,kind,kind==='capture'?t.capture:kind==='release'||s.options.directCount?t.release:t.hop);
  }else if(old.progress>=0&&old.progress<56){
   const center=root.querySelector('.nj-dice-center').getBoundingClientRect();
   flight(old,{left:center.left+center.width/2-8,top:center.top+center.height/2-8,width:16},null,'finish',t.capture);
   sparks(root,s.winner!==null?16:8);
  }
 }
 if(!before.exists)animate(root.querySelector('.nj-board'),[{opacity:0,transform:'translateY(9px) scale(.985)'},{opacity:1,transform:'none'}],{duration:420,easing:'cubic-bezier(.2,.7,.2,1)'});
 const dice=[...document.querySelectorAll('.nj-dice-center .nj-cube,.nj-dice-controls .nj-cube')];
 dice.forEach((cube,i)=>{
  const old=before.dice[i];if(!old)return;
  const button=cube.closest('button'),used=button.classList.contains('is-used'),selected=button.classList.contains('is-selected');
  if(before.rolling&&!s.rolling)animate(cube,[{transform:old.transform},{transform:getComputedStyle(cube).transform}],{duration:t.settle,easing:'cubic-bezier(.2,.65,.35,1)'});
  if(used&&!old.used)animate(button,[{opacity:1},{opacity:.3}],{duration:t.ui,easing:'ease-out'});
  if(selected&&!old.selected)animate(button,[{transform:'scale(.96)'},{transform:'scale(1.07)',offset:.55},{transform:'scale(1)'}],{duration:t.ui,easing:'ease-out'});
 });
 const status=document.getElementById('status');if(status&&before.status!==status.textContent)animate(status,[{opacity:.3,transform:'translateY(4px)'},{opacity:1,transform:'translateY(0)'}],{duration:t.ui,easing:'ease-out'});
}
function captureHud(root){return [...root.querySelectorAll('.player')].map(el=>({turn:el.classList.contains('turn'),value:el.querySelector('.value')?.textContent}));}
function playHud(root,before,s){
 [...root.querySelectorAll('.player')].forEach((el,i)=>{
  const old=before[i];if(!old)return;
  if(!old.turn&&el.classList.contains('turn'))animate(el,[{boxShadow:'0 0 0 0 #e6cf9155'},{boxShadow:'0 0 0 5px #e6cf9100'}],{duration:500,easing:'ease-out'});
  if(old.value!==el.querySelector('.value')?.textContent)animate(el.querySelector('.value'),[{opacity:0,transform:'translateY(7px)'},{opacity:1,transform:'translateY(0)'}],{duration:timing(s).ui,easing:'ease-out'});
 });
}
function sheet(d,back,open){
 d.getAnimations().forEach(a=>a.cancel());back.getAnimations().forEach(a=>a.cancel());
 const generation=String((Number(d.dataset.motionGeneration)||0)+1);d.dataset.motionGeneration=generation;d.dataset.closing=String(!open);
 if(open){d.open=true;back.hidden=false;animate(d,[{transform:'translateY(100%)'},{transform:'translateY(0)'}],{duration:280,easing:'cubic-bezier(.22,.7,.25,1)'});animate(back,[{opacity:0},{opacity:1}],{duration:200});return;}
 const a=animate(d,[{transform:'translateY(0)'},{transform:'translateY(100%)'}],{duration:200,easing:'ease-in',fill:'forwards'});
 animate(back,[{opacity:1},{opacity:0}],{duration:200,fill:'forwards'});
 const done=()=>{if(d.isConnected&&d.dataset.motionGeneration===generation){d.open=false;back.hidden=true;d.dataset.closing='false';}};
 if(a)a.finished.then(done,()=>{});else done();
}
function cancel(){for(const a of flights)a.cancel();flights.clear();document.querySelectorAll('.nj-motion-layer').forEach(el=>el.remove());}
window.NightLudoMotion=Object.freeze({timing,capture,play,captureHud,playHud,sheet,cancel});
})();
