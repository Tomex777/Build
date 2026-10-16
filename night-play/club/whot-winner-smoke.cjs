'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const root=path.resolve(__dirname,'..');
const fileTypes={'.css':'text/css','.js':'application/javascript','.html':'text/html'};
const server=http.createServer((req,res)=>{
 const src=new URL(req.url,'http://localhost').pathname;
 const paths={'/club/':'club/index.html','/club':'club/index.html','/club/style.css':'club/style.css','/game-shell.css':'game-shell.css','/club/ludo-immersive.css':'club/ludo-immersive.css','/club/games.js':'club/games.js','/club/naija-ludo.js':'club/naija-ludo.js','/club/ludo-motion.js':'club/ludo-motion.js','/club/ludo-dice-3d.js':'club/ludo-dice-3d.js'};
 const rel=paths[src];
 if(!rel){res.writeHead(404);res.end('Not found');return}
 res.writeHead(200,{'content-type':fileTypes[path.extname(rel)]||'text/plain','cache-control':'no-store'});
 res.end(fs.readFileSync(path.join(root,rel)));
});
function snap(page,tag,width){
 return page.screenshot({type:'jpeg',quality:58,fullPage:true}).then(buffer=>{
  if(process.env.WHOT_QA_IMAGE==='1'&&width===390)console.log('WHOT_QA_IMAGE_'+tag+'='+buffer.toString('base64'));
 });
}
async function finish(page,winner){
 await page.evaluate(winner=>{
  const key='night-play-club-v1';
  const obj=JSON.parse(localStorage.getItem(key));
  const whot=obj.whot;
  whot.discard.push(...whot.hands[winner]);
  whot.hands[winner]=[];
  whot.winner=winner;whot.turn=winner;whot.pendingWhot=null;
  whot.msg=winner===0?'You win!':'Computer wins.';
  localStorage.setItem(key,JSON.stringify(obj));
 },winner);
 await page.reload();
 await page.locator('.whot-win-layer').waitFor({state:'visible'});
}
(async()=>{
 await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  for(const width of [390,1280]){
   const page=await browser.newPage({viewport:{width,height:844},isMobile:width<=600,hasTouch:width<=600});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.route('https://cdn.jsdelivr.net/**',route=>route.abort());
   await page.goto(base+'/club/?game=whot');
   await page.locator('.whot-scoreboard').waitFor();
   assert.equal(await page.locator('.whot-scoreboard').count(),1,'one board scoreboard');
   assert.equal(await page.locator('.controls-col').isVisible(),false,'remove redundant sidebar');
   assert.equal(await page.locator('#gameSubtitle').isVisible(),false,'remove decorative subtitle');
   assert.equal(await page.locator('#gameCategory').isVisible(),false,'remove category label');
   assert.equal(await page.locator('.whot-seat-you strong').innerText(),'5');
   assert.equal(await page.locator('.whot-seat-cpu strong').innerText(),'5');
   assert.match(await page.locator('.whot-turn-indicator').innerText(),/YOUR TURN/);
   assert.equal(await page.locator('.hand .card-label').count(),0,'no duplicate hand count');
   assert.equal(await page.locator('.table-surface > .card-row .cards-counter').count(),0,'no duplicate CPU count');
   const look=await page.locator('.table-surface').evaluate(el=>({bg:getComputedStyle(el).backgroundImage,shadow:getComputedStyle(el).boxShadow,radius:getComputedStyle(el).borderRadius}));
   assert.ok(look.bg.includes('repeating-linear-gradient'),'felt material has visible woven texture');
   assert.ok(look.shadow.includes('inset'),'felt table includes raised wooden edge');
   assert.equal(await page.locator('.whot-win-layer').count(),0,'no premature victory banner');
   await snap(page,'TABLE',width);
   await finish(page,0);
   assert.equal(await page.locator('.whot-win-layer.player-victory').count(),1,'winner rendered inside board');
   assert.equal(await page.locator('.whot-win-content h2').innerText(),'YOU WIN');
   assert.equal(await page.locator('.whot-win-count').innerText(),'CPU · 5 CARDS LEFT');
   assert.equal(await page.locator('.night-action-row').isVisible(),false,'no duplicate new game action over victory');
   assert.equal(await page.locator('.whot-win-particles i').count(),18,'small visual victory celebration');
   const bounds=await page.locator('.whot-win-layer').boundingBox();
   const tableBounds=await page.locator('.table-surface').boundingBox();
   assert.ok(Math.abs(bounds.x-tableBounds.x)<2&&Math.abs(bounds.y-tableBounds.y)<2,'result is on table, not off-board or in a popup');
   await snap(page,'WIN',width);
   await page.getByRole('button',{name:/Play again/}).click();
   assert.equal(await page.locator('.whot-win-layer').count(),0,'rematch removes result and starts a fresh hand');
   assert.equal(await page.locator('.hand .whot-card').count(),5,'rematch deals five fresh cards');
   assert.equal(await page.locator('.whot-seat-cpu strong').innerText(),'5');
   await finish(page,1);
   assert.equal(await page.locator('.whot-win-layer.computer-victory').count(),1,'CPU win is on same board');
   assert.equal(await page.locator('.whot-win-content h2').innerText(),'CPU WINS');
   assert.equal(await page.locator('.whot-win-count').innerText(),'YOU · 5 CARDS LEFT');
   assert.equal(await page.locator('.whot-win-particles i').count(),0,'CPU win is distinguished from a player victory');
   await snap(page,'LOSE',width);
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
   assert.ok(overflow<=1,'no horizontal overflow at '+width+'px: '+overflow);
   assert.deepEqual(errors,[],'no console page exceptions');
   await page.close();
  }
  console.log('WHOT WINNER QA PASS: immersive table, one HUD, player/CPU result overlays, rematch, no redundant information, mobile/desktop');
 }finally{await browser.close()}
})().catch(e=>{console.error('WHOT WINNER QA FAILED',e.stack||e);process.exitCode=1}).finally(()=>server.close());