/**
 * NIGHT Play Jigsaw. Based on PlayJigsaw.net's MIT engine, with an original
 * NIGHT Play gallery, persistent saves, phone-first tray and pointer interaction.
 * Curated real photos replace procedural art; legacy puzzles and custom uploads are supported.
 * See THIRD_PARTY_LICENSE.txt.
 */
import {PuzzleBoard} from './engine/PuzzleBoard.js';
import {SoundSynth} from './engine/SoundSynth.js';
import {ConfettiSystem} from './engine/Confetti.js';
import {renderThemeArt} from './themes.js';
import {photos,getPhoto,photoUrl} from './photo-gallery.js';

const $=id=>document.getElementById(id);
const SAVE_KEY='night-play-jigsaw-v1';
const choices=photos;
const legacyArtIds=new Set(['nature','sunset','aurora','castle','cats','anime','abstract']);
const loadedPhotos=new Map(),inflightPhotos=new Map();
let imageLoadVersion=0;
function loadPhoto(id){
 const photo=getPhoto(id);
 if(!photo)return Promise.reject(new Error('Unknown picture'));
 if(loadedPhotos.has(id))return Promise.resolve(loadedPhotos.get(id));
 if(inflightPhotos.has(id))return inflightPhotos.get(id);
 const request=new Promise((resolve,reject)=>{
  const img=new Image();img.crossOrigin='anonymous';
  img.onload=()=>img.naturalWidth&&img.naturalHeight?resolve(img):reject(new Error('Empty image'));
  img.onerror=()=>reject(new Error('Image unavailable'));
  img.src=photoUrl(photo);
 });
 inflightPhotos.set(id,request);
 request.then(img=>{loadedPhotos.set(id,img);inflightPhotos.delete(id)},()=>inflightPhotos.delete(id));
 return request;
}
function paintPhotoSource(img){
 const ctx=board.sourceCtx,w=board.sourceCanvas.width,h=board.sourceCanvas.height;
 const scale=Math.max(w/img.naturalWidth,h/img.naturalHeight),sw=w/scale,sh=h/scale;
 ctx.clearRect(0,0,w,h);
 ctx.drawImage(img,(img.naturalWidth-sw)/2,(img.naturalHeight-sh)/2,sw,sh,0,0,w,h);
}
const dimMap={6:[2,3],12:[3,4],24:[4,6],48:[6,8],72:[8,9],96:[8,12],144:[12,12]};
const modeRules={
 relaxed:{hints:3,previews:3,peekMs:6000},
 challenge:{hints:1,previews:1,peekMs:4000},
 memory:{hints:0,previews:1,peekMs:5000}
};
let challengeMode='relaxed',usedHints=0,usedPreviews=0,previewTimeout=null,previewTick=null,paused=false;
const canvas=$('puzzleCanvas'),wrapper=$('canvasWrapper'),board=new PuzzleBoard(canvas,wrapper);
const sound=new SoundSynth(),confetti=new ConfettiSystem();
let theme='photo-alpine-lake',category='all',piecesCount=24,customData=null,elapsed=0,secondsTimer=null,won=false,activeRound=false;
let galleryReady=false,resumeCandidate=null;
let dragId=null,dragX=0,dragY=0,dragMoved=false,dragRect=null,dragOrigins=null,winAnimationId=null,winTimeout=null,toastTimeout=null;
let zoom=1,panX=0,panY=0,panId=null,panStart=null,pinchDistance=0,pinchStartZoom=1;
const pointers=new Map();
function applyZoom(){
 const maxX=(zoom-1)*canvas.width*.5,maxY=(zoom-1)*canvas.height*.5;
 panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));
 const transform='translate('+panX+'px,'+panY+'px) scale('+zoom+')';
 canvas.style.transform=transform;board.dragCanvas.style.transform=transform;$('previewOverlay').style.transform=transform;
 $('zoomReset').textContent=Math.round(zoom*100)+'%';
 $('zoomOut').disabled=zoom<=1.001;$('zoomIn').disabled=zoom>=2.2;
}
function setZoom(value){
 zoom=Math.max(1,Math.min(2.2,Math.round(value*100)/100));
 if(zoom===1){panX=0;panY=0}applyZoom();
}

