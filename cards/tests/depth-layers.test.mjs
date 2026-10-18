import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {loadCatalogue,inside,reviewFingerprint} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';

const yoruichi=async()=>(await loadCatalogue()).cards.find(c=>c.id==='yoruichi-featured');
const decode=uri=>sharp(Buffer.from(uri.split(',')[1],'base64')).ensureAlpha().raw().toBuffer({resolveWithObject:true});

test('the silhouette removes the baked-in aura while preserving original character pixels',async()=>{
  const card=await yoruichi(),assets=await acquireArtwork(card);
  const fore=await decode(assets.depthLayers.nocturne.foreground),base=await decode(assets.art);
  const [left,top,width,height]=card.recipe.crop;
  const source=await sharp(await fs.readFile(inside(card.source.localPath))).extract({left,top,width,height}).ensureAlpha().raw().toBuffer();
  const pixel=(data,x,y)=>data.subarray((y*width+x)*4,(y*width+x)*4+4);
  assert.deepEqual(fore.info,base.info);
  for(const [x,y] of [[100,250],[650,200]]){
    assert.ok(pixel(base.data,x,y)[3]>32,'This sample contains the original aura');
    assert.equal(pixel(fore.data,x,y)[3],0,'The foreground must exclude the aura');
  }
  for(const [x,y] of [[420,300],[210,500],[695,500]])
    assert.deepEqual(pixel(fore.data,x,y),pixel(source,x,y),'Face, clothing and white armour use the pinned original pixels');
  assert.equal(pixel(fore.data,400,0)[3],0,'The source crop boundary is feathered');
});

test('layering preserves the unconfigured finish and glass lettering, and places aura inside the frame behind the figure',async()=>{
  const card=await yoruichi(),original=structuredClone(card);
  delete original.recipe.depthLayers;
  const assets=await acquireArtwork(card),before=await acquireArtwork(original);
  assert.equal(assets.art,before.art);
  assert.equal(await cardSvg(card,assets,'emberglass'),await cardSvg(original,before,'emberglass'));
  const afterSvg=await cardSvg(card,assets,'nocturne'),beforeSvg=await cardSvg(original,before,'nocturne');
  const rank=svg=>svg.slice(svg.indexOf('<g data-layer="power-rank"'));
  assert.equal(rank(afterSvg),rank(beforeSvg));
  assert.ok(afterSvg.indexOf('data-layer="illustration-aura"')<afterSvg.indexOf('data-depth="subject"'));
  assert.match(afterSvg,/<g data-layer="illustration-aura"[^>]+clip-path="url\(#[^"]+-aura-window\)"/);
  assert.ok(afterSvg.includes(`data-foreground-mask-sha256="${card.recipe.depthLayers.nocturne.mask.sha256}"`));
  assert.notEqual(reviewFingerprint(card,'nocturne','renderer'),reviewFingerprint(original,'nocturne','renderer'));
});

test('a silhouette cannot silently transfer to another source, crop or checksum',async()=>{
  const card=await yoruichi();
  for(const field of ['sourceSHA256','crop','sha256']){
    const changed=structuredClone(card);
    changed.recipe.depthLayers.nocturne.mask[field]=field==='crop'?[0,0,740,750]:'0'.repeat(64);
    await assert.rejects(()=>acquireArtwork(changed),/Silhouette mask/);
  }
});
