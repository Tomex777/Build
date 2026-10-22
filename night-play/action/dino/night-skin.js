/* NIGHT Play hand-drawn pixel character and hazard sheet.
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
// New NIGHT runner: courier with mint helmet, copper scarf, visor and
// two-frame stride. The body never leaves Chromium's 44×47 sprite footprint.
function courier(frame){
 const duck=frame==='duck1'||frame==='duck2';
 const width=duck?59:44;
 return spriteCanvas(width,47,({r,poly})=>{
  if(duck){
   const sway=frame==='duck1'?0:2;
   r(4,37,51,7,'outline');r(8,31,45,11,'teal');
   r(12,27,36,8,'navy');r(20,22,26,9,'mint');r(34,23,8,4,'pale');
   poly([[6,28],[18,28],[15,33],[3,33]],'rust');
   poly([[16,18],[45,18],[52,24],[49,34],[17,34],[12,29]],'outline');
   poly([[18,18],[43,18],[49,24],[46,30],[18,30],[14,26]],'mint');
   r(35,22,12,5,'navy');r(45,23,3,2,'gold');
   r(11+sway,38,15,5,'navy');r(37-sway,38,15,5,'navy');
   r(9+sway,43,17,3,'outline');r(34-sway,43,19,3,'outline');
   r(7,25,6,5,'ruby');return;
  }
  const run1=frame==='run1',run2=frame==='run2',jump=frame==='jump',crash=frame==='crash';
  // Two separate feet animate against the original 12fps runner state.
  if(crash){
   r(9,37,24,8,'navy');r(6,43,29,4,'outline');
   r(12,25,23,13,'teal');r(5,31,8,5,'rust');
   r(16,12,23,18,'outline');r(18,13,19,15,'mint');
   r(30,18,7,4,'navy');r(32,19,3,2,'ruby');
   r(12,36,9,4,'gold');return;
  }
  // Feet and shin guards, alternating for the run frames.
  if(jump){r(15,33,9,7,'navy');r(24,33,9,8,'navy');r(13,39,11,4,'outline');r(25,40,11,4,'outline')}
  else if(run1){
   r(15,31,8,9,'navy');r(12,39,10,5,'outline');r(10,43,15,3,'navy');
   r(24,31,8,11,'navy');r(26,40,10,5,'outline');r(25,44,15,2,'navy')
  }else if(run2){
   r(16,31,9,11,'navy');r(17,40,12,5,'outline');
   r(26,31,9,7,'navy');r(30,37,9,5,'outline');r(30,41,12,4,'navy')
  }else{
   r(16,31,8,12,'navy');r(15,42,11,4,'outline');
   r(26,31,8,12,'navy');r(25,42,11,4,'outline')
  }
  // Back scarf and backpack create a recognizable silhouette.
  poly([[11,20],[19,20],[17,25],[7,29],[4,27],[12,23]],'rust');
  r(9,24,9,9,'outline');r(11,25,8,7,'gold');
  poly([[15,17],[32,17],[37,24],[32,34],[14,34],[12,24]],'outline');
  poly([[17,18],[31,18],[35,24],[30,32],[16,32],[14,24]],'teal');
  r(17,20,13,4,'mint');r(21,27,9,3,'shadow');
  // Hood with separated cap peak and readable eyes.
  poly([[17,7],[25,4],[35,6],[40,12],[38,21],[31,25],[18,23],[14,16]],'outline');
  poly([[18,9],[26,6],[34,8],[38,13],[36,20],[29,23],[19,21],[16,16]],'mint');
  r(22,8,11,4,'pale');r(26,13,12,8,'skin');
  r(29,13,10,5,'navy');r(32,15,3,2,'cream');
  r(36,20,4,3,'outline');
  // Glove and amber arm band.
  r(32,25,6,5,'gold');r(35,27,6,5,'outline');
  r(33,29,7,3,'skin');
  if(jump){r(15,35,8,3,'gold');r(28,35,7,3,'gold')}
 });
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
   g.drawImage(artwork,0,0,artwork.width,artwork.height,
    x,y,dw,height*pixelRatio);
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