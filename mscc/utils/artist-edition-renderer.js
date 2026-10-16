// Transplanted without layout changes from the archived NIGHT-Naruto.html
// Every composition, foil, frame, color, and SVG geometry is retained.
// Do not substitute generic card templates for these three Artist Editions.
const window = Object.create(null);
(()=>{
/** All measurements are in the 1000 × 1400 master coordinate space. */
const WIDTH=1000, HEIGHT=1400;
const clamp=(v,a,b)=>Math.min(b,Math.max(a,v));
const xml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const fmt=n=>Number(n.toFixed(3));
function rng(seed=1){let n=seed>>>0;return()=>{n+=0x6D2B79F5;let t=n;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296;};}
function star(cx,cy,r=15){return `M${cx} ${cy-r}Q${cx+2} ${cy-2} ${cx+r} ${cy}Q${cx+2} ${cy+2} ${cx} ${cy+r}Q${cx-2} ${cy+2} ${cx-r} ${cy}Q${cx-2} ${cy-2} ${cx} ${cy-r}Z`;}
/** Deterministic multi-sine guilloché. This is vector engraving, not a bitmap overlay. */
function guilloche({cx=500,cy=470,rx=380,ry=400,lines=22,phase=0}={}){
 return Array.from({length:lines},(_,k)=>{
  const pts=[];for(let i=0;i<=500;i++){
   const t=i/500*Math.PI*2, wave=Math.sin(t*18+k*.34+phase)*5+Math.sin(t*7-k*.22)*7;
   pts.push(`${i?'L':'M'}${fmt(cx+(rx+k*1.25+wave)*Math.cos(t))},${fmt(cy+(ry+k*1.25+wave)*Math.sin(t))}`);
  }return `<path d="${pts.join('')}Z"/>`;
 }).join('');
}
function rosette(cx,cy,r=43,petals=16){let s='';for(let k=0;k<6;k++){
 const pts=[];for(let i=0;i<=400;i++){const t=i/400*2*Math.PI,rr=r-5+k*1.25+5*Math.cos(t*petals+k*.6);pts.push(`${i?'L':'M'}${fmt(cx+rr*Math.cos(t))} ${fmt(cy+rr*Math.sin(t))}`);}s+=`<path d="${pts.join('')}Z"/>`;
}return s;}
/** Cover with an explicit artwork focal point, including edge clamping. */
function cover(iw,ih,box,focal=[.5,.5]){
 const scale=Math.max(box.w/iw,box.h/ih),w=iw*scale,h=ih*scale;
 return{x:fmt(box.x+clamp(box.w/2-focal[0]*w,box.w-w,0)),y:fmt(box.y+clamp(box.h/2-focal[1]*h,box.h-h,0)),w:fmt(w),h:fmt(h)};
}


const THEMES={
 nocturne:{ink:'#0e2530',deep:'#030a13',metal:['#2e3b48','#8babb5','#f1f8f2','#658d9b','#d4f5eb','#3c566a','#e5ecf1'],text:'#edf5ef',muted:'#b4d2d5',foil:['#7ae1d4','#9ab4ec','#d3a6d8','#f0d5ae','#83d6d5'],accent:'#bbe7e2'},
 tidelight:{ink:'#143c55',deep:'#021e32',metal:['#527b94','#c6e4ed','#fcfffa','#8dbccc','#e8f7f7','#6f9fb5','#f6fbf5'],text:'#f2fcff',muted:'#b2d9e6',foil:['#7bc9ec','#bdbfe9','#e0bdcd','#f4e9cd','#97e0e5'],accent:'#d5f1f4'},
 verdant:{ink:'#163c31',deep:'#06251f',metal:['#6a4e2e','#bb9558','#ffedb8','#9b793f','#ebd394','#76603b','#f8ebbc'],text:'#fff1c9',muted:'#d0c79b',foil:['#8fbda6','#c7cbb1','#dfb69c','#f7de9e','#9dc5a1'],accent:'#e8ce91'}
};
/** Shared by initial SVG generation and live DOM updates. */
function lightCoordinates(light=[.28,.2]){
 if(!Array.isArray(light)||light.length!==2||light.some(n=>!Number.isFinite(n)))throw new Error('Invalid light');
 const x=Math.max(0,Math.min(1,light[0]))*1000,y=Math.max(0,Math.min(1,light[1]))*1400;
 return {x,y,metal:[x-680,y-560,x+1050,y+1020],foil:[x-530,y-650,x+660,y+780],sweep:[x-500,y-350,x+500,y+350]};
}
/** Apply the same shader uniforms used by renderCard; no geometry is rebuilt. */
function setCardLight(svg,light){
 const c=lightCoordinates(light);
 for(const [kind,values]of [['metal',c.metal],['foil',c.foil],['stripe',c.sweep]]){
  const node=svg.querySelector(`[id$="${kind}"]`);if(!node)continue;
  for(const [i,a]of ['x1','y1','x2','y2'].entries())node.setAttribute(a,fmt(values[i]));
 }
 const spec=svg.querySelector('[id$="spec"]');if(spec){spec.setAttribute('cx',fmt(c.x));spec.setAttribute('cy',fmt(c.y));}
}
const lineGradient=(id,values,stops)=>`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${fmt(values[0])}" y1="${fmt(values[1])}" x2="${fmt(values[2])}" y2="${fmt(values[3])}">${stops}</linearGradient>`;
function materialDefs(p,t,{light=[.28,.2],foil=1}={}){
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
function backdrop(p,t,seed,{engraving=true}={}){
 const random=rng(seed);const stars=Array.from({length:32},()=>`<circle cx="${fmt(70+random()*860)}" cy="${fmt(150+random()*850)}" r="${fmt(.4+random()*.9)}" opacity="${fmt(.15+random()*.25)}"/>`).join('');
 return `<rect width="1000" height="1400" fill="url(#${p}sky)"/>
 ${engraving?`<g fill="none" stroke="url(#${p}foil)" stroke-width=".65" opacity=".36">${guilloche({cx:500,cy:540,rx:360,ry:425,lines:18})}</g>`:''}<g fill="${t.accent}">${stars}</g>`;
}
function foilContours(p){
 return `<g fill="none" stroke="url(#${p}foil)" stroke-width=".85" opacity=".25">${Array.from({length:48},(_,i)=>`<path d="M-60 ${660+i*5}C${180+i*2} ${1050+i*3},${600-i*2} ${580+i*7},1060 ${780+i*5}"/>`).join('')}</g>`;
}
function engravedFrame(p,t,{ornate=true,variant='nocturne'}={}){
 const base=`<rect x="4" y="4" width="992" height="1392" rx="39" fill="none" stroke="#02080d" stroke-width="7"/><rect x="8" y="8" width="984" height="1384" rx="36" fill="none" stroke="url(#${p}edge)" stroke-width="5"/>`;
 if(variant==='tidelight')return `${base}<rect x="18" y="18" width="964" height="1364" rx="29" fill="none" stroke="url(#${p}pearl)" stroke-width="17"/><rect x="29" y="29" width="942" height="1342" rx="23" fill="none" stroke="url(#${p}metal)" stroke-width="1.4"/><rect x="12" y="12" width="976" height="1376" rx="34" fill="none" stroke="url(#${p}stripe)" stroke-width="12"/>`;
 const detail=ornate?`<path d="M38 182V70Q38 38 70 38H210M790 38H930Q962 38 962 70V182M38 1218V1330Q38 1362 70 1362H210M790 1362H930Q962 1362 962 1330V1218" fill="none" stroke="url(#${p}metal)" stroke-width="1.5"/>`:'';
 return `${base}<rect x="18" y="18" width="964" height="1364" rx="30" fill="none" stroke="${t.deep}" stroke-width="15"/><rect width="1000" height="1400" fill="url(#${p}foil)" mask="url(#${p}edgeMask)" opacity=".38"/><rect width="1000" height="1400" fill="url(#${p}groove)" mask="url(#${p}edgeMask)"/><rect x="27" y="27" width="946" height="1346" rx="24" fill="none" stroke="url(#${p}metal)" stroke-width="1.6"/><rect x="12" y="12" width="976" height="1376" rx="34" fill="none" stroke="url(#${p}stripe)" stroke-width="8"/>${detail}`;
}



const nameSize=(s,max=154)=>Math.min(max,720/(s.length*.5));
function artwork(a,box,focal){const q=cover(a.artWidth,a.artHeight,box,focal);return `<image href="${a.art}" x="${q.x}" y="${q.y}" width="${q.w}" height="${q.h}"/>`;}
function brand(c,p,t){return `<text x="72" y="98" class="sans" font-size="24" letter-spacing="7" fill="${t.text}">NIGHT</text><text x="926" y="98" text-anchor="end" class="sans" font-size="17" letter-spacing="3" fill="${t.text}">${xml(c.collection)}</text>`;}
function credit(c,a,p,t,ink=t.muted){return `<text x="72" y="1332" class="sans" font-size="15" letter-spacing="1.4" fill="${ink}">${xml(c.material)}</text><a href="${xml(a.attributionUrl??a.sourceUrl)}"><text x="926" y="1332" text-anchor="end" class="sans" font-size="15" fill="${ink}">${xml(a.artist)} · ${xml(a.license)}</text></a>`;}
function sheen(p,f){return `<g class="foil-layer" opacity="${f}"><g mask="url(#${p}artFoilMask)"><rect width="1000" height="1400" fill="url(#${p}spec)" opacity=".36"/>${foilContours(p)}<rect width="1000" height="1400" fill="url(#${p}stripe)" opacity=".5"/></g><rect width="1000" height="1400" fill="url(#${p}stripe)" mask="url(#${p}edgeMask)"/></g>`;}
function nocturne(c,a,p,t,f){
 const s=c.subject??{x:25,y:60,width:950,height:1234};
 let ticks='';for(let i=0;i<80;i++){const angle=i/80*Math.PI*2,rx=382,ry=424,cx=500,cy=565,len=i%5===0?10:4;const x=cx+Math.cos(angle)*rx,y=cy+Math.sin(angle)*ry;ticks+=`<path d="M${fmt(x)} ${fmt(y)}l${fmt(Math.cos(angle)*len)} ${fmt(Math.sin(angle)*len)}"/>`;}
 return `${backdrop(p,t,c.seed,{engraving:false})}
 <ellipse cx="500" cy="565" rx="378" ry="420" fill="url(#${p}moon)"/>
 <ellipse cx="500" cy="565" rx="378" ry="420" fill="url(#${p}grain)" opacity=".6"/><ellipse cx="500" cy="565" rx="376" ry="418" fill="url(#${p}foil)" opacity="${f*.1}"/><ellipse cx="500" cy="565" rx="376" ry="418" fill="url(#${p}spec)" opacity="${f*.25}"/>
 <g fill="none" stroke="url(#${p}metal)" stroke-width=".9" opacity=".8">${ticks}<ellipse cx="500" cy="565" rx="378" ry="420" stroke-width="2"/><ellipse cx="500" cy="565" rx="368" ry="410" stroke-width=".6"/></g>
 <g fill="none" stroke="url(#${p}foil)" stroke-width=".8" opacity="${.16+.36*f}">${guilloche({cx:500,cy:565,rx:346,ry:388,lines:14})}</g>
 <ellipse cx="500" cy="565" rx="376" ry="418" fill="url(#${p}stripe)" opacity="${f*.6}"/>
 <path d="M66 970Q220 1015 840 290M76 1010Q245 1035 900 335" fill="none" stroke="url(#${p}foil)" stroke-width="1.1" opacity=".34"/>
 <image href="${a.art}" x="${s.x}" y="${s.y}" width="${s.width}" height="${s.height}" preserveAspectRatio="xMidYMid meet" filter="url(#${p}shadow)"/>
 ${sheen(p,f)}<rect x="28" y="1020" width="944" height="350" fill="url(#${p}bottom)"/>
 ${brand(c,p,t)}
 <text data-text="name" x="70" y="1212" class="display" font-size="${nameSize(c.name,164)}" fill="url(#${p}letter)">${xml(c.name)}</text>
 <path d="${star(895,1152,24)}" fill="url(#${p}metal)"/><path d="M873 1152H918M895 1129V1176" stroke="url(#${p}metal)" stroke-width=".9"/>
 <text data-text="edition" x="75" y="1281" class="sans" font-size="43" letter-spacing="6" fill="${t.text}">${xml(c.edition)}</text>
 <text x="924" y="1281" text-anchor="end" class="sans" font-size="20" letter-spacing="3" fill="${t.muted}">${xml(c.number)}</text>
 ${credit(c,a,p,t)}`;
}
function tidelight(c,a,p,t,f){
 return `<rect width="1000" height="1400" fill="url(#${p}pearl)"/>${artwork(a,{x:24,y:25,w:952,h:1192},c.focal)}
 <rect x="28" y="26" width="944" height="205" fill="url(#${p}top)"/>
 <g fill="none" stroke="url(#${p}foil)" opacity="${.18+.25*f}"><path d="M18 120Q115 450 25 730Q-5 975 182 1100" stroke-width="5"/><path d="M968 340Q835 645 963 863Q1012 1030 807 1146" stroke-width="4"/><path d="M972 406Q881 649 976 844" stroke-width="1.3"/></g>
 ${sheen(p,f)}
 <rect x="26" y="976" width="948" height="397" fill="url(#${p}pearlFade)"/>
 <path d="M30 1066Q262 1145 602 1099T972 1083M30 1073Q261 1152 602 1106T972 1090" fill="none" stroke="url(#${p}metal)" stroke-width=".8" opacity=".65"/>
 ${brand(c,p,t)}
 <text data-text="name" x="73" y="1222" class="display" font-size="${nameSize(c.name,149)}" fill="url(#${p}pearlLetter)">${xml(c.name)}</text>
 <path d="M76 1240H926" stroke="#557e92" stroke-width=".7" opacity=".35"/>
 <text data-text="edition" x="78" y="1291" class="sans" font-size="40" letter-spacing="5.5" fill="#143b55">${xml(c.edition)}</text>
 <text x="925" y="1291" text-anchor="end" class="sans" font-size="20" letter-spacing="3" fill="#41647a">${xml(c.number)}</text>
 <path d="${star(893,1177,26)}" fill="url(#${p}metal)" stroke="#a2c4cc" stroke-width=".6"/>
 ${credit(c,a,p,t,'#3f6275')}`;
}
function botanical(p){
 const stemX=y=>74+17*Math.sin((y-220)/155);
 let stem='',leaves='';for(let y=214;y<=1158;y+=8)stem+=`${y===214?'M':'L'}${fmt(stemX(y))} ${y}`;
 for(let i=0;i<8;i++){
  const y=290+i*109,x=stemX(y),h=49+10*Math.sin(i*1.7),w=24+5*Math.cos(i*1.2);
  leaves+=`<path d="M${fmt(x)} ${y}C${fmt(x-w-9)} ${fmt(y-h*.25)},${fmt(x-w)} ${fmt(y-h*.85)},${fmt(x-8)} ${fmt(y-h)}C${fmt(x+6)} ${fmt(y-h*.61)},${fmt(x+7)} ${fmt(y-h*.28)},${fmt(x)} ${y}Z"/><path d="M${fmt(x)} ${y+18}C${fmt(x+w+12)} ${y+6},${fmt(x+w+5)} ${y-18},${fmt(x+w-1)} ${y-30}C${fmt(x+9)} ${y-19},${fmt(x+4)} ${y+2},${fmt(x)} ${y+18}Z"/><path d="M${fmt(x)} ${y}Q${fmt(x-13)} ${fmt(y-h*.5)} ${fmt(x-8)} ${fmt(y-h)}M${fmt(x)} ${y+18}Q${fmt(x+18)} ${y-1} ${fmt(x+w-1)} ${y-30}" fill="none" stroke-width=".4"/>`;
 }
 const plant=`<path d="${stem}" fill="none" stroke-width="1.2"/><g stroke-width=".85" fill="url(#${p}metal)" fill-opacity=".13">${leaves}</g>`;
 return `<g stroke="url(#${p}metal)" opacity=".85">${plant}<g transform="translate(1000 0) scale(-1 1)">${plant}</g></g>`;
}
function verdant(c,a,p,t,f){
 const arch='M100 1110V554A400 400 0 0 1 900 554V1110Q500 1190 100 1110Z';
 return `<defs><clipPath id="${p}portal"><path d="${arch}"/></clipPath></defs>
 <rect width="1000" height="1400" fill="${t.deep}"/><rect width="1000" height="1400" fill="url(#${p}grain)" opacity=".5"/>
 <g clip-path="url(#${p}portal)">${artwork(a,{x:100,y:154,w:800,h:1036},c.focal)}${sheen(p,f)}</g>
 <path d="${arch}" fill="none" stroke="#302d1c" stroke-width="12"/><path d="${arch}" fill="none" stroke="url(#${p}metal)" stroke-width="4"/>
 <path d="M90 1108V552A410 410 0 0 1 910 552V1108M111 1106V554A389 389 0 0 1 889 554V1106" fill="none" stroke="url(#${p}foil)" stroke-width=".7" opacity=".75"/>
 ${botanical(p)}${brand(c,p,t)}
 <path d="M430 145Q461 145 500 126Q539 145 570 145M465 139Q500 101 535 139" fill="none" stroke="url(#${p}metal)" stroke-width="1.2"/>
 <rect x="170" y="1090" width="660" height="116" fill="url(#${p}bottom)" opacity=".8"/>
 <text data-text="name" x="500" y="1218" text-anchor="middle" class="italic" font-size="${nameSize(c.name,145)}" fill="url(#${p}letter)">${xml(c.name)}</text>
 <path d="M151 1267H245M755 1267H849" stroke="url(#${p}metal)" stroke-width=".8"/>
 <text data-text="edition" x="500" y="1282" text-anchor="middle" class="sans" font-size="40" letter-spacing="8" fill="${t.text}">${xml(c.edition)}</text>
 <text x="925" y="1282" text-anchor="end" class="sans" font-size="18" letter-spacing="2" fill="${t.muted}">${xml(c.number)}</text>
 ${credit(c,a,p,t)}`;
}
function compositionFor(c){return c.composition??(c.layout==='cutout'?'orbital':c.theme==='verdant'?'botanical':'pearl');}
function frontDesign(c,a,p,t,foil){
 return ({orbital:nocturne,pearl:tidelight,botanical:verdant}[compositionFor(c)])(c,a,p,t,foil);
}





function validateCard(c){
 if(!c||typeof c!=='object')throw new TypeError('A card definition is required');
 for(const key of ['id','name','edition','number','collection','material','artwork']){
  if(typeof c[key]!=='string'||!c[key].trim())throw new TypeError(`Missing ${key}`);
 }
 if(!/^[a-z][a-z0-9-]{0,47}$/.test(c.id))throw new Error('Card id must be a short lowercase slug');
 if(!THEMES[c.theme])throw new Error('Unknown material theme');
 if(!['cutout','fullart'].includes(c.layout))throw new Error('Unknown layout');
 if(c.composition&&!['orbital','pearl','botanical'].includes(c.composition))throw new Error('Unknown composition');
 if((compositionFor(c)==='orbital')!==(c.layout==='cutout'))throw new Error('Orbital composition requires a cutout; pearl and botanical require full art');
 if(c.name.length>24||c.edition.length>28||c.collection.length>32)throw new Error('Display text exceeds safe design limits');
 if(c.subtitle&&(typeof c.subtitle!=='string'||c.subtitle.length>40))throw new Error('Subtitle is too long');
 if(c.layout==='cutout'&&c.subject&&(Object.values(c.subject).some(n=>!Number.isFinite(n))||!(c.subject.width>0)||!(c.subject.height>0)))throw new Error('Invalid subject geometry');
 if(c.focal&&(!Array.isArray(c.focal)||c.focal.length!==2||c.focal.some(n=>!Number.isFinite(n)||n<0||n>1)))throw new Error('Focal points must be between 0 and 1');
 return c;
}
function emblem(p,x,y,r=22){return `<g transform="translate(${x} ${y})" fill="none" stroke="url(#${p}metal)"><path d="M0 ${-r}L${r} 0L0 ${r}L${-r} 0Z" stroke-width="1.1"/><path d="M0 ${-r*.7}V${r*.7}M${-r*.6} 0H${r*.6}" stroke-width=".8"/><path d="${star(0,0,r*.42)}" fill="url(#${p}metal)" stroke="none"/></g>`;}
function titleSize(s){return Math.min(150,650/(s.length*.47));}
/**
 * Portable renderer; browser and Node use this exact function.
 * assets = { art: data URI, artWidth, artHeight, artist, license, licenseUrl,
 *            sourceUrl, attributionUrl, fonts:{display:data URI,sans:data URI} }.
 * No network or raster drawing occurs here. All design layers are SVG.
 */
function renderCard(def,assets,{side='front',light=[.28,.2],foil=1,prefix}={}){
 const c=validateCard(def),t=THEMES[c.theme],p=prefix??`${c.id}-${side}-`,f=clamp(Number(foil)||0,0,1);
 if(!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(p))throw new Error('Invalid SVG id prefix');
 if(!/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(assets?.art??''))throw new Error('Embed a validated PNG, JPEG, or WebP data URI');
 if(!Number.isFinite(assets.artWidth)||!Number.isFinite(assets.artHeight)||assets.artWidth<=0||assets.artHeight<=0)throw new Error('Invalid artwork dimensions');
 for(const url of [assets.sourceUrl,assets.licenseUrl,assets.outputLicenseUrl??assets.licenseUrl,assets.attributionUrl??assets.sourceUrl]){if(new URL(url).protocol!=='https:')throw new Error('Attribution URLs must use HTTPS');}
 if(!['display','sans',...(assets.fonts?.italic?['italic']:[])].every(k=>/^data:font\/ttf;base64,[A-Za-z0-9+/=]+$/.test(assets.fonts?.[k]??'')))throw new Error('Embed the display and sans fonts');
 if(!Array.isArray(light)||light.length!==2||light.some(n=>!Number.isFinite(n)))throw new Error('Invalid light');
 if(!['front','back'].includes(side))throw new Error('Unknown card side');
 const safeLight=light.map(n=>clamp(n,0,1));
 const fonts=`@font-face{font-family:NightDisplay;src:url('${assets.fonts.display}') format('truetype');font-weight:500}@font-face{font-family:NightSans;src:url('${assets.fonts.sans}') format('truetype');font-weight:500}@font-face{font-family:NightItalic;src:url('${assets.fonts.italic??assets.fonts.display}') format('truetype');font-weight:500}`;
 const common=`<defs><style>${fonts}text{font-kerning:normal}.sans{font-family:NightSans,Arial,sans-serif;font-weight:500}.display{font-family:NightDisplay,Georgia,serif;font-weight:500}.italic{font-family:NightItalic,Georgia,serif;font-weight:500}</style>
 <clipPath id="${p}clip"><rect x="3" y="3" width="994" height="1394" rx="40"/></clipPath>
 ${materialDefs(p,t,{light:safeLight,foil:f})}
 </defs>`;
 const attribution=`${assets.artist} · ${assets.license}`;
 const meta=xml(JSON.stringify({renderer:'NIGHT Collectibles 1.1',card:c.id,artwork:c.artwork,artist:assets.artist,license:assets.license,licenseUrl:assets.licenseUrl,source:assets.sourceUrl,changes:`Artwork cropped/scaled; SVG frame, typography and foil added. Card image license: ${assets.outputLicense??assets.license}.`,side}));
 const header=`<text x="77" y="107" class="sans" font-size="25" letter-spacing="7" fill="${t.text}">NIGHT</text><text x="909" y="107" text-anchor="end" class="sans" font-size="18" letter-spacing="3" fill="${t.text}">${xml(c.collection)}</text><path d="M73 143H927" fill="none" stroke="url(#${p}metal)" stroke-width=".8" opacity=".6"/>`;
 let content;
 if(side==='front'){
  content=frontDesign(c,assets,p,t,f);
 }else{
  content=`${backdrop(p,t,c.seed)}<rect width="1000" height="1400" fill="${t.deep}" opacity=".72"/>
   ${header}
   <g fill="none" stroke="url(#${p}metal)" stroke-width=".8" opacity=".6">${rosette(500,510,205,24)}</g>
   ${emblem(p,500,510,108)}
   <text x="500" y="827" text-anchor="middle" class="display" font-size="${Math.min(87,800/((c.name.length+c.edition.length+3)*.48))}" fill="${t.text}">${xml(c.name)} / ${xml(c.edition.charAt(0)+c.edition.slice(1).toLowerCase())}</text>
   <text x="500" y="881" text-anchor="middle" class="sans" font-size="18" letter-spacing="4" fill="${t.muted}">${xml(c.collection)} · ${xml(c.number)}</text>
   <path d="M160 944H840" stroke="url(#${p}metal)" stroke-width=".8"/>
   <text x="500" y="1000" text-anchor="middle" class="sans" font-size="22" fill="${t.text}">Artwork by ${xml(assets.artist)}</text>
   <text x="500" y="1043" text-anchor="middle" class="sans" font-size="19" fill="${t.muted}">${xml(assets.title??'Kiki illustration')}</text>
   <text x="500" y="1084" text-anchor="middle" class="sans" font-size="18" fill="${t.muted}">Cropped and framed by NIGHT · ${xml(assets.outputLicense??assets.license)}</text>
   <a href="${xml(assets.outputLicenseUrl??assets.licenseUrl)}"><text x="500" y="1122" text-anchor="middle" class="sans" font-size="16" fill="${t.muted}">${xml((assets.outputLicenseUrl??assets.licenseUrl).replace('https://',''))}</text></a>
   <a href="${xml(assets.sourceUrl)}"><text x="500" y="1156" text-anchor="middle" class="sans" font-size="16" fill="${t.muted}">Source artwork · ${xml(new URL(assets.sourceUrl).hostname)}</text></a>
   <text x="500" y="1294" text-anchor="middle" class="sans" font-size="17" letter-spacing="5" fill="${t.muted}">NIGHT · ARTIST EDITIONS</text>
   <rect width="1000" height="1400" fill="url(#${p}spec)" opacity="${f*.25}"/>`;
 }
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}" viewBox="0 0 ${WIDTH} ${HEIGHT}" role="img" aria-labelledby="${p}title ${p}desc">
 <title id="${p}title">${xml(c.name)} — ${xml(c.edition)} (${side})</title><desc id="${p}desc">${xml(c.description)} Illustration by ${xml(assets.artist)}, ${xml(assets.license)}. A collectible display card.</desc><metadata>${meta}</metadata>
 ${common}<g clip-path="url(#${p}clip)">${content}${engravedFrame(p,t,{variant:compositionFor(c)==='pearl'?'tidelight':c.theme})}</g></svg>`;
}

window.Night={renderCard,setCardLight,WIDTH,HEIGHT};})();
export const renderCard = window.Night.renderCard;
export const CARD_WIDTH = window.Night.WIDTH;
export const CARD_HEIGHT = window.Night.HEIGHT;
