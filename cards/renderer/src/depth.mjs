import {xml,fmt,rng,star,rosette,clamp} from './geometry.mjs';
import {THEMES,materialDefs,setCardLight} from './materials.mjs';
import {validateCard} from './index.mjs';

export const DEPTH_VERSION='2.0';
export const DEPTH_EDITIONS=['nocturne','tidelight'];

/** Small physical displacements, applied equally to still exports and the viewer. */
export function depthPose(light=[.28,.2]){
 const x=(clamp(light[0],0,1)-.28),y=(clamp(light[1],0,1)-.2);
 return {rear:[-x*7,-y*5],subject:[x*13,y*8],front:[x*20,y*11]};
}
export function setDepthLight(svg,light){
 setCardLight(svg,light);
 for(const [layer,xy]of Object.entries(depthPose(light))){
  for(const node of svg.querySelectorAll(`[data-depth="${layer}"]`))node.setAttribute('transform',`translate(${fmt(xy[0])} ${fmt(xy[1])})`);
 }
 const c=[light[0]*1000,light[1]*1400];
 const lightNode=svg.querySelector('[id$="lensLight"]');
 if(lightNode){lightNode.setAttribute('cx',fmt(c[0]));lightNode.setAttribute('cy',fmt(c[1]));}
 const shadows=svg.querySelectorAll('[id$="cast"] feDropShadow');
 shadows.forEach((s,i)=>{s.setAttribute('dx',fmt((.5-clamp(light[0],0,1))*52*(i?.18:1)));s.setAttribute('dy',fmt(((.5-clamp(light[1],0,1))*76+6)*(i?.18:1)));});
}
const translate=(pose,key)=>`data-depth="${key}" transform="translate(${fmt(pose[key][0])} ${fmt(pose[key][1])})"`;

function engravedLens(p,{cx=500,cy=540,rx=399,ry=405,pearl=false}={}){
 let rings='',ticks='';
 for(let i=0;i<68;i++){
  const q=i/67,xx=rx*(1-q*.73),yy=ry*(1-q*.73);
  rings+=`<ellipse cx="${cx}" cy="${cy}" rx="${fmt(xx)}" ry="${fmt(yy)}" fill="none" stroke="url(#${p}${i%5===0?'foil':'metal'})" stroke-width="${i%5===0?1.15:.52}" opacity="${pearl?.32:.38}"/>`;
 }
 for(let i=0;i<144;i++){
  const a=i/144*Math.PI*2,s=i%12===0?13:i%3===0?7:3.2;
  const x=cx+Math.cos(a)*(rx+6),y=cy+Math.sin(a)*(ry+6);
  ticks+=`<path d="M${fmt(x)} ${fmt(y)}l${fmt(Math.cos(a)*s)} ${fmt(Math.sin(a)*s)}" stroke-width="${i%12===0?1.5:.7}"/>`;
 }
 return `<g><ellipse cx="${cx}" cy="${cy+12}" rx="${rx+11}" ry="${ry+11}" fill="#020d17" opacity=".5" filter="url(#${p}blur)"/>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx+14}" ry="${ry+14}" fill="url(#${p}metal)"/>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx+9}" ry="${ry+9}" fill="${pearl?'#839da6':'#030c13'}"/>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx+6}" ry="${ry+6}" fill="url(#${p}lens)"/>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="url(#${p}lensLight)"/>
 ${rings}<g fill="none" stroke="url(#${p}metal)" opacity=".9">${ticks}</g>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx-3}" ry="${ry-3}" fill="none" stroke="url(#${p}foil)" stroke-width="2" opacity=".7"/>
 <ellipse cx="${cx}" cy="${cy}" rx="${rx+12}" ry="${ry+12}" fill="none" stroke="url(#${p}stripe)" stroke-width="4"/>
 </g>`;
}

