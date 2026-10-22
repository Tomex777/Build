/* NIGHT Play shell controlling the unmodified Chromium runner's gameplay state.
 * Physics, collision geometry, jump timing and dinosaur frames are provided by
 * vendored chromium-runner.js rather than NIGHT's retired custom runner. */
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const soundKey='night-play-action-sfx-v1';
const bestKey='night-play-v1-action-runner-chromium';
let runner=null,started=false,paused=false,dead=false,sound=true,loadedSound=false;
let best=0,lastPersistedBest=0,lastSaveAt=0,activePointer=null,pointerDownY=0,score=0;
let touchGesture='idle',touchTimer=null;
try{sound=localStorage.getItem(soundKey)!=='off';best=Math.max(0,Number(localStorage.getItem(bestKey))||0);lastPersistedBest=best}catch{}
$('best').textContent=String(best);
function persistBest(){if(best<=lastPersistedBest)return;try{localStorage.setItem(bestKey,String(best));lastPersistedBest=best;lastSaveAt=performance.now()}catch{}}
function displayScore(n){score=Math.max(0,Math.floor(n));$('score').textContent=String(score);
 if(score>best){best=score;$('best').textContent=String(best)}
 if(best>lastPersistedBest&&performance.now()-lastSaveAt>2000)persistBest();}
function overlay(tag,title,copy,action){$('overlayTag').textContent=tag;$('overlayTitle').textContent=title;$('overlayCopy').textContent=copy;$('overlayAction').textContent=action;$('overlay').hidden=false}
function hideOverlay(){$('overlay').hidden=true}
function pauseIcon(isPaused){$('pauseButton').setAttribute('aria-label',isPaused?'Resume game':'Pause game');
 $('pauseButton').querySelector('use').setAttribute('href',isPaused?'#icon-play':'#icon-pause');}
function setSound(on){sound=on;const btn=$('soundToggle');btn.setAttribute('aria-pressed',String(on));btn.textContent='Sound effects · '+(on?'On':'Off');
 try{localStorage.setItem(soundKey,on?'on':'off')}catch{}}
setSound(sound);
function prepareAudio(){if(!sound||loadedSound||!runner)return;try{runner.loadSounds();loadedSound=true}catch(e){console.warn('Dino audio unavailable; gameplay unaffected',e)}}
function start(){if(!runner?.canvas)return;
 if(dead){runner.restart();dead=false}
 else if(!started){
  // Original Runner starts with an opening zoom animation on Chrome's error
  // page. NIGHT uses its own Start overlay but keeps Chromium physics intact.
  runner.activated=true;runner.playingIntro=false;runner.tRex.playingIntro=false;
  runner.tRex.xPos=50; // Chromium's normal post-intro running position.
  runner.containerEl.style.width=runner.dimensions.WIDTH+'px';
  runner.play();
 }else if(paused){runner.play()}
 started=true;paused=false;hideOverlay();pauseIcon(false);
 prepareAudio();$('arenaHint').textContent='TAP · JUMP / SWIPE ↓';
}
function pause(){if(!runner||!started||dead)return;
 if(paused){runner.play();paused=false;hideOverlay()}
 else{runner.stop();paused=true;persistBest();overlay('PAUSED','Endless Runner','Your run is on hold.','Resume')}
 pauseIcon(paused);
}
function jumpStart(){if(!runner||!started||paused||dead)return;
 if(!runner.tRex.jumping&&!runner.tRex.ducking){
  if(runner.soundFx?.BUTTON_PRESS)runner.playSound(runner.soundFx.BUTTON_PRESS);
  runner.tRex.startJump(runner.currentSpeed);
 }}
