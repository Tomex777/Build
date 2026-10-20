/* NIGHT Play on-board Tic-Tac-Toe victory and defeat.
 * The original in-board draw overlay remains untouched. This module adds the
 * same presentation to X and O wins without rewriting the legacy monolithic
 * index page; remove the bridge when TTT is extracted into its own module. */
(()=>{
'use strict';
let timer=null,activeLayer=null,generation=0;
function cancel(){
 generation++;clearTimeout(timer);timer=null;
 activeLayer?.remove();activeLayer=null;
}
function seeFinished(){
 const board=document.getElementById('tttboard'),label=document.getElementById('turnlabel');
 if(!board||!label||!board.isConnected||board.querySelector('.ttt-draw-overlay'))return;
 const value=label.textContent?.trim();
 if(value!=='X wins!'&&value!=='O wins!')return;
 cancel();
 const token=generation;
 const winner=value[0],againstCPU=document.getElementById('modeBtn')?.textContent?.includes('BOT');
 const result=againstCPU?(winner==='X'?'win':'loss'):'local';
 const title=againstCPU?(winner==='X'?'You won!':'You lost'):winner+' wins!';
 const subtitle=againstCPU?(winner==='X'?'Good game. You took this round.':'The computer takes this round.'):'Well played. '+winner+' takes this round.';
 const curtain=document.createElement('div');curtain.className='ttt-draw-overlay';
 curtain.dataset.result=result;curtain.setAttribute('role','status');
 curtain.setAttribute('aria-label',title+' Next round starts automatically.');
 curtain.innerHTML='<div class="ttt-draw-card"><span class="ttt-draw-kicker">ROUND COMPLETE</span><svg class="ttt-draw-symbol" viewBox="0 0 128 70" fill="none" aria-hidden="true"><path class="draw-x" d="M11 16 37 54M37 16 11 54"/><path class="draw-equal" d="M53 27h22M53 41h22"/><circle class="draw-o" cx="102" cy="35" r="21"/></svg><h3></h3><p></p><div class="ttt-draw-next"><span>Next round starting</span><span class="ttt-draw-seconds" aria-hidden="true">3s</span></div><div class="ttt-draw-track" aria-hidden="true"><div class="ttt-draw-fill"></div></div></div>';
 curtain.querySelector('h3').textContent=title;
 curtain.querySelector('p').textContent=subtitle;
 board.appendChild(curtain);activeLayer=curtain;
 timer=setTimeout(()=>{
  if(generation!==token||!board.isConnected||activeLayer!==curtain||label.textContent?.trim()!==value)return;
  activeLayer=null;curtain.remove();
  if(typeof window.resetTttRound==='function')window.resetTttRound();
 },3000);
}
const styles=document.createElement('style');
styles.textContent='.ttt-draw-overlay[data-result="win"] .ttt-draw-card{background:linear-gradient(145deg,#fbfff7,#dff1e5);border-color:#aad9b8}.ttt-draw-overlay[data-result="loss"] .ttt-draw-card{background:linear-gradient(145deg,#fffaf8,#f7e5df);border-color:#ecc5b8}.ttt-draw-overlay[data-result="win"] .ttt-draw-kicker{color:#267148}.ttt-draw-overlay[data-result="loss"] .ttt-draw-kicker{color:#a25944}';
document.head.appendChild(styles);
if(typeof window.moveTtt==='function'){
 const original=window.moveTtt;
 window.moveTtt=function(...args){const value=original.apply(this,args);queueMicrotask(seeFinished);return value};
}
if(typeof window.initTtt==='function'){
 const original=window.initTtt;
 window.initTtt=function(...args){cancel();const value=original.apply(this,args);queueMicrotask(seeFinished);return value};
}
window.__nightTttResultTest={seeFinished,cancel};
})();
