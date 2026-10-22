/* NIGHT Play Piano Tiles: recorded, polyphonic acoustic grand piano.
 * Local audio assets trigger on successful taps/holds, no oscillator chimes.
 * No backing track or unrelated background audio. */
(()=>{"use strict";
const W=400,H=660,LANES=4,HIT=H-93,KEYS={1:0,2:1,3:2,4:3,a:0,s:1,d:2,f:3};
const $=id=>document.getElementById(id),board=$("board"),ctx=board.getContext("2d");
// Piano keys are a consistent ink-dark instrument surface, not four unrelated
// coloured arcade bars. Held notes use a soft turquoise completion accent.
const colors=["#263948","#263948","#263948","#263948"],deepColors=["#101e2b","#101e2b","#101e2b","#101e2b"];
let preparing=false,running=false,paused=false,ended=false,elapsed=0,previous=0,raf=0;
let notes=[],score=0,streak=0,complete=0,held=new Map(),keyHeld=new Set();
const SONGS=window.NightPianoSongLibrary.songs;
let selectedSong=SONGS[0],endlessNextAt=0,endlessIndex=0;
let songFilter="All",songQuery="";
const favouriteKey="night-play-piano-favourites-v1";
function savedFavorites(){try{const a=JSON.parse(localStorage.getItem(favouriteKey)||"[]");return new Set(Array.isArray(a)?a:[])}catch{return new Set()}}
let favorites=savedFavorites();
function bestFor(song){try{return Math.max(0,Number(localStorage.getItem("night-play-piano-tiles-best-"+song.id+"-v1"))||0)}catch{return 0}}
const bestKey=()=>selectedSong.id==="frere"?"night-play-piano-tiles-best-v1":"night-play-piano-tiles-best-"+selectedSong.id+"-v1";
const readBest=()=>{try{return Math.max(0,Number(localStorage.getItem(bestKey()))||0)}catch{return 0}};
function saveBest(){try{if(score>readBest())localStorage.setItem(bestKey(),String(score))}catch{}}
function scale(){const dpr=Math.min(2,Math.max(1,window.devicePixelRatio||1));
 board.width=Math.round(W*dpr);board.height=Math.round(H*dpr);
 ctx.setTransform(dpr,0,0,dpr,0,0);render();}
function updateHud(){
 $("score").textContent=String(score);$("streak").textContent=String(streak);
 $("best").textContent=String(Math.max(score,readBest()));
}
// Endless mode now plays an original, *harmonically coherent* C / G / Am / F
// piano phrase. It alternates two eight-bar variations rather than selecting
// unrelated pitches that sound like random beeps even through a real piano.
const ENDLESS_MELODY=[
 // Opening motif: C major, G major, A minor, F major (8 notes per bar).
 261.63,329.63,392,523.25,392,329.63,293.66,261.63,
 246.94,293.66,392,493.88,440,392,293.66,246.94,
 220,261.63,329.63,440,523.25,440,329.63,261.63,
 174.61,261.63,349.23,440,392,349.23,329.63,261.63,
 // A second phrase rises and returns to the tonic.
 261.63,392,440,523.25,659.25,523.25,440,392,
 246.94,392,440,493.88,587.33,493.88,392,293.66,
 220,329.63,392,440,523.25,659.25,523.25,440,
 174.61,261.63,349.23,440,392,329.63,293.66,261.63
];
function extendEndless(count){
 const result=[],lanes=[0,2,1,3,0,3,1,2,1,3,2,0,3,1,0,2];
 for(let k=0;k<count;k++){
  const i=endlessIndex++,lane=lanes[i%lanes.length];
  const hold=i>3&&i%16===15;
  const beat=Math.max(.39,.68-Math.min(.29,i*.00135));
  const motif=ENDLESS_MELODY[i%ENDLESS_MELODY.length];
  result.push({lane,freq:motif,at:endlessNextAt,
   duration:hold?.76:0,hold,state:"waiting",pointer:null,start:0,
   scoreGiven:false,voice:null,flash:0,hitBottom:null});
  endlessNextAt+=beat;
 }
 return result;
}
function chart(){
 if(selectedSong.id==="endless"){endlessIndex=0;endlessNextAt=1.45;return spaceNotes(extendEndless(32));}
 if(selectedSong.id==="twinkle")return spaceNotes(twinkleChart());
 if(selectedSong.id!=="frere")return spaceNotes(window.NightPianoSongLibrary.arrangement(selectedSong));
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
 return spaceNotes(result);
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
 // The song library is a separate browse screen; not a three-tab HUD.
 $("pause").hidden=$("playPage").hidden;
}
function renderSongLibrary(){
 const list=$("songList");list.replaceChildren();
 const filtered=SONGS.filter(song=>{
  const genre=song.genre;
  if(songFilter==="Favorites"&&!favorites.has(song.id))return false;
  if(!["All","Favorites"].includes(songFilter)&&genre!==songFilter)return false;
  return (song.title+" "+song.artist+" "+song.difficulty).toLocaleLowerCase().includes(songQuery);
 });
 $("songCount").textContent=SONGS.length+" playable piano arrangements";
 for(const song of filtered){
  const item=document.createElement("article");item.className="song-card";item.dataset.id=song.id;
  const art=document.createElement("div");art.className="song-card-art";
  const palette=song.genre==="Classical"?["#e5e3ed","#554d85"]:
   song.genre==="Original"?["#dfebe8","#427969"]:
   song.genre==="Endless"?["#dce6ef","#3d637f"]:["#ece6d9","#766449"];
  art.style.setProperty("--art-bg",palette[0]);art.style.setProperty("--art-ink",palette[1]);
  art.innerHTML='<svg viewBox="0 0 28 28" aria-hidden="true"><path d="M10 18V6l13-3v12"/><ellipse cx="6.5" cy="20.5" rx="5" ry="3"/><ellipse cx="19.5" cy="17.5" rx="5" ry="3"/></svg>';
  const detail=document.createElement("div");detail.className="song-card-main";
  const title=document.createElement("div");title.className="song-card-title";title.textContent=song.title;
  const artist=document.createElement("div");artist.className="song-card-artist";artist.textContent=song.artist;
  const meta=document.createElement("div");meta.className="song-card-meta";
  const diff=document.createElement("span");diff.className="song-level";diff.textContent=song.difficulty;
  const best=document.createElement("span");best.textContent=bestFor(song)?"Best "+bestFor(song).toLocaleString():"";
  meta.append(diff,best);detail.append(title,artist,meta);
  const actions=document.createElement("div");actions.className="song-card-actions";
  const fave=document.createElement("button");fave.type="button";fave.className="song-favorite";
  fave.dataset.favourite=song.id;fave.setAttribute("aria-label",(favorites.has(song.id)?"Remove ":"Add ")+song.title+(favorites.has(song.id)?" from favourites":" to favourites"));
  fave.setAttribute("aria-pressed",String(favorites.has(song.id)));
  fave.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 8.3c0 4.2-8.8 10.5-8.8 10.5S3.2 12.5 3.2 8.3a4.8 4.8 0 0 1 8.8-2.5 4.8 4.8 0 0 1 8.8 2.5Z"/></svg>';
  const play=document.createElement("button");play.type="button";play.className="song-play";play.dataset.play=song.id;
  play.textContent="Play";play.setAttribute("aria-label","Play "+song.title);
  actions.append(fave,play);item.append(art,detail,actions);list.append(item);
 }
 if(!filtered.length){const empty=document.createElement("p");empty.className="song-empty";
  empty.textContent="No songs match your search.";list.append(empty)}
}
function buildFilters(){
 const container=$("songFilters");
 for(const name of ["All","Traditional","Classical","Original","Endless","Favorites"]){
  const button=document.createElement("button");button.type="button";button.className="song-filter";
  button.textContent=name==="Original"?"NIGHT Originals":name==="All"?"All songs":name;
  button.setAttribute("aria-pressed",String(songFilter===name));
  button.addEventListener("click",()=>{
   songFilter=name;
   for(const b of container.children)b.setAttribute("aria-pressed",String(b===button));
   renderSongLibrary();
  });container.append(button);
 }
}
function showLibrary(){
 if(preparing)return;
 clearRun();$("playPage").hidden=true;$("songLibrary").hidden=false;
 $("pause").hidden=true;renderSongLibrary();
 window.scrollTo({top:0,behavior:"instant"});
}
function openSong(song){
 if(!song||preparing)return;
 selectedSong=song;
 $("songLibrary").hidden=true;$("playPage").hidden=false;$("pause").hidden=false;
 $("activeSongTitle").textContent=song.title;
 $("activeSongArtist").textContent=song.artist;
 clearRun();cover("","Ready to play?",song.title+" · "+song.difficulty,"Start playing");
 window.scrollTo({top:0,behavior:"instant"});
}

function chooseSong(id){openSong(SONGS.find(song=>song.id===id))}
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
  // Start with the first key already visible and descending; do not make
  // players stare at an empty piano board for the first second or two.
  // The key still takes ~0.9s to reach the natural lower playing area.
  elapsed=Math.max(0,notes[0].at-.38);
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
// Beat-locked world displacement: all tiles move through a continuous shared
// timeline. A successful tap must never teleport every other tile.
const TILE_HEIGHT=166,TILE_GAP=TILE_HEIGHT;
function baseSpeed(){return ({Easy:525,Normal:570,Hard:610,Expert:660})[selectedSong.difficulty]||570}
const MAX_RAMP=100,RAMP_PER_SECOND=1.3;
function trackTravel(t){
 t=Math.max(0,t);
 const base=baseSpeed();
 if(selectedSong.id!=="endless")return base*t;
 const threshold=MAX_RAMP/RAMP_PER_SECOND,accelerated=Math.min(t,threshold);
 return base*t+.5*RAMP_PER_SECOND*accelerated*accelerated+
  MAX_RAMP*Math.max(0,t-threshold);
}
function speedAt(t){
 return baseSpeed()+(selectedSong.id==="endless"?
  Math.min(MAX_RAMP,Math.max(0,t)*RAMP_PER_SECOND):0);
}
function currentFallSpeed(){return speedAt(elapsed)}
function timeForDistance(distance){
 let lo=0,hi=Math.max(2,distance/baseSpeed()+3);
 for(let i=0;i<28;i++){const mid=(lo+hi)/2;if(trackTravel(mid)<distance)lo=mid;else hi=mid}
 return (lo+hi)/2;
}
// A normal key occupies one tile-height; the white gap before the next key
// is exactly ANOTHER tile-height (166 world units), regardless of tempo.
// Long notes occupy their full height before the same clean 166-unit gap.
// Chords share an exact timestamp, not a fake gap between simultaneous keys.
function spaceNotes(batch,previous=[]){
 const prev=previous.length?previous[previous.length-1]:null;
 let prevAt=prev?.at??null,prevLength=prev?
  Math.max(...previous.filter(n=>Math.abs(n.at-prevAt)<.01)
   .map(n=>n.hold?138+n.duration*speedAt(n.at):TILE_HEIGHT)):0;
 let groupRaw=null,currentAt=null;
 for(const n of batch){
  const raw=n.at;
  if(groupRaw===null||Math.abs(raw-groupRaw)>.01){
   currentAt=prevAt===null?Math.max(1.15,raw):
    timeForDistance(trackTravel(prevAt)+prevLength+TILE_GAP);
   prevAt=currentAt;prevLength=0;groupRaw=raw;
  }
  n.at=currentAt;
  n.holdSpeed=speedAt(currentAt);
  prevLength=Math.max(prevLength,n.hold?138+n.duration*n.holdSpeed:TILE_HEIGHT);
 }
 return batch;
}
function noteGeometry(n){
 const speed=currentFallSpeed(),bottom=n.state==="holding"&&Number.isFinite(n.hitBottom)?
  n.hitBottom:HIT+trackTravel(elapsed)-trackTravel(n.at);
 const length=n.hold?138+n.duration*(n.holdSpeed||speedAt(n.at)):TILE_HEIGHT;
 const tailLength=n.state==="holding"?
  138+Math.max(0,n.duration-(elapsed-n.start))*(n.holdSpeed||speedAt(n.at)):length;
 return {bottom,top:bottom-length,left:n.lane*100+4,right:n.lane*100+96,speed,length,tailLength};
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
 // Play the next musical note in order; simultaneous chord notes remain legal.
 const pending=notes.filter(t=>t.state==="waiting").sort((a,b)=>a.at-b.at);
 const earliest=pending[0]?.at;
 const n=pending.find(t=>{
  if(t.lane!==lane||t.at>earliest+.025)return false;
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
  n.state="holding";n.pointer=id;n.hitBottom=hit.bottom;n.holdSpeed=hit.speed;held.set(id,n);
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
 }else{
  // Piano Tiles 2 style: the player can release early and continue, but
  // only earns a fraction of the sustained-note bonus. Never award a full
  // held-note score for a one-frame tap.
  const fraction=Math.max(0,Math.min(1,(elapsed-n.start)/n.duration));
  finishNote(n,false);
  score+=Math.floor(fraction*70);
  updateHud();
  $("status").textContent="Partial hold";
 }
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
$("songSearch").addEventListener("input",e=>{songQuery=e.target.value.trim().toLocaleLowerCase();renderSongLibrary()});
$("songList").addEventListener("click",e=>{
 const heart=e.target.closest("[data-favourite]");
 if(heart){
  const id=heart.dataset.favourite;
  if(favorites.has(id))favorites.delete(id);else favorites.add(id);
  try{localStorage.setItem(favouriteKey,JSON.stringify([...favorites]))}catch{}
  renderSongLibrary();return;
 }
 const play=e.target.closest("[data-play]");
 if(play){chooseSong(play.dataset.play);begin()}
});
$("songBack").addEventListener("click",showLibrary);
buildFilters();renderSongLibrary();$("pause").hidden=true;
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
  if(trackTravel(endlessNextAt)-trackTravel(elapsed)<baseSpeed()*12){
   notes.push(...spaceNotes(extendEndless(20),notes));
   endlessNextAt=notes[notes.length-1].at;
  }
  if(notes.length>110)notes=notes.filter(t=>t.state!=="done");
 }
 if(!ended&&selectedSong.id!=="endless"&&complete===notes.length){win();return;}
 render();
 if(!ended)raf=requestAnimationFrame(tick);
}
function render(){
 const x=ctx;
 x.clearRect(0,0,W,H);
 x.fillStyle="#f6f8f8";x.fillRect(0,0,W,H);
 for(let lane=0;lane<LANES;lane++){
  x.fillStyle=lane%2===0?"#fafbf9":"#f0f3f1";
  x.fillRect(lane*100,0,100,H);
  if(lane){x.fillStyle="#d8e1dd";x.fillRect(lane*100-.7,0,1.4,H);}
 }
 // Held feedback belongs inside the long tile; never flash or tint an entire lane.
 // A low-contrast timing shelf, not a full-width black strike line.
 const cue=x.createLinearGradient(0,HIT-24,0,H);
 cue.addColorStop(0,"#d1dfd700");cue.addColorStop(1,"#c6d6cb64");
 x.fillStyle=cue;x.fillRect(0,HIT-24,W,H-HIT+24);
 for(const n of notes){
  if(n.state==="done")continue;
  const g=noteGeometry(n);
  if(g.bottom<0||g.top>H)continue;
  const progress=n.state==="holding"?Math.min(1,Math.max(0,(elapsed-n.start)/n.duration)):0;
  // The held head stays beneath the finger while the blue tail scrolls into it.
  const tail=g.tailLength;
  const tileTop=g.bottom-tail;
  const top=Math.max(-4,tileTop),bottom=Math.min(H+4,g.bottom);
  x.save();x.beginPath();x.rect(g.left,top,g.right-g.left,bottom-top);x.clip();
  const gradient=x.createLinearGradient(0,tileTop,0,g.bottom);
  if(n.hold){
   gradient.addColorStop(0,n.state==="holding"?"#77c5da":"#acdcee");
   gradient.addColorStop(.60,n.state==="holding"?"#409ebc":"#5cadd1");
   gradient.addColorStop(.83,n.state==="holding"?"#234b66":"#37596d");
   gradient.addColorStop(1,n.state==="holding"?"#142b3c":"#111f2c");
  }else{
   gradient.addColorStop(0,colors[n.lane]);gradient.addColorStop(1,deepColors[n.lane]);
  }
  x.fillStyle=gradient;x.fillRect(g.left,tileTop,g.right-g.left,tail);
  x.fillStyle=n.state==="holding"?"#bfe8d9":"#ffffff31";
  x.fillRect(g.left+9,tileTop+9,3,Math.max(0,tail-18));
  if(n.hold){
   x.fillStyle=n.state==="holding"?"#a1e0c9":"#aac3c9";
   x.fillRect(g.left+20,g.bottom-12,(g.right-g.left-40)*Math.max(.12,progress),4);
   x.strokeStyle="#eefaff";x.lineWidth=2;
   x.beginPath();x.arc((g.left+g.right)/2,g.bottom-27,8,0,Math.PI*2);x.stroke();
   x.beginPath();x.moveTo((g.left+g.right)/2,g.bottom-41);
   x.lineTo((g.left+g.right)/2,Math.max(tileTop+8,g.bottom-61));
   x.stroke();
  }
  x.restore();
 }
 // A flash-free subtle inset edge shows completion without lighting up whole lanes.

}
if(location.hostname==="localhost"||location.hostname==="127.0.0.1")
 window.__nightPianoTest=Object.freeze({snapshot:()=>({
  song:selectedSong.id,running,paused,ended,elapsed,score,streak,complete,endlessIndex,
  speed:currentFallSpeed(),distance:trackTravel(elapsed),
  notes:notes.map(n=>({lane:n.lane,at:n.at,duration:n.duration,hold:n.hold,state:n.state}))
 }),geometry:()=>notes.map(noteGeometry),travelAt:trackTravel});
clearRun();$("playPage").hidden=true;$("songLibrary").hidden=false;
scale();window.addEventListener("resize",scale,{passive:true});
})();
