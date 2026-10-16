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
    await page.locator('#story p.go .word').first().waitFor({state:'attached'});
    assert.ok(await page.locator('#story .word').count()>=20,'individual word spans are present');
    const motion=await page.locator('#story p.go .word').first().evaluate(el=>{
      const style=getComputedStyle(el);return {name:style.animationName,duration:style.animationDuration}
    });
    assert.equal(motion.name,'word-ink','words animate their ink instead of sliding the whole line');
    assert.equal(motion.duration,'0.9s','default word transition follows 900ms source cadence');
    const sourceReference=await page.locator('.col').evaluate(el=>{const c=getComputedStyle(el),p=document.querySelector('#story p');const r=el.getBoundingClientRect(),cs=getComputedStyle(p);return{x:r.x,y:r.y,w:r.width,pSize:cs.fontSize,pLineHeight:cs.lineHeight,font:cs.fontFamily}});
    assert.ok(Math.abs(sourceReference.x-(width===390?20:360))<1,'reader x aligns to HARK public CSS reference');
    assert.ok(Math.abs(sourceReference.w-(width===390?350:560))<1,'reader width aligns to HARK public CSS reference');
    assert.ok(Math.abs(sourceReference.y-75.96)<1,'first line starts at HARK measured reading position');
    assert.equal(sourceReference.pSize,width===390?'17px':'17.5px','HARK reference text size matches');
    assert.ok(sourceReference.font.includes('Newsreader'),'reading text declares matching Newsreader typeface');
    const rectBefore=await page.locator('#story p').last().boundingBox();
    await page.waitForTimeout(600);
    const active=await page.locator('#story p.go').count();
    assert.ok(active>=3,'multiple narration lines animate together instead of one-by-one serial waiting');
    if(width===390&&process.env.NIGHT_STORY_VISUAL_QA==='1'){
      const shot=await page.screenshot({type:'jpeg',quality:42});
      console.log('STORY_MID_REVEAL_JPEG_BASE64='+shot.toString('base64'));
    }
    const rectAfter=await page.locator('#story p').last().boundingBox();
    assert.ok(Math.abs(rectBefore.y-rectAfter.y)<1,'full pre-layout avoids jarring vertical shifts');
    await page.locator('#story').click();
    assert.equal(await page.locator('.choice').count(),2,'choices appear immediately on tap');
    await page.waitForFunction(()=>{
      const el=document.querySelector('.choices');
      return el&&!el.classList.contains('pending')&&parseFloat(getComputedStyle(el).opacity)>.95;
    },null,{timeout:1500});
    assert.equal(await page.locator('.choice:visible').count(),2,'choices are actually visible, not only present in the DOM');
    const measuredChoices=await page.locator('.choices').evaluate(el=>{
      const a=el.getBoundingClientRect(),b=el.querySelector('.choice').getBoundingClientRect();
      return {x:a.x,y:a.y,w:a.width,h:b.height,transition:getComputedStyle(el).transitionDuration};
    });
    const expectedY=width===390?291:297;
    assert.ok(Math.abs(measuredChoices.y-expectedY)<3,'choice start matches HARK measured vertical rhythm');
    assert.ok(Math.abs(measuredChoices.h-(width===390?48:49))<2,'choice row matches HARK measured height');
    assert.equal(measuredChoices.transition,'0.14s','choices do not have a slow entrance');
    console.log('NIGHT_HARK_REFERENCE_METRICS',width,JSON.stringify({text:sourceReference,choices:measuredChoices}));
    if(width===390&&process.env.NIGHT_STORY_VISUAL_QA==='1'){
      const shot=await page.screenshot({type:'jpeg',quality:46,fullPage:true});
      console.log('STORY_FULL_SCENE_JPEG_BASE64='+shot.toString('base64'));
    }
    await page.locator('.choice').first().click();
    assert.match(await page.locator('#story').innerText(),/You lift the phone to your ear/,'branch choice works');
    await page.locator('#readerOptions summary').click();
    await page.getByRole('button',{name:'Restart story'}).click();
    await page.locator('#readerOptions summary').click();
    await page.locator('#pace').selectOption('instant');
    assert.equal(await page.locator('#story p').count(),5,'reading speed instant works');
    const size=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:innerWidth}));
    assert.ok(size.scroll<=size.width+1,'story has no horizontal overflow');
    await page.locator('.control-strip a').click();
    await page.waitForURL('**/games');
    await page.locator('#view-games').waitFor({state:'visible'});
    assert.equal(errors.length,0,'no page JavaScript exceptions: '+errors.join('; '));
    await page.close();
  }
  const script=story.match(/<script>([\s\S]*?)<\/script>/)[1];
  const scenes=JSON.parse(script.slice(script.indexOf('const scenes=')+13,script.indexOf(';\n\nconst reduced=')));
  const common=['start','answer','still','notebook','imani'];
  const routes=[
    [...common,'trust','tram','honest','museum','leila','bell','research','plan','descent','exchange','mother','solution','appeal','scales','testimony','release','hold','help','dawn','warning','home'],
    [...common,'alone','tram','lie','museum','bell','child','plan','descent','exchange','voss','mother','solution','fight','bell_route','break','severed'],
    [...common,'alone','tram','honest','museum','leila','bell','research','plan','descent','exchange','mother','sacrifice_offer','solution','appeal','scales','burden','keeper']
  ];
  for(const route of routes){
    const page=await browser.newPage({viewport:{width:390,height:844}});
    const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.addInitScript(()=>localStorage.setItem('night-play-fiction-reading-speed','instant'));
    await page.goto(origin+'/stories/');
    let flags={};
    for(let i=0;i<route.length-1;i++){
      const current=route[i],next=route[i+1];
      const option=scenes[current].options.find(o=>o[1]===next&&(!o[3]||flags[o[3]]));
      assert.ok(option,'test route has an available choice '+current+' → '+next);
      if(current==='hold'){
        await page.reload();
        assert.match(await page.locator('#story').innerText(),/You catch the bell/,'resume current scene');
        assert.equal(await page.getByRole('button',{name:/Call for the help you accepted/}).count(),1,'ally choice survives reload');
      }
      await page.locator('.choice').filter({has:page.locator('.choice-label',{hasText:option[0]})}).click();
      flags={...flags,...option[2]};
      assert.ok((await page.locator('#story').innerText()).includes(scenes[next].beats[0].text),'scene loads: '+next);
      assert.ok(await page.evaluate(()=>scrollY<2),'new scene starts at top');
      if(next==='bell_route'&&!flags.ally)assert.equal(await page.getByRole('button',{name:/Call for the help/}).count(),0);
    }
    assert.match(await page.locator('#story').innerText(),/THE END/,'complete ending text');
    await page.reload();
    assert.match(await page.locator('#story').innerText(),/THE END/,'ending persists');
    await page.getByRole('button',{name:/Read another path/}).click();
    const progress=await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-last-call-v2')));
    assert.equal(progress.scene,'start');assert.deepEqual(progress.flags,{},'new run clears consequences');
    assert.equal(errors.length,0,errors.join('; '));
    await page.close();
  }
  console.log('PASS combined NIGHT Play Games → Stories → overlapping 900ms ink, zero layout shifts → tap skip, choice branching, options → Games on mobile & desktop');
 } finally {await browser.close()}
})().catch(e=>{console.error(e.stack||e);process.exitCode=1}).finally(()=>server.close());