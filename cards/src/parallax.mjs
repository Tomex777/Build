import {powerPresentation} from './power.mjs';

const xml=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));

/** A material/type specimen, not a canonical SSS assessment of its reference
 * character. No active collection card or power assessment is modified. */
export function renderParallaxStudy(card,assets,{prefix='parallax-study-'}={}){
  if(!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(prefix))throw Error('Invalid Parallax prefix');
  if(!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(assets.art))throw Error('Use pinned embedded artwork');
  const p=prefix,artX=-60,artY=100,artWidth=1120,artHeight=artWidth*assets.artHeight/assets.artWidth;
  const fonts=`@font-face{font-family:PDisplay;src:url('${assets.fonts.display}') format('truetype');font-weight:500}@font-face{font-family:NSans;src:url('${assets.fonts.sans}') format('truetype');font-weight:500}.d{font-family:PDisplay,Georgia,serif;font-weight:500}.s{font-family:NSans,Arial,sans-serif;font-weight:500}text{font-kerning:normal}`;
  const metadata={design:'Parallax / proposed SSS material study',renderer:'NIGHT programmed SVG',referenceCharacter:card.name,referenceForm:card.form,referencePowerGrade:card.power.rank,canonicalAssignment:false,source:card.source.page,originalArtworkSHA256:card.source.sha256,rights:card.source.rights,construction:'Sculpted platinum planes, open recessed field, selectively prismatic edges, original transparent illustration in front of the frame; no generated artwork.'};
  const well='M112 311L398 208H796L920 332V1010L773 1147H193L77 1036V448';
  const rim='M126 325L404 225H789L902 339V1003L765 1130H201L95 1027V454';
  let svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1600" viewBox="0 0 1000 1600" data-design="parallax" data-material-study="true" data-canonical-assignment="false" data-reference-power-rank="${card.power.rank}" role="img" aria-labelledby="${p}title"><title id="${p}title">Parallax SSS design study using ${xml(card.name)} artwork; reference character grade remains ${card.power.rank}</title><metadata>${xml(JSON.stringify(metadata))}</metadata><defs><style>${fonts}</style>
    <clipPath id="${p}card"><rect x="3" y="3" width="994" height="1594" rx="25"/></clipPath>
    <linearGradient id="${p}shell" x1="0" y1="0" x2=".72" y2="1"><stop stop-color="#f3f5ee"/><stop offset=".25" stop-color="#d5dde0"/><stop offset=".48" stop-color="#f6f7f1"/><stop offset=".68" stop-color="#c6d1d6"/><stop offset="1" stop-color="#eef2ed"/></linearGradient>
    <linearGradient id="${p}platinum" x1="0" y1="0" x2="1" y2=".9"><stop stop-color="#8d9ca7"/><stop offset=".13" stop-color="#fbfff9"/><stop offset=".26" stop-color="#c5d0d6"/><stop offset=".42" stop-color="#617684"/><stop offset=".5" stop-color="#e8f1ef"/><stop offset=".57" stop-color="#fbfff9"/><stop offset=".72" stop-color="#9babb6"/><stop offset="1" stop-color="#eef7f3"/></linearGradient>
    <linearGradient id="${p}facet" x1="0" y1="0" x2=".85" y2="1"><stop stop-color="#ffffff"/><stop offset=".3" stop-color="#d6e1e4"/><stop offset=".58" stop-color="#fafffa"/><stop offset="1" stop-color="#7d929f"/></linearGradient>
    <linearGradient id="${p}inside" x1="0" y1="0" x2=".8" y2="1"><stop stop-color="#243a45"/><stop offset=".35" stop-color="#0a1d28"/><stop offset=".7" stop-color="#10232c"/><stop offset="1" stop-color="#030d15"/></linearGradient>
    <linearGradient id="${p}prism" gradientUnits="userSpaceOnUse" x1="70" y1="1120" x2="950" y2="320"><stop stop-color="#70c9d1"/><stop offset=".22" stop-color="#d6c6e5"/><stop offset=".39" stop-color="#f4efe0"/><stop offset=".53" stop-color="#f4d5ba"/><stop offset=".66" stop-color="#c9b2d7"/><stop offset=".79" stop-color="#83cad6"/><stop offset="1" stop-color="#ecf4e6"/></linearGradient>
    <linearGradient id="${p}glass" x1="0" y1="0" x2=".85" y2="1"><stop stop-color="#f9ffff" stop-opacity=".78"/><stop offset=".25" stop-color="#d2e4e9" stop-opacity=".16"/><stop offset=".52" stop-color="#fff" stop-opacity=".48"/><stop offset="1" stop-color="#9cbecb" stop-opacity=".18"/></linearGradient>
    <linearGradient id="${p}name" x1="0" y1="0" x2=".7" y2="1"><stop stop-color="#0a2534"/><stop offset=".55" stop-color="#294c5a"/><stop offset=".58" stop-color="#66818b"/><stop offset=".62" stop-color="#294653"/><stop offset="1" stop-color="#18333e"/></linearGradient>
    <linearGradient id="${p}fade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#edf2ee" stop-opacity="0"/><stop offset=".65" stop-color="#edf2ee" stop-opacity=".85"/><stop offset="1" stop-color="#edf2ee"/></linearGradient>
    <pattern id="${p}brush" width="5" height="11" patternUnits="userSpaceOnUse" patternTransform="rotate(24)"><path d="M.4 0V11" stroke="#fff" stroke-width=".4" opacity=".32"/><path d="M2.4 0V11" stroke="#34515f" stroke-width=".35" opacity=".09"/></pattern>
    <pattern id="${p}field-grain" width="3" height="3" patternUnits="userSpaceOnUse"><path d="M.4 .4h.4" stroke="#d0e1e7" stroke-opacity=".08" stroke-width=".4"/></pattern>
    <filter id="${p}well-shadow" x="-.2" y="-.2" width="1.4" height="1.4"><feDropShadow dx="3" dy="15" stdDeviation="15" flood-color="#112a38" flood-opacity=".28"/></filter>
    <filter id="${p}subject-shadow" x="-.2" y="-.2" width="1.4" height="1.4"><feDropShadow dx="7" dy="15" stdDeviation="11" flood-color="#000911" flood-opacity=".75"/></filter>
    <filter id="${p}rim" x="-.05" y="-.05" width="1.1" height="1.1"><feMorphology in="SourceAlpha" operator="dilate" radius=".5" result="expanded"/><feComposite in="expanded" in2="SourceAlpha" operator="out" result="edge"/><feFlood flood-color="#f1ffff" flood-opacity=".45"/><feComposite in2="edge" operator="in"/></filter>
    <image id="${p}subject" href="${assets.art}" x="${artX}" y="${artY}" width="${artWidth}" height="${artHeight}" preserveAspectRatio="xMidYMid meet"/>
  </defs><g clip-path="url(#${p}card)">
    <rect width="1000" height="1600" fill="url(#${p}shell)"/>
    <rect width="1000" height="1600" fill="url(#${p}brush)" opacity=".6"/>
    <path d="M19 275V75L75 19H641M980 1325V1525L925 1581H359" fill="none" stroke="url(#${p}platinum)" stroke-width="2"/>
    <path d="M31 281V80L81 31H638M969 1319V1520L919 1569H362" fill="none" stroke="#6c8792" stroke-width=".55" opacity=".5"/>
    <path d="M715 20H925L980 75V240M20 1360V1525L75 1580H275" fill="none" stroke="#6e8b98" stroke-width=".7" opacity=".5"/>
    <g data-depth="rear">
      <path d="${well}Z" fill="#283e4b" filter="url(#${p}well-shadow)"/>
      <path d="M97 310L395 195H805L938 325V1018L782 1164H186L58 1044V446Z" fill="url(#${p}platinum)"/>
      <path d="${well}Z" fill="url(#${p}inside)"/>
      <path d="${well}Z" fill="url(#${p}field-grain)"/>
      <path d="M97 310L395 195H805L938 325L920 332L796 208H398L112 311L77 448L58 446Z" fill="url(#${p}facet)"/>
      <path d="M938 325V1018L782 1164L773 1147L920 1010V332Z" fill="url(#${p}platinum)"/>
      <path d="M58 446L77 448V1036L193 1147H773L782 1164H186L58 1044Z" fill="url(#${p}facet)"/>
      <path d="${rim}" fill="none" stroke="url(#${p}prism)" stroke-width="1.8" opacity=".85"/>
      <path d="M94 456V1024L202 1128H761M409 227H786L899 341" fill="none" stroke="#e4f7fa" stroke-width=".65" opacity=".7"/>
      <path d="M310 243L402 209H792L907 325M925 752V1009L777 1150H612" fill="none" stroke="url(#${p}prism)" stroke-width="3.7" opacity=".7"/>
      <path d="M411 244H773L884 355V475M117 819V1015L213 1111H354" fill="none" stroke="#8fb2c2" stroke-width=".5" opacity=".38"/>
    </g>
    <g data-depth="subject"><use href="#${p}subject" filter="url(#${p}subject-shadow)"/><use href="#${p}subject" filter="url(#${p}rim)"/><use href="#${p}subject"/></g>
    <g data-depth="front">
      <path d="M53 1064L205 1178H619L705 1147H194L77 1036Z" fill="url(#${p}glass)" stroke="#f8ffff" stroke-opacity=".8" stroke-width=".8"/>
      <path d="M67 1080L203 1182H612" fill="none" stroke="url(#${p}prism)" stroke-width="3"/>
      <path d="M782 1164L952 1006V815L938 828V1018Z" fill="url(#${p}glass)" stroke="#f4ffff" stroke-width=".75" stroke-opacity=".64"/>
      <path d="M792 1162L955 1010V906" fill="none" stroke="url(#${p}prism)" stroke-width="2.5"/>
    </g>
    <rect x="0" y="1080" width="1000" height="520" fill="url(#${p}fade)"/>
    <text x="70" y="96" class="s" font-size="22" letter-spacing="8" fill="#294b5c">NIGHT</text>
    <path d="M73 115H194" stroke="#527787" stroke-width=".7" opacity=".6"/>
    <text data-name="true" x="68" y="1322" class="d" font-size="116" letter-spacing="1.2" fill="url(#${p}name)">PARALLAX</text>
    <text x="75" y="1382" class="s" font-size="18" letter-spacing="5" fill="#3d6474">SSS / MATERIAL STUDY</text>
    <path d="M75 1412H925" stroke="#72929c" stroke-width=".55" opacity=".65"/>
    <text x="75" y="1451" class="s" font-size="12" letter-spacing="2.1" fill="#54707c">${xml(card.title.toUpperCase())} ARTWORK / COLLECTION GRADE ${xml(card.power.rank)}</text>
    <text x="75" y="1515" class="s" font-size="10" letter-spacing="1.6" fill="#627f8b">PLATINUM / SELECTIVE PRISMATIC FOIL</text>
    <a href="${xml(card.source.page)}"><text x="75" y="1536" class="s" font-size="8.5" fill="#71858d">${xml(card.source.artist)} · ${xml(card.source.credit)}</text></a>
    <rect x="3" y="3" width="994" height="1594" rx="25" fill="none" stroke="url(#${p}platinum)" stroke-width="5"/>
    <rect x="11" y="11" width="978" height="1578" rx="18" fill="none" stroke="#fff" stroke-width=".8" opacity=".8"/>
  </g></svg>`;
  // Existing glass-letter optics are reused as a tier type specimen. Its
  // scope explicitly describes the specimen rather than assessing Byakuya.
  const study={...card,id:card.id+'-parallax-study',recipe:{...card.recipe,powerBadgeTone:{parallax:'light'}},power:{...card.power,rank:'SSS',review:undefined,scope:'Parallax tier lettering specimen; not a character power assignment.',basis:'Design study only. Reference character collection grade remains '+card.power.rank+'.'}};
  return powerPresentation(svg,study,'parallax');
}
