const assert=require('node:assert/strict')
const fs=require('node:fs'),path=require('node:path'),http=require('node:http')
const {chromium}=require('playwright')
let profile=null,expiresAt=Date.now()+300000,unavailable=false,links=0
const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,'http://localhost')
  if(url.pathname==='/api/account'){
    res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store')
    if(unavailable){res.statusCode=503;res.end(JSON.stringify({error:'NIGHT connection is being updated. Please try again shortly.'}));return}
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
    profile={id:'NIGHT-test',displayName:'Pat'}
    await page.getByRole('button',{name:'Continue',exact:true}).waitFor({timeout:10000})
    await page.getByRole('button',{name:'Continue',exact:true}).click()
    assert.equal(await page.locator('#playerDisplayName').textContent(),'Pat')
    await page.getByRole('button',{name:'Edit name',exact:true}).click()
    assert.equal(await page.locator('#playerNameInput').inputValue(),'Pat')
    await page.locator('#playerNameInput').fill('Alex')
    await page.locator('#playerNameForm [type="submit"]').click()
    await page.waitForFunction(()=>window.NightAccount.profile?.displayName==='Alex')
    await page.locator('header [data-dialog="connect"]').click()
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
    await page.locator('header [data-dialog="connect"]').click()
    await page.getByRole('button',{name:'Remove NIGHT Play account',exact:true}).click()
    await page.getByRole('button',{name:'Remove account',exact:true}).click()
    await page.waitForFunction(()=>!window.NightAccount.profile)
    await page.locator('header [data-dialog="connect"]').click()
    await page.locator('.account-command').waitFor()
    await page.screenshot({path:path.join(__dirname,'visual-output/account-link-desktop.jpg')})
    assert.deepEqual(errors,[])
    console.log('PASS NIGHT Play account browser flow: phone/desktop layout, copy, automatic verification, shared name, disconnect, delete consent, outage/retry, expiry/renewal, dialog close and reload')
    await context.close()
  }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);server.closeAllConnections();server.close();process.exitCode=1})
