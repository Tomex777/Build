/** NIGHT Atelier: ten independently composed, code-rendered collectible fronts.
 * The supplied character illustration is the only raster layer. */
import {xml,fmt,rng} from './geometry.mjs';

export const ATELIER_VERSION='1.0.0';
export const ATELIER_WIDTH=1000, ATELIER_HEIGHT=1600;
export const ATELIER_DESIGNS=Object.freeze([
 {id:'obsidian',number:'01',name:'Obsidian',material:'BLACK LACQUER / COPPER FOIL',description:'Open full-art, black lacquer and floating copper rails.'},
 {id:'gilt',number:'02',name:'Gilt',material:'CHAMPAGNE / INTAGLIO GOLD',description:'Warm ivory, engraved champagne metal and a museum title plaque.'},
 {id:'spectra',number:'03',name:'Spectra',material:'OPAL GLASS / PRISMATIC FOIL',description:'Pearlescent glass with spectral facets and an iridescent edge.'},
 {id:'monograph',number:'04',name:'Monograph',material:'COTTON STOCK / DEBOSSED INK',description:'A restrained editorial edition with a vertical typographic spine.'},
 {id:'exoskeleton',number:'05',name:'Exoskeleton',material:'TITANIUM / MACHINED CHROME',description:'Asymmetric industrial rails, bevels and a cut metal nameplate.'},
 {id:'vermilion',number:'06',name:'Vermilion',material:'SCARLET LACQUER / GOLD LEAF',description:'Borderless scarlet lacquer with gestural ink engraving.'},
 {id:'archive',number:'07',name:'Archive',material:'SMOKED GLASS / SATIN SILVER',description:'A floating illustration, translucent case and archival label.'},
 {id:'cutline',number:'08',name:'Cutline',material:'SPOT INK / BRUSHED PALLADIUM',description:'Graphic diagonal cuts, black ink and an electric pink foil edge.'},
 {id:'palatine',number:'09',name:'Palatine',material:'ONYX / ENGRAVED BRASS',description:'A gold title above the illustration, architectural fluting and a stepped Art Deco frame.'},
 {id:'stratum',number:'10',name:'Stratum',material:'MINERAL GLASS / SILVER ETCH',description:'Layered mineral contours, deep teal and an etched silver title.'}
]);

