import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const mime=f=>f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':f.endsWith('.txt')?'text/plain':'text/html';
const server=createServer((req,res)=>{
 let p=new URL(req.url,'http://127.0.0.1').pathname;
 let file=p==='/games'?'index.html':p.startsWith('/jigsaw')?p.replace(/^\//,''):null;
 if(file?.endsWith('/'))file+='index.html';
 if(!file||file.includes('..')){res.writeHead(404);res.end('Not found');return;}
 try{const bytes=readFileSync(path.join(root,file));res.writeHead(200,{'content-type':mime(file),'cache-control':'no-store'});res.end(bytes)}
 catch{res.writeHead(404);res.end('missing '+file)}
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const url='http://127.0.0.1:'+server.address().port;
mkdirSync(path.join(root,'visual-output'),{recursive:true});
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(url+'/jigsaw/');
 await page.locator('#galleryGrid .gallery-card').first().waitFor();
 assert.equal(await page.locator('#galleryGrid .gallery-card').count(),8,'Seven art themes and a private photo upload');
 assert.equal(await page.locator('#playScreen').isVisible(),false,'Puzzle gallery opens before choosing picture');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-GALLERY-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 await page.locator('[data-filter="anime"]').click();
 assert.equal(await page.locator('#galleryGrid .gallery-card:visible').count(),1,'Anime category is available');
 await page.locator('[data-theme="anime"]').click();
 await page.locator('#puzzleCanvas').waitFor({state:'visible'});
 const first=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.equal(first.theme,'anime');
 assert.equal(first.pieces,12);assert.equal(first.placed,0);assert.equal(first.board.w>340,true,'Board nearly fills mobile width');
 assert.equal(first.tray.y>first.board.y+first.board.h,true,'All loose pieces belong in the tray below');
 assert.equal(await page.locator('.game-topline .top-settings').count(),1,'Game settings stay in the header');
 assert.equal(await page.locator('.game-topline .wordmark .brand-logo use').getAttribute('href'),'#night-mark','Actual NIGHT brand mark');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-PLAY-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 // Real Chromium touch should drag the topmost visible jigsaw piece into place.
 const cdp=await page.context().newCDPSession(page);
 const info=await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot(),p=s.pieces.at(-1);
  const board=document.getElementById('puzzleCanvas'),r=board.getBoundingClientRect();
  return {fromX:r.left+p.x+((s.board.w/4)/2),fromY:r.top+p.y+((s.board.h/3)/2),
   toX:r.left+p.correctX+((s.board.w/4)/2),toY:r.top+p.correctY+((s.board.h/3)/2)}});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:info.fromX,y:info.fromY,id:11}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:(info.fromX+info.toX)/2,y:(info.fromY+info.toY)/2,id:11}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:info.toX,y:info.toY,id:11}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 await page.waitForFunction(()=>window.__nightJigsawTest.snapshot().placed>=1,null,{timeout:1200});
 assert.equal(await page.locator('#progress').innerText(),'1 / 12','Touch drag moves piece into image');
 await page.locator('#hintButton').click();
 assert.equal(await page.locator('#progress').innerText(),'2 / 12','Hint places one unsolved piece');
 const beforeReload=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 await page.reload();await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 const afterReload=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.equal(afterReload.placed,beforeReload.placed,'Reload restores solved pieces');
 assert.equal(afterReload.theme,'anime','Reload preserves puzzle image');
 assert.equal(afterReload.moves,beforeReload.moves,'Reload retains move count');
 // Customizes settings without leaving the full-screen game.
 await page.locator('#settingsButton').click();
 await page.locator('[data-pieces="24"]').click();
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().pieces),24);
 assert.equal(await page.locator('#progress').textContent(),'0 / 24','Difficulty starts a clean new round');
 await page.locator('#settingsButton').click();
 await page.locator('#guideSwitch').uncheck();
 await page.locator('#soundSwitch').uncheck();
 await page.locator('[data-close="settingsDialog"]').click();
 await page.locator('#previewButton').click();
 assert.equal(await page.locator('#previewDialog').isVisible(),true,'Reference picture is available in a dialog');
 await page.locator('[data-close="previewDialog"]').click();
 await page.reload();await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 assert.equal(await page.locator('#guideSwitch').isChecked(),false,'Guide preference persists');
 assert.equal(await page.locator('#soundSwitch').isChecked(),false,'Audio preference persists');
 await page.locator('#pickPicture').click();
 await page.locator('#galleryPanel').waitFor({state:'visible'});
 assert.equal(new URL(page.url()).pathname,'/jigsaw/','Gallery and board stay in the same full-screen game route');
 // Private user photo becomes a playable puzzle and survives a reload.
 const tinyPng=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9QjM8yAAAAAASUVORK5CYII=','base64');
 await page.locator('#imageFile').setInputFiles({name:'photo.png',mimeType:'image/png',buffer:tinyPng});
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().theme==='custom',null,{timeout:2500});
 assert.equal(await page.locator('#puzzleCanvas').isVisible(),true,'Uploaded image creates a puzzle');
 await page.reload();await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().theme==='custom',null,{timeout:2500});
 await page.locator('#hintButton').click();
 assert.equal(await page.locator('#progress').textContent(),'1 / 24','Custom photo puzzle works after reload');
 for(let i=0;i<24;i++)await page.evaluate(i=>window.__nightJigsawTest.place(i),i);
 await page.locator('#victoryDialog').waitFor({state:'visible',timeout:2500});
 assert.equal(await page.locator('#victoryTitle').textContent(),'Beautifully done!');
 assert.equal(await page.locator('#victoryPieces').textContent(),'24');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-WIN-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 await page.locator('#victoryNext').click();assert.equal(await page.locator('#galleryPanel').isVisible(),true);
 assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-jigsaw-v1')),null,'Finished puzzle is not resumed as unfinished');
 assert.deepEqual(errors,[],'Jigsaw game must have no browser exceptions');

 // Desktop receives a side tray and no horizontal overflow.
 const desktop=await browser.newPage({viewport:{width:1280,height:850}});const deskErrors=[];
 desktop.on('pageerror',e=>deskErrors.push(String(e)));
 await desktop.goto(url+'/jigsaw/');await desktop.locator('[data-theme="nature"]').click();
 const dl=await desktop.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.ok(dl.tray.x>dl.board.x+dl.board.w,'On desktop the tray sits alongside the board');
 assert.ok(await desktop.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
 await desktop.screenshot({path:path.join(root,'visual-output/JIGSAW-PLAY-DESKTOP.jpg'),type:'jpeg',quality:75,fullPage:true});
 assert.deepEqual(deskErrors,[]);
 await desktop.close();
 await page.goto(url+'/games');await page.locator('#view-games').waitFor({state:'visible'});
 assert.equal(await page.locator('[data-jigsaw]').count(),1,'Jigsaw is present in main game library');
 await page.locator('[data-jigsaw]').click();await page.waitForURL('**/jigsaw/');
 assert.equal(await page.locator('#galleryPanel').isVisible(),true,'Library opens integrated jigsaw route');
 console.log('PASS NIGHT Play Jigsaw: gallery, artwork, real touch drag, hints, save/reload, settings, upload, win and responsive desktop.');
}finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok))}
