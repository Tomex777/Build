/* NIGHT Play Chess — browser-only tabletop client. Chess rules are enforced by
 * vendored BSD-2-Clause chess.js v0.10.3, not custom pseudo-legal move guesses.
 * CPU/local games are honest offline matches, NEVER described as multiplayer.
 */
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const board=$('chessBoard'),promotionLayer=$('promotionLayer'),PERSIST='night-chess-match-v1';
const ICONS={p:'p',r:'r',n:'n',b:'b',q:'q',k:'k'};
const files='abcdefgh';
let match=new Chess(),mode='computer',selected=null,available=[],lastMove=null;
let busy=false,epoch=0,cpuTimer=null,cpuWorker=null,promotionPending=null,difficulty='normal';
let cpuWorkerReplies=0,lastCpuNodes=0,lastCpuDepth=0;
function pieceSVG(p){
 return '<svg class="piece '+p.color+'" viewBox="0 0 64 64" role="img" aria-label="'+(p.color==='w'?'White ':'Black ')+({p:'pawn',r:'rook',n:'knight',b:'bishop',q:'queen',k:'king'}[p.type])+'"><use href="#chess-'+ICONS[p.type]+'"></use></svg>';
}
function read(){
 try{
  const data=JSON.parse(localStorage.getItem(PERSIST)||'null');
  if(!data||!Array.isArray(data.moves))return;
  if(data.mode==='local'||data.mode==='computer')mode=data.mode;
  if(['easy','normal','hard'].includes(data.difficulty))difficulty=data.difficulty;
  const test=new Chess();
  for(const u of data.moves){
   if(typeof u!=='string'||!/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(u))throw Error('Bad move');
   const made=test.move({from:u.slice(0,2),to:u.slice(2,4),promotion:u[4]||'q'});
   if(!made)throw Error('Invalid move');
  }
  match=test;
  const history=match.history({verbose:true});
  lastMove=history.length?history[history.length-1]:null;
 }catch{match=new Chess();mode='computer';}
}
function save(){
 try{
  const data={mode,difficulty,moves:match.history({verbose:true}).map(m=>m.from+m.to+(m.promotion||''))};
  localStorage.setItem(PERSIST,JSON.stringify(data));
 }catch{}
}
function isHumanTurn(){return !busy&&!promotionPending&&!match.game_over()&&(mode==='local'||match.turn()==='w')}
function gameLabel(){
 if(match.in_checkmate())return match.turn()==='w'?'Black wins by checkmate':'White wins by checkmate';
 if(match.in_stalemate())return 'Draw by stalemate';
 if(match.in_threefold_repetition())return 'Draw by repetition';
 if(match.insufficient_material())return 'Draw · insufficient material';
 if(match.in_draw())return 'Game drawn';
 return match.turn()==='w'?'White to move':'Black to move';
}
function renderMoves(){
 const history=match.history(),el=$('moveList');
 if(!history.length){el.innerHTML='<span class="empty-moves">First move pending</span>';return}
 let html='';
 for(let i=0;i<history.length;i+=2)html+='<div class="move-row"><span class="move-num">'+(i/2+1)+'.</span><b>'+history[i]+'</b><b>'+(history[i+1]||'—')+'</b></div>';
 el.innerHTML=html;el.scrollTop=el.scrollHeight;
}
function render(){
 const current=match.turn(),legal=new Set(available.map(m=>m.to));
 const inCheck=match.in_check(),rows=[];
 for(let r=0;r<8;r++)for(let c=0;c<8;c++){
  const sq=files[c]+(8-r),p=match.get(sq);
  const active=selected===sq,checked=inCheck&&p?.type==='k'&&p.color===current;
  const justMoved=lastMove&&(lastMove.from===sq||lastMove.to===sq);
  const classes='chess-square '+((r+c)%2?'dark':'light')+(active?' selected':'')+(legal.has(sq)?' legal':'')+(justMoved?' lastmove':'')+(checked?' in-check':'');
  const coord=(c===0?'<span class="chess-coord">'+(8-r)+'</span>':'')+(r===7?'<span class="chess-coord file-coord">'+files[c]+'</span>':'');
  rows.push('<button type="button" role="gridcell" class="'+classes+'" data-square="'+sq+'" aria-label="'+sq+' '+(p?(p.color==='w'?'White ':'Black ')+({p:'pawn',r:'rook',n:'knight',b:'bishop',q:'queen',k:'king'}[p.type]):'empty')+'">'+(p?pieceSVG(p):'')+coord+'</button>');
 }
 board.innerHTML=rows.join('');
 $('matchStatus').textContent=gameLabel();
 $('matchMessage').textContent=match.game_over()?'Match finished. Start a new game to play again.':busy?'Piece moving…':inCheck?'Check! Your king must be protected.':
  current==='b'&&mode==='computer'?'Computer is choosing a move.':selected?'Choose a highlighted square.':'Select a piece to see legal moves.';
 $('whiteTurn').textContent=match.game_over()?'FINISHED':current==='w'?'TO PLAY':'WAITING';
 $('blackTurn').textContent=match.game_over()?'FINISHED':current==='b'?'TO PLAY':'WAITING';
 $('whiteTurn').classList.toggle('active',!match.game_over()&&current==='w');
 $('blackTurn').classList.toggle('active',!match.game_over()&&current==='b');
 $('modeLabel').textContent=mode==='computer'?'VS COMPUTER':'LOCAL · 2 PLAYERS';
 $('blackName').textContent=mode==='computer'?'Computer':'Black';
 $('blackDetails').textContent=mode==='computer'?'CPU · BLACK':'PLAYER 2 · BLACK';
 $('whiteDetails').textContent=mode==='computer'?'YOU · WHITE':'PLAYER 1 · WHITE';
 $('vsCpu').setAttribute('aria-pressed',String(mode==='computer'));
 $('vsLocal').setAttribute('aria-pressed',String(mode==='local'));
 $('difficultyRow').hidden=mode!=='computer';
 $('cpuDifficulty').value=difficulty;
 $('undo').disabled=busy||!match.history().length;
 $('localNote').textContent=mode==='computer'?'Computer plays black.':'Two players on one device.';
 renderMoves();save();
}
function cancel(){
 epoch++;busy=false;selected=null;available=[];promotionPending=null;
 clearTimeout(cpuTimer);cpuTimer=null;
 if(cpuWorker){cpuWorker.terminate();cpuWorker=null}
 promotionLayer.hidden=true;
}
function newMatch(nextMode=mode){
 cancel();mode=nextMode;match=new Chess();lastMove=null;render();
}
function squareClicked(sq){
 if(!isHumanTurn())return;
 const p=match.get(sq),color=match.turn();
 if(selected){
  const moves=available.filter(m=>m.to===sq);
  if(moves.length){
   if(moves.some(m=>m.promotion)){
    promotionPending={from:selected,to:sq};
    $('promotionChoices').innerHTML=['q','r','b','n'].map(x=>'<button type="button" data-promote="'+x+'" aria-label="Promote to '+({q:'queen',r:'rook',b:'bishop',n:'knight'}[x])+'">'+pieceSVG({color,type:x})+'</button>').join('');
    promotionLayer.hidden=false;$('promotionChoices').firstElementChild?.focus();return;
   }
   commitMove({from:selected,to:sq});return;
  }
 }
 if(p&&p.color===color){
  selected=sq;available=match.moves({square:sq,verbose:true});render();
 }else{selected=null;available=[];render()}
}
function animatePiece(from,to){
 if(matchMedia('(prefers-reduced-motion: reduce)').matches)return Promise.resolve();
 const source=board.querySelector('[data-square="'+from+'"] .piece');
 const target=board.querySelector('[data-square="'+to+'"]');
 if(!source||!target)return Promise.resolve();
 const a=source.getBoundingClientRect(),b=target.getBoundingClientRect(),ghost=source.cloneNode(true);
 ghost.classList.add('chess-flight');
 Object.assign(ghost.style,{left:a.left+'px',top:a.top+'px',width:a.width+'px',height:a.height+'px',margin:'0'});
 document.body.appendChild(ghost);source.style.visibility='hidden';
 const dx=b.left+b.width/2-a.left-a.width/2,dy=b.top+b.height/2-a.top-a.height/2;
 const flight=ghost.animate([{transform:'translate3d(0,0,0)'},{offset:.65,transform:'translate3d('+(dx*.65)+'px,'+(dy*.65-4)+'px,0)'},{transform:'translate3d('+dx+'px,'+dy+'px,0)'}],
  {duration:250,easing:'cubic-bezier(.2,.74,.2,1)',fill:'forwards'});
 return flight.finished.catch(()=>{}).finally(()=>{source.style.visibility='';ghost.remove()});
}
async function commitMove(move){
 if(busy||match.game_over())return false;
 const allowed=match.moves({verbose:true}).some(m=>m.from===move.from&&m.to===move.to&&(!m.promotion||(m.promotion===(move.promotion||'q'))));
 if(!allowed)return false;
 const token=epoch;busy=true;selected=null;available=[];promotionPending=null;promotionLayer.hidden=true;
 try{
  await animatePiece(move.from,move.to);
  if(token!==epoch)return false;
  const result=match.move({...move,promotion:move.promotion||'q'});
  if(!result)return false;
  lastMove=result;render();return true;
 }finally{
  if(token===epoch){
   busy=false;
   render();
   if(mode==='computer'&&match.turn()==='b'&&!match.game_over())queueCPU();
  }
 }
}
// The full game-tree calculation lives in a dedicated worker so even a
// low-end phone can continue drawing frames and accept an Undo immediately.
function queueCPU(){
 if(mode!=='computer'||match.turn()!=='b'||match.game_over()||busy||cpuTimer!==null||cpuWorker)return;
 const token=epoch;
 cpuTimer=setTimeout(()=>{
  cpuTimer=null;
  if(token!==epoch||mode!=='computer'||match.turn()!=='b'||match.game_over())return;
  const fallback=()=>{
   if(token!==epoch||mode!=='computer'||match.turn()!=='b'||match.game_over())return;
   // Worker failures must not leave a player staring at a frozen game.
   // Limit main-thread fallback to a small, legal, bounded search.
   const move=window.NightChessAI?.chooseMove(match,'easy',{time:90,maxNodes:1200});
   if(move)commitMove(move);
  };
  if(typeof Worker==='undefined'){fallback();return}
  let worker;
  try{
   worker=new Worker('/strategy/ai-worker.js');
   cpuWorker=worker;
   const moves=match.history({verbose:true}).map(m=>m.from+m.to+(m.promotion||''));
   worker.onmessage=event=>{
    if(cpuWorker!==worker)return;
    worker.terminate();cpuWorker=null;
    if(token!==epoch||mode!=='computer'||match.turn()!=='b')return;
    if(event.data?.error){fallback();return}
    const move=event.data?.move;
    if(!move||!match.moves({verbose:true}).some(m=>m.from===move.from&&m.to===move.to&&(!m.promotion||m.promotion===(move.promotion||'q')))){fallback();return}
    cpuWorkerReplies++;
    lastCpuNodes=Number(move.nodes)||0;lastCpuDepth=Number(move.depth)||0;
    commitMove(move);
   };
   worker.onerror=()=>{if(cpuWorker===worker){worker.terminate();cpuWorker=null;fallback()}};
   worker.postMessage({job:token,moves,difficulty});
  }catch{
   if(worker)worker.terminate();
   cpuWorker=null;
   fallback();
  }
 },160);
}
board.addEventListener('click',e=>{const cell=e.target.closest('[data-square]');if(cell)squareClicked(cell.dataset.square)});
$('vsCpu').addEventListener('click',()=>{if(mode!=='computer')newMatch('computer')});
$('vsLocal').addEventListener('click',()=>{if(mode!=='local')newMatch('local')});
$('cpuDifficulty').addEventListener('change',event=>{
 const next=event.target.value;if(!['easy','normal','hard'].includes(next))return;
 difficulty=next;
 if(mode==='computer'&&match.turn()==='b'&&!match.game_over()){cancel();render();queueCPU()}
 else save();
});
$('newMatch').addEventListener('click',()=>newMatch());
$('undo').addEventListener('click',()=>{
 if(busy||!match.history().length)return;
 cancel();
 if(mode==='computer'&&match.turn()==='w'&&match.history().length>=2)match.undo();
 match.undo();lastMove=match.history({verbose:true}).at(-1)||null;render();
 if(mode==='computer'&&match.turn()==='b')queueCPU();
});
$('promotionChoices').addEventListener('click',e=>{
 const button=e.target.closest('[data-promote]');if(!button||!promotionPending)return;
 commitMove({...promotionPending,promotion:button.dataset.promote});
});
$('promotionCancel').addEventListener('click',()=>{promotionPending=null;promotionLayer.hidden=true;render()});
window.__nightChessQA=()=>({fen:match.fen(),turn:match.turn(),moves:match.history(),gameOver:match.game_over(),inCheck:match.in_check(),mode,busy,legalSquares:available.map(m=>m.to),pendingPromotion:!!promotionPending,difficulty,thinking:!!cpuWorker||cpuTimer!==null,cpuWorkerReplies,lastCpuNodes,lastCpuDepth});
read();render();if(mode==='computer'&&match.turn()==='b'&&!match.game_over())queueCPU();
})();
