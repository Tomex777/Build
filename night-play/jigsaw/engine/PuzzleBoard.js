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
  this.showGhost=false;this.edgeOnly=false;this.placedCount=0;this.moveCount=0;
  this.selectedPieces=[];this.scatterZones=[];this.lastJoinCount=0;
  this.sourceCanvas=document.createElement('canvas');this.sourceCtx=this.sourceCanvas.getContext('2d');
  this.sourceCanvas.width=800;this.sourceCanvas.height=600;
 }
 resize(){
  const rect=this.wrapper.getBoundingClientRect();
  const w=Math.max(280,Math.round(rect.width)),h=Math.max(260,Math.round(rect.height));
  if(this.canvas.width===w&&this.canvas.height===h)return;
  const old={w:this.canvas.width,h:this.canvas.height,pw:this.pieceWidth,ph:this.pieceHeight};
  const anchors=new Map();
  for(const p of this.pieces)if(!p.isPlaced&&!anchors.has(p.group||p.id))
   anchors.set(p.group||p.id,{row:p.row,col:p.col,x:p.x,y:p.y});
  this.canvas.width=w;this.canvas.height=h;this.dragCanvas.width=w;this.dragCanvas.height=h;
  this.setupBoardLayout();
  if(this.pieces.length){
   for(const p of this.pieces){
    if(p.isPlaced){p.x=this.boardX+p.col*this.pieceWidth;p.y=this.boardY+p.row*this.pieceHeight;}
    else{
     const a=anchors.get(p.group||p.id);
     p.x=Math.max(-this.pieceWidth*.15,Math.min(w-this.pieceWidth*.85,
       (a?.x||0)/Math.max(1,old.w)*w+(p.col-a.col)*this.pieceWidth));
     p.y=Math.max(-this.pieceHeight*.15,Math.min(h-this.pieceHeight*.85,
       (a?.y||0)/Math.max(1,old.h)*h+(p.row-a.row)*this.pieceHeight));
    }
    p.correctX=this.boardX+p.col*this.pieceWidth;p.correctY=this.boardY+p.row*this.pieceHeight;
    PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,this.pieceWidth,this.pieceHeight,this.rows,this.cols);
   }
   if((old.w<680)!==(w<680))this.sweepLoose();
  }
 }
 setDimensions(rows,cols){this.rows=rows;this.cols=cols;this.totalPieces=rows*cols;}
 setupBoardLayout(){
  const cw=this.canvas.width,ch=this.canvas.height,mobile=cw<680;
  this.boardWidth=mobile?Math.min(cw-20,780):Math.min(cw*.68,935,(ch-24)*4/3);
  this.boardHeight=this.boardWidth*.75;
  this.boardX=(cw-this.boardWidth)/2;
  this.boardY=(ch-this.boardHeight)/2;
  const gap=12;
  if(mobile){
   this.scatterZones=[
    {x:4,y:5,w:cw-8,h:Math.max(20,this.boardY-gap-5)},
    {x:4,y:this.boardY+this.boardHeight+gap,w:cw-8,h:Math.max(20,ch-this.boardY-this.boardHeight-gap-6)}
   ];
  }else{
   this.scatterZones=[
    {x:5,y:5,w:Math.max(15,this.boardX-gap-5),h:ch-10},
    {x:this.boardX+this.boardWidth+gap,y:5,w:Math.max(15,cw-this.boardX-this.boardWidth-gap-5),h:ch-10}
   ];
  }
  // Store free-piece positions as ratios of the whole tabletop, independent of
  // its arrangement; saves survive portrait rotation and zoom adjustments.
  this.tray={x:0,y:0,w:cw,h:ch};
  this.pieceWidth=this.boardWidth/this.cols;this.pieceHeight=this.boardHeight/this.rows;
 }
 buildPieces(){
  this.resize();this.setupBoardLayout();
  this.placedCount=0;this.moveCount=0;this.selectedPiece=null;this.selectedPieces=[];
  const {hEdges,vEdges}=PieceCutter.generateEdgeMatrix(this.rows,this.cols);
  const zones=this.scatterZones,pw=this.pieceWidth,ph=this.pieceHeight;
  // Best-candidate scattering: no visible rows, no extra piece-tray box.
  // Distribute across top/bottom on phones, left/right on wider screens.
  const occupied=[[],[]],slots=[];
  const count=this.totalPieces;
  for(let i=0;i<count;i++){
   const zoneIndex=i%zones.length,z=zones[zoneIndex],previous=occupied[zoneIndex];
   const rangeX=Math.max(0,z.w-pw),rangeY=Math.max(0,z.h-ph);
   let best=null,bestScore=-Infinity;
   for(let trial=0;trial<24;trial++){
    const x=z.x+Math.random()*rangeX,y=z.y+Math.random()*rangeY;
    let score=previous.length?Infinity:10;
    for(const old of previous){
     const dist=Math.hypot((x-old.x)/(pw*.84||1),(y-old.y)/(ph*.84||1));
     if(dist<score)score=dist;
    }
    score+=Math.random()*.085;
    if(score>bestScore){best={x,y};bestScore=score;}
   }
   occupied[zoneIndex].push(best);slots.push(best);
  }
  // Shuffle which source image goes into each distributed location.
  for(let i=slots.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[slots[i],slots[j]]=[slots[j],slots[i]];}
  this.pieces=[];
  for(let row=0;row<this.rows;row++)for(let col=0;col<this.cols;col++){
   const i=row*this.cols+col,position=slots[i],p={
    id:row+'-'+col,row,col,x:position.x,y:position.y,group:row+'-'+col,
    correctX:this.boardX+col*pw,correctY:this.boardY+row*ph,
    edges:{top:row===0?0:-hEdges[row-1][col],right:col===this.cols-1?0:vEdges[row][col],
     bottom:row===this.rows-1?0:hEdges[row][col],left:col===0?0:-vEdges[row][col-1]},
    isPlaced:false
   };
   PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,pw,ph,this.rows,this.cols);
   this.pieces.push(p);
  }
 }
 // Return all unsolved pieces to whichever free edges fit the current
 // viewport; group geometry is kept intact so completed subassemblies survive.
 sweepLoose(){
  const groups=new Map();
  for(const p of this.pieces)if(!p.isPlaced){
   const key=p.group||p.id;if(!groups.has(key))groups.set(key,[]);
   groups.get(key).push(p);
  }
  const all=[...groups.values()].sort((a,b)=>b.length-a.length);
  const filled=[[],[]],zones=this.scatterZones;
  for(let i=0;i<all.length;i++){
   const group=all[i],zid=i%zones.length,z=zones[zid];
   const minRow=Math.min(...group.map(p=>p.row)),minCol=Math.min(...group.map(p=>p.col));
   const maxRow=Math.max(...group.map(p=>p.row)),maxCol=Math.max(...group.map(p=>p.col));
   const gw=(maxCol-minCol+1)*this.pieceWidth,gh=(maxRow-minRow+1)*this.pieceHeight;
   const xRange=Math.max(0,z.w-gw),yRange=Math.max(0,z.h-gh);
   let best=null,bestDistance=-1;
   for(let sample=0;sample<24;sample++){
    let x=z.x+Math.random()*xRange,y=z.y+Math.random()*yRange;
    if(gw>z.w)x=z.x-(gw-z.w)*.5;
    if(gh>z.h)y=z.y-(gh-z.h)*.5;
    x=Math.max(0,Math.min(this.canvas.width-gw,x));
    y=Math.max(0,Math.min(this.canvas.height-gh,y));
    let score=filled[zid].length?Infinity:10;
    for(const rect of filled[zid]){
     const nx=(x+gw/2-rect.x-rect.w/2)/Math.max(this.pieceWidth,Math.min(gw,rect.w));
     const ny=(y+gh/2-rect.y-rect.h/2)/Math.max(this.pieceHeight,Math.min(gh,rect.h));
     score=Math.min(score,Math.hypot(nx,ny));
    }
    if(score>bestDistance){bestDistance=score;best={x,y};}
   }
   filled[zid].push({...best,w:gw,h:gh});
   for(const p of group){p.x=best.x+(p.col-minCol)*this.pieceWidth;p.y=best.y+(p.row-minRow)*this.pieceHeight;}
  }
  this.render();
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
 bringToTop(index){
  const p=this.pieces[index],id=p.group||p.id;
  const selected=this.pieces.filter(item=>(item.group||item.id)===id);
  this.pieces=this.pieces.filter(item=>(item.group||item.id)!==id).concat(selected);
  this.selectedPieces=selected;
  return p;
 }
 moveSelectionTo(x,y){
  const p=this.selectedPiece;if(!p)return;
  const dx=x-p.x,dy=y-p.y;
  for(const item of this.selectedPieces){item.x+=dx;item.y+=dy;}
 }
 groupPieces(id){return this.pieces.filter(p=>(p.group||p.id)===id&&!p.isPlaced);}
 anchorGroup(id){
  const group=this.groupPieces(id);
  for(const p of group){p.x=p.correctX;p.y=p.correctY;p.isPlaced=true;this.placedCount++;}
  return group.length;
 }
 checkSnap(p){
  this.lastJoinCount=0;if(!p||p.isPlaced)return false;
  const threshold=Math.max(11,Math.min(27,Math.min(this.pieceWidth,this.pieceHeight)*.23));
  let selected=this.groupPieces(p.group||p.id);
  if(!selected.length)return false;
  const positionFits=m=>Math.hypot(m.x-m.correctX,m.y-m.correctY)<threshold;
  if(selected.some(positionFits)){this.anchorGroup(p.group||p.id);return true;}
  const byId=new Map(this.pieces.map(x=>[x.id,x]));
  // Join geometrically correct neighbours even if both pieces are outside the
  // finished board. Connected sections can then be dragged as one.
  let change=true,loops=0;
  while(change&&loops++<this.totalPieces){
   change=false;
   const ids=new Set(selected.map(x=>x.id));
   for(const member of selected){
    const options=[[member.row-1,member.col],[member.row+1,member.col],[member.row,member.col-1],[member.row,member.col+1]];
    for(const [r,c] of options){
     const neighbour=byId.get(r+'-'+c);
     if(!neighbour||ids.has(neighbour.id))continue;
     const expectX=member.x+(c-member.col)*this.pieceWidth;
     const expectY=member.y+(r-member.row)*this.pieceHeight;
     const dx=neighbour.x-expectX,dy=neighbour.y-expectY;
     if(Math.hypot(dx,dy)>threshold)continue;
     const other=neighbour.isPlaced?[]:this.groupPieces(neighbour.group||neighbour.id);
     if(neighbour.isPlaced){
      // The free connected section has reached an already anchored section.
      this.anchorGroup(p.group||p.id);return true;
     }
     if(!other.length)continue;
     for(const item of selected){item.x+=dx;item.y+=dy;}
     const groupId=p.group||p.id;
     for(const item of other){item.group=groupId;ids.add(item.id);}
     selected=this.groupPieces(groupId);
     this.lastJoinCount++;change=true;break;
    }
    if(change)break;
   }
  }
  if(selected.some(positionFits)){this.anchorGroup(p.group||p.id);return true;}
  return false;
 }
 hintRandomPiece(){
  const loose=this.pieces.filter(p=>!p.isPlaced);if(!loose.length)return null;
  const p=loose[Math.floor(Math.random()*loose.length)];
  this.anchorGroup(p.group||p.id);this.moveCount++;return p;
 }
 serialize(){
  return {layoutVersion:3,rows:this.rows,cols:this.cols,moves:this.moveCount,ghost:this.showGhost,
   edgeOnly:this.edgeOnly,
   pieces:this.pieces.map(p=>({id:p.id,row:p.row,col:p.col,group:p.group||p.id,isPlaced:p.isPlaced,edges:p.edges,
    nx:(p.x+this.pieceWidth/2)/this.canvas.width,
    ny:(p.y+this.pieceHeight/2)/this.canvas.height}))};
 }
 restore(saved){
  if(!saved||saved.rows!==this.rows||saved.cols!==this.cols||!Array.isArray(saved.pieces)||saved.pieces.length!==this.totalPieces)return false;
  const byId=new Map(saved.pieces.map(p=>[p.id,p]));
  if(byId.size!==this.totalPieces)return false;
  this.pieces=[];this.selectedPieces=[];this.placedCount=0;
  this.moveCount=Math.max(0,Number(saved.moves)||0);
  this.showGhost=saved.ghost===true;this.edgeOnly=saved.edgeOnly===true;
  for(let row=0;row<this.rows;row++)for(let col=0;col<this.cols;col++){
   const data=byId.get(row+'-'+col);
   if(!data||!data.edges||!Object.values(data.edges).every(v=>[-1,0,1].includes(v)))return false;
   const p={...data,id:row+'-'+col,row,col,group:data.group||row+'-'+col,
    correctX:this.boardX+col*this.pieceWidth,correctY:this.boardY+row*this.pieceHeight,isPlaced:Boolean(data.isPlaced)};
   if(p.isPlaced){p.x=p.correctX;p.y=p.correctY;this.placedCount++;}
   else if(saved.layoutVersion===3){
    p.x=Math.max(0,Math.min(this.canvas.width-this.pieceWidth,(Number(data.nx)||.5)*this.canvas.width-this.pieceWidth/2));
    p.y=Math.max(0,Math.min(this.canvas.height-this.pieceHeight,(Number(data.ny)||.5)*this.canvas.height-this.pieceHeight/2));
   }else{
    // Old saves stored free-piece positions relative to the previous bottom/right tray.
    // Respect placed pieces and migrate free positions into the corresponding region.
    const zone=this.scatterZones[this.canvas.width<680?1:1];
    p.x=zone.x+Math.max(0,Math.min(1,Number(data.nx)||.5))*Math.max(0,zone.w-this.pieceWidth);
    p.y=zone.y+Math.max(0,Math.min(1,Number(data.ny)||.5))*Math.max(0,zone.h-this.pieceHeight);
   }
   PieceCutter.renderPieceToCanvas(p,this.sourceCanvas,this.pieceWidth,this.pieceHeight,this.rows,this.cols);
   this.pieces.push(p);
  }
  // Reject malformed group identities rather than joining unrelated pieces.
  for(const p of this.pieces)if(!this.pieces.some(x=>x.id===p.group))p.group=p.id;
  return true;
 }
 render(confetti=null){
  // Costly full-scene painting only happens after a completed move, settings
  // change or resize. Finger movement never calls this path.
  this.staticPaintCount++;
  const ctx=this.ctx,w=this.canvas.width,h=this.canvas.height;
  ctx.clearRect(0,0,w,h);
  const matte=ctx.createLinearGradient(0,0,w,h);
  matte.addColorStop(0,'#e5dcd0');matte.addColorStop(.55,'#ddd3c6');matte.addColorStop(1,'#d8cfc2');
  ctx.fillStyle=matte;ctx.fillRect(0,0,w,h);
  // One-pixel tabletop frame — never a thick bezel consuming puzzle space.
  ctx.save();ctx.shadowColor='#483d3140';ctx.shadowBlur=8;ctx.shadowOffsetY=3;
  ctx.fillStyle='#837b6f';ctx.fillRect(this.boardX,this.boardY,this.boardWidth,this.boardHeight);ctx.restore();
  ctx.strokeStyle='#71695e';ctx.lineWidth=1;
  ctx.strokeRect(this.boardX+.5,this.boardY+.5,this.boardWidth-1,this.boardHeight-1);
  if(this.showGhost){
   // Optional, intentionally very faint guide. Even Relaxed mode is a puzzle.
   ctx.save();ctx.globalAlpha=.17;ctx.drawImage(this.sourceCanvas,this.boardX,this.boardY,this.boardWidth,this.boardHeight);ctx.restore();
  }

  for(const p of this.pieces)if(p.isPlaced){ctx.drawImage(p.offscreenCanvas,p.x-p.canvasMargin,p.y-p.canvasMargin);}
  for(const p of this.pieces)if(!p.isPlaced&&!this.selectedPieces.includes(p)){
   ctx.save();
   if(this.edgeOnly&&p.row!==0&&p.col!==0&&p.row!==this.rows-1&&p.col!==this.cols-1)ctx.globalAlpha=.18;
   ctx.shadowColor='#34271c66';ctx.shadowBlur=7;ctx.shadowOffsetY=3;
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
  if(!this.selectedPiece)return;
  for(const p of this.selectedPieces){
   ctx.save();ctx.shadowColor='#332a205e';ctx.shadowBlur=8;ctx.shadowOffsetY=3;
   ctx.drawImage(p.offscreenCanvas,p.x-p.canvasMargin,p.y-p.canvasMargin);
   ctx.restore();
  }
 }
}
