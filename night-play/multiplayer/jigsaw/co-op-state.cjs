/* NIGHT Play cooperative Jigsaw: pure server-side room reducer.
 * NOT a browser-synchronized demo. A trusted MSCC RPC adapter must authenticate
 * command.actor from its HttpOnly session and persist revisions atomically.
 * Coordinates are canonical puzzle CELL units, never viewport pixels.
 */
'use strict';
const {randomUUID}=require('node:crypto');
const SHAPES=Object.freeze({24:[4,6],48:[6,8],144:[12,12]});
const LEASE_MS=12000, MAX_PLAYERS=16, MAX_LOG=96;
const has=(object,key)=>Object.prototype.hasOwnProperty.call(object,key);
const validId=x=>typeof x==='string'&&/^[a-zA-Z0-9_-]{8,96}$/.test(x);
const display=x=>String(x||'').trim().replace(/\s+/g,' ').slice(0,28);
const finite=x=>typeof x==='number'&&Number.isFinite(x);
function random(seed){
 let a=seed>>>0;
 return ()=>{a=(a+0x6d2b79f5)>>>0;let t=a;t=Math.imul(t^(t>>>15),t|1);t^=t+Math.imul(t^(t>>>7),t|61);return ((t^(t>>>14))>>>0)/4294967296;};
}
function createPuzzle({photoId,count=24,hostId,hostName,seed=1,roundId=randomUUID()},now=Date.now()){
 if(typeof roundId!=='string'||!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(roundId)||
    !Number.isInteger(count)||!has(SHAPES,count)||!validId(hostId)||!display(hostName)||
    typeof photoId!=='string'||!/^photo-[a-z0-9-]{1,40}$/.test(photoId)||
    !Number.isSafeInteger(seed)||seed<0||seed>0xffffffff)throw Error('Invalid puzzle configuration.');
 const [rows,cols]=SHAPES[count],rnd=random(seed),edgeRnd=random(seed^0x9e3779b9),pieces=[];
 // Server-owned, reciprocal jigsaw edges are identical on every device.
 const hEdges=Array.from({length:rows-1},()=>Array.from({length:cols},()=>edgeRnd()<.5?1:-1));
 const vEdges=Array.from({length:rows},()=>Array.from({length:cols-1},()=>edgeRnd()<.5?1:-1));
 for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){
  const id=r+'-'+c,side=(r*cols+c)%2===0;
  // Loose cardboard around the board: no extra bottom tray.
  const x=side?-2.1+rnd()*1.65:cols+.45+rnd()*1.65;
  const y=-.4+rnd()*(rows-.2);
  pieces.push({id,row:r,col:c,group:id,x,y,placed:false,version:0,edges:{
   top:r===0?0:-hEdges[r-1][c],right:c===cols-1?0:vEdges[r][c],
   bottom:r===rows-1?0:hEdges[r][c],left:c===0?0:-vEdges[r][c-1]
  }});
 }
 return {format:1,roundId,photoId,count,rows,cols,seed,revision:0,moves:0,
  startedAt:now,updatedAt:now,finishedAt:null,hostId,
  players:[{id:hostId,name:display(hostName),joinedAt:now,lastSeen:now}],
  pieces,leases:[],opLog:[]};
}
function member(state,actor){return state.players.some(p=>p.id===actor);}
function groupMembers(state,piece){return state.pieces.filter(p=>p.group===piece.group);}
function error(state,message){return {accepted:false,state,error:message};}
function liveLease(state,group,now){return state.leases.find(l=>l.group===group&&l.expiresAt>now);}
function publicSnapshot(state,viewer,now=Date.now()){
 return {format:state.format,roundId:state.roundId,photoId:state.photoId,count:state.count,
  rows:state.rows,cols:state.cols,seed:state.seed,revision:state.revision,
  moves:state.moves,finishedAt:state.finishedAt,
  players:state.players.map(p=>({name:p.name,host:p.id===state.hostId,
   online:now-p.lastSeen<45000})),
  pieces:state.pieces.map(p=>({id:p.id,row:p.row,col:p.col,group:p.group,
   x:p.x,y:p.y,placed:p.placed,version:p.version,edges:{...p.edges},
   locked:Boolean(liveLease(state,p.group,now)),
   heldByMe:liveLease(state,p.group,now)?.actor===viewer}))};
}
// Commands must execute in a database transaction with compare-and-swap
// on 'revision'; serial execution here is NOT a deployment guarantee.
function applyCommand(current,command,now=Date.now()){
 if(!current||current.format!==1||!command||!validId(command.actor))return error(current,'Invalid command.');
 if(!Number.isSafeInteger(now)||now<0)return error(current,'Invalid timestamp.');
 if(command.roundId!==current.roundId)return error(current,'This move belongs to an older puzzle. Refresh the room.');
 if(command.type==='join'){
  if(member(current,command.actor))return {accepted:true,state:current,duplicate:true};
  if(current.players.length>=MAX_PLAYERS)return error(current,'Room is full.');
  if(!display(command.name))return error(current,'Enter your display name.');
  const next=structuredClone(current);
  next.players.push({id:command.actor,name:display(command.name),joinedAt:now,lastSeen:now});
  next.revision++;next.updatedAt=now;
  return {accepted:true,state:next};
 }
 if(!member(current,command.actor))return error(current,'Join this room first.');
 if(command.mutationId!==undefined && (!validId(command.mutationId)))return error(current,'Invalid operation ID.');
 if(command.mutationId && current.opLog.some(x=>x.actor===command.actor&&x.id===command.mutationId))
  return {accepted:true,state:current,duplicate:true};
 const next=structuredClone(current), actor=command.actor;
 const player=next.players.find(p=>p.id===actor), type=command.type;
 const finish=(changed=[])=>{
  if(command.mutationId){
   next.opLog.push({actor,id:command.mutationId});
   if(next.opLog.length>MAX_LOG)next.opLog.splice(0,next.opLog.length-MAX_LOG);
  }
  next.revision++;next.updatedAt=now;
  if(player)player.lastSeen=now;
  return {accepted:true,state:next,changed};
 };
 if(type==='leave'){
  if(actor===next.hostId)return error(current,'Host cannot leave an active room.');
  next.players=next.players.filter(p=>p.id!==actor);
  next.leases=next.leases.filter(l=>l.actor!==actor);
  return finish();
 }
 if(type==='rename'){
  if(!display(command.name))return error(current,'Enter your display name.');
  player.name=display(command.name);return finish();
 }
 if(type==='heartbeat'){
  // A heartbeat only refreshes presence, never alters a piece.
  return finish();
 }
 if(next.finishedAt!==null)return error(current,'This puzzle is complete.');
 const piece=next.pieces.find(p=>p.id===command.pieceId);
 if(!piece||piece.placed)return error(current,'Piece is not movable.');
 const members=groupMembers(next,piece),group=piece.group;
 next.leases=next.leases.filter(l=>l.expiresAt>now);
 const lease=liveLease(next,group,now);
 if(type==='claim'){
  if(!Number.isSafeInteger(command.expectedVersion)||piece.version!==command.expectedVersion)
   return error(current,'Piece changed. Refresh the board.');
  if(lease&&lease.actor!==actor)return error(current,'Another player is moving this piece.');
  if(lease)lease.expiresAt=now+LEASE_MS;
  else next.leases.push({group,actor,expiresAt:now+LEASE_MS});
  return finish();
 }
 if(!lease||lease.actor!==actor)return error(current,'Claim the piece before moving it.');
 if(type==='release'){
  next.leases=next.leases.filter(l=>l!==lease);return finish();
 }
 if(type==='renew'){
  lease.expiresAt=now+LEASE_MS;return finish();
 }
 if(type!=='drop')return error(current,'Unknown operation.');
 if(piece.version!==command.expectedVersion||!Number.isSafeInteger(command.expectedVersion))
  return error(current,'Piece changed. Refresh the board.');
 const {x,y}=command;
 if(!finite(x)||!finite(y)||Math.abs(x)>next.cols+4||Math.abs(y)>next.rows+4)
  return error(current,'Invalid piece position.');
 const dx=x-piece.x,dy=y-piece.y;
 for(const p of members){p.x+=dx;p.y+=dy;}
 const close=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y)<.23;
 const anchor=(set)=>{
  for(const p of set){p.x=p.col;p.y=p.row;p.placed=true;}
 };
 let moving=groupMembers(next,piece),changed=new Set(moving.map(p=>p.id));
 const fitsBoard=()=>moving.some(p=>close(p,{x:p.col,y:p.row}));
 if(fitsBoard())anchor(moving);
 else{
  let again=true,iterations=0;
  while(again&&iterations++<next.count){
   again=false;
   const ids=new Set(moving.map(p=>p.id));
   for(const p of moving){
    const neighbours=[[p.row-1,p.col],[p.row+1,p.col],[p.row,p.col-1],[p.row,p.col+1]];
    for(const [r,c] of neighbours){
     const neighbour=next.pieces.find(q=>q.id===r+'-'+c);
     if(!neighbour||ids.has(neighbour.id))continue;
     const target={x:p.x+(c-p.col),y:p.y+(r-p.row)};
     if(!close(neighbour,target))continue;
     if(neighbour.placed){anchor(moving);again=false;break;}
     if(liveLease(next,neighbour.group,now))continue; // never hijack an active drag
     const other=groupMembers(next,neighbour),adjustX=neighbour.x-target.x,adjustY=neighbour.y-target.y;
     for(const m of moving){m.x+=adjustX;m.y+=adjustY;}
     for(const q of other){q.group=group;changed.add(q.id);}
     moving=groupMembers(next,piece);again=true;break;
    }
    if(again||moving.every(q=>q.placed))break;
   }
   if(moving.some(q=>q.placed)||fitsBoard()){anchor(moving);break;}
  }
 }
 next.leases=next.leases.filter(l=>l!==lease);
 next.moves++;
 const version=next.revision+1;
 for(const p of moving){p.version=version;changed.add(p.id);}
 if(next.pieces.every(p=>p.placed))next.finishedAt=now;
 return finish([...changed]);
}
module.exports={SHAPES,LEASE_MS,createPuzzle,applyCommand,publicSnapshot};
