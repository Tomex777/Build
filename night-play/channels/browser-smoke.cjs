const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),http=require('node:http')
const {chromium}=require('playwright')
let linked=false,verified=false,deleted=false,unavailable=false
const channelId='120363282083849178@newsletter',proof='NIGHT-VERIFY-1234567890ABCDEF'
const server=http.createServer(async(req,res)=>{
  const route=new URL(req.url,'http://localhost').pathname
  if(route==='/api/account'){
    res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store')
    if(unavailable){res.statusCode=503;return res.end(JSON.stringify({error:'NIGHT connection is being updated.'}))}
    if(req.method==='GET')return res.end(JSON.stringify({linked,profile:linked?{id:'NIGHT-test',displayName:'Pat'}:null}))
    let body='';for await (const part of req)body+=part
    const {action,channelId:requested}=JSON.parse(body)
    if(action==='channel:list')return res.end(JSON.stringify({channels:deleted?[]:[{id:channelId,name:'After Hours Club',status:verified?'connected':'awaiting_admin',proof:verified?undefined:proof,expiresAt:Date.now()+3600000}]}))
    if(requested!==channelId){res.statusCode=400;return res.end(JSON.stringify({error:'Invalid channel.'}))}
    if(action==='channel:verify'){verified=true;return res.end(JSON.stringify({connected:true,name:'After Hours Club'}))}
    if(action==='channel:remove'){deleted=true;return res.end(JSON.stringify({ok:true}))}
    res.statusCode=400;return res.end(JSON.stringify({error:'Unknown action.'}))
  }
  if(route==='/site-shell.css'){
    res.setHeader('Content-Type','text/css; charset=utf-8')
    return res.end(fs.readFileSync(path.join(__dirname,'../site-shell.css')))
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
    await page.getByRole('heading',{name:'Connect WhatsApp'}).waitFor()
    assert.equal(await page.getByRole('link',{name:'Connect WhatsApp'}).isVisible(),true)
    assert.equal(await page.locator('#connect').isVisible(),false,'Private setup is hidden until authenticated')
    assert.equal(await page.locator('nav.mobile-nav').isVisible(),true,'Approved NIGHT Play bottom navigation exists')
    assert.equal(await page.locator('.site-header .brand-mark').count(),1,'Uses official NIGHT Play logo')
    assert.equal(await page.getByRole('button',{name:'Refresh channels'}).count(),0,'No redundant refresh button')
    linked=true
    await page.reload()
    await page.getByRole('heading',{name:'After Hours Club'}).waitFor()
    assert.match(await page.locator('.channel-pill').textContent(),/Waiting for admin/)
    assert.equal(await page.locator('.channel-anonymous').count(),0,'unverified channels do not get anonymous session entry')
    assert.equal(await page.locator('#inviteUrl,#linkForm,.url-wrap').count(),0)
    assert.equal(await page.locator('#connect').isVisible(),true)
    assert.match(await page.locator('#connect').textContent(),/\.joinchannel/)
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'No mobile horizontal overflow')
    await page.screenshot({path:path.join(__dirname,'../visual-output/channels-mobile.jpg'),fullPage:true})
    await page.getByRole('button',{name:'Copy proof'}).click()
    assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),proof)
    await page.getByRole('button',{name:'Check access'}).click()
    await page.locator('.channel-pill.ready').waitFor()
    assert.equal(await page.locator('.channel-proof').count(),0)
    const anon=page.getByRole('link',{name:'Open Anonymous Nights for After Hours Club'})
    assert.equal(await anon.isVisible(),true,'verified channel has a prominent Anonymous Nights entry')
    assert.equal(await anon.getAttribute('href'),'/anonymous/?channel='+encodeURIComponent(channelId))
    await page.screenshot({path:path.join(__dirname,'../visual-output/channels-verified-anonymous-mobile.jpg'),fullPage:true})
    await page.getByRole('button',{name:'Disconnect',exact:true}).click()
    assert.match(await page.locator('.channel-confirm').textContent(),/Revoke that permission/)
    await page.getByRole('button',{name:'Keep channel'}).click()
    assert.equal(await page.locator('.channel-confirm').count(),0,'No confirmation dialog if user cancels')
    await page.getByRole('button',{name:'Disconnect',exact:true}).click()
    await page.getByRole('button',{name:'Disconnect channel'}).click()
    await page.getByRole('heading',{name:'No channels connected'}).waitFor()
    assert.equal(await page.getByRole('link',{name:'Connect WhatsApp'}).isVisible(),false)
    unavailable=true
    await page.reload()
    await page.getByRole('heading',{name:'Channels unavailable'}).waitFor()
    assert.equal(await page.getByRole('link',{name:'Connect WhatsApp'}).isVisible(),false,'Outages must not be misrepresented as logged-out users')
    assert.equal(await page.getByRole('button',{name:'Try again'}).isVisible(),true)
    assert.match(await page.locator('#stateText').textContent(),/does not mean/)
    unavailable=false
    await page.getByRole('button',{name:'Try again'}).click()
    await page.getByRole('heading',{name:'No channels connected'}).waitFor()
    await page.setViewportSize({width:1280,height:900})
    await page.screenshot({path:path.join(__dirname,'../visual-output/channels-desktop.jpg'),fullPage:true})
    assert.equal(await page.locator('nav.mobile-nav').isVisible(),false)
    assert.equal(await page.locator('.site-header .links').isVisible(),true)
    assert.deepEqual(errors,[])
    await context.close()
    console.log('PASS NIGHT Play channels: unified NIGHT chrome, mobile nav, no duplicate reload/form, proof, guarded disconnect, proper offline state, two viewports')
  }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);server.closeAllConnections();server.close();process.exitCode=1})
