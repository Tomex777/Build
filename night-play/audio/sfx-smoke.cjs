'use strict';
/* Real Chromium gesture/unlock, cross-game persistence and event regression. */
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'..');
const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost'),p=url.pathname;
  if(p==='/api/account'){res.setHeader('content-type','application/json');res.end(JSON.stringify({linked:false,profile:null}));return;}
  const rel=p==='/play/2048'||p==='/games'||p==='/'?'index.html':
            p==='/club'||p==='/club/'?'club/index.html':
            p==='/arcade'||p==='/arcade/'?'arcade/index.html':
            p==='/jigsaw'||p==='/jigsaw/'?'jigsaw/index.html':p.replace(/^\//,'');
  const file=path.resolve(root,rel);
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end('Not found');return;}
  res.setHeader('content-type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');
  res.end(fs.readFileSync(file));
});
(async()=>{
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
  try{
    const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    const base='http://127.0.0.1:'+server.address().port;
    await page.goto(base+'/play/2048');
    await page.locator('#board2048').waitFor();
    assert.equal(await page.evaluate(()=>NightAudio.snapshot().enabled),true,'Sounds enabled on new devices');
    assert.equal(await page.evaluate(()=>NightAudio.snapshot().unlocked),false,'Never start audio without gesture');
    await page.locator('#gameSettings').click();
    const switcher=page.getByRole('switch',{name:'Sound effects'});
    assert.equal(await switcher.count(),1,'One custom sound switch in game settings');
    await switcher.click();
    assert.equal(await switcher.getAttribute('aria-checked'),'false');
    assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-sfx-enabled')),'false');
    assert.equal(await page.evaluate(()=>NightAudio.play('eat')),false,'Disabled sound produces nothing');
    await switcher.click();
    assert.equal(await switcher.getAttribute('aria-checked'),'true');
    await page.waitForFunction(()=>NightAudio.snapshot().ready);
    assert.equal(await page.evaluate(()=>NightAudio.play('eat')),true,'Web Audio gesture unlock creates audible event');
    assert.equal(await page.evaluate(()=>NightAudio.snapshot().lastCue),'eat');

    await page.goto(base+'/arcade/?game=blocks');
    await page.locator('[data-control="rotate"]').waitFor();
    assert.equal(await page.evaluate(()=>NightAudio.enabled),true,'Cross-game preference persists');
    await page.locator('[data-control="rotate"]').click();
    assert.equal(await page.evaluate(()=>NightAudio.snapshot().lastCue),'rotate','Block Drop rotation produces its own effect');
    await page.locator('#settingsButton').click();
    assert.equal(await page.getByRole('switch',{name:'Sound effects'}).count(),1,'Arcade uses shared switch');
    await page.getByRole('switch',{name:'Sound effects'}).click();
    await page.locator('#settingsClose').click();
    const count=await page.evaluate(()=>NightAudio.snapshot().total);
    await page.locator('[data-control="rotate"]').click();
    assert.equal(await page.evaluate(()=>NightAudio.snapshot().total),count,'Muted Block Drop stays silent');

    await page.goto(base+'/club/?game=whot');
    await page.locator('#board').waitFor();
    assert.equal(await page.evaluate(()=>NightAudio.enabled),false,'Mute persisted into WHOT');
    await page.locator('#settingsButton').click();
    await page.getByRole('switch',{name:'Sound effects'}).click();
    assert.equal(await page.evaluate(()=>NightAudio.enabled),true);
    await page.locator('#settingsClose').click();
    await page.goto(base+'/jigsaw/');
    await page.locator('#galleryGrid .gallery-card').first().waitFor();
    assert.equal(await page.locator('#soundSwitch').isChecked(),true,'Jigsaw follows same sound preference');
    await page.locator('#settingsButton').click();
    await page.locator('#soundSwitch').uncheck();
    assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-sfx-enabled')),'false');
    await page.reload();
    await page.locator('#galleryGrid .gallery-card').first().waitFor();
    assert.equal(await page.locator('#soundSwitch').isChecked(),false,'Jigsaw sound switch persists after reload');
    assert.deepEqual(errors,[],'No browser JavaScript errors');
    console.log('NIGHT shared game sound smoke passed (gesture unlock, gameplay, mute, persistence, 4 routes).');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>server.close());
