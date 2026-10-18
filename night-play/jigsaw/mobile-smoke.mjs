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
 // Stub image responses so gameplay tests remain deterministic even if the CDN is offline.
 // Production always requests real Unsplash photographs, never these fixtures.
 const fakeImage=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9QjM8yAAAAAASUVORK5CYII=','base64');
 const mockPhoto=route=>route.fulfill({status:200,contentType:'image/png',body:fakeImage,headers:{'access-control-allow-origin':'*'}});
 await page.route('https://images.unsplash.com/**',mockPhoto);
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(url+'/jigsaw/');
 await page.locator('#galleryGrid .gallery-card').first().waitFor();
 assert.equal(await page.locator('#galleryGrid .gallery-card').count(),15,'Fourteen photographic puzzles and a private photo upload');
 const label=await page.locator('#galleryGrid .gallery-image').first().evaluate(el=>getComputedStyle(el,'::after').content);
 assert.ok(label==='none'||label==='normal','Jigsaw gallery pictures must not show Start Puzzle arrow or badge');
 const photoSources=await page.locator('#galleryGrid .gallery-image img').evaluateAll(imgs=>imgs.map(x=>x.src));
 assert.equal(photoSources.length,14,'Every gallery item has a photograph');
 assert.equal(new Set(photoSources).size,14,'Photographs are distinct');
 assert.ok(photoSources.every(src=>src.startsWith('https://images.unsplash.com/photo-')),'No generated-art gallery cards');
 assert.equal(await page.locator('#playScreen').isVisible(),false,'Puzzle gallery opens before choosing picture');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-GALLERY-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 console.log('NIGHT_JIGSAW_GALLERY_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:50,fullPage:true})).toString('base64'));
 await page.locator('[data-filter="flowers"]').click();
 assert.equal(await page.locator('#galleryGrid .gallery-card:visible').count(),2,'Flower photographs are a category');
 await page.locator('[data-theme="photo-pink-blooms"]').click();
 await page.locator('#puzzleCanvas').waitFor({state:'visible'});
 const first=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.equal(first.theme,'photo-pink-blooms');
 assert.equal(first.pieces.length,24,'New players begin with 24 jigsaw pieces');
 assert.equal(first.placed,0);
 assert.equal(await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot();return s.board.w>=document.getElementById('puzzleCanvas').width-24}),true,'Puzzle board fills nearly the full canvas width');
 assert.equal(first.guide,false,'Picture guide must be off by default');
 assert.equal(await page.locator('#guideSwitch').isChecked(),false,'Guide switch starts unchecked');
 assert.equal(first.challengeMode,'relaxed');
 assert.equal(await page.locator('#previewRemaining').textContent(),'3 left');
 assert.equal(await page.locator('#hintRemaining').textContent(),'3 left');
 assert.equal(await page.locator('#canvasWrapper').evaluate(el=>getComputedStyle(el).borderTopWidth),'0px','No boxed tray frame');
 assert.ok(first.board.y>20&&first.board.y+first.board.h<await page.evaluate(()=>document.getElementById('puzzleCanvas').height-20),'Puzzle is centered between two loose-piece areas on mobile');
 assert.equal(await page.locator('#edgesButton').count(),1,'Edge-piece filtering is available');
 assert.equal(await page.locator('#zoomIn').count(),1,'Zoom control is available');
 assert.equal(first.zoom,1,'Tabletop starts at natural 100% size');
 await page.locator('#zoomIn').click();
 assert.ok((await page.evaluate(()=>window.__nightJigsawTest.snapshot())).zoom>1,'Zoom control enlarges tabletop');
 await page.locator('#zoomReset').click();
 assert.equal((await page.evaluate(()=>window.__nightJigsawTest.snapshot())).zoom,1,'Zoom reset restores natural size');
 await page.locator('#edgesButton').click();
 assert.equal((await page.evaluate(()=>window.__nightJigsawTest.snapshot())).edgeOnly,true,'Edges filter dims interior pieces');
 await page.locator('#edgesButton').click();
 assert.equal(await page.locator('.game-topline .top-settings').count(),1,'Game settings stay in the header');
 assert.equal(await page.locator('.game-topline .wordmark .brand-logo use').getAttribute('href'),'#night-mark','Actual NIGHT brand mark');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-PLAY-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 console.log('NIGHT_JIGSAW_PLAY_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:60,fullPage:true})).toString('base64'));
 // Real Chromium touch should drag the topmost visible jigsaw piece into place.
 const cdp=await page.context().newCDPSession(page);
 const info=await page.evaluate(()=>{
  const s=window.__nightJigsawTest.snapshot(),board=document.getElementById('puzzleCanvas'),r=board.getBoundingClientRect();
  const sx=r.width/board.width,sy=r.height/board.height;
  // On small phones, later loose rows are BELOW the viewport. Start the
  // browser gesture with a real visible puzzle piece instead of an offscreen one.
  const visible=s.pieces.filter(p=>{
   const y=r.top+(p.y+s.board.pieceHeight/2)*sy;
   return !p.placed&&y>r.top&&y<innerHeight-30;
  });
  if(!visible.length)throw new Error('No loose piece reachable on mobile viewport');
  const p=visible[visible.length-1];
  return {fromX:r.left+(p.x+s.board.pieceWidth/2)*sx,fromY:r.top+(p.y+s.board.pieceHeight/2)*sy,
   toX:r.left+(p.correctX+s.board.pieceWidth/2)*sx,toY:r.top+(p.correctY+s.board.pieceHeight/2)*sy};
 });
 assert.equal(await page.locator('#dragCanvas').count(),1,'Puzzle moves on an independent drawing layer');
 assert.equal(await page.locator('#dragCanvas').evaluate(el=>getComputedStyle(el).pointerEvents),'none','Drag layer never intercepts finger touches');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:info.fromX,y:info.fromY,id:11}]});
 await page.waitForFunction(()=>window.__nightJigsawTest.snapshot().selected!==null);
 const paintStart=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 const initialPiece=paintStart.selected;
 assert.ok(paintStart.staticPaints>0,'Static board painted on initial pickup');
 const dragMidX=(info.fromX+info.toX)/2,dragMidY=(info.fromY+info.toY)/2;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:dragMidX,y:dragMidY,id:11}]});
 // Repeated real Chromium touch movement must never repaint the full scene.
 for(let step=1;step<=16;step++){
  const t=.5+step*.025;
  await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{
   x:info.fromX+(info.toX-info.fromX)*t,
   y:info.fromY+(info.toY-info.fromY)*t,id:11
  }]});
 }
 const touchEndX=info.fromX+(info.toX-info.fromX)*.9;
 const touchEndY=info.fromY+(info.toY-info.fromY)*.9;
 const fingerDelta=await page.evaluate(({x,y})=>{
  const board=document.getElementById('puzzleCanvas'),r=board.getBoundingClientRect();
  return {x:(x/r.width)*board.width,y:(y/r.height)*board.height}
 },{x:touchEndX-info.fromX,y:touchEndY-info.fromY});
 // Chromium can combine move events; wait for the LAST move, not an earlier one.
 await page.waitForFunction(({x,y})=>{
  const s=window.__nightJigsawTest.snapshot();
  return s.selected&&Math.abs(s.selected.x-x)<3&&Math.abs(s.selected.y-y)<3;
 },{x:initialPiece.x+fingerDelta.x,y:initialPiece.y+fingerDelta.y},{timeout:3000});
 const moving=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.equal(moving.staticPaints,paintStart.staticPaints,'Jigsaw drag never redraws 24 static pieces');
 assert.ok(moving.dragPaints>paintStart.dragPaints+2,'Only the selected piece redraws with the finger');
 assert.ok(Math.abs(moving.selected.x-(initialPiece.x+fingerDelta.x))<3 &&
   Math.abs(moving.selected.y-(initialPiece.y+fingerDelta.y))<3,
   'Picked-up piece follows finger coordinates without visible offset or lag');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:info.toX,y:info.toY,id:11}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 assert.ok((await page.evaluate(()=>window.__nightJigsawTest.snapshot())).staticPaints>paintStart.staticPaints,'Full scene only redraws on release');
 await page.waitForFunction(()=>window.__nightJigsawTest.snapshot().placed>=1,null,{timeout:1200});
 assert.equal(await page.locator('#progress').innerText(),'1 / 24','Touch drag moves piece into image');
 await page.locator('#hintButton').click();
 assert.equal(await page.locator('#progress').innerText(),'2 / 24','Hint places one unsolved piece');
 assert.equal((await page.evaluate(()=>window.__nightJigsawTest.snapshot())).placed,2,'Touch drag and hint both count placed pieces');
 const beforeReload=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 await page.reload();
 assert.equal(await page.locator('#resumePanel').isVisible(),true,'Resume choice shown without a gallery flash');
 if(beforeReload.theme==='photo-pink-blooms')console.log('NIGHT_JIGSAW_RESUME_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:55,fullPage:true})).toString('base64'));
 assert.equal(await page.locator('#galleryPanel').isVisible(),false,'Gallery stays hidden during resume prompt');
 await page.locator('#resumePlaying').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 const afterReload=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.equal(afterReload.placed,beforeReload.placed,'Reload restores solved pieces');
 assert.equal(afterReload.theme,'photo-pink-blooms','Reload preserves puzzle image');
 assert.equal(afterReload.moves,beforeReload.moves,'Reload retains move count');
 assert.equal(afterReload.usedHints,1,'Reload must not refund a spent hint');
 // Customizes settings without leaving the full-screen game.
 await page.locator('#settingsButton').click();
 await page.locator('[data-pieces="48"]').click();
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().pieces.length),48);
 assert.equal(await page.locator('#progress').textContent(),'0 / 48','Difficulty starts a clean new round');
 await page.locator('#settingsButton').click();
 await page.locator('#guideSwitch').check();
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().guide),true,'Picture guide can be enabled');
 await page.locator('#guideSwitch').uncheck();
 await page.locator('#soundSwitch').uncheck();
 await page.locator('[data-close="settingsDialog"]').click();
 await page.locator('#previewButton').click();
 assert.equal(await page.locator('#previewDialog').isVisible(),true,'Timed reference preview opens');
 assert.equal(await page.locator('#previewRemaining').textContent(),'2 left','Preview spends one peek');
 await page.locator('[data-close="previewDialog"]').click();
 await page.reload();
 assert.equal(await page.locator('#resumePanel').isVisible(),true,'Resume choice shown without a gallery flash');
 assert.equal(await page.locator('#galleryPanel').isVisible(),false,'Gallery stays hidden during resume prompt');
 await page.locator('#resumePlaying').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
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
 await page.reload();
 assert.equal(await page.locator('#resumePanel').isVisible(),true,'Private photo offers Continue or New');
 await page.locator('#resumePlaying').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().theme==='custom'&&window.__nightJigsawTest?.snapshot().activeRound,null,{timeout:2500});
 await page.locator('#hintButton').click();
 assert.equal(await page.locator('#progress').textContent(),'1 / 48','Custom photo puzzle works after reload');
 for(let i=0;i<48;i++)await page.evaluate(i=>window.__nightJigsawTest.place(i),i);
 await page.locator('#victoryDialog').waitFor({state:'visible',timeout:2500});
 assert.equal(await page.locator('#victoryTitle').textContent(),'Beautifully done!');
 assert.equal(await page.locator('#victoryPieces').textContent(),'48');
 await page.screenshot({path:path.join(root,'visual-output/JIGSAW-WIN-MOBILE.jpg'),type:'jpeg',quality:80,fullPage:true});
 await page.locator('#victoryNext').click();assert.equal(await page.locator('#galleryPanel').isVisible(),true);
 assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-jigsaw-v1')),null,'Finished puzzle is not resumed as unfinished');
 // Help is scarce in every mode, including Relaxed.
 await page.locator('[data-theme="photo-tabby-cat"]').click();
 assert.equal(await page.locator('#previewRemaining').textContent(),'3 left');
 for(let i=0;i<3;i++){
  await page.locator('#previewButton').click();
  await page.locator('#previewDialog').waitFor({state:'visible'});
  await page.locator('[data-close="previewDialog"]').click();
  await page.locator('#previewDialog').waitFor({state:'hidden'});
 }
 assert.equal(await page.locator('#previewButton').isDisabled(),true,'Relaxed has max three preview peeks');
 for(let i=0;i<3;i++)await page.locator('#hintButton').click();
 assert.equal(await page.locator('#hintButton').isDisabled(),true,'Relaxed has max three hints');
 await page.reload();
 assert.equal(await page.locator('#resumePanel').isVisible(),true,'Resume choice shown without a gallery flash');
 assert.equal(await page.locator('#galleryPanel').isVisible(),false,'Gallery stays hidden during resume prompt');
 await page.locator('#resumePlaying').click();
 await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 assert.equal(await page.locator('#hintButton').isDisabled(),true,'Reload cannot replenish hints');
 assert.equal(await page.locator('#previewButton').isDisabled(),true,'Reload cannot replenish peeks');

 await page.locator('#settingsButton').click();
 await page.locator('[data-mode="challenge"]').click();
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().challengeMode),'challenge');
 assert.equal(await page.locator('#hintRemaining').textContent(),'1 left');
 assert.equal(await page.locator('#previewRemaining').textContent(),'1 left');
 await page.locator('#hintButton').click();
 assert.equal(await page.locator('#hintButton').isDisabled(),true,'Challenge grants one hint');
 await page.locator('#previewButton').click();
 await page.locator('[data-close="previewDialog"]').click();
 assert.equal(await page.locator('#previewButton').isDisabled(),true,'Challenge grants one picture peek');
 await page.locator('#settingsButton').click();
 await page.locator('[data-mode="memory"]').click();
 await page.locator('#previewDialog').waitFor({state:'visible',timeout:2500});
 assert.equal(await page.locator('#guideSwitch').isDisabled(),true,'Memory disables even the faint guide');
 assert.equal(await page.locator('#hintButton').isDisabled(),true,'Memory grants zero hints');
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().usedPreviews),1,'Memory automatically shows the only preview');
 await page.locator('[data-close="previewDialog"]').click();
 await page.locator('#previewDialog').waitFor({state:'hidden'});
 assert.equal(await page.locator('#previewButton').isDisabled(),true,'Memory cannot peek twice');
 await page.locator('#settingsButton').click();
 await page.locator('[data-pieces="144"]').click();
 await page.locator('#previewDialog').waitFor({state:'visible',timeout:2500});
 await page.locator('[data-close="previewDialog"]').click();
 assert.equal(await page.evaluate(()=>window.__nightJigsawTest.snapshot().pieces.length),144,'144 piece difficulty is playable');
 assert.equal(await page.locator('#progress').textContent(),'0 / 144');
 const huge=await page.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.ok(huge.board.y>90,'Master difficulty still reserves space for loose pieces above the board');
 await page.locator('#pickPicture').click();
 assert.deepEqual(errors,[],'Jigsaw game must have no browser exceptions');

 // Desktop receives a side loose-piece space and no horizontal overflow.
 const desktop=await browser.newPage({viewport:{width:1280,height:850}});const deskErrors=[];
  await desktop.route('https://images.unsplash.com/**',mockPhoto);
 desktop.on('pageerror',e=>deskErrors.push(String(e)));
 await desktop.goto(url+'/jigsaw/');await desktop.locator('[data-theme="photo-alpine-lake"]').click();
  await desktop.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 const dl=await desktop.evaluate(()=>window.__nightJigsawTest.snapshot());
 assert.ok(dl.board.x>90&&dl.board.x+dl.board.w<await desktop.evaluate(()=>document.getElementById('puzzleCanvas').width-90),'Desktop picture is centered between left and right scattering areas');
 assert.ok(await desktop.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
 await desktop.screenshot({path:path.join(root,'visual-output/JIGSAW-PLAY-DESKTOP.jpg'),type:'jpeg',quality:75,fullPage:true});
 console.log('NIGHT_JIGSAW_DESKTOP_JPEG_BASE64='+(await desktop.screenshot({type:'jpeg',quality:50,fullPage:true})).toString('base64'));
 assert.deepEqual(deskErrors,[]);
 await desktop.close();
 await page.goto(url+'/games');await page.locator('#view-games').waitFor({state:'visible'});
 assert.equal(await page.locator('[data-jigsaw]').count(),1,'Jigsaw is present in main game library');
 await page.locator('[data-jigsaw]').click();await page.waitForURL('**/jigsaw/');
 assert.equal(await page.locator('#resumePanel').isVisible(),true,'Returning from the library offers Continue or New Picture');
  assert.equal(await page.locator('#galleryPanel').isVisible(),false,'Unfinished puzzle never flashes the gallery');
  await page.locator('#startFresh').click();
  assert.equal(await page.locator('#galleryPanel').isVisible(),true,'New picture choice works without deleting progress');
  assert.equal(await page.locator('#resumeGalleryBanner').isVisible(),true,'Saved game is still accessible from the gallery');
  await page.locator('#resumeFromGallery').click();
  await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound&&window.__nightJigsawTest.snapshot().pieces.length===144);
  assert.equal(await page.locator('#playScreen').isVisible(),true,'Continue restores the unfinished Master puzzle');

 // Separate mobile browser: help controls must remain within viewport, never below puzzle.
 const hud=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:1});
 const hudErrors=[];hud.on('pageerror',e=>hudErrors.push(String(e)));
 await hud.route('https://images.unsplash.com/**',mockPhoto);
 await hud.goto(url+'/jigsaw/');
 await hud.locator('[data-theme="photo-alpine-lake"]').click();
 await hud.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound);
 const checkHud=()=>hud.evaluate(()=>{
  const ids=['previewButton','hintButton','fullscreenButton','edgesButton','sweepButton'],rects={};
  for(const id of ids){const r=document.getElementById(id).getBoundingClientRect();rects[id]={top:r.top,bottom:r.bottom,left:r.left,right:r.right,visible:r.width>=20&&r.height>=20&&r.top>=-1&&r.bottom<=innerHeight+1&&r.left>=-1&&r.right<=innerWidth+1}}
  return {width:innerWidth,rects,toolbarBottom:document.querySelector('.play-hud').getBoundingClientRect().bottom,boardTop:document.querySelector('.play-mat').getBoundingClientRect().top,overflow:document.documentElement.scrollWidth>innerWidth+2};
 });
 let info=await checkHud();
 assert.equal(info.overflow,false,'Puzzle has no horizontal overflow');
 assert.ok(Object.values(info.rects).every(v=>v.visible),'All top tools visible without scrolling '+JSON.stringify(info));
 assert.ok(info.toolbarBottom<=info.boardTop+2,'Assistance controls belong above the puzzle');
 assert.equal(await hud.locator('#previewButton').count(),1);
 assert.equal(await hud.locator('#hintButton').count(),1);
 await hud.screenshot({path:path.join(root,'visual-output/JIGSAW-TOP-TOOLS-MOBILE.jpg'),type:'jpeg',quality:74});
 await hud.locator('#fullscreenButton').click();
 await hud.waitForTimeout(220);
 info=await checkHud();
 assert.ok(info.rects.previewButton.visible&&info.rects.hintButton.visible,'Full screen must retain visible Preview and Hint');
 const full=await hud.evaluate(()=>Boolean(document.fullscreenElement));
 if(full){
  assert.equal(await hud.locator('#fullscreenButton').getAttribute('aria-pressed'),'true');
  await hud.screenshot({path:path.join(root,'visual-output/JIGSAW-TRUE-FULLSCREEN.jpg'),type:'jpeg',quality:74});
 }
 await hud.locator('#previewButton').click();
 assert.equal(await hud.locator('#previewDialog').isVisible(),true,'Preview works from upper toolbar');
 await hud.locator('[data-close="previewDialog"]').click();
 assert.equal(await hud.locator('#previewRemaining').innerText(),'2 left');
 await hud.locator('#hintButton').click();
 assert.equal(await hud.locator('#hintRemaining').innerText(),'2 left');
 assert.ok((await hud.evaluate(()=>window.__nightJigsawTest.snapshot())).placed>0,'Upper hint still places piece');
 await hud.locator('#settingsButton').click();
 assert.equal(await hud.locator('#settingsDialog').isVisible(),true);
 assert.equal(await hud.locator('#restartButton').count(),1);
 await hud.locator('#restartButton').click();
 assert.equal(await hud.locator('#settingsDialog').isVisible(),false);
 assert.equal((await hud.evaluate(()=>window.__nightJigsawTest.snapshot())).placed,0,'Restart resets play');
 if(await hud.evaluate(()=>Boolean(document.fullscreenElement)))await hud.locator('#fullscreenButton').click();
 await hud.setViewportSize({width:320,height:700});
 info=await checkHud();
 assert.equal(info.overflow,false,'320px phone must not horizontally overflow '+JSON.stringify(info));
 assert.ok(info.rects.previewButton.visible&&info.rects.hintButton.visible,'Help remains visible at 320px '+JSON.stringify(info));
 await hud.screenshot({path:path.join(root,'visual-output/JIGSAW-TOP-TOOLS-COMPACT.jpg'),type:'jpeg',quality:74});
 assert.deepEqual(hudErrors,[],'Fullscreen and compact HUD run without browser errors');
 await hud.close();
 console.log('PASS NIGHT Play Jigsaw: photographic gallery, real touch drag, hints, save/reload, settings, upload, win and responsive desktop.');
}finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok))}
