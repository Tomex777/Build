import {fmt,guilloche,rng} from './geometry.mjs';
export const THEMES={
 nocturne:{ink:'#0e2530',deep:'#030a13',metal:['#2e3b48','#8babb5','#f1f8f2','#658d9b','#d4f5eb','#3c566a','#e5ecf1'],text:'#edf5ef',muted:'#b4d2d5',foil:['#7ae1d4','#9ab4ec','#d3a6d8','#f0d5ae','#83d6d5'],accent:'#bbe7e2'},
 tidelight:{ink:'#143c55',deep:'#021e32',metal:['#527b94','#c6e4ed','#fcfffa','#8dbccc','#e8f7f7','#6f9fb5','#f6fbf5'],text:'#f2fcff',muted:'#b2d9e6',foil:['#7bc9ec','#bdbfe9','#e0bdcd','#f4e9cd','#97e0e5'],accent:'#d5f1f4'},
 verdant:{ink:'#163c31',deep:'#06251f',metal:['#6a4e2e','#bb9558','#ffedb8','#9b793f','#ebd394','#76603b','#f8ebbc'],text:'#fff1c9',muted:'#d0c79b',foil:['#8fbda6','#c7cbb1','#dfb69c','#f7de9e','#9dc5a1'],accent:'#e8ce91'}
};
/** Shared by initial SVG generation and live DOM updates. */
export function lightCoordinates(light=[.28,.2]){
 if(!Array.isArray(light)||light.length!==2||light.some(n=>!Number.isFinite(n)))throw new Error('Invalid light');
 const x=Math.max(0,Math.min(1,light[0]))*1000,y=Math.max(0,Math.min(1,light[1]))*1400;
 return {x,y,metal:[x-680,y-560,x+1050,y+1020],foil:[x-530,y-650,x+660,y+780],sweep:[x-500,y-350,x+500,y+350]};
}
/** Apply the same shader uniforms used by renderCard; no geometry is rebuilt. */
export function setCardLight(svg,light){
 const c=lightCoordinates(light);
 for(const [kind,values]of [['metal',c.metal],['foil',c.foil],['stripe',c.sweep]]){
  const node=svg.querySelector(`[id$="${kind}"]`);if(!node)continue;
  for(const [i,a]of ['x1','y1','x2','y2'].entries())node.setAttribute(a,fmt(values[i]));
 }
 const spec=svg.querySelector('[id$="spec"]');if(spec){spec.setAttribute('cx',fmt(c.x));spec.setAttribute('cy',fmt(c.y));}
}
const lineGradient=(id,values,stops)=>`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${fmt(values[0])}" y1="${fmt(values[1])}" x2="${fmt(values[2])}" y2="${fmt(values[3])}">${stops}</linearGradient>`;
export function materialDefs(p,t,{light=[.28,.2],foil=1}={}){
 const c=lightCoordinates(light),stops=t.metal.map((v,i)=>`<stop offset="${[0,18,31,38,53,66,100][i]}%" stop-color="${v}"/>`).join('');
 return `${lineGradient(p+'metal',c.metal,stops)}
 ${lineGradient(p+'foil',c.foil,t.foil.map((v,i)=>`<stop offset="${i*25}%" stop-color="${foil?v:t.accent}"/>`).join(''))}
 ${lineGradient(p+'stripe',c.sweep,`<stop stop-color="#fff" stop-opacity="0"/><stop offset=".30" stop-color="#b1d3e4" stop-opacity="0"/><stop offset=".43" stop-color="#fff" stop-opacity="${foil*.13}"/><stop offset=".50" stop-color="#fff" stop-opacity="${foil*.38}"/><stop offset=".57" stop-color="#eedbfc" stop-opacity="${foil*.16}"/><stop offset=".70" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>`)}
 <linearGradient id="${p}edge" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fffce9"/><stop offset=".12" stop-color="${t.metal[4]}"/><stop offset=".3" stop-color="${t.metal[0]}"/><stop offset=".6" stop-color="${t.metal[2]}"/><stop offset=".8" stop-color="${t.metal[0]}"/><stop offset="1" stop-color="${t.metal[5]}"/></linearGradient>
 <linearGradient id="${p}letter" x1="0" y1="0" x2=".2" y2="1"><stop stop-color="${t.text}"/><stop offset=".40" stop-color="${t.text}"/><stop offset=".51" stop-color="${t.muted}"/><stop offset=".56" stop-color="${t.text}"/><stop offset=".82" stop-color="${t.accent}"/><stop offset="1" stop-color="${t.text}"/></linearGradient>
 <linearGradient id="${p}pearlLetter" x1="0" y1="0" x2=".2" y2="1"><stop stop-color="#082b45"/><stop offset=".49" stop-color="#163d56"/><stop offset=".54" stop-color="#5a7f92"/><stop offset=".59" stop-color="#163d56"/><stop offset="1" stop-color="#082a3d"/></linearGradient>
 <linearGradient id="${p}pearl" x1="0" y1="0" x2=".4" y2="1"><stop stop-color="#f7fff9"/><stop offset=".4" stop-color="#d0eaf0"/><stop offset=".53" stop-color="#fffdf3"/><stop offset="1" stop-color="#b2d2e0"/></linearGradient>
 <linearGradient id="${p}pearlFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#edf9f8" stop-opacity="0"/><stop offset=".26" stop-color="#e7f5f3" stop-opacity=".93"/><stop offset=".46" stop-color="#e9f5f3"/><stop offset="1" stop-color="#d4e7ec"/></linearGradient>
 <linearGradient id="${p}bottom" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${t.deep}" stop-opacity="0"/><stop offset=".37" stop-color="${t.deep}" stop-opacity=".42"/><stop offset=".74" stop-color="${t.deep}" stop-opacity=".97"/><stop offset="1" stop-color="${t.deep}"/></linearGradient>
 <linearGradient id="${p}top" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${t.deep}" stop-opacity=".6"/><stop offset="1" stop-color="${t.deep}" stop-opacity="0"/></linearGradient>
 <radialGradient id="${p}sky" cx=".48" cy=".35" r=".75"><stop stop-color="#345a67"/><stop offset=".40" stop-color="${t.ink}"/><stop offset="1" stop-color="${t.deep}"/></radialGradient>
 <radialGradient id="${p}moon" cx=".36" cy=".29" r=".78"><stop stop-color="#607b83"/><stop offset=".32" stop-color="#304754"/><stop offset=".73" stop-color="#142b3a"/><stop offset="1" stop-color="#25434d"/></radialGradient>
 <radialGradient id="${p}spec" gradientUnits="userSpaceOnUse" cx="${fmt(c.x)}" cy="${fmt(c.y)}" r="740"><stop stop-color="#fff" stop-opacity="${.45*foil}"/><stop offset=".30" stop-color="${t.accent}" stop-opacity="${.2*foil}"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
 <pattern id="${p}grain" width="7" height="9" patternUnits="userSpaceOnUse"><path d="M.5 1h1M4 4h.6M2 7h.5" stroke="white" stroke-opacity=".15" stroke-width=".55"/><path d="M5.5 1h.5M.5 6h1" stroke="black" stroke-opacity=".18" stroke-width=".5"/></pattern>
 <pattern id="${p}groove" width="3.4" height="3.4" patternUnits="userSpaceOnUse" patternTransform="rotate(32)"><path d="M0 0V4" stroke="#fff" stroke-opacity=".23" stroke-width=".45"/></pattern>
 <pattern id="${p}dots" width="13" height="13" patternUnits="userSpaceOnUse"><circle cx="3" cy="3" r=".7" fill="${t.accent}" opacity=".14"/></pattern>
 <pattern id="${p}microfoil" width="12" height="16" patternUnits="userSpaceOnUse"><path d="M2 3h.7M8 10h.5" stroke="#fff" stroke-width=".6" opacity=".65"/></pattern>
 <filter id="${p}shadow" x="-.2" y="-.2" width="1.4" height="1.4"><feDropShadow dx="0" dy="12" stdDeviation="16" flood-color="#000" flood-opacity=".6"/></filter>
 <filter id="${p}softMask"><feGaussianBlur stdDeviation="25"/></filter>
 <mask id="${p}edgeMask"><rect width="1000" height="1400" rx="40" fill="white"/><rect x="40" y="40" width="920" height="1320" rx="26" fill="black"/></mask>
 <mask id="${p}artFoilMask"><rect width="1000" height="1400" fill="black"/><g filter="url(#${p}softMask)"><path d="M-40 600Q140 1050 520 1080Q760 1070 1040 850V1440H-40Z" fill="white"/></g></mask>`;
}
export function backdrop(p,t,seed,{engraving=true}={}){
 const random=rng(seed);const stars=Array.from({length:32},()=>`<circle cx="${fmt(70+random()*860)}" cy="${fmt(150+random()*850)}" r="${fmt(.4+random()*.9)}" opacity="${fmt(.15+random()*.25)}"/>`).join('');
 return `<rect width="1000" height="1400" fill="url(#${p}sky)"/>
 ${engraving?`<g fill="none" stroke="url(#${p}foil)" stroke-width=".65" opacity=".36">${guilloche({cx:500,cy:540,rx:360,ry:425,lines:18})}</g>`:''}<g fill="${t.accent}">${stars}</g>`;
}
export function foilContours(p){
 return `<g fill="none" stroke="url(#${p}foil)" stroke-width=".85" opacity=".25">${Array.from({length:48},(_,i)=>`<path d="M-60 ${660+i*5}C${180+i*2} ${1050+i*3},${600-i*2} ${580+i*7},1060 ${780+i*5}"/>`).join('')}</g>`;
}
export function engravedFrame(p,t,{ornate=true,variant='nocturne'}={}){
 const base=`<rect x="4" y="4" width="992" height="1392" rx="39" fill="none" stroke="#02080d" stroke-width="7"/><rect x="8" y="8" width="984" height="1384" rx="36" fill="none" stroke="url(#${p}edge)" stroke-width="5"/>`;
 if(variant==='tidelight')return `${base}<rect x="18" y="18" width="964" height="1364" rx="29" fill="none" stroke="url(#${p}pearl)" stroke-width="17"/><rect x="29" y="29" width="942" height="1342" rx="23" fill="none" stroke="url(#${p}metal)" stroke-width="1.4"/><rect x="12" y="12" width="976" height="1376" rx="34" fill="none" stroke="url(#${p}stripe)" stroke-width="12"/>`;
 const detail=ornate?`<path d="M38 182V70Q38 38 70 38H210M790 38H930Q962 38 962 70V182M38 1218V1330Q38 1362 70 1362H210M790 1362H930Q962 1362 962 1330V1218" fill="none" stroke="url(#${p}metal)" stroke-width="1.5"/>`:'';
 return `${base}<rect x="18" y="18" width="964" height="1364" rx="30" fill="none" stroke="${t.deep}" stroke-width="15"/><rect width="1000" height="1400" fill="url(#${p}foil)" mask="url(#${p}edgeMask)" opacity=".38"/><rect width="1000" height="1400" fill="url(#${p}groove)" mask="url(#${p}edgeMask)"/><rect x="27" y="27" width="946" height="1346" rx="24" fill="none" stroke="url(#${p}metal)" stroke-width="1.6"/><rect x="12" y="12" width="976" height="1376" rx="34" fill="none" stroke="url(#${p}stripe)" stroke-width="8"/>${detail}`;
}
