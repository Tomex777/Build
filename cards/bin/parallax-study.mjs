import fs from 'node:fs/promises';
import sharp from 'sharp';
import path from 'node:path';
import {loadCatalogue,inside,atomic,digest} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {createSession} from '../src/render.mjs';
import {renderParallaxStudy} from '../src/parallax.mjs';

const width=Number(process.argv[2]??3000),folder=inside('dist/parallax-study');
const card=(await loadCatalogue()).cards.find(c=>c.id==='byakuya-senbonzakura');
const assets=await acquireArtwork(card),svg=renderParallaxStudy(card,assets),engine=await createSession();
try{
 const {png,qa}=await engine.render(svg,width,assets.focal);
 await fs.mkdir(folder,{recursive:true});
 await atomic(path.join(folder,'Parallax-Study.svg'),svg);
 await atomic(path.join(folder,'Parallax-Study.png'),png);
 await atomic(path.join(folder,'Parallax-Study--phone.png'),await sharp(png).resize(320,512).png().toBuffer());
 await atomic(path.join(folder,'study.json'),JSON.stringify({design:'Parallax',proposedTier:'SSS',canonicalAssignment:false,referenceCharacter:card.name,referenceForm:card.form,referencePowerGrade:card.power.rank,source:card.source,svgSHA256:digest(svg),pngSHA256:digest(png),width,height:width*1.6,qa},null,2)+'\n');
 console.log('Rendered Parallax material study',width,qa);
}finally{await engine.close()}
