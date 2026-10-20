import {createLights,toggleLights,createSlide,slide,validSlide,solvedSlide,matchSimon} from './rules.mjs';
const requested=new URLSearchParams(location.search).get('game');
const kind=['lights','slide','simon'].includes(requested)?requested:'lights';
const labels={
 lights:['Lights Out','Logic puzzle','Turn every light off. One tap switches nearby lights too.'],
 slide:['Sliding Puzzle','Number puzzle','Slide numbered tiles into order and leave one space empty.'],
 simon:['Simon Says','Memory challenge','Watch the colour pattern, then repeat its growing sequence.']
};
const [title,category,description]=labels[kind];
document.title=title+' · Sora';
document.getElementById('title').textContent=title;
document.getElementById('category').textContent=category;
document.getElementById('description').textContent=description;
const boardEl=document.getElementById('board'),statsEl=document.getElementById('stats');
const actionsEl=document.getElementById('actions'),messageEl=document.getElementById('message');
const storageKey='sora_brain_'+kind+'_v1';
function load(){try{return JSON.parse(localStorage.getItem(storageKey))||{};}catch{return {};}}
function store(o){try{localStorage.setItem(storageKey,JSON.stringify(o));}catch{}}
const initial=load();
const safe=(n,fallback=0)=>Number.isSafeInteger(n)&&n>=0?n:fallback;
function stat(label,value){return '<div class="stat"><strong>'+value+'</strong><span>'+label+'</span></div>';}
function message(value,type=''){messageEl.textContent=value;messageEl.className='message '+type;}
function actions(items){actionsEl.replaceChildren();for(const [label,fn,primary] of items){
 const btn=document.createElement('button');btn.className='action'+(primary?' primary':'');btn.textContent=label;btn.onclick=fn;actionsEl.append(btn);
}}
function runLights(){
 let level=Math.min(60,Math.max(1,safe(initial.level,1))),moves=safe(initial.moves),best=safe(initial.best);
 let size=Math.min(5,3+Math.floor((level-1)/5));
 let board=Array.isArray(initial.board)&&initial.board.length===size*size&&
   initial.board.every(x=>typeof x==='boolean')?initial.board.slice():createLights(size,level*719+231).board;
 let won=board.every(x=>!x);
 const save=()=>store({level,moves,best,board});
 function restart(nextLevel){
  level=nextLevel;size=Math.min(5,3+Math.floor((level-1)/5));
  board=createLights(size,level*719+231).board;moves=0;won=false;save();render();
 }
 function render(){
  statsEl.innerHTML=stat('Level',level)+stat('Moves',moves)+stat('Best',best||'—');
  boardEl.className='lights';boardEl.style.gridTemplateColumns='repeat('+size+',1fr)';boardEl.replaceChildren();
  board.forEach((on,i)=>{
   const cell=document.createElement('button');cell.className='cell'+(on?' lit':'');
   cell.setAttribute('aria-label','Light row '+(Math.floor(i/size)+1)+' column '+(i%size+1)+(on?' on':' off'));
   cell.setAttribute('aria-pressed',String(on));cell.disabled=won;
   cell.onclick=()=>{
    if(won)return;
    board=toggleLights(board,size,i);moves++;won=board.every(x=>!x);
    if(won&&(!best||moves<best))best=moves;save();render();
   };
   boardEl.append(cell);
  });
  if(won){message('All lights out. Nice work.','win');actions([['Next level',()=>restart(Math.min(level+1,60)),true]]);}
  else{message('Each tap changes the selected light and its neighbours.');actions([['Restart',()=>restart(level)]]);}
 }
 render();
}
function runSlide(){
 let level=Math.min(40,Math.max(1,safe(initial.level,1))),moves=safe(initial.moves),best=safe(initial.best);
 let size=level<=3?3:4;
 let board=validSlide(initial.board,size)?initial.board.slice():createSlide(size,level*991+37);
 let won=solvedSlide(board);
 const save=()=>store({level,moves,best,board});
 function restart(nextLevel){
  level=nextLevel;size=level<=3?3:4;
  board=createSlide(size,level*991+37);moves=0;won=false;save();render();
 }
 function render(){
  statsEl.innerHTML=stat('Level',level)+stat('Moves',moves)+stat('Best',best||'—');
  boardEl.className='slide';boardEl.style.gridTemplateColumns='repeat('+size+',1fr)';boardEl.replaceChildren();
  board.forEach((value,i)=>{
   const possible=value!==0&&slide(board,size,i)!==null;
   const cell=document.createElement('button');cell.className='cell'+(value?'':' blank')+(possible?' ready':'');
   cell.textContent=value?String(value):'';cell.disabled=!possible||won;
   cell.setAttribute('aria-label',value?(possible?'Slide tile ':'Tile ')+value:'Empty space');
   cell.onclick=()=>{
    const next=slide(board,size,i);if(!next||won)return;
    board=next;moves++;won=solvedSlide(board);
    if(won&&(!best||moves<best))best=moves;save();render();
   };
   boardEl.append(cell);
  });
  if(won){message('Puzzle complete!','win');actions([['Next level',()=>restart(Math.min(level+1,40)),true]]);}
  else{message('Tap any numbered tile next to the empty space.');actions([['Restart',()=>restart(level)]]);}
 }
 render();
}
function runSimon(){
 let best=safe(initial.best),round=0,sequence=[],guess=0,phase='ready',timers=[],audio;
 const wait=(fn,time)=>timers.push(setTimeout(fn,time));
 const pads=[];
 function clear(){timers.forEach(clearTimeout);timers=[];pads.forEach(p=>p.classList.remove('active'));}
 function tone(at){
  try{
   audio=audio||new(window.AudioContext||window.webkitAudioContext)();
   if(audio.state==='suspended')audio.resume();
   const oscillator=audio.createOscillator(),gain=audio.createGain();
   oscillator.type='sine';oscillator.frequency.value=[329.63,392,493.88,587.33][at];
   gain.gain.setValueAtTime(.018,audio.currentTime);
   gain.gain.exponentialRampToValueAtTime(.001,audio.currentTime+.15);
   oscillator.connect(gain).connect(audio.destination);oscillator.start();oscillator.stop(audio.currentTime+.17);
  }catch{}
 }
 function flash(at,time){
  pads[at].classList.add('active');tone(at);wait(()=>pads[at].classList.remove('active'),time);
 }
 function render(){
  statsEl.innerHTML=stat('Round',round)+stat('Best',best);
  if(phase==='ready'){message('Ready to test your memory?');actions([['Start',start,true]]);}
  if(phase==='show'){message('Watch the sequence…');actions([]);}
  if(phase==='input'){message('Your turn · '+(guess+1)+' of '+sequence.length);actions([]);}
  if(phase==='lost'){message('Wrong colour. You reached round '+round+'.','fail');actions([['Try again',start,true]]);}
 }
 function present(){
  clear();phase='show';guess=0;render();
  const speed=Math.max(330,710-round*20);
  sequence.forEach((color,i)=>wait(()=>flash(color,speed*.6),300+i*speed));
  wait(()=>{phase='input';render();},300+sequence.length*speed+170);
 }
 function advance(){round++;sequence.push(Math.floor(Math.random()*4));present();}
 function start(){
  clear();round=0;sequence=[];phase='ready';
  try{audio=audio||new(window.AudioContext||window.webkitAudioContext)();audio.resume();}catch{}
  advance();
 }
 function choose(at){
  if(phase!=='input')return;
  flash(at,190);const verdict=matchSimon(sequence,guess,at);
  if(verdict==='wrong'){phase='lost';render();return;}
  if(verdict==='complete'){
   if(round>best){best=round;store({best});}
   phase='show';render();message('Perfect. Next round!','win');wait(advance,800);
  }else{guess++;render();}
 }
 boardEl.className='simon';
 ['Teal','Gold','Blue','Rose'].forEach((name,i)=>{
  const pad=document.createElement('button');pad.className='pad';
  pad.setAttribute('aria-label',name+' colour pad');pad.onclick=()=>choose(i);
  pads.push(pad);boardEl.append(pad);
 });
 window.addEventListener('pagehide',()=>{clear();if(audio)audio.close().catch(()=>{});},{once:true});
 render();
}
if(kind==='lights')runLights();else if(kind==='slide')runSlide();else runSimon();
