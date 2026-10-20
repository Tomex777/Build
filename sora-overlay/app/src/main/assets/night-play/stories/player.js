(async function(){
'use strict';
const E=window.NightStory,$=id=>document.getElementById(id);
const ui={story:$('story'),choices:$('choices'),pace:$('pace'),menu:$('readerOptions'),dialog:$('storyDialog'),body:$('dialogBody'),title:$('dialogTitle'),warning:$('saveWarning'),recap:$('recap')};
const el=(tag,text,className)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n};
let storage;try{storage=window.localStorage;storage.getItem('night-story-check')}catch{const m=new Map();storage={getItem:k=>m.get(k)||null,setItem:(k,v)=>m.set(k,v)};warn('Browser storage is unavailable. This session cannot be resumed after closing.');}
let story,book,run,timers=[],generation=0,lines=[],ready=false,revealing=false,revealGuard=0,writeTimer,soundContext,soundEnabled=false,ambient=[],persistBlocked=false,reviewIndex=null;
const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
function warn(text){ui.warning.textContent=text;ui.warning.hidden=false}
function button(text,fn){const b=el('button',text);b.type='button';b.addEventListener('click',fn);return b}
function later(fn,ms){const t=setTimeout(fn,ms);timers.push(t);return t}
function cancel(){generation++;timers.forEach(clearTimeout);timers=[];revealing=false}
function write(){
 if(!run)return;
 run.updated=Date.now();
 book=E.storeRun(story,book,run);
 try{if(!persistBlocked)E.persist(story,book,storage)}catch{warn('Your latest reading is still open, but autosave could not be written. Free some browser storage before closing.');}
}
function saveSoon(){clearTimeout(writeTimer);writeTimer=setTimeout(write,180)}
function snapshot(){if(!run||reviewIndex!==null)return;run.reading.scroll=scrollY;run.reading.ready=ready;saveSoon()}
function addAmbient(){
 stopAmbient();if(!soundEnabled||document.hidden)return;
 try{
  soundContext=soundContext||new (window.AudioContext||window.webkitAudioContext)();soundContext.resume();
  for(const [f,g] of [[110,.009],[164.81,.006]]){const o=soundContext.createOscillator(),a=soundContext.createGain();o.type='sine';o.frequency.value=f;a.gain.value=g;o.connect(a).connect(soundContext.destination);o.start();ambient.push({o,a})}
 }catch{soundEnabled=false;$('sound').checked=false}
}
function stopAmbient(){for(const x of ambient){try{x.o.stop();x.o.disconnect();x.a.disconnect()}catch{}}ambient=[]}
function bell(){if(!soundEnabled||!soundContext)return;const o=soundContext.createOscillator(),g=soundContext.createGain(),t=soundContext.currentTime;o.frequency.value=523.25;g.gain.setValueAtTime(.025,t);g.gain.exponentialRampToValueAtTime(.0001,t+1.4);o.connect(g).connect(soundContext.destination);o.start();o.stop(t+1.5)}
function revealName(line){const label=line.querySelector('.speaker');if(label&&line.dataset.revealName){label.textContent=line.dataset.revealName;line.setAttribute('aria-label',label.textContent+': '+line.dataset.utterance);line.classList.add('name-revealed')}}
function setLine(line){line.classList.remove('go');line.classList.add('settled');revealName(line)}
function markRead(index){
 setLine(lines[index]);
 let count=0;for(const line of lines){if(!line.classList.contains('settled'))break;count++}
 run.reading.revealed=count;saveSoon();
}
function displayRecap(){
 ui.recap.replaceChildren();ui.recap.hidden=false;
 ui.recap.append(el('h2','YOUR STORY'),el('h3',story.endings[run.ending]),el('p',E.text('{{fullName}}',run),'recap-identity'));
 ui.recap.append(resourceDisplay());
 const list=el('ul');E.summary(story,run).forEach(t=>list.append(el('li',t)));ui.recap.append(list);
 ui.recap.append(el('p',book.endings.filter(e=>(story.mainEndings||Object.keys(story.endings)).includes(e)).length+' of '+(story.mainEndings||Object.keys(story.endings)).length+' endings discovered in your playthroughs.'));
 ui.recap.append(button('Start a separate playthrough',()=>newRun()));
 const link=el('a','Story library');link.href='/stories/library.html';ui.recap.append(link);
}
function showChoices(){
 if(ready)return;ready=true;revealing=false;run.reading.ready=true;run.reading.revealed=lines.length;
 ui.choices.classList.remove('pending');for(const b of ui.choices.querySelectorAll('button'))b.disabled=b.dataset.locked==='true';
 if(run.ending){ui.choices.hidden=true;displayRecap();bell()}
 updateNavigation();write();
}
function revealAll(){
 if(!revealing)return;
 cancel();lines.forEach(setLine);revealGuard=performance.now()+280;run.reading.revealed=lines.length;showChoices();
}
function makeLine(beat){
 const p=el('div',undefined,'step'+(beat.type?' cinematic '+beat.type:''));p.setAttribute('aria-label',beat.speaker?beat.speaker+': '+beat.text:beat.text);
 if(beat.kind)p.dataset.kind=beat.kind;
 if(beat.speaker){const slot=el('div',undefined,'speaker-slot');slot.append(el('span',beat.speaker,'speaker'));p.dataset.utterance=beat.text;if(beat.revealName){p.dataset.revealName=E.text(beat.revealName,run);const size=el('span',p.dataset.revealName,'speaker-size');size.setAttribute('aria-hidden','true');slot.append(size)}p.append(slot)}
 const content=el('p',undefined,'beat-text');p.append(content);
 if(beat.type==='notification'){p.setAttribute('role','status');p.append(el('small',beat.detail,'event-detail'));}

 for(const part of beat.text.split(/(\s+)/)){if(!part)continue;if(/^\s+$/.test(part))content.append(document.createTextNode(part));else{const span=el('span',part,'word');span.setAttribute('aria-hidden','true');content.append(span)}}
 return p;
}
function resolveBlankPages(){let guard=0;while(!E.beats(story,run).length&&guard++<500){const c=E.choices(story,run);if(c.length!==1||!c[0].continue)break;run=E.choose(story,run,c[0].id)}}
// Review only recorded prose. No E.choose, E.enter, effects, history edits or save rewind.
function updateNavigation(){
 const window=E.readingWindow(story,run),index=reviewIndex===null?window.current:reviewIndex;
 const position=window.indices.indexOf(index),previous=position>0;
 const options=E.options(story,run);
 const pagination=reviewIndex===null&&!run.ending&&options.length===1&&options[0].continue&&!options[0].locked;
 const next=reviewIndex!==null||pagination;
 $('readPrevious').hidden=!previous;$('readContinue').hidden=!next;
 $('readingNav').hidden=!(previous||next);
 $('readPrevious').disabled=!ready;$('readContinue').disabled=!ready;
 ui.choices.hidden=reviewIndex!==null||pagination||Boolean(run.ending);
}
function reviewPage(){
 cancel();ready=true;revealing=false;ui.menu.open=false;ui.recap.hidden=true;
 const page=run.pages[reviewIndex];
 if(!page){reviewIndex=null;render(true);return;}
 $('chapterStatus').textContent=page.chapter?'Chapter '+page.chapter+' of '+story.chapters.length+' · '+page.title:page.title;
 ui.story.setAttribute('aria-label','Previously read: '+page.title);
 document.documentElement.dataset.atmosphere=story.nodes[page.node]?.atmosphere||story.atmosphere||'quiet';
 // Recover speaker and cinematic formatting where the authored beat matches its saved text.
 // The saved paragraph always wins when current conditions differ from the original visit.
 const candidates=(story.nodes[page.node]?.beats||[]).map(beat=>({
  ...beat,text:E.text(beat.text,run),speaker:beat.speaker?E.text(beat.speaker,run):undefined,
  detail:E.text(beat.detail,run)
 }));
 const used=new Set();
 const recorded=page.text.map(text=>{
  const i=candidates.findIndex((beat,n)=>!used.has(n)&&(beat.speaker?beat.speaker+': '+beat.text:beat.text)===text);
  if(i<0)return {text};
  used.add(i);return candidates[i];
 });
 lines=recorded.map(makeLine);lines.forEach(setLine);
 ui.story.replaceChildren(...lines);ui.choices.replaceChildren();
 ui.choices.classList.remove('pending');updateNavigation();
 scrollTo({top:0,behavior:'instant'});
}
function previousPage(){
 if(!ready)return;
 const window=E.readingWindow(story,run),index=reviewIndex===null?window.current:reviewIndex;
 const position=window.indices.indexOf(index);if(position<=0)return;
 if(reviewIndex===null)snapshot();
 reviewIndex=window.indices[position-1];reviewPage();
}
function continuePage(){
 if(!ready)return;
 if(reviewIndex!==null){
  const window=E.readingWindow(story,run),position=window.indices.indexOf(reviewIndex);
  if(position<0||position>=window.indices.length-1)return;
  const next=window.indices[position+1];
  if(next===window.current){reviewIndex=null;render(true);}
  else{reviewIndex=next;reviewPage();}
  return;
 }
 const options=E.options(story,run);
 if(run.ending||options.length!==1||!options[0].continue||options[0].locked)return;
 run=E.choose(story,run,options[0].id);write();render();
}
function render(restore=false){
 reviewIndex=null;cancel();resolveBlankPages();for(const id of ['openCharacter','openJournal','openChapters'])$(id).disabled=false;ready=false;const epoch=generation,node=story.nodes[run.node],bs=E.beats(story,run);
 ui.menu.open=false;ui.recap.hidden=true;ui.choices.hidden=false;$('readingNav').hidden=true;ui.story.setAttribute('aria-label','Chapter '+node.chapter+': '+node.title);
 $('chapterStatus').textContent=node.chapter?'Chapter '+node.chapter+' of '+story.chapters.length+' · '+node.title:node.title;
 document.documentElement.dataset.atmosphere=node.atmosphere||story.atmosphere||'quiet';
 lines=bs.map(makeLine);ui.story.replaceChildren(...lines);ui.choices.replaceChildren();ui.choices.classList.add('pending');
 const saved=restore?Math.min(run.reading.revealed||0,lines.length):0;
 lines.slice(0,saved).forEach(setLine);
 for(const [i,c] of E.options(story,run).entries()){
  const b=el('button',undefined,'choice');b.type='button';b.disabled=true;b.dataset.choice=c.id;if(c.locked){b.dataset.locked='true';b.setAttribute('aria-disabled','true');b.classList.add('locked')}
  b.append(el('span',c.locked?'·':String(i+1),'choice-number'),el('span',c.label,'choice-label'));
  if(c.requirement)b.append(el('small',c.requirement,'choice-requirement'));
  if(c.risk)b.append(el('small',c.risk,'choice-risk'));
  b.addEventListener('click',()=>{if(!ready||c.locked||performance.now()<revealGuard)return;run=E.choose(story,run,c.id);write();render();scrollTo({top:0,behavior:'instant'})});ui.choices.append(b);
 }
 if(restore)requestAnimationFrame(()=>scrollTo({top:run.reading.scroll||0,behavior:'instant'}));else scrollTo({top:0,behavior:'instant'});
 if(reduced||ui.pace.value==='instant'||(restore&&run.reading.ready)){lines.forEach(setLine);showChoices();return}
 revealing=true;const factor=ui.pace.value==='quick'?.45:1;let end=0,cadence=0;
 bs.forEach((beat,index)=>{
  if(index<saved)return;
  const line=lines[index],words=line.querySelectorAll('.word');
  const previous=bs[index-1];
  cadence+=(index===saved?0:previous?.type?Math.min(1800,420+previous.text.split(/\s+/).length*28):(run.node==='start'?0:135))+(beat.delayBefore||0)+(beat.pause||0);
  const offset=Math.round(cadence*factor);
  if(beat.tone==='unease')line.style.setProperty('--tint','#aaa4b6');
  if(beat.tone==='quiet')line.style.setProperty('--tint','#8aa6a2');
  const spread=Math.round(Math.min(650,Math.max(220,words.length*7))*factor),fade=Math.round(900*factor);
  line.style.setProperty('--fade',fade+'ms');
  // Stable positions; stagger the ink, never characters or paragraph geometry.
  words.forEach((word,j)=>word.style.animationDelay=Math.round(((j*67+index*43)%Math.max(1,spread)))+'ms');
  later(()=>{if(epoch===generation){line.classList.add('go');if(beat.type==='notification')bell()}},offset);
  if(beat.revealName)later(()=>{if(epoch===generation)revealName(line)},offset+(beat.revealAfter||620)*factor);
  later(()=>{if(epoch===generation)markRead(index)},offset+spread+fade);
  end=Math.max(end,offset+spread+fade);
 });
 later(()=>{if(epoch===generation){lines.forEach(setLine);showChoices()}},end+20);
 write();
}
function openPanel(title,build){snapshot();ui.menu.open=false;ui.title.textContent=title;ui.body.replaceChildren();ui.body.className='';build(ui.body);ui.dialog.showModal()}
function resourceDisplay(){
 const row=el('div',undefined,'resource-display');
 for(const [key,def] of Object.entries(story.rules?.resources||{})){
  const value=run.state.resources[key],item=el('div',undefined,'resource');
  item.append(el('small',def.title));const marks=el('span',def.icon==='heart'?Array.from({length:def.max},(_,i)=>i<value?'♥':'♡').join(' '):value+' / '+def.max,def.icon==='heart'?'heart-marks':'resource-value');marks.setAttribute('aria-label',value+' of '+def.max+' '+def.title);item.append(marks);row.append(item);
 }
 return row;
}
function attributePicker(body,state,onChange){
 const selection=Object.fromEntries(Object.keys(story.rules.attributes).map(k=>[k,0]));let remaining=state.points;
 const head=el('div',undefined,'allocation-head'),counter=el('span',remaining+' points remaining');counter.setAttribute('aria-live','polite');head.append(el('span','Attributes'),counter);body.append(head);
 const rows=[];
 for(const [key,def] of Object.entries(story.rules.attributes)){
  const row=el('div',undefined,'attribute-row'),label=el('div');label.append(el('strong',def.title),el('small',def.description));
  const value=el('output',String(state.attributes[key])),minus=button('−',()=>change(-1)),plus=button('+',()=>change(1));minus.setAttribute('aria-label','Decrease '+def.title);plus.setAttribute('aria-label','Increase '+def.title);
  const controls=el('div',undefined,'attribute-controls');controls.append(minus,value,plus);row.append(label,controls);body.append(row);rows.push({key,def,value,minus,plus});
  function change(amount){selection[key]+=amount;remaining-=amount;refresh()}
 }
 function refresh(){counter.textContent=remaining+' '+(remaining===1?'point':'points')+' remaining';for(const r of rows){r.value.value=String(state.attributes[r.key]+selection[r.key]);r.minus.disabled=selection[r.key]===0;r.plus.disabled=remaining===0||state.attributes[r.key]+selection[r.key]>=r.def.max}onChange(selection,remaining)}
 refresh();return selection;
}
function setup(body,dialog=false){
 body.classList.add('setup');const form=el('form');form.noValidate=true;
 if(!dialog)form.append(el('h2','Before the call'));
 const fields=el('div',undefined,'name-fields');
 for(const [key,label] of [['firstName','First name'],['surname','Surname']]){const wrap=el('label',label),input=el('input');input.type='text';input.id=key;input.name=key;input.value=story.identity[key];input.autocomplete='off';input.maxLength=120;input.required=true;input.spellcheck=false;wrap.append(input);fields.append(wrap)}
 form.append(fields);const error=el('p',undefined,'input-error');error.setAttribute('role','alert');error.hidden=true;
 const submit=el('button','Begin','primary');submit.type='submit';let selection={};
 attributePicker(form,E.initial(story),(allocation,remaining)=>{selection=allocation;submit.disabled=remaining!==0});
 form.append(error,submit);form.addEventListener('submit',e=>{e.preventDefault();try{const id=E.identity(story,{firstName:form.elements.firstName.value,surname:form.elements.surname.value});run=E.start(story,'Playthrough '+(book.playthroughs.length+1),id,selection);write();if(dialog)ui.dialog.close();ui.story.classList.remove('setup');ui.choices.hidden=false;render()}catch(err){error.textContent=err.message;error.hidden=false}});body.append(form);
}
function newRun(){snapshot();write();openPanel('Before the call',body=>setup(body,true))}
function character(){openPanel(E.text('{{fullName}}',run),body=>{
 body.append(resourceDisplay());
 for(const def of Object.values(story.rules.resources))body.append(el('p',def.description,'sheet-note'));
 const apply=button('Apply points',()=>{try{run=E.allocate(story,run,selection);write();ui.dialog.close();render()}catch(err){warn(err.message)}});apply.className='primary';let selection={};
 if(run.state.points){attributePicker(body,run.state,(value,remaining)=>{selection=value;apply.disabled=remaining===run.state.points});body.append(apply)}
 else{const list=el('dl',undefined,'attribute-summary');for(const [key,def] of Object.entries(story.rules.attributes)){list.append(el('dt',def.title),el('dd',String(run.state.attributes[key])))}body.append(list)}
 const abilities=Object.entries(run.state.abilities);
 if(abilities.length)body.append(el('h3','Abilities'));
 for(const [key,a] of abilities){const def=story.rules.abilities[key],row=el('div',undefined,'ability-row');row.append(el('strong',def.title+' · Level '+a.level),el('p',def.levels[a.level-1]),el('small',def.cost));body.append(row)}
 for(const section of story.sheetSections||[])if(E.matches(section.when,run.state))body.append(el('h3',E.text(section.title,run)),el('p',E.text(section.text,run)));
 for(const [key,rec] of Object.entries(story.characters||{})){if(!E.matches(rec.when,run.state))continue;if(key!=='protagonist')body.append(el('h3',E.text(rec.name,run)));body.append(el('p',E.text(rec.text,run)))}
 if(run.legacyHeritagePending)body.append(button(story.migration.interludeLabel||'Read new interlude',()=>{run=E.interlude(story,run,story.migration.catchupNode);write();ui.dialog.close();render()}));
})}

function saves(){openPanel('Your playthroughs',body=>{
 body.append(button('Start a new playthrough',()=>newRun()));
 for(const r of [...book.playthroughs].sort((a,b)=>b.updated-a.updated)){
  const row=el('div',undefined,'save-row');row.append(el('strong',r.state.identity.firstName+' '+r.state.identity.surname),el('span',r.label,'save-label'));if(r.id===run?.id)row.append(el('span','Open','active-save'));
  const n=story.nodes[r.node];row.append(el('small',(r.ending?story.endings[r.ending]:'Chapter '+n.chapter+' · '+n.title)+' · '+new Date(r.updated).toLocaleString()));
  if(r.id!==run?.id)row.append(button(r.ending?'View ending':'Resume',()=>{snapshot();write();run=E.clone(book.playthroughs.find(x=>x.id===r.id));book.active=run.id;write();ui.dialog.close();render(true)}));body.append(row);
 }
 try{if(story.shortEdition&&storage.getItem(story.shortEdition.saveKey)){const p=el('p');const a=el('a','Resume your earlier short edition');a.href=story.shortEdition.href;p.append(a);body.append(p)}}catch{}
 body.append(el('p','Playthroughs are saved in this browser. Starting another keeps the earlier one.'));
})}
function journal(){openPanel('Story journal',body=>{
 const discovered=run.state.knowledge.map(k=>story.knowledge[k]).filter(Boolean);
 if(discovered.length)body.append(el('h3','Discovered knowledge'));
 for(const k of discovered){const row=el('div',undefined,'journal-row');row.append(el('strong',E.text(k.title,run)),el('p',E.text(k.text,run)));body.append(row)}
 if(run.state.inventory.length)body.append(el('h3','What you carry'));const items=el('ul',undefined,'journal-list');run.state.inventory.forEach(k=>items.append(el('li',E.text(story.items[k]||k,run))));if(run.state.inventory.length)body.append(items);
 if(Object.keys(run.state.memories).length)body.append(el('h3','What people remember'));
 for(const [name,memories] of Object.entries(run.state.memories)){body.append(el('strong',name));const ul=el('ul',undefined,'journal-list');memories.forEach(m=>ul.append(el('li',E.text(m.detail,run))));body.append(ul)}
 if(run.history.length)body.append(el('h3','Your decisions'));const ul=el('ul',undefined,'journal-list');run.history.forEach(h=>ul.append(el('li','Chapter '+h.chapter+' · '+h.label)));body.append(ul);
})}
function chapterRead(number){
 openPanel('Chapter '+number,body=>{body.className='chapter-read';const pages=run.pages.filter(p=>p.chapter===number);for(const p of pages)for(const t of p.text)body.append(el('p',E.text(t,run)));});
}
function chapters(){openPanel('Chapters',body=>{
 body.className='';
 for(const c of story.chapters){
  const reached=run.checkpoints.some(x=>x.chapter===c.number),completed=run.chapters.includes(c.number),row=el('div',undefined,'chapter-row');
  row.append(el('div',reached?c.number+'. '+c.title:'Chapter '+c.number,'chapter-title'));
  row.append(el('small',completed?'Completed':reached?'In progress':'Not reached'));
  if(completed)row.append(button('Read chapter',()=>chapterRead(c.number)));
  if(reached)row.append(button('Replay in a new playthrough',()=>{snapshot();write();run=E.fork(story,run,c.number,'From chapter '+c.number+' · '+(book.playthroughs.length+1));write();ui.dialog.close();render()}));
  body.append(row);
 }
})}
$('readPrevious').addEventListener('click',previousPage);$('readContinue').addEventListener('click',continuePage);
ui.story.addEventListener('pointerup',revealAll);
document.addEventListener('keydown',e=>{if(ui.dialog.open||e.repeat||e.altKey||e.ctrlKey||e.metaKey)return;if(['BUTTON','INPUT','SELECT','TEXTAREA','SUMMARY'].includes(document.activeElement?.tagName))return;if(e.key===' '||e.key==='Enter'){if(revealing){e.preventDefault();revealAll()}}});
ui.pace.addEventListener('change',()=>{try{storage.setItem('night-play-fiction-reading-speed',ui.pace.value)}catch{}if(run){snapshot();render(true)}});
$('sound').addEventListener('change',e=>{soundEnabled=e.target.checked;addAmbient();if(soundEnabled)bell()});
$('openCharacter').addEventListener('click',character);$('openSaves').addEventListener('click',saves);$('openJournal').addEventListener('click',journal);$('openChapters').addEventListener('click',chapters);
ui.dialog.querySelector('.close-dialog').addEventListener('click',()=>{ui.dialog.close();ui.body.className=''});
ui.dialog.addEventListener('click',e=>{if(e.target===ui.dialog){const r=ui.dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)ui.dialog.close()}});
addEventListener('scroll',()=>{if(!ui.dialog.open)snapshot()},{passive:true});
addEventListener('pagehide',()=>{snapshot();clearTimeout(writeTimer);write();stopAmbient()});
document.addEventListener('visibilitychange',()=>{if(document.hidden){snapshot();clearTimeout(writeTimer);write();stopAmbient()}else if(soundEnabled)addAmbient()});
try{
 const contentKey=new URLSearchParams(location.search).get('story')||'last-call';if(!/^[a-z0-9-]+$/.test(contentKey))throw Error('Unknown story');
 const response=await fetch('/stories/'+contentKey+'.json');if(!response.ok)throw Error('Story file unavailable');story=await response.json();E.validateStory(story);document.title=story.title+' · NIGHT Play';document.querySelector('h1').textContent=story.title;
 const pace=storage.getItem('night-play-fiction-reading-speed');if(['cinematic','quick','instant'].includes(pace))ui.pace.value=pace;
 book=E.load(story,storage);for(const id of ['openCharacter','openSaves','openJournal','openChapters','pace','sound'])$(id).disabled=false;
 if(book.recovery){try{const raw=storage.getItem('night-story:'+story.id+':v3');if(raw)storage.setItem('night-story:'+story.id+':recovery:'+Date.now(),raw)}catch{persistBlocked=true}warn(persistBlocked?'A save could not be safely archived. It remains untouched; this recovery session is not being written over it.':'A save could not be opened. Its original data has been retained; usable playthroughs remain available.');}
 run=book.playthroughs.find(r=>r.id===book.active);
 if(run){write();render(true)}else{$('readingNav').hidden=true;ui.story.replaceChildren();for(const id of ['openCharacter','openJournal','openChapters'])$(id).disabled=true;ui.choices.hidden=true;setup(ui.story);$('chapterStatus').textContent='The Last Call';}

}catch(error){ui.story.replaceChildren(el('p','The story could not be opened. Your existing saves have not been changed.'));ui.choices.classList.remove('pending');ui.choices.append(button('Try again',()=>location.reload()));console.error(error)}
})();
