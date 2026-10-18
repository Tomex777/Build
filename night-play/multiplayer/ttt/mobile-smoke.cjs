const assert=require('node:assert/strict')
const http=require('node:http'),fs=require('node:fs'),path=require('node:path')
const {chromium}=require('playwright')
const games=new Map()
let counter=0
function room(user,match){
 const side=match.x===user?'X':'O'
 return {matchId:match.id,code:match.code,side,mode:match.mode,
  opponent:side==='X'?(match.o||null):match.x,board:match.board,turn:match.turn,
  status:match.status,winner:match.winner,version:match.version}
}
function handle(action,user,args){
 const mine=[...games.values()].filter(g=>g.x===user||g.o===user)
 if(action==='status')return {linked:true,profile:{id:user,displayName:user}}
 if(action==='match:list')return {matches:mine.map(m=>room(user,m))}
 let match
 if(action==='match:create'||action==='match:random'){
  if(action==='match:random')match=[...games.values()].find(m=>m.mode==='public'&&m.status==='waiting'&&m.x!==user)
  if(!match){
   const n=++counter
   match={id:'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),code:'ABCDEF'+String(n).padStart(3,'0'),x:user,o:null,mode:action==='match:random'?'public':'invite',board:'.........',status:'waiting',turn:'X',winner:null,version:1}
   games.set(match.id,match)
  }else{match.o=user;match.status='active';match.version++}
  return room(user,match)
 }
 if(action==='match:join'){
  match=[...games.values()].find(g=>g.code===String(args.code||'').replace(/\s/g,''))
  if(!match)return {error:'Match not found.',status:404}
  if(!match.o){match.o=user;match.status='active';match.version++}
  return room(user,match)
 }
 match=games.get(args.matchId)
 if(!match||![match.x,match.o].includes(user))return {error:'Match not found.',status:404}
 if(action==='match:state')return room(user,match)
 if(action==='match:leave'){match.status='finished';match.version++;return room(user,match)}
 if(action==='match:move'){
  const side=match.x===user?'X':'O'
  if(match.turn!==side||match.board[args.cell]!=='.')return {error:'Not your turn.',status:409}
  match.board=match.board.slice(0,args.cell)+side+match.board.slice(args.cell+1)
  match.turn=side==='X'?'O':'X';match.version++
  return room(user,match)
 }
 return {error:'Unknown action',status:400}
}
const dir=__dirname
const server=http.createServer(async(req,res)=>{
 const u=new URL(req.url,'http://127.0.0.1')
 if(u.pathname==='/api/account'){
  let body={}
  if(req.method==='POST'){let raw='';for await(const chunk of req)raw+=chunk;body=JSON.parse(raw)}
  const user=(req.headers.cookie||'').includes('night-user=bob')?'Bob':(req.headers.cookie||'').includes('night-user=dan')?'Dan':(req.headers.cookie||'').includes('night-user=cara')?'Cara':'Alice'
  const data=handle(body.action||'status',user,body);res.statusCode=typeof data.status==='number'?data.status:200
  res.setHeader('content-type','application/json');res.end(JSON.stringify(data));return
 }
 const file=path.join(dir,u.pathname==='/multiplayer/ttt/'?'index.html':u.pathname==='/multiplayer/ttt/app.js'?'app.js':'invalid')
 if(!file.startsWith(dir+path.sep)||!fs.existsSync(file)){res.statusCode=404;res.end();return}
 res.setHeader('content-type',file.endsWith('.js')?'text/javascript':'text/html');res.end(fs.readFileSync(file))
})
;(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
 const origin='http://127.0.0.1:'+server.address().port
 const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']})
 const getPage=async(user)=>{
  const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true})
  await context.addCookies([{name:'night-user',value:user.toLowerCase(),url:origin}])
  const page=await context.newPage();await page.goto(origin+'/multiplayer/ttt/')
  await page.locator('#inviteBtn').waitFor();return [context,page]
 }
 try {
  const [a,alice]=await getPage('Alice')
  const [b,bob]=await getPage('Bob')
  await alice.locator('#inviteBtn').click()
  await alice.locator('#invitePanel').waitFor({state:'visible'})
  assert.equal(await alice.locator('#lobby').isVisible(),true,'invite stays on the matchmaking page')
  assert.equal(await alice.locator('#waitingPanel').isVisible(),true)
  assert.equal(await alice.locator('#room').isVisible(),false,'do not show the board before a friend joins')
  assert.equal(await alice.locator('#board .cell').count(),0,'no playable squares appear during invitation')
  fs.mkdirSync(path.join(dir,'../../visual-output'),{recursive:true})
  await alice.screenshot({path:path.join(dir,'../../visual-output/online-ttt-waiting-mobile.jpg')})
  await alice.locator('#waitingBack').click()
  await alice.locator('.match-row').first().click()
  assert.equal(await alice.locator('#waitingPanel').isVisible(),true,'saved invitation resumes to waiting, not the board')
  assert.equal(await alice.locator('#room').isVisible(),false)
  const code=(await alice.locator('#code').textContent()).replace(/\s/g,'')
  assert.match(code,/^ABCDEF\d{3}$/)
  await bob.locator('#inviteInput').fill(code)
  await bob.getByRole('button',{name:'Join',exact:true}).click()
  await bob.getByText('Opponent',{exact:true}).waitFor()
  assert.equal(await bob.locator('#room').isVisible(),true,'joining player enters the real board directly')
  await alice.waitForFunction(()=>document.getElementById('waitingTitle').textContent==='Friend joined!',{timeout:11000})
  assert.equal(await alice.locator('#room').isVisible(),false,'host sees friend-found confirmation first')
  await alice.waitForFunction(()=>document.getElementById('opponent').textContent==='Bob'&&!document.getElementById('room').hidden,{timeout:11000})
  await alice.getByRole('button',{name:'Square 1, empty'}).click()
  await bob.waitForFunction(()=>document.querySelector('.cell').textContent==='X',{timeout:11000})
  await bob.getByRole('button',{name:'Square 4, empty'}).click()
  await alice.waitForFunction(()=>document.querySelectorAll('.cell')[3].textContent==='O',{timeout:11000})
  await alice.getByRole('button',{name:'Lobby'}).click()
  await alice.locator('.match-row').first().click()
  assert.equal(await alice.locator('.cell').first().textContent(),'X')
  fs.mkdirSync(path.join(dir,'../../visual-output'),{recursive:true})
  await alice.screenshot({path:path.join(dir,'../../visual-output/online-ttt-mobile.jpg')})
  const [c,cara]=await getPage('Cara')
  const [d,dan]=await getPage('Dan')
  await cara.locator('#randomBtn').click()
  await cara.getByText('Finding an opponent',{exact:true}).waitFor()
  assert.equal(await cara.locator('#room').isVisible(),false,'random matching stays in lobby')
  await dan.locator('#randomBtn').click()
  await dan.getByText('Opponent',{exact:true}).waitFor()
  await cara.waitForFunction(()=>document.getElementById('opponent').textContent==='Dan'&&!document.getElementById('room').hidden,{timeout:11000})
  for(const ctx of [a,b,c,d])await ctx.close()
  console.log('PASS NIGHT Play online TTT mobile: wait in lobby until friend found, join confirmation, live remote moves, resume, random pairing, and mobile screenshots')
 }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
})().catch(error=>{console.error(error);server.closeAllConnections();server.close();process.exitCode=1})
