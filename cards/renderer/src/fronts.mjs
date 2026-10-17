import {xml,cover,guilloche,star,fmt} from './geometry.mjs';
import {backdrop,foilContours} from './materials.mjs';
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
export function compositionFor(c){return c.composition??(c.layout==='cutout'?'orbital':c.theme==='verdant'?'botanical':'pearl');}
export function frontDesign(c,a,p,t,foil){
 return ({orbital:nocturne,pearl:tidelight,botanical:verdant}[compositionFor(c)])(c,a,p,t,foil);
}
