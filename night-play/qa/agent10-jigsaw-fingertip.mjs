import {chromium} from 'playwright';import{createServer}from'node:http';import{readFileSync}from'node:fs';import path from'node:path';import{fileURLToPath}from'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const types={'.html':'text/html','.js':'text/javascript','.mjs':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.jpg':'image/jpeg','.png':'image/png','.webp':'image/webp','.json':'application/json'};
const server=createServer((req,res)=>{let p=new URL(req.url,'http://localhost').pathname.replace(/^\//,'');if(p==='jigsaw'||p==='jigsaw/')p='jigsaw/index.html';try{const b=readFileSync(path.join(root,p));res.writeHead(200,{'content-type':types[path.extname(p)]||'application/octet-stream'});res.end(b)}catch{res.writeHead(404);res.end('not found')}});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));const B='http://127.0.0.1:'+server.address().port;
const b=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
try{for(const width of [320,390]){const page=await b.newPage({viewport:{width,height:width===320?700:844},isMobile:true,hasTouch:true});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(B+'/jigsaw/');await page.locator('[data-theme="photo-alpine-lake"]').click();await page.waitForFunction(()=>window.__nightJigsawTest?.snapshot().activeRound,{timeout:10000});
await page.locator('#hudMenuButton').click();await page.locator('#hudSettingsButton').click();await page.locator('[data-pieces="144"]').click();await page.waitForTimeout(170);
const start=await page.evaluate(()=>{const s=window.__nightJigsawTest.snapshot(),cv=document.querySelector('#puzzleCanvas'),r=cv.getBoundingClientRect(),sx=r.width/cv.width,sy=r.height/cv.height;
return {pieces:s.pieces.filter(p=>!p.placed).map(p=>({id:p.id,cx:p.correctX,cy:p.correctY,x:r.left+(p.x+s.board.pieceWidth/2)*sx,y:r.top+(p.y+s.board.pieceHeight/2)*sy})).filter(p=>p.x>7&&p.x<innerWidth-7&&p.y>52&&p.y<innerHeight-55),board:s.board,rect:r.toJSON()};});
const cdp=await page.context().newCDPSession(page);let selected=0,match=0,miss=0,selectionLog=[];
for(let i=0;i<Math.min(26,start.pieces.length);i++){const p=start.pieces[Math.floor((i+0.5)*start.pieces.length/Math.min(26,start.pieces.length))];
await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:p.x,y:p.y,id:100+i}]});await page.waitForTimeout(38);
let s=await page.evaluate(()=>window.__nightJigsawTest.snapshot().selected);await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
const isMatch=!!s&&Math.abs(s.correctX-p.cx)<0.1&&Math.abs(s.correctY-p.cy)<0.1;selected+=!!s;match+=isMatch;miss+=!isMatch;selectionLog.push({target:p.id,got:s?.id,exact:isMatch,x:Math.round(p.x),y:Math.round(p.y)});
}
console.log('FINGER_PRECISION',JSON.stringify({width,total:start.pieces.length,tries:selectionLog.length,selected,correctPiece:match,wrongOrNone:miss,sample:selectionLog.slice(0,15),errors}));await page.close();}
}finally{await b.close();await new Promise(ok=>server.close(ok));}
