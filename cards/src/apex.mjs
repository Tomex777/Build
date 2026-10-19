import {FRAME_PATHS} from './frame-depth.mjs';
const xml=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));

// Separate architecture for the two upper tiers. Illustration remains in front
// of the actual structural opening; these are not palette swaps of Nocturne.
export function renderApex(card,a,design) {
  if(!['eclipse','ascendant'].includes(design))throw Error('Unknown apex design');
  const ex=design==='ascendant',p=`${design}-${card.id}-`,d=FRAME_PATHS[design];
  const ink=ex?'#1b2730':'#eee9f4',muted=ex?'#627078':'#aaa2bd';
  const field=ex?'#e9e6e0':'#100e1b',footer=ex?'#e9e6e0':'#100e1b';
  const frame=ex?`
    <path d="M130 296L384 165H842L922 324V1018L662 1156H157L77 996Z" fill="#17242b" stroke="#080f15" stroke-width="4"/>
    <path d="${d}" fill="url(#${p}well)" stroke="url(#${p}metal)" stroke-width="7"/>
    <path d="M112 295L381 155H594M928 467V1028L671 1167H442" fill="none" stroke="#ac514a" stroke-width="3"/>
    <path d="M154 344L187 284L393 210H792M871 937V987L649 1105H208" fill="none" stroke="#cbd4d1" stroke-width=".8" opacity=".75"/>
    <path d="M65 401L102 322V647L65 725ZM935 754L898 832V1090L935 1013Z" fill="url(#${p}metal)"/>
  `:`
    <path d="M88 368C88 247 225 186 500 186C775 186 912 247 912 368V928C912 1067 692 1133 500 1175C308 1133 88 1067 88 928Z" fill="#050711" stroke="#04050b" stroke-width="13"/>
    <path d="${d}" fill="url(#${p}well)" stroke="url(#${p}metal)" stroke-width="11"/>
    <path d="M141 369C141 287 259 243 500 243C741 243 859 287 859 369V920C859 1013 689 1067 500 1112C311 1067 141 1013 141 920Z" fill="none" stroke="#b4a0ca" stroke-width=".8" opacity=".7"/>
    <path d="M69 458V366Q69 226 314 177M931 458V366Q931 226 686 177M69 801V929Q69 1095 394 1178M931 801V929Q931 1095 606 1178" fill="none" stroke="url(#${p}metal)" stroke-width="3"/>
  `;
  const size=Math.min(ex?160:182,870/(card.title.length*.6));
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1600" viewBox="0 0 1000 1600" role="img" aria-labelledby="${p}title"><title id="${p}title">${xml(card.name+' / '+design)}</title><metadata>${xml(JSON.stringify({source:card.source.page,artworkSHA256:card.source.sha256,construction:'Programmed SVG, original source RGB and source-bound silhouette matte.'}))}</metadata><defs>
  <style>@font-face{font-family:NSans;src:url('${a.fonts.sans}')}@font-face{font-family:NDisplay;src:url('${a.fonts.display}')}@font-face{font-family:NBlack;src:url('${a.fonts.black}')}text{font-weight:400;font-kerning:normal}</style>
  <clipPath id="${p}clip"><rect x="4" y="4" width="992" height="1592" rx="${ex?18:30}"/></clipPath>
  <linearGradient id="${p}well" x1="0" y1="0" x2="1" y2="1"><stop stop-color="${ex?'#849394':'#343148'}"/><stop offset=".52" stop-color="${ex?'#d3d2c9':'#111325'}"/><stop offset="1" stop-color="${ex?'#9badaa':'#2f2338'}"/></linearGradient>
  <linearGradient id="${p}metal" x1="0" y1="0" x2="1" y2=".8"><stop stop-color="${ex?'#647975':'#746380'}"/><stop offset=".18" stop-color="${ex?'#dee4da':'#ded8e7'}"/><stop offset=".24" stop-color="#fcf8ed"/><stop offset=".31" stop-color="${ex?'#68817c':'#867991'}"/><stop offset=".54" stop-color="${ex?'#203a3e':'#352d44'}"/><stop offset=".71" stop-color="#f2ecdf"/><stop offset=".78" stop-color="${ex?'#afbfba':'#b8a6c6'}"/><stop offset="1" stop-color="${ex?'#405655':'#5d4c70'}"/></linearGradient>
  <linearGradient id="${p}fade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="${footer}" stop-opacity="0"/><stop offset=".73" stop-color="${footer}"/><stop offset="1" stop-color="${footer}"/></linearGradient>
  <pattern id="${p}grain" width="5" height="7" patternUnits="userSpaceOnUse"><path d="M0 1H2M3 5h1" stroke="${ink}" opacity=".04" stroke-width=".6"/></pattern>
  <filter id="${p}cast" x="-.25" y="-.25" width="1.5" height="1.5"><feDropShadow dx="${ex?13:6}" dy="16" stdDeviation="11" flood-color="#020511" flood-opacity="${ex?.32:.85}"/></filter>
  <image id="${p}hero" href="${a.art}" x="-40" y="105" width="1080" height="${1080*a.artHeight/a.artWidth}" preserveAspectRatio="xMidYMid meet"/>
  </defs><g clip-path="url(#${p}clip)"><rect width="1000" height="1600" fill="${field}"/><rect width="1000" height="1600" fill="url(#${p}grain)"/>
  ${ex?`<path d="M0 363L428 141H1000M0 1301L596 982H1000" fill="none" stroke="#869b98" stroke-width=".7" opacity=".35"/><path d="M0 1321L1000 793" stroke="#fffdf5" stroke-width="30" opacity=".28"/>`:`<path d="M33 292V1190M967 292V1190" stroke="#6a6178" stroke-width=".7" opacity=".5"/>`}
  <g data-depth="rear" data-inner-frame="${design}">${frame}</g>
  <g data-layer="hero" data-depth="subject"><use href="#${p}hero" filter="url(#${p}cast)"/><use href="#${p}hero"/></g>
  <rect x="0" y="1065" width="1000" height="355" fill="url(#${p}fade)"/>
  <text data-name="true" x="${ex?68:500}" y="1350" text-anchor="${ex?'start':'middle'}" font-family="${ex?'NBlack':'NDisplay'}" font-size="${size}" fill="${ink}" letter-spacing="${ex?-5:1}">${xml(card.title.toUpperCase())}</text>
  <text x="${ex?74:500}" y="1407" text-anchor="${ex?'start':'middle'}" font-family="NSans" font-size="16" letter-spacing="3.8" fill="${muted}">${xml(card.form.split(' / ')[0].toUpperCase())}</text>
  <path d="M69 1465H931" stroke="url(#${p}metal)" stroke-width=".8"/>
  <text x="70" y="1517" font-family="NSans" font-size="12" letter-spacing="4.8" fill="${muted}">${design.toUpperCase()}</text><text x="930" y="1517" text-anchor="end" font-family="NSans" font-size="12" letter-spacing="3" fill="${muted}">${String(card.number).padStart(3,'0')}</text>
  <text x="68" y="79" font-family="NSans" font-size="20" letter-spacing="7" fill="${muted}">NIGHT</text>
  ${ex?`<path d="M22 266V22H732M978 1334V1578H268" fill="none" stroke="#2a4447" stroke-width="1.3"/><path d="M22 291V432M978 1168V1309" stroke="#ac514a" stroke-width="3"/>`:`<rect x="14" y="14" width="972" height="1572" rx="26" fill="none" stroke="url(#${p}metal)" stroke-width="1.2"/><path d="M45 131H369M631 131H955M45 1543H279M721 1543H955" stroke="#8e809e" stroke-width=".7" opacity=".6"/>`}
  </g></svg>`;
}
