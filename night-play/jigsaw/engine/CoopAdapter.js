/* NIGHT Play Jigsaw: browser-side adapter for A8's server-authoritative cell model.
 * This is NOT a live networking implementation. The caller must obtain verified
 * snapshots and actor-scoped leases from the backend; browser state is never the
 * source of truth for remote pieces, scores, snapping or game completion. */
import {PieceCutter} from './PieceCutter.js';

const SHAPES = Object.freeze({24:[4,6],48:[6,8],144:[12,12]});
const finite = n => typeof n === 'number' && Number.isFinite(n);
const own = (o,k) => Object.prototype.hasOwnProperty.call(o,k);
const dirs = ['top','right','bottom','left'];
const validEdges = value => value && dirs.every(k => own(value,k) && [-1,0,1].includes(value[k]));
const sameEdges = (a,b) => Boolean(a && b && dirs.every(k => a[k] === b[k]));
const clamp = (n,a,b) => Math.max(a,Math.min(b,n));
function hash(text) {
 let h=2166136261;
 for (const c of text) {h^=c.charCodeAt(0);h=Math.imul(h,16777619);}
 return h>>>0;
}
function geometry(board) {
 if(!board || !finite(board.boardX) || !finite(board.boardY) ||
   !finite(board.pieceWidth) || !finite(board.pieceHeight) ||
   board.pieceWidth<=0 || board.pieceHeight<=0) throw Error('Board geometry not initialized.');
 return board;
}
export function toCellPosition(board, x, y) {
 geometry(board);
 if(!finite(x)||!finite(y))throw Error('Non-finite piece position.');
 return {x:(x-board.boardX)/board.pieceWidth,y:(y-board.boardY)/board.pieceHeight};
}
export function fromCellPosition(board, x, y) {
 geometry(board);
 if(!finite(x)||!finite(y))throw Error('Non-finite server position.');
 return {x:board.boardX+x*board.pieceWidth,y:board.boardY+y*board.pieceHeight};
}
export function makeDropCommand(board, pieceId, expectedVersion, mutationId) {
 if(typeof pieceId !== 'string' || !/^[0-9]+-[0-9]+$/.test(pieceId) ||
    !Number.isSafeInteger(expectedVersion) || expectedVersion<0 ||
    typeof mutationId!=='string' || !/^[A-Za-z0-9_-]{8,96}$/.test(mutationId))
  throw Error('Invalid piece drop metadata.');
 const piece=board?.pieces?.find(p=>p.id===pieceId);
 if(!piece || piece.isPlaced)throw Error('Piece is not movable.');
 const {x,y}=toCellPosition(board,piece.x,piece.y);
 // Positions outside the board are valid scatter. Never silently clamp to a
 // board edge: that would snap distant pieces when released on a narrow phone.
 return {type:'drop',pieceId,expectedVersion,x,y,mutationId};
}
export function validateSnapshot(snapshot) {
 const pair=SHAPES[snapshot?.count];
 if(!pair || !Array.isArray(snapshot.pieces) ||
   pair[0]!==snapshot.rows || pair[1]!==snapshot.cols ||
   snapshot.pieces.length!==snapshot.count ||
   !Number.isSafeInteger(snapshot.revision) || snapshot.revision<0 ||
   !Number.isSafeInteger(snapshot.moves) || snapshot.moves<0)
   throw Error('Invalid cooperative puzzle snapshot.');
 const ids=new Map();
 for(const p of snapshot.pieces){
  if(!p || !Number.isInteger(p.row) || !Number.isInteger(p.col) ||
    p.row<0 || p.row>=snapshot.rows || p.col<0 || p.col>=snapshot.cols ||
    p.id!==p.row+'-'+p.col || ids.has(p.id) ||
    typeof p.group!=='string' || !finite(p.x) || !finite(p.y) ||
    !Number.isSafeInteger(p.version) || p.version<0 ||
    typeof p.placed!=='boolean' || !validEdges(p.edges))
    throw Error('Invalid cooperative piece.');
  if(p.placed && (Math.abs(p.x-p.col)>1e-6 || Math.abs(p.y-p.row)>1e-6))
    throw Error('Anchored piece has noncanonical coordinates.');
  ids.set(p.id,p);
 }
 if(ids.size!==snapshot.count)throw Error('Puzzle has missing piece IDs.');
 for(const p of ids.values()){
  if(!ids.has(p.group))throw Error('Invalid group anchor.');
  if(p.row===0 && p.edges.top!==0 || p.col===0 && p.edges.left!==0 ||
     p.row===snapshot.rows-1 && p.edges.bottom!==0 ||
     p.col===snapshot.cols-1 && p.edges.right!==0)
    throw Error('Outside edge must be flat.');
  const right=ids.get(p.row+'-'+(p.col+1));
  const down=ids.get((p.row+1)+'-'+p.col);
  if(right && p.edges.right!==-right.edges.left ||
     down && p.edges.bottom!==-down.edges.top)
    throw Error('Contradictory server edge pair.');
 }
 return snapshot;
}
function projectedPositions(board,snapshot) {
 geometry(board);validateSnapshot(snapshot);
 const buckets=new Map(),projected=new Map();
 for(const p of snapshot.pieces) {
  if(!buckets.has(p.group))buckets.set(p.group,[]);
  buckets.get(p.group).push(p);
 }
 const mobile=board.viewportWidth<680;
 for(const [groupId,items] of buckets){
  const anchor=items.find(p=>p.id===groupId) || items[0];
  let target=fromCellPosition(board,anchor.x,anchor.y);
  // The server is desktop-agnostic and initially scatters to left/right.
  // On narrow phones those regions are offscreen. Re-map only server-side
  // left/right *unplaced* loose groups to the visible top/bottom tabletop.
  if(mobile && !items.some(p=>p.placed) &&
      (anchor.x < -.5 || anchor.x > snapshot.cols+.25)){
   const bottom=anchor.x>snapshot.cols+.25;
   const zones=board.scatterZones || [];
   const z=zones[bottom?1:0];
   if(z && finite(z.x) && finite(z.y) && finite(z.w) && finite(z.h)){
    const w=(Math.max(...items.map(p=>p.col))-Math.min(...items.map(p=>p.col))+1)*board.pieceWidth;
    const h=(Math.max(...items.map(p=>p.row))-Math.min(...items.map(p=>p.row))+1)*board.pieceHeight;
    const salt=hash(groupId)*1/4294967296;
    const fraction=((anchor.y/snapshot.rows)+salt*.21)%1;
    const t=fraction<0?fraction+1:fraction;
    target={
     x:clamp(z.x + t*Math.max(0,z.w-w),0,Math.max(0,board.viewportWidth-w)),
     y:clamp(z.y + (salt*.83)*Math.max(0,z.h-h),0,Math.max(0,board.viewportHeight-h))
    };
    // Include a group-relative cell offset if the group anchor is not its
    // leftmost/topmost member (avoids dragging a large group into the gutter).
    const minRow=Math.min(...items.map(p=>p.row)),minCol=Math.min(...items.map(p=>p.col));
    target.x+=(anchor.col-minCol)*board.pieceWidth;
    target.y+=(anchor.row-minRow)*board.pieceHeight;
   }
  }
  for(const p of items){
   const correct=fromCellPosition(board,p.col,p.row);
   projected.set(p.id,{
    ...p,edges:{...p.edges},
    x:p.placed?correct.x:target.x+(p.x-anchor.x)*board.pieceWidth,
    y:p.placed?correct.y:target.y+(p.y-anchor.y)*board.pieceHeight,
    correctX:correct.x,correctY:correct.y,isPlaced:p.placed
   });
  }
 }
 return snapshot.pieces.map(p=>projected.get(p.id));
}
export function previewServerSnapshot(board,snapshot) {
 return projectedPositions(board,snapshot);
}
export function applyServerSnapshot(board,snapshot,{render=true,preserveDragging=true}={}) {
 validateSnapshot(snapshot);
 if(!board || typeof board.resize!=='function' || typeof board.setupBoardLayout!=='function' ||
    typeof board.damageForPieces!=='function' || typeof board.render!=='function')
    throw Error('Expected a PuzzleBoard instance.');
 const dimensionChanged=board.rows!==snapshot.rows || board.cols!==snapshot.cols;
 if(dimensionChanged){
  board.pieces=[];board.selectedPieces=[];board.selectedPiece=null;
  board.setDimensions(snapshot.rows,snapshot.cols);
 }
 board.resize();board.setupBoardLayout();
 const display=projectedPositions(board,snapshot);
 const previous=new Map(board.pieces.map(p=>[p.id,p]));
 const dragging=preserveDragging?new Set((board.selectedPieces||[]).map(p=>p.id)):new Set();
 const changed=[],damaged=[];
 const next=display.map(p=>{
  const prior=previous.get(p.id);
  const faceChanged=!prior?.offscreenCanvas || !sameEdges(prior.edges,p.edges);
  if(prior) {
   const moved=Math.abs(prior.x-p.x)>.02 || Math.abs(prior.y-p.y)>.02 ||
    prior.isPlaced!==p.isPlaced || prior.group!==p.group;
   if(moved && !dragging.has(p.id))damaged.push(...board.damageForPieces([prior]));
   if(faceChanged || moved)changed.push(p.id);
  } else changed.push(p.id);
  if(prior && dragging.has(p.id)) {
   p.x=prior.x;p.y=prior.y;
  }
  const out=prior||p;
  Object.assign(out,p);
  if(prior && dragging.has(p.id)){out.x=p.x;out.y=p.y;}
  if(faceChanged){
   PieceCutter.renderPieceToCanvas(out,board.sourceCanvas,board.pieceWidth,board.pieceHeight,
     board.rows,board.cols,board.dpr);
  }
  return out;
 });
 board.pieces=next;
 board.placedCount=snapshot.pieces.filter(p=>p.placed).length;
 board.moveCount=snapshot.moves;
 board.totalPieces=snapshot.count;
 board.lastCoopRevision=snapshot.revision;
 if(render) {
  if(dimensionChanged || !previous.size || changed.length>Math.max(24,snapshot.count/3))
   board.render();
  else if(changed.length)board.renderDamage(damaged.concat(board.damageForPieces(next.filter(p=>changed.includes(p.id)))));
 }
 return {revision:snapshot.revision,changed,completed:snapshot.finishedAt!==null};
}