function detailDefs(p,t,pearl,assets,box,light){
 return `<linearGradient id="${p}field" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${pearl?'#c6dfe3':'#06141d'}"/><stop offset=".38" stop-color="${pearl?'#f7f5e9':'#0a2733'}"/><stop offset=".72" stop-color="${pearl?'#bfd6e0':'#02080e'}"/><stop offset="1" stop-color="${pearl?'#e8eeeb':'#061a26'}"/></linearGradient>
 <radialGradient id="${p}lens" cx=".42" cy=".30" r=".8"><stop stop-color="${pearl?'#f4f6e5':'#6c9298'}"/><stop offset=".29" stop-color="${pearl?'#a6cdd2':'#254753'}"/><stop offset=".65" stop-color="${pearl?'#e0ddcc':'#10222e'}"/><stop offset="1" stop-color="${pearl?'#5b91a6':'#04121c'}"/></radialGradient>
 <radialGradient id="${p}lensLight" gradientUnits="userSpaceOnUse" cx="${light[0]*1000}" cy="${light[1]*1400}" r="660"><stop stop-color="#fff3ce" stop-opacity=".7"/><stop offset=".27" stop-color="#d5faff" stop-opacity=".15"/><stop offset=".66" stop-color="#c6c6ff" stop-opacity=".12"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
 <linearGradient id="${p}rimMetal" x1="0" y1="0" x2="1" y2=".7"><stop stop-color="#253a41"/><stop offset=".18" stop-color="#b8c9c9"/><stop offset=".24" stop-color="#fdf7df"/><stop offset=".29" stop-color="#36575c"/><stop offset=".48" stop-color="#627f87"/><stop offset=".52" stop-color="#ecf6ef"/><stop offset=".57" stop-color="#7e9fa5"/><stop offset=".8" stop-color="#253d4a"/><stop offset="1" stop-color="#d7dad0"/></linearGradient>
 <linearGradient id="${p}titleMetal" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${pearl?'#102f45':'#fff8e7'}"/><stop offset=".43" stop-color="${pearl?'#24465c':'#d8dfd8'}"/><stop offset=".46" stop-color="${pearl?'#597a88':'#617f88'}"/><stop offset=".51" stop-color="${pearl?'#153a52':'#dcece8'}"/><stop offset="1" stop-color="${pearl?'#102b40':'#ffffff'}"/></linearGradient>
 <linearGradient id="${p}titleFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${pearl?'#dfefed':'#03101a'}" stop-opacity="0"/><stop offset=".55" stop-color="${pearl?'#e3efea':'#03101a'}" stop-opacity=".97"/><stop offset="1" stop-color="${pearl?'#e3efea':'#03101a'}"/></linearGradient>
 <linearGradient id="${p}subjectFade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="white"/><stop offset=".83" stop-color="white"/><stop offset="1" stop-color="black"/></linearGradient>
 <mask id="${p}bodyFade"><rect x="-150" y="-100" width="1300" height="1350" fill="url(#${p}subjectFade)"/></mask>
 <image id="${p}subject" href="${assets.art}" x="${box.x}" y="${box.y}" width="${box.width}" height="${box.height}" preserveAspectRatio="xMidYMid meet"/>
 <mask id="${p}alpha" mask-type="alpha"><use href="#${p}subject"/></mask>
 <mask id="${p}selective"><rect width="1000" height="1400" fill="white"/><ellipse cx="${box.x+box.width*.44}" cy="${box.y+box.height*.49}" rx="205" ry="215" fill="black" filter="url(#${p}blur)"/></mask>
 <pattern id="${p}satin" width="2.3" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(38)"><path d="M.3 0V6" stroke="white" stroke-opacity=".12" stroke-width=".32"/></pattern>
 <pattern id="${p}diamondGrain" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M4 .8L7.2 4 4 7.2 .8 4Z" fill="none" stroke="${pearl?'#33566a':'#c7e9ee'}" stroke-width=".23" opacity=".16"/></pattern>
 <filter id="${p}blur" x="-.4" y="-.4" width="1.8" height="1.8"><feGaussianBlur stdDeviation="9"/></filter>
 <filter id="${p}cast" x="-.3" y="-.3" width="1.6" height="1.6"><feDropShadow dx="${fmt((.5-light[0])*52)}" dy="${fmt((.5-light[1])*76+6)}" stdDeviation="15" flood-color="#000a13" flood-opacity="${pearl?.42:.9}"/><feDropShadow dx="${fmt((.5-light[0])*52*.18)}" dy="${fmt(((.5-light[1])*76+6)*.18)}" stdDeviation="2.5" flood-color="#01070d" flood-opacity=".75"/></filter>
 <filter id="${p}rim" x="-.1" y="-.1" width="1.2" height="1.2"><feMorphology in="SourceAlpha" operator="dilate" radius=".85" result="expanded"/><feComposite in="expanded" in2="SourceAlpha" operator="out" result="edge"/><feFlood flood-color="${pearl?'#efffff':'#c3f2ff'}"/><feComposite in2="edge" operator="in"/></filter>
 <filter id="${p}textRelief" x="-.1" y="-.1" width="1.2" height="1.3"><feDropShadow dx="0" dy="1.1" stdDeviation=".4" flood-color="${pearl?'#fff':'#000'}" flood-opacity=".9"/></filter>`;
}

