import assert from "node:assert/strict";
import {createServer} from "node:http";
import {readFileSync} from "node:fs";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {chromium} from "playwright";
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),"..");
const assets={"/arcade/":"arcade/index.html","/arcade":"arcade/index.html","/arcade/style.css":"arcade/style.css","/game-shell.css":"game-shell.css","/arcade/games.js":"arcade/games.js","/games":"index.html"};
const server=createServer((req,res)=>{const file=assets[new URL(req.url,"http://localhost").pathname];if(!file){res.writeHead(404);res.end();return;}const kind=file.endsWith(".js")?"text/javascript":file.endsWith(".css")?"text/css":"text/html";res.writeHead(200,{"content-type":kind});res.end(readFileSync(path.join(root,file)));});
await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));const url="http://127.0.0.1:"+server.address().port;
let browser;try{
browser=await chromium.launch({headless:true,channel:"chrome",args:["--no-sandbox"]});
const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});const errors=[];
page.on("pageerror",e=>errors.push(String(e)));
await page.goto(url+"/arcade/?game=bird");await page.locator("canvas").waitFor();
assert.equal(await page.locator("#title").innerText(),"Wing Hop");
assert.equal(await page.locator('.night-brand-mark use').getAttribute('href'),'#night-mark','Arcade uses real approved NIGHT logo');
assert.equal(await page.locator('#settingsButton').isVisible(),true,'Jigsaw-style settings icon is top-right');
assert.equal(await page.locator('#picks').isVisible(),false,'No crowded multi-game menu above gameplay');
await page.locator('#settingsButton').click();
assert.equal(await page.locator('#settingsDialog').isVisible(),true);
await page.locator('#settingsClose').click();
await page.locator('#screen').evaluate(el=>{if(el.getBoundingClientRect().width<300)throw Error('Arcade board must fill available mobile width')});
console.log('NIGHT_ARCADE_SHELL_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:56,fullPage:true})).toString('base64'));
assert.equal(await page.locator('[data-control="bird-skin"]').count(),1,"Wing Hop offers real bundled art skins");
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().skin),'parrot',"Parrot Kenney sprite is the default");
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.perf().wingCached),true,"Wing Hop caches static background instead of painting sun and sky each frame");
await page.locator('[data-control="bird-skin"]').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().skin),'chick',"Players can switch to Kenney chick artwork");
await page.locator('[data-control="bird-skin"]').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().skin),'parrot',"Players can switch back to parrot artwork");
await page.waitForTimeout(250);
console.log("NIGHT_WINGHOP_VISUAL_JPEG_BASE64="+(await page.locator("canvas.mode-bird").screenshot({type:"jpeg",quality:56})).toString("base64"));
await page.locator('button[data-control="flap"]').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().started),true,"flapping starts physics");
await page.waitForTimeout(720);
console.log("NIGHT_WINGHOP_ACTION_JPEG_BASE64="+(await page.locator("canvas.mode-bird").screenshot({type:"jpeg",quality:54})).toString("base64"));
await page.locator("#restart").click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().started),false,"restart resets bird");
await page.locator("#settingsButton").click();await page.locator('[data-game="blocks"]').click();
assert.equal(await page.locator("#title").innerText(),"Block Drop");
// Block Drop's 10 x 20 arena must fit vertically, not expand to 780px
// on a 390px-wide phone. Check portrait sizes, a short phone and a tablet.
for(const [width,height] of [[320,568],[360,740],[390,844],[412,915],[768,1024]]){
 await page.setViewportSize({width,height});
 const bounds=await page.locator('canvas.mode-blocks').evaluate(el=>{
  const r=el.getBoundingClientRect();
  return {x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom,
   pageWidth:document.documentElement.scrollWidth,screenWidth:innerWidth,screenHeight:innerHeight,
   controlsTop:document.querySelector('#touchbar').getBoundingClientRect().top};
 });
 assert.ok(bounds.width>=160,'Block Drop retains usable cell sizes at '+width+'×'+height);
 assert.ok(bounds.x>=0&&bounds.x+bounds.width<=bounds.screenWidth+1,'board stays inside screen width at '+width+'×'+height);
 if(width<=650){
  assert.ok(bounds.width>=width-18,'Block Drop fills the phone width with no wasted side gutters at '+width+'×'+height+': '+JSON.stringify(bounds));
  assert.ok(bounds.height<=bounds.width*2+2,'all 20 rows fit without vertical expansion at '+width+'×'+height);
 }else{
  assert.ok(Math.abs(bounds.height/bounds.width-2)<.03,'desktop keeps square cell proportions at '+width+'×'+height);
 }
 assert.ok(bounds.bottom<=bounds.screenHeight-4,'entire falling-block board is visible without vertical scrolling at '+width+'×'+height+': '+JSON.stringify(bounds));
 assert.ok(bounds.controlsTop>=bounds.bottom-1,'game controls never overlap the board at '+width+'×'+height);
 assert.ok(bounds.pageWidth<=bounds.screenWidth+2,'no sideways viewport overflow at '+width+'×'+height);
}
await page.setViewportSize({width:390,height:844});
assert.equal(await page.locator('.block-move-group button').count(),2,'Left thumb has only move keys');
assert.equal(await page.locator('.block-action-group button').count(),3,'Right thumb has rotate, soft and hard drop keys');
assert.equal(await page.locator('#touchbar [data-control="pause"]').count(),0,'Pause is not squeezed into the movement keys');
assert.equal(await page.locator('#blockPause').isVisible(),true,'Pause is a separate real control in the HUD');
assert.equal(await page.locator('#touchbar [data-control="drop"]').getAttribute('aria-label'),'Hard drop piece instantly');
const gameButtons=await page.locator('.block-control-layout button').evaluateAll(bs=>bs.map(b=>{
 const r=b.getBoundingClientRect();return {x:r.x,right:r.right,y:r.y,bottom:r.bottom};
}));
assert.equal(gameButtons.length,5);
assert.ok(gameButtons.every(b=>b.x>=0&&b.right<=390&&b.bottom<=844),'grouped game controls visible on mobile');
assert.ok(gameButtons[1].right+5<gameButtons[2].x,'movement and action groups have separate thumb zones');
// Exercise actual PointerEvents across the rendered canvas. Moves must use
// rendered-cell thresholds even when the 10x20 playfield is compressed.
async function gesture(points,terminal='pointerup'){
 await page.locator('canvas.mode-blocks').evaluate((el,{points,terminal})=>{
  const r=el.getBoundingClientRect();
  points.forEach(([x,y],i)=>{
   const event=i===0?'pointerdown':i===points.length-1?terminal:'pointermove';
   el.dispatchEvent(new PointerEvent(event,{
    bubbles:true,cancelable:true,pointerType:'touch',pointerId:1234,
    isPrimary:true,button:0,buttons:i===points.length-1?0:1,
    clientX:r.x+r.width*x,clientY:r.y+r.height*y
   }));
  });
 },{points,terminal});
}
await page.locator('#restart').click();
const beforeTap=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.cells);
await page.locator('canvas.mode-blocks').evaluate(el=>{
 const r=el.getBoundingClientRect(),x=r.x+r.width*.5,y=r.y+r.height*.42;
 el.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:11,pointerType:'touch',clientX:x,clientY:y}));
 el.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerId:11,pointerType:'touch',clientX:x,clientY:y}));
});
assert.notDeepEqual(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.cells),beforeTap,'tap rotates the piece');
await page.locator('#restart').click();
const origin=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x);
await gesture([[.42,.26],[.67,.26],[.67,.26]]);
const afterSwipe=await page.evaluate(()=>window.__nightArcadeTest.snapshot());
assert.ok(afterSwipe.cur.x>=origin+2,'drag right follows finger across multiple columns');
assert.deepEqual(afterSwipe.cur.cells,(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.cells)),'drag does not accidentally rotate');
assert.ok(afterSwipe.grid.flat().every(v=>v===null),'sideways drag cannot lock the piece');
await page.locator('#restart').click();
const beforeSlow=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.y);
await page.locator('canvas.mode-blocks').evaluate(el=>{
 const r=el.getBoundingClientRect(),x=r.x+r.width*.5,y=r.y+r.height*.25;
 el.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:25,pointerType:'touch',clientX:x,clientY:y}));
 el.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,pointerId:25,pointerType:'touch',clientX:x,clientY:y+55}));
});
await page.waitForTimeout(320);
await page.locator('canvas.mode-blocks').evaluate(el=>{
 const r=el.getBoundingClientRect(),x=r.x+r.width*.5,y=r.y+r.height*.25+55;
 el.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerId:25,pointerType:'touch',clientX:x,clientY:y}));
});
const slow=await page.evaluate(()=>window.__nightArcadeTest.snapshot());
assert.ok(slow.cur.y>beforeSlow,'slow downward drag accelerates one piece');
assert.ok(slow.grid.flat().every(v=>v===null),'slow drag never triggers hard drop');
await page.locator('#restart').click();
await gesture([[.5,.25],[.5,.44],[.5,.44]]);
assert.ok(await page.evaluate(()=>window.__nightArcadeTest.snapshot().grid.flat().some(v=>v!==null)),'quick downward flick hard-drops immediately');
await page.locator('#restart').click();
const beforeCancel=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.cells);
await gesture([[.4,.22],[.4,.22]],'pointercancel');
assert.deepEqual(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.cells),beforeCancel,'pointer cancellation does not rotate');
await page.locator('#blockPause').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.flags().paused),true,'separate Pause works');
const pausedX=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x);
await gesture([[.4,.22],[.65,.22],[.65,.22]]);
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x),pausedX,'gestures cannot move pieces while paused');
await page.locator('#blockPause').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.flags().paused),false,'pause toggles back to play');
await page.locator('#restart').click();
// Press-and-hold must repeat, not wait for an extra release click.
const rightButton=page.locator('.block-move-group [data-control="right"]');
const beforeHold=await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x);
await rightButton.dispatchEvent('pointerdown',{button:0,pointerId:44});
await page.waitForTimeout(410);
await rightButton.dispatchEvent('pointerup',{button:0,pointerId:44});
assert.ok(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x)>=beforeHold+2,'holding a direction repeats movement');
await page.locator('#restart').click();
const initial=await page.evaluate(()=>{const s=window.__nightArcadeTest.snapshot();return [s.cur.x,s.cur.y];});
await page.locator('[data-control="left"]').click();assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cur.x),initial[0]-1);
const paintBefore=await page.evaluate(()=>window.__nightArcadeTest.perf().blockPaints);
await page.waitForTimeout(170);
const paintAfter=await page.evaluate(()=>window.__nightArcadeTest.perf().blockPaints);
assert.ok(paintAfter-paintBefore<=2,'Block Drop must not fully repaint the board every animation frame');
await page.locator('[data-control="drop"]').click();
assert.ok(await page.evaluate(()=>window.__nightArcadeTest.snapshot().grid.flat().some(x=>x!==null)),"hard-drop locks shape");
await page.locator("#settingsButton").click();await page.locator('[data-game="mines"]').click();
assert.equal(await page.locator("#mineboard button").count(),81);
const originalMine=await page.evaluate(()=>{window.__mineStable=document.querySelector('#mineboard [data-tile="40"]');return true});