const timeText=s=>String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');
function safeRead(key){try{return localStorage.getItem(key)}catch{return null}}
function safeWrite(key,value){try{localStorage.setItem(key,value);return true}catch{return false}}
function showToast(message){const t=$('toast');clearTimeout(toastTimeout);t.textContent=message;t.hidden=false;toastTimeout=setTimeout(()=>t.hidden=true,2800)}
function guideEnabled(){return challengeMode!=='memory'&&$('guideSwitch').checked;}
function updateHelpUI(){
 const rules=modeRules[challengeMode],hints=Math.max(0,rules.hints-usedHints),previews=Math.max(0,rules.previews-usedPreviews);
 $('hintRemaining').textContent=String(hints);$('previewRemaining').textContent=String(previews);
 $('hintButton').setAttribute('aria-label','Use a hint, '+hints+' left');
 $('previewButton').setAttribute('aria-label','Preview picture, '+previews+' left');
 $('hintButton').disabled=!activeRound||won||hints===0;
 $('previewButton').disabled=!activeRound||won||previews===0;
 $('guideSwitch').disabled=challengeMode==='memory';
 for(const b of document.querySelectorAll('[data-mode]')){
  const selected=b.dataset.mode===challengeMode;b.classList.toggle('active',selected);b.setAttribute('aria-pressed',String(selected));
 }
}
function endPreview(){
 if(previewTimeout!==null){clearTimeout(previewTimeout);previewTimeout=null}
 if(previewTick!==null){clearInterval(previewTick);previewTick=null}
 $('previewOverlay').hidden=true;
}
function positionPreview(){
 const layer=$('previewOverlay');
 layer.style.left=board.boardX/canvas.width*100+'%';
 layer.style.top=board.boardY/canvas.height*100+'%';
 layer.style.width=board.boardWidth/canvas.width*100+'%';
 layer.style.height=board.boardHeight/canvas.height*100+'%';
}
function showReference({automatic=false}={}){
 if(!activeRound||won||paused)return;
 const rules=modeRules[challengeMode];
 if(usedPreviews>=rules.previews){if(!automatic)showToast('No picture peeks left this round');return}
 endPreview();usedPreviews++;updateHelpUI();saveGame();closeHudMenu();
 const layer=$('previewOverlay');
 $('previewImage').src=board.sourceCanvas.toDataURL('image/jpeg',.85);
 positionPreview();layer.hidden=false;applyZoom();
 const expiry=Date.now()+rules.peekMs;
 const tick=()=>{$('previewCountdown').textContent=Math.max(0,Math.ceil((expiry-Date.now())/1000))+'s'};
 tick();previewTick=setInterval(tick,250);
 previewTimeout=setTimeout(endPreview,rules.peekMs);
}
function setTime(){ $('timer').textContent=timeText(elapsed);}
function setStats(){
 $('moves').textContent=String(board.moveCount);
 $('progress').textContent=board.placedCount+' / '+board.totalPieces;
 $('progressBar').style.width=Math.round(100*board.placedCount/Math.max(1,board.totalPieces))+'%';
 setTime();
}
function timerStop(){if(secondsTimer!==null){clearInterval(secondsTimer);secondsTimer=null}}
function timerStart(){if(secondsTimer!==null||paused||won||!activeRound||document.hidden||$('playScreen').hidden)return;
 secondsTimer=setInterval(()=>{elapsed++;setTime();if(elapsed%5===0)saveGame()},1000);
}
function gameData(){
 return {version:2,theme,pieces:piecesCount,elapsed,won,challengeMode,usedHints,usedPreviews,customData:theme==='custom'?customData:null,
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
 endPreview();
 $('victoryDialog').close?.();
}
function resetBoard(fresh=true,saved=null){
 activeRound=true;won=false;paused=false;$('pauseOverlay').hidden=true;updatePauseButton();closeHudMenu();
 elapsed=fresh?0:Math.max(0,Number(saved?.elapsed)||0);
 if(fresh){usedHints=0;usedPreviews=0}
 else{usedHints=Math.min(modeRules[challengeMode].hints,Math.max(0,Number(saved?.usedHints)||0));
  usedPreviews=Math.min(modeRules[challengeMode].previews,Math.max(0,Number(saved?.usedPreviews)||0))}
 resetEffects();
 const dims=dimMap[piecesCount]||dimMap[24];
 board.setDimensions(...dims);board.showGhost=guideEnabled();
 setZoom(1);board.edgeOnly=false;$('edgesButton').setAttribute('aria-pressed','false');
 $('openingPanel').hidden=true;$('resumePanel').hidden=true;$('galleryPanel').hidden=true;$('playScreen').hidden=false;
 wrapper.classList.toggle('master-puzzle',piecesCount===144);
 board.resize();
 if(theme==='custom'&&customData){
  // Custom pictures are processed before resetBoard to guarantee a valid source image.
  }else if(getPhoto(theme)){
  const img=loadedPhotos.get(theme);
  if(!img)throw new Error('Image not yet loaded');
  paintPhotoSource(img);
 }else renderThemeArt(board,theme);
 if(!fresh&&saved?.board&&!board.restore(saved.board))board.buildPieces();
 else if(fresh||!saved?.board)board.buildPieces();
 board.showGhost=guideEnabled();
 $('edgesButton').setAttribute('aria-pressed',String(board.edgeOnly));
 setStats();board.render();updateHelpUI();timerStop();timerStart();
 if(fresh){saveGame();if(challengeMode==='memory')setTimeout(()=>{if(activeRound&&!won&&challengeMode==='memory'&&usedPreviews===0)showReference({automatic:true})},120)}
 window.scrollTo({top:0,behavior:'instant'});
}
async function startImage(id){
 if(!getPhoto(id))return;
 const loadVersion=++imageLoadVersion;
 const card=$('galleryGrid').querySelector('[data-theme="'+id+'"]');
 card?.classList.add('is-loading');
 try{
  await loadPhoto(id);
  if(loadVersion!==imageLoadVersion)return;
  timerStop();customData=null;theme=id;resetBoard(true);
  // Let the image and its pieces speak for themselves; no startup toast.
 }catch{
  if(loadVersion===imageLoadVersion)showToast('Picture could not load. Check your connection and retry.');
 }finally{card?.classList.remove('is-loading')}
}
function renderGallery(){
 if(galleryReady)return;
 galleryReady=true;
 const root=$('galleryGrid');root.replaceChildren();
 // Lightweight thumbnails load lazily; full-resolution images load on selection.
 for(const item of choices){
  const btn=document.createElement('button');btn.className='gallery-card';btn.type='button';
  btn.dataset.category=item.category;btn.dataset.theme=item.id;
  const img=new Image();img.loading='lazy';img.decoding='async';img.alt=item.name+' photograph';img.src=photoUrl(item,true);
  const art=document.createElement('div');art.className='gallery-image';art.appendChild(img);
  img.onerror=()=>{img.classList.add('is-broken');const fail=document.createElement('span');fail.className='image-error';fail.textContent='Image unavailable';art.appendChild(fail)};
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
 const count=list.filter(x=>!x.hidden&&!x.classList.contains('upload-card')).length;
 $('galleryCount').textContent=count+(count===1?' picture':' pictures');
 for(const b of $('categoryChips').querySelectorAll('button')){const yes=b.dataset.filter===category;b.classList.toggle('active',yes);b.setAttribute('aria-pressed',String(yes));}
}
function savedCandidate(){
 try{
  const saved=JSON.parse(safeRead(SAVE_KEY)||'null');
  return saved&&!saved.won&&dimMap[saved.pieces]&&
   (getPhoto(saved.theme)||legacyArtIds.has(saved.theme)||(saved.theme==='custom'&&saved.customData))?saved:null;
 }catch{return null}
}
function continuePuzzle(){
 const saved=savedCandidate();
 if(!saved){showGallery();return}
 $('resumePanel').hidden=true;
 $('openingPanel').hidden=true;
 $('resumeGalleryBanner').hidden=true;
 if(!resumeSaved(saved)){showGallery();showToast('Your saved puzzle could not be restored')}
}
function showGallery(){
 if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});
 closeHudMenu();paused=false;$('pauseOverlay').hidden=true;imageLoadVersion++;
 timerStop();if(activeRound&&!won)saveGame();
 endPreview();$('playScreen').hidden=true;$('resumePanel').hidden=true;$('openingPanel').hidden=true;
 activeRound=false;category='all';
 renderGallery();
 $('galleryPanel').hidden=false;
 $('resumeGalleryBanner').hidden=!savedCandidate();
 filterGallery();window.scrollTo({top:0,behavior:'instant'});
}
function showWelcome(saved){
 resumeCandidate=saved;
 const item=choices.find(x=>x.id===saved.theme);
 const label=item?.name||(saved.theme==='custom'?'Your photograph':'Your saved picture');
 $('resumeSummary').textContent=label+' · '+saved.pieces+' pieces · '+Math.min(saved.pieces,saved.board?.placedCount||saved.board?.pieces?.filter(p=>p.isPlaced).length||0)+' completed. Pick up exactly where you stopped.';
 $('openingPanel').hidden=true;$('playScreen').hidden=true;$('galleryPanel').hidden=true;$('resumePanel').hidden=false;
}
function resumeSaved(saved){
 if(!saved||!dimMap[saved.pieces]||(!getPhoto(saved.theme)&&!legacyArtIds.has(saved.theme)&&saved.theme!=='custom')||saved.won)return false;
 const loadVersion=++imageLoadVersion;
 theme=saved.theme;piecesCount=saved.pieces;customData=saved.customData||null;
 challengeMode=modeRules[saved.challengeMode]?saved.challengeMode:'relaxed';
 if(theme==='custom'){
  if(!customData)return false;
  const img=new Image();
  img.onload=()=>{if(loadVersion!==imageLoadVersion)return;paintCustomSource(img);resetBoard(false,saved)};
  img.onerror=()=>{if(loadVersion===imageLoadVersion){showGallery();showToast('Could not restore your picture')}};
  img.src=customData;
 }else if(getPhoto(theme)){
  loadPhoto(theme).then(()=>{
   if(loadVersion===imageLoadVersion)resetBoard(false,saved);
  }).catch(()=>{
   if(loadVersion===imageLoadVersion){showGallery();showToast('Saved picture could not load. Check your connection.')}
  });
 }else resetBoard(false,saved); // Legacy artwork stays unchanged.
 updateDifficultyButtons();return true;
}
function updateDifficultyButtons(){
 for(const b of document.querySelectorAll('[data-pieces]'))b.classList.toggle('active',Number(b.dataset.pieces)===piecesCount);
 updateHelpUI();
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
  ++imageLoadVersion;
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
 if(won)return;paused=false;endPreview();won=true;timerStop();setStats();sound.playVictory();recordWin();updateHelpUI();
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
 if(paused)return;
 board.moveCount++;const snapped=board.checkSnap(p),joined=board.lastJoinCount>0;
 board.selectedPieces=[];
 if(snapped||joined)sound.playSnap({joined});else sound.playDrop();
 setStats();board.render();saveGame();
 if(board.placedCount===board.totalPieces)celebrate();
}
function point(e){
 // The CSS transform is applied equally to both canvases. The transformed
 // bounding box converts touch coordinates back into original tabletop units.
 const r=dragRect||canvas.getBoundingClientRect();
 return {x:(e.clientX-r.left)*canvas.width/r.width,y:(e.clientY-r.top)*canvas.height/r.height};
}
function cancelSelection(){
 if(!board.selectedPiece)return;
 for(const p of board.selectedPieces){
  const origin=dragOrigins?.get(p.id);if(origin){p.x=origin.x;p.y=origin.y;}
 }
 board.selectedPiece=null;board.selectedPieces=[];dragId=null;dragRect=null;
 canvas.classList.remove('dragging');board.render();
}
canvas.addEventListener('pointerdown',e=>{
 if(!activeRound||won||paused||e.button!==0)return;
 pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
 if(pointers.size===2){
  cancelSelection();panId=null;const points=[...pointers.values()];
  pinchDistance=Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y);
  pinchStartZoom=zoom;
  canvas.setPointerCapture(e.pointerId);e.preventDefault();return;
 }
 if(pointers.size>2||dragId!==null)return;
 dragRect=canvas.getBoundingClientRect();
 const pos=point(e),hit=board.getPieceAt(pos.x,pos.y);
 if(!hit){
  dragRect=null;
  if(zoom>1){panId=e.pointerId;panStart={clientX:e.clientX,clientY:e.clientY,x:panX,y:panY};canvas.setPointerCapture(e.pointerId);e.preventDefault();}
  return;
 }
 const piece=board.bringToTop(hit.index);board.selectedPiece=piece;
 dragId=e.pointerId;dragX=pos.x-piece.x;dragY=pos.y-piece.y;
 dragOrigins=new Map(board.selectedPieces.map(p=>[p.id,{x:p.x,y:p.y}]));
 dragMoved=false;canvas.classList.add('dragging');
 canvas.setPointerCapture(e.pointerId);
 sound.playPickup();board.render();e.preventDefault();
});
canvas.addEventListener('pointermove',e=>{
 if(pointers.has(e.pointerId))pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
 if(pointers.size>=2&&pinchDistance>0){
  const points=[...pointers.values()];
  const current=Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y);
  setZoom(pinchStartZoom*current/Math.max(1,pinchDistance));e.preventDefault();return;
 }
 if(e.pointerId===panId&&panStart){
  panX=panStart.x+(e.clientX-panStart.clientX);
  panY=panStart.y+(e.clientY-panStart.clientY);
  applyZoom();e.preventDefault();return;
 }
 if(e.pointerId!==dragId||!board.selectedPiece)return;
 const pos=point(e),p=board.selectedPiece;
 const x=Math.max(-board.pieceWidth*.15,Math.min(canvas.width-board.pieceWidth*.85,pos.x-dragX));
 const y=Math.max(-board.pieceHeight*.15,Math.min(canvas.height-board.pieceHeight*.85,pos.y-dragY));
 if(Math.hypot(x-p.x,y-p.y)>2)dragMoved=true;
 board.moveSelectionTo(x,y);board.renderDrag();e.preventDefault();
});
function releaseDrag(e){
 pointers.delete(e.pointerId);
 if(pointers.size<2)pinchDistance=0;
 if(e.pointerId===panId){
  panId=null;panStart=null;if(canvas.hasPointerCapture(e.pointerId))canvas.releasePointerCapture(e.pointerId);
  e.preventDefault();return;
 }
 if(e.pointerId!==dragId||!board.selectedPiece)return;
 const p=board.selectedPiece;
 board.selectedPiece=null;dragId=null;dragRect=null;
 canvas.classList.remove('dragging');
 if(canvas.hasPointerCapture(e.pointerId))canvas.releasePointerCapture(e.pointerId);
 if(e.type==='pointercancel'){
  for(const item of board.selectedPieces){const origin=dragOrigins?.get(item.id);if(origin){item.x=origin.x;item.y=origin.y;}}
  board.selectedPieces=[];board.render();e.preventDefault();return;
 }
 if(dragMoved)refreshAfterMove(p);
 else{board.selectedPieces=[];board.render();}
 e.preventDefault();
}
canvas.addEventListener('pointerup',releaseDrag);
canvas.addEventListener('pointercancel',releaseDrag);
canvas.addEventListener('wheel',e=>{
 if(!activeRound||won||(!e.ctrlKey&&!e.metaKey))return;
 setZoom(zoom*(e.deltaY<0?1.1:1/1.1));e.preventDefault();
},{passive:false});
$('zoomIn').addEventListener('click',()=>setZoom(zoom*1.12));
$('zoomOut').addEventListener('click',()=>setZoom(zoom/1.12));
$('zoomReset').addEventListener('click',()=>setZoom(1));
$('edgesButton').addEventListener('click',()=>{
 if(paused)return;
 board.edgeOnly=!board.edgeOnly;
 $('edgesButton').setAttribute('aria-pressed',String(board.edgeOnly));
 sound.playPickup();board.render();saveGame();
});
$('sweepButton').addEventListener('click',()=>{
 if(!activeRound||won||paused)return;
 board.sweepLoose();sound.playDrop();saveGame();
});
function dialog(id){$(id).showModal()}
document.querySelectorAll('[data-close]').forEach(b=>b.addEventListener('click',()=>$(b.dataset.close).close()));

