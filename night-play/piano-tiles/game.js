/* NIGHT Play Piano Tiles. One finite, public-domain Frère Jacques arrangement;
 * synthesized note-on events follow actual successful taps and sustained holds.
 * No backing track, remote assets, or nonmusical background audio. */
(()=>{"use strict";
const W=400,H=660,LANES=4,HIT=H-93,KEYS={1:0,2:1,3:2,4:3,a:0,s:1,d:2,f:3};
const $=id=>document.getElementById(id),board=$("board"),ctx=board.getContext("2d");
const colors=["#253a68","#253a68","#263a74","#273a69"],ACCENT="#86d4d4";
let audioCtx=null,running=false,paused=false,ended=false,elapsed=0,previous=0,raf=0;
let notes=[],score=0,streak=0,complete=0,held=new Map(),keyHeld=new Set(),ripples=[];
const bestKey="night-play-piano-tiles-best-v1";
const readBest=()=>{try{return Math.max(0,Number(localStorage.getItem(bestKey))||0)}catch{return 0}};
function saveBest(){try{if(score>readBest())localStorage.setItem(bestKey,String(score))}catch{}}
function scale(){const dpr=Math.min(2.5,Math.max(1,window.devicePixelRatio||1));
 board.width=Math.round(W*dpr);board.height=Math.round(H*dpr);
 ctx.setTransform(dpr,0,0,dpr,0,0);render();}
function updateHud(){
 $("score").textContent=String(score);$("streak").textContent=String(streak);
 $("best").textContent=String(Math.max(score,readBest()));
}
function chart(){
 // Frère Jacques (traditional), C-major; lanes determine hit geometry,
 // note.frequency determines pitch. The final bar includes two-note chords.
 const phrase=[
  [0,261.63],[1,293.66],[2,329.63],[0,261.63],
  [0,261.63],[1,293.66],[2,329.63],[0,261.63],
  [2,329.63],[3,349.23],[2,392.00],[2,329.63],
  [3,349.23],[2,392.00],[2,329.63],[1,293.66],
  [2,392.00],[3,440.00],[2,392.00],[1,349.23],
  [2,329.63],[0,261.63],[2,392.00],[3,440.00],
  [2,392.00],[1,349.23],[2,329.63],[0,261.63],
  [0,261.63],[2,392.00],[0,261.63],[0,261.63]
 ];
 let t=2.15;const result=[];
 for(let i=0;i<phrase.length;i++){
  const [lane,freq]=phrase[i],hold=[7,14,27,31].includes(i);
  const beat=Math.max(.34,.535-i*.0045);
  const duration=hold?Math.max(.65,beat*1.55):0;
  result.push({lane,freq,at:t,duration,hold,state:"waiting",pointer:null,
   start:0,scoreGiven:false,voice:null,flash:0});
  if(i===19||i===29){
   const other=(lane+2)%4;
   result.push({lane:other,freq:freq/2,at:t,duration:0,hold:false,
    state:"waiting",pointer:null,start:0,scoreGiven:false,voice:null,flash:0});
  }
  t+=beat;
 }
 return result;
}
function cover(kicker,title,copy,button){
 $("coverKicker").textContent=kicker;$("coverTitle").textContent=title;
 $("coverCopy").textContent=copy;
 $("coverAction").hidden=!button;
 if(button)$("coverAction").textContent=button;
 $("cover").hidden=false;
}
function setPauseButton(){
 $("pause").disabled=!running||ended;
 $("pauseLabel").textContent=paused?"Resume":"Pause";
 $("pause").setAttribute("aria-label",paused?"Resume Piano Tiles":"Pause Piano Tiles");
 $("pauseIcon").setAttribute("href",paused?"#icon-play":"#icon-pause");
}
function closeSounds(){for(const n of notes)if(n.voice){n.voice.stop();n.voice=null}}
function clearRun(){
 if(raf)cancelAnimationFrame(raf);raf=0;closeSounds();notes=chart();
 held.clear();keyHeld.clear();ripples=[];score=0;streak=0;complete=0;
 elapsed=0;previous=0;running=false;paused=false;ended=false;
 updateHud();setPauseButton();$("status").textContent="Ready";render();
}
function begin(){
 clearRun();running=true;paused=false;ended=false;
 $("cover").hidden=true;setPauseButton();$("status").textContent="Play the next tile";
 previous=0;raf=requestAnimationFrame(tick);
}
function lose(reason){
 if(ended)return;
 ended=true;running=false;paused=false;
 if(raf)cancelAnimationFrame(raf);raf=0;
 closeSounds();held.clear();keyHeld.clear();saveBest();updateHud();setPauseButton();
 $("status").textContent=reason;
 cover("ROUND OVER",reason,"Score "+score+" · "+complete+" / "+notes.length+" notes","Play again");
 render();
}
function win(){
 if(ended)return;ended=true;running=false;paused=false;
 if(raf)cancelAnimationFrame(raf);raf=0;
 closeSounds();held.clear();keyHeld.clear();saveBest();updateHud();setPauseButton();
 $("status").textContent="Song complete";
 cover("SONG COMPLETE","Beautifully played.","Score "+score+" · "+complete+" notes","Play again");
 render();
}
function pause(){
 if(!running||ended)return;
 paused=!paused;setPauseButton();
 if(paused){
  if(raf)cancelAnimationFrame(raf);raf=0;closeSounds();
  // Pausing mid-hold should not forfeit the current note; its held pointer
  // still owns the tile and the logical clock stays frozen.
  cover("PAUSED","Take your time.","Use Resume in the top bar.",null);
  $("status").textContent="Paused";
 }else{
  $("cover").hidden=true;previous=0;
  $("status").textContent="Keep the rhythm";
  raf=requestAnimationFrame(tick);
 }
}
function sound(frequency,hold){
 try{
  audioCtx=audioCtx||new (window.AudioContext||window.webkitAudioContext)();
  if(audioCtx.state==="suspended")audioCtx.resume();
  const time=audioCtx.currentTime,osc1=audioCtx.createOscillator(),
    osc2=audioCtx.createOscillator(),gain=audioCtx.createGain();
  osc1.type="triangle";osc1.frequency.setValueAtTime(frequency,time);
  osc2.type="sine";osc2.frequency.setValueAtTime(frequency*2,time);
  const harmonics=audioCtx.createGain();harmonics.gain.value=.16;
  osc1.connect(gain);osc2.connect(harmonics);harmonics.connect(gain);
  gain.connect(audioCtx.destination);
  gain.gain.setValueAtTime(.0001,time);
  gain.gain.exponentialRampToValueAtTime(.13,time+.013);
  gain.gain.exponentialRampToValueAtTime(hold?.075:.0003,time+(hold?.23:.29));
  osc1.start(time);osc2.start(time);
  let stopped=false;
  const stop=()=>{
   if(stopped)return;stopped=true;
   const t=audioCtx.currentTime;
   gain.gain.cancelScheduledValues(t);
   gain.gain.setValueAtTime(Math.max(.0003,gain.gain.value),t);
   gain.gain.exponentialRampToValueAtTime(.0001,t+.15);
   osc1.stop(t+.17);osc2.stop(t+.17);
  };
  if(!hold)setTimeout(stop,310);
  return {stop};
 }catch{return {stop(){}};}
}
function noteGeometry(n){
 const speed=294+Math.min(65,complete*1.6);
 const bottom=HIT+(elapsed-n.at)*speed;
 const length=n.hold?(n.duration*speed+112):117;
 return {bottom,top:bottom-length,left:n.lane*100+4,right:n.lane*100+96,
  speed,length};
}
function finishNote(n,holdBonus){
 if(n.state==="done")return;
 n.state="done";if(n.voice){n.voice.stop();n.voice=null;}
 n.pointer=null;complete++;streak++;
 if(holdBonus)score+=75+Math.min(75,streak*2);
 updateHud();
 ripples.push({lane:n.lane,life:.32,age:0});
}
function play(x,y,id){
 if(!running||paused||ended||!Number.isFinite(x)||!Number.isFinite(y))return;
 const lane=Math.floor(x/100);
 if(lane<0||lane>=LANES||y<0||y>H)return;
 const n=notes.filter(t=>t.lane===lane&&t.state==="waiting"&&Math.abs(elapsed-t.at)<=.235)
   .sort((a,b)=>Math.abs(elapsed-a.at)-Math.abs(elapsed-b.at))
   .find(t=>{const g=noteGeometry(t);return y>=g.top-7&&y<=g.bottom+18;});
 if(!n){lose("Wrong tile");return;}
 n.start=elapsed;n.flash=.24;
 const accurate=Math.abs(elapsed-n.at)<.085;
 score+=accurate?125:100;
 updateHud();n.voice=sound(n.freq,n.hold);
 if(n.hold){
  n.state="holding";n.pointer=id;held.set(id,n);
  $("status").textContent="Keep holding";
 }else{
  finishNote(n,false);$("status").textContent=accurate?"Perfect":"Good";
 }
}
function release(id){
 const n=held.get(id);held.delete(id);
 if(!n)return;
 n.pointer=null;
 if(ended||!running)return;
 if(elapsed>=n.at+n.duration-.07){
  finishNote(n,true);$("status").textContent="Long note complete";
 }else lose("Hold the long tile");
}
function xy(e){
 const r=board.getBoundingClientRect();
 return {x:(e.clientX-r.left)*W/r.width,y:(e.clientY-r.top)*H/r.height};
}
board.addEventListener("pointerdown",e=>{
 if(e.button!==0||!running||paused||ended)return;
 e.preventDefault();
 if(board.setPointerCapture)board.setPointerCapture(e.pointerId);
 const {x,y}=xy(e);play(x,y,e.pointerId);
});
for(const event of ["pointerup","pointercancel","lostpointercapture"])
 board.addEventListener(event,e=>release(e.pointerId));
document.addEventListener("keydown",e=>{
 if(e.repeat||e.ctrlKey||e.altKey||e.metaKey||/input|textarea|select/i.test(e.target.tagName))return;
 if(e.key==="Escape"||e.key.toLowerCase()==="p"){
  if(running){e.preventDefault();pause();}return;
 }
 const lane=KEYS[e.key.toLowerCase()];
 if(lane===undefined||!running||paused||ended)return;
 e.preventDefault();const key="key-"+e.key.toLowerCase();
 if(keyHeld.has(key))return;
 keyHeld.add(key);play(lane*100+50,HIT-33,key);
});
document.addEventListener("keyup",e=>{
 const key="key-"+e.key.toLowerCase();keyHeld.delete(key);release(key);
});
$("coverAction").addEventListener("click",begin);
$("restart").addEventListener("click",begin);
$("pause").addEventListener("click",pause);
document.addEventListener("visibilitychange",()=>{if(document.hidden&&running&&!paused)pause()});
function tick(ts){
 if(!running||paused||ended)return;
 if(previous)elapsed+=Math.min(.05,(ts-previous)/1000);
 previous=ts;
 // A held long note may complete while the next note is being played.
 for(const n of notes){
  if(n.state==="holding"&&elapsed>=n.at+n.duration){
   if(n.pointer!==null)held.delete(n.pointer);
   finishNote(n,true);$("status").textContent="Long note complete";
  }
  if(n.state==="waiting"&&elapsed>n.at+.255){lose("Missed tile");break;}
  n.flash=Math.max(0,n.flash-.016);
 }
 for(const r of ripples)r.age+=.016;
 ripples=ripples.filter(r=>r.age<r.life);
 if(!ended&&complete===notes.length){win();return;}
 render();
 if(!ended)raf=requestAnimationFrame(tick);
}
function render(){
 const x=ctx;
 x.clearRect(0,0,W,H);
 x.fillStyle="#f4f6f9";x.fillRect(0,0,W,H);
 for(let lane=0;lane<LANES;lane++){
  x.fillStyle=lane%2===0?"#f6f8fd":"#e9edf5";
  x.fillRect(lane*100,0,100,H);
  if(lane){x.fillStyle="#ced7e2";x.fillRect(lane*100-.7,0,1.4,H);}
 }
 const active=Array.from(held.values());
 for(const n of active){x.fillStyle="#d7f1ee";x.fillRect(n.lane*100,0,100,H);}
 // A low-contrast timing shelf, not a full-width black strike line.
 const cue=x.createLinearGradient(0,HIT-24,0,H);
 cue.addColorStop(0,"#cad7e700");cue.addColorStop(1,"#c6d6e59c");
 x.fillStyle=cue;x.fillRect(0,HIT-24,W,H-HIT+24);
 for(const n of notes){
  if(n.state==="done")continue;
  const g=noteGeometry(n);
  if(g.bottom<0||g.top>H)continue;
  const top=Math.max(-4,g.top),bottom=Math.min(H+4,g.bottom);
  x.save();x.beginPath();x.rect(g.left,top,g.right-g.left,bottom-top);x.clip();
  const gradient=x.createLinearGradient(0,g.top,0,g.bottom);
  if(n.state==="holding"){
   gradient.addColorStop(0,"#296b76");gradient.addColorStop(1,"#49b8bf");
  }else{
   gradient.addColorStop(0,colors[n.lane]);gradient.addColorStop(1,"#131e3b");
  }
  x.fillStyle=gradient;x.fillRect(g.left,g.top,g.right-g.left,g.length);
  x.fillStyle=n.state==="holding"?"#bff1e9":"#96b3d3";
  x.fillRect(g.left+9,g.top+9,3,Math.max(0,g.length-18));
  if(n.hold){
   const progress=n.state==="holding"?Math.min(1,Math.max(0,(elapsed-n.at)/n.duration)):0;
   x.fillStyle=n.state==="holding"?"#8df3dc":"#8bb4db";
   x.fillRect(g.left+20,g.bottom-12,(g.right-g.left-40)*Math.max(.12,progress),4);
   x.fillStyle="#f4fafb";
   x.beginPath();x.arc((g.left+g.right)/2,g.bottom-27,5,0,Math.PI*2);x.fill();
  }
  x.restore();
 }
 for(const r of ripples){
  x.globalAlpha=Math.max(0,1-r.age/r.life);
  x.fillStyle=ACCENT;x.fillRect(r.lane*100,HIT-17,100,6);x.globalAlpha=1;
 }
 // A four-lane touch guide, kept unobtrusive beneath active notes.
 for(let i=0;i<4;i++){
  x.fillStyle="#7a8ba4";x.font="700 14px system-ui";
  x.textAlign="center";x.fillText(String(i+1),i*100+50,H-18);
 }
}
if(location.hostname==="localhost"||location.hostname==="127.0.0.1")
 window.__nightPianoTest=Object.freeze({snapshot:()=>({
  running,paused,ended,elapsed,score,streak,complete,
  notes:notes.map(n=>({lane:n.lane,at:n.at,duration:n.duration,hold:n.hold,state:n.state}))
 }),geometry:()=>notes.map(noteGeometry)});
clearRun();scale();window.addEventListener("resize",scale,{passive:true});
})();
