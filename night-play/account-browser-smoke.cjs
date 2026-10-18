const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),http=require('node:http')
const {chromium}=require('playwright')
let profile=null,expiresAt=Date.now()+300000,unavailable=false,links=0,slowStatusMs=0,statusGets=0
const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,'http://localhost')
  if(url.pathname==='/api/account'){
     if(url.searchParams.get('resource')==='notice'){res.setHeader('Content-Type','application/json');res.end(JSON.stringify({enabled:false,revision:0}));return}
    res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store')
    if(unavailable){res.statusCode=503;res.end(JSON.stringify({error:'NIGHT connection is being updated. Please try again shortly.'}));return}
    if(req.method==='GET'){statusGets++;if(slowStatusMs){const delay=slowStatusMs;slowStatusMs=0;await new Promise(resolve=>setTimeout(resolve,delay))}}
    let body={};if(req.method==='POST'){let raw='';for await(const chunk of req)raw+=chunk;body=JSON.parse(raw)}
    if(body.action==='link'){links++;res.end(JSON.stringify({code:'ABCD-EFGH-JKLM',command:'.minigames link ABCD-EFGH-JKLM',expiresAt}));return}
    if(body.action==='logout'||body.action==='delete')profile=null
    if(body.action==='profile')profile={...profile,displayName:body.displayName}
    res.end(JSON.stringify({linked:Boolean(profile),profile}));return
  }
  const relative=['/','/profile','/dashboard','/games','/terms','/privacy'].includes(url.pathname)?'index.html':url.pathname.slice(1)
  const file=path.join(__dirname,relative)
  if(!file.startsWith(__dirname+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.statusCode=404;res.end();return}
  res.setHeader('Content-Type',relative.endsWith('.js')?'text/javascript':relative.endsWith('.css')?'text/css':'text/html')
  res.end(fs.readFileSync(file))
})
;(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
  const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']})
  const base='http://127.0.0.1:'+server.address().port
  fs.mkdirSync(path.join(__dirname,'visual-output'),{recursive:true})
  try{
    const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
    await context.grantPermissions(['clipboard-read','clipboard-write'],{origin:base})
    // Simulate a known signed-in browser on a slow backend. The first paint
    // must NEVER show the Connect WhatsApp prompt while the HttpOnly cookie is
    // still being validated, and startup must produce only one GET /status.
    profile={id:'NIGHT-warm',displayName:'Pat'}
    slowStatusMs=1150
    const warm=await context.newPage(),warmErrors=[]
    warm.on('pageerror',error=>warmErrors.push(String(error)))
    const initialGets=statusGets
    await warm.goto(base+'/profile',{waitUntil:'domcontentloaded'})
    await warm.waitForFunction(()=>Boolean(window.NightAccount))
    assert.equal(await warm.locator('html').getAttribute('data-night-account'),'checking','pending verification is neither guest nor signed-in')
    assert.equal(await warm.locator('header [data-dialog="connect"]').isVisible(),false,'no false disconnected flash while loading')
    await warm.waitForFunction(()=>document.documentElement.dataset.nightAccount==='linked',{timeout:5000})
    assert.equal(await warm.locator('header [data-dialog="connect"]').isVisible(),false,'linked session keeps sign-in action absent')
    assert.equal(await warm.locator('#playerDisplayName').textContent(),'Pat')
    assert.equal(statusGets-initialGets,1,'head prefetch and account client share one request')
    const firstState=await warm.evaluate(()=>window.NightAccount.profile?.displayName)
    assert.equal(firstState,'Pat')
    // A guest sees the same neutral pending state, then exactly one CTA.
    profile=null;slowStatusMs=1100
    const guestGets=statusGets
    await warm.reload({waitUntil:'domcontentloaded'})
    await warm.waitForFunction(()=>Boolean(window.NightAccount))
    assert.equal(await warm.locator('html').getAttribute('data-night-account'),'checking')
    assert.equal(await warm.locator('header [data-dialog="connect"]').isVisible(),false)
    await warm.waitForFunction(()=>document.documentElement.dataset.nightAccount==='guest',{timeout:5000})
    assert.equal(await warm.locator('header [data-dialog="connect"]').isVisible(),true)
    assert.equal(await warm.locator('header [data-dialog="connect"]').textContent(),'Connect WhatsApp')
    assert.equal(statusGets-guestGets,1,'guest startup also avoids duplicate account status fetches')
    assert.deepEqual(warmErrors,[])
    await warm.close()
    const page=await context.newPage(),errors=[]
    page.on('pageerror',error=>errors.push(String(error)))
    await page.goto(base+'/profile')
    await page.waitForFunction(()=>Boolean(window.NightAccount))
    await page.locator('header [data-dialog="connect"]').click()
    await page.locator('.account-command').waitFor()
    assert.equal(await page.locator('.account-command').textContent(),'.minigames link ABCD-EFGH-JKLM')
    await page.getByRole('button',{name:'Copy command',exact:true}).click()
    assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),'.minigames link ABCD-EFGH-JKLM')
    assert.equal(await page.locator('#infoDialog').evaluate(el=>el.scrollWidth<=el.clientWidth),true)
    await page.screenshot({path:path.join(__dirname,'visual-output/account-link-mobile.jpg')})
    profile={id:'NIGHT-test',displayName:'Pat',avatarData:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+i4VsAAAAASUVORK5CYII='}
    await page.getByRole('button',{name:'Continue',exact:true}).waitFor({timeout:10000})
    await page.getByRole('button',{name:'Continue',exact:true}).click()
    assert.equal(await page.locator('#playerDisplayName').textContent(),'Pat')
    assert.equal(await page.locator('header [data-dialog="connect"]').isVisible(),false)
    assert.equal(await page.locator('#profileIdentityCaption').isVisible(),false)
    assert.equal(await page.locator('#profileAvatar').isVisible(),true)
    assert.equal(await page.locator('#accountAchievements .account-achievement').count(),4)
    await page.locator('[data-profile-tab="games"]').click()
    assert.equal(await page.locator('#recordsPicker').isVisible(),true)
    await page.getByRole('button',{name:'Edit name',exact:true}).click()
    assert.equal(await page.locator('#playerNameInput').inputValue(),'Pat')
    await page.locator('#playerNameInput').fill('Alex')
    await page.locator('#playerNameForm [type="submit"]').click()
    await page.waitForFunction(()=>window.NightAccount.profile?.displayName==='Alex')
    await page.locator('[data-profile-tab="settings"]').click()
    await page.getByRole('button',{name:'Manage account',exact:true}).click()
    await page.getByRole('button',{name:'Disconnect this browser',exact:true}).waitFor()
    await page.screenshot({path:path.join(__dirname,'visual-output/account-connected-mobile.jpg')})
    await page.getByRole('button',{name:'Remove NIGHT Play account',exact:true}).click()
    await page.getByRole('button',{name:'Keep account',exact:true}).click()
    assert.equal(profile.displayName,'Alex')
    await page.getByRole('button',{name:'Disconnect this browser',exact:true}).click()
    await page.waitForFunction(()=>!window.NightAccount.profile)
    unavailable=true
    await page.locator('header [data-dialog="connect"]').click()
    await page.getByRole('button',{name:'Try again',exact:true}).waitFor()
    assert.match(await page.locator('#infoBody').textContent(),/being updated/)
    unavailable=false;expiresAt=Date.now()+2000
    await page.getByRole('button',{name:'Try again',exact:true}).click()
    await page.getByRole('button',{name:'Generate a new code',exact:true}).waitFor({timeout:7000})
    const before=links;expiresAt=Date.now()+300000
    await page.getByRole('button',{name:'Generate a new code',exact:true}).click()
    await page.locator('.account-command').waitFor()
    assert.equal(links,before+1)
    await page.keyboard.press('Escape')
    assert.equal(await page.locator('#infoDialog').evaluate(el=>el.open),false)
    profile={id:'NIGHT-test',displayName:'Alex'}
    await page.reload()
    await page.waitForFunction(()=>window.NightAccount?.profile?.displayName==='Alex')
    await page.setViewportSize({width:1280,height:900})
    await page.locator('[data-profile-tab="settings"]').click()
    await page.getByRole('button',{name:'Manage account',exact:true}).click()
    await page.getByRole('button',{name:'Remove NIGHT Play account',exact:true}).click()
    await page.getByRole('button',{name:'Remove account',exact:true}).click()
    await page.waitForFunction(()=>!window.NightAccount.profile)
    await page.locator('header [data-dialog="connect"]').click()
    await page.locator('.account-command').waitFor()
    await page.screenshot({path:path.join(__dirname,'visual-output/account-link-desktop.jpg')})
    assert.deepEqual(errors,[])
    console.log('PASS NIGHT Play account browser flow: zero auth flash, one early HttpOnly status request, slow linked/guest hydration, phone/desktop layout, copy, automatic verification, name, disconnect, outage, expiry and reload')
    await context.close()
  }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().then(()=>require('./multiplayer/ttt/mobile-smoke.cjs')).catch(error=>{console.error(error);server.closeAllConnections();server.close();process.exitCode=1})
