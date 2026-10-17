import {WIDTH,HEIGHT,xml,star,rosette,cover,clamp} from './geometry.mjs';
import {THEMES,materialDefs,backdrop,engravedFrame,setCardLight} from './materials.mjs';
import {frontDesign,compositionFor} from './fronts.mjs';
export {WIDTH,HEIGHT,THEMES,setCardLight};
export function validateCard(c){
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
export function renderCard(def,assets,{side='front',light=[.28,.2],foil=1,prefix}={}){
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
