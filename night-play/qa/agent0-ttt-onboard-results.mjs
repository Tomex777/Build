import assert from 'node:assert/strict';
import{createServer}from'node:http';import{readFileSync,statSync,mkdirSync}from'node:fs';
import path from'node:path';import{fileURLToPath}from'node:url';import{chromium}from'playwright';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'visual-output','agent0-results');mkdirSync(output,{recursive:true});
const server=createServer((req,res)=>{
 const route=new URL(req.url,'http://localhost').pathname;
 if(route.startsWith('/api/')){res.writeHead(401,{'content-type':'application/json'});res.end('{"linked":false}');return}
 const file=path.resolve(root,'.'+(['/','/play/ttt','/games','/dashboard'].includes(route)?'/index.html':route));
 if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return}
 try{if(!statSync(file).isFile())throw Error('no file');
 const ext=path.extname(file);
 res.writeHead(200,{'content-type':{'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml','.png':'image/png'}[ext]||'application/octet-stream'});
 res.end(readFileSync(file));}catch{res.writeHead(404);res.end('missing')}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const base='http://127.0.0.1:'+server.address().port,results=[];
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--no-sandbox']});
try{
 for(const width of [320,390]){
  const page=await browser.newPage({viewport:{width,height:844},isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(String(e)));
  await page.goto(base+'/play/ttt');
  await page.waitForFunction(()=>!!window.__nightTttResultTest);
  await page.locator('#modeBtn').click();
  assert.equal(await page.locator('#modeBtn').innerText(),'2 PLAYERS');
  const play=async moves=>{for(const cell of moves)await page.locator('#tttboard > .ttt-cell').nth(cell).tap()};
  await play([0,3,1,4,2]); // X wins
  await page.locator('#tttboard > .ttt-draw-overlay').waitFor({state:'visible',timeout:2000});
  assert.equal(await page.locator('.ttt-draw-card h3').innerText(),'X wins!');
  const board=await page.locator('#tttboard').boundingBox(),layer=await page.locator('.ttt-draw-overlay').boundingBox();
  assert.ok(layer.x>=board.x-2&&layer.y>=board.y-2&&layer.x+layer.width<=board.x+board.width+2&&layer.y+layer.height<=board.y+board.height+2,'overlay is inside the board');
  await page.screenshot({path:path.join(output,'ttt-won-'+width+'.png')});
  await page.waitForFunction(()=>document.querySelectorAll('#tttboard>.ttt-draw-overlay').length===0,{timeout:5500});
  await play([0,3,1,4,8,5]); // O wins
  await page.locator('#tttboard > .ttt-draw-overlay').waitFor({state:'visible',timeout:2000});
  assert.equal(await page.locator('.ttt-draw-card h3').innerText(),'O wins!');
  await page.screenshot({path:path.join(output,'ttt-lost-'+width+'.png')});
  await page.waitForFunction(()=>document.querySelectorAll('#tttboard>.ttt-draw-overlay').length===0,{timeout:5500});
  await play([0,1,2,4,3,5,7,6,8]);
  await page.locator('#tttboard > .ttt-draw-overlay').waitFor({state:'visible',timeout:2000});
  assert.equal(await page.locator('.ttt-draw-card h3').innerText(),"It's a draw!");
  await page.screenshot({path:path.join(output,'ttt-draw-'+width+'.png')});
  assert.deepEqual(errors,[]);
  results.push({width,overlayWithinBoard:true,win:true,loss:true,draw:true,autoReset:true});
  await page.close();
 }
 console.log('AGENT_ZERO_TTT_ALL_INBOARD_RESULTS '+JSON.stringify(results));
}finally{await browser.close();await new Promise(done=>server.close(done));}
