import { readFileSync } from 'node:fs';
import {mkdir,writeFile} from 'node:fs/promises';
import { createServer } from 'node:http';
import { chromium } from 'playwright';
const html=readFileSync(new URL('./index.html',import.meta.url));
const server=createServer((req,res)=>{res.writeHead(200,{'content-type':'text/html; charset=utf-8'});res.end(html)});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const b=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
try{
 const page=await b.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true,deviceScaleFactor:1});
 const home='http://127.0.0.1:'+server.address().port;
 await page.goto(home+'/games');await page.locator('#view-games').waitFor({state:'visible'});
 const snapshot=async name=>{
  const shot=await page.screenshot({type:'jpeg',quality:65,fullPage:false,animations:'disabled'});
  await mkdir('night-play/visual-output',{recursive:true});
  await writeFile('night-play/visual-output/'+name.toLowerCase()+'.jpg',shot);
 };
 await snapshot('LIBRARY');
 for(const kind of ['snake','2048','ttt']){
  await page.locator('.library-card[data-game="'+kind+'"]').click();
  if(kind==='snake')await page.locator('#snakeOverlay').click();
  await page.waitForTimeout(350);
  const dim=await page.evaluate(()=>{const id=({snake:'.snake-shell','2048':'#board2048',ttt:'#tttboard'})[document.querySelector('#gameDialog').dataset.skin];
    const board=document.querySelector(id),r=board.getBoundingClientRect();return {size:Math.round(r.width),height:Math.round(r.height),top:Math.round(r.top),vw:innerWidth,vh:innerHeight,centerY:Math.round(r.top+r.height/2),pageCenterY:Math.round(innerHeight/2)}})
  console.log('ARENA_METRICS_'+kind+':'+JSON.stringify(dim));
  const title=await page.locator('#gameTitle').boundingBox();
  console.log('TITLE_METRICS_'+kind+':'+JSON.stringify({y:Math.round(title.y),height:Math.round(title.height)}));
  if(title.y>=130)throw Error('Game title is not at the top: '+kind+' y='+title.y);
  // The temporary compressed image log is for *actual* visual review in chat.
  const inspect=await page.screenshot({type:'jpeg',quality:28,fullPage:false,animations:'disabled'});
  console.log('REVIEW_SHOT_'+kind+':'+inspect.toString('base64'));
  if(Math.abs(dim.centerY-dim.pageCenterY)>30)throw Error('Arena not centred: '+kind+' '+JSON.stringify(dim));
  await snapshot(kind.toUpperCase());
  await page.locator('[data-close-game]').first().click();
 }
}finally{await b.close();await new Promise(resolve=>server.close(resolve))}
