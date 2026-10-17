const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),http=require('node:http')
const {chromium}=require('playwright')
let linked=false,verified=false,deleted=false
const channelId='120363282083849178@newsletter',proof='NIGHT-VERIFY-1234567890ABCDEF'
const server=http.createServer(async(req,res)=>{
  const route=new URL(req.url,'http://localhost').pathname
  if(route==='/api/account'){
    res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store')
    if(req.method==='GET')return res.end(JSON.stringify({linked,profile:linked?{id:'NIGHT-test',displayName:'Pat'}:null}))
    let body='';for await (const part of req)body+=part
    const {action,channelId:requested}=JSON.parse(body)
    if(action==='channel:list')return res.end(JSON.stringify({channels:deleted?[]:[{id:channelId,name:'After Hours Club',status:verified?'connected':'awaiting_admin',proof:verified?undefined:proof,expiresAt:Date.now()+3600000}]}))
    if(requested!==channelId){res.statusCode=400;return res.end(JSON.stringify({error:'Invalid channel.'}))}
    if(action==='channel:verify'){verified=true;return res.end(JSON.stringify({connected:true,name:'After Hours Club'}))}
    if(action==='channel:remove'){deleted=true;return res.end(JSON.stringify({ok:true}))}
    res.statusCode=400;return res.end(JSON.stringify({error:'Unknown action.'}))
  }
  if(route==='/channels/'||route==='/channels'){
    res.setHeader('Content-Type','text/html; charset=utf-8')
    return res.end(fs.readFileSync(path.join(__dirname,'index.html')))
  }
  res.statusCode=404;res.end()
})
;(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']})
  const base='http://127.0.0.1:'+server.address().port
  fs.mkdirSync(path.join(__dirname,'../visual-output'),{recursive:true})
  try{
    const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
    await context.grantPermissions(['clipboard-write','clipboard-read'],{origin:base})
    const page=await context.newPage(),errors=[]
    page.on('pageerror',e=>errors.push(String(e)))
    await page.goto(base+'/channels/')
    await page.locator('#emptyTitle').getByText('Connect WhatsApp first').waitFor()
    assert.equal(await page.locator('#connect').isVisible(),false,'Unauthenticated users do not see onboarding instructions')
    assert.equal(await page.locator('#connectWhatsApp').isVisible(),true)
    linked=true
    await page.getByRole('button',{name:'Refresh channels'}).click()
    await page.getByRole('heading',{name:'After Hours Club'}).waitFor()
    assert.match(await page.locator('.pill').textContent(),/Waiting for NIGHT admin/)
    assert.equal(await page.locator('#inviteUrl,#linkForm,.url-wrap').count(),0,'No redundant website channel URL form')
    assert.equal(await page.getByRole('button',{name:'Copy channel link'}).count(),0,'No redundant copy channel link button')
    assert.equal(await page.locator('#connect').isVisible(),true,'Onboarding text for linked owners')
    assert.match(await page.locator('#connect').textContent(),/\.joinchannel/)
    assert.match(await page.locator('#connect').textContent(),/Admin permission is separate/)
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'No mobile overflow')
    await page.screenshot({path:path.join(__dirname,'../visual-output/channels-mobile.jpg'),fullPage:true})
    await page.getByRole('button',{name:'Copy proof'}).click()
    assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),proof)
    await page.getByRole('button',{name:'Check access'}).click()
    await page.locator('.pill.ready').waitFor()
    assert.equal(await page.locator('.proof').count(),0)
    page.once('dialog',dialog=>dialog.accept())
    await page.getByRole('button',{name:'Disconnect'}).click()
    await page.getByText('No channels linked yet').waitFor()
    await page.setViewportSize({width:1280,height:900})
    await page.screenshot({path:path.join(__dirname,'../visual-output/channels-desktop.jpg'),fullPage:true})
    assert.deepEqual(errors,[])
    await context.close()
    console.log('PASS NIGHT Play Channels mobile/desktop: no redundant input or copy-link action, verified-channel guide, proof, disconnect and no overflow')
  }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);server.closeAllConnections();server.close();process.exitCode=1})
