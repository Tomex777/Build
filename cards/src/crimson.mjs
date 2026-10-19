const xml=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));

/** Muted oxblood enamel and dark steel; programmed SVG with original pixels. */
export function renderCrimson(card,a) {
  const p=`crimson-${card.id}-`,h=1130*a.artHeight/a.artWidth;
  const size=Math.min(185,890/(card.title.length*.57));
  const metadata={source:card.source.page,artworkSHA256:card.source.sha256,construction:'Programmed SVG and pinned original artwork; no generated imagery.'};
  return `<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="1600" viewBox="0 0 1000 1600" role="img" aria-labelledby="${p}title"><title id="${p}title">${xml(card.name)} / Crimson</title><metadata>${xml(JSON.stringify(metadata))}</metadata><defs>
  <style>@font-face{font-family:NSans;src:url('${a.fonts.sans}')}@font-face{font-family:NItalic;src:url('${a.fonts.italic}')}text{font-weight:400;font-kerning:normal}</style>
  <clipPath id="${p}clip"><rect x="5" y="5" width="990" height="1590" rx="27"/></clipPath>
  <linearGradient id="${p}field" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#29181f"/><stop offset=".48" stop-color="#100e15"/><stop offset="1" stop-color="#210e16"/></linearGradient>
  <linearGradient id="${p}steel" x1="0" y1="0" x2="1" y2=".6"><stop stop-color="#241f29"/><stop offset=".23" stop-color="#b99fa5"/><stop offset=".27" stop-color="#f3d5d4"/><stop offset=".34" stop-color="#44343f"/><stop offset=".68" stop-color="#936b78"/><stop offset=".73" stop-color="#ead7d5"/><stop offset="1" stop-color="#3b2b37"/></linearGradient>
  <linearGradient id="${p}fade" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#170f17" stop-opacity="0"/><stop offset=".5" stop-color="#170f17" stop-opacity=".86"/><stop offset="1" stop-color="#170f17"/></linearGradient>
  <pattern id="${p}paper" width="4" height="6" patternUnits="userSpaceOnUse"><path d="M0 .5H4M1 3h.4" fill="none" stroke="#ead9d7" stroke-width=".35" opacity=".055"/></pattern>
  <filter id="${p}cast" x="-.3" y="-.3" width="1.6" height="1.6"><feDropShadow dx="9" dy="17" stdDeviation="10" flood-color="#03070b" flood-opacity=".76"/><feDropShadow dx="1" dy="2" stdDeviation="1.5" flood-color="#000" flood-opacity=".6"/></filter>
  <image id="${p}hero" href="${a.art}" x="-65" y="112" width="1130" height="${h}" preserveAspectRatio="xMidYMid meet"/>
  </defs><g clip-path="url(#${p}clip)"><rect width="1000" height="1600" fill="url(#${p}field)"/><rect width="1000" height="1600" fill="url(#${p}paper)"/>
  <g data-depth="rear" data-inner-frame="crimson"><path d="M132 274L188 218H868V1000L792 1096H132Z" fill="#100e15" stroke="#08080e" stroke-width="19"/><path d="M132 274L188 218H868V1000L792 1096H132Z" fill="none" stroke="url(#${p}steel)" stroke-width="8"/><path d="M147 281L195 233H853V995L785 1081H147Z" fill="none" stroke="#b5798c" stroke-width=".8" opacity=".55"/><path d="M104 480V317L188 190H380M896 790V1031L815 1130H637" fill="none" stroke="#80394e" stroke-width="2"/><path d="M167 354V289L205 251H292M735 1059H773L832 984V914" fill="none" stroke="#cba7a9" stroke-width="1" opacity=".5"/></g>
  <g data-layer="hero" data-depth="subject"><use href="#${p}hero" filter="url(#${p}cast)"/><use href="#${p}hero"/></g>
  <rect x="0" y="1010" width="1000" height="520" fill="url(#${p}fade)"/>
  <text data-name="true" x="64" y="1366" font-family="NItalic" font-size="${size}" fill="#f0e3dc">${xml(card.title.toUpperCase())}</text>
  <text x="70" y="1427" font-family="NSans" font-size="17" letter-spacing="4" fill="#b6a2a9">${xml(card.form.split(' / ')[0].toUpperCase())}</text>
  <path d="M68 1470H932" fill="none" stroke="url(#${p}steel)" stroke-width=".7"/>
  <text x="68" y="1520" font-family="NSans" font-size="12" letter-spacing="4" fill="#bca7ae">CRIMSON</text><text x="932" y="1520" text-anchor="end" font-family="NSans" font-size="12" letter-spacing="3" fill="#bca7ae">${String(card.number).padStart(3,'0')}</text>
  <text x="68" y="79" font-family="NSans" font-size="20" letter-spacing="7" fill="#c4b3b8">NIGHT</text>
  <path d="M23 210V23H218M782 23H977V210M23 1390V1577H218M782 1577H977V1390" fill="none" stroke="url(#${p}steel)" stroke-width="1.2"/><rect x="10" y="10" width="980" height="1580" rx="22" fill="none" stroke="#8c5b6c" stroke-width=".6" opacity=".7"/>
  </g></svg>`;
}
