'use strict'
const assert=require('node:assert/strict')
const fs=require('node:fs'),http=require('node:http'),path=require('node:path')
const {chromium}=require('playwright')
const key='120363282083849178@newsletter'
let linked=true,channels=[{id:key,name:'🎀 ChRio 🎀',status:'connected'}],down=false,statusHits=0
const root=path.join(__dirname)
const server=http.createServer(async(req,res)=>{
 const pathname=new URL(req.url,'http://localhost').pathname
 if(pathname==='/api/account'){
  res.setHeader('content-type','application/json')
  if(down){res.writeHead(503);res.end(JSON.stringify({error:'NIGHT service unavailable'}));return}
  if(req.method==='GET'){statusHits++;res.end(JSON.stringify({linked,profile:linked?{id:'NIGHT-test',displayName:'Pat'}:null}));return}
  let raw='';for await(const part of req)raw+=part
  const payload=JSON.parse(raw)
  if(payload.action==='channel:list'){res.end(JSON.stringify({channels}));return}
  res.writeHead(400);res.end(JSON.stringify({error:'Unsupported'}));return
 }
 if(pathname==='/anonymous/'||pathname==='/anonymous'||pathname.startsWith('/anonymous/join/')){
  res.setHeader('content-type','text/html;charset=utf-8')
  res.end(fs.readFileSync(path.join(root,'index.html')));return
 }
 res.writeHead(404);res.end()
})
;(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
 const origin='http://127.0.0.1:'+server.address().port
 const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']})
 try{
  const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
  const errors=[];page.on('pageerror',e=>errors.push(e.message))
  await page.goto(origin+'/anonymous/?channel='+encodeURIComponent(key))
  await page.locator('#anonWorkspace').waitFor({state:'visible'})
  assert.equal(await page.locator('#anonChannelName').textContent(),'🎀 ChRio 🎀')
  assert.equal(await page.locator('#anonStatus').isVisible(),false)
  assert.equal(await page.locator('#heroPrimary').isVisible(),false,'fake publish UI must remain hidden')
  assert.equal(await page.locator('#submitMessage').isVisible(),false,'fake submissions cannot be posted')
  assert.equal(await page.getByRole('button',{name:'Start anonymous night, not available yet'}).isDisabled(),true,'no live invitation until backend is ready')
  assert.equal(await page.getByRole('link',{name:'Your channels'}).getAttribute('href'),'/channels/')
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,'no horizontal overflow')
  const capture=path.join(__dirname,'../visual-output')
  fs.mkdirSync(capture,{recursive:true})
  await page.screenshot({path:path.join(capture,'anonymous-linked-mobile.jpg'),fullPage:true})
  await page.setViewportSize({width:1280,height:900})
  await page.screenshot({path:path.join(capture,'anonymous-linked-desktop.jpg'),fullPage:true})
  // An arbitrary channel identifier does not confer administrative access.
  await page.goto(origin+'/anonymous/?channel=not-my-channel')
  await page.getByRole('heading',{name:'Channel not found'}).waitFor()
  assert.equal(await page.locator('#anonWorkspace').isVisible(),false)
  channels=[{id:key,name:'Not verified',status:'awaiting_admin'}]
  await page.goto(origin+'/anonymous/')
  await page.getByRole('heading',{name:'No verified channels'}).waitFor()
  assert.equal(await page.locator('#anonWorkspace').isVisible(),false)
  channels=[{id:key,name:'🎀 ChRio 🎀',status:'connected'}]
  linked=false
  await page.goto(origin+'/anonymous/')
  await page.getByRole('heading',{name:'Connect WhatsApp'}).waitFor()
  assert.equal(await page.locator('#anonWorkspace').isVisible(),false)
  assert.equal(await page.locator('#anonStatusLink').getAttribute('href'),'/profile')
  linked=true;down=true
  await page.goto(origin+'/anonymous/')
  await page.getByRole('heading',{name:'Channel unavailable'}).waitFor()
  assert.equal(await page.locator('#anonStatusRetry').isVisible(),true)
  assert.equal(await page.getByRole('link',{name:'Connect WhatsApp'}).count(),0)
  down=false
  await page.locator('#anonRetry').click()
  await page.locator('#anonWorkspace').waitFor({state:'visible'})
  const count=statusHits
  await page.goto(origin+'/anonymous/join/expired-invite')
  await page.getByRole('heading',{name:'This invitation is not active'}).waitFor()
  assert.equal(statusHits,count,'inactive visitor link does not request account details')
  assert.deepEqual(errors,[],'anonymous channel hub has no browser JS errors')
  await page.close()
  console.log('PASS Anonymous Nights: linked verified channel, honest inactive control, no fake publishing, unauthorized query, guest, outage, mobile/desktop, invalid visitor invite')
 }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().catch(err=>{console.error(err);server.closeAllConnections();server.close();process.exitCode=1})