const R=(x,y,w,h,fill,extra='')=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}" ${extra}/>`;
const P=(d,stroke,width=1,extra='')=>`<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${width}" ${extra}/>`;
const T=(txt,x,y,size=20,fill='#eee',extra='')=>`<text x="${x}" y="${y}" font-size="${size}" fill="${fill}" class="s" ${extra}>${xml(txt)}</text>`;
const corners=(p,{inset=26,size=72,color='silver',width=2}={})=>[[inset,inset,1,1],[1000-inset,inset,-1,1],[inset,1600-inset,1,-1],[1000-inset,1600-inset,-1,-1]].map(([x,y,sx,sy])=>`<g transform="translate(${x} ${y}) scale(${sx} ${sy})">${P(`M0 ${size}V0H${size}`,`url(#${p}${color})`,width)}${P(`M8 ${size-19}V8H${size-19}`,`url(#${p}${color})`,.6)}</g>`).join('');
const border=(p,inset=16,r=24,metal='silver',w=2)=>R(inset,inset,1000-2*inset,1600-2*inset,'none',`rx="${r}" stroke="url(#${p}${metal})" stroke-width="${w}"`);

function engraving(p,{kind='flow',color='gold',opacity=.25,count=65}={}){
 let s='';
 for(let i=0;i<count;i++){
  const q=i/(count-1);let d;
  if(kind==='diagonal')d=`M${-400+i*22} -40L${550+i*22} 1640`;
  else if(kind==='flute')d=`M${50+i*14} -30V${1030+Math.abs(i-count/2)*5}`;
  else if(kind==='contour')d=`M-90 ${250+i*18}C${180+i*3} ${-230+i*25},${730-i*4} ${1180-i*5},1120 ${660+i*12}`;
  else d=`M-90 ${540+i*13}C${240+i*5} ${-80+i*9},${470-i*3} ${1440-i*3},1110 ${310+i*19}`;
  s+=P(d,`url(#${p}${color})`,i%9===0?1.25:.5,`opacity="${i%9===0?opacity*1.7:opacity}"`);
 }
 return `<g data-material="engraving">${s}</g>`;
}
function ticks(p,x,y,w,color='silver',n=50){return `<g stroke="url(#${p}${color})" opacity=".65">${Array.from({length:n},(_,i)=>P(`M${fmt(x+i*w/(n-1))} ${y}v${i%5===0?12:4}`,`url(#${p}${color})`,.65)).join('')}</g>`;}
function specks(p,color='#c8e7ee',seed=7){let s='',rand=rng(seed);for(let i=0;i<85;i++){let x=32+rand()*936,y=120+rand()*1120;s+=`<circle cx="${fmt(x)}" cy="${fmt(y)}" r="${fmt(.4+rand()*.8)}" opacity="${fmt(.1+rand()*.25)}"/>`;}return `<g fill="${color}">${s}</g>`;}

function defs(p,a,c){
 const font=`@font-face{font-family:AtelierSerif;src:url('${a.fonts.display}')}@font-face{font-family:AtelierSans;src:url('${a.fonts.sans}')}@font-face{font-family:AtelierItalic;src:url('${a.fonts.italic}')}text{font-kerning:normal}.s{font-family:AtelierSans,Arial,sans-serif;font-weight:500}.d{font-family:AtelierSerif,Georgia,serif;font-weight:500}.i{font-family:AtelierItalic,Georgia,serif;font-weight:500}`;
 return `<style>${font}</style>
 <clipPath id="${p}clip">${R(3,3,994,1594,'white','rx="28"')}</clipPath>
 <linearGradient id="${p}black" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="#0e1920"/><stop offset=".35" stop-color="#0c1015"/><stop offset="1" stop-color="#02080b"/></linearGradient>
 <linearGradient id="${p}cream" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#ded8c6"/><stop offset=".37" stop-color="#fbf5e6"/><stop offset=".7" stop-color="#e7dec8"/><stop offset="1" stop-color="#f6eedc"/></linearGradient>
 <linearGradient id="${p}opal" x1="0" y1="0" x2="1" y2=".9"><stop stop-color="#f0e9ed"/><stop offset=".26" stop-color="#c4e2e0"/><stop offset=".45" stop-color="#f5f0d6"/><stop offset=".68" stop-color="#d4cef0"/><stop offset=".83" stop-color="#c4dfe3"/><stop offset="1" stop-color="#f9efdf"/></linearGradient>
 <linearGradient id="${p}red" x1="0" y1="1" x2=".8" y2="0"><stop stop-color="#1b070d"/><stop offset=".4" stop-color="#501017"/><stop offset=".76" stop-color="#b62f2e"/><stop offset="1" stop-color="#421016"/></linearGradient>
 <linearGradient id="${p}teal" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#092d39"/><stop offset=".36" stop-color="#1c535c"/><stop offset=".72" stop-color="#061e2b"/><stop offset="1" stop-color="#020e1b"/></linearGradient>
 <linearGradient id="${p}gold" x1="0" y1="0" x2="1" y2=".75"><stop stop-color="#84643a"/><stop offset=".13" stop-color="#e8c68c"/><stop offset=".22" stop-color="#fff1c2"/><stop offset=".29" stop-color="#aa763c"/><stop offset=".48" stop-color="#e9cd96"/><stop offset=".51" stop-color="#fff7d3"/><stop offset=".56" stop-color="#b17f49"/><stop offset=".8" stop-color="#d4b178"/><stop offset="1" stop-color="#fcdfaa"/></linearGradient>
 <linearGradient id="${p}copper" x1="0" y1="0" x2="1" y2=".8"><stop stop-color="#694434"/><stop offset=".2" stop-color="#f6d2b0"/><stop offset=".35" stop-color="#a56843"/><stop offset=".48" stop-color="#d89569"/><stop offset=".52" stop-color="#ffedd0"/><stop offset=".72" stop-color="#89533c"/><stop offset="1" stop-color="#e7b18b"/></linearGradient>
 <linearGradient id="${p}silver" x1="0" y1="0" x2="1" y2=".7"><stop stop-color="#3e545d"/><stop offset=".12" stop-color="#adbfbd"/><stop offset=".21" stop-color="#fcfbe4"/><stop offset=".27" stop-color="#4a707c"/><stop offset=".47" stop-color="#a5bec1"/><stop offset=".51" stop-color="#f5ffff"/><stop offset=".57" stop-color="#718c91"/><stop offset=".8" stop-color="#364c5b"/><stop offset="1" stop-color="#d5ded8"/></linearGradient>
 <linearGradient id="${p}foil" x1="0" y1="0" x2=".95" y2=".4"><stop stop-color="#ffe4bb"/><stop offset=".16" stop-color="#ecabd1"/><stop offset=".31" stop-color="#acaef2"/><stop offset=".46" stop-color="#9ce3f0"/><stop offset=".55" stop-color="#f0ffdd"/><stop offset=".69" stop-color="#f8dbb8"/><stop offset=".83" stop-color="#dcbfe9"/><stop offset="1" stop-color="#bdeef2"/></linearGradient>
 <linearGradient id="${p}title" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#fff8e5"/><stop offset=".42" stop-color="#dfddd1"/><stop offset=".46" stop-color="#7f959b"/><stop offset=".5" stop-color="#eff5e9"/><stop offset="1" stop-color="#f9edd8"/></linearGradient>
 <linearGradient id="${p}blackFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#030b10" stop-opacity="0"/><stop offset=".6" stop-color="#030b10" stop-opacity=".92"/><stop offset="1" stop-color="#030b10"/></linearGradient>
 <linearGradient id="${p}artFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="white"/><stop offset=".72" stop-color="white"/><stop offset=".9" stop-color="#ddd"/><stop offset="1" stop-color="black"/></linearGradient>
 <mask id="${p}bodyFade">${R(-250,-250,1500,1600,`url(#${p}artFade)`)}</mask>
 <pattern id="${p}grain" width="4" height="7" patternUnits="userSpaceOnUse"><path d="M.5 0v7" stroke="#fff" stroke-width=".24" opacity=".07"/><path d="M2 2h.2m.7 3h.2" stroke="#000" stroke-width=".6" opacity=".12"/></pattern>
 <pattern id="${p}satin" width="5" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(38)"><path d="M.5 0v6" stroke="#f0f5d9" stroke-width=".4" opacity=".22"/></pattern>
 <pattern id="${p}micro" width="12" height="12" patternUnits="userSpaceOnUse"><path d="M6 2l4 4-4 4-4-4Z" fill="none" stroke="#fff" stroke-width=".25" opacity=".2"/></pattern>
 <radialGradient id="${p}sheen" cx=".17" cy=".12" r="1"><stop stop-color="#fff9df" stop-opacity=".3"/><stop offset=".5" stop-color="#fff" stop-opacity="0"/><stop offset=".8" stop-color="#98dbe5" stop-opacity=".08"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
 <linearGradient id="${p}glass" x1="0" y1="0" x2="1" y2=".8"><stop stop-color="#effcff" stop-opacity=".14"/><stop offset=".39" stop-color="#fff" stop-opacity="0"/><stop offset=".4" stop-color="#f7f3e4" stop-opacity=".2"/><stop offset=".44" stop-color="#fff" stop-opacity=".03"/><stop offset="1" stop-color="#c7f5f1" stop-opacity=".12"/></linearGradient>
 <filter id="${p}shadow" x="-.3" y="-.2" width="1.6" height="1.5"><feDropShadow dx="12" dy="23" stdDeviation="18" flood-color="#000b16" flood-opacity=".62"/><feDropShadow dx="2" dy="4" stdDeviation="2" flood-color="#01070c" flood-opacity=".65"/></filter>
 <filter id="${p}rim" x="-.1" y="-.1" width="1.2" height="1.2"><feMorphology in="SourceAlpha" operator="dilate" radius=".6" result="d"/><feComposite in="d" in2="SourceAlpha" operator="out" result="e"/><feFlood flood-color="#f3e4c5"/><feComposite in2="e" operator="in"/></filter>
 <filter id="${p}relief" x="-.1" y="-.1" width="1.2" height="1.3"><feDropShadow dx="0" dy="1.3" stdDeviation=".4" flood-color="#000" flood-opacity=".7"/></filter>`;
}

function art(p,a,{x=-62,y=114,w=1125,shadow=true,fade=true,opacity=1}={}){
 const im=`<use href="#${p}characterSource"/>`;
 const contentHeight=a.croppedArtworkDimensions?.[1]??a.artHeight;
 const bottom=y+w*contentHeight/a.artWidth;
 return `<defs><image id="${p}characterSource" href="${a.art}" x="${x}" y="${y}" width="${w}" height="${fmt(w*a.artHeight/a.artWidth)}" preserveAspectRatio="xMidYMid meet"/><linearGradient id="${p}edgeFade" gradientUnits="userSpaceOnUse" x1="0" y1="${fmt(bottom-150)}" x2="0" y2="${fmt(bottom)}"><stop stop-color="white"/><stop offset=".18" stop-color="white"/><stop offset=".84" stop-color="#555"/><stop offset="1" stop-color="black"/></linearGradient><mask id="${p}fittedBodyFade">${R(-250,-250,1500,2100,`url(#${p}edgeFade)`)}</mask></defs><g data-layer="character" ${fade?`mask="url(#${p}fittedBodyFade)"`:''} opacity="${opacity}">${shadow?`<g filter="url(#${p}shadow)">${im}</g><g filter="url(#${p}rim)" opacity=".4">${im}</g>`:''}${im}</g>`;
}
function label(p,c,{x=65,y=1370,size=143,ink='title',align='start',cls='d'}={}){
 const fill=['title','gold','silver','foil','copper'].includes(ink)?`url(#${p}${ink})`:ink;
 const maxWidth=align==='middle'?860:870;
 const fs=Math.min(size,maxWidth/(c.name.length*.67));
 return `<text data-text="name" x="${x}" y="${y}" font-size="${fmt(fs)}" fill="${fill}" class="${cls}" text-anchor="${align}" ${ink==='title'||ink==='gold'?'filter="url(#'+p+'relief)"':''}>${xml(c.name.toUpperCase())}</text>`;
}
function header(p,{color='#e0e5dc',small='ATELIER EDITIONS',y=89,x=63,right=936}={}){
 return T('NIGHT',x,y,23,color,'letter-spacing="8"')+T(small,right,y,11,color,'text-anchor="end" letter-spacing="2.8"');
}
function foot(p,a,d,{color='#8da3a7',y=1539,x=65,right=935}={}){
 return T(d.material,x,y-29,10,color,'letter-spacing="1.5"')+T(`${d.number} / NIGHT ATELIER`,right,y-29,10,color,'letter-spacing="1.5" text-anchor="end"')+T(`${a.artist} · ${a.license}`,x,y,8.7,color);
}

function obsidian(p,c,a,d){return R(0,0,1000,1600,`url(#${p}black)`)+R(0,0,1000,1600,`url(#${p}grain)`)+engraving(p,{kind:'diagonal',color:'copper',opacity:.08,count:56})+
 P('M26 366V27H312M688 27H974V366M26 1120V1573H170M830 1573H974V1120',`url(#${p}copper)`,2.5)+P('M43 281V44H275M725 44H957V281',`url(#${p}copper)`,.5)+specks(p,'#f7cf94')+
 art(p,a,{x:-160,y:-25,w:1320})+R(0,1090,1000,510,`url(#${p}blackFade)`)+
 P('M65 1226H188M812 1226H935',`url(#${p}copper)`,.8)+label(p,c,{y:1385,size:159,ink:'copper'})+
 T(c.subtitle,71,1436,16,'#e5d9c9','letter-spacing="5"')+header(p,{color:'#e9decf'})+foot(p,a,d,{color:'#ae9b87'})+border(p,8,24,'copper',.8);}

function gilt(p,c,a,d){return R(0,0,1000,1600,`url(#${p}cream)`)+R(22,22,956,1556,`url(#${p}satin)`,'rx="21"')+
 R(38,139,924,1127,'#ba9c63','rx="4"')+R(46,147,908,1111,'#292c2b')+R(55,156,890,1093,`url(#${p}gold)`)+R(60,161,880,1083,'#3d3931')+
 engraving(p,{color:'gold',kind:'flow',opacity:.12})+art(p,a,{x:-50,y:157,w:1100})+
 P('M63 1197V1243H249M937 1197V1243H751',`url(#${p}gold)`,3)+
 R(83,1279,834,191,'#d4bb8c')+R(87,1283,826,183,`url(#${p}cream)`)+R(94,1290,812,169,'none',`stroke="#a88d5e" stroke-width=".6"`)+
 label(p,c,{x:500,y:1398,size:139,ink:'#332b25',align:'middle'})+T(c.subtitle,500,1440,13,'#766349','text-anchor="middle" letter-spacing="5"')+
 header(p,{color:'#493f31'})+foot(p,a,d,{color:'#76674e'})+border(p,16,16,'gold',3)+corners(p,{inset:25,size:61,color:'gold',width:.8});}

function spectra(p,c,a,d){return R(0,0,1000,1600,`url(#${p}opal)`)+R(0,0,1000,1600,`url(#${p}micro)`)+
 `<path d="M0 640L1000 76V292L0 856Z" fill="url(#${p}foil)" opacity=".32"/><path d="M0 915L1000 352V813L0 1376Z" fill="#fff" opacity=".22"/>`+
 engraving(p,{kind:'diagonal',color:'silver',opacity:.12,count:72})+
 `<path d="M72 130H928L964 166V1130L844 1250H72L36 1214V166Z" fill="url(#${p}glass)" stroke="url(#${p}silver)" stroke-width="7"/>`+
 `<path d="M81 143H919L951 175V1125L838 1237H81L49 1205V175Z" fill="none" stroke="url(#${p}foil)" stroke-width="2"/>`+
 art(p,a,{x:-66,y:133,w:1135})+R(36,1070,928,195,`url(#${p}glass)`)+
 P('M70 1283H930',`url(#${p}silver)`,.8)+label(p,c,{x:500,y:1402,align:'middle',ink:'#183c50',size:150})+
 T(c.subtitle,500,1450,16,'#3d6070','text-anchor="middle" letter-spacing="4.6"')+header(p,{color:'#2d4e5f'})+foot(p,a,d,{color:'#486477'})+
 border(p,10,23,'silver',10)+border(p,20,18,'foil',2);}

function monograph(p,c,a,d){return R(0,0,1000,1600,'#f0eadc')+R(0,0,1000,1600,`url(#${p}grain)`)+
 R(184,139,775,1136,'#c5ba9f')+R(191,146,761,1122,'#2d302d')+
 R(38,139,117,1144,'none','stroke="#39382f" stroke-width=".8"')+
 `<text data-text="vertical-name" x="0" y="0" transform="translate(124 1241) rotate(-90)" class="d" font-size="${Math.min(112,950/(c.name.length*.72))}" fill="#302e27" letter-spacing="${Math.min(12,25/c.name.length)}">${xml(c.name.toUpperCase())}</text>`+
 art(p,a,{x:85,y:144,w:1085})+P('M184 139H959V1275H184',`url(#${p}gold)`,1)+
 T(c.subtitle,45,1380,35,'#2b2c28','letter-spacing="3.3"')+P('M46 1411H954','#625e4f',.7)+
 T('NIGHT',45,1467,17,'#383b33','letter-spacing="6"')+T('THE MONOGRAPH EDITION',955,1467,12,'#756f61','text-anchor="end" letter-spacing="2.5"')+
 header(p,{color:'#32382f',small:'COLLECTED WORKS',x:46,right:954})+foot(p,a,d,{color:'#827967',x:46,right:954})+border(p,10,20,'gold',.8);}

function exoskeleton(p,c,a,d){
 let bolts='';for(const[x,y]of[[35,45],[965,45],[35,1555],[965,1555]])bolts+=`<g transform="translate(${x} ${y})"><circle r="5" fill="#354751" stroke="#a1b1b4" stroke-width=".8"/>${P('M-2 -2L2 2','#070f14',1.3)}</g>`;
 return R(0,0,1000,1600,'#0a1119')+R(0,0,1000,1600,`url(#${p}satin)`)+
 `<path d="M85 112H836L910 186V1051L833 1128H85Z" fill="#1c2d38" stroke="#4f6269" stroke-width="2"/>`+
 engraving(p,{kind:'diagonal',color:'silver',opacity:.08,count:65})+
 `<path d="M18 18H982V204H955V71H813L783 41H62V338H18Z" fill="url(#${p}silver)"/><path d="M18 386H53V1450L113 1510H946V1181H982V1582H18Z" fill="url(#${p}silver)"/>`+
 `<path d="M942 257H977V1065L944 1098H921V319Z" fill="url(#${p}silver)"/>`+
 P('M31 403V1461L103 1533H928',`url(#${p}foil)`,1)+P('M940 383V977',`url(#${p}copper)`,4)+
 art(p,a,{x:-99,y:114,w:1180})+
 `<path d="M65 1260H819L935 1144V1438H65Z" fill="url(#${p}silver)"/><path d="M73 1268H821L926 1163V1430H73Z" fill="#111f2a"/>`+
 label(p,c,{x:96,y:1386,size:146,ink:'silver',cls:'s'})+T(c.subtitle,100,1412,14,'#afc4c9','letter-spacing="4.8"')+
 header(p,{x:84,right:916,color:'#bdccd0',y:91})+foot(p,a,d,{x:100,right:916,color:'#8aa2af',y:1488})+bolts+ticks(p,77,115,154,'copper',24);
}

function vermilion(p,c,a,d){
 let brush='';for(let i=0;i<48;i++)brush+=P(`M${-170+i*3} ${1140+i*2.8}C${240+i*5} ${250+i*4},${650+i*3} ${990+i*2},${1180+i*2} ${0+i*9}`,i%7===0?`url(#${p}gold)`:'#190c12',i%7===0?.9:2.2,`opacity="${i%7===0?.55:.4}"`);
 return R(0,0,1000,1600,`url(#${p}red)`)+R(0,0,1000,1600,`url(#${p}grain)`)+brush+
 `<path d="M-100 1430C121 898 245 362 969 18" fill="none" stroke="#e3684a" stroke-width="75" opacity=".1"/>`+
 art(p,a,{x:-90,y:-15,w:1200})+R(0,1060,1000,540,`url(#${p}blackFade)`)+
 P('M61 1290L929 1218',`url(#${p}gold)`,1.4)+label(p,c,{x:67,y:1384,size:175,ink:'#f3dfc2',cls:'i'})+
 T(c.subtitle,76,1441,15,'#e8b9a1','letter-spacing="5.5"')+header(p,{color:'#f7d7b6',small:'ATELIER / VERMILION'})+
 foot(p,a,d,{color:'#b18a78'})+P('M26 140V26H185M815 26H974V140M26 1460V1574H185M815 1574H974V1460',`url(#${p}gold)`,1.4);}

function archive(p,c,a,d){return R(0,0,1000,1600,'#172b2c')+R(0,0,1000,1600,`url(#${p}grain)`)+
 R(40,39,920,1522,'#789895','rx="29" opacity=".19"')+R(50,49,900,1502,`url(#${p}glass)`,'rx="23"')+
 engraving(p,{kind:'diagonal',color:'silver',opacity:.075,count:70})+
 R(78,137,844,1094,'#223b3a','rx="5" stroke="#9cb9b1" stroke-width=".6"')+
 R(94,153,812,1062,'none','stroke="#83a5a0" stroke-width=".4"')+ticks(p,96,175,808,'silver',105)+
 art(p,a,{x:-40,y:150,w:1100})+
 R(82,1248,836,244,'#d7e1d7','rx="3"')+R(88,1254,824,232,`url(#${p}grain)`)+
 T('NIGHT / ARCHIVE',109,1290,13,'#4d645f','letter-spacing="2.8"')+T('07',885,1290,13,'#4d645f','text-anchor="end" letter-spacing="2"')+
 label(p,c,{x:107,y:1400,size:118,ink:'#233b37',cls:'s'})+P('M109 1420H886','#8ca39a',.6)+
 T(c.subtitle,110,1460,13,'#4f6a61','letter-spacing="4"')+
 header(p,{x:88,right:911,color:'#c0d3c7',small:'ART COLLECTION'})+foot(p,a,d,{x:88,right:912,color:'#9bb4a8',y:1539})+
 border(p,17,27,'silver',4)+border(p,31,24,'silver',.5)+P('M59 368V70H362M941 1232V1530H638','#e1f8ee',2,'opacity=".6"');}

function cutline(p,c,a,d){return R(0,0,1000,1600,'#deddd0')+R(0,0,1000,1600,`url(#${p}grain)`)+
 `<path d="M483 0H1000V1600H0V1012Z" fill="#080e14"/><path d="M975 0H1000V866L138 1600H95L975 850Z" fill="#f682a8"/>`+
 T('N',-40,1099,580,'#2d3434','opacity=".08"')+
 `<path d="M982 112L1000 94V357L982 374Z" fill="url(#${p}silver)"/>`+
 art(p,a,{x:-99,y:-52,w:1250})+
 `<path d="M0 1315L1000 1130V1600H0Z" fill="#0a1015"/>`+
 P('M0 1306L1000 1121',`url(#${p}silver)`,2)+P('M0 1320L1000 1135','#ec8aaa',.65)+
 label(p,c,{x:57,y:1400,size:163,ink:'#f1efe1',cls:'s'})+T(c.subtitle,65,1455,15,'#c6c8c2','letter-spacing="5"')+
 header(p,{color:'#202a2d',small:'',x:45,y:91})+T('08',951,89,13,'#dcddcd','text-anchor="end" letter-spacing="4"')+
 foot(p,a,d,{color:'#9ba5a9'})+border(p,8,23,'silver',1);}

function palatine(p,c,a,d){
 let flutes='';for(let i=0;i<20;i++){const x=70+i*9;flutes+=P(`M${x} 1440V${350+i*8}L${348+i*4} 325M${1000-x} 1440V${350+i*8}L${652-i*4} 325`,`url(#${p}gold)`,i%4===0?2:.65,`opacity="${i%4===0?.7:.32}"`);}
 return R(0,0,1000,1600,'#11191a')+R(0,0,1000,1600,`url(#${p}grain)`)+flutes+
 `<path d="M28 86H55V54H86V27H914V54H945V86H973V1514H945V1546H914V1573H86V1546H55V1514H28Z" fill="none" stroke="url(#${p}gold)" stroke-width="7"/>`+
 `<path d="M42 97H68V67H98V41H902V67H932V97H959V1502H932V1533H902V1559H98V1533H68V1502H42Z" fill="none" stroke="url(#${p}gold)" stroke-width=".65"/>`+
 P('M70 335H930',`url(#${p}gold)`,.75)+art(p,a,{x:-47,y:335,w:1090})+R(55,1380,890,185,`url(#${p}blackFade)`)+
 P('M95 136H223V123H777V136H905',`url(#${p}gold)`,1)+
 label(p,c,{x:500,y:258,size:151,ink:'gold',align:'middle'})+T(c.subtitle,500,302,14,'#d9c093','text-anchor="middle" letter-spacing="4.7"')+T('PALATINE',500,1450,16,'#d9c093','text-anchor="middle" letter-spacing="6"')+
 P('M95 1481H905',`url(#${p}gold)`,.6)+header(p,{color:'#f1d19c',x:93,right:907})+foot(p,a,d,{color:'#ad9874',x:93,right:907,y:1531});}

function stratum(p,c,a,d){return R(0,0,1000,1600,`url(#${p}teal)`)+R(0,0,1000,1600,`url(#${p}grain)`)+
 `<path d="M0 310C440 -170 430 996 1000 603V1091C540 1398 357 139 0 698Z" fill="url(#${p}foil)" opacity=".1"/>`+
 engraving(p,{kind:'contour',color:'foil',opacity:.25,count:76})+
 `<path d="M28 383V69L69 28H930L972 70V1220" fill="none" stroke="url(#${p}silver)" stroke-width="5"/><path d="M28 1064V1520L80 1572H931L972 1531V1320" fill="none" stroke="url(#${p}silver)" stroke-width="5"/>`+
 P('M36 111H964',`url(#${p}silver)`,.8)+art(p,a,{x:-63,y:111,w:1130})+
 `<path d="M0 1245L1000 1087V1600H0Z" fill="#061724" opacity=".92"/>`+
 P('M-60 1261L1060 1084',`url(#${p}foil)`,1.6)+P('M-60 1272L1060 1095',`url(#${p}silver)`,.5)+
 label(p,c,{x:68,y:1409,size:160,ink:'title'})+T(c.subtitle,75,1460,15,'#c0dad6','letter-spacing="5.4"')+
 header(p,{color:'#d6e9df'})+foot(p,a,d,{color:'#88b0b5',y:1543})+border(p,10,23,'silver',.7);}

const renderers={obsidian,gilt,spectra,monograph,exoskeleton,vermilion,archive,cutline,palatine,stratum};

/** Character data is independent of design. All art transforms preserve aspect ratio. */
export function renderAtelierCard(character,assets,{design='obsidian',prefix}={}){
 const d=ATELIER_DESIGNS.find(d=>d.id===design);if(!d)throw new Error('Unknown Atelier design');
 if(typeof character?.name!=='string'||!character.name.trim()||character.name.length>36)throw new Error('A character name of 1–36 characters is required');
 if(!/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(assets.art??''))throw new Error('Embed a supplied raster illustration');
 if(!['display','sans','italic'].every(k=>/^data:font\/ttf;base64,[A-Za-z0-9+/=]+$/.test(assets.fonts?.[k]??'')))throw new Error('Embed the licensed font files');
 if(!Number.isFinite(assets.artWidth)||!Number.isFinite(assets.artHeight)||assets.artWidth<=0||assets.artHeight<=0)throw new Error('Invalid artwork dimensions');
 const p=prefix??`atelier-${d.id}-`;if(!/^[A-Za-z][A-Za-z0-9_-]*$/.test(p))throw new Error('Invalid SVG id prefix');
 const c={name:character.name.trim(),subtitle:character.subtitle??character.name.toUpperCase()};
 const meta={renderer:`NIGHT Atelier ${ATELIER_VERSION}`,design:d,character:c.name,artwork:assets.title,artist:assets.artist,source:assets.sourceUrl,rights:assets.rights??assets.license,nativeArtwork:[assets.artWidth,assets.artHeight],construction:'Programmatic SVG; supplied raster illustration; no generated imagery.'};
 return `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1600" viewBox="0 0 1000 1600" role="img" aria-labelledby="${p}accessibleTitle"><title id="${p}accessibleTitle">${xml(c.name)} / NIGHT ${xml(d.name)}</title><metadata>${xml(JSON.stringify(meta))}</metadata><defs>${defs(p,assets,c)}</defs><g clip-path="url(#${p}clip)">${renderers[d.id](p,c,assets,d)}${R(0,0,1000,1600,`url(#${p}sheen)`,'pointer-events="none" opacity=".22"')}</g></svg>`;
}
