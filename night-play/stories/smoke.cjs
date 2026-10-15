const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const {pathToFileURL}=require('node:url');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const output=path.resolve(process.env.STORY_QA_OUTPUT||'story-screenshots');
 fs.mkdirSync(output,{recursive:true});
 const url=pathToFileURL(path.resolve('night-play/stories/index.html')).href;
 try{
  for(const width of [390,1280]){
   const page=await browser.newPage({viewport:{width,height:844}});
   const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto(url);
   await page.locator('.line').first().waitFor();
   assert.ok((await page.locator('.line').count())<5,'lines must reveal progressively');
   assert.ok(await page.locator('.line.inking .ink-word').count()>2,'the active line should reveal separate words');
   const motion=await page.locator('.ink-word').first().evaluate(e=>({animation:getComputedStyle(e).animationName,duration:getComputedStyle(e).animationDuration,delay:getComputedStyle(e).animationDelay}));
   assert.equal(motion.animation,'ink-arrive','words should ink instead of moving/fading the whole line');
   assert.equal(motion.duration,'0.9s','cinematic mode should use 900ms ink duration');
   assert.equal(await page.locator('.choice').count(),0,'choices must wait for narration');
   await page.screenshot({path:path.join(output,'first-line-'+width+'.png')});
   await page.locator('#reveal').click();
   assert.equal(await page.locator('.line').count(),5,'skip should show complete scene');
   assert.equal(await page.locator('.choice').count(),2,'choices should show promptly on skip');
   const opacity=await page.evaluate(()=>[...document.querySelectorAll('.line,.choice')].map(x=>Number(getComputedStyle(x).opacity)));
   assert.ok(opacity.every(x=>x===1),'skip must actually display all text and choices immediately');
   const settled=await page.locator('.ink-word').first().evaluate(e=>({animation:getComputedStyle(e).animationName,color:getComputedStyle(e).color}));
   assert.equal(settled.animation,'none','skip must stop running word animations');
   assert.notEqual(settled.color,'rgba(0, 0, 0, 0)','skip words must not be transparent');
   await page.screenshot({path:path.join(output,'choices-'+width+'.png'),fullPage:true});
   const seconds=await page.locator('.choice').first().evaluate(b=>parseFloat(getComputedStyle(b).animationDuration));
   assert.ok(seconds<.2,'choice entrance animation must be quick');
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
   assert.ok(overflow<=1,'layout must not overflow horizontally');
   await page.locator('.choice').first().click();
   assert.match(await page.locator('#location').textContent(),/02:14 AM/,'choice must branch');
   await page.locator('#restart').click();
   assert.match(await page.locator('#location').textContent(),/02:13 AM/,'restart should work');
   await page.locator('#pace').selectOption('instant');
   assert.equal(await page.locator('.line').count(),5,'instant mode should reveal everything');
   assert.equal(errors.length,0,'no JavaScript errors: '+errors.join('; '));
   await page.close();
  }
  const videoContext=await browser.newContext({viewport:{width:390,height:844},recordVideo:{dir:output,size:{width:390,height:844}}});
  const page=await videoContext.newPage();
  await page.goto(url);
  await page.waitForFunction(()=>document.querySelectorAll('.line').length===5,null,{timeout:15000});
  const t=Date.now();
  await page.waitForFunction(()=>document.querySelectorAll('.choice').length===2,null,{timeout:2000});
  assert.ok(Date.now()-t<900,'choices should not wait too long after final text');
  await page.waitForTimeout(600);
  await page.close();
  await videoContext.close();
  console.log('PASS: mobile + desktop line reveals, fast choices, tap skip, branching, restart, speed, error checks');
 }finally{await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1});