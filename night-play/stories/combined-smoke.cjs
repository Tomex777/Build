'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const E=require('./engine.js'),story=require('./last-call.json');
const root=path.resolve(__dirname,'..');
const output=path.join(__dirname,'qa-output');fs.mkdirSync(output,{recursive:true});
const server=http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://local').pathname;
 const relative=pathname==='/stories/'?'stories/index.html':pathname==='/games'||pathname==='/'?'index.html':pathname.replace(/^\//,'');
 const target=path.resolve(root,relative);if(!target.startsWith(root+path.sep)||!fs.existsSync(target)||fs.statSync(target).isDirectory()){res.writeHead(404);res.end();return}
 res.writeHead(200,{'content-type':target.endsWith('.json')?'application/json':target.endsWith('.js')?'text/javascript':'text/html; charset=utf-8'});res.end(fs.readFileSync(target));
});
function decisionRun(){let r=E.start(story,'Final decision');for(let i=0;i<500;i++){const cs=E.choices(story,r);if(cs.some(c=>c.id==='c29:sever'))return r;r=E.choose(story,r,cs[0].id)}throw Error('No final decision')}
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  for(const width of [390,1280]){
   const page=await browser.newPage({viewport:{width,height:844},isMobile:width<600,hasTouch:width<600});const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(origin+'/games');await page.getByRole('button',{name:'Stories',exact:true}).click();await page.locator('.story-entry').click();await page.waitForURL('**/stories/');
   await page.locator('#story p').first().waitFor();assert.equal(await page.locator('#story p').count(),5);
   const metrics=await page.locator('.col').evaluate(x=>{const r=x.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,font:getComputedStyle(document.querySelector('#story p')).fontSize}});
   assert.ok(Math.abs(metrics.x-(width===390?20:360))<1);assert.ok(Math.abs(metrics.y-75.96)<2);assert.equal(metrics.width,width===390?350:560);assert.equal(metrics.font,width===390?'17px':'17.5px');
   await page.locator('#story').click();await page.waitForTimeout(330);
   assert.equal(await page.locator('.choice').count(),2);assert.ok((await page.locator('#story').innerText()).includes('dated tomorrow'));
   await page.screenshot({path:path.join(output,'reader-'+width+'.png'),fullPage:true});
   await page.locator('[data-choice="start:answer"]').click();assert.ok((await page.locator('#story').innerText()).includes('breathing'));
   await page.locator('#readerOptions summary').click();await page.locator('#pace').selectOption('instant');
   await page.locator('.choice').first().click();assert.ok((await page.locator('#story').innerText()).includes('checked the hallway'));
   const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-story:the-last-call:v3')));const savedNode=saved.playthroughs[0].node;
   await page.reload();await page.locator('#story p').first().waitFor();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('night-story:the-last-call:v3')).playthroughs[0].node),savedNode);
   await page.locator('#readerOptions summary').click();await page.locator('#openJournal').click();
   assert.equal(await page.locator('#storyDialog').evaluate(x=>x.open),true);assert.ok(!(await page.locator('#dialogBody').innerText()).includes('Wren:'));
   await page.screenshot({path:path.join(output,'journal-'+width+'.png'),fullPage:true});await page.getByRole('button',{name:'Close panel'}).click();
   await page.locator('#readerOptions summary').click();await page.locator('#openSaves').click();await page.getByRole('button',{name:'Start a new playthrough',exact:true}).click();
   const two=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-story:the-last-call:v3')));assert.equal(two.playthroughs.length,2);assert.equal(two.playthroughs[0].node,savedNode,'new run preserves original');
   await page.locator('#readerOptions summary').click();await page.locator('#openSaves').click();await page.getByRole('button',{name:'Resume',exact:true}).click();assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('night-story:the-last-call:v3')).active),two.playthroughs[0].id);
   await page.goto(origin+'/stories/library.html');await page.waitForFunction(()=>document.querySelector('#read').textContent==='Continue reading');assert.ok((await page.locator('#progress').innerText()).includes('Chapter 1'));
   await page.goto(origin+'/games');assert.equal(await page.locator('.story-entry h3').innerText(),'Continue Reading');
   assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));assert.deepEqual(errors,[]);await page.close();
  }
  for(const ending of ['release','sever','keeper']){
   const page=await browser.newPage({viewport:{width:390,height:844}}),r=decisionRun(),book=E.storeRun(story,E.emptyBook(story),r);
   await page.addInitScript(({book})=>{if(!localStorage.getItem('night-story:the-last-call:v3'))localStorage.setItem('night-story:the-last-call:v3',JSON.stringify(book));localStorage.setItem('night-play-fiction-reading-speed','instant')},{book});
   await page.goto(origin+'/stories/');await page.locator('[data-choice="c29:'+ending+'"]').click();
   for(let i=0;i<50;i++){if(await page.locator('#recap').isVisible())break;await page.locator('.choice').first().click()}
   await page.locator('#recap').waitFor({state:'visible'});assert.match(await page.locator('#story').innerText(),/THE END/);assert.equal(await page.locator('#recap h3').innerText(),story.endings[ending]);
   await page.screenshot({path:path.join(output,'ending-'+ending+'.png'),fullPage:true});
   await page.reload();await page.locator('#recap').waitFor({state:'visible'});assert.equal(await page.locator('#recap h3').innerText(),story.endings[ending]);
   await page.locator('#readerOptions summary').click();await page.locator('#openChapters').click();await page.getByRole('button',{name:'Read chapter',exact:true}).first().click();assert.ok(!(await page.locator('#dialogBody').innerText()).includes('THE END'),'chapter view shows past chapter, not future ending');
   await page.getByRole('button',{name:'Close panel'}).click();await page.locator('#readerOptions summary').click();await page.locator('#openChapters').click();await page.getByRole('button',{name:'Replay in a new playthrough',exact:true}).first().click();
   const data=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-story:the-last-call:v3')));assert.equal(data.playthroughs.length,2);assert.equal(data.playthroughs[0].ending,ending);assert.equal(data.playthroughs[1].ending,null);
   await page.close();
  }
  const reduced=await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'});await reduced.goto(origin+'/stories/');await reduced.locator('.choice:not([disabled])').first().waitFor();assert.equal(await reduced.locator('#story .settled').count(),5);await reduced.close();
  console.log('PASS mobile + desktop reader, preserved typography, tap safety, save resume, separate runs, journal, library/home continuation, all endings, spoiler-safe chapters and non-destructive replay, reduced motion.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>server.close());
