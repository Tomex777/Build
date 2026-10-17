import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import {hash} from '../src/discovery.mjs';
import {registerArtwork} from '../src/catalog.mjs';
test('Register acquired art, preserve provenance, and refuse duplicate IDs or altered bytes',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'night-catalog-'));
 try{
  const b=await sharp({create:{width:2100,height:2200,channels:4,background:'#778899aa'}}).png().toBuffer();
  const m={id:'krita-test',filename:'art.png',status:'acquired-needs-composition-review',sha256:hash(b),license:'CC BY-SA 4.0',licenseUrl:'https://creativecommons.org/licenses/by-sa/4.0/',sourceUrl:'https://krita.org/en/about/mascot/',artist:'Artist Name',title:'Original title'};
  await fs.writeFile(path.join(dir,'art.png'),b);await fs.writeFile(path.join(dir,'manifest.json'),JSON.stringify(m));
  const entry=await registerArtwork(path.join(dir,'manifest.json'),{root:dir,id:'registered-art'});
  assert.equal(entry.artist,m.artist);assert.equal(entry.originalSha256,m.sha256);assert.equal(entry.visualApproval,false);assert.equal(entry.sha256,hash(await fs.readFile(path.join(dir,entry.localPath))));
  await assert.rejects(registerArtwork(path.join(dir,'manifest.json'),{root:dir,id:'registered-art'}),/already exists/);
  await fs.writeFile(path.join(dir,'art.png'),'modified');
  await assert.rejects(registerArtwork(path.join(dir,'manifest.json'),{root:dir,id:'modified-art'}),/hash does not match/);
 }finally{await fs.rm(dir,{recursive:true,force:true})}
});
