(async function(){
'use strict';
const E=window.NightStory,$=id=>document.getElementById(id);
const ui={story:$('story'),choices:$('choices'),pace:$('pace'),menu:$('readerOptions'),dialog:$('storyDialog'),body:$('dialogBody'),title:$('dialogTitle'),warning:$('saveWarning'),recap:$('recap')};
const el=(tag,text,className)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(className)n.className=className;return n};
let storage;try{storage=window.localStorage;storage.getItem('night-story-check')}catch{const m=new Map();storage={getItem:k=>m.get(k)||null,setItem:(k,v)=>m.set(k,v)};warn('Browser storage is unavailable. This session cannot be resumed after closing.');}
let story,book,run,timers=[],generation=0,lines=[],ready=false,revealing=false,revealGuard=0,writeTimer,soundContext,soundEnabled=false,ambient=[],persistBlocked=false;
const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
function warn(text){ui.warning.textContent=text;ui.warning.hidden=false}
function button(text,fn){const b=el('button',text);b.type='button';b.addEventListener('click',fn);return b}
function later(fn,ms){const t=setTimeout(fn,ms);timers.push(t);return t}
function cancel(){generation++;timers.forEach(clearTimeout);timers=[];revealing=false}
function write(){
 if(!run)return;
 book=E.storeRun(story,book,run);
 try{if(!persistBlocked)E.persist(story,book,storage)}catch{warn('Your latest reading is still open, but autosave could not be written. Free some browser storage before closing.');}
}
function saveSoon(){clearTimeout(writeTimer);writeTimer=setTimeout(write,180)}
function snapshot(){if(!run)return;run.reading.scroll=scrollY;run.reading.ready=ready;saveSoon()}
function addAmbient(){
 stopAmbient();if(!soundEnabled||document.hidden)return;
 try{
  soundContext=soundContext||new (window.AudioContext||window.webkitAudioContext)();soundContext.resume();
  for(const [f,g] of [[110,.009],[164.81,.006]]){const o=soundContext.createOscillator(),a=soundContext.createGain();o.type='sine';o.frequency.value=f;a.gain.value=g;o.connect(a).connect(soundContext.destination);o.start();ambient.push({o,a})}
 }catch{soundEnabled=false;$('sound').checked=false}
}
function stopAmbient(){for(const x of ambient){try{x.o.stop();x.o.disconnect();x.a.disconnect()}catch{}}ambient=[]}
function bell(){if(!soundEnabled||!soundContext)return;const o=soundContext.createOscillator(),g=soundContext.createGain(),t=soundContext.currentTime;o.frequency.value=523.25;g.gain.setValueAtTime(.025,t);g.gain.exponentialRampToValueAtTime(.0001,t+1.4);o.connect(g).connect(soundContext.destination);o.start();o.stop(t+1.5)}
function setLine(line){line.classList.remove('go');line.classList.add('settled')}
function markRead(index){
 setLine(lines[index]);
 let count=0;for(const line of lines){if(!line.classList.contains('settled'))break;count++}
 run.reading.revealed=count;saveSoon();
}
function displayRecap(){
 ui.recap.replaceChildren();ui.recap.hidden=false;
 ui.recap.append(el('h2','YOUR STORY'),el('h3',story.endings[run.ending]));
 const list=el('ul');E.summary(story,run).forEach(t=>list.append(el('li',t)));ui.recap.append(list);
 ui.recap.append(el('p',book.endings.length+' of '+Object.keys(story.endings).length+' endings discovered in your playthroughs.'));
 ui.recap.append(button('Start a separate playthrough',()=>newRun()));
 const link=el('a','Story library');link.href='/stories/library.html';ui.recap.append(link);
}
function showChoices(){
 if(ready)return;ready=true;revealing=false;run.reading.ready=true;run.reading.revealed=lines.length;
 ui.choices.classList.remove('pending');for(const b of ui.choices.querySelectorAll('button'))b.disabled=false;
 if(run.ending){ui.choices.hidden=true;displayRecap();bell()}
 write();
}
function revealAll(){
 if(!revealing)return;
 cancel();lines.forEach(setLine);revealGuard=performance.now()+280;run.reading.revealed=lines.length;showChoices();
}
function makeLine(beat){
 const p=el('p',undefined,'step');p.setAttribute('aria-label',beat.text);
 for(const part of beat.text.split(/(\s+)/)){if(!part)continue;if(/^\s+$/.test(part))p.append(document.createTextNode(part));else{const span=el('span',part,'word');span.setAttribute('aria-hidden','true');p.append(span)}}
 return p;
}
function resolveBlankPages(){let guard=0;while(!E.beats(story,run).length&&guard++<500){const c=E.choices(story,run);if(c.length!==1||!c[0].continue)break;run=E.choose(story,run,c[0].id)}}
function render(restore=false){
 cancel();resolveBlankPages();ready=false;const epoch=generation,node=story.nodes[run.node],bs=E.beats(story,run);
 ui.menu.open=false;ui.recap.hidden=true;ui.choices.hidden=false;ui.story.setAttribute('aria-label','Chapter '+node.chapter+': '+node.title);
 $('chapterStatus').textContent='Chapter '+node.chapter+' of '+story.chapters.length+' · '+node.title;
 document.documentElement.dataset.atmosphere=['c27','c28','c29'].some(x=>run.node.startsWith(x))?'threshold':node.chapter<4?'unease':'quiet';
 lines=bs.map(makeLine);ui.story.replaceChildren(...lines);ui.choices.replaceChildren();ui.choices.classList.add('pending');
 const saved=restore?Math.min(run.reading.revealed||0,lines.length):0;
 lines.slice(0,saved).forEach(setLine);
 for(const [i,c] of E.choices(story,run).entries()){
  const b=el('button',undefined,'choice');b.type='button';b.disabled=true;b.dataset.choice=c.id;
  b.append(el('span',String(i+1),'choice-number'),el('span',c.label,'choice-label'));
  b.addEventListener('click',()=>{if(!ready||performance.now()<revealGuard)return;run=E.choose(story,run,c.id);write();render();scrollTo({top:0,behavior:'instant'})});ui.choices.append(b);
 }
 if(restore)requestAnimationFrame(()=>scrollTo({top:run.reading.scroll||0,behavior:'instant'}));else scrollTo({top:0,behavior:'instant'});
 if(reduced||ui.pace.value==='instant'||(restore&&run.reading.ready)){lines.forEach(setLine);showChoices();return}
 revealing=true;const factor=ui.pace.value==='quick'?.45:1;let end=0;
 bs.forEach((beat,index)=>{
  if(index<saved)return;
  const line=lines[index],words=line.querySelectorAll('.word');
  const offset=run.node==='start'?0:Math.round((index-saved)*135*factor+(beat.pause||0)*factor);
  const spread=Math.round(Math.min(650,Math.max(220,words.length*7))*factor),fade=Math.round(900*factor);
  line.style.setProperty('--fade',fade+'ms');
  // Stable positions; stagger the ink, never characters or paragraph geometry.
  words.forEach((word,j)=>word.style.animationDelay=Math.round(((j*67+index*43)%Math.max(1,spread)))+'ms');
  later(()=>{if(epoch===generation)line.classList.add('go')},offset);
  later(()=>{if(epoch===generation)markRead(index)},offset+spread+fade);
  end=Math.max(end,offset+spread+fade);
 });
 later(()=>{if(epoch===generation){lines.forEach(setLine);showChoices()}},end+20);
 write();
}
function openPanel(title,build){snapshot();ui.menu.open=false;ui.title.textContent=title;ui.body.replaceChildren();ui.body.className='';build(ui.body);ui.dialog.showModal()}
function newRun(){snapshot();write();run=E.start(story,'Playthrough '+(book.playthroughs.length+1));write();ui.dialog.close();render()}
function saves(){openPanel('Your playthroughs',body=>{
 body.append(button('Start a new playthrough',()=>newRun()));
 for(const r of [...book.playthroughs].sort((a,b)=>b.updated-a.updated)){
  const row=el('div',undefined,'save-row');row.append(el('strong',r.label));if(r.id===run.id)row.append(el('span','Open','active-save'));
  const n=story.nodes[r.node];row.append(el('small',(r.ending?story.endings[r.ending]:'Chapter '+n.chapter+' · '+n.title)+' · '+new Date(r.updated).toLocaleString()));
  if(r.id!==run.id)row.append(button(r.ending?'View ending':'Resume',()=>{snapshot();write();run=E.clone(book.playthroughs.find(x=>x.id===r.id));book.active=run.id;write();ui.dialog.close();render(true)}));body.append(row);
 }
 try{if(storage.getItem('night-play-last-call-v2')){const p=el('p');const a=el('a','Resume your earlier short edition');a.href='/stories/short-edition.html';p.append(a);body.append(p)}}catch{}
 body.append(el('p','Playthroughs are saved in this browser. Starting another keeps the earlier one.'));
})}
function journal(){openPanel('Story journal',body=>{
 const discovered=run.state.knowledge.map(k=>story.knowledge[k]).filter(Boolean);
 body.append(el('h3','Discovered knowledge'));
 if(!discovered.length)body.append(el('p','Discoveries appear here as you read.'));
 for(const k of discovered){const row=el('div',undefined,'journal-row');row.append(el('strong',k.title),el('p',k.text));body.append(row)}
 body.append(el('h3','What you carry'));const items=el('ul',undefined,'journal-list');run.state.inventory.forEach(k=>items.append(el('li',story.items[k]||k)));body.append(items);
 body.append(el('h3','What people remember'));
 for(const [name,memories] of Object.entries(run.state.memories)){body.append(el('strong',name));const ul=el('ul',undefined,'journal-list');memories.forEach(m=>ul.append(el('li',m.detail)));body.append(ul)}
 body.append(el('h3','Your decisions'));const ul=el('ul',undefined,'journal-list');run.history.forEach(h=>ul.append(el('li','Chapter '+h.chapter+' · '+h.label)));body.append(ul);
})}
function chapterRead(number){
 openPanel('Chapter '+number,body=>{body.className='chapter-read';const pages=run.pages.filter(p=>p.chapter===number);for(const p of pages)for(const t of p.text)body.append(el('p',t));});
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
ui.story.addEventListener('pointerup',revealAll);
document.addEventListener('keydown',e=>{if(ui.dialog.open||e.repeat||e.altKey||e.ctrlKey||e.metaKey)return;if(['BUTTON','INPUT','SELECT','TEXTAREA','SUMMARY'].includes(document.activeElement?.tagName))return;if(e.key===' '||e.key==='Enter'){if(revealing){e.preventDefault();revealAll()}}});
ui.pace.addEventListener('change',()=>{try{storage.setItem('night-play-fiction-reading-speed',ui.pace.value)}catch{}snapshot();render(true)});
$('sound').addEventListener('change',e=>{soundEnabled=e.target.checked;addAmbient();if(soundEnabled)bell()});
$('openSaves').addEventListener('click',saves);$('openJournal').addEventListener('click',journal);$('openChapters').addEventListener('click',chapters);
ui.dialog.querySelector('.close-dialog').addEventListener('click',()=>{ui.dialog.close();ui.body.className=''});
ui.dialog.addEventListener('click',e=>{if(e.target===ui.dialog){const r=ui.dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)ui.dialog.close()}});
addEventListener('scroll',()=>{if(!ui.dialog.open)snapshot()},{passive:true});
addEventListener('pagehide',()=>{snapshot();clearTimeout(writeTimer);write();stopAmbient()});
document.addEventListener('visibilitychange',()=>{if(document.hidden){snapshot();clearTimeout(writeTimer);write();stopAmbient()}else if(soundEnabled)addAmbient()});
try{
 const response=await fetch('/stories/last-call.json');if(!response.ok)throw Error('Story file unavailable');story=await response.json();E.validateStory(story);
 const pace=storage.getItem('night-play-fiction-reading-speed');if(['cinematic','quick','instant'].includes(pace))ui.pace.value=pace;
 book=E.load(story,storage);
 if(book.recovery){try{const raw=storage.getItem('night-story:'+story.id+':v3');if(raw)storage.setItem('night-story:'+story.id+':recovery:'+Date.now(),raw)}catch{persistBlocked=true}warn(persistBlocked?'A save could not be safely archived. It remains untouched; this recovery session is not being written over it.':'A save could not be opened. Its original data has been retained; usable playthroughs remain available.');}
 run=book.playthroughs.find(r=>r.id===book.active)||E.start(story,'Playthrough 1');write();render(true);
}catch(error){ui.story.replaceChildren(el('p','The story could not be opened. Your existing saves have not been changed.'));ui.choices.classList.remove('pending');ui.choices.append(button('Try again',()=>location.reload()));console.error(error)}
})();