$('settingsButton').addEventListener('click',()=>{closeHudMenu();updateDifficultyButtons();dialog('settingsDialog')});
$('guideSwitch').addEventListener('change',e=>{board.showGhost=guideEnabled();safeWrite('night-play-jigsaw-guide',String(e.target.checked));board.render();saveGame()});
$('soundSwitch').addEventListener('change',e=>{sound.enabled=e.target.checked;safeWrite('night-play-jigsaw-sound',String(e.target.checked))});
document.querySelectorAll('[data-mode]').forEach(b=>b.addEventListener('click',()=>{
 const next=b.dataset.mode;if(next===challengeMode||!modeRules[next])return;
 challengeMode=next;safeWrite('night-play-jigsaw-mode',next);
 $('settingsDialog').close();updateHelpUI();
 if(activeRound&&!$('playScreen').hidden)resetBoard(true);
 else showToast('Mode: '+next);
}));
document.querySelectorAll('[data-pieces]').forEach(b=>b.addEventListener('click',()=>{
 const next=Number(b.dataset.pieces);if(next===piecesCount)return;
 piecesCount=next;updateDifficultyButtons();safeWrite('night-play-jigsaw-pieces',String(next));
 $('settingsDialog').close();
 if(activeRound&&$('playScreen').hidden===false)resetBoard(true);else showToast(piecesCount+' pieces selected');
}));
$('categoryChips').addEventListener('click',e=>{const b=e.target.closest('[data-filter]');if(b){category=b.dataset.filter;filterGallery()}});
$('pickPicture').addEventListener('click',showGallery);
function closeHudMenu(){
 $('hudMenu').hidden=true;$('hudMenuButton').setAttribute('aria-expanded','false');
}
$('hudMenuButton').addEventListener('click',e=>{
 e.stopPropagation();const opened=$('hudMenu').hidden;
 $('hudMenu').hidden=!opened;$('hudMenuButton').setAttribute('aria-expanded',String(opened));
});
$('hudMenu').addEventListener('click',e=>e.stopPropagation());
document.addEventListener('pointerdown',e=>{
 if(!$('hudMenu').hidden&&!$('hudMenu').contains(e.target)&&!$('hudMenuButton').contains(e.target))closeHudMenu();
});
$('hudSettingsButton').addEventListener('click',()=>$('settingsButton').click());
$('restartMenuButton').addEventListener('click',()=>$('restartButton').click());
function updatePauseButton(){
 $('pauseButton').setAttribute('aria-pressed',String(paused));
 $('pauseButton').setAttribute('aria-label',paused?'Resume puzzle':'Pause puzzle');
 $('pauseButton').title=paused?'Resume':'Pause';
 $('pauseButton').querySelector('use').setAttribute('href',paused?'#icon-play':'#icon-pause');
 $('pauseOverlay').hidden=!paused;
}
function togglePause(){
 if(!activeRound||won)return;
 paused=!paused;endPreview();closeHudMenu();updatePauseButton();
 if(paused){timerStop();saveGame()}else{timerStart();board.render()}
}
$('pauseButton').addEventListener('click',togglePause);
$('resumeButton').addEventListener('click',togglePause);
$('resumePlaying').addEventListener('click',continuePuzzle);
$('resumeFromGallery').addEventListener('click',continuePuzzle);
$('startFresh').addEventListener('click',showGallery);
$('previewButton').addEventListener('click',()=>showReference());
$('hintButton').addEventListener('click',()=>{
 if(!activeRound||won||paused||usedHints>=modeRules[challengeMode].hints){showToast('No hints left in this round');return}
 const p=board.hintRandomPiece();if(!p)return;
 usedHints++;sound.playSnap();setStats();board.render();updateHelpUI();saveGame();
 if(board.placedCount===board.totalPieces)celebrate();
});
$('restartButton').addEventListener('click',()=>{
 if(!activeRound||won)return;
 if(theme==='custom'&&!customData)return;
 closeHudMenu();if($('settingsDialog').open)$('settingsDialog').close();resetBoard(true);
});
const fullscreenButton=$('fullscreenButton');
function updateFullscreenButton(){
 const active=Boolean(document.fullscreenElement);
 fullscreenButton.setAttribute('aria-pressed',String(active));
 fullscreenButton.setAttribute('aria-label',active?'Exit full screen':'Enter full screen');
 fullscreenButton.title=active?'Exit full screen':'Full screen';
 fullscreenButton.lastChild.textContent=active?'Exit fullscreen':'Fullscreen';
 fullscreenButton.querySelector('use').setAttribute('href',active?'#icon-contract':'#icon-expand');
 // Browser viewport resizes synchronously after fullscreen transitions.
 if(activeRound&&!$('playScreen').hidden)requestAnimationFrame(()=>{
  board.resize();positionPreview();applyZoom();board.render();
 });
}
fullscreenButton.addEventListener('click',async()=>{
 closeHudMenu();
 try{
  if(document.fullscreenElement)await document.exitFullscreen();
  else if(document.documentElement.requestFullscreen)await document.documentElement.requestFullscreen();
  else showToast('Full screen is not supported by this browser');
 }catch{showToast('Full screen is unavailable on this device')}
});
document.addEventListener('fullscreenchange',updateFullscreenButton);
$('victoryNext').addEventListener('click',()=>{$('victoryDialog').close();showGallery()});
$('victoryReplay').addEventListener('click',()=>{$('victoryDialog').close();resetBoard(true)});
$('imageFile').addEventListener('change',e=>onPhoto(e.target.files?.[0]));
const resize=new ResizeObserver(()=>{if(!activeRound||$('playScreen').hidden)return;board.resize();positionPreview();applyZoom();board.render();saveGame()});
resize.observe(wrapper);
document.addEventListener('visibilitychange',()=>{if(document.hidden){timerStop();endPreview();saveGame()}else timerStart()});
window.addEventListener('pagehide',()=>{timerStop();saveGame()});
window.addEventListener('keydown',e=>{
 if(e.key==='Escape'){
  if(!$('hudMenu').hidden){closeHudMenu();return}
  if(!$('previewOverlay').hidden){endPreview();return}
  if(!document.fullscreenElement&&!$('playScreen').hidden&&!['settingsDialog','victoryDialog'].some(id=>$(id).open)){e.preventDefault();togglePause()}
 }
 if((e.key===' '||e.key==='p'||e.key==='P')&&activeRound&&!$('playScreen').hidden&&!['INPUT','TEXTAREA','BUTTON'].includes(document.activeElement.tagName)){e.preventDefault();togglePause()}
});
// Gallery is intentionally NOT rendered on page load when a saved puzzle exists.
 // This prevents an unfinished puzzle from flashing the full gallery before resuming.
