import assert from 'node:assert/strict';
import {loadCatalogue} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg,createSession} from '../src/render.mjs';

const card=(await loadCatalogue()).cards.find(c=>c.id==='yoruichi-featured');
const assets=await acquireArtwork(card),svg=await cardSvg(card,assets,'nocturne');
const engine=await createSession();
try {
  const result=await engine.render(svg,320,assets.focal);
  assert.ok(result.qa.characterBreakout.longestCrossing>=24);
  const contained=svg.replace(/<image\b[^>]*\bid="[^"]*subject"[^>]*\/>/,image=>image.replace(/\bx="[^"]*"/,'x="400"').replace(/\by="[^"]*"/,'y="400"').replace(/\bwidth="[^"]*"/,'width="200"').replace(/\bheight="[^"]*"/,'height="256"'));
  assert.notEqual(contained,svg);
  await assert.rejects(()=>engine.render(contained,320,assets.focal),/does not visibly cross the inner frame/);
  console.log('PASS: real character breakout; contained figure with large separate aura is rejected.');
} finally {await engine.close();}
