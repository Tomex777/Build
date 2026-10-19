import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { chromium } from 'playwright'
const html=readFileSync(new URL('./index.html',import.meta.url))
const server=createServer((req,res)=>{if(req.url==='/site-shell.css'){res.writeHead(200,{'content-type':'text/css'});res.end(readFileSync(new URL('./site-shell.css',import.meta.url)));return}if(req.url==='/account-client.js'){res.writeHead(200,{'content-type':'text/javascript'});res.end(readFileSync(new URL('./account-client.js',import.meta.url)));return}if(req.url==='/api/account'){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({linked:false,profile:null}));return}res.writeHead(200,{'content-type':'text/html; charset=utf-8','cache-control':'no-store'});res.end(html)})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
let browser
try {
 const base='http://127.0.0.1:'+server.address().port
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']})
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
 const failures=[]
 async function checkNav(destination){
  const metrics=await page.evaluate(()=>{
   const nav=document.querySelector('.mobile-nav'),box=nav.getBoundingClientRect();
   const items=[...nav.querySelectorAll('a')].map(a=>{
    const rect=a.getBoundingClientRect(),style=getComputedStyle(a);
    return {name:a.textContent.trim(),height:rect.height,width:rect.width,current:a.getAttribute('aria-current'),background:style.backgroundColor}
   });
   return {display:getComputedStyle(nav).display,top:box.top,bottom:box.bottom,height:box.height,viewportHeight:innerHeight,
    headerProfile:document.querySelectorAll('header .profile-icon').length,
    overflow:document.documentElement.scrollWidth-innerWidth,viewportWidth:innerWidth,items};
  });
  assert.equal(metrics.display,'grid','bottom navigation is balanced in three equal tracks');
  assert.ok(Math.abs(metrics.bottom-metrics.viewportHeight)<=2,'bar anchors to bottom of visible viewport');
  assert.equal(metrics.headerProfile,0,'no duplicated top-right profile shortcut');
  assert.ok(metrics.items.every(item=>item.height>=44),'each tab has at least 44px height');
  assert.equal(metrics.items.filter(item=>item.current==='page').length,1,'one active bottom destination');
  assert.equal(metrics.items.find(item=>item.current==='page').name,destination);
  assert.notEqual(metrics.items.find(item=>item.current==='page').background,'rgba(0, 0, 0, 0)','selected tab has a visible surface');
  assert.ok(metrics.overflow<=2,'mobile navigation never introduces horizontal scrolling');
   assert.ok(Math.abs(metrics.viewportWidth-(page.viewportSize()?.width||390))<=1,'mobile layout must not auto-zoom due to oversized header');
  console.log('NIGHT_NAV_METRICS '+JSON.stringify(metrics));
 }

 page.on('pageerror',err=>failures.push(String(err)))
 await page.goto(base+'/')
 await page.locator('#view-welcome').waitFor({state:'visible'})
 assert.equal(await page.locator('#view-games').isVisible(),false)
 assert.equal(await page.locator('.mobile-nav').isVisible(),false,'Landing page must not show app tabs')
 assert.equal(await page.locator('header .links').isVisible(),false,'Landing page must not show app navigation')
 assert.equal(await page.locator('header .landing-enter').isVisible(),true)
 assert.equal(await page.locator('.hero-art .floating').count(),0,'Welcome art uses no fake floating badges')
 assert.equal(await page.locator('.hero-game-art').evaluate(el=>getComputedStyle(el).transform),'none','Art remains flat rather than tilted')
 assert.equal(await page.locator('.hero-game-art').evaluate(el=>getComputedStyle(el).filter),'none','No artificial depth effects')

 await page.locator('#announcementMore').click()
 assert.equal(await page.locator('#announcementHistory').isVisible(),true)
 await page.locator('#announcementHistoryClose').click()
 assert.equal(await page.locator('#announcementHistory').isVisible(),false)
 await page.locator('#announcementDismiss').click()
 assert.equal(await page.locator('#announcementBar').isVisible(),false,'Dismiss must remove banner and dropdown')
 await page.locator('#view-welcome .hero').getByRole('link',{name:/Enter NIGHT Play/}).click()
 await page.locator('#view-dashboard').waitFor({state:'visible'})
 assert.equal(new URL(page.url()).pathname,'/dashboard')
 assert.match((await page.locator('#homeGreeting').textContent())||'',/^Good (morning|afternoon|evening|night), Player\.$/)
 assert.equal(await page.locator('header').evaluate(el=>getComputedStyle(el).position),'sticky')
 assert.equal(await page.locator('#announcementBar').evaluate(el=>getComputedStyle(el).position),'sticky')
 assert.equal(await page.locator('#view-dashboard #dashSnakeBest').count(),0)
 await checkNav('Home')
 console.log('NIGHT_NAV_AFTER_DASHBOARD='+(await page.screenshot({type:'jpeg',quality:68})).toString('base64'))
 await page.evaluate(()=>{
  localStorage.setItem('night-play-v1-snake','90');
  localStorage.setItem('night-play-history-v1-snake',JSON.stringify([12,35,90]));
  for(let i=0;i<120;i++)localStorage.setItem('night-play-history-v1-game-'+i,JSON.stringify([i,i+1]));
 })
 await page.locator('.mobile-nav a[href="/profile"]').click()
 await page.locator('#view-profile').waitFor({state:'visible'})
 assert.equal(new URL(page.url()).pathname,'/profile')
 await checkNav('Profile')
 console.log('NIGHT_NAV_AFTER_PROFILE='+(await page.screenshot({type:'jpeg',quality:68})).toString('base64'))
 assert.equal(await page.locator('#view-profile .inner-page-head').count(),0,'Profile begins with player card, not a redundant heading')
 await page.locator('#editPlayerName').click()
 await page.locator('#playerNameInput').fill('Alex')
 await page.locator('#playerNameForm button[type="submit"]').click()
 assert.equal(await page.locator('#playerDisplayName').textContent(),'Alex')
 assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-player-name-v1')),'Alex')

 assert.equal(await page.locator('#view-profile').getByText('Not connected').count(),0,'No repeated disconnected status')
 assert.equal(await page.locator('#view-profile button[data-dialog="connect"]').count(),0,'Card must not duplicate the connection CTA')
 assert.equal(await page.locator('header button[data-dialog="connect"]').isVisible(),true,'Only global connect CTA')
 assert.equal(await page.locator('#selectedGameTitle').textContent(),'Game activity','Overview is default')
 assert.equal(await page.locator('#analyticsBest').textContent(),'121','121 games with history in test')
 assert.equal(await page.locator('#analyticsLatest').textContent(),'243','Compare completed rounds instead of unlike scores')
 assert.ok(await page.locator('#analyticsGraph rect').count()>4,'Activity bars show per-game participation')
 assert.equal(await page.locator('#analyticsGraph circle').count(),0,'Overview is a per-game chart rather than selected scores')
 await page.locator('#recordsPicker').click()
 assert.ok(await page.locator('#recordOptions button').count()>100,'Picker should support 100+ games in a fixed-height menu')
 assert.equal(await page.locator('#recordOptions button').first().innerText().then(x=>x.includes('All games')),true,'Overview must be selectable')
 await page.locator('#recordSearch').fill('game 117')
 assert.equal(await page.locator('#recordOptions button').count(),1,'Search must filter game options')
 await page.locator('#recordOptions button').click()
 assert.equal(await page.locator('#selectedGameTitle').textContent(),'Game 117')
 assert.equal(await page.locator('#analyticsBest').textContent(),'0')
 assert.equal(await page.locator('#analyticsLatest').textContent(),'118')
 assert.ok(await page.locator('#analyticsGraph circle').count()>0)
 await page.locator('#recordsPicker').click()
 await page.locator('#recordSearch').fill('Snake')
 await page.locator('#recordOptions button').click()
 assert.equal(await page.locator('#analyticsBest').textContent(),'90')
 assert.equal(await page.locator('#analyticsRuns').textContent(),'3')
 assert.equal(await page.locator('#analyticsGraph circle').count(),3)
 await page.locator('#recordsPicker').click()
 await page.locator('#recordSearch').fill('all games')
 await page.locator('#recordOptions button').first().click()
 assert.equal(await page.locator('#selectedGameTitle').textContent(),'Game activity')
 await page.locator('.mobile-nav a[href="/games"]').click()
 await page.locator('#view-games').waitFor({state:'visible'})
 await checkNav('Games')
 console.log('NIGHT_NAV_AFTER_GAMES='+(await page.screenshot({type:'jpeg',quality:68})).toString('base64'))
 console.log('NIGHT_GAME_GALLERY_BASE64='+(await page.locator('#view-games').screenshot({type:'jpeg',quality:58})).toString('base64'));
 assert.equal(await page.locator('.brand-mark use').first().getAttribute('href'),'#icon-brand')
 assert.equal(await page.locator('.library-card .game-art').count(),9,'Other games retain their SVG thumbnails')
 assert.equal(await page.locator('.library-card[data-jigsaw] .jigsaw-cover-photo').count(),1,'Jigsaw no longer uses the old generated puzzle illustration')
 assert.equal(await page.locator('.library-card[data-name="wing hop flappy bird"] .wing-preview-bird').count(),1,'Wing Hop uses actual bundled character artwork')
 assert.equal(await page.locator('.library-card[data-club="ludo"]').count(),0,'Unfinished Ludo is hidden from the public game library')
 assert.equal(await page.locator('.library-card[data-jigsaw]').count(),1,'Jigsaw is listed among puzzle games')
 assert.equal(await page.locator('.mobile-nav .ui-icon').count(),3,'All mobile tabs render SVG icons')
 assert.equal(await page.locator('.library-card .game-emoji').count(),0,'No emoji artwork')
 assert.ok(await page.locator('.library-card').first().evaluate(el=>getComputedStyle(el).borderTopWidth)==='1px','Cards have thin border')
 assert.ok(await page.locator('.library-card').first().evaluate(el=>getComputedStyle(el).boxShadow)!=='none','Games have soft shadow')

 const artBounds=await page.locator('.library-card .game-art').first().evaluate(el=>({width:el.getBBox().width,height:el.getBBox().height}))
 assert.ok(artBounds.width>40&&artBounds.height>30,'Artwork must occupy visible SVG geometry')
 await page.locator('.library-card[data-game="2048"]').click()
 await page.locator('#board2048 .tile').first().waitFor()
 assert.equal(new URL(page.url()).pathname,'/play/2048')
 assert.equal(await page.locator('#gameDialog').getAttribute('data-skin'),'2048','Game-specific visual skin is applied')

 assert.equal(await page.locator('#board2048 .tile').count(),16)
 assert.match(await page.locator('#gameSettings svg path').getAttribute('d'),/^M9\.671 /,'2048 has the approved gear icon');
 const btn2048=await page.locator('#gameSettings').boundingBox();
 assert.ok(btn2048.width>=37&&btn2048.width<=100,'2048 settings icon matches compact Jigsaw button');
 // Re-rendering and swiping must not remount the grid or hide its cell surfaces.
 const stable2048=await page.evaluate(()=>{
  grid=[[0,0,0,2],[0,0,0,0],[0,0,0,0],[0,0,0,0]];tileScore=0;draw2048();
  const board=document.getElementById('board2048'),before=[...board.querySelectorAll(':scope > .tile')];
  move2048('left');
  const after=[...board.querySelectorAll(':scope > .tile')];
  return {sameNodes:before.every((node,i)=>node===after[i]),sliding:board.classList.contains('tile-sliding'),
   visible:after.every(node=>getComputedStyle(node).visibility==='visible'),
   opaque:after.every(node=>getComputedStyle(node).backgroundColor!=='rgba(0, 0, 0, 0)'),
   movingTiles:board.querySelectorAll(':scope > .tile-layer .tile-ghost').length};
 });
 assert.equal(stable2048.sameNodes,true,'2048 must retain grid cell nodes on swipe');
 assert.equal(stable2048.sliding,true,'2048 retains the sliding-tile animation');
 assert.equal(stable2048.visible,true,'2048 grid cells remain visible during animation');
 assert.equal(stable2048.opaque,true,'2048 grid cells have an opaque backing throughout animation');
 assert.ok(stable2048.movingTiles>0,'2048 animated tiles are rendered above stable grid');
 await page.evaluate(()=>{move2048('right');move2048('left')});
 await page.waitForTimeout(230);
 assert.equal(await page.locator('#board2048').evaluate(board=>board.classList.contains('tile-sliding')),false,'Rapid swipes must clean up the latest animation');
 assert.equal(await page.locator('#board2048 .tile-layer').count(),0,'No stale 2048 animation overlay after rapid swipes');
 assert.ok(await page.locator('#board2048').evaluate(el=>el.getBoundingClientRect().width)>=376,'2048 arena occupies nearly full 390px viewport')
 const title2048=await page.locator('#gameTitle').boundingBox();
 assert.ok(title2048.y<130,'Game title is at the TOP, not alongside the board');
 // The game must consume a swipe before releasing the finger.
 // Send real Chromium touch events, not only synthetic PointerEvents.
 // Verify the 2048 move happens on touchMove, while the finger is still down.
 await page.evaluate(()=>{grid=[[2,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];tileScore=0;draw2048()});
 const cdp=await page.context().newCDPSession(page);
 const b2048=await page.locator('#board2048').boundingBox();
 const sx=b2048.x+b2048.width*.3,sy=b2048.y+b2048.height*.4;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:sx,y:sy,id:7}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:sx+75,y:sy,id:7}]});
 await page.waitForFunction(()=>grid[0][3]===2,null,{timeout:1500});
 assert.equal(await page.evaluate(()=>grid[0][3]),2,'Real 2048 gesture must act before touch release');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 const movedOnPointerMove=await page.evaluate(()=>{
  grid=[[2,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];tileScore=0;draw2048();
  const el=document.getElementById('board2048');
  const send=(type,x,y)=>el.dispatchEvent(new PointerEvent(type,{bubbles:true,pointerId:73,pointerType:'touch',clientX:x,clientY:y}));
  send('pointerdown',100,200);send('pointermove',160,200);
  const received=grid[0][3]===2;send('pointerup',160,200);return received
 });
 assert.equal(movedOnPointerMove,true,'2048 touch input must act during swipe, not on touchend');


 await page.locator('#gameSettings').click()
 await page.getByRole('button',{name:'5 × 5'}).click()
 await page.locator('#closeSettings').click()
 assert.equal(await page.locator('#board2048 .tile').count(),25)
 await page.keyboard.press('ArrowLeft')
 await page.keyboard.press('ArrowUp')
 assert.ok(await page.evaluate(()=>Boolean(localStorage.getItem('night-play-save-2048-5'))))
 await page.locator('[data-close-game]').first().click()
 await page.locator('.library-card[data-game="snake"]').click()
 assert.match(await page.locator('#gameSettings svg path').getAttribute('d'),/^M9\.671 /,'Snake has the corrected settings icon');
 console.log('NIGHT_SNAKE_SETTINGS_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:48})).toString('base64'));
 assert.equal(await page.locator('.dpad').count(),0,'No Snake directional buttons exist')
 const tallArena=await page.locator('.snake-shell').evaluate(el=>{const r=el.getBoundingClientRect();return {width:r.width,height:r.height,ratio:r.height/r.width}});
 assert.ok(tallArena.width>=375,'Snake retains nearly full device width on tall phones');
 assert.ok(tallArena.height>=510,'Snake gameplay must be visibly LONGER than before, not square');
 assert.ok(Math.abs(tallArena.ratio-25/18)<.02,'Snake portrait arena is 18 columns × 25 rows with proportional cells');
 assert.deepEqual(await page.evaluate(()=>[snake.size,snake.rows,snake.canvas.width,snake.canvas.height]),[18,25,360,500],'Snake uses a genuine rectangular internal grid');
 
 const arenaPlacement=await page.locator('.snake-shell').evaluate(el=>{const r=el.getBoundingClientRect();return {boardCenter:r.top+r.height/2,screenCenter:innerHeight/2}});
 assert.ok(Math.abs(arenaPlacement.boardCenter-arenaPlacement.screenCenter)<30,'Snake arena must be central, not pushed down the screen')
 assert.equal(await page.locator('#snakeCanvas').evaluate(el=>getComputedStyle(el).imageRendering),'auto','No pixelated performance-heavy interpolation')
 assert.equal(await page.evaluate(()=>snakeTickMs()),115,'Classic speed restored and slightly quicker')
 await page.locator('#snakeOverlay').waitFor({state:'visible'})
 await page.locator('#snakeOverlay').click()
 assert.equal(await page.locator('#snakeOverlay').isVisible(),false,'Snake overlay dismissed before any touch gesture')
 // Test a real finger swipe with the snake's game-loop paused for deterministic input inspection.
 await page.evaluate(()=>{if(snakeTimer)clearInterval(snakeTimer);snakeTimer=null;snake.dir='right';snake.next='right';snake.turnQueue=[];snake.started=true;gamePaused=false;});
 const bSnake=await page.locator('#snakeCanvas').boundingBox();
 const px=bSnake.x+bSnake.width*.55,py=bSnake.y+bSnake.height*.58;
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:px,y:py,id:8}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:px,y:py-34,id:8}]});
 await page.waitForFunction(()=>snake.turnQueue.includes('up'),null,{timeout:1500});
 assert.equal((await page.evaluate(()=>snake.turnQueue))[0],'up','Real Snake swipe is accepted before finger release');
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 await page.evaluate(()=>{snake.turnQueue=[];snake.dir='right';snake.next='right';snakeTimer=setInterval(stepSnake,snakeTickMs())});
 const touchQueue=await page.evaluate(()=>{
  const el=document.getElementById('snakeCanvas');
  snake.turnQueue=[];snake.dir='right';snake.next='right';snake.started=true;gamePaused=false;
  const send=(type,x,y)=>el.dispatchEvent(new PointerEvent(type,{bubbles:true,pointerId:21,pointerType:'touch',clientX:x,clientY:y}));
  send('pointerdown',170,170);send('pointermove',170,154);
  const beforeLift=[...snake.turnQueue];
  send('pointermove',151,154);
  const corners=[...snake.turnQueue];
  send('pointerup',151,154);
  return {beforeLift,corners}
 });
 assert.deepEqual(touchQueue.beforeLift,['up'],'Snake responds before finger lifts');
 assert.deepEqual(touchQueue.corners,['up','left'],'Fast swipe corners must queue without dropped inputs');

 await page.locator('#gameSettings').click()
 assert.equal(await page.locator('.snake-mode-preview').count(),5)
 await page.getByRole('button',{name:'Turbo'}).click()
 assert.equal(await page.evaluate(()=>snakeTickMs()),66)
 await page.locator('.settings-group').filter({hasText:'Snake speed'}).getByRole('button',{name:'Classic'}).click()
 assert.equal(await page.evaluate(()=>snakeTickMs()),115)
 await page.getByRole('button',{name:'Maze I',exact:true}).click()
 await page.locator('#closeSettings').click()
 assert.equal(await page.evaluate(()=>localStorage.getItem('night-play-setting-snake-mode')),'maze1')
 assert.ok(await page.evaluate(()=>snake.maze.length>0))
 assert.equal(await page.locator('#snakeOverlay').isVisible(),true)
 await page.locator('#snakeOverlay').click()
 await page.locator('[data-close-game]').first().click()
 await page.locator('.library-card[data-game="ttt"]').click()
 assert.match(await page.locator('#gameSettings svg path').getAttribute('d'),/^M9\.671 /,'Tic-Tac-Toe has the corrected settings icon');
 console.log('NIGHT_TTT_SETTINGS_JPEG_BASE64='+(await page.screenshot({type:'jpeg',quality:48})).toString('base64'));
 await page.locator('#gameSettings').click()
 await page.getByRole('button',{name:'Hard'}).click()
 await page.locator('#closeSettings').click()
 assert.equal(await page.locator('#tttboard button').count(),9)
 assert.ok(await page.locator('#tttboard').evaluate(el=>el.getBoundingClientRect().width)>=376,'TTT board should fill almost the entire viewport')
 const naturalBoardWidth=await page.locator('#tttboard').evaluate(el=>el.getBoundingClientRect().width);
 await page.locator('#gameFocus').click()
 assert.equal(await page.locator('#gameDialog').evaluate(el=>el.classList.contains('focus-active')),true)
 assert.equal(await page.locator('#gameFocus').getAttribute('aria-pressed'),'true')
 const focusBoardWidth=await page.locator('#tttboard').evaluate(el=>el.getBoundingClientRect().width);
 assert.ok(Math.abs(focusBoardWidth-naturalBoardWidth)<=1,'Focus must NOT enlarge the board');
 assert.ok((await page.locator('#gameTitle').boundingBox()).y<130,'Game title stays near top even in focus mode')
 await page.locator('#gameFocus').click()
 assert.equal(await page.locator('#gameFocus').getAttribute('aria-pressed'),'false')

 await page.locator('#tttboard button').first().click()
 await page.evaluate(()=>{window.__nightTttXMark=document.querySelector('#tttboard .ttt-cell .ttt-mark')});
 await page.waitForFunction(()=>Array.from(document.querySelectorAll('#tttboard button')).some(b=>b.textContent==='O'),null,{timeout:10000})
 assert.equal(await page.evaluate(()=>document.querySelector('#tttboard .ttt-cell .ttt-mark')===window.__nightTttXMark),true,'Existing X must retain its SVG after bot plays O');
 await page.evaluate(()=>drawTtt());
 assert.equal(await page.evaluate(()=>document.querySelector('#tttboard .ttt-cell .ttt-mark')===window.__nightTttXMark),true,'Unrelated Tic-Tac-Toe renders must never replay the X animation');
 // A completely filled draw should show a centered result card for ~3s,
 // then start the next local round on the very same game screen.
 const tiedBoard=['X','O','X','X','O','O','O','X',''];
 await page.evaluate(tie=>{
  mode='local';ttt=[...tie];turn='X';tttFinished=false;gamePaused=false;
  drawTtt();moveTtt(8);
 },tiedBoard);
 assert.equal(await page.locator('#tttboard .ttt-cell').count(),9);
 await page.locator('#tttboard .ttt-draw-overlay').waitFor({state:'visible',timeout:2000});
 assert.equal(await page.locator('.ttt-draw-card h3').textContent(),"It's a draw!",'A draw must display its animated result card');
 assert.equal(await page.locator('.ttt-draw-seconds').textContent(),'3s','Draw card holds a 3-second countdown');
 assert.equal(await page.evaluate(()=>ttt.every(Boolean)&&tttFinished),true,'Filled round stays on screen while result appears');
 await page.waitForFunction(()=>!tttFinished&&ttt.every(mark=>mark===''),null,{timeout:5000});
 await page.locator('#tttboard .ttt-draw-overlay').waitFor({state:'detached',timeout:2000});
 assert.equal(await page.evaluate(()=>mode),'local','Automatic next round keeps 2-player mode');
 assert.equal(await page.evaluate(()=>turn),'X','Automatic next round begins with X');
 assert.equal(new URL(page.url()).pathname,'/play/ttt','Auto-start must not navigate to another page');
 assert.deepEqual(await page.evaluate(()=>JSON.parse(localStorage.getItem('night-play-save-ttt')).board),Array(9).fill(''),'Next round is saved with blank cells');

 // Restart cancels a pending result timer; it must never erase a newly played X.
 await page.evaluate(tie=>{ttt=[...tie];turn='X';tttFinished=false;mode='local';drawTtt();moveTtt(8)},tiedBoard);
 await page.locator('#tttboard .ttt-draw-overlay').waitFor({state:'visible',timeout:2000});
 await page.locator('#restart').click();
 assert.equal(await page.locator('#tttboard .ttt-draw-overlay').count(),0,'Restart dismisses draw overlay');
 await page.evaluate(()=>{mode='local';drawTtt();moveTtt(0)});
 await page.waitForTimeout(3400);
 assert.equal(await page.evaluate(()=>ttt[0]),'X','Old result timeout cannot erase new-round moves');
 assert.equal(await page.locator('#tttboard .ttt-draw-overlay').count(),0);
 await page.locator('[data-close-game]').first().click()
 await page.locator('.mobile-nav a[href="/dashboard"]').click()
 assert.match((await page.locator('#homeGreeting').textContent())||'',/^Good (morning|afternoon|evening|night), Alex\.$/)
 await page.locator('.mobile-nav a[href="/profile"]').click()
 await page.locator('#view-profile').waitFor({state:'visible'})
 await page.locator('footer').scrollIntoViewIfNeeded()
 await page.evaluate(()=>window.scrollTo(0,document.documentElement.scrollHeight))
 const mobileFooter=await page.evaluate(()=>{
  const f=document.querySelector('footer').getBoundingClientRect(),nav=document.querySelector('.mobile-nav').getBoundingClientRect()
  return {footerBottom:f.bottom,barTop:nav.top};
 })
 assert.ok(mobileFooter.footerBottom<=mobileFooter.barTop+3,'full footer remains accessible above bottom navigation')
 assert.equal(await page.locator('footer a[href="/terms"]').isVisible(),true)
 assert.equal(await page.locator('footer a[href="/privacy"]').isVisible(),true)
 await page.locator('footer a[href="/privacy"]').click()
 await page.locator('#view-privacy').waitFor({state:'visible'})
 assert.equal(new URL(page.url()).pathname,'/privacy')
 assert.ok((await page.locator('#view-privacy h1').textContent()).includes('Privacy'))
 assert.equal(await page.locator('.mobile-nav').isVisible(),false)
 await page.locator('footer a[href="/terms"]').click()
 await page.locator('#view-terms').waitFor({state:'visible'})
 assert.equal(new URL(page.url()).pathname,'/terms')
 await page.locator('.legal-back').filter({visible:true}).click()
 await page.locator('#view-dashboard').waitFor({state:'visible'})
 const foot=await page.evaluate(()=>{
   const rect=document.querySelector('footer').getBoundingClientRect();
   return {absoluteBottom:rect.bottom+window.scrollY,documentHeight:document.documentElement.scrollHeight}
 })
 const bottomNavSpace=await page.locator('.mobile-nav').evaluate(el=>el.getBoundingClientRect().height)
  assert.ok(Math.abs(foot.absoluteBottom+bottomNavSpace-foot.documentHeight)<5,'Footer terminates before the dedicated navigation clearance')
 
  // Compact phone: no clipped tabs, active label or sideways scroll.
  await page.setViewportSize({width:320,height:700})
  await checkNav('Home')
  console.log('NIGHT_NAV_AFTER_320='+(await page.screenshot({type:'jpeg',quality:67})).toString('base64'))
  // Tablet: top links must remain navigable after the phone dock disappears.
  await page.setViewportSize({width:768,height:1024})
  assert.equal(await page.locator('.mobile-nav').isVisible(),false,'no bottom dock on tablets')
  assert.equal(await page.locator('header .links').isVisible(),true,'tablets expose the full top navigation')
  assert.deepEqual(await page.locator('header .links a').allTextContents(),['Home','Games','Profile'])
  await page.locator('header .links a[href="/profile"]').click()
  assert.equal(new URL(page.url()).pathname,'/profile')
  assert.equal(await page.locator('header .links a[aria-current="page"]').textContent(),'Profile')
  assert.equal(await page.locator('header .profile-icon').count(),0,'no repeated desktop profile shortcut')
  console.log('NIGHT_NAV_AFTER_TABLET='+(await page.screenshot({type:'jpeg',quality:67})).toString('base64'))
  assert.deepEqual(failures,[],'Browser runtime errors')
 console.log('PASS NIGHT Play V10 mobile: authentic NIGHT logo, full-width game boards, restored Snake controls and user-configurable speed')
} finally {if(browser)await browser.close();await new Promise(resolve=>server.close(resolve))}
