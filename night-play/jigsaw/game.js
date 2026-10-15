/**
 * NIGHT Play Jigsaw. Based on PlayJigsaw.net's MIT engine, with an original
 * NIGHT Play gallery, persistent saves, phone-first tray and pointer interaction.
 * The puzzle art is locally rendered; private image uploads never leave the browser.
 * See THIRD_PARTY_LICENSE.txt.
 */
import {PuzzleBoard} from './engine/PuzzleBoard.js';
import {SoundSynth} from './engine/SoundSynth.js';
import {ConfettiSystem} from './engine/Confetti.js';
import {renderThemeArt} from './themes.js';

const $=id=>document.getElementById(id);
const SAVE_KEY='night-play-jigsaw-v1';
const choices=[
 {id:'nature',name:'Alpine Escape',category:'nature'},
 {id:'sunset',name:'Golden Hour',category:'nature'},
 {id:'aurora',name:'Northern Lights',category:'fantasy'},
 {id:'castle',name:'Enchanted Castle',category:'fantasy'},
 {id:'cats',name:'Playful Cat',category:'animals'},
 {id:'anime',name:'Sakura Dawn',category:'anime'},
 {id:'abstract',name:'Chromatic Flow',category:'abstract'}
];
const dimMap={6:[2,3],12:[3,4],24:[4,6],48:[6,8]};
const canvas=$('puzzleCanvas'),wrapper=$('canvasWrapper'),board=new PuzzleBoard(canvas,wrapper);
const sound=new SoundSynth(),confetti=new ConfettiSystem();
let theme='nature',category='all',piecesCount=12,customData=null,elapsed=0,secondsTimer=null,won=false,activeRound=false;
let dragId=null,dragX=0,dragY=0,dragMoved=false,winAnimationId=null,winTimeout=null,toastTimeout=null;
const timeText=s=>String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');
function safeRead(key){try{return localStorage.getItem(key)}catch{return null}}
function safeWrite(key,value){try{localStorage.setItem(key,value);return true}catch{return false}}
function showToast(message){const t=$('toast');clearTimeout(toastTimeout);t.textContent=message;t.hidden=false;toastTimeout=setTimeout(()=>t.hidden=true,2800)}
function setTime(){ $('timer').textContent=timeText(elapsed);}
function setStats(){
 $('moves').textContent=String(board.moveCount);
 $('progress').textContent=board.placedCount+' / '+board.totalPieces;
 $('progressBar').style.width=Math.round(100*board.placedCount/Math.max(1,board.totalPieces))+'%';
 setTime();
}
function timerStop(){if(secondsTimer!==null){clearInterval(secondsTimer);secondsTimer=null}}
function timerStart(){if(secondsTimer!==null||won||!activeRound||document.hidden||$('playScreen').hidden)return;
 secondsTimer=setInterval(()=>{elapsed++;setTime();if(elapsed%5===0)saveGame()},1000);
}
function gameData(){
 return {version:1,theme,pieces:piecesCount,elapsed,won,customData:theme==='custom'?customData:null,
  board:board.serialize(),updatedAt:Date.now()};
}
function saveGame(){
 if(!activeRound||won)return;
 if(!safeWrite(SAVE_KEY,JSON.stringify(gameData()))&&theme==='custom')showToast('Your custom photo is too large to save. You can still play it.');
}
function resetEffects(){
 if(winTimeout!==null){clearTimeout(winTimeout);winTimeout=null;}
 if(winAnimationId!==null){cancelAnimationFrame(winAnimationId);winAnimationId=null;}
 confetti.particles=[];
 $('victoryDialog').close?.();
}
function resetBoard(fresh=true,saved=null){
 activeRound=true;won=false;elapsed=fresh?0:Math.max(0,Number(saved?.elapsed)||0);
 resetEffects();
 const dims=dimMap[piecesCount]||dimMap[12];
 board.setDimensions(...dims);board.showGhost=$('guideSwitch').checked;
 $('galleryPanel').hidden=true;$('playScreen').hidden=false;
 board.resize();
 if(theme==='custom'&&customData){
  // Custom pictures are processed before resetBoard to guarantee a valid source image.
 }else renderThemeArt(board,theme);
 if(!fresh&&saved?.board&&!board.restore(saved.board))board.buildPieces();
 else if(fresh||!saved?.board)board.buildPieces();
 board.showGhost=$('guideSwitch').checked;
 setStats();board.render();timerStop();timerStart();
 if(fresh)saveGame();
 window.scrollTo({top:0,behavior:'instant'});
}
function startImage(id){
 timerStop();customData=null;theme=id;resetBoard(true);showToast('Drag the pieces below into the picture');
}
function renderGallery(){
 const root=$('galleryGrid');root.replaceChildren();
 // Preview thumbnails reuse exactly the art used as puzzle source.
 const source=document.createElement('canvas');source.width=800;source.height=600;
 const previewBoard={sourceCanvas:source,sourceCtx:source.getContext('2d')};
 for(const item of choices){
  const btn=document.createElement('button');btn.className='gallery-card';btn.type='button';
  btn.dataset.category=item.category;btn.dataset.theme=item.id;
  const img=new Image();img.loading='lazy';img.alt=item.name+' puzzle artwork';img.src=renderThemeArt(previewBoard,item.id);
  const art=document.createElement('div');art.className='gallery-image';art.appendChild(img);
  const info=document.createElement('div');info.className='gallery-info';
  const title=document.createElement('b');title.textContent=item.name;
  const tag=document.createElement('small');tag.textContent=item.category;
  info.append(title,tag);btn.append(art,info);btn.addEventListener('click',()=>startImage(item.id));
  root.appendChild(btn);
 }
 const upload=document.createElement('button');upload.type='button';upload.className='gallery-card upload-card';upload.dataset.category='custom';
 upload.innerHTML='<div class="gallery-image"><svg class="ico" aria-hidden="true"><use href="#icon-upload"></use></svg><span class="gallery-upload-label">Choose your photo</span></div><div class="gallery-info"><b>Your picture</b><small>CUSTOM</small></div>';
 upload.addEventListener('click',()=>$('imageFile').click());root.appendChild(upload);
 filterGallery();
}
function filterGallery(){
 const list=[...$('galleryGrid').children];
 for(const btn of list)btn.hidden=category!=='all'&&btn.dataset.category!==category;
 const count=list.filter(x=>!x.hidden).length;
 $('galleryCount').textContent=count+(count===1?' picture':' pictures');
 for(const b of $('categoryChips').querySelectorAll('button')){const yes=b.dataset.filter===category;b.classList.toggle('active',yes);b.setAttribute('aria-pressed',String(yes));}
}
function showGallery(){
 timerStop();if(activeRound&&!won)saveGame();
 $('playScreen').hidden=true;$('galleryPanel').hidden=false;
 activeRound=false;category='all';filterGallery();window.scrollTo({top:0,behavior:'instant'});
}
function resumeSaved(saved){
 if(!saved||!dimMap[saved.pieces]||(!choices.some(c=>c.id===saved.theme)&&saved.theme!=='custom')||saved.won)return false;
 theme=saved.theme;piecesCount=saved.pieces;customData=saved.customData||null;
 if(theme==='custom'){
  if(!customData)return false;
  const img=new Image();
  img.onload=()=>{paintCustomSource(img);resetBoard(false,saved);};
  img.onerror=()=>{showGallery();showToast('Could not restore your picture');};
  img.src=customData;
 }else resetBoard(false,saved);
 updateDifficultyButtons();return true;
}
function updateDifficultyButtons(){
 for(const b of document.querySelectorAll('[data-pieces]'))b.classList.toggle('active',Number(b.dataset.pieces)===piecesCount);
}
function paintCustomSource(img){
 const ctx=board.sourceCtx,w=board.sourceCanvas.width,h=board.sourceCanvas.height;
 const ratio=Math.max(w/img.naturalWidth,h/img.naturalHeight),sw=w/ratio,sh=h/ratio;
 ctx.clearRect(0,0,w,h);ctx.drawImage(img,(img.naturalWidth-sw)/2,(img.naturalHeight-sh)/2,sw,sh,0,0,w,h);
}
function onPhoto(file){
 if(!file||!file.type.startsWith('image/'))return showToast('Please select an image file');
 if(file.size>12*1024*1024)return showToast('Please choose an image smaller than 12 MB');
 const url=URL.createObjectURL(file),img=new Image();
 img.onload=()=>{
  URL.revokeObjectURL(url);
  paintCustomSource(img);
  customData=board.sourceCanvas.toDataURL('image/jpeg',.78);
  theme='custom';timerStop();resetBoard(true);$('imageFile').value='';
 };
 img.onerror=()=>{URL.revokeObjectURL(url);showToast('Unable to read this image')};
 img.src=url;
}
function recordWin(){
 const key='night-play-jigsaw-best-'+piecesCount;
 const prior=Number(safeRead(key))||0;if(!prior||elapsed<prior)safeWrite(key,String(elapsed));
 try{
  const historyKey='night-play-history-v1-jigsaw',saves=JSON.parse(safeRead(historyKey)||'[]');
  const list=Array.isArray(saves)?saves:[];list.push(piecesCount);safeWrite(historyKey,JSON.stringify(list.slice(-12)));
  const countKey='night-play-history-count-v1-jigsaw';
  safeWrite(countKey,String((Number(safeRead(countKey))||0)+1));
  const prev=Number(safeRead('night-play-v1-jigsaw'))||0;
  if(piecesCount>prev)safeWrite('night-play-v1-jigsaw',String(piecesCount));
  localStorage.removeItem(SAVE_KEY);
 }catch{}
}
function celebrate(){
 if(won)return;won=true;timerStop();setStats();sound.playVictory();recordWin();
 $('victoryTime').textContent=timeText(elapsed);
 $('victoryMoves').textContent=String(board.moveCount);
 $('victoryPieces').textContent=String(board.totalPieces);
 confetti.explode(canvas.width/2,board.boardY+board.boardHeight/2,90);
 const started=performance.now();
 function loop(now){board.render(confetti);if(now-started<1800)winAnimationId=requestAnimationFrame(loop);else{winAnimationId=null;board.render()}}
 winAnimationId=requestAnimationFrame(loop);
 winTimeout=setTimeout(()=>{winTimeout=null;if(won&&!$('victoryDialog').open)$('victoryDialog').showModal()},650);
}
function refreshAfterMove(p){
 board.moveCount++;const snapped=board.checkSnap(p);
 if(snapped)sound.playSnap();
 setStats();board.render();saveGame();
 if(snapped&&board.placedCount===board.totalPieces)celebrate();
}
function point(e){
 const r=canvas.getBoundingClientRect();
 return {x:(e.clientX-r.left)*canvas.width/r.width,y:(e.clientY-r.top)*canvas.height/r.height};
}
canvas.addEventListener('pointerdown',e=>{
 if(!activeRound||won||dragId!==null||e.button!==0)return;
 const pos=point(e),hit=board.getPieceAt(pos.x,pos.y);if(!hit)return;
 const piece=board.bringToTop(hit.index);board.selectedPiece=piece;
 dragId=e.pointerId;dragX=pos.x-piece.x;dragY=pos.y-piece.y;dragMoved=false;
 canvas.classList.add('dragging');canvas.setPointerCapture(e.pointerId);
 sound.playPickup();board.render();e.preventDefault();
});
canvas.addEventListener('pointermove',e=>{
 if(e.pointerId!==dragId||!board.selectedPiece)return;
 const pos=point(e),p=board.selectedPiece;
 const x=Math.max(-board.pieceWidth*.15,Math.min(canvas.width-board.pieceWidth*.85,pos.x-dragX));
 const y=Math.max(-board.pieceHeight*.15,Math.min(canvas.height-board.pieceHeight*.85,pos.y-dragY));
 if(Math.hypot(x-p.x,y-p.y)>2)dragMoved=true;
 p.x=x;p.y=y;board.render();e.preventDefault();
});
function releaseDrag(e){
 if(e.pointerId!==dragId||!board.selectedPiece)return;
 const p=board.selectedPiece;board.selectedPiece=null;dragId=null;canvas.classList.remove('dragging');
 if(canvas.hasPointerCapture(e.pointerId))canvas.releasePointerCapture(e.pointerId);
 if(dragMoved||board.checkSnap(p)){ // snap only once, then count and complete
  if(p.isPlaced){board.placedCount--;p.isPlaced=false;}
  refreshAfterMove(p);
 }else{board.render();}
 e.preventDefault();
}
canvas.addEventListener('pointerup',releaseDrag);
canvas.addEventListener('pointercancel',releaseDrag);
function dialog(id){$(id).showModal()}
document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>$(b.dataset.close).close()));
$('settingsButton').addEventListener('click',()=>{updateDifficultyButtons();dialog('settingsDialog')});
$('guideSwitch').addEventListener('change',e=>{board.showGhost=e.target.checked;safeWrite('night-play-jigsaw-guide',String(e.target.checked));board.render();saveGame()});
$('soundSwitch').addEventListener('change',e=>{sound.enabled=e.target.checked;safeWrite('night-play-jigsaw-sound',String(e.target.checked))});
document.querySelectorAll('[data-pieces]').forEach(b=>b.addEventListener('click',()=>{
 const next=Number(b.dataset.pieces);if(next===piecesCount)return;
 piecesCount=next;updateDifficultyButtons();safeWrite('night-play-jigsaw-pieces',String(next));
 $('settingsDialog').close();
 if(activeRound&&$('playScreen').hidden===false)resetBoard(true);else showToast(piecesCount+' pieces selected');
}));
$('categoryChips').addEventListener('click',e=>{const b=e.target.closest('[data-filter]');if(b){category=b.dataset.filter;filterGallery()}});
$('pickPicture').addEventListener('click',showGallery);
$('previewButton').addEventListener('click',()=>{
 $('previewImage').src=board.sourceCanvas.toDataURL('image/jpeg',.86);dialog('previewDialog');
});
$('hintButton').addEventListener('click',()=>{
 if(won)return;const p=board.hintRandomPiece();if(!p)return;
 sound.playSnap();setStats();board.render();saveGame();if(board.placedCount===board.totalPieces)celebrate();
});
$('restartButton').addEventListener('click',()=>{if(theme==='custom'&&!customData)return;resetBoard(true)});
$('victoryNext').addEventListener('click',()=>{$('victoryDialog').close();showGallery()});
$('victoryReplay').addEventListener('click',()=>{$('victoryDialog').close();resetBoard(true)});
$('imageFile').addEventListener('change',e=>onPhoto(e.target.files?.[0]));
const resize=new ResizeObserver(()=>{if(!activeRound||$('playScreen').hidden)return;board.resize();board.render();saveGame()});
resize.observe(wrapper);
document.addEventListener('visibilitychange',()=>{if(document.hidden){timerStop();saveGame()}else timerStart()});
window.addEventListener('pagehide',()=>{timerStop();saveGame()});
window.addEventListener('keydown',e=>{
 if(e.key==='Escape'&&$('playScreen').hidden===false&&!['settingsDialog','previewDialog','victoryDialog'].some(id=>$(id).open))showGallery();
});
// Construct gallery from the same procedural images as the playing board.
renderGallery();
$('guideSwitch').checked=safeRead('night-play-jigsaw-guide')!=='false';
$('soundSwitch').checked=safeRead('night-play-jigsaw-sound')!=='false';
sound.enabled=$('soundSwitch').checked;
piecesCount=Number(safeRead('night-play-jigsaw-pieces'))||12;
if(!dimMap[piecesCount])piecesCount=12;
updateDifficultyButtons();
let prior=null;try{prior=JSON.parse(safeRead(SAVE_KEY)||'null')}catch{}
if(!resumeSaved(prior))showGallery();
if(location.hostname==='localhost'||location.hostname==='127.0.0.1'){
 window.__nightJigsawTest=Object.freeze({
  snapshot:()=>({theme,pieces:piecesCount,elapsed,won,activeRound,placed:board.placedCount,moves:board.moveCount,
   board:{x:board.boardX,y:board.boardY,w:board.boardWidth,h:board.boardHeight},tray:{...board.tray},
   pieces:board.pieces.map(p=>({id:p.id,x:p.x,y:p.y,correctX:p.correctX,correctY:p.correctY,placed:p.isPlaced}))}),
  place:(i)=>{const p=board.pieces[i];if(!p||p.isPlaced)return false;p.x=p.correctX;p.y=p.correctY;refreshAfterMove(p);return true}
 });
}