function jumpEnd(){if(runner&&runner.tRex?.jumping)runner.tRex.endJump()}
function duckStart(){if(!runner||!started||paused||dead)return;
 if(runner.tRex.jumping)runner.tRex.setSpeedDrop();
 else runner.tRex.setDuck(true);
}
function duckEnd(){if(runner?.tRex){runner.tRex.speedDrop=false;runner.tRex.setDuck(false)}}
$('overlayAction').addEventListener('click',()=>start());
$('pauseButton').addEventListener('click',()=>pause());
$('settingsButton').addEventListener('click',()=>{if(started&&!paused&&!dead)pause();$('settingsDialog').showModal()});
$('settingsClose').addEventListener('click',()=>$('settingsDialog').close());
$('settingsDialog').addEventListener('click',e=>{if(e.target===$('settingsDialog'))$('settingsDialog').close()});
$('soundToggle').addEventListener('click',()=>{setSound(!sound);if(sound)prepareAudio()});
// A single gesture surface: quick taps launch the original jump; sliding
// downward instead activates Chromium's native crouch. No dock/DUCK button.
// Short (45ms) recognition avoids a jump firing before a duck gesture.
$('touchPad').addEventListener('pointerdown',e=>{
 if(activePointer!==null)return;
 e.preventDefault();activePointer=e.pointerId;pointerDownY=e.clientY;
 touchGesture='pending';
 try{$('touchPad').setPointerCapture(e.pointerId)}catch{}
 touchTimer=setTimeout(()=>{
  touchTimer=null;
  if(touchGesture==='pending'){touchGesture='jump';jumpStart()}
 },45);
});
$('touchPad').addEventListener('pointermove',e=>{
 if(e.pointerId!==activePointer)return;
 const dy=e.clientY-pointerDownY;
 if(dy>23 && touchGesture!=='duck'){
  if(touchTimer){clearTimeout(touchTimer);touchTimer=null}
  if(touchGesture==='jump')jumpEnd();
  touchGesture='duck';duckStart();
 }else if(touchGesture==='jump'&&dy>23&&runner?.tRex?.jumping){
  runner.tRex.setSpeedDrop();
 }
});
function releaseTouch(e){
 if(e.pointerId!==activePointer)return;
 if(touchTimer){clearTimeout(touchTimer);touchTimer=null}
 if(touchGesture==='pending'){jumpStart();jumpEnd()}
 else if(touchGesture==='jump')jumpEnd();
 else if(touchGesture==='duck')duckEnd();
 activePointer=null;touchGesture='idle';
 try{$('touchPad').releasePointerCapture(e.pointerId)}catch{}
}
$('touchPad').addEventListener('pointerup',releaseTouch);
$('touchPad').addEventListener('pointercancel',releaseTouch);
$('touchPad').addEventListener('lostpointercapture',releaseTouch);
window.addEventListener('keydown',e=>{
 if(['Space','ArrowDown','ArrowUp'].includes(e.code))e.preventDefault();
 if($('settingsDialog').open||!$('overlay').hidden){
  if(['Space','ArrowDown','ArrowUp'].includes(e.code))e.stopImmediatePropagation();
  return;
 }
 if(e.code==='KeyP'){e.preventDefault();pause()}
},true);
document.addEventListener('visibilitychange',()=>{if(document.hidden&&started&&!paused&&!dead)pause()});
window.addEventListener('blur',()=>{if(started&&!paused&&!dead)pause()});
function pulse(){
 if(runner?.canvas){
  const measured=runner.distanceMeter?.getActualDistance?
   runner.distanceMeter.getActualDistance(Math.ceil(runner.distanceRan)):runner.distanceRan*.025;
  if(started)displayScore(measured);
  if(started&&runner.crashed&&!dead){
   dead=true;paused=false;duckEnd();activePointer=null;touchGesture='idle';
   if(touchTimer)clearTimeout(touchTimer);touchTimer=null;persistBest();
   overlay('GAME OVER','Run ended','Score '+score+' · Best '+best,'Play again');pauseIcon(false);
  }
 }
 requestAnimationFrame(pulse);
}
window.addEventListener('load',async()=>{
 if(typeof window.Runner!=='function'){overlay('UNAVAILABLE','Game did not load','Refresh to try again.','Retry');return}
 // Keep Chromium's untouched 150px physics world and jump arc, while its
 // *rendering canvas* occupies the complete height of the shared NIGHT arena.
 // Pixel sprites stay 1:1: the additional height is transparent world sky.
 // Wrapping native scaling also handles high-DPI buffers and resize repaint.
 const scaleNativeCanvas=window.Runner.updateCanvasScaling;
 window.Runner.updateCanvasScaling=function(canvas,logicalWidth){
  const nativeHeight=window.Runner.defaultDimensions.HEIGHT; // 150 gameplay units
  const fullHeight=Math.max(nativeHeight,Math.round($('stage').clientHeight-33));
  canvas.height=fullHeight; // clear/reset pixel buffer before upstream scales it
  const scaled=scaleNativeCanvas.call(this,canvas,logicalWidth,fullHeight);
  canvas.style.height=fullHeight+'px';
  canvas.getContext('2d').translate(0,fullHeight-nativeHeight);
  return scaled;
 };
 try{await window.NightDinoSkin.install()}catch(e){
  console.error('Unable to install NIGHT game artwork',e);
  overlay('UNAVAILABLE','Artwork unavailable','Refresh to try again.','Retry');return;
 }
 runner=new window.Runner('#main-frame-error');
 const nativeSound=runner.playSound.bind(runner);
 runner.playSound=buffer=>{if(sound)nativeSound(buffer)};
 // Native Chromium pixel counters remain available for scoring, but NIGHT
 // shows the score just once in its shared HUD.
 const hideNativeMeter=()=>{if(runner.distanceMeter)runner.distanceMeter.draw=()=>{}};
 hideNativeMeter();
 runner.tRex.xPos=50;
 window.__nightDinoTest={snapshot:()=>({
  ready:!!runner.canvas,playing:runner.playing,paused,dead,started,
  dinosaurY:runner.tRex?.yPos,groundY:runner.tRex?.groundYPos,
  jumping:runner.tRex?.jumping,ducking:runner.tRex?.ducking,
  obstacles:runner.horizon?.obstacles?.map(o=>({type:o.typeConfig?.type,x:o.xPos,y:o.yPos,w:o.width})),
  speed:runner.currentSpeed,score,best,distanceRan:runner.distanceRan,
  canvasHeight:runner.canvas?.getBoundingClientRect().height,
  arenaHeight:$('stage').getBoundingClientRect().height,
  gravity:runner.tRex?.config.GRAVITY,jumpVelocity:runner.tRex?.config.INIITAL_JUMP_VELOCITY
 }),jump:jumpStart,release:jumpEnd,pause,start};
 pulse();
});
})();
