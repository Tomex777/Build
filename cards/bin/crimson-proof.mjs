import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {loadCatalogue,inside,atomic,digest,rendererDigest} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg,createSession} from '../src/render.mjs';

const card=(await loadCatalogue()).cards.find(c=>c.id==='kenpachi-tybw');
const assets=await acquireArtwork(card),svg=await cardSvg(card,assets,'crimson'),engine=await createSession();
try {
  const result=await engine.render(svg,3000,assets.focal),folder=inside('dist/crimson-refinement');
  await fs.mkdir(folder,{recursive:true});
  await atomic(path.join(folder,'Crimson-Kenpachi.svg'),svg);
  await atomic(path.join(folder,'Crimson-Kenpachi.png'),result.png);
  await atomic(path.join(folder,'Crimson-Kenpachi--phone.png'),await sharp(result.png).resize(320,512).png().toBuffer());
  await atomic(path.join(folder,'proof.json'),JSON.stringify({purpose:'material-and-depth-comparison',activeEdition:false,id:card.id,form:card.form,power:card.power,proposedCollectionDesign:card.designs[0],source:card.source,recipe:card.recipe,rendererDigest:await rendererDigest(),pngSHA256:digest(result.png),svgSHA256:digest(svg),qa:result.qa},null,2)+'\n');
  console.log('Saved separate Crimson material proof; Kenpachi retains his real form grade.');
} finally {await engine.close();}
