'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const main=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const story=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const server=http.createServer((req,res)=>{
  res.writeHead(200,{'content-type':'text/html; charset=utf-8','cache-control':'no-store'});
  res.end(req.url.startsWith('/stories')?story:main);
});
(async()=>{
 await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const origin='http://127.0.0.1:'+server.address().port;
 try{
  for(const width of [390,1280]){
    const page=await browser.newPage({viewport:{width,height:844},isMobile:width<600,hasTouch:width<600});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(origin+'/games');
    await page.locator('#view-games').waitFor({state:'visible'});
    assert.equal(await page.locator('.library-card.story-entry[href="/stories/"]').count(),1,'story preview card exists once');
    await page.getByRole('button',{name:'Stories',exact:true}).click();
    assert.equal(await page.locator('.library-card:visible').count(),1,'Stories filter isolates interactive fiction');
    const link=page.locator('.library-card.story-entry');
    await link.click();
    await page.waitForURL('**/stories/');
    assert.match(await page.title(),/The Last Call/i);
    assert.equal(await page.locator('#story p').count(),5,'all narration is pre-laid for zero page-shifting');
    await page.locator('#story p.go .ink-word').first().waitFor({state:'attached'});
    assert.ok(await page.locator('#story .ink-word').count()>=20,'individual word spans are present');
    const motion=await page.locator('#story p.go .ink-word').first().evaluate(el=>{
      const style=getComputedStyle(el);return {name:style.animationName,duration:style.animationDuration}
    });
    assert.equal(motion.name,'appear','words animate their ink rather than whole-line sliding');
    assert.equal(motion.duration,'0.9s','default word transition follows 900ms source cadence');
    const rectBefore=await page.locator('#story p').last().boundingBox();
    await page.waitForTimeout(600);
    const active=await page.locator('#story p.go').count();
    assert.ok(active>=3,'multiple narration lines animate together instead of one-by-one serial waiting');
    const rectAfter=await page.locator('#story p').last().boundingBox();
    assert.ok(Math.abs(rectBefore.y-rectAfter.y)<1,'full pre-layout avoids jarring vertical shifts');
    await page.locator('#story').click();
    assert.equal(await page.locator('.choice').count(),2,'choices appear immediately on tap');
    await page.locator('.choice').first().click();
    assert.match(await page.locator('#location').textContent(),/02:14 AM/,'branch choice works');
    await page.getByRole('button',{name:'Reading options'}).click();
    await page.getByRole('button',{name:'Restart story'}).click();
    await page.getByRole('button',{name:'Reading options'}).click();
    await page.locator('#pace').selectOption('instant');
    assert.equal(await page.locator('#story p').count(),5,'reading speed instant works');
    const size=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth}));
    assert.ok(size.scroll<=size.width+1,'story has no horizontal overflow');
    await page.getByRole('link',{name:/Games/}).first().click();
    await page.waitForURL('**/games');
    await page.locator('#view-games').waitFor({state:'visible'});
    assert.equal(errors.length,0,'no page JavaScript exceptions: '+errors.join('; '));
    await page.close();
  }
  console.log('PASS combined NIGHT Play Games → Stories → overlapping 900ms ink, zero layout shifts → tap skip, choice branching, options → Games on mobile & desktop');
 } finally {await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>server.close());