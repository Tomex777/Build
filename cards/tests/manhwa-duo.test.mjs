import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {composeRomanceCutouts,romanceDefaultComposition,validateRomanceComposition,ROMANCE_STAGE} from '../src/manhwa-duo.mjs';
import {loadManhwaRoster,validateArtLock,toRomanceRendererCard,sha256} from '../src/manhwa.mjs';

const roster=await loadManhwaRoster();
const gong=roster.editions.find(x=>x.id==='gong-ja-raviel');
const seedArt=(subject,path,color)=>({
  subject,form:gong.form,page:gong.story.source,
  image:'https://example.org/source-cutout-'+color+'.png',sha256:sha256(color),
  width:1000,height:1500,credit:'Verified source illustrator',rights:'Rights reserved',
  usage:'reference-only',status:'source-pinned-candidate',transparencyVerified:true,
  localPath:path,crop:[0,0,1000,1500],focal:[.5,.45]
});
const pair=()=>({subjects:[
  seedArt(gong.subjects[0],'artwork/manhwa/sss-revival/gong.png','left'),
  seedArt(gong.subjects[1],'artwork/manhwa/sss-revival/raviel.png','right')],
  composition:romanceDefaultComposition()
});
const lock=(duo)=>({version:1,collection:roster.id,editions:{[gong.id]:duo}});
const mockCutout=async color=>{
  const img='<svg xmlns="http://www.w3.org/2000/svg" width="800" height="1200">'
    +'<ellipse cx="400" cy="600" rx="290" ry="510" fill="'+color+'"/></svg>';
  return 'data:image/png;base64,'+(await sharp(Buffer.from(img)).png().toBuffer()).toString('base64');
};

test('a romance duo pins two original source hashes and distinct original file paths',()=>{
  const duo=pair();
  assert.ok(validateArtLock(roster,lock(duo)));
  const card=toRomanceRendererCard(gong,duo,8);
  assert.equal(card.family,'romance');
  assert.equal(card.sourceSubjects.length,2);
  assert.equal(card.source.sha256,duo.subjects[0].sha256);
  assert.equal(card.secondSource.sha256,duo.subjects[1].sha256);
  assert.equal(card.designs[0],'tidelight');
  assert.equal(card.romanceComposition.mode,'two-subject-source-locked');
  assert.notEqual(card.source.localPath,card.secondSource.localPath);
});

test('a wrong or duplicated source cannot masquerade as both romance subjects',()=>{
  const duo=pair();duo.subjects[1].subject='Kim Gong-ja';
  assert.throws(()=>validateArtLock(roster,lock(duo)),/Invalid pinned art identity/);
  const same=pair();same.subjects[1].localPath=same.subjects[0].localPath;
  assert.throws(()=>validateArtLock(roster,lock(same)),/separately pinned/);
  const absent=pair();delete absent.subjects[1];
  assert.throws(()=>validateArtLock(roster,lock(absent)),/Invalid pinned art identity|separately pinned/);
});

test('two graphic layers produce a true transparent paired montage with measurable geometry',async()=>{
  const left=await mockCutout('#ce264c');
  const right=await mockCutout('#2688cf');
  const result=await composeRomanceCutouts({left,right,composition:romanceDefaultComposition()});
  assert.equal(result.subjectCount,2);
  assert.equal(result.derivative,'two-source-pinned-composite');
  const buf=Buffer.from(result.art.slice('data:image/png;base64,'.length),'base64');
  const image=sharp(buf);
  const meta=await image.metadata();
  assert.deepEqual([meta.width,meta.height],[ROMANCE_STAGE.width,ROMANCE_STAGE.height]);
  assert.ok(meta.hasAlpha);
  const read=async(x,y)=>[...await image.extract({left:x,top:y,width:1,height:1}).raw().toBuffer()];
  const leftPixel=await read(280,450),rightPixel=await read(1050,450),bottomPixel=await read(900,2100);
  assert.ok(leftPixel[0]>leftPixel[2], 'left figure is red');
  assert.ok(rightPixel[2]>rightPixel[0], 'right figure is blue');
  assert.equal(bottomPixel[3],0);
});

test('a pair cannot render from duplicated, solid-background or missing images',async()=>{
  assert.throws(()=>validateRomanceComposition({mode:'flattened'}),/cannot be flattened/);
  const good=await mockCutout('#5db888');
  await assert.rejects(composeRomanceCutouts({left:good,right:'',composition:romanceDefaultComposition()}),/separate PNG/);
  const solid='data:image/png;base64,'+(await sharp({create:{width:700,height:1000,channels:4,
    background:{r:5,g:8,b:10,alpha:1}}}).png().toBuffer()).toString('base64');
  await assert.rejects(composeRomanceCutouts({left:good,right:solid,composition:romanceDefaultComposition()}),/meaningful transparent/);
});
