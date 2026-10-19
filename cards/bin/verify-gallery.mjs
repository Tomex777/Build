import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=process.cwd(),folder=path.join(root,process.argv[2]??'dist/bleach-refined-masters'),results=[];
const browser=await chromium.launch({headless:true,executablePath:process.env.NIGHT_BROWSER_PATH??path.join(root,'.cache/browser/chrome-headless-shell-linux64/chrome-headless-shell'),args:['--no-sandbox']});
try{
 for(const target of [path.join(folder,'index.html'),path.join(root,'dist/Bleach-Collection.html')]){
  const context=await browser.newContext({viewport:{width:360,height:800}});await context.setOffline(true);
  const page=await context.newPage(),errors=[],network=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(!/^(file:|data:)/.test(r.url()))network.push(r.url())});
  await page.goto(pathToFileURL(target).href);await page.evaluate(async()=>{const imgs=[...document.querySelectorAll('.card img')];for(const i of imgs)i.loading='eager';await Promise.all(imgs.map(i=>i.decode()))});
  assert.equal(await page.locator('.card').count(),85);assert.equal(await page.locator('.card:visible').count(),85);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile horizontal overflow');
  const search=page.getByRole('searchbox',{name:'Search cards'}),rank=page.getByLabel('Power rank',{exact:true});
  await search.fill('Ichigo');assert.equal(await page.locator('.card:visible').count(),5);
  await rank.selectOption('A');assert.equal(await page.locator('.card:visible').count(),3);
  await rank.selectOption('B');assert.equal(await page.locator('.card:visible').count(),1);
  await rank.selectOption('SS');assert.equal(await page.locator('.card:visible').count(),0);assert.ok(await page.locator('.empty').isVisible());
  await search.fill('');await rank.selectOption('SS');assert.equal(await page.locator('.card:visible').count(),1);
  await page.locator('.card:visible').click();assert.ok(await page.locator('dialog').isVisible());assert.match(await page.locator('#viewer-name').textContent(),/Ichibe/);await page.locator('#viewer-image').evaluate(i=>i.decode());
  await page.getByLabel('Next card',{exact:true}).click();assert.match(await page.locator('#viewer-name').textContent(),/Ichibe/);
  await page.keyboard.press('Escape');assert.ok(!await page.locator('dialog').isVisible());
  await rank.selectOption('');await search.fill('Yoruichi');assert.equal(await page.locator('.card:visible').count(),2);
  await page.locator('.card:visible').first().click();await page.locator('#viewer-image').evaluate(i=>i.decode());const firstImage=await page.locator('#viewer-image').getAttribute('src');
  assert.equal(await page.locator('#viewer-finish').textContent(),'nocturne');
  await page.getByLabel('Next card',{exact:true}).click();await page.locator('#viewer-image').evaluate(i=>i.decode());assert.equal(await page.locator('#viewer-finish').textContent(),'nocturne');assert.notEqual(await page.locator('#viewer-image').getAttribute('src'),firstImage);
  await page.keyboard.press('ArrowLeft');assert.equal(await page.locator('#viewer-image').getAttribute('src'),firstImage);
  await page.locator('summary').click();assert.ok(await page.locator('#scope').isVisible());assert.match(await page.locator('#references a').first().getAttribute('href'),/^https:\/\//);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Mobile viewer overflow');
  await page.getByLabel('Close card',{exact:true}).click();await search.fill('');
  await page.setViewportSize({width:1024,height:768});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Desktop overflow');
  await page.locator('.card').first().click();await page.locator('#viewer-image').evaluate(i=>i.decode());assert.ok(await page.evaluate(()=>document.querySelector('dialog').getBoundingClientRect().right<=innerWidth),'Desktop viewer overflow');await page.keyboard.press('Escape');
  assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
  const inline=await page.evaluate(()=>[...document.querySelectorAll('.card img')].every(i=>i.src.startsWith('data:')));
  if(target.endsWith('Bleach-Collection.html'))assert.ok(inline);
  results.push({target:path.basename(target),cards:85,ichigoForms:5,filters:true,viewer:true,keyboard:true,sourceLinks:true,phoneAndDesktop:true,offline:true,brokenImages:0,scriptErrors:0});
  await context.close();
 }
 await fs.mkdir(path.join(root,'.cache/collection-research'),{recursive:true});
 await fs.writeFile(path.join(root,'.cache/collection-research/gallery-qa.json'),JSON.stringify(results,null,2)+'\n');console.log(JSON.stringify(results,null,2));
}finally{await browser.close()}
