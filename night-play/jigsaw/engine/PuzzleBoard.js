/**
 * NIGHT Play responsive adaptation of PlayJigsaw.net's MIT-licensed PuzzleBoard.
 * The engine retains the upstream PieceCutter, offscreen artwork masks, piece snapping,
 * canvas rendering and hints. See ../THIRD_PARTY_LICENSE.txt.
 */
import {PieceCutter} from './PieceCutter.js';

export class PuzzleBoard{
 constructor(canvas,wrapper){
  this.canvas=canvas;this.ctx=canvas.getContext('2d');this.wrapper=wrapper;
  this.dragCanvas=wrapper.querySelector('#dragCanvas');
  this.dragCtx=this.dragCanvas.getContext('2d',{alpha:true});
  this.staticPaintCount=0;this.dragPaintCount=0;
  this.rows=3;this.cols=4;this.totalPieces=12;this.pieces=[];this.selectedPiece=null;
  this.boardX=0;this.boardY=0;this.boardWidth=0;this.boardHeight=0;
  this.pieceWidth=0;this.pieceHeight=0;this.tray={x:0,y:0,w:0,h:0};
  this.showGhost=false;this.placedCount=0;this.moveCount=0;
  this.sourceCanvas=document.createElement('canvas');this.sourceCtx=this.sourceCanvas.getContext('2d');
  this.sourceCanvas.width=800;this.sourceCanvas.height=600;
 }
 resize(){
  const rect=this.wrapper.getBoundingClientRect(),w=Math.max(300,Math.round(rect.width)),h=Math.max(460,Math.round(rect.height));
  if(this.canvas.width===w&&this.canvas.height===h)return;
  const old={w:this.canvas.width,h:this.canvas.height,x:this.boardX,y:this.boardY,pw:this.pieceWidth,ph:this.pieceHeight,tray:{...this.tray}};
  this.canvas.width=w;this.canvas.height=h;
  this.dragCanvas.width=w;this.dragCanvas.height=h;
  this.setupBoardLayout();
  if(this.pieces.length){
   for(const p of this.pieces){
    if(p.isPlaced){p.x=this.boardX+p.col*this.pieceWidth;p.y=this.boardY+p.row*this.pieceHeight;}
    else if(old.w>0&&old.tray.w>0){
     const nx=(p.x-old.tray.x+old.pw*.5)/old.tray.w,ny=(p.y-old.tray.y+old.ph*.5)/old.tray.h;
     p.x=this.tray.x+nx*this.tray.w-this.pieceWidth*.5;
     p.y=this.tray.y+ny*this.tray.h-this.pieceHeight*.5;
    }
    p.correctX=this.boardX+p.col*this.pieceWidth;p.correctY=this.boardY+p.row*this.pieceHeight;
    PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,this.pieceWidth,this.pieceHeight,this.rows,this.cols);
   }
  }
 }
 setDimensions(rows,cols){this.rows=rows;this.cols=cols;this.totalPieces=rows*cols;}
 setupBoardLayout(){
  const cw=this.canvas.width,ch=this.canvas.height,mobile=cw<680;
  if(mobile){
   // Keep the actual picture at almost the complete available phone width.
   this.boardWidth=Math.min(cw-6,780);this.boardHeight=this.boardWidth*.75;
   this.boardX=(cw-this.boardWidth)/2;this.boardY=5;
   const looseY=this.boardY+this.boardHeight+22;
   // Loose pieces share the canvas with the picture rather than occupying a framed box.
   this.tray={x:5,y:looseY,w:cw-10,h:Math.max(80,ch-looseY-7)};
  }else{
   this.boardWidth=Math.min(cw*.64,740,(ch-15)*4/3);this.boardHeight=this.boardWidth*.75;
   this.boardX=7;this.boardY=(ch-this.boardHeight)/2;
   const tx=this.boardX+this.boardWidth+16;
   this.tray={x:tx,y:9,w:Math.max(100,cw-tx-9),h:ch-18};
  }
  this.pieceWidth=this.boardWidth/this.cols;this.pieceHeight=this.boardHeight/this.rows;
 }
 buildPieces(){
  this.resize();this.setupBoardLayout();this.placedCount=0;this.moveCount=0;this.selectedPiece=null;
  const {hEdges,vEdges}=PieceCutter.generateEdgeMatrix(this.rows,this.cols);
  const count=this.totalPieces,t=this.tray;
  const slotCols=Math.max(1,Math.min(this.cols,Math.floor(t.w/(this.pieceWidth*.97))));
  const slotRows=Math.ceil(count/slotCols);
  const slots=Array.from({length:count},(_,i)=>{
   const col=i%slotCols,row=Math.floor(i/slotCols),sw=t.w/slotCols,sh=t.h/slotRows;
   const stagger=(((i*7)%5)-2)*Math.min(5,Math.max(0,(sw-this.pieceWidth)/5));
   const drift=(((i*11)%7)-3)*Math.min(3,Math.max(0,(sh-this.pieceHeight)/8));
   return {x:Math.min(t.x+t.w-this.pieceWidth,Math.max(t.x,t.x+col*sw+(sw-this.pieceWidth)/2+stagger)),
    y:Math.min(t.y+t.h-this.pieceHeight,Math.max(t.y,t.y+row*sh+(sh-this.pieceHeight)/2+drift))};
  });
  for(let i=count-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[slots[i],slots[j]]=[slots[j],slots[i]];}
  this.pieces=[];
  for(let row=0;row<this.rows;row++)for(let col=0;col<this.cols;col++){
   const i=row*this.cols+col,p={
    id:row+'-'+col,row,col,x:slots[i].x,y:slots[i].y,
    correctX:this.boardX+col*this.pieceWidth,correctY:this.boardY+row*this.pieceHeight,
    edges:{top:row===0?0:-hEdges[row-1][col],right:col===this.cols-1?0:vEdges[row][col],
     bottom:row===this.rows-1?0:hEdges[row][col],left:col===0?0:-vEdges[row][col-1]},
    isPlaced:false
   };
   PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,this.pieceWidth,this.pieceHeight,this.rows,this.cols);
   this.pieces.push(p);
  }
 }
 getPieceAt(x,y){
  // Keep the selected piece on top while dragging; its large rectangular hit target
  // makes touch acquisition more forgiving than pixel-perfect contour detection.
  for(let i=this.pieces.length-1;i>=0;i--){const p=this.pieces[i];if(p.isPlaced)continue;
   const pad=Math.min(this.pieceWidth*.12,8);
   if(x>=p.x-pad&&x<=p.x+this.pieceWidth+pad&&y>=p.y-pad&&y<=p.y+this.pieceHeight+pad)return{piece:p,index:i};
  }
  return null;
 }
 bringToTop(index){const p=this.pieces.splice(index,1)[0];this.pieces.push(p);return p;}
 checkSnap(p){
  const threshold=Math.max(16,Math.min(32,this.pieceWidth*.27));
  if(Math.hypot(p.x-p.correctX,p.y-p.correctY)>threshold)return false;
  p.x=p.correctX;p.y=p.correctY;p.isPlaced=true;this.placedCount++;return true;
 }
 hintRandomPiece(){
  const loose=this.pieces.filter(p=>!p.isPlaced);if(!loose.length)return null;
  const p=loose[Math.floor(Math.random()*loose.length)];
  p.x=p.correctX;p.y=p.correctY;p.isPlaced=true;this.placedCount++;this.moveCount++;return p;
 }
 serialize(){
  return {rows:this.rows,cols:this.cols,moves:this.moveCount,ghost:this.showGhost,
   pieces:this.pieces.map(p=>({id:p.id,row:p.row,col:p.col,isPlaced:p.isPlaced,edges:p.edges,
    // Relative to the tray, not absolute px, so the save survives portrait rotation.
    nx:(p.x-this.tray.x+this.pieceWidth/2)/this.tray.w,
    ny:(p.y-this.tray.y+this.pieceHeight/2)/this.tray.h}))};
 }
 restore(saved){
  if(!saved||saved.rows!==this.rows||saved.cols!==this.cols||!Array.isArray(saved.pieces)||saved.pieces.length!==this.totalPieces)return false;
  const byId=new Map(saved.pieces.map(p=>[p.id,p]));
  if(byId.size!==this.totalPieces)return false;
  this.pieces=[];this.placedCount=0;this.moveCount=Math.max(0,Number(saved.moves)||0);this.showGhost=saved.ghost!==false;
  for(let row=0;row<this.rows;row++)for(let col=0;col<this.cols;col++){
   const data=byId.get(row+'-'+col);
   if(!data||!data.edges||!Object.values(data.edges).every(v=>[-1,0,1].includes(v)))return false;
   const p={...data,id:row+'-'+col,row,col,correctX:this.boardX+col*this.pieceWidth,
    correctY:this.boardY+row*this.pieceHeight,isPlaced:Boolean(data.isPlaced)};
   p.x=p.isPlaced?p.correctX:this.tray.x+(Math.max(-1,Math.min(2,Number(data.nx)||0)))*this.tray.w-this.pieceWidth/2;
   p.y=p.isPlaced?p.correctY:this.tray.y+(Math.max(-1,Math.min(2,Number(data.ny)||0)))*this.tray.h-this.pieceHeight/2;
   if(p.isPlaced)this.placedCount++;
   PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,this.pieceWidth,this.pieceHeight,this.rows,this.cols);
   this.pieces.push(p);
  }
  return true;
 }
 render(confetti=null){
  // Costly full-scene painting only happens after a completed move, settings
  // change or resize. Finger movement never calls this path.
  this.staticPaintCount++;
  const ctx=this.ctx,w=this.canvas.width,h=this.canvas.height;
  ctx.clearRect(0,0,w,h);
  ctx.fillStyle='#edf3ec';ctx.fillRect(0,0,w,h);
  // One-pixel tabletop frame — never a thick bezel consuming puzzle space.
  ctx.fillStyle='#f6f8f2';ctx.fillRect(this.boardX,this.boardY,this.boardWidth,this.boardHeight);
  ctx.strokeStyle='#96b29e';ctx.lineWidth=1;
  ctx.strokeRect(this.boardX+.5,this.boardY+.5,this.boardWidth-1,this.boardHeight-1);
  if(this.showGhost){
   // Optional, intentionally very faint guide. Even Relaxed mode is a puzzle.
   ctx.save();ctx.globalAlpha=.075;ctx.drawImage(this.sourceCanvas,this.boardX,this.boardY,this.boardWidth,this.boardHeight);ctx.restore();
  }
  if(w<680){
   ctx.fillStyle='#6c8977';ctx.textAlign='center';ctx.font='750 10px system-ui';
   ctx.fillText('LOOSE PIECES · DRAG TO THE PICTURE',w/2,this.tray.y-7);
  }
  for(const p of this.pieces)if(p.isPlaced){ctx.drawImage(p.offscreenCanvas,p.x-p.canvasMargin,p.y-p.canvasMargin);}
  for(const p of this.pieces)if(!p.isPlaced&&p!==this.selectedPiece){
   ctx.save();ctx.shadowColor='#183b295c';ctx.shadowBlur=4;ctx.shadowOffsetY=2;
   ctx.drawImage(p.offscreenCanvas,p.x-p.canvasMargin,p.y-p.canvasMargin);ctx.restore();
  }
  if(confetti)confetti.updateAndRender(ctx);
  this.renderDrag();
 }
 renderDrag(){
  // Clear only the transparent layer. The 24–144 other puzzle pieces and the
  // background remain cached in the canvas underneath, even at 120Hz touch.
  this.dragPaintCount++;
  const ctx=this.dragCtx;
  ctx.clearRect(0,0,this.dragCanvas.width,this.dragCanvas.height);
  const p=this.selectedPiece;
  if(!p)return;
  ctx.save();
  ctx.shadowColor='#0f452b55';ctx.shadowBlur=5;ctx.shadowOffsetY=2;
  ctx.drawImage(p.offscreenCanvas,p.x-p.canvasMargin,p.y-p.canvasMargin);
  ctx.restore();
 }
}
