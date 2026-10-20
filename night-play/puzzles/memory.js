/* NIGHT Play Memory Match — mobile-first deck studio and game engine.
 * This module owns only Memory Match. Sudoku and Connect Four remain untouched.
 * All pair images are stable; no random image calls after a game starts. */
(function(root){
"use strict";
const core=root.NIGHTPuzzleCore,art=root.NIGHTMemoryArt;
if(!core||!art)throw new Error("Memory engine requires rule and art modules");
const KEY="night-play-memory-v2",OLD_KEY="night-play-puzzle-room-v1-memory",PREF_KEY="night-play-memory-board-size-v1";
const SIZES=[[4,4],[4,5],[4,6],[4,8],[4,10],[5,6],[6,5],[6,6],[6,8],[6,10]];
const DEFAULT={theme:"wildlife",cols:6,rows:5};
const isSize=(c,r)=>SIZES.some(([a,b])=>a===c&&b===r);
const themeValid=v=>art.themes.some(t=>t.id===v);
const plain=v=>v&&typeof v==="object"&&!Array.isArray(v);
function safeParse(key){try{return JSON.parse(localStorage.getItem(key)||"null");}catch{return null;}}
function valid(v){
 if(!plain(v)||v.version!==2||v.type!=="memory"||!themeValid(v.theme)||!isSize(v.cols,v.rows))return false;
 const count=v.cols*v.rows,pairs=count/2;
 if(!["setup","playing"].includes(v.phase)||!Array.isArray(v.deck)||v.deck.length!==count)return false;
 if(v.deck.some(n=>!Number.isInteger(n)||n<0||n>=pairs))return false;
 const counts=Array(pairs).fill(0);v.deck.forEach(n=>counts[n]++);
 if(counts.some(n=>n!==2))return false;
 if(!Array.isArray(v.matched)||v.matched.length!==count||v.matched.some(n=>typeof n!=="boolean"))return false;
 for(let i=0;i<count;i++)if(v.matched[i]!==v.matched[v.deck.findIndex(n=>n===v.deck[i])])return false;
 if(!Number.isInteger(v.moves)||v.moves<0||v.moves>1000000||!Number.isInteger(v.elapsed)||v.elapsed<0||v.elapsed>1000000)return false;
 if(typeof v.done!=="boolean"||(v.done&&!v.matched.every(Boolean)))return false;
 return true;
}
function initial(){
 const previous=safeParse(KEY);if(valid(previous))return previous;
 const legacy=safeParse(OLD_KEY);
 if(plain(legacy)&&legacy.type==="memory"&&Array.isArray(legacy.deck)&&legacy.deck.length===16&&
  legacy.deck.every(n=>Number.isInteger(n)&&n>=0&&n<8)&&Array.isArray(legacy.matched)&&legacy.matched.length===16){
  const counts=Array(8).fill(0);legacy.deck.forEach(n=>counts[n]++);
  if(counts.every(n=>n===2)){
   const matched=legacy.matched.map(Boolean),done=matched.every(Boolean);
   return {version:2,type:"memory",phase:"playing",theme:"shapes",cols:4,rows:4,
    deck:legacy.deck.slice(),matched,moves:Math.max(0,Math.trunc(legacy.tries)||0),
    elapsed:Math.max(0,Math.trunc(legacy.elapsed)||0),done};
  }
 }
 const seed=(Date.now()^(Math.random()*0xffffffff))>>>0;
 return {version:2,type:"memory",phase:"setup",...DEFAULT,
  deck:core.memoryDeck(15,seed),matched:Array(30).fill(false),moves:0,elapsed:0,done:false};
}
function preferredSize(){
 const pref=safeParse(PREF_KEY);
 return pref&&isSize(pref.cols,pref.rows)?{cols:pref.cols,rows:pref.rows}:{cols:DEFAULT.cols,rows:DEFAULT.rows};
}
function saveSize(cols,rows){
 if(!isSize(cols,rows))return false;
 try{localStorage.setItem(PREF_KEY,JSON.stringify({cols,rows}));}catch{}
 return true;
}
function create({board,tools,status,message,newButton}){
 let state=initial(),draft={theme:state.theme,...preferredSize()};
 let timer=null,pending=null,active=false,lock=false,flipped=[],statusMessage="";
 function save(){try{localStorage.setItem(KEY,JSON.stringify(state));}catch{}}
 function clearPending(){if(pending!==null){clearTimeout(pending);pending=null;}lock=false;flipped=[];}
 function stopTimer(){if(timer!==null){clearInterval(timer);timer=null;}}
 function formatTime(t){return String(Math.floor(t/60)).padStart(2,"0")+":"+String(t%60).padStart(2,"0");}
 function updateStatus(){
  status.replaceChildren();status.classList.toggle("memory-toolbar",state.phase==="playing");
  if(state.phase!=="playing")return;
  const parts=[
   [String(state.matched.filter(Boolean).length/2)+" / "+String(state.deck.length/2),"Pairs"],
   [String(state.moves),"Moves"],[formatTime(state.elapsed),"Time"]];
  for(const [number,label] of parts){const chip=document.createElement("span");chip.className="memory-stat";
   const big=document.createElement("strong");big.textContent=number;
   const small=document.createElement("small");small.textContent=label;chip.append(big,small);status.append(chip);}
  const change=document.createElement("button");change.type="button";change.className="memory-deck-btn";
  change.dataset.memoryAction="change";change.textContent="Change deck";status.append(change);
 }
 function showMessage(text){message.textContent=text||"";}
 // Button labels reveal the actual card identity only after a face is turned.
 // The art itself is deliberately aria-hidden; without these labels the game
 // cannot be played with a screen reader, because every card sounds identical.
 function faceName(id,theme=state.theme){
  if(theme==="storybook")return art.figureNames[id]||"Character "+(id+1);
  if(theme==="shapes")return "Shape "+(id+1);
  return art.photos[theme]?.[id]?.[0]||"Picture "+(id+1);
 }
 function cardArtwork(id,theme=state.theme,loadPhoto=true){
  return art.face(theme,id,loadPhoto);
 }
 function themePreview(theme){
  return '<span class="memory-theme-art">'+[2,1,0].map((id,k)=>'<span class="memory-theme-fan fan-'+k+'">'+cardArtwork(id,theme)+'</span>').join("")+'</span>';
 }
 function renderSetup(){
  clearPending();stopTimer();state.phase="setup";save();
  board.className="puzzle-board memory-choice-workspace";
  status.classList.remove("memory-toolbar");status.replaceChildren();
  tools.replaceChildren();newButton.hidden=true;
  message.textContent="";
  const options=art.themes.map(t=>'<button type="button" class="memory-theme-option'+(draft.theme===t.id?' chosen':'')+
   '" data-memory-theme="'+t.id+'" aria-pressed="'+(draft.theme===t.id)+'" aria-label="Choose '+art.escapeText(t.name)+' deck">'+
   themePreview(t.id)+'<span class="memory-theme-info"><strong>'+art.escapeText(t.name)+'</strong><small>'+art.escapeText(t.caption)+'</small></span></button>').join("");
  board.innerHTML='<div class="memory-setup-top"><div class="memory-setup-overline">MAKE IT YOURS</div><h2>Pick your world.</h2><p>A deck you love. A challenge that fits.</p></div>'+
   '<h3 class="memory-section-label">CARD COLLECTION</h3><div class="memory-theme-grid">'+options+'</div>'+
   '<button type="button" class="memory-start" data-memory-action="start">Start matching <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="m5 12h14m-6-6 6 6-6 6" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg></button>'+
   (state.moves>0&&!state.done?'<button type="button" class="memory-return" data-memory-action="resume">Return to saved match</button>':'')+
   '<p class="memory-layout-credit">Original NIGHT artwork. Photography from Unsplash; photos require a connection.</p>';
 }
 // Grid choice lives in the existing top-right Settings sheet, never on the
 // deck picker. Persist the preference separately so changing it leaves the
 // active match untouched; it takes effect only after explicitly starting over.
 function renderSettings(host){
  host.replaceChildren();
  const title=document.createElement("span");title.className="puzzle-options-label";
  title.textContent="BOARD SIZE";host.append(title);
  const options=document.createElement("div");options.className="memory-settings-grid";
  const preferred=preferredSize();
  for(const [cols,rows] of SIZES){
   const button=document.createElement("button");
   button.type="button";button.className="memory-settings-size";
   button.dataset.memorySize=cols+"x"+rows;
   const selected=preferred.cols===cols&&preferred.rows===rows;
   button.setAttribute("aria-pressed",String(selected));
   button.innerHTML="<strong>"+cols+" × "+rows+"</strong><small>"+(cols*rows)+" cards</small>";
   options.append(button);
  }
  host.append(options);
  const note=document.createElement("p");note.className="memory-settings-note";note.id="memorySizeNote";
  note.textContent=state.phase==="playing"
   ?"Saved for your next round. Your current "+state.cols+" × "+state.rows+" match stays intact."
   :"Saved for the next round. Extra rows scroll vertically.";
  host.append(note);
 }
 function changePreferredSize(e){
  const button=e.target.closest("[data-memory-size]");
  if(!button)return;
  const [cols,rows]=button.dataset.memorySize.split("x").map(Number);
  if(!saveSize(cols,rows))return;
  draft.cols=cols;draft.rows=rows;
  renderSettings(e.currentTarget);
 }
 function buildBoard(){
  board.className="puzzle-board memory-grid memory-playing-board";
  board.style.setProperty("--memory-columns",state.cols);
  const fragment=document.createDocumentFragment();
  state.deck.forEach((id,i)=>{
   const b=document.createElement("button");b.type="button";b.dataset.memory=String(i);b.className="memory-card";
   b.innerHTML='<span class="memory-flip-body"><span class="memory-card-back"><span class="memory-back-mark" aria-hidden="true"><svg viewBox="0 0 108 108"><path fill="currentColor" d="M24 54 C24 37.43 37.43 24 54 24 C70.57 24 84 37.43 84 54 L24 54 Z"/><path fill="currentColor" d="M22 66h64v6H22z"/></svg></span></span>'+
   '<span class="memory-card-front">'+cardArtwork(id,state.theme,false)+'</span></span>';
   fragment.append(b);
  });
  board.replaceChildren(fragment);tools.replaceChildren();
  newButton.hidden=false;updateStatus();syncCards();
 }
 // The 6×10 board can contain 60 cards. Avoid requesting 30 remote photographs
 // before the player has flipped even one card; hidden backs use local SVG art.
 // An attempted request is remembered per card so offline failures do not loop
 // through fresh requests every time another card is flipped.
 function requestVisiblePhoto(button,id){
  if(button.dataset.photoRequested==="1")return;
  const url=art.photoUrl(state.theme,id);
  if(!url)return;
  const host=button.querySelector(".memory-photo-layer");
  if(!host)return;
  button.dataset.photoRequested="1";
  const img=document.createElement("img");
  img.alt="";img.draggable=false;img.decoding="async";img.loading="eager";
  host.append(img);
  img.src=url;
 }
 function syncCards(){
  for(let i=0;i<state.deck.length;i++){
   const button=board.children[i];if(!button)continue;
   const matched=state.matched[i],open=flipped.includes(i);
   if(matched||open)requestVisiblePhoto(button,state.deck[i]);
   button.classList.toggle("flipped",open);button.classList.toggle("matched",matched);
   button.disabled=matched||lock||state.done;
   const label="Card "+(i+1)+(matched?", matched: "+faceName(state.deck[i]):open?", revealed: "+faceName(state.deck[i]):", face down");
   if(button.getAttribute("aria-label")!==label)button.setAttribute("aria-label",label);
  }
 }
 function tick(){
  if(!active||state.phase!=="playing"||state.done||document.hidden)return;
  state.elapsed++;save();
  const elapsed=status.querySelectorAll(".memory-stat strong")[2];if(elapsed)elapsed.textContent=formatTime(state.elapsed);
 }
 function showPlaying(){
  clearPending();state.phase="playing";save();showMessage("");
  buildBoard();stopTimer();timer=setInterval(tick,1000);
 }
 function start(){
  const count=draft.cols*draft.rows,seed=(Date.now()^(Math.random()*4294967295))>>>0;
  state={version:2,type:"memory",phase:"playing",...draft,deck:core.memoryDeck(count/2,seed),matched:Array(count).fill(false),moves:0,elapsed:0,done:false};
  save();showPlaying();window.scrollTo(0,0);
 }
 function toSetup(){
  clearPending();draft={theme:state.theme,...preferredSize()};renderSetup();window.scrollTo(0,0);
 }
 function pick(i){
  if(state.phase!=="playing"||state.done||lock||state.matched[i]||flipped.includes(i))return;
  flipped.push(i);syncCards();
  if(flipped.length<2)return;
  state.moves++;
  const [a,b]=flipped;
  if(state.deck[a]===state.deck[b]){
   state.matched[a]=true;state.matched[b]=true;flipped=[];
   if(state.matched.every(Boolean)){state.done=true;statusMessage="All "+(state.deck.length/2)+" pairs found in "+state.moves+" moves · "+formatTime(state.elapsed);stopTimer();showMessage(statusMessage);}
   else showMessage("Pair found!");
   save();syncCards();updateStatus();
   return;
  }
  lock=true;syncCards();updateStatus();showMessage("");
  pending=setTimeout(()=>{pending=null;if(!active||state.phase!=="playing")return;
   flipped=[];lock=false;syncCards();save();},820);
  save();
 }
 function click(e){
  const theme=e.target.closest("[data-memory-theme]");
  if(theme&&state.phase==="setup"){
   draft.theme=theme.dataset.memoryTheme;renderSetup();return;
  }
  const action=e.target.closest("[data-memory-action]");
  if(action){
   if(action.dataset.memoryAction==="start"){start();return;}
   if(action.dataset.memoryAction==="resume"&&state.deck.length){showPlaying();window.scrollTo(0,0);return;}
   if(action.dataset.memoryAction==="change"){toSetup();return;}
  }
  const card=e.target.closest("[data-memory]");
  if(card&&state.phase==="playing")pick(Number(card.dataset.memory));
 }
 function imageEvent(e){
  const img=e.target;
  if(!(img instanceof HTMLImageElement)||!img.closest(".memory-choice-workspace,.memory-playing-board"))return;
  if(e.type==="load"&&img.naturalWidth>0)img.classList.add("ready");
  if(e.type==="error")img.remove();
 }
 document.getElementById("gameOptions").addEventListener("click",changePreferredSize);
 board.addEventListener("click",click);
 board.addEventListener("load",imageEvent,true);
 board.addEventListener("error",imageEvent,true);
 status.addEventListener("click",click);
 function mount(){
  active=true;state=valid(safeParse(KEY))?safeParse(KEY):initial();
  draft={theme:state.theme,...preferredSize()};
  if(state.phase==="playing"){showPlaying();if(state.done)showMessage("All "+(state.deck.length/2)+" pairs found in "+state.moves+" moves · "+formatTime(state.elapsed));}
  else renderSetup();
 }
 function unmount(){
  clearPending();stopTimer();save();active=false;
  board.classList.remove("memory-choice-workspace","memory-playing-board");
  board.style.removeProperty("--memory-columns");status.classList.remove("memory-toolbar");
  newButton.hidden=false;
 }
 function restart(){
  if(!active)return;
  if(state.phase==="setup"){start();return;}
  draft={theme:state.theme,...preferredSize()};start();
 }
 function pageHide(){
  if(!active)return;
  if(document.hidden){
   clearPending();save();syncCards();
  }
 }
 document.addEventListener("visibilitychange",pageHide);
 root.addEventListener("pagehide",save);
 return {mount,unmount,restart,renderSettings,phase:()=>state.phase,snapshot:()=>JSON.parse(JSON.stringify(state))};
}
root.NIGHTMemory={create,SIZES,valid};
})(typeof window!=="undefined"?window:globalThis);
