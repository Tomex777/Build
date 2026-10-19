export const FRAME_PATHS = Object.freeze({
  nocturne:'M106 205L158 153H842L894 205V1007L834 1067H166L106 1007Z',
  tidelight:'M67 228Q189 137 397 157L816 171Q918 173 937 291L913 818Q835 1012 586 1077L200 1064Q71 1045 70 897Z',
  vanguard:'M105 260H895V1105H105Z',
  ivory:'M118 288Q118 220 186 220H814Q882 220 882 288V1050Q882 1118 814 1118H186Q118 1118 118 1050Z',
  voltage:'M115 265L185 195H885V1010L810 1120H115Z',
  crimson:'M132 274L188 218H868V1000L792 1096H132Z',
  emberglass:'M108 276L220 216H892V1004L780 1112H108Z',
  obsidian:'M130 290V244H870V1096H130Z',
  eclipse:'M120 365C120 272 230 220 500 220C770 220 880 272 880 365V922C880 1036 697 1092 500 1135C303 1092 120 1036 120 922Z',
  ascendant:'M154 315L390 192H824L892 326V1000L655 1126H176L108 992Z'
});
const PALETTES={vanguard:['#74838a','#1b252c'],ivory:['#7e9da5','#d9e4df'],voltage:['#ccdf79','#172c2d'],emberglass:['#d5a475','#2b1e1b'],obsidian:['#a97f5e','#111b20']};

/** Keep an inner architecture behind the real figure. The outer edge is trim. */
export function frameDepth(svg,design) {
  const d=FRAME_PATHS[design];
  if(!d)throw Error('Missing inner-frame geometry');
  const restored=['nocturne','tidelight'].includes(design);
  if(!restored && !['crimson','eclipse','ascendant'].includes(design)) {
    const [metal,field]=PALETTES[design];
    const rear=`<g data-depth="rear" data-inner-frame="${design}"><path d="${d}" fill="${field}" fill-opacity=".16" stroke="#030a0e" stroke-opacity=".5" stroke-width="13"/><path d="${d}" fill="none" stroke="${metal}" stroke-opacity=".72" stroke-width="${design==='vanguard'?1.8:3}"/><path d="${d}" fill="none" stroke="#fff1d6" stroke-width=".65" opacity=".44" transform="translate(2 2)"/></g>`;
    let found=0;
    svg=svg.replace(/<g data-layer="(?:hero|character)"[^>]*>/,group=>{found++;return rear+group;});
    if(found!==1)throw Error('Expected one foreground figure for inner frame');
  }
  const figures=svg.match(/<g\b(?=[^>]*(?:data-depth="subject"|data-layer="(?:hero|character)"))[^>]*>/g)??[];
  if(figures.length!==1)throw Error('Expected one character foreground');
  svg=svg.replace('</svg>',`<path id="frame-${design}-boundary" data-frame-boundary="true" d="${d}" transform="scale(1 ${restored?1600/1400:1})" fill="none" stroke="none" pointer-events="none"/></svg>`);
  return svg.replace('<svg ','<svg data-character-breakout="required" ');
}
