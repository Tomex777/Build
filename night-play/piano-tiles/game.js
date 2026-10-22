/* NIGHT Play Piano Tiles: recorded, polyphonic acoustic grand piano.
 * Local audio assets trigger on successful taps/holds, no oscillator chimes.
 * No backing track or unrelated background audio. */
(()=>{"use strict";
const W=400,H=660,LANES=4,HIT=H-93,KEYS={1:0,2:1,3:2,4:3,a:0,s:1,d:2,f:3};
const $=id=>document.getElementById(id),board=$("board"),ctx=board.getContext("2d");
const colors=["#2193a2","#8260c6","#d77d46","#328c78"],deepColors=["#165665","#46356e","#814521","#1c5446"],ACCENT="#86d4d4";
let preparing=false,running=false,paused=false,ended=false,elapsed=0,previous=0,raf=0;
let notes=[],score=0,streak=0,complete=0,held=new Map(),keyHeld=new Set();
const SONGS=[
 {id:"endless",title:"Endless"},
 {id:"frere",title:"Frère Jacques"},
 {id:"twinkle",title:"Twinkle, Twinkle"}
];
let selectedSong=SONGS[0],endlessNextAt=0,endlessIndex=0;
const bestKey=()=>selectedSong.id==="frere"?"night-play-piano-tiles-best-v1":"night-play-piano-tiles-best-"+selectedSong.id+"-v1";
const readBest=()=>{try{return Math.max(0,Number(localStorage.getItem(bestKey()))||0)}catch{return 0}};
function saveBest(){try{if(score>readBest())localStorage.setItem(bestKey(),String(score))}catch{}}
function scale(){const dpr=Math.min(2.5,Math.max(1,window.devicePixelRatio||1));
 board.width=Math.round(W*dpr);board.height=Math.round(H*dpr);
 ctx.setTransform(dpr,0,0,dpr,0,0);render();}
function updateHud(){
 $("score").textContent=String(score);$("streak").textContent=String(streak);
 $("best").textContent=String(Math.max(score,readBest()));
}
// Endless is an actual continuous mode, not a finite song masquerading as one.
function extendEndless(count){
 const result=[],lanes=[0,2,1,3,0,3,1,2,1,3,2,0,3,1,0,2];
 const pitches=[261.63,293.66,329.63,392,440,523.25,587.33,659.25];
 for(let k=0;k<count;k++){
  const i=endlessIndex++,lane=lanes[i%lanes.length];
  const hold=i>3&&i%19===10;
  const beat=Math.max(.36,.67-Math.min(.29,i*.0017));
  result.push({lane,freq:pitches[(i*3+Math.floor(i/4))%pitches.length],at:endlessNextAt,
   duration:hold?.67:0,hold,state:"waiting",pointer:null,start:0,
   scoreGiven:false,voice:null,flash:0,hitBottom:null});
  endlessNextAt+=beat;
 }
 return result;
}
function chart(){
 if(selectedSong.id==="endless"){endlessIndex=0;endlessNextAt=1.45;return extendEndless(32);}
 if(selectedSong.id==="twinkle")return twinkleChart();
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
  const [lane,freq]=phrase[i],hold=[7,14,26,31].includes(i);
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
// Public-domain Twinkle, Twinkle Little Star, full A–B–B–A variation.
// Interleaved lanes avoid a string of tiny repeated taps on one narrow lane.
function twinkleChart(){
 const pitches=[
 261.63,261.63,392,392,440,440,392,
 349.23,349.23,329.63,329.63,293.66,293.66,261.63,
 392,392,349.23,349.23,329.63,329.63,293.66,
 392,392,349.23,349.23,329.63,329.63,293.66,
 261.63,261.63,392,392,440,440,392,
 349.23,349.23,329.63,329.63,293.66,293.66,261.63
 ];
 let t=2.15;const result=[];
 for(let i=0;i<pitches.length;i++){
  const lane=[0,1,2,3,1,0,2,3][i%8];
  const beat=Math.max(.44,.60-i*.0038);
  const hold=[6,13,20,27,34,41].includes(i);
  const duration=hold?Math.max(.72,beat*1.5):0;
  result.push({lane,freq:pitches[i],at:t,duration,hold,state:"waiting",pointer:null,
   start:0,scoreGiven:false,voice:null,flash:0});
  // The final cadence gets an independent second finger/key harmony.
  if(i===35||i===40)result.push({lane:(lane+2)%4,freq:pitches[i]/2,at:t,
   duration:0,hold:false,state:"waiting",pointer:null,start:0,
   scoreGiven:false,voice:null,flash:0});
  t+=beat;
 }
 return result;
}
function cover(kicker,title,copy,button){
 $("coverKicker").textContent=kicker;$("coverKicker").hidden=!kicker;$("coverTitle").textContent=title;
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
function closeSounds(){window.NightPiano.silence();for(const n of notes)n.voice=null}
function setSongControls(){
 for(const button of document.querySelectorAll("[data-song]")){
  const active=button.dataset.song===selectedSong.id;
  button.setAttribute("aria-pressed",String(active));
  button.disabled=preparing||(running&&!ended);
 }
}
function chooseSong(id){
 if(running&&!ended)return;
 const found=SONGS.find(song=>song.id===id);
 if(!found||selectedSong===found)return;
 selectedSong=found;
 clearRun();
 cover("","Feel the rhythm.",selectedSong.id==="endless"?"Tap any visible colored tile. Keep holding the long ones.":"Tap the visible notes anywhere along their tiles.","Start playing");
}
function clearRun(){
 if(raf)cancelAnimationFrame(raf);raf=0;closeSounds();notes=chart();
 held.clear();keyHeld.clear();score=0;streak=0;complete=0;
 elapsed=0;previous=0;running=false;paused=false;ended=false;
 document.body.classList.remove("is-playing");
 updateHud();setPauseButton();setSongControls();$("restart").hidden=true;$("status").textContent="";render();
}
async function begin(){
 if(preparing)return;
 preparing=true;
 const action=$("coverAction");action.disabled=true;
 cover("","Preparing grand piano","Loading real recorded piano notes…",null);
 setSongControls();
 try{
  // Decode on the player's gesture, cache on-device and only then start.
  await window.NightPiano.prepare();
  clearRun();running=true;paused=false;ended=false;
  document.body.classList.add("is-playing");
  $("cover").hidden=true;$("restart").hidden=false;
  setPauseButton();$("status").textContent="Tap any visible tile";
  previous=0;raf=requestAnimationFrame(tick);
 }catch(error){
  console.error("Unable to load recorded piano",error);
  cover("AUDIO UNAVAILABLE","Piano could not load",
   "The grand piano recordings couldn't be loaded. Check your connection and try again.","Retry");
 }finally{preparing=false;action.disabled=false;setSongControls()}
}
function lose(reason){
 if(ended)return;
 ended=true;running=false;paused=false;
 if(raf)cancelAnimationFrame(raf);raf=0;
 closeSounds();held.clear();keyHeld.clear();saveBest();updateHud();setPauseButton();setSongControls();$("restart").hidden=true;
 document.body.classList.remove("is-playing");
 $("status").textContent="";
 cover("ROUND OVER",reason,selectedSong.id==="endless"?"Score "+score+" · "+complete+" tiles":"Score "+score+" · "+complete+" / "+notes.length+" notes","Play again");
 render();
}
function win(){
 if(ended)return;ended=true;running=false;paused=false;
 if(raf)cancelAnimationFrame(raf);raf=0;
 closeSounds();held.clear();keyHeld.clear();saveBest();updateHud();setPauseButton();setSongControls();
 document.body.classList.remove("is-playing");$("status").textContent="";
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
  $("status").textContent="";
 }else{
  $("cover").hidden=true;previous=0;
  $("status").textContent="";
  raf=requestAnimationFrame(tick);
 }
}
function sound(frequency,hold){
 return window.NightPiano.play(frequency,hold);
}
function noteGeometry(n){
 const speed=294+Math.min(65,complete*1.6);
 const bottom=n.state==="holding"&&Number.isFinite(n.hitBottom)?n.hitBottom:HIT+(elapsed-n.at)*speed;
 const length=n.hold?(n.duration*speed+138):166;
 return {bottom,top:bottom-length,left:n.lane*100+4,right:n.lane*100+96,
  speed,length};
}
function finishNote(n,holdBonus){
 if(n.state==="done")return;
 n.state="done";
  if(n.voice){
   // Short notes ring as actual piano notes. Hold tails damp on release.
   if(n.hold)n.voice.release(.34);
   n.voice=null;
  }
 n.pointer=null;complete++;streak++;
 if(holdBonus)score+=75+Math.min(75,streak*2);
 updateHud();

}
function play(x,y,id){
 if(!running||paused||ended||!Number.isFinite(x)||!Number.isFinite(y))return;
 const lane=Math.floor(x/100);
 if(lane<0||lane>=LANES||y<0||y>H)return;
 // Judge the actual visible rectangle, not a 470ms invisible timing gate.
 // Before this fix a visibly touched tile could keep falling because it was
 // still far above the old narrow timing shelf.
 const n=notes.filter(t=>t.lane===lane&&t.state==="waiting")
  .sort((a,b)=>noteGeometry(b).bottom-noteGeometry(a).bottom)
  .find(t=>{
   const g=noteGeometry(t);
   return g.bottom>=0&&g.top<=H&&
    y>=Math.max(0,g.top)-9&&y<=Math.min(H,g.bottom)+9;
  });
 if(!n){lose("Wrong tile");return;}
 const hit=noteGeometry(n);
 n.start=elapsed;n.flash=.24;
 // The full tile is tappable; the lower playable third earns 'Perfect'.
 const accurate=hit.bottom>=HIT-45&&hit.bottom<=H+18;
 score+=accurate?125:100;
 updateHud();n.voice=sound(n.freq,n.hold);
 if(n.hold){
  n.state="holding";n.pointer=id;n.hitBottom=hit.bottom;held.set(id,n);
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
 if(elapsed>=n.start+n.duration-.07){
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
document.querySelectorAll("[data-song]").forEach(button=>button.addEventListener("click",()=>chooseSong(button.dataset.song)));
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
  if(n.state==="holding"&&elapsed>=n.start+n.duration){
   if(n.pointer!==null)held.delete(n.pointer);
   finishNote(n,true);$("status").textContent="Long note complete";
  }
  // Only miss once the complete visible tile has left the playable canvas.
  if(n.state==="waiting"&&noteGeometry(n).top>H+5){lose("Missed tile");break;}
  n.flash=Math.max(0,n.flash-.016);
 }

 if(!ended&&selectedSong.id==="endless"){
  if(endlessNextAt-elapsed<11)notes.push(...extendEndless(20));
  if(notes.length>110)notes=notes.filter(t=>t.state!=="done");
 }
 if(!ended&&selectedSong.id!=="endless"&&complete===notes.length){win();return;}
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
 // Held feedback belongs inside the long tile; never flash or tint an entire lane.
 // A low-contrast timing shelf, not a full-width black strike line.
 const cue=x.createLinearGradient(0,HIT-24,0,H);
 cue.addColorStop(0,"#cad7e700");cue.addColorStop(1,"#c6d6e59c");
 x.fillStyle=cue;x.fillRect(0,HIT-24,W,H-HIT+24);
 for(const n of notes){
  if(n.state==="done")continue;
  const g=noteGeometry(n);
  if(g.bottom<0||g.top>H)continue;
  const progress=n.state==="holding"?Math.min(1,Math.max(0,(elapsed-n.start)/n.duration)):0;
  // Long tails gradually retract toward their anchored key while held; no screen flash.
  const tail=n.state==="holding"?Math.max(45,g.length*(1-progress)):g.length;
  const tileTop=n.state==="holding"?g.bottom-tail:g.top;
  const top=Math.max(-4,tileTop),bottom=Math.min(H+4,g.bottom);
  x.save();x.beginPath();x.rect(g.left,top,g.right-g.left,bottom-top);x.clip();
  const gradient=x.createLinearGradient(0,tileTop,0,g.bottom);
  if(n.state==="holding"){
   gradient.addColorStop(0,"#296b76");gradient.addColorStop(1,"#49b8bf");
  }else{
   gradient.addColorStop(0,colors[n.lane]);gradient.addColorStop(1,deepColors[n.lane]);
  }
  x.fillStyle=gradient;x.fillRect(g.left,tileTop,g.right-g.left,tail);
  x.fillStyle=n.state==="holding"?"#bff1e9":"#96b3d3";
  x.fillRect(g.left+9,tileTop+9,3,Math.max(0,tail-18));
  if(n.hold){
   x.fillStyle=n.state==="holding"?"#8df3dc":"#8bb4db";
   x.fillRect(g.left+20,g.bottom-12,(g.right-g.left-40)*Math.max(.12,progress),4);
   x.fillStyle="#f4fafb";
   x.beginPath();x.arc((g.left+g.right)/2,g.bottom-27,5,0,Math.PI*2);x.fill();
  }
  x.restore();
 }
 // A flash-free subtle inset edge shows completion without lighting up whole lanes.

}
if(location.hostname==="localhost"||location.hostname==="127.0.0.1")
 window.__nightPianoTest=Object.freeze({snapshot:()=>({
  song:selectedSong.id,running,paused,ended,elapsed,score,streak,complete,endlessIndex,
  notes:notes.map(n=>({lane:n.lane,at:n.at,duration:n.duration,hold:n.hold,state:n.state}))
 }),geometry:()=>notes.map(noteGeometry)});
clearRun();scale();window.addEventListener("resize",scale,{passive:true});
})();