function frame(p,t,pearl){
 const oct='M73 29H927L971 73V1327L927 1371H73L29 1327V73Z';
 const inner='M77 43H923L957 77V1323L923 1357H77L43 1323V77Z';
 let hatches='';for(let y=160;y<1240;y+=7){hatches+=`<path d="M19 ${y}l7 3M974 ${y+3}l7-3"/>`;}
 let corners='';for(const [x,y,sx,sy]of [[38,38,1,1],[962,38,-1,1],[38,1362,1,-1],[962,1362,-1,-1]]){
  corners+=`<g transform="translate(${x} ${y}) scale(${sx} ${sy})"><path d="M0 106V39L39 0H106" fill="none" stroke="url(#${p}metal)" stroke-width="2"/><path d="M9 97V43L43 9H97" fill="none" stroke="url(#${p}foil)" stroke-width=".65"/><path d="M22 55L55 22" stroke="#f3f6ec" stroke-width=".6" opacity=".7"/><circle cx="25" cy="25" r="2.1" fill="url(#${p}metal)"/></g>`;
 }
 return `<rect x="5" y="5" width="990" height="1390" rx="39" fill="none" stroke="#040a10" stroke-width="9"/><rect x="9" y="9" width="982" height="1382" rx="35" fill="none" stroke="url(#${p}rimMetal)" stroke-width="9"/>
 <rect x="18" y="18" width="964" height="1364" rx="28" fill="none" stroke="${pearl?'#dce9df':'#07161e'}" stroke-width="9"/>
 <path d="${oct}" fill="none" stroke="url(#${p}rimMetal)" stroke-width="10"/><path d="${oct}" fill="none" stroke="url(#${p}stripe)" stroke-width="8" class="foil-layer"/>
 <path d="${inner}" fill="none" stroke="${pearl?'#456a78':'#c1dbda'}" stroke-width=".7" opacity=".65"/>
 <g fill="none" stroke="url(#${p}metal)" stroke-width=".55" opacity=".45">${hatches}</g>${corners}`;
}

