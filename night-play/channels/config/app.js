(() => {
'use strict';
const $=id=>document.getElementById(id);
const params=new URLSearchParams(location.search);
const channelId=params.get('channel')||'';
const MODULES=['memes','news','anonymous','ai'];
const CATEGORIES=new Set(['relatable','wholesome','anime','nigerian','animals','relationships','dark']);
const INTERESTS=new Set(['casting','releases','trailers','renewals','romance','platforms']);
const MAX_APPROVED=10; // Agent C's bounded durable queue contract.
let channel=null,config=null,batch=null,selection=null,selectionUncertain=false,trayEditing=false,reviewBusy=false,configBusy=false,loadEpoch=0;
const decisions=new Map(),captionDirty=new Set();
const jid=/^\d{8,25}@newsletter$/;
const make=(tag,cls,text)=>{const el=document.createElement(tag);if(cls)el.className=cls;if(text!==undefined)el.textContent=String(text);return el};
const setBusy=(el,value)=>{if(el)el.disabled=value;};
const verifiedChannelName=()=>channel?.verifiedNameSource==='whatsapp-raw'
  &&typeof channel.verifiedDisplayName==='string'&&channel.verifiedDisplayName.trim()
  ?channel.verifiedDisplayName:null;
const toast=message=>{const t=$('toast');t.textContent=message;t.hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>t.hidden=true,4300)};
const note=(message,isError=false,id='notice')=>{const n=$(id);n.hidden=!message;n.className='config-banner'+(isError?' error':'');n.textContent=message||''};
const showState=(title,body,retry=false)=>{$('state').hidden=false;$('configUI').hidden=true;$('stateTitle').textContent=title;$('stateText').textContent=body;$('retry').hidden=!retry};
const errorMessage=error=>error instanceof Error?error.message:'NIGHT could not finish this request.';
const mayHaveCommitted=error=>!Number.isInteger(error?.status)||error.status===409||error.status>=500;
async function api(action,rest={}){
  let response;
  try {
    response=await fetch('/api/account',{
      method:action==='status'?'GET':'POST',
      credentials:'same-origin',cache:'no-store',
      headers:action==='status'?{}:{'content-type':'application/json'},
      body:action==='status'?undefined:JSON.stringify({action,channelId,...rest}),
      signal:AbortSignal.timeout(14000)
    });
  }catch{throw new Error('NIGHT did not confirm the request. Check status before retrying.');}
  const data=await response.json().catch(()=>({error:'Invalid response from NIGHT.'}));
  if(!response.ok){const failure=new Error(typeof data.error==='string'?data.error:'This feature is not available on NIGHT yet.');failure.status=response.status;throw failure;}
  return data;
}
const validSettings=value=>{
  if(!value||typeof value!=='object'||!Number.isInteger(value.revision)||value.revision<0
    ||!value.modules||typeof value.modules!=='object')return false;
  return MODULES.every(key=>typeof value.modules[key]==='boolean');
};
const selected=(id,allow)=>[...$(id).querySelectorAll('input:checked')].map(e=>e.value).filter(x=>allow.has(x));
function fillChecks(id,items){
 const current=new Set(Array.isArray(items)?items:[]);
 for(const node of $(id).querySelectorAll('input'))node.checked=current.has(node.value);
}
function replacementRow(rule={}){
 const row=make('div','replacement-row');
 const from=make('input','text-input'),to=make('input','text-input');
 from.type=to.type='text';from.maxLength=80;to.maxLength=120;
 from.placeholder='Original phrase';to.placeholder='Replace with';
 from.setAttribute('aria-label','Original word or phrase');
 to.setAttribute('aria-label','Replacement text');
 from.value=typeof rule.from==='string'?rule.from:'';
 to.value=typeof rule.to==='string'?rule.to:'';
 const remove=make('button','btn','Remove');remove.type='button';
 remove.addEventListener('click',()=>row.remove());
 row.append(from,to,remove);
 $('replacementRows').append(row);
}
function paintReplacements(rules){
 $('replacementRows').replaceChildren();
 for(const item of Array.isArray(rules)?rules.slice(0,32):[])if(item&&typeof item==='object')replacementRow(item);
}
function readReplacements(){
 const rows=[...$('replacementRows').children];
 if(rows.length>32)throw Error('Use 32 or fewer caption replacements.');
 const seen=new Set(),rules=[];
 for(const row of rows){
   const [fromInput,toInput]=row.querySelectorAll('input');
   const from=fromInput.value.trim(),to=toInput.value;
   if(!from&&!to)continue;
   if(!from||from.length>80||to.length>120||/[\r\n]/.test(from))throw Error('Enter a valid phrase (maximum 80 characters) for each replacement.');
   const key=from.normalize('NFC').toLocaleLowerCase('en');
   if(seen.has(key))throw Error('Each replacement phrase must be unique.');
   seen.add(key);rules.push({from,to});
 }
 return rules;
}
function paintConfig(){
 const m=config.modules;
 MODULES.forEach(id=>{const input=document.querySelector('[data-module="'+id+'"]');if(input){input.checked=m[id]===true;input.disabled=configBusy||(id==='ai'&&config.capabilities?.ai!==true)}});
 $('memeMode').value=['automatic','review'].includes(config.memes?.mode)?config.memes.mode:'review';
 $('memeDaily').value=[1,2,3,4].includes(config.memes?.dailyLimit)?String(config.memes.dailyLimit):'1';
 $('memeTime').value=/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(config.memes?.localTime||'')?config.memes.localTime:'18:00';
 fillChecks('memeCategories',config.memes?.categories);
 $('newsMode').value=['review','automatic'].includes(config.news?.mode)?config.news.mode:'review';
 $('spoilerPolicy').value=['avoid','warn'].includes(config.news?.spoilers)?config.news.spoilers:'avoid';
 $('newsTime').value=/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(config.news?.localTime||'')?config.news.localTime:'18:00';
 $('newsDaily').value=[1,2,3].includes(config.news?.dailyLimit)?String(config.news.dailyLimit):'1';
 fillChecks('newsInterests',config.news?.interests);
 const branding=config.branding;
 $('brandingControls').hidden=!branding;
 $('brandingUnavailable').hidden=Boolean(branding);
 if(branding){
   $('captionReplacementsEnabled').checked=branding.captionReplacementsEnabled===true;
   paintReplacements(branding.captionReplacements);
   $('signatureEnabled').checked=branding.signatureEnabled===true;
   $('posterName').value=typeof branding.posterName==='string'?branding.posterName:'';
   $('watermarkEnabled').checked=branding.watermarkEnabled===true;
   // If an older server already stored ON, permit disabling it even while name proof is unavailable.
   $('watermarkEnabled').disabled=!verifiedChannelName()&&branding.watermarkEnabled!==true;
   const alpha=[0.15,0.2,0.22,0.25,0.3].includes(branding.watermarkOpacity)?branding.watermarkOpacity:0.22;
   $('watermarkOpacity').value=String(alpha);
   $('watermarkPosition').value=['auto','bottom-left','bottom-right','top-left','top-right'].includes(branding.watermarkPosition)?branding.watermarkPosition:'auto';
 }
 $('watermarkName').textContent=verifiedChannelName()||'Exact channel name not yet verified';
 $('watermarkWarning').hidden=Boolean(verifiedChannelName());
 $('configUI').hidden=false;$('state').hidden=true;
}
async function changeConfig(patch,control){
 if(configBusy||!config)return;
 const prior=config;configBusy=true;
 for(const element of document.querySelectorAll('[data-module],#saveMemes,#saveNews,#saveBranding'))element.disabled=true;
 control&&setBusy(control,true);
 try{
   const result=await api('channel:config:update',{expectedRevision:config.revision,patch});
   if(!validSettings(result))throw Error('The bot did not confirm the settings. Refresh before changing anything else.');
   config=result;paintConfig();updateReview();note('Settings saved to NIGHT for this channel.');
 }catch(error){
   config=prior;paintConfig();updateReview();note(errorMessage(error)+' Save not confirmed. Reload to check actual settings.',true);
 }finally{
   configBusy=false;paintConfig();
   for(const id of ['saveMemes','saveNews','saveBranding'])setBusy($(id),false);
   if(control)setBusy(control,false);
 }
}
function attachControls(){
 document.querySelectorAll('[data-module]').forEach(el=>el.addEventListener('change',()=>{
   if(!config||configBusy)return;
   const id=el.dataset.module;
   if(id==='ai'&&config.capabilities?.ai!==true){el.checked=false;return;}
   if(id==='memes'&&el.checked&&!selected('memeCategories',CATEGORIES).length){
     el.checked=false;note('Choose at least one meme category before enabling.',true);return;
   }
   if(id==='news'&&el.checked&&!selected('newsInterests',INTERESTS).length){
     el.checked=false;note('Choose at least one news interest before enabling.',true);return;
   }
   const patch={modules:{[id]:el.checked}};
   if(id==='memes'&&el.checked)patch.memes={
     mode:$('memeMode').value,categories:selected('memeCategories',CATEGORIES),dailyLimit:Number($('memeDaily').value),localTime:$('memeTime').value,timezone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC'
   };
   if(id==='news'&&el.checked)patch.news={
     mode:$('newsMode').value,interests:selected('newsInterests',INTERESTS),spoilers:$('spoilerPolicy').value,localTime:$('newsTime').value,dailyLimit:Number($('newsDaily').value),timezone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC'
   };
   changeConfig(patch,el);
 }));
 $('saveMemes').addEventListener('click',()=>{
   const categories=selected('memeCategories',CATEGORIES);
   if(!categories.length){note('Choose at least one meme category.',true);return;}
   changeConfig({memes:{mode:$('memeMode').value,categories,dailyLimit:Number($('memeDaily').value),localTime:$('memeTime').value,timezone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC'}},$('saveMemes'));
 });
 $('addReplacement').addEventListener('click',()=>{
   if($('replacementRows').children.length>=32){note('Maximum 32 caption replacements.',true);return;}
   replacementRow();$('replacementRows').lastElementChild.querySelector('input')?.focus();
 });
 $('saveBranding').addEventListener('click',()=>{
   if(!config?.branding)return;
   let captionReplacements;
   try{captionReplacements=readReplacements();}catch(error){note(errorMessage(error),true);return;}
   const captionReplacementsEnabled=$('captionReplacementsEnabled').checked;
   if(captionReplacementsEnabled&&!captionReplacements.length){note('Add a caption replacement before enabling the word swaps.',true);return;}
   if($('watermarkEnabled').checked&&!verifiedChannelName()){note('NIGHT must verify the unmodified WhatsApp channel name before saving a watermark.',true);return;}
   const posterName=$('posterName').value.trim();
   const signatureEnabled=$('signatureEnabled').checked;
   if(signatureEnabled&&!posterName){
     note('An automatic post needs an explicitly credited person. Enter a poster name or disable the signature.',true);return;
   }
   changeConfig({branding:{
     captionReplacementsEnabled,captionReplacements,
     signatureEnabled,posterName,watermarkEnabled:$('watermarkEnabled').checked,
     watermarkOpacity:Number($('watermarkOpacity').value),
     watermarkPosition:$('watermarkPosition').value
   }},$('saveBranding'));
 });
 $('closePostPreview').addEventListener('click',()=>$('postPreview').close());
 $('saveNews').addEventListener('click',()=>{
   const interests=selected('newsInterests',INTERESTS);
   if(!interests.length){note('Choose at least one news interest.',true);return;}
   changeConfig({news:{mode:$('newsMode').value,interests,spoilers:$('spoilerPolicy').value,localTime:$('newsTime').value,dailyLimit:Number($('newsDaily').value),timezone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC'}},$('saveNews'));
 });
 $('findMemes').addEventListener('click',()=>findMemes(false));
 $('nextBatch').addEventListener('click',()=>findMemes(true));
 $('publishApproved').addEventListener('click',()=>publish('now'));
 $('scheduleButton').addEventListener('click',()=>{$('schedulePanel').hidden=false;$('scheduleAt').focus()});
 $('cancelSchedule').addEventListener('click',()=>{$('schedulePanel').hidden=true});
 $('confirmSchedule').addEventListener('click',()=>publish('schedule'));
 $('refreshJobs').addEventListener('click',loadJobs);
 $('retry').addEventListener('click',load);
}
function imageFromSource(candidate){
 if(typeof candidate.previewData==='string'&&/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/i.test(candidate.previewData)&&candidate.previewData.length<=1900000)return candidate.previewData;
 if(typeof candidate.previewUrl==='string'&&/^\/api\/channel-(?:media|assets)\/(?:[A-Za-z0-9_-]{1,100}\/){0,5}[A-Za-z0-9_-]{1,100}(?:\.(?:png|jpg|jpeg|webp))?$/.test(candidate.previewUrl))return candidate.previewUrl;
 return '';
}
function validBatch(result){
 if(!result||typeof result.batchId!=='string'||!/^[A-Za-z0-9_-]{8,96}$/.test(result.batchId)
   ||!Array.isArray(result.candidates)||result.candidates.length!==4)return false;
 const ids=new Set();
 return result.candidates.every(c=>{
   if(!c||typeof c.id!=='string'||! /^[A-Za-z0-9_-]{5,100}$/.test(c.id)||ids.has(c.id)
     ||typeof c.caption!=='string'||c.caption.length>1800||!imageFromSource(c))return false;
   ids.add(c.id);return true;
 });
}
const decisionValue=c=>decisions.get(c.id)?.decision||'pending';
const licensable=c=>c?.publishable===true&&c?.rightsStatus==='verified'&&c?.reviewOnly!==true;
const validSelection=value=>{
 if(!value||typeof value!=='object'||typeof value.id!=='string'
    ||!/^[A-Za-z0-9_-]{8,96}$/.test(value.id)||!Number.isInteger(value.revision)||value.revision<0
    ||!Array.isArray(value.items)||value.items.length>MAX_APPROVED)return false;
 const seen=new Set();
 return value.items.every(item=>{
   if(!item||typeof item.batchId!=='string'||!/^[A-Za-z0-9_-]{8,96}$/.test(item.batchId)
      ||typeof item.candidateId!=='string'||!/^[A-Za-z0-9_-]{5,100}$/.test(item.candidateId)
      ||typeof item.caption!=='string'||item.caption.length>1800
      ||typeof item.title!=='string'||item.title.length>200
      ||!licensable(item)||!imageFromSource(item))return false;
   const id=item.batchId+':'+item.candidateId;
   if(seen.has(id))return false;
   seen.add(id);return true;
 });
};
const readSelection=result=>{
 if(!validSelection(result?.selection))throw Error('NIGHT has not confirmed the saved selections. Publishing is unavailable until the bot supports a persistent approved tray.');
 return result.selection;
};
const approved=()=>selection?.items||[];
function updateReview(){
 const count=approved().length;
 $('reviewSummary').hidden=!batch&&!count;
 $('approvedCount').textContent=count+' approved'+(count===MAX_APPROVED?' · selection full':'');
 $('publishApproved').disabled=!count||reviewBusy||configBusy||selectionUncertain||trayEditing||captionDirty.size>0||config?.modules?.memes!==true;
 $('scheduleButton').disabled=!count||reviewBusy||configBusy||selectionUncertain||trayEditing||captionDirty.size>0||config?.modules?.memes!==true;
 $('nextBatch').disabled=reviewBusy||configBusy||selectionUncertain||trayEditing||captionDirty.size>0||config?.modules?.memes!==true;
 $('findMemes').disabled=reviewBusy||configBusy||selectionUncertain||trayEditing||captionDirty.size>0||config?.modules?.memes!==true;
 for(const button of document.querySelectorAll('#selectedPosts button.selected-action'))
   button.disabled=reviewBusy||configBusy||selectionUncertain||trayEditing||config?.modules?.memes!==true;
 for(const card of document.querySelectorAll('.review-card')){
   const candidateId=card.dataset.candidate;
   const c=batch?.candidates?.find(item=>item.id===candidateId);
   const selectedAlready=approved().some(item=>item.batchId===batch?.batchId&&item.candidateId===candidateId);
   const previewButton=card.querySelector('[data-post-preview]');
   if(previewButton)previewButton.disabled=reviewBusy||configBusy||config?.modules?.memes!==true;
   for(const button of card.querySelectorAll('[data-review-action]')){
     const act=button.dataset.reviewAction;
     button.disabled=reviewBusy||configBusy||selectionUncertain||trayEditing||config?.modules?.memes!==true
       ||(act==='approved'&&(!licensable(c)||card.dataset.previewFailed==='true'||(count>=MAX_APPROVED&&!selectedAlready)));
   }
   const status=card.querySelector('.decision');
   if(c&&count>=MAX_APPROVED&&!selectedAlready&&licensable(c)){
     if(status&&!status.textContent)status.textContent='Selection full — post or remove one before approving';
   }else if(status?.textContent==='Selection full — post or remove one before approving')status.textContent='';
 }
}
function paintSelection(){
 const list=$('selectedPosts');
 list.replaceChildren();
 $('selectionTray').hidden=!approved().length;
 for(const item of approved()){
   const row=make('div','selected-item');
   const img=make('img');img.src=imageFromSource(item);img.alt=item.title;img.loading='lazy';
   const info=make('div','selected-copy');
   info.append(make('strong',null,item.title),make('small',null,item.caption||'No caption'));
   const actions=make('div','selected-actions');
   const edit=make('button','btn selected-action','Edit caption'),preview=make('button','btn selected-action','Preview'),remove=make('button','btn selected-action','Remove');
   edit.type=preview.type=remove.type='button';
   edit.setAttribute('aria-label','Edit '+item.title+' caption');
   preview.setAttribute('aria-label','Preview '+item.title+' post');
   remove.setAttribute('aria-label','Remove '+item.title+' from approved selection');
   edit.addEventListener('click',()=>{
     if(trayEditing||reviewBusy||configBusy||selectionUncertain)return;
     trayEditing=true;
     const editor=make('textarea','text-input textarea selected-editor');
     editor.maxLength=1800;editor.value=item.caption;editor.setAttribute('aria-label','Edit approved caption for '+item.title);
     const save=make('button','btn primary','Save caption'),cancel=make('button','btn','Cancel edit');
     save.type=cancel.type='button';
     save.addEventListener('click',()=>updateSelectedCaption(item,editor.value));
     cancel.addEventListener('click',()=>{trayEditing=false;paintSelection();updateReview();});
     actions.replaceChildren(editor,save,cancel);updateReview();editor.focus();
   });
   preview.addEventListener('click',()=>previewPost(item,item.caption,item.batchId));
   remove.addEventListener('click',()=>removeSelected(item,remove));
   actions.append(edit,preview,remove);
   row.append(img,info,actions);list.append(row);
 }
}
async function updateSelectedCaption(item,caption){
 if(reviewBusy||configBusy||!trayEditing||selectionUncertain||!selection||config?.modules?.memes!==true)return;
 reviewBusy=true;updateReview();
 try{
   const result=await api('channel:memes:review',{batchId:item.batchId,candidateId:item.candidateId,decision:'approved',caption,expectedSelectionRevision:selection.revision});
   if(result?.decision!=='approved'||result?.candidateId!==item.candidateId)throw Error('The bot did not confirm the updated caption.');
   const next=readSelection(result);
   const changed=next.items.find(i=>i.batchId===item.batchId&&i.candidateId===item.candidateId);
   if(next.id!==selection.id||!changed||changed.caption!==caption)throw Error('The new approved caption was not confirmed.');
   selection=next;
   if(batch?.batchId===item.batchId){decisions.set(item.candidateId,{decision:'approved',caption});captionDirty.delete(item.candidateId);paintReview();}
   note('Approved caption saved.');
 }catch(error){
   if(mayHaveCommitted(error))selectionUncertain=true;
   note(errorMessage(error)+' Reload to verify the approved caption.',true);
 }finally{reviewBusy=false;trayEditing=false;paintSelection();updateReview();}
}
async function removeSelected(item,button){
 if(reviewBusy||configBusy||trayEditing||selectionUncertain||!selection||config?.modules?.memes!==true)return;
 reviewBusy=true;setBusy(button,true);updateReview();
 try{
   const result=await api('channel:memes:review',{batchId:item.batchId,candidateId:item.candidateId,decision:'skipped',caption:item.caption,expectedSelectionRevision:selection.revision});
   if(result?.decision!=='skipped'||result?.candidateId!==item.candidateId)throw Error('NIGHT did not confirm removal.');
   const next=readSelection(result);
   if(next.id!==selection.id||next.items.some(i=>i.batchId===item.batchId&&i.candidateId===item.candidateId))throw Error('The saved selection was not updated.');
   selection=next;decisions.set(item.candidateId,{decision:'skipped',caption:item.caption});
   if(batch?.batchId===item.batchId)paintReview();
   else paintSelection();
   note('Removed from approved selection.');
 }catch(error){if(mayHaveCommitted(error))selectionUncertain=true;note(errorMessage(error)+' Reload to verify your saved approvals.',true);}
 finally{reviewBusy=false;paintSelection();updateReview();}
}
function reviewCard(c){
 const card=make('article','review-card');
 card.dataset.candidate=c.id;
 const state=decisionValue(c);
 if(state==='approved'||state==='declined')card.classList.add(state);
 const art=make('div','review-art'),img=make('img');
 img.src=imageFromSource(c);img.alt=typeof c.alt==='string'?c.alt:'Meme candidate';img.loading='lazy';img.decoding='async';
 img.addEventListener('error',()=>{card.dataset.previewFailed='true';art.replaceChildren(make('span','config-note','Preview unavailable. Do not approve this image.'));approve.disabled=true});
 art.append(img);
 const body=make('div','review-card-body');
 body.append(make('h3',null,typeof c.title==='string'?c.title:('Meme · '+(c.category||'Uncategorised'))));
 const source=make('p','source');
 source.append(document.createTextNode((c.category||'Meme')+' · '+(c.sourceName||'Source unverified')));
 if(typeof c.sourceUrl==='string'&&/^https:\/\//.test(c.sourceUrl)){
   const a=make('a',null,'View source');a.href=c.sourceUrl;a.rel='noopener noreferrer';a.target='_blank';
   source.append(document.createTextNode(' · '),a);
 }
 body.append(source);
 const label=make('label','field'),lbl=make('span',null,'Caption'),caption=make('textarea','text-input textarea');
 caption.maxLength=1800;caption.value=decisions.get(c.id)?.caption??c.caption;caption.setAttribute('aria-label','Caption for '+(c.title||c.id));
 label.append(lbl,caption);body.append(label);
 const status=make('p','decision',state==='approved'?'Approved':state==='declined'?'Declined':state==='skipped'?'Skipped':'');
 status.setAttribute('aria-live','polite');
 const actions=make('div','action-row');
 const approve=make('button','btn primary','Approve'),decline=make('button','btn','Decline'),skip=make('button','btn','Skip'),preview=make('button','btn','Preview post');
 approve.dataset.reviewAction='approved';
 decline.dataset.reviewAction='declined';skip.dataset.reviewAction='skipped';preview.dataset.postPreview='1';
 if(!licensable(c)){approve.disabled=true;status.textContent='Review only · Media reuse rights not verified';}
 approve.type=decline.type=skip.type=preview.type='button';
 caption.addEventListener('input',()=>{
   captionDirty.add(c.id);status.textContent='Caption edited — approve again to save';updateReview();
 });
 approve.addEventListener('click',()=>decide(c,'approved',caption.value,card,status,actions));
 decline.addEventListener('click',()=>decide(c,'declined',caption.value,card,status,actions));
 skip.addEventListener('click',()=>decide(c,'skipped',caption.value,card,status,actions));
 preview.addEventListener('click',()=>previewPost(c,caption.value));
 actions.append(approve,decline,skip,preview);
 body.append(actions,status);card.append(art,body);
 return card;
}
function paintReview(){
 $('candidates').replaceChildren();
 if(batch){
   for(const c of batch.candidates)$('candidates').append(reviewCard(c));
   $('batchLabel').textContent='Four candidates';
 }else $('batchLabel').textContent='';
 paintSelection();updateReview();
}
async function findMemes(next){
 if(reviewBusy||configBusy||!channel||config?.modules?.memes!==true)return;
 if(trayEditing){note('Save or cancel the approved caption edit first.',true,'reviewNotice');return;}
 if(selectionUncertain){note('Reload to verify the approved selection before finding more memes.',true,'reviewNotice');return;}
 if(captionDirty.size){note('Approve, decline, or skip edited captions before finding another batch.',true,'reviewNotice');return;}
 reviewBusy=true;setBusy($('findMemes'),true);updateReview();
 note('Searching permitted sources…',false,'reviewNotice');
 try{
   const result=await api('channel:memes:find',{categories:selected('memeCategories',CATEGORIES),next:next===true});
   if(!validBatch(result))throw Error('NIGHT did not return four complete sourced meme images.');
   const saved=readSelection(result);
   if(selection&&saved.id!==selection.id)throw Error('The saved selection changed. Reload before continuing.');
   batch=result;selection=saved;decisions.clear();captionDirty.clear();
   for(const d of result.decisions||[])if(d&&typeof d.candidateId==='string'&&['approved','declined','skipped'].includes(d.decision))decisions.set(d.candidateId,{decision:d.decision,caption:typeof d.caption==='string'?d.caption:''});
   paintReview();note('',false,'reviewNotice');
 }catch(error){note(errorMessage(error)+' No content was approved or published.',true,'reviewNotice');}
 finally{reviewBusy=false;setBusy($('findMemes'),false);paintSelection();updateReview();}
}
async function previewPost(candidate,caption,batchId=batch?.batchId){
 if(!batchId||configBusy||config?.modules?.memes!==true)return;
 const dialog=$('postPreview'),body=$('postPreviewContent');
 body.replaceChildren(make('p','config-note','Requesting the actual formatted post from NIGHT…'));
 if(!dialog.open)dialog.showModal();
 try{
   const result=await api('channel:post:preview',{batchId,candidateId:candidate.candidateId||candidate.id,caption});
   const image=imageFromSource(result||{});
   if(!image||typeof result?.formattedCaption!=='string'||result.formattedCaption.length>2200)
     throw Error('NIGHT has not returned a verified final-media preview.');
   const art=make('div','review-art'),img=make('img');
   img.src=image;img.alt='Watermarked post preview from NIGHT';art.append(img);
   const label=make('p','config-note','Caption as it will be sent');
   const displayed=make('p','config-preview-caption',result.formattedCaption);
   displayed.style.whiteSpace='pre-wrap';displayed.style.overflowWrap='anywhere';
   body.replaceChildren(art,label,displayed);
 }catch(error){
   body.replaceChildren(make('p','config-note',errorMessage(error)+' No final preview is available. Approving here will not bypass the publishing checks.'));
 }
}
async function decide(candidate,decision,caption,card,status,actions){
 if(reviewBusy||configBusy||trayEditing||selectionUncertain||!batch||!selection||config?.modules?.memes!==true
    ||(decision==='approved'&&(!licensable(candidate)
      ||(approved().length>=MAX_APPROVED&&!approved().some(i=>i.batchId===batch.batchId&&i.candidateId===candidate.id)))))return;
 reviewBusy=true;
 for(const btn of actions.querySelectorAll('button'))setBusy(btn,true);
 updateReview();
 try{
   const result=await api('channel:memes:review',{batchId:batch.batchId,candidateId:candidate.id,decision,caption,expectedSelectionRevision:selection.revision});
   if(result?.decision!==decision||result?.candidateId!==candidate.id)throw Error('NIGHT did not confirm that decision.');
   const saved=readSelection(result);
   if(saved.id!==selection.id)throw Error('The approved selection changed. Refresh before continuing.');
   const inTray=saved.items.some(i=>i.batchId===batch.batchId&&i.candidateId===candidate.id);
   if(inTray!==(decision==='approved'))throw Error('The bot did not persist the selection correctly.');
   selection=saved;
   decisions.set(candidate.id,{decision,caption:typeof result.caption==='string'?result.caption:caption});
   captionDirty.delete(candidate.id);
   card.classList.toggle('approved',decision==='approved');
   card.classList.toggle('declined',decision==='declined');
   status.textContent=decision==='approved'?'Approved':decision==='declined'?'Declined':'Skipped';
   paintSelection();
   note('',false,'reviewNotice');
 }catch(error){if(mayHaveCommitted(error))selectionUncertain=true;note(errorMessage(error)+' Decision not confirmed. Reload to verify saved approvals.',true,'reviewNotice');}
 finally{reviewBusy=false;for(const btn of actions.querySelectorAll('button'))setBusy(btn,false);
   const approveButton=actions.querySelector('button.primary');
   if(approveButton)approveButton.disabled=!licensable(candidate)||card.dataset.previewFailed==='true';
   paintSelection();updateReview();}
}
function localToUTC(input){
 if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(input))throw Error('Choose a valid date and time.');
 const d=new Date(input);
 const reconstructed=[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')+'T'+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
 if(!Number.isFinite(d.getTime())||reconstructed!==input)throw Error('That local time does not exist. Choose another time.');
 if(d.getTime()<Date.now()+60_000)throw Error('Choose a time at least one minute from now.');
 return d.toISOString();
}
async function publish(mode){
 if(reviewBusy||configBusy||trayEditing||selectionUncertain||!selection||!approved().length||captionDirty.size>0||config?.modules?.memes!==true)return;
 let schedule={};
 if(mode==='schedule'){
   try{schedule={scheduledAt:localToUTC($('scheduleAt').value),timezone:Intl.DateTimeFormat().resolvedOptions().timeZone||'UTC',spacingMinutes:Number($('spacingMinutes').value)}}catch(error){note(errorMessage(error),true,'reviewNotice');return;}
 }
 const approvedCount=approved().length;
 reviewBusy=true;updateReview();
 const btn=mode==='schedule'?$('confirmSchedule'):$('publishApproved');
 setBusy(btn,true);
 try{
   const result=await api('channel:memes:publish',{selectionId:selection.id,expectedSelectionRevision:selection.revision,mode,...schedule});
   if(!Array.isArray(result?.jobs)||result.jobs.length!==approvedCount)throw Error('NIGHT did not confirm the selected publishing jobs. Check publishing status before retrying.');
   const cleared=readSelection(result);
   if(cleared.id!==selection.id||cleared.items.length)throw Error('NIGHT did not confirm the approved selection was consumed. Check status before retrying.');
   selection=cleared;batch=null;decisions.clear();captionDirty.clear();paintReview();$('schedulePanel').hidden=true;
   note(result.jobs.length+(mode==='schedule'?' posts scheduled.':' approved posts queued.'));
   await loadJobs();
 }catch(error){if(mayHaveCommitted(error))selectionUncertain=true;note(errorMessage(error)+' Check publishing status and reload before trying again.',true,'reviewNotice');await loadJobs();}
 finally{reviewBusy=false;setBusy(btn,false);updateReview();}
}
async function loadJobs(){
 if(!channel)return;
 $('jobs').replaceChildren(make('p','config-note','Checking publishing status…'));
 try{
   const result=await api('channel:jobs:list');
   if(!Array.isArray(result?.jobs))throw Error('Invalid publishing status.');
   $('jobs').replaceChildren();
   if(!result.jobs.length){$('jobs').append(make('p','config-note','No publishing activity yet.'));return;}
   for(const job of result.jobs.slice(0,40)){
     if(!job||typeof job.id!=='string'||typeof job.status!=='string')continue;
     const row=make('div','job'),copy=make('div');
     copy.append(make('strong',null,String(job.title||job.type||'Channel post').slice(0,120)));
     const date=job.scheduledAt||job.createdAt;
     const when=date&&Number.isFinite(Date.parse(date))?new Date(date).toLocaleString():'';
     copy.append(make('small',null,(when?when+' · ':'')+job.status));
     row.append(copy);
     if(['queued','scheduled'].includes(job.status)){
       const cancel=make('button','btn danger','Cancel');
       cancel.type='button';cancel.addEventListener('click',async()=>{
         setBusy(cancel,true);
         try{const done=await api('channel:jobs:cancel',{jobId:job.id});if(done?.cancelled!==true)throw Error('Cancellation not confirmed.');await loadJobs();}catch(error){note(errorMessage(error),true);setBusy(cancel,false);}
       });row.append(cancel);
     }else row.append(make('span','job-state',job.status));
     $('jobs').append(row);
   }
 }catch(error){$('jobs').replaceChildren(make('p','config-note',errorMessage(error)));}
}
async function load(){
 const epoch=++loadEpoch;channel=null;config=null;batch=null;selection=null;selectionUncertain=false;trayEditing=false;decisions.clear();captionDirty.clear();
 $('configUI').hidden=true;note('');
 if(!jid.test(channelId)){showState('Invalid channel','Choose a verified channel from Your channels.');return;}
 showState('Checking permissions…','Verifying your WhatsApp identity and channel ownership.');
 try{
   const status=await api('status');if(epoch!==loadEpoch)return;
   if(status.linked!==true||!status.profile){showState('Connect WhatsApp','Connect through Profile before configuring a channel.');return;}
   const owned=await api('channel:list');if(epoch!==loadEpoch)return;
   channel=Array.isArray(owned.channels)?owned.channels.find(c=>c.id===channelId&&c.status==='connected'):null;
   if(!channel){showState('Access unavailable','Only a verified, connected channel can be configured.');return;}
   $('channelTitle').textContent=channel.name||'Verified WhatsApp channel';
   const result=await api('channel:config:get');if(epoch!==loadEpoch)return;
   if(!validSettings(result))throw Error('NIGHT has not returned verified, persistent settings. Nothing can be enabled.');
   config=result;paintConfig();
   // A browser's local storage never authorizes publication or grants ownership.
   try{
     const saved=await api('channel:memes:current');
     if(epoch===loadEpoch){
       selection=readSelection(saved);
       if(validBatch(saved))batch=saved;
       for(const d of saved.decisions||[])if(d&&typeof d.candidateId==='string'&&['approved','declined','skipped'].includes(d.decision))decisions.set(d.candidateId,{decision:d.decision,caption:typeof d.caption==='string'?d.caption:''});
       paintReview();
     }
   }catch(error){if(epoch===loadEpoch)note(errorMessage(error)+' Saved selections are unavailable until backend storage is ready.',true,'reviewNotice');}
   if(epoch===loadEpoch)await loadJobs();
 }catch(error){if(epoch!==loadEpoch)return;showState('Configuration unavailable',errorMessage(error)+' Channel settings remain unchanged.',true);}
}
attachControls();
load();
})();
