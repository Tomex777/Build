/** All measurements are in the 1000 × 1400 master coordinate space. */
export const WIDTH=1000, HEIGHT=1400;
export const clamp=(v,a,b)=>Math.min(b,Math.max(a,v));
export const xml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
export const fmt=n=>Number(n.toFixed(3));
export function rng(seed=1){let n=seed>>>0;return()=>{n+=0x6D2B79F5;let t=n;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296;};}
export function star(cx,cy,r=15){return `M${cx} ${cy-r}Q${cx+2} ${cy-2} ${cx+r} ${cy}Q${cx+2} ${cy+2} ${cx} ${cy+r}Q${cx-2} ${cy+2} ${cx-r} ${cy}Q${cx-2} ${cy-2} ${cx} ${cy-r}Z`;}
/** Deterministic multi-sine guilloché. This is vector engraving, not a bitmap overlay. */
export function guilloche({cx=500,cy=470,rx=380,ry=400,lines=22,phase=0}={}){
 return Array.from({length:lines},(_,k)=>{
  const pts=[];for(let i=0;i<=500;i++){
   const t=i/500*Math.PI*2, wave=Math.sin(t*18+k*.34+phase)*5+Math.sin(t*7-k*.22)*7;
   pts.push(`${i?'L':'M'}${fmt(cx+(rx+k*1.25+wave)*Math.cos(t))},${fmt(cy+(ry+k*1.25+wave)*Math.sin(t))}`);
  }return `<path d="${pts.join('')}Z"/>`;
 }).join('');
}
export function rosette(cx,cy,r=43,petals=16){let s='';for(let k=0;k<6;k++){
 const pts=[];for(let i=0;i<=400;i++){const t=i/400*2*Math.PI,rr=r-5+k*1.25+5*Math.cos(t*petals+k*.6);pts.push(`${i?'L':'M'}${fmt(cx+rr*Math.cos(t))} ${fmt(cy+rr*Math.sin(t))}`);}s+=`<path d="${pts.join('')}Z"/>`;
}return s;}
/** Cover with an explicit artwork focal point, including edge clamping. */
export function cover(iw,ih,box,focal=[.5,.5]){
 const scale=Math.max(box.w/iw,box.h/ih),w=iw*scale,h=ih*scale;
 return{x:fmt(box.x+clamp(box.w/2-focal[0]*w,box.w-w,0)),y:fmt(box.y+clamp(box.h/2-focal[1]*h,box.h-h,0)),w:fmt(w),h:fmt(h)};
}