$('guideSwitch').checked=safeRead('night-play-jigsaw-guide')==='true';
applyZoom();
challengeMode=modeRules[safeRead('night-play-jigsaw-mode')]?safeRead('night-play-jigsaw-mode'):'relaxed';
$('soundSwitch').checked=safeRead('night-play-jigsaw-sound')!=='false';
sound.enabled=$('soundSwitch').checked;
piecesCount=Number(safeRead('night-play-jigsaw-pieces'))||24;
if(!dimMap[piecesCount])piecesCount=24;
updateDifficultyButtons();
const prior=savedCandidate();
if(prior)showWelcome(prior);
else showGallery();
if(location.hostname==='localhost'||location.hostname==='127.0.0.1'){
 window.__nightJigsawTest=Object.freeze({
  snapshot:()=>({theme,pieces:piecesCount,elapsed,won,activeRound,challengeMode,usedHints,usedPreviews,guide:board.showGhost,
   activeDragId:dragId,staticPaints:board.staticPaintCount,dragPaints:board.dragPaintCount,zoom,edgeOnly:board.edgeOnly,groups:new Set(board.pieces.filter(p=>!p.isPlaced).map(p=>p.group)).size,
   selected:board.selectedPiece?{id:board.selectedPiece.id,x:board.selectedPiece.x,y:board.selectedPiece.y}:null,
   placed:board.placedCount,moves:board.moveCount,
   board:{x:board.boardX,y:board.boardY,w:board.boardWidth,h:board.boardHeight,rows:board.rows,cols:board.cols,pieceWidth:board.pieceWidth,pieceHeight:board.pieceHeight},tray:{...board.tray},
   pieces:board.pieces.map(p=>({id:p.id,x:p.x,y:p.y,group:p.group,correctX:p.correctX,correctY:p.correctY,placed:p.isPlaced}))}),
  place:(i)=>{const p=board.pieces[i];if(!p||p.isPlaced)return false;p.x=p.correctX;p.y=p.correctY;refreshAfterMove(p);return true}
 });
}