function architecture(p,pearl){
 if(pearl)return `<path d="M67 241Q189 150 397 170L816 184Q918 186 937 304L913 831Q835 1025 586 1090L200 1077Q71 1058 70 910Z" fill="none" stroke="#507b88" stroke-width="19" opacity=".3" filter="url(#${p}blur)"/>
 <path d="M67 228Q189 137 397 157L816 171Q918 173 937 291L913 818Q835 1012 586 1077L200 1064Q71 1045 70 897Z" fill="url(#${p}lens)" stroke="url(#${p}rimMetal)" stroke-width="15"/>
 <path d="M79 235Q195 151 398 171L810 184Q902 186 922 296L899 814Q824 996 583 1063L203 1050Q85 1034 84 894Z" fill="url(#${p}diamondGrain)" stroke="#e7ffff" stroke-width="2"/>
 ${Array.from({length:27},(_,i)=>`<path d="M${75+i*1.1} ${310+i*11}C${164+i*4} ${897+i*2},${723+i*2} ${1044-i*2},${928-i*.2} ${659-i*9}" fill="none" stroke="url(#${p}foil)" stroke-width=".65" opacity=".45"/>`).join('')}`;
 return `<path d="M106 212L158 160H842L894 212V1014L834 1074H166L106 1014Z" fill="#020a10" stroke="#061018" stroke-width="21"/>
 <path d="M106 205L158 153H842L894 205V1007L834 1067H166L106 1007Z" fill="url(#${p}field)" stroke="url(#${p}rimMetal)" stroke-width="12"/>
 <path d="M119 211L164 167H836L881 211V1001L828 1054H172L119 1001Z" fill="url(#${p}diamondGrain)" stroke="#8cb8c5" stroke-width=".6"/>
 <path d="M130 267V222L173 179H270M730 179H827L870 222V267M130 939V990L179 1040H282M718 1040H821L870 990V939" fill="none" stroke="url(#${p}foil)" stroke-width="2"/>`;
}

function particles(p,seed,pearl){
 const rand=rng(seed);let stars='';
 for(let i=0;i<56;i++){
  const x=70+rand()*860,y=160+rand()*870,r=.35+rand()*.9;
  stars+=`<circle cx="${fmt(x)}" cy="${fmt(y)}" r="${fmt(r)}" opacity="${fmt(.15+rand()*.35)}"/>`;
 }
 return `<g fill="${pearl?'#3d6578':'#c8edf4'}">${stars}</g>`;
}

function pearlOptics(p){
 let arcs='';
 for(let i=0;i<112;i++){
  const r=110+i*7.4;
  arcs+=`<ellipse cx="272" cy="871" rx="${fmt(r)}" ry="${fmt(r*.93)}" fill="none" stroke="url(#${p}${i%9===0?'foil':'metal'})" stroke-width="${i%9===0?1.2:.52}" opacity="${i%9===0?.47:.24}"/>`;
 }
 return `<defs><clipPath id="${p}pearlWindow"><path d="M79 235Q195 151 398 171L810 184Q902 186 922 296L899 814Q824 996 583 1063L203 1050Q85 1034 84 894Z"/></clipPath></defs><g clip-path="url(#${p}pearlWindow)"><rect x="60" y="155" width="880" height="930" fill="url(#${p}lensLight)"/>${arcs}
 <path d="M107 1014C304 1123 920 721 905 269" fill="none" stroke="url(#${p}foil)" stroke-width="17" opacity=".16"/>
 <path d="M107 1014C304 1123 920 721 905 269" fill="none" stroke="url(#${p}metal)" stroke-width="1.2" opacity=".65"/>
 </g>`;
}

