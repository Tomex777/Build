import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const designSource=readFileSync(path.join(root,'squid-game/index.html'),'utf8');
// Preserve the user's preferred original block-like contestant design and matched moving parts.
assert.match(designSource,/box\(1\.1,1\.5,\.62,p\.shade/,'original cuboid torso is retained');
assert.match(designSource,/sphere\(\.45,'#252b29',0,3\.42,-\.075,body\)/,'hair moves with the head, not separately');
assert.match(designSource,/leg\.position\.set\(s\*\.27,1\.155,0\)/,'feet are grounded on the track');
assert.match(designSource,/p\.stride\+=traveled\*1\.65/,'footsteps follow ground movement, not an independent clock');

const server=createServer((req,res)=>{
 const route=new URL(req.url,'http://localhost').pathname;
 const rel=route==='/squid-game/'?'squid-game/index.html':route==='/game-shell.css'?'game-shell.css':null;
 if(!rel){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':rel.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8'});
 res.end(readFileSync(path.join(root,rel)));
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox','--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors=[];
 page.on('pageerror',e=>errors.push(String(e)));
 await page.goto('http://127.0.0.1:'+server.address().port+'/squid-game/',{waitUntil:'domcontentloaded'});
 // Wait for an actual WebGL attempt before collecting 3D screenshots.
 await page.waitForFunction(()=>{
  const canvas=document.getElementById('webgl');
  return document.getElementById('loadError').style.display==='block' || (canvas.width!==960 && canvas.height!==560);
 },null,{timeout:20000});
 const usedFallback=await page.locator('#arena').evaluate(el=>el.classList.contains('fallback3d'));
 assert.equal(usedFallback,false,'3D gameplay QA must render real WebGL; a 2.5D fallback is not a 3D pass');
 console.log('3D WebGL renderer initialized before screenshots');
 assert.equal(await page.locator('#arena').getAttribute('data-style'),'three','3D is the default arena');
 assert.equal(await page.locator('[data-mode="three"]').getAttribute('aria-selected'),'true');
 const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
 await page.locator('#start').click();
 await page.locator('#run').dispatchEvent('pointerdown',{pointerId:8,pointerType:'touch',isPrimary:true});
 await page.waitForTimeout(550);
 await page.locator('#run').dispatchEvent('pointerup',{pointerId:8,pointerType:'touch',isPrimary:true});

 for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[1280,800]]){
  await page.setViewportSize({width,height});
  const dims=await page.evaluate(()=>({
   scroll:document.documentElement.scrollWidth,
   viewport:document.documentElement.clientWidth,
   stage:document.getElementById('stage').getBoundingClientRect().toJSON(),
   run:document.getElementById('run').getBoundingClientRect().toJSON()
  }));
  assert.ok(dims.scroll<=dims.viewport+2,'horizontal overflow at '+width+' '+JSON.stringify(dims));
  assert.ok(dims.stage.width>=290,'arena unexpectedly narrow at '+width);
  assert.ok(dims.stage.height>=290,'arena too short at '+width);
  assert.ok(dims.run.width>=65,'forward button inaccessible at '+width);
  await page.screenshot({path:path.join(output,'squid-3d-'+width+'.jpg'),type:'jpeg',quality:80,fullPage:true});
 }
 await page.setViewportSize({width:390,height:844});
 // Real held pointer: leaving the button while captured must NOT cut off movement.
 await page.reload({waitUntil:'domcontentloaded'});
 await page.locator('#start').click();
 await page.locator('#run').scrollIntoViewIfNeeded();
 const runBox=await page.locator('#run').boundingBox();
 assert.ok(runBox,'forward button must be on screen');
 await page.mouse.move(runBox.x+runBox.width/2,runBox.y+runBox.height/2);
 await page.mouse.down();
 await page.waitForTimeout(250);
 const beforeDrag=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 await page.mouse.move(runBox.x+runBox.width+55,runBox.y-12,{steps:4});
 await page.waitForTimeout(260);
 const afterDrag=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 assert.ok(afterDrag>beforeDrag+1.2,'dragging a held pointer outside must keep the captured run active');
 await page.mouse.up();
 await page.waitForTimeout(140);
 const onRelease=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 await page.waitForTimeout(300);
 const afterRelease=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 assert.ok(Math.abs(afterRelease-onRelease)<.05,'player must stop exactly on pointer release with no sliding');
 // Red-light grace freezes the animation/position, even if the button stays pressed.
 await page.waitForFunction(()=>document.getElementById('lightLabel').textContent==='RED LIGHT',{timeout:8500});
 const atRed=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 await page.waitForTimeout(240);
 const afterRed=await page.locator('#progress').evaluate(el=>parseFloat(el.style.width)||0);
 assert.equal(afterRed,atRed,'contestant may not drift forward during red light');
 // Reload for a fresh round before touch, elimination, restart and keyboard tests.
 await page.reload({waitUntil:'domcontentloaded'});
 await page.locator('#start').click();
 assert.equal(await page.locator('#run').isEnabled(),true,'forward movement should be enabled');
 await page.locator('#run').dispatchEvent('pointerdown',{pointerId:1,pointerType:'touch',isPrimary:true});
 await page.waitForTimeout(850);
 await page.locator('#run').dispatchEvent('pointerup',{pointerId:1,pointerType:'touch',isPrimary:true});
 const progress=Number((await page.locator('#progress').getAttribute('style')||'').match(/([\d.]+)%/)?.[1]||0);
 assert.ok(progress>0,'holding forward must move the player');
 await page.locator('#run').dispatchEvent('pointerdown',{pointerId:2,pointerType:'touch',isPrimary:true});
 await page.waitForFunction(()=>document.getElementById('msgTitle').textContent.includes('eliminated'),{timeout:10500});
 assert.equal(await page.locator('#run').isDisabled(),true,'red-light elimination disables movement');
 await page.locator('#run').dispatchEvent('pointerup',{pointerId:2,pointerType:'touch',isPrimary:true});
 await page.locator('#start').click();
 assert.equal(await page.locator('#run').isEnabled(),true,'restart enables another round');
 await page.keyboard.down('ArrowUp');await page.waitForTimeout(300);await page.keyboard.up('ArrowUp');
 assert.ok(Number((await page.locator('#distance').innerText()).split('/')[0].trim())>0,'keyboard movement works');
 await page.locator('[data-mode="twofive"]').click();
 assert.equal(await page.locator('#arena').getAttribute('data-style'),'twofive');
 await page.locator('[data-mode="three"]').click();
 assert.equal(await page.locator('#arena').getAttribute('data-style'),'three');
 assert.deepEqual(errors,[],'browser JavaScript exceptions');
 console.log('PASS Squid Game arena: 5 viewport screenshots, 3D default, touch, elimination, restart, keyboard, mode switching; WebGL fallback='+usedFallback);
}finally{
 if(browser)await browser.close();
 await new Promise(resolve=>server.close(resolve));
}
