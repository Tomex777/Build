import assert from 'node:assert/strict';
import {PuzzleBoard} from './engine/PuzzleBoard.js';
import {SoundSynth} from './engine/SoundSynth.js';

const noOp=()=>{};
const makeCtx=()=>new Proxy({}, {get:(obj,name)=>{
 if(name==='canvas')return {width:390,height:844};
 if(name==='globalAlpha')return obj.globalAlpha??1;
 if(name==='createLinearGradient')return ()=>({addColorStop:noOp});
 return obj[name]??noOp;
},set:(obj,k,v)=>(obj[k]=v,true)});
const makeCanvas=(w=360,h=660)=>({width:w,height:h,getContext:()=>makeCtx()});
globalThis.document={createElement:tag=>{assert.equal(tag,'canvas');return makeCanvas()}};
const canvas=makeCanvas(),drag=makeCanvas(),wrapper={
 width:390,height:844,
 querySelector:selector=>{assert.equal(selector,'#dragCanvas');return drag},
 getBoundingClientRect(){return {width:this.width,height:this.height}}
};
const board=new PuzzleBoard(canvas,wrapper);
board.setDimensions(4,6);board.buildPieces();
assert.equal(board.pieces.length,24);
assert.ok(board.boardY>70,'mobile board must leave visible scattered space above');
assert.ok(board.boardY+board.boardHeight<canvas.height-70,'mobile board must leave space below');
assert.ok(board.pieces.some(p=>p.y+p.canvasMargin<board.boardY),'some pieces above');
assert.ok(board.pieces.some(p=>p.y>board.boardY+board.boardHeight),'some pieces below');
assert.ok(new Set(board.pieces.map(p=>Math.round(p.x))).size>=12,'scattering is not a rigid tray grid');
const a=board.pieces.find(p=>p.row===1&&p.col===1),b=board.pieces.find(p=>p.row===1&&p.col===2);
// Isolate the intended connection; unrelated random neighbours can
// accidentally fall within snap distance and make this test flaky.
for(const p of board.pieces)if(p!==a&&p!==b){p.x=-10000;p.y=-10000;}
a.x=34;a.y=30;b.x=a.x+board.pieceWidth+2;b.y=a.y+1;
assert.equal(board.checkSnap(a),false,'free neighbour should join, not auto-solve');
assert.equal(board.lastJoinCount,1,'adjacent jigsaw pieces must link');
assert.equal(a.group,b.group,'connected pieces form one group');
const oldA={x:a.x,y:a.y},oldB={x:b.x,y:b.y};
board.selectedPiece=board.bringToTop(board.pieces.indexOf(a));
board.moveSelectionTo(a.x+35,a.y+20);
assert.ok(Math.abs(a.x-oldA.x-35)<.01&&Math.abs(b.x-oldB.x-35)<.01,'dragging a joined group moves both pieces');
assert.ok(Math.abs(b.y-oldB.y-20)<.01,'joined piece tracks vertical motion');
board.selectedPiece=null;board.selectedPieces=[];
a.x=a.correctX+1;a.y=a.correctY+1;
assert.equal(board.checkSnap(a),true,'free group snaps as a unit to the target');
assert.equal(board.placedCount,2,'anchoring a group places both members');
assert.ok(a.isPlaced&&b.isPlaced,'both joined pieces are finished');
board.edgeOnly=true;const saved=board.serialize();
assert.equal(saved.layoutVersion,3,'new save format persists grouped coordinates');
const restored=new PuzzleBoard(makeCanvas(),wrapper);
restored.setDimensions(4,6);restored.resize();restored.setupBoardLayout();
assert.ok(restored.restore(saved),'grouped puzzle can be resumed');
assert.equal(restored.placedCount,2);
assert.equal(restored.edgeOnly,true,'edge filter persists');
const c=restored.pieces.find(p=>p.id===a.id),d=restored.pieces.find(p=>p.id===b.id);
assert.equal(c.group,d.group,'connections survive saving');
wrapper.width=1280;wrapper.height=850;
restored.resize();
assert.equal(restored.placedCount,2,'rotation/resize preserves placed groups');
assert.ok(restored.pieces.some(p=>!p.isPlaced&&p.x+p.offscreenCanvas.width-p.canvasMargin*2<restored.boardX),'desktop loose pieces can sit to left');
assert.ok(restored.pieces.some(p=>!p.isPlaced&&p.x>restored.boardX+restored.boardWidth),'desktop loose pieces can sit to right');
const placedBeforeSweep=restored.placedCount;
restored.sweepLoose();
assert.equal(restored.placedCount,placedBeforeSweep,'Sweep retains finished pieces');
assert.equal(restored.pieces.find(p=>p.id===a.id).group,restored.pieces.find(p=>p.id===b.id).group,'Sweep retains grouped sections');
wrapper.width=390;wrapper.height=844;

// Verify high piece counts remain playable without precreating separate DOM nodes.
board.setDimensions(12,12);board.buildPieces();
assert.equal(board.pieces.length,144);
assert.ok(board.pieces.every(p=>p.offscreenCanvas),'all puzzle silhouettes are pre-rendered');

// Audio should remain fully optional; no sound context created until a gesture.
let emitted=0;
const param=()=>({value:0,setValueAtTime:noOp,exponentialRampToValueAtTime:noOp});
const node=()=>({frequency:param(),gain:param(),Q:param(),threshold:param(),knee:param(),ratio:param(),attack:param(),release:param(),connect:noOp,disconnect:noOp,start:()=>emitted++,stop:noOp});
class FakeAudioContext{
 constructor(){this.currentTime=0;this.sampleRate=44100;this.destination=node();this.state='running';}
 createGain(){return node()}createDynamicsCompressor(){return node()}
 createOscillator(){return node()}createBiquadFilter(){return node()}
 createBufferSource(){return node()}
 createBuffer(){return {getChannelData:()=>new Float32Array(44100/5)}}
}
globalThis.window={AudioContext:FakeAudioContext};
const sound=new SoundSynth();
assert.equal(sound.audioCtx,null,'no audio should start before gameplay');
sound.playPickup();sound.playDrop();sound.playSnap();sound.playSnap({joined:true});sound.playVictory();
assert.ok(emitted>=18,'tactile effects comprise layered independent envelopes');
const before=emitted;sound.enabled=false;sound.playSnap();sound.playVictory();
assert.equal(emitted,before,'muted jigsaw never plays sound');
console.log('PASS NIGHT Jigsaw: scattered tabletop, connected pieces, touch group motion, saves, 144 pieces, optional layered tactile audio');
