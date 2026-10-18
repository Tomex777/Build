import {chromium} from 'playwright';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'visual-output/difficulty-audit');mkdirSync(output,{recursive:true});
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css'};
const server=createServer((req,res)=>{
 let p=new URL(req.url,'http://localhost').pathname.replace(/^\//,'');
 if(p==='jigsaw'||p==='jigsaw/')p='jigsaw/index.html';
 try{const file=readFileSync(path.join(root,p));res.writeHead(200,{'content-type':types[path.extname(p)]||'text/plain'});res.end(file)}
 catch{res.writeHead(404);res.end();}
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
const site='http://127.0.0.1:'+server.address().port+'/jigsaw/';
let browser;
const all=[];
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 for(const [width,height] of [[390,844],[320,700],[1280,850]]){
  for(const n of [24,48,72,96,144]){
   const page=await browser.newPage({viewport:{width,height},isMobile:width<=390,hasTouch:width<=390,deviceScaleFactor:1});
   const errs=[];page.on('pageerror',e=>errs.push(String(e.message)));
   const fake=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9QjM8yAAAAAASUVORK5CYII=','base64');
   await page.route('https://images.unsplash.com/**',r=>r.fulfill({status:200,contentType:'image/png',body:fake,headers:{'access-control-allow-origin':'*'}}));
   await page.goto(site);
   await page.locator('[data-theme="photo-alpine-lake"]').click();
   await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound,{timeout:7000}).catch(()=>{});
   if(n!==24){
    await page.locator('#hudMenuButton').click();await page.locator('#hudSettingsButton').click();
    await page.locator('[data-pieces="'+n+'"]').click();
   }
   await page.waitForTimeout(120);
   const stats=await page.evaluate(()=>{
    const s=window.__nightJigsawTest.snapshot(),b=s.board,r=document.getElementById('puzzleCanvas').getBoundingClientRect();
    const loose=s.pieces.filter(p=>!p.placed),areas={top:0,bottom:0,left:0,right:0,board:0};
    for(const p of loose){const x=p.x+b.pieceWidth/2,y=p.y+b.pieceHeight/2;if(x<b.x)areas.left++;else if(x>b.x+b.w)areas.right++;else if(y<b.y)areas.top++;else if(y>b.y+b.h)areas.bottom++;else areas.board++;}
    let overlaps=0,heavyOverlap=0;
    for(let i=0;i<loose.length;i++)for(let j=i+1;j<loose.length;j++){
     const ax=Math.max(0,Math.min(loose[i].x+b.pieceWidth,loose[j].x+b.pieceWidth)-Math.max(loose[i].x,loose[j].x));
     const ay=Math.max(0,Math.min(loose[i].y+b.pieceHeight,loose[j].y+b.pieceHeight)-Math.max(loose[i].y,loose[j].y));
     const ratio=ax*ay/(b.pieceWidth*b.pieceHeight);
     if(ratio>.1)overlaps++;
     if(ratio>.5)heavyOverlap++;
    }
    return {n:s.pieces.length,b,canvas:r.toJSON(),piecePx:{width:b.pieceWidth,height:b.pieceHeight},
      areas,overlaps,heavyOverlap,viewport:[innerWidth,innerHeight],pageScroll:document.documentElement.scrollHeight-innerHeight,
      density:loose.length/(r.width*r.height),zoom:s.zoom};
   });
   stats.device=width+'x'+height;stats.errors=errs;
   all.push(stats);
   const screenshot=await page.screenshot({type:'jpeg',quality:49});
   const name='NIGHT-'+width+'-'+n;
   writeFileSync(path.join(output,name+'.jpg'),screenshot);
   if(n===144||n===72)console.log('DIFFICULTY_SCREEN_'+name.replaceAll('-','_')+'='+screenshot.toString('base64'));
   console.log('DIFFICULTY_BASELINE='+JSON.stringify(stats));
   await page.close();
  }
 }
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 try{
  await page.goto('https://playjigsaw.net/puzzles/puz-orchard-goat-sheltered-edge-7894e43b',{waitUntil:'domcontentloaded',timeout:45000});
  await page.waitForTimeout(1200);
  const nums=await page.locator('button').evaluateAll(xs=>xs.filter(x=>/36|64|100|144|200|400/.test(x.textContent||'')||/400/.test(x.getAttribute('aria-label')||'')).slice(0,24).map(x=>({text:x.textContent.trim().slice(0,60),aria:x.getAttribute('aria-label')})));
  console.log('REFERENCE_DIFF_BUTTONS='+JSON.stringify(nums));
  const option=page.getByRole('button',{name:/400 pieces|400 pcs/i}).first();
  if(await option.count()){await option.click({timeout:7000});console.log('REFERENCE_400_SELECTED');}
  const play=page.getByRole('link',{name:/play with/i}).first();
  if(await play.count()){await play.click({timeout:12000});await page.waitForTimeout(1800);}
  const ss=await page.screenshot({type:'jpeg',quality:55});
  writeFileSync(path.join(output,'REFERENCE-390-high.jpg'),ss);
  console.log('REFERENCE_390_HIGH='+ss.toString('base64'));
  console.log('REFERENCE_STATE='+JSON.stringify({url:page.url(),text:(await page.locator('body').innerText()).slice(0,800)}));
 }catch(e){console.log('REFERENCE_ISSUE '+String(e.message||e).slice(0,300))}
 await page.close();
}finally{await browser?.close();await new Promise(done=>server.close(done))}
writeFileSync(path.join(output,'report.json'),JSON.stringify(all,null,2));