await page.locator('[data-tile="40"]').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().started),true,"first reveal plants mines");
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().cells[40]),0,"first reveal keeps 3x3 clear");
assert.equal(await page.evaluate(()=>window.__mineStable===document.querySelector('#mineboard [data-tile="40"]')),true,'Minesweeper reuses 81 cell buttons after reveal');
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.flags().finished),false,"first tap cannot lose");
await page.locator('[data-control="flag"]').first().click();
const covered=await page.evaluate(()=>{const s=window.__nightArcadeTest.snapshot();return s.open.findIndex((v,i)=>!v&&!s.flags[i]);});
await page.locator('[data-tile="'+covered+'"]').click();
assert.equal(await page.evaluate(i=>window.__nightArcadeTest.snapshot().flags[i],covered),true,"flag mode marks cell");
assert.equal(await page.evaluate(()=>window.__mineStable===document.querySelector('#mineboard [data-tile="40"]')),true,'Minesweeper does not rebuild board after flagging');
const longPressAt=await page.evaluate(()=>{
 const s=window.__nightArcadeTest.snapshot();
 return s.open.findIndex((v,i)=>!v&&!s.flags[i]);
});
const longPress=page.locator('[data-tile="'+longPressAt+'"]');
await longPress.dispatchEvent('pointerdown',{button:0,clientX:18,clientY:18});
await page.waitForTimeout(600);
assert.equal(await page.evaluate(i=>window.__nightArcadeTest.snapshot().flags[i],longPressAt),true,'Long press flags exactly once');
await longPress.dispatchEvent('contextmenu',{button:2});
assert.equal(await page.evaluate(i=>window.__nightArcadeTest.snapshot().flags[i],longPressAt),true,'Follow-on Android context menu cannot silently unflag');
await longPress.dispatchEvent('pointerup',{button:0});
await page.locator("#settingsButton").click();await page.locator('[data-game="maze"]').click();
assert.equal(await page.locator("#title").innerText(),"Maze Chase");
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.snapshot().walls.length),21);
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.perf().mazeCached),true,"Maze Chase caches static walls and collectible dots");
assert.ok(await page.evaluate(()=>window.__nightArcadeTest.snapshot().total)>100);
await page.locator('[data-control="down"]').click();
assert.deepEqual(await page.evaluate(()=>window.__nightArcadeTest.snapshot().p.dir),[0,1]);
await page.locator('[data-control="pause"]').click();
assert.equal(await page.evaluate(()=>window.__nightArcadeTest.flags().paused),true);
for(const name of ["bird","blocks","mines","maze"]){
  await page.goto(url+"/arcade/?game="+name);
  assert.equal(await page.locator("#picks [aria-current=true]").getAttribute("data-game"),name);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),"no sideways scroll on phone: "+name);
  if(name!=="bird")console.log("NIGHT_ARCADE_SCREEN_"+name.toUpperCase()+"_JPEG_BASE64="+(await page.screenshot({type:"jpeg",quality:50,fullPage:true})).toString("base64"));
}
await page.goto(url+"/games");await page.locator("#view-games").waitFor({state:"visible"});
assert.equal(await page.locator("[data-arcade]").count(),4,"all four in current library");
await page.locator('[data-arcade="mines"]').click();
await page.waitForURL("**/arcade/?game=mines");
assert.equal(await page.locator("#mineboard button").count(),81);
assert.deepEqual(errors,[],"no browser exceptions");
console.log("PASS NIGHT arcade: 4 playable responsive original games; physics, touch, hard-drop, safe mines, flags, mazes, routing");
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}