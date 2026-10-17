import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import {allowedURL,licensePolicy,parseKrita,parseCommons,request,acquire,hash} from '../src/discovery.mjs';
const page=(license=true)=>`<p>${license?'https://creativecommons.org/licenses/by-sa/4.0/':''}</p><h3>Kiki Splash Art Gallery</h3><a href=https://krita.org/image.png><figure><img src=thumb.png alt="Kiki Portrait"></figure></a><h3>Kiki's Alternative Designs</h3><a href=https://krita.org/fan.png><img alt="Fan art"></a>`;
test('Only modification-friendly, commercial CC licenses are accepted',()=>{
 assert(licensePolicy('https://creativecommons.org/publicdomain/zero/1.0/deed.en'));
 for(const s of ['by/4.0','by/3.0','by-sa/4.0','by-sa/3.0'])assert(licensePolicy(`https://creativecommons.org/licenses/${s}/`));
 for(const s of ['by-nc/4.0','by-nd/4.0','by-nc-sa/4.0'])assert.equal(licensePolicy(`https://creativecommons.org/licenses/${s}/`),null);
 for(const s of ['https://example.com/license','http://creativecommons.org/licenses/by/4.0/','https://creativecommons.org.evil.org/licenses/by/4.0/'])assert.equal(licensePolicy(s),null);
});
test('Provider URLs reject private addresses, ports, credentials and lookalike hosts',()=>{
 for(const u of ['http://krita.org/a','https://127.0.0.1/a','https://169.254.169.254/','https://krita.org.evil.com/a','https://name:secret@krita.org/a','https://krita.org:8080/a'])assert.throws(()=>allowedURL(u));
 assert.equal(allowedURL('https://cdn.kde.org/art.png'),'https://cdn.kde.org/art.png');
});
test('Krita discovery follows high-resolution links, excludes separately licensed fan art, and fails closed when the license changes',()=>{
 const r=parseKrita(page());assert.equal(r.length,1);assert.equal(r[0].originalUrl,'https://krita.org/image.png');assert.equal(r[0].shareAlike,true);
 assert.throws(()=>parseKrita(page(false)),/license markup changed/);
});
test('Commons rejects low-resolution, restricted and incomplete-license records',()=>{
 const base={pageid:7,title:'File:Example.png',imageinfo:[{url:'https://upload.wikimedia.org/a.png',descriptionurl:'https://commons.wikimedia.org/wiki/File:Example.png',width:4000,height:4000,mime:'image/png',extmetadata:{Artist:{value:'<b>A. Artist</b>'},LicenseUrl:{value:'https://creativecommons.org/licenses/by/4.0/'}}}]};
 assert.equal(parseCommons({query:{pages:{7:base}}}).length,1);
 for(const mutate of [x=>x.imageinfo[0].width=200,x=>x.imageinfo[0].extmetadata.LicenseUrl.value='',x=>x.imageinfo[0].extmetadata.Restrictions={value:'trademark'}]){
  const copy=structuredClone(base);mutate(copy);assert.equal(parseCommons({query:{pages:{7:copy}}}).length,0);
 }
});
test('Downloads reject over-limit streams and blocked redirects before following them',async()=>{
 await assert.rejects(request('https://krita.org/a',{maxBytes:4,fetchImpl:async()=>new Response('1234567')}),/download limit/);
 await assert.rejects(request('https://krita.org/a',{fetchImpl:async()=>new Response(null,{status:302,headers:{location:'https://127.0.0.1/secret'}})}),/approved HTTPS/);
});
test('Acquisition verifies current rights, decodes actual dimensions and writes a content hash and attribution',async()=>{
 const png=await sharp({create:{width:2100,height:2200,channels:3,background:'#445566'}}).png().toBuffer();
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'night-art-test-'));
 const record=parseKrita(page())[0];const fetchImpl=async u=>new Response(u.includes('/about/')?page():png);
 try{
  const r=await acquire(record,{destination:dir,fetchImpl});assert.equal(r.width,2100);assert.equal(r.height,2200);assert.equal(r.sha256,hash(png));assert.equal(r.visualApproval,false);
  assert.match(await fs.readFile(path.join(dir,r.id+'-ATTRIBUTION.txt'),'utf8'),/CC BY-SA/);
  const revoked=async u=>new Response(u.includes('/about/')?page(false):png);
  await assert.rejects(acquire(record,{destination:dir,fetchImpl:revoked}),/license markup changed/);
  await assert.rejects(acquire(record,{destination:dir,minWidth:3000,fetchImpl}),/pixel\/format/);
 }finally{await fs.rm(dir,{recursive:true,force:true})}
});
