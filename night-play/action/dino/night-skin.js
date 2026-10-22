/* NIGHT Play original runner character and custom obstacle atlas.
 * Swaps appearance at the exact Chromium source coordinates. No physics,
 * obstacle spawning, hitbox, velocity, or T-Rex animation timers are changed.
 * All art is authored for NIGHT Play, not extracted from Chrome's artwork.
 */
(()=>{
'use strict';
const C={
 outline:'#20343b',navy:'#243b4d',shadow:'#335465',teal:'#3c7e79',
 mint:'#69bb9d',pale:'#d7f1d2',skin:'#ffdbab',cream:'#fff0c7',
 gold:'#efb66c',sun:'#ffcd79',rust:'#be735f',ruby:'#e77564'
};
function spriteCanvas(w,h,paint){
 const canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;
 const g=canvas.getContext('2d');g.imageSmoothingEnabled=false;
 const r=(x,y,w,h,color)=>{g.fillStyle=C[color]||color;g.fillRect(x,y,w,h)};
 const poly=(coords,color)=>{g.fillStyle=C[color]||color;g.beginPath();
  coords.forEach(([x,y],i)=>i?g.lineTo(x,y):g.moveTo(x,y));g.closePath();g.fill()};
 paint({r,poly,g});return canvas;
}
// Original NIGHT Play runner visual, restored from action/games.js before
// the Chromium engine migration. Dark-green cap, warm face, green shirt,
// tan waist band and light trainers — *not* the later mint-armoured courier.
// Each original Chromium frame is redrawn in a source-sized canvas, so no
// physics, obstacle collision box or frame timer needs to change.
function courier(frame){
 const duck=frame==='duck1'||frame==='duck2',width=duck?59:44;
 // Render at 3× then downsample into Chromium's existing atlas dimensions:
 // restores the old smooth-shape style rather than using blocky pixel art.
 const aa=3,canvas=document.createElement('canvas');
 canvas.width=width*aa;canvas.height=47*aa;
 const g=canvas.getContext('2d');g.scale(aa,aa);
 g.lineJoin='round';g.lineCap='round';
 const fill=(x,y,w,h,col,rad=0)=>{
  g.fillStyle=col;g.beginPath();
  if(rad)g.roundRect(x,y,w,h,Math.min(rad,h/2,w/2));
  else g.rect(x,y,w,h);
  g.fill();
 };
 const disk=(x,y,r,col)=>{g.beginPath();g.arc(x,y,r,0,Math.PI*2);g.fillStyle=col;g.fill()};
 const line=(x,y,x2,y2,color,lw)=>{g.strokeStyle=color;g.lineWidth=lw;
  g.beginPath();g.moveTo(x,y);g.lineTo(x2,y2);g.stroke()};
 const stride=frame==='run1'?-1:frame==='run2'?1:0;
 g.translate(width/2,46);
 if(duck){
  // The recognizable side-facing crouch from the initial action game.
  const sway=frame==='duck1'?-1:1;
  fill(-25,-11,47,8,'#32725b',4);
  fill(-27,-5,50,5,'#be885e',3);
  fill(-23,-2,17,3,'#28463e',2);fill(7,-2,17,3,'#28463e',2);
  fill(-19,-17,23,12,'#32725b',5);
  disk(11,-18,10,'#e9b18c');
  fill(2,-29,20,8,'#254f49',4);
  fill(0,-23,21,4,'#386f5b',2);
  disk(16,-18,1.6,'#253a37');
  fill(-17+sway,-2,15,3,'#f4e9d4',2);
  fill(10-sway,-2,16,3,'#f4e9d4',2);
 } else {
  const jumping=frame==='jump',crash=frame==='crash';
  if(crash){g.rotate(.17);g.translate(1,2)}
  else if(jumping){g.rotate(-.08)}
  // Scale the original 51-pixel-tall model into the original 47px atlas
  // WITHOUT scaling the engine's collision geometry or jump height.
  g.scale(.87,.87);
  // Swinging arms, staggered trousers and rounded cream trainers:
  // reconstruct the exact palette/proportions from old paintRunner().
  const swing=jumping?5:stride*6;
  line(-8,-29,-15,-22+stride*4,'#e7ae89',5);
  line(8,-29,14,-22-stride*4,'#e7ae89',5);
  line(-5,-16,-7+swing,-6,'#2b5448',7);
  line(5,-16,7-swing,-6,'#2b5448',7);
  fill(-13+swing,-6,13,6,'#f4e9d4',3);
  fill(2-swing,-6,13,6,'#f4e9d4',3);
  // Simple green jersey, contrast collar and the tan waist band.
  fill(-11,-34,22,21,'#32725b',6);
  fill(-9,-27,18,4,'#8ac0a2',2);
  fill(-10,-16,20,7,'#be885e',3);
  disk(0,-41,10,'#e9b18c');
  // Original green cap, curved peak and tiny eye.
  fill(-11,-51,22,8,'#254f49',5);
  fill(-11,-44,22,4,'#386f5b',2);
  disk(4,-40,1.5,'#253a37');
  if(crash){
   // Small tilt and shut eye, not a different space-suit character.
   line(1,-40,7,-40,'#253a37',1.8);
  }
 }
 return canvas;
}
function thornPillar(width,height,kind,variation=0){
 return spriteCanvas(width,height,({r,poly})=>{
  const tall=kind==='large',cx=Math.floor(width/2);
  // NIGHT obstacle: crystalline thorn growth, broad foot and clear dark edge.
  poly([[cx-7,height-3],[cx-6,Math.floor(height*.39)],[cx-3,8],
        [cx,1],[cx+4,Math.floor(height*.31)],[cx+7,height-3]],'outline');
  poly([[cx-4,height-5],[cx-3,Math.floor(height*.39)],[cx,7],
        [cx+3,Math.floor(height*.35)],[cx+4,height-5]],'teal');
  poly([[cx,8],[cx+3,Math.floor(height*.38)],[cx,Math.floor(height*.62)],[cx-1,8]],'mint');
  poly([[cx-3,Math.floor(height*.57)],[cx-12,Math.floor(height*.45)],
         [cx-12,Math.floor(height*.72)],[cx-5,Math.floor(height*.78)]],'outline');
  poly([[cx+4,Math.floor(height*.66)],[cx+11,Math.floor(height*.51)],
         [cx+12,Math.floor(height*.77)],[cx+6,Math.floor(height*.83)]],'outline');
  r(cx-5,height-7,11,4,'shadow');r(cx-8,height-3,17,3,'outline');
  r(cx-1,height-11,3,4,'gold');
  if(tall){r(cx-2,14,4,4,'pale');r(cx+5,25,3,4,'gold')}
  if(variation%2)r(cx-3,height-15,3,4,'pale');
 });
}
function flyer(frame){
 return spriteCanvas(46,40,({r,poly})=>{
  const upper=frame===0;
  // Flying sentry with twin articulated solar wings and a lantern eye.
  const ly=upper?4:18;
  const ry=upper?4:18;
  poly([[18,18],[4,ly],[1,ly+8],[13,27],[22,28]],'outline');
  poly([[18,20],[6,ly+4],[4,ly+8],[15,26],[22,26]],'teal');
  poly([[28,18],[42,ry],[45,ry+8],[33,27],[25,28]],'outline');
  poly([[28,20],[40,ry+4],[42,ry+8],[31,26],[25,26]],'mint');
  r(17,12,12,19,'outline');r(19,15,8,13,'shadow');
  r(19,17,8,7,'gold');r(21,19,4,3,'cream');
  r(20,28,6,5,'outline');r(22,32,3,5,'ruby');
  r(11,upper?12:22,6,3,'pale');r(31,upper?12:22,6,3,'gold');
 });
}
const entries=[
 {key:'TREX',parts:[[0,44,()=>courier('jump')],[44,44,()=>courier('idle')],
    [88,44,()=>courier('run1')],[132,44,()=>courier('run2')],
    [220,44,()=>courier('crash')],[264,59,()=>courier('duck1')],[323,59,()=>courier('duck2')]]},
 {key:'CACTUS_SMALL',parts:[...Array(3)].map((_,i)=> {
  const width=17*(i+1),offset=17*(i*(i+1)/2);
  return [offset,width,()=>cluster(width,35,17,'small',i)];
 })},
 {key:'CACTUS_LARGE',parts:[...Array(3)].map((_,i)=>{
  const width=25*(i+1),offset=25*(i*(i+1)/2);
  return [offset,width,()=>cluster(width,50,25,'large',i)];
 })},
 {key:'PTERODACTYL',parts:[[0,46,()=>flyer(0)],[46,46,()=>flyer(1)]]}
];
function cluster(width,height,unit,kind,phase){
 return spriteCanvas(width,height,({g})=>{
  for(let i=0;i<width/unit;i++){
   const s=thornPillar(unit,height,kind,i+phase);
   g.drawImage(s,i*unit,0);
  }
 });
}
function install(){
 const pixelRatio=window.devicePixelRatio>1?2:1;
 const img=document.getElementById(pixelRatio===2?'offline-resources-2x':'offline-resources-1x');
 if(!img?.complete||!img.naturalWidth)throw Error('Chromium sprite base not loaded');
 const out=document.createElement('canvas');out.width=img.naturalWidth;out.height=img.naturalHeight;
 const g=out.getContext('2d');g.imageSmoothingEnabled=false;g.drawImage(img,0,0);
 const pos=pixelRatio===2?
  {TREX:[1678,2],CACTUS_SMALL:[446,2],CACTUS_LARGE:[652,2],PTERODACTYL:[260,2]}:
  {TREX:[848,2],CACTUS_SMALL:[228,2],CACTUS_LARGE:[332,2],PTERODACTYL:[134,2]};
 for(const entry of entries){
  const [sx,sy]=pos[entry.key];
  const height=entry.key==='TREX'?47:entry.key==='CACTUS_SMALL'?35:entry.key==='CACTUS_LARGE'?50:40;
  for(const [offset,width,paint] of entry.parts){
   const x=sx+offset*pixelRatio,y=sy;
   // Do not overwrite another entry's next source region (HDPI sheets are 1px tight).
   const dw=Math.min(width*pixelRatio,out.width-x);
   g.clearRect(x,y,dw,height*pixelRatio);
   const artwork=paint();
   g.imageSmoothingEnabled=entry.key==='TREX';
   g.imageSmoothingQuality='high';
   g.drawImage(artwork,0,0,artwork.width,artwork.height,
    x,y,dw,height*pixelRatio);
   g.imageSmoothingEnabled=false;
  }
 }
 return new Promise((resolve,reject)=>{
  img.onload=()=>resolve({sheet:img,scale:pixelRatio});
  img.onerror=()=>reject(new Error('NIGHT sprite sheet failed to load'));
  img.src=out.toDataURL('image/png');
 });
}
window.NightDinoSkin={install};
})();