const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),zlib=require('node:zlib');
const {chromium}=require('playwright');
const root=path.join(__dirname,'../..');
const output=path.join(root,'visual-output');
fs.mkdirSync(output,{recursive:true});
const chan='120363282083849178@newsletter',other='120363282083849179@newsletter';
let linked=false,connected=false,down=false,configUnavailable=false,rawNameVerified=true,batchNumber=0,publishAttempts=0;
let settings={revision:1,modules:{memes:false,news:false,anonymous:false,ai:false},
 memes:{mode:'review',categories:[],dailyLimit:1},news:{mode:'review',interests:[],spoilers:'avoid'},branding:{captionReplacementsEnabled:false,captionReplacements:[],signatureEnabled:false,posterName:'',watermarkEnabled:false,watermarkOpacity:0.22,watermarkPosition:'auto'},capabilities:{ai:false}};
let decisions={},jobs=[];
function crc(bytes){let n=0xffffffff;for(const b of bytes){n^=b;for(let i=0;i<8;i++)n=n&1?0xedb88320^(n>>>1):n>>>1;}return(n^0xffffffff)>>>0}
function png(seed){
 const w=320,h=180,raw=Buffer.alloc((w*3+1)*h);
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){
   const i=y*(w*3+1)+1+x*3;
   raw[i]=(x+seed*37)%256;raw[i+1]=(y*2+seed*53)%256;raw[i+2]=(seed*55+((x^y)&63))%256;
 }
 const chunk=(type,payload)=>{
   const n=Buffer.alloc(4),tag=Buffer.from(type),c=Buffer.alloc(4);
   n.writeUInt32BE(payload.length);c.writeUInt32BE(crc(Buffer.concat([tag,payload])));
   return Buffer.concat([n,tag,payload,c]);
 };
 const hdr=Buffer.alloc(13);hdr.writeUInt32BE(w,0);hdr.writeUInt32BE(h,4);hdr[8]=8;hdr[9]=2;
 return 'data:image/png;base64,'+Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',hdr),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]).toString('base64');
}
const fixtures=[png(1),png(2),png(3),png(4)];
let current=null,selection={id:'selection_000001',revision:0,items:[]};
function send(res,status,body){
 res.statusCode=status;res.setHeader('Cache-Control','no-store');
 res.setHeader('Content-Type','application/json');res.end(JSON.stringify(body));
}
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost');const route=url.pathname;
 if(route==='/api/account'){
   if(down)return send(res,503,{error:'Backend unavailable'});
   if(req.method==='GET')return send(res,200,{linked,profile:linked?{id:'QA-author',displayName:'QA Owner'}:null});
   let raw='';for await(const part of req)raw+=part;
   let q;try{q=JSON.parse(raw)}catch{return send(res,400,{error:'Invalid JSON'})}
   const requested=q.channelId,action=q.action;
   if(action==='channel:list')return send(res,200,{channels:linked?[{id:chan,name:'K-Drama 🌙 Zone',verifiedDisplayName:'K-Drama 🌙 Zone',verifiedNameSource:rawNameVerified?'whatsapp-raw':'unverified',status:connected?'connected':'awaiting_admin',anonymousStartEnabled:false,proof:'NIGHT-TEST-1234'}]:[]});
   if(!linked||!connected||requested!==chan)return send(res,403,{error:'Channel ownership not verified'});
   if(configUnavailable&&action==='channel:config:get')return send(res,503,{error:'Configuration service unavailable'});
   if(action==='channel:config:get')return send(res,200,settings);
   if(action==='channel:config:update'){
     if(q.expectedRevision!==settings.revision)return send(res,409,{error:'Config was changed elsewhere'});
     const patch=q.patch||{};
     if(patch.modules){
       if(Object.entries(patch.modules).some(([k,v])=>!['memes','news','anonymous','ai'].includes(k)||typeof v!=='boolean'))return send(res,400,{error:'Invalid module'});
       if(patch.modules.ai===true)return send(res,403,{error:'AI responses not permitted'});
       Object.assign(settings.modules,patch.modules);
     }
     if(patch.memes){if(!Array.isArray(patch.memes.categories)||!patch.memes.categories.length)return send(res,400,{error:'Pick categories'});settings.memes=patch.memes}
     if(patch.news){if(!Array.isArray(patch.news.interests)||!patch.news.interests.length)return send(res,400,{error:'Pick interests'});settings.news=patch.news}
     if(patch.branding){
       if(typeof patch.branding.signatureEnabled!=='boolean'||typeof patch.branding.watermarkEnabled!=='boolean'||(patch.branding.signatureEnabled&&!patch.branding.posterName))return send(res,400,{error:'Poster name required'});
       settings.branding=patch.branding;
     }
     settings={...settings,revision:settings.revision+1};return send(res,200,settings);
   }
   if(action==='channel:memes:current')return send(res,200,{...(current||{}),selection});
   if(action==='channel:memes:find'){
     const base=++batchNumber;
     decisions={};
     current={batchId:'batch_0_'+String(base).padStart(8,'0'),candidates:[1,2,3,4].map((n,i)=>({
       id:'meme_'+base+'_'+n,title:'Test candidate '+n,category:n%2?'anime':'relatable',
       sourceName:'QA licensed fixtures',sourceUrl:'https://example.org/qa-fixture-'+n,rightsStatus:n===4?'unknown':'verified',publishable:n!==4,reviewOnly:n===4,
       previewData:fixtures[i],caption:'Caption for '+n
     })),decisions:[]};
     return send(res,200,{...current,selection});
   }
   if(action==='channel:post:preview'){
     const candidate=(current?.batchId===q.batchId?current.candidates.find(c=>c.id===q.candidateId):null)
       ||selection.items.find(c=>c.batchId===q.batchId&&c.candidateId===q.candidateId);
     if(!candidate)return send(res,404,{error:'Candidate not found'});
     const name=settings.branding?.signatureEnabled?settings.branding.posterName:'';
     return send(res,200,{previewData:candidate.previewData,formattedCaption:q.caption+(name?'\n> '+name:'')});
   }
   if(action==='channel:memes:review'){
     if(settings.modules.memes!==true)return send(res,403,{error:'Enable memes before reviewing for publication'});
     if(q.expectedSelectionRevision!==selection.revision)return send(res,409,{error:'Selection changed elsewhere'});
     const candidate=(current?.batchId===q.batchId?current.candidates.find(c=>c.id===q.candidateId):null)
       ||selection.items.find(c=>c.batchId===q.batchId&&c.candidateId===q.candidateId);
     if(!candidate)return send(res,404,{error:'Candidate not found'});
     if(!['approved','declined','skipped'].includes(q.decision))return send(res,400,{error:'Invalid decision'});
     if(q.decision==='approved'&&(candidate.publishable!==true||candidate.rightsStatus!=='verified'))return send(res,403,{error:'Rights not verified'});
     const others=selection.items.filter(item=>!(item.batchId===q.batchId&&item.candidateId===q.candidateId));
     if(q.decision==='approved')others.push({batchId:q.batchId,candidateId:q.candidateId,title:candidate.title,caption:q.caption,
       rightsStatus:'verified',publishable:true,reviewOnly:false,previewData:candidate.previewData});
     selection={...selection,revision:selection.revision+1,items:others};
     const decision={candidateId:q.candidateId,decision:q.decision,caption:q.caption};
     if(current?.batchId===q.batchId){
       decisions[q.candidateId]=decision;current.decisions=Object.values(decisions);
     }
     return send(res,200,{...decision,selection});
   }
   if(action==='channel:memes:publish'){
     publishAttempts++;
     if(!settings.modules.memes)return send(res,403,{error:'Memes disabled'});
     if(selection.id!==q.selectionId||q.expectedSelectionRevision!==selection.revision)return send(res,409,{error:'Stale selection'});
     const ok=selection.items;
     if(!ok.length)return send(res,403,{error:'No approvals'});
     if(q.mode==='schedule'&&!q.scheduledAt)return send(res,400,{error:'Missing schedule'});
     const written=ok.map((item,i)=>({id:'queued_'+batchNumber+'_'+i,title:item.caption,status:q.mode==='schedule'?'scheduled':'queued',scheduledAt:q.scheduledAt||null}));
     jobs.push(...written);selection={...selection,revision:selection.revision+1,items:[]};current=null;
     return send(res,200,{jobs:written,selection});
   }
   if(action==='channel:jobs:list')return send(res,200,{jobs});
   if(action==='channel:jobs:cancel'){
     const item=jobs.find(j=>j.id===q.jobId);if(!item||!['queued','scheduled'].includes(item.status))return send(res,403,{error:'Cannot cancel'});
     item.status='cancelled';return send(res,200,{cancelled:true});
   }
   return send(res,400,{error:'Unknown action'});
 }
 let file=null;
 if(route==='/channels'||route==='/channels/')file=path.join(root,'channels/index.html');
 else if(route==='/channels/config/'||route==='/channels/config/index.html')file=path.join(__dirname,'index.html');
 else if(route==='/channels/config/app.js')file=path.join(__dirname,'app.js');
 else if(route==='/channels/config/style.css')file=path.join(__dirname,'style.css');
 else if(route==='/site-shell.css')file=path.join(root,'site-shell.css');
 if(!file)return send(res,404,{error:'Not found'});
 res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html');
 res.end(fs.readFileSync(file));
});
async function main(){
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const base='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-sandbox']});
 const errors=[];
 try{
   const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
   page.on('pageerror',error=>errors.push(String(error)));
   await page.goto(base+'/channels/');
   await page.getByRole('heading',{name:'Connect WhatsApp'}).waitFor();
   assert.equal(await page.getByRole('link',{name:/Configure/}).count(),0,'No configure link for unlinked account');
   linked=true;
   await page.reload();await page.getByRole('heading',{name:'K-Drama 🌙 Zone'}).waitFor();
   assert.equal(await page.getByRole('link',{name:/Configure/}).count(),0,'Unverified channel does not expose configuration');
   connected=true;
   await page.reload();await page.getByRole('link',{name:'Configure K-Drama 🌙 Zone'}).waitFor();
   await page.getByRole('link',{name:'Configure K-Drama 🌙 Zone'}).click();
   await page.getByRole('heading',{name:'Configure channel'}).waitFor();
   await page.locator('#configUI').waitFor({state:'visible'});
   assert.match(await page.locator('#channelTitle').textContent(),/K-Drama/);
   assert.equal(await page.locator('[data-module="memes"]').isChecked(),false,'Memes must default OFF');
   assert.equal(await page.locator('[data-module="news"]').isChecked(),false,'News must default OFF');
   assert.equal(await page.locator('[data-module="anonymous"]').isChecked(),false,'Anonymous must default OFF');
   assert.equal(await page.locator('[data-module="ai"]').isDisabled(),true,'AI guard remains OFF and unavailable without permission');
   await page.locator('[data-module="memes"]').click({force:true});
   await page.getByText('Choose at least one meme category').waitFor();
   assert.equal(settings.modules.memes,false,'Cannot enable memes without configured categories');
   await page.locator('#memeCategories input[value="relatable"]').check({force:true});
   await page.locator('[data-module="memes"]').check({force:true});
   await page.waitForFunction(()=>document.querySelector('[data-module="memes"]')?.checked===true && document.querySelector('#saveMemes')?.disabled===false);
   assert.equal(settings.modules.memes,true,'Module change written to signed backend');
   await page.locator('#memeMode').selectOption('automatic');
   await page.locator('#memeDaily').selectOption('2');
   await page.getByRole('button',{name:'Save meme settings'}).click();
   await page.waitForFunction(()=>document.querySelector('#saveMemes')?.disabled===false && document.querySelector('#memeMode')?.value==='automatic');
   assert.equal(settings.memes.mode,'automatic');
   assert.deepEqual(settings.memes.categories,['relatable']);
   assert.equal(await page.locator('#watermarkName').textContent(),'K-Drama 🌙 Zone','Watermark source is exact verified Unicode/emoji display name');
   await page.locator('#captionReplacementsEnabled').check({force:true});
   await page.getByRole('button',{name:'Save branding'}).click();
   await page.getByText('Add a caption replacement before enabling').waitFor();
   await page.getByRole('button',{name:'Add replacement'}).click();
   await page.getByRole('textbox',{name:'Original word or phrase'}).fill('what the fuck');
   await page.getByRole('textbox',{name:'Replacement text'}).fill('WTF');
   await page.locator('#signatureEnabled').check({force:true});
   await page.locator('#posterName').fill('QA Owner');
   await page.locator('#watermarkEnabled').check({force:true});
   await page.getByRole('button',{name:'Save branding'}).click();
   await page.waitForFunction(()=>document.querySelector('#saveBranding')?.disabled===false);
   assert.equal(settings.branding.posterName,'QA Owner');
   assert.equal(settings.branding.captionReplacementsEnabled,true,'Caption word replacement is explicit opt-in');
   assert.deepEqual(settings.branding.captionReplacements,[{from:'what the fuck',to:'WTF'}],'Replacement mapping saves server-side');
   assert.equal(settings.branding.watermarkPosition,'auto','Quiet-corner watermark placement supported');
   assert.equal(settings.branding.watermarkEnabled,true);
   await page.getByRole('button',{name:'Find memes'}).click();
   await page.locator('.review-card').first().waitFor();
   assert.equal(await page.locator('.review-card').count(),4,'Real batch has four sourced images');
   assert.equal(await page.locator('.review-art img').count(),4);
   assert.equal(await page.locator('.review-card').nth(3).getByRole('button',{name:'Approve'}).isDisabled(),true,'No approval for unknown media reuse rights');
   const firstCards=page.locator('.review-card');
   await firstCards.nth(0).locator('textarea').fill('Caption edited by owner');
   await firstCards.nth(0).getByRole('button',{name:'Preview post'}).click();
   await page.locator('#postPreview[open]').waitFor();
   await page.getByText('Caption as it will be sent').waitFor();
   assert.match(await page.locator('#postPreviewContent').textContent(),/> QA Owner/,'Human caption attribution is separate from channel watermark');
   await page.screenshot({path:path.join(output,'channels-A-preview-dialog.jpg'),fullPage:false});
   await page.getByRole('button',{name:'Close preview'}).click();
   await firstCards.nth(0).getByRole('button',{name:'Approve'}).click();
   await firstCards.nth(0).getByText('Approved', {exact:true}).waitFor();
   await firstCards.nth(1).getByRole('button',{name:'Decline'}).click();
   await firstCards.nth(1).getByText('Declined', {exact:true}).waitFor();
   assert.match(await page.locator('#approvedCount').textContent(),/^1 approved/);
   assert.equal(Object.values(decisions).filter(x=>x.decision==='approved').length,1);
   await page.reload();
   await page.locator('#configUI').waitFor({state:'visible'});
   assert.match(await page.locator('#approvedCount').textContent(),/^1 approved/,'Server restores first approval on reload');
   assert.equal(await page.locator('#captionReplacementsEnabled').isChecked(),true,'Caption replacements survive settings reload');
   assert.equal(await page.getByRole('textbox',{name:'Replacement text'}).inputValue(),'WTF');
   await page.getByRole('button',{name:'Next four'}).click();
   await page.locator('.review-card').first().getByText('Test candidate 1').waitFor();
   assert.match(await page.locator('#approvedCount').textContent(),/^1 approved/,'Selection survives a new batch');
   assert.equal(await page.locator('.selected-item').count(),1,'Prior image stays in approved tray');
   await page.getByRole('button',{name:'Edit Test candidate 1 caption'}).click();
   await page.getByRole('textbox',{name:'Edit approved caption for Test candidate 1'}).fill('Refined caption after Next four');
   assert.equal(await page.getByRole('button',{name:'Publish approved'}).isDisabled(),true,'Cannot publish while an approved caption is being edited');
   await page.getByRole('button',{name:'Save caption'}).click();
   await page.getByText('Approved caption saved.').waitFor();
   assert.equal(selection.items[0].caption,'Refined caption after Next four','Older approval caption persisted in server snapshot');
   await page.getByRole('button',{name:'Preview Test candidate 1 post'}).click();
   await page.getByText('Caption as it will be sent').waitFor();
   assert.match(await page.locator('#postPreviewContent').textContent(),/Refined caption after Next four/,'Old approval uses actual saved preview');
   await page.getByRole('button',{name:'Close preview'}).click();
   await page.locator('.review-card').nth(1).getByRole('button',{name:'Skip'}).click();
   await page.locator('.review-card').nth(1).getByText('Skipped',{exact:true}).waitFor();
   await page.locator('.review-card').nth(3).getByRole('button',{name:'Decline'}).click();
   await page.locator('.review-card').nth(3).getByText('Declined',{exact:true}).waitFor();
   assert.equal(await page.locator('.review-card').nth(3).getByRole('button',{name:'Approve'}).isDisabled(),true,'Rights-unknown approval stays disabled after review action');
   await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).click();
   await page.getByText('2 approved',{exact:true}).waitFor();
   await page.reload();
   await page.locator('#configUI').waitFor({state:'visible'});
   assert.equal(await page.locator('.selected-item').count(),2,'Two server-confirmed approvals survive reload and Next four');
   await page.getByRole('button',{name:'Remove Test candidate 1 from approved selection'}).last().click();
   await page.getByText('1 approved',{exact:true}).waitFor();
   assert.equal(selection.items[0].caption,'Refined caption after Next four','Remove updates backend selection, retains older approved batch');
   const cards=page.locator('.review-card');
   for(const width of [320,360,390,412,1280]){
     await page.setViewportSize({width,height:width===1280?900:780});
     assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),true,'No horizontal overflow at '+width);
     const img=await cards.nth(0).locator('.review-art img').boundingBox();
     assert.ok(img&&img.width>200&&img.height>100,'Image is visible at '+width);
     await page.screenshot({path:path.join(output,'channels-A-review-'+width+'.jpg'),fullPage:true});
   }
   await page.getByRole('button',{name:'Publish approved'}).click();
   await page.getByText('1 approved posts queued.').waitFor();
   assert.equal(jobs.length,1,'Only one approved post queued');
   assert.equal(jobs[0].title,'Refined caption after Next four','Cross-batch caption edit reaches the queue');
   assert.equal(jobs.some(x=>x.title.includes('Caption for 2')),false,'Declined post is NEVER queued');
   await page.getByRole('button',{name:'Cancel',exact:true}).click();
   await page.getByText('cancelled',{exact:false}).first().waitFor();
   assert.equal(jobs[0].status,'cancelled');
   await page.getByRole('button',{name:'Find memes'}).click();
   await page.locator('.review-card').first().waitFor();
   await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).click();
   await page.getByRole('button',{name:'Schedule approved'}).click();
   const scheduled=new Date(Date.now()+1000*60*60*24);
   const local=[scheduled.getFullYear(),String(scheduled.getMonth()+1).padStart(2,'0'),String(scheduled.getDate()).padStart(2,'0')].join('-')+'T'+String(scheduled.getHours()).padStart(2,'0')+':'+String(scheduled.getMinutes()).padStart(2,'0');
   await page.locator('#scheduleAt').fill(local);
   await page.getByRole('button',{name:'Confirm schedule'}).click();
   await page.getByText('1 posts scheduled.').waitFor();
   assert.equal(jobs[1].status,'scheduled','Scheduled approved item persisted');
   await page.locator('[data-module="memes"]').uncheck({force:true});
   await page.waitForFunction(()=>document.querySelector('[data-module="memes"]')?.checked===false);
   assert.equal(settings.modules.memes,false,'Module OFF persists backend-side');
   assert.equal(await page.getByRole('button',{name:'Find memes'}).isDisabled(),true,'OFF blocks discovery');
   assert.equal(await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).isDisabled(),true,'OFF blocks approval');
   assert.equal(await page.locator('.review-card').first().getByRole('button',{name:'Skip'}).isDisabled(),true,'OFF blocks review changes');
   assert.equal(await page.getByRole('button',{name:'Publish approved'}).isDisabled(),true,'Cannot publish while OFF');
   await page.locator('[data-module="memes"]').check({force:true});
   await page.waitForFunction(()=>document.querySelector('[data-module="memes"]')?.checked===true);
   // Another authenticated tab changes the server-owned selection revision.
   selection={...selection,revision:selection.revision+1};
   await page.locator('.review-card').first().getByRole('button',{name:'Skip'}).click();
   await page.getByText('Selection changed elsewhere').waitFor();
   assert.equal(await page.getByRole('button',{name:'Next four'}).isDisabled(),true,'Stale state forbids further selections until verified refresh');
   assert.equal(await page.getByRole('button',{name:'Find memes'}).isDisabled(),true,'Cannot discard uncertain review state');
   await page.reload();await page.locator('#configUI').waitFor({state:'visible'});
   assert.equal(await page.getByRole('button',{name:'Find memes'}).isEnabled(),true,'Reload restores authoritative snapshot');
   // Server cap is 10, and the UI must not offer an eleventh approval.
   selection={...selection,revision:selection.revision+1,items:Array.from({length:10},(_,i)=>({
     batchId:'batch_cap_00001',candidateId:'cap_candidate_'+i,title:'Selected cap '+i,
     caption:'Owner approved '+i,rightsStatus:'verified',publishable:true,reviewOnly:false,previewData:fixtures[i%4]
   }))};
   await page.reload();await page.locator('#configUI').waitFor({state:'visible'});
   assert.match(await page.locator('#approvedCount').textContent(),/10 approved · selection full/);
   assert.equal(await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).isDisabled(),true,'Eleventh approval disabled');
   await page.getByRole('button',{name:'Remove Selected cap 0 from approved selection'}).click();
   await page.getByText('9 approved',{exact:true}).waitFor();
   assert.equal(await page.locator('.review-card').first().getByRole('button',{name:'Approve'}).isEnabled(),true,'Approve restored after removing a selected post');
   selection={...selection,revision:selection.revision+1,items:[]};
   await page.reload();await page.locator('#configUI').waitFor({state:'visible'});
   const before=publishAttempts;
   await page.goto(base+'/channels/config/?channel='+encodeURIComponent(other));
   await page.getByText('Access unavailable',{exact:true}).waitFor();
   assert.equal(await page.locator('#configUI').isVisible(),false,'Other channel private settings hidden');
   assert.equal(publishAttempts,before,'Other channel cannot publish');
   configUnavailable=true;
   await page.goto(base+'/channels/config/?channel='+encodeURIComponent(chan));
   await page.getByText('Configuration unavailable',{exact:true}).waitFor();
   assert.equal(await page.locator('#configUI').isVisible(),false,'Outage is not fake OFF defaults');
   await page.screenshot({path:path.join(output,'channels-A-unavailable.jpg'),fullPage:true});
   configUnavailable=false;rawNameVerified=false;
   await page.reload();await page.locator('#configUI').waitFor({state:'visible'});
   await page.getByText('Exact channel name not yet verified').waitFor();
   assert.equal(await page.locator('#watermarkEnabled').isChecked(),true,'Existing legacy watermark is shown honestly when identity proof is lost');
   await page.locator('#watermarkEnabled').uncheck({force:true});
   await page.getByRole('button',{name:'Save branding'}).click();
   await page.waitForFunction(()=>document.querySelector('#saveBranding')?.disabled===false);
   assert.equal(settings.branding.watermarkEnabled,false,'Owner can disable legacy watermark even without raw-name proof');
   assert.equal(await page.locator('#watermarkEnabled').isDisabled(),true,'Cannot enable watermark with unverified raw identity');
   await page.screenshot({path:path.join(output,'channels-A-unverified-watermark.jpg'),fullPage:false});
   assert.deepEqual(errors,[],'No browser JS exceptions');
   console.log('PASS Channel A: owner gate, defaults OFF, update, four-card review, decline guard, scheduling, cancellation, OFF block, 5 sizes & screenshots');
 }finally{await browser.close();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
}
main().catch(e=>{console.error(e);server.closeAllConnections();server.close();process.exitCode=1});
