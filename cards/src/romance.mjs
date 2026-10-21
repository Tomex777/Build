import {renderRestoredCard} from '../renderer/src/restored.mjs';
import {frameDepth} from './frame-depth.mjs';
import {xml} from '../renderer/src/geometry.mjs';

// Reuse NIGHT architecture, materials, fonts and depth; retain two original
// images as independently identified, embedded source layers.
export function renderRomanceCard(card, assets) {
  if (!['nocturne','tidelight'].includes(card.design)) throw Error('Unknown romance finish');
  if (card.subjects?.length!==2 || assets.subjects?.length!==2) throw Error('A duo requires two source subjects');
  if (new Set(card.subjects.map(s=>s.id)).size!==2) throw Error('Duo subjects must be distinct');
  for (let i=0;i<2;i++) {
    const s=assets.subjects[i],p=card.subjects[i];
    if(s.id!==p.id || !/^[a-z][a-z0-9-]+$/.test(s.id)) throw Error('Subject identity mismatch');
    if(!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(s.art)) throw Error('Embed original PNG artwork');
    if(!/^[a-f0-9]{64}$/.test(s.sha256)) throw Error('Source checksum required');
    const [x,y,r,b]=s.alphaBBox??[];
    if(![x,y,r,b,s.width,s.height].every(Number.isFinite)||x<0||y<0||r>s.width||b>s.height||r<=x||b<=y) throw Error('Invalid source alpha bounds');
    if(![p.x,p.y,p.width].every(Number.isFinite)||p.width<=0) throw Error('Invalid duo placement');
  }
  const first=assets.subjects[0];
  const def={id:card.id,name:card.name,edition:card.bond,number:card.number,collection:'ROMANCE',material:card.series,artwork:card.id,theme:card.design,seed:card.seed??93,layout:card.design==='nocturne'?'cutout':'fullart',composition:card.design==='nocturne'?'orbital':'pearl'};
  const common={...assets,art:first.art,artWidth:first.width,artHeight:first.height,sourceUrl:first.page,licenseUrl:first.page,artist:'Original character artwork',license:'Rights reserved',outputLicense:'Personal design study',title:card.name,rights:'Original artwork rights reserved'};
  // The existing frame adapter expects a single SVG root. Apply it before
  // adding nested subject viewports, so its boundary remains at the root.
  let svg=frameDepth(renderRestoredCard(def,common,{prefix:`romance-${card.id}-`}),card.design);
  let replaced=0;
  svg=svg.replace(/<image\b[^>]*\bid="([^"]*subject)"[^>]*\/>/g,(opening,id)=>{
    replaced++;
    const figures=assets.subjects.map((s,i)=>{
      const p=card.subjects[i],[left,top,right,bottom]=s.alphaBBox,w=right-left,h=bottom-top;
      return `<svg id="${id}-${s.id}" data-subject-viewport="${s.id}" x="${p.x}" y="${p.y}" width="${p.width}" height="${p.width*h/w}" viewBox="${left} ${top} ${w} ${h}" overflow="visible"><image id="${id}-${s.id}-image" data-romance-subject="${s.id}" data-source-sha256="${s.sha256}" href="${s.art}" width="${s.width}" height="${s.height}"/></svg>`;
    }).join('');
    // Preserve the portrait renderer's equal-scale compensation.
    const transform=opening.match(/\btransform="[^"]*"/)?.[0]??'';
    return `<g id="${id}" ${transform} data-romance-duo="true">${figures}</g>`;
  });
  if(replaced!==1)throw Error('Shared subject structure changed; review romance adapter');
  svg=svg.replace('ARTIST EDITIONS','ROMANCE');
  svg=svg.replace(/<text\b[^>]*data-text="name"[^>]*>/,tag=>tag.replace(/font-size="([^"]+)"/,(_,size)=>`font-size="${Number(size)*.84}" data-name="true" data-title-scale=".84"`));
  svg=svg.replace(/<path d="M75 1099H231M769 1099H925"[^>]*\/>/,'');
  svg=svg.replace(/<text\b(?=[^>]*\bx="76")(?=[^>]*\by="1333")[^>]*>[\s\S]*?<\/text>/,tag=>tag.replace(/font-size="[^"]*"/,`font-size="${card.series.length>35?15:18}"`).replace(/letter-spacing="[^"]*"/,'letter-spacing="1.2"').replace(/>[^<]*<\/text>/,`>${xml(card.series)}</text>`));
  svg=svg.replace(/<a href="[^"]*"><text\b(?=[^>]*\bx="925")(?=[^>]*\by="1333")[^>]*>[\s\S]*?<\/a>/,'');
  svg=svg.replace(/<metadata>[\s\S]*?<\/metadata>/,`<metadata>${xml(JSON.stringify({renderer:'NIGHT restored romance adapter 1',card:card.id,family:'romance',design:card.design,bond:card.bond,sourceImages:assets.subjects.map(({art,...s})=>s),characterArtworkGenerated:false,powerRank:null,export:[3000,4800],rights:'Original artwork rights reserved; personal design study'}))}</metadata>`);
  return svg.replace('<svg ',`<svg data-family="romance" data-duo-subjects="2" data-card-id="${card.id}" `);
}
