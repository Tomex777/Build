/* Agent 5 standalone contract tests. Run with:
 * node night-play/jigsaw/co-op-adapter-test.mjs
 * These simulate browser geometry; they do not claim a deployed server.
 */
import assert from 'node:assert/strict';
import {PuzzleBoard} from './engine/PuzzleBoard.js';
import {toCellPosition,fromCellPosition,makeDropCommand,validateSnapshot,
 previewServerSnapshot,applyServerSnapshot} from './engine/CoopAdapter.js';

const nop=()=>{};
const ctx=()=>new Proxy({},{
 get(obj,key){
  if(key==='createLinearGradient')return ()=>({addColorStop:nop});
  return obj[key]??nop;
 },
 set(obj,key,value){obj[key]=value;return true;}
});
let canvases=0;
const canvas=()=>({width:1,height:1,style:{},getContext:()=>ctx()});
globalThis.document={createElement(tag){
 assert.equal(tag,'canvas');canvases++;return canvas();
}};
globalThis.window={devicePixelRatio:2};
function makeBoard(width=390,height=844){
 const drag=canvas(),wrapper={
  width,height,querySelector(s){assert.equal(s,'#dragCanvas');return drag;},
  getBoundingClientRect(){return {width:this.width,height:this.height}}
 };
 const board=new PuzzleBoard(canvas(),wrapper);
 return {board,wrapper};
}
function fixture(count){
 const [rows,cols]=({24:[4,6],48:[6,8],144:[12,12]})[count];
 const h=Array.from({length:rows-1},(_,r)=>Array.from({length:cols},(_,c)=>(r+c)%2?1:-1));
 const v=Array.from({length:rows},(_,r)=>Array.from({length:cols-1},(_,c)=>(r*c)%2?-1:1));
 const pieces=[];
 for(let row=0;row<rows;row++)for(let col=0;col<cols;col++){
  const left=(row*cols+col)%2===0;
  pieces.push({
   id:row+'-'+col,row,col,group:row+'-'+col,
   x:left?-1.4:cols+1.2,y:((row*cols+col)*.27)%(rows-.6),
   version:0,placed:false,
   edges:{top:row===0?0:-h[row-1][col],right:col===cols-1?0:v[row][col],
    bottom:row===rows-1?0:h[row][col],left:col===0?0:-v[row][col-1]}
  });
 }
 return {format:1,roundId:'00000000-0000-4000-8000-000000000001',photoId:'photo-alpine-lake',count,rows,cols,revision:0,moves:0,
  finishedAt:null,pieces};
}
for(const count of [24,48,144]){
 const snapshot=fixture(count);
 assert.equal(validateSnapshot(snapshot),snapshot,'server contract '+count);
 const {board,wrapper}=makeBoard();
 board.setDimensions(snapshot.rows,snapshot.cols);
 board.resize();
 const projected=previewServerSnapshot(board,snapshot);
 assert.equal(projected.length,count);
 assert.ok(projected.some(p=>p.y<board.boardY),'phone loose pieces scatter above image');
 assert.ok(projected.some(p=>p.y>board.boardY+board.boardHeight),'phone loose pieces scatter below image');
 assert.ok(projected.every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)));
 const initialCanvases=canvases;
 let result=applyServerSnapshot(board,snapshot);
 assert.equal(result.revision,0);
 assert.equal(board.pieces.length,count);
 assert.equal(board.placedCount,0);
 assert.ok(canvases>=initialCanvases+count,'source-backed piece masks constructed');
 const afterFirstApply=canvases;
 result=applyServerSnapshot(board,snapshot);
 assert.equal(result.changed.length,0,'unchanged server snapshot does not repaint '+count);
 assert.equal(canvases,afterFirstApply,'unchanged server snapshot must not recut sprites '+count);
 const moved=structuredClone(snapshot);
 const item=moved.pieces.find(p=>p.id==='0-0');
 const screen=board.pieces.find(p=>p.id==='0-0');
 item.x=1.3;item.y=1.1;item.version=1;moved.revision=1;moved.moves=1;
 const pos=fromCellPosition(board,item.x,item.y);
 const back=toCellPosition(board,pos.x,pos.y);
 assert.ok(Math.abs(back.x-item.x)<1e-9&&Math.abs(back.y-item.y)<1e-9,'cell coordinates round-trip');
 result=applyServerSnapshot(board,moved);
 assert.ok(result.changed.includes(item.id));
 assert.equal(board.moveCount,1);
 assert.equal(canvases,afterFirstApply,'one drop moves cached sprites rather than re-rasterizing '+count);
 const landed=board.pieces.find(p=>p.id===item.id);
 assert.ok(Math.abs(landed.x-pos.x)<1e-6,'dropped position maps consistently on mobile');
 const op=makeDropCommand(board,item.id,item.version,'transportMutation0123');
 assert.equal(op.type,'drop');assert.equal(op.pieceId,'0-0');
 assert.equal(op.roundId,snapshot.roundId,'every operation is bound to the live server round');
 assert.ok(Math.abs(op.x-1.3)<1e-6&&Math.abs(op.y-1.1)<1e-6);
 // Server placement is authoritative; local client never decides a snap.
 const placed=structuredClone(moved);
 const done=placed.pieces.find(p=>p.id==='0-0');
 done.placed=true;done.x=0;done.y=0;done.version=2;
 placed.revision=2;
 applyServerSnapshot(board,placed);
 assert.equal(board.placedCount,1);
 assert.ok(board.pieces.find(p=>p.id==='0-0').isPlaced);
 const stale=applyServerSnapshot(board,moved);
 assert.equal(stale.ignored,true,'older room update must not roll back server-authored placement');
 assert.equal(board.placedCount,1);
 const otherRound=structuredClone(snapshot);
 otherRound.roundId='00000000-0000-4000-8000-000000000002';
 applyServerSnapshot(board,otherRound);
 assert.equal(board.placedCount,0,'new server round invalidates all prior pieces');
 assert.throws(()=>makeDropCommand(board,'0-0',0,'bad'),/Invalid piece drop metadata/);
 applyServerSnapshot(board,placed); // Reset the old test round only for assertions below.
 assert.throws(()=>makeDropCommand(board,'0-0',2,'transportMutation0124'),/not movable/);
 // Resizing must project canonical x/y again without sharing viewport ratios.
 wrapper.width=1280;wrapper.height=850;board.resize();
 const desktop=previewServerSnapshot(board,snapshot);
 assert.ok(desktop.some(p=>p.x<board.boardX),'desktop left-edge scatter exists');
 assert.ok(desktop.some(p=>p.x>board.boardX+board.boardWidth),'desktop right-edge scatter exists');
 const original=structuredClone(snapshot);
 const broken=structuredClone(snapshot);broken.pieces[0].edges.right*=-1;
 assert.throws(()=>validateSnapshot(broken),/edge pair|Outside/);
 assert.deepEqual(snapshot,original,'projection does not mutate remote snapshot');
 console.log('PASS '+count+' pieces: phone/desktop positions, network cells, sprite reuse, server snap and malformed-edge rejection');
}
console.log('PASS NIGHT Play Jigsaw co-op visual adapter contract (simulated backend, NOT live multiplayer).');