/** A reusable layered SVG. The only raster layer is the supplied illustration. */
export function renderDepthCard(def,assets,{light=[.28,.2],foil=1,prefix,side='front'}={}){
 const c=validateCard(def),pearl=c.theme==='tidelight',t=THEMES[c.theme];
 if(!DEPTH_EDITIONS.includes(c.theme))throw new Error('Depth editions support Nocturne and Tidelight');
 if(!Array.isArray(light)||light.length!==2||light.some(v=>!Number.isFinite(v)))throw new Error('Invalid light');
 const li=light.map(v=>clamp(v,0,1)),pose=depthPose(li),p=prefix??`depth-${c.id}-${side}-`,f=clamp(Number(foil)||0,0,1);
 if(!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(p))throw new Error('Invalid SVG prefix');
 if(!/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(assets.art??''))throw new Error('Artwork must be an embedded image');
 if(!['display','sans'].every(k=>/^data:font\/ttf;base64,[A-Za-z0-9+/=]+$/.test(assets.fonts?.[k]??'')))throw new Error('Fonts must be embedded TTF files');
 if(!Number.isFinite(assets.artWidth)||!Number.isFinite(assets.artHeight)||assets.artWidth<=0||assets.artHeight<=0)throw new Error('Invalid artwork dimensions');
 for(const u of [assets.sourceUrl,assets.licenseUrl])if(new URL(u).protocol!=='https:')throw new Error('Source URLs must use HTTPS');
 const box=c.depthSubject??{x:9,y:147,width:982,height:1174};
 if(['x','y','width','height'].some(k=>!Number.isFinite(box[k]))||box.width<=0||box.height<=0)throw new Error('Invalid character placement');
 const font=`@font-face{font-family:NightDisplay;src:url('${assets.fonts.display}') format('truetype');font-weight:500}@font-face{font-family:NightSans;src:url('${assets.fonts.sans}') format('truetype');font-weight:500}.d{font-family:NightDisplay,Georgia,serif;font-weight:500}.s{font-family:NightSans,Arial,sans-serif;font-weight:500}text{font-kerning:normal}`;
 const name=c.name.toUpperCase(),nameSize=Math.min(151,780/(name.length*.58));
 const textInk=pearl?'#183e53':'#e7f2ec',subInk=pearl?'#426c7e':'#9bbac4';
 const titleZone=`<rect x="43" y="1006" width="914" height="350" fill="url(#${p}titleFade)"/>
 <path d="M75 1099H231M769 1099H925" stroke="url(#${p}metal)" stroke-width=".7" opacity=".7"/>
 <text x="70" y="1221" class="d" font-size="${nameSize}" fill="url(#${p}titleMetal)" filter="url(#${p}textRelief)" data-text="name">${xml(name)}</text>
 <text x="76" y="1282" class="s" font-size="25" letter-spacing="5.5" fill="${textInk}" data-text="edition">${xml(c.edition)}</text>
 <g transform="translate(879 1190)" fill="none" stroke="url(#${p}rimMetal)" stroke-width="1"><path d="M0-36 31-18 31 18 0 36-31 18-31-18Z" fill="${pearl?'#c5dce0':'#0d2631'}"/><path d="M0-30 26-15 26 15 0 30-26 15-26-15ZM0-36V-21M31-18 18-11M31 18 18 11M0 36V21M-31 18-18 11M-31-18-18-11" stroke-width=".6"/><path d="${star(0,0,19)}" fill="url(#${p}metal)" stroke="none"/></g>
 <text x="925" y="1282" class="s" font-size="17" letter-spacing="3" text-anchor="end" fill="${subInk}">${xml(c.number)}</text>
 <path d="M76 1305H925" stroke="url(#${p}metal)" stroke-width=".55" opacity=".5"/>
 <text x="76" y="1333" class="s" font-size="10.5" letter-spacing="1.8" fill="${subInk}">${xml(c.material)}</text>
 <a href="${xml(assets.sourceUrl)}"><text x="925" y="1333" text-anchor="end" class="s" font-size="10.5" fill="${subInk}">${xml(assets.artist)} · ${xml(assets.license)}</text></a>`;
 const header=`<text x="77" y="105" class="s" font-size="25" letter-spacing="8" fill="${textInk}">NIGHT</text><text x="924" y="105" text-anchor="end" class="s" font-size="12" letter-spacing="3" fill="${subInk}">ARTIST EDITIONS</text><path d="M78 125H190M814 125H923" stroke="url(#${p}metal)" stroke-width=".6" opacity=".5"/>`;
 let content;
 if(side==='back'){
  content=`<rect width="1000" height="1400" fill="url(#${p}field)"/><rect width="1000" height="1400" fill="url(#${p}diamondGrain)"/>${header}<g opacity=".65">${engravedLens(p,{cx:500,cy:550,rx:280,ry:280,pearl})}</g><path d="${star(500,550,64)}" fill="url(#${p}metal)"/><text x="500" y="958" class="d" font-size="77" text-anchor="middle" fill="${textInk}">${xml(c.name)} / ${xml(c.edition)}</text><text x="500" y="1026" class="s" font-size="20" text-anchor="middle" fill="${subInk}">Artwork · ${xml(assets.artist)}</text><text x="500" y="1070" class="s" font-size="17" text-anchor="middle" fill="${subInk}">${xml(assets.outputLicense)}</text><a href="${xml(assets.sourceUrl)}"><text x="500" y="1114" class="s" font-size="17" text-anchor="middle" fill="${subInk}">${xml(new URL(assets.sourceUrl).hostname)}</text></a><text x="500" y="1307" class="s" font-size="16" letter-spacing="5" text-anchor="middle" fill="${subInk}">NIGHT · ARTIST EDITIONS</text>`;
 }else if(side==='front'){
  content=`<rect width="1000" height="1400" fill="url(#${p}field)"/><rect width="1000" height="1400" fill="url(#${p}diamondGrain)"/>${particles(p,c.seed,pearl)}
  <g ${translate(pose,'rear')}>${architecture(p,pearl)}${pearl?pearlOptics(p):engravedLens(p,{cx:525,cy:576,rx:350,ry:378,pearl})}
  <path d="M32 870C191 1082 878 1008 979 465M37 892C243 1094 853 1027 978 500" fill="none" stroke="url(#${p}foil)" stroke-width="1" opacity=".6"/></g>
  <g ${translate(pose,'subject')}><g mask="url(#${p}bodyFade)"><use href="#${p}subject" filter="url(#${p}cast)"/><use href="#${p}subject" filter="url(#${p}rim)" opacity=".65"/><use href="#${p}subject"/>
  <g class="foil-layer" mask="url(#${p}alpha)" opacity="${f*.23}"><g mask="url(#${p}selective)"><rect width="1000" height="1400" fill="url(#${p}stripe)"/><rect width="1000" height="1400" fill="url(#${p}satin)" opacity=".16"/></g></g></g></g>
  <g ${translate(pose,'front')}><path d="M47 952Q73 1080 220 1105M953 863Q928 1048 781 1094" fill="none" stroke="url(#${p}rimMetal)" stroke-width="${pearl?5:2}" opacity=".8"/><path d="${star(118,875,13)}${star(872,288,11)}" fill="url(#${p}metal)" opacity=".9"/></g>
  ${titleZone}${header}`;
 }else throw new Error('Unknown side');
 const metadata={renderer:'NIGHT Depth Editions '+DEPTH_VERSION,card:c.id,artwork:assets.title,artist:assets.artist,source:assets.sourceUrl,rights:assets.rights??assets.license,outputRights:assets.outputLicense,nativeArtwork:[assets.artWidth,assets.artHeight],design:'Programmatic SVG, layered character, cast shadows, alpha rim, optical engraving and selective foil'};
 return `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1400" viewBox="0 0 1000 1400" role="img" aria-labelledby="${p}title"><title id="${p}title">${xml(c.name)} — ${xml(c.edition)} (${side})</title><metadata>${xml(JSON.stringify(metadata))}</metadata><defs><style>${font}</style><clipPath id="${p}cardClip"><rect x="4" y="4" width="992" height="1392" rx="38"/></clipPath>${materialDefs(p,t,{light:li,foil:f})}${detailDefs(p,t,pearl,assets,box,li)}</defs><g clip-path="url(#${p}cardClip)">${content}${frame(p,t,pearl)}</g></svg>`;
}
