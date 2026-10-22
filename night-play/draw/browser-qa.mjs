// Isolated source integration with two independent browsers and the real
// authoritative SQLite room module. QA identities are fixtures, NOT live WA.
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {createServer} from 'node:http';
import {randomBytes} from 'node:crypto';
import {readFileSync,mkdirSync} from 'node:fs';
import {resolve,extname} from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';
const {NightPlayDraw}=await import(pathToFileURL(resolve(process.env.NIGHT_DRAW_BACKEND_ROOT,'mscc/night-play-draw.js')));
const db=new DatabaseSync(':memory:');db.exec('PRAGMA foreign_keys=ON;CREATE TABLE play_users(id TEXT PRIMARY KEY,display_name TEXT NOT NULL)');db.prepare('INSERT INTO play_users VALUES (?,?)').run('host','QA Host');
const game=new NightPlayDraw(db),tokens={host:randomBytes(32).toString('base64url'),guest:randomBytes(32).toString('base64url')};
const root=fileURLToPath(new URL('../',import.meta.url)),out=root+'visual-output/review-fixes/';mkdirSync(out,{recursive:true});
const server=createServer(async(req,res)=>{const path=new URL(req.url,'http://localhost').pathname;if(path==='/api/account'){const actor=/qa_actor=host/.test(req.headers.cookie||'')?'host':'guest';res.setHeader('content-type','application/json');if(req.method==='GET'){res.end(JSON.stringify({linked:actor==='host',profile:actor==='host'?{displayName:'QA Host'}:null,features:{draw:true}}));return}let raw='';for await(const chunk of req)raw+=chunk;try{const body=JSON.parse(raw);res.end(JSON.stringify(game.request(body.action,{...body,userId:actor==='host'?'host':null,guestSession:tokens[actor]})))}catch(e){res.statusCode=e.status||500;res.end(JSON.stringify({error:e.message}))}return}
 let route=path.endsWith('/')?path+'index.html':path;const file=resolve(root,'.'+route);if(!file.startsWith(root)){res.writeHead(403);res.end();return}try{res.writeHead(200,{'content-type':({'.html':'text/html','.js':'text/javascript','.css':'text/css'})[extname(file)]||'application/octet-stream'});res.end(readFileSync(file))}catch{res.writeHead(404);res.end()}});
await new Promise(done=>server.listen(0,'127.0.0.1',done));const base='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
try{
 const hostContext=await browser.newContext({viewport:{width:390,height:844},hasTouch:true,isMobile:true}),guestContext=await browser.newContext({viewport:{width:360,height:740},hasTouch:true,isMobile:true});
 await hostContext.addCookies([{name:'qa_actor',value:'host',url:base}]);await guestContext.addCookies([{name:'qa_actor',value:'guest',url:base}]);
 const host=await hostContext.newPage(),guest=await guestContext.newPage(),errors=[];for(const p of [host,guest])p.on('pageerror',e=>errors.push(e.message));
 await host.goto(base+'/draw/');await host.getByRole('button',{name:'Create room',exact:true}).tap();const invitation=await host.locator('.invite').textContent();
 await guest.goto(invitation);await guest.getByRole('textbox',{name:'Your name',exact:true}).fill('QA Friend');await guest.getByRole('button',{name:'Join game',exact:true}).tap();
 await host.getByRole('button',{name:'Start game',exact:true}).waitFor();await host.waitForFunction(()=>!document.querySelector('[data-action=start]').disabled);await host.getByRole('button',{name:'Start game',exact:true}).tap();
 await guest.locator('#guessForm').waitFor({state:'visible'});const word=await host.locator('#word').textContent();assert.ok(word.length>2);assert.notEqual(await guest.locator('#word').textContent(),word,'guesser cannot see artist word');
 const box=await host.locator('#canvas').boundingBox();await host.mouse.move(box.x+box.width*.1,box.y+box.height*.1);await host.mouse.down();await host.mouse.move(box.x+box.width*.8,box.y+box.height*.8,{steps:30});await host.mouse.up();
 await guest.waitForFunction(()=>document.querySelector('#canvas').getContext('2d').getImageData(400,300,1,1).data[0]<150,null,{timeout:10000});await guest.screenshot({path:out+'draw-remote-stroke-360.png',fullPage:true});
 await guest.reload();await guest.locator('#guessForm').waitFor({state:'visible'});assert.notEqual(await guest.locator('#word').textContent(),word,'reconnect keeps private word hidden');
 await guest.locator('#guess').fill(word);await guest.getByRole('button',{name:'Guess',exact:true}).tap();await host.locator('#next').waitFor({state:'visible'});await host.locator('#next').tap();
 await guest.locator('#tools').waitFor({state:'visible'});await host.locator('#guessForm').waitFor({state:'visible'});assert.equal(await host.locator('#tools').isVisible(),false,'artist role swaps on different devices');
 const bad=await guest.request.post(base+'/api/account',{data:{action:'draw:advance',code:new URL(invitation).searchParams.get('code')}});assert.equal(bad.status(),403,'guest cannot advance the room');
 await host.screenshot({path:out+'draw-artist-swapped-390.png',fullPage:true});assert.deepEqual(errors,[]);
 console.log('PASS source-only real backend: two independent browsers, join, private artist word, remote strokes, reconnect, actual correct guess, scoring, artist rotation and host permissions');
}finally{await browser.close();await new Promise(done=>server.close(done));db.close()}
