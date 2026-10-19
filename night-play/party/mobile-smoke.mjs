import assert from 'node:assert/strict';
import {createServer} from 'node:http';
import {readFileSync,mkdirSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const routes={'/party':'party/index.html','/party/':'party/index.html',
 '/party/games.js':'party/games.js','/party/style.css':'party/style.css',
 '/game-shell.css':'game-shell.css'};
const server=createServer((req,res)=>{
 const p=new URL(req.url,'http://localhost').pathname;
 if(!routes[p]){res.writeHead(404);res.end('Not found');return}
 const name=routes[p];
 res.writeHead(200,{'content-type':name.endsWith('.css')?'text/css':name.endsWith('.js')?'text/javascript':'text/html'});
 res.end(readFileSync(path.join(root,name)));
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
let browser;
try{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors=[];page.on('pageerror',e=>errors.push(String(e)));
 const url='http://127.0.0.1:'+server.address().port;
 const output=path.join(root,'visual-output');mkdirSync(output,{recursive:true});
 async function geometry(label){
   for(const [w,height] of [[320,568],[390,844],[430,932],[768,1024],[1280,860]]){
     await page.setViewportSize({width:w,height});
     const info=await page.evaluate(()=>({
       scroll:document.documentElement.scrollWidth,
       width:innerWidth,
       cards:[...document.querySelectorAll('.party-game-card')].map(x=>x.getBoundingClientRect().width)
     }));
     assert.ok(info.scroll<=info.width+2,'horizontal overflow '+label+' at '+w+'x'+height+' '+JSON.stringify(info));
     assert.ok(info.cards.every(x=>x>=130),'card width too narrow '+label+' at '+w);
     if(w===320||w===390||w===1280)await page.screenshot({path:path.join(output,'party-online-'+label+'-'+w+'.jpg'),type:'jpeg',quality:76,fullPage:true});
   }
   await page.setViewportSize({width:390,height:844});
 }
 await page.goto(url+'/party/');
 assert.equal(await page.locator('.party-game-card').count(),6);
 assert.equal(await page.locator('.party-art-svg use').count(),6,'all games must use real SVG imagery');
 assert.equal(await page.locator('.party-game-art').count(),6);
 assert.equal(await page.locator('.party-card-arrow').count(),0,'no decorative arrow per card');
 assert.equal(await page.locator('#settingsButton').count(),0,'no duplicate nonfunctional settings');
 assert.equal(await page.locator('.party-online-link').count(),0,'no extra navigation stage');
 for(const mode of ['wyr','mlt','tot','nhie','trivia']){
   const link=page.locator('.party-game-card.mode-'+mode);
   assert.equal(await link.getAttribute('href'),'/party/room/?game='+mode);
 }
 assert.equal(await page.locator('.party-game-card.mode-quiz').getAttribute('href'),'/party/?game=quiz');
 const text=await page.locator('body').innerText();
 assert.doesNotMatch(text,/one phone|pass the phone|play locally|practice on this|local party|↗|←|‹/i);
 await geometry('library');
 await page.locator('.party-game-card.mode-quiz').click();
 assert.equal(await page.locator('.party-editor-question').count(),3);
 assert.equal(await page.locator('#playerName,#quizOwner,#settingsButton').count(),0);
 assert.equal(await page.locator('.party-answer-pill').count(),6);
 assert.equal(await page.locator('.party-inline-icon use').count(),1,'the editor uses a proper add SVG icon');
 assert.equal(await page.locator('[data-action="publish"]').count(),1);
 assert.equal(await page.locator('[data-action="play-quiz"]').count(),0,'no offline practice actions');
 await page.locator('[data-q="0"][data-field="prompt"]').fill('Which snack do I choose?');
 await page.locator('[data-q="0"][data-field="option-0"]').fill('Chocolate');
 await page.locator('[data-correct="0"][value="1"]').check();
 const saved=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-personal-quiz-v1')));
 assert.equal(saved.questions[0].prompt,'Which snack do I choose?');
 assert.equal(saved.questions[0].options[0],'Chocolate');
 assert.equal(saved.questions[0].correct,1);
 await geometry('quiz-editor');
 await page.reload();
 assert.equal(await page.locator('[data-q="0"][data-field="prompt"]').inputValue(),'Which snack do I choose?','quiz draft survives refresh');
 await page.locator('[data-action="add"]').click();
 assert.equal(await page.locator('.party-editor-question').count(),4);
 await page.locator('[data-q="3"][data-field="prompt"]').fill('What is my favorite season?');
 await page.locator('[data-q="3"][data-field="option-0"]').fill('Summer');
 await page.locator('[data-q="3"][data-field="option-1"]').fill('Winter');
 await page.locator('[data-remove="3"]').click();
 assert.equal(await page.locator('.party-editor-question').count(),3);
 assert.equal(await page.locator('[data-action="publish"]').count(),1);
 await page.screenshot({path:path.join(output,'party-online-quiz-edited.jpg'),type:'jpeg',quality:79});
 // Navigate via a real click but don't pretend a room has been deployed in this UI-only test.
 await page.locator('[data-action="publish"]').evaluate(el=>el.click());
 await page.waitForURL('**/party/room/?game=quiz',{waitUntil:'domcontentloaded'}).catch(()=>{});
 assert.ok(page.url().includes('/party/room/?game=quiz'),'quiz publishing navigates to genuine online room flow');
 assert.deepEqual(errors,[],'no browser exceptions');
 console.log('PASS: online-only party hub, six real SVG game icons, no arrows or duplicate/local modes, custom quiz editor, mobile/tablet/desktop screenshots');
}finally{if(browser)await browser.close();await new Promise(ok=>server.close(ok))}
