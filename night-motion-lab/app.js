import {catalog, categoryMeta} from './animations/catalog.js';
import {runMotion, pauseMotion, playMotion, stopMotion} from './animations/engine.js';

const $ = (s,root=document)=>root.querySelector(s);
const $$ = (s,root=document)=>[...root.querySelectorAll(s)];
const gallery=$('#gallery'), filters=$('#categoryFilters'), toast=$('#toast');
const STORAGE='night-motion-lab-v1';
const state={
  category:'all', view:'all', query:'', speed:1, playing:true, globalLoop:false,
  ratings:{}, compare:new Set(), visible:new Set(), reduced:matchMedia('(prefers-reduced-motion: reduce)').matches,
  override:false
};
try{Object.assign(state.ratings,JSON.parse(localStorage.getItem(STORAGE)||'{}'));}catch{}
const metaMap=new Map(categoryMeta.map(x=>[x[0],x]));

function save(){localStorage.setItem(STORAGE,JSON.stringify(state.ratings));}
function announce(msg){toast.textContent=msg;toast.classList.add('show');clearTimeout(announce.t);announce.t=setTimeout(()=>toast.classList.remove('show'),1600);}
function labelFor(cat){return metaMap.get(cat)?.[1]||cat;}
function selectedItems(){return catalog.filter(x=>state.ratings[x.id]==='like'||state.ratings[x.id]==='maybe');}
function updateSummary(){
  const like=Object.values(state.ratings).filter(x=>x==='like').length;
  const maybe=Object.values(state.ratings).filter(x=>x==='maybe').length;
  const no=Object.values(state.ratings).filter(x=>x==='no').length;
  $('#selectionSummary').textContent=like+' LIKE · '+maybe+' MAYBE · '+no+' NO';
  $('#compareCount').textContent=state.compare.size;
}
function passes(item){
  if(state.category!=='all'&&item.category!==state.category)return false;
  const q=state.query.trim().toLowerCase();
  if(q&&!((item.id+' '+item.name+' '+item.description).toLowerCase().includes(q)))return false;
  const r=state.ratings[item.id];
  if(state.view==='selected'&&!(r==='like'||r==='maybe'))return false;
  if(state.view==='likes'&&r!=='like')return false;
  return true;
}
function card(item){
  const el=document.createElement('article');el.className='motion-card';el.dataset.id=item.id;
  el.innerHTML=
    '<div class="card-head"><div><span class="card-id">'+item.id+'</span><h3 class="card-title">'+item.name+'</h3></div><span class="category-label">'+labelFor(item.category)+'</span></div>'+
    '<div class="card-stage-wrap"><div class="animation-stage" tabindex="0" role="button" aria-label="Preview '+item.id+' '+item.name+'"></div></div>'+
    '<p class="card-description">'+item.description+'</p>'+
    '<div class="card-actions"><button class="replay">Replay</button><button class="preview">Preview</button><label><input class="loop-toggle" type="checkbox"> Loop</label><label><input class="compare-toggle" type="checkbox"> Compare</label></div>'+
    '<div class="rating-row"><button data-rating="like">LIKE</button><button data-rating="maybe">MAYBE</button><button data-rating="no">NO</button></div>';
  const stage=$('.animation-stage',el);
  el._item=item;el._stage=stage;
  const replay=()=>runCard(el,true);
  $('.replay',el).addEventListener('click',replay);
  $('.preview',el).addEventListener('click',()=>openPreview(item,$('.loop-toggle',el).checked));
  stage.addEventListener('click',()=>openPreview(item,$('.loop-toggle',el).checked));
  stage.addEventListener('keydown',ev=>{if(ev.key==='Enter'||ev.key===' '){ev.preventDefault();openPreview(item,$('.loop-toggle',el).checked);}});
  $('.loop-toggle',el).addEventListener('change',()=>runCard(el,true));
  $('.compare-toggle',el).addEventListener('change',ev=>{
    if(ev.target.checked){
      if(state.compare.size>=4){ev.target.checked=false;announce('Comparison is limited to 4 concepts.');return;}
      state.compare.add(item.id);
    } else state.compare.delete(item.id);
    updateSummary();
  });
  $$('.rating-row button',el).forEach(b=>b.addEventListener('click',()=>{
    const r=b.dataset.rating;
    state.ratings[item.id]=state.ratings[item.id]===r?null:r;
    if(!state.ratings[item.id])delete state.ratings[item.id];
    save();syncRating(el);updateSummary();
    if(state.view!=='all')applyFilters();
  }));
  syncRating(el);observer.observe(el);return el;
}
function syncRating(el){
  const r=state.ratings[el.dataset.id];
  $$('.rating-row button',el).forEach(b=>b.classList.toggle('selected',b.dataset.rating===r));
}
function runCard(el,force=false){
  if(!force&&!state.visible.has(el.dataset.id))return;
  if(state.reduced&&!state.override){stopMotion(el._stage);return;}
  runMotion(el._stage,el._item,{speed:state.speed,loop:state.globalLoop||$('.loop-toggle',el).checked});
  if(!state.playing)pauseMotion(el._stage);
}
const observer=new IntersectionObserver(entries=>{
  for(const entry of entries){
    const el=entry.target;
    if(entry.isIntersecting){
      state.visible.add(el.dataset.id);
      if(state.playing)runCard(el);
    }else{
      state.visible.delete(el.dataset.id);pauseMotion(el._stage);
    }
  }
},{rootMargin:'120px 0px',threshold:.05});

function renderGallery(){
  gallery.innerHTML='';
  catalog.forEach(item=>gallery.append(card(item)));
  applyFilters();updateSummary();
}
function applyFilters(){
  let count=0;
  $$('.motion-card',gallery).forEach(el=>{
    const show=passes(el._item);
    el.hidden=!show;if(show)count++;
    if(!show)pauseMotion(el._stage);else if(state.visible.has(el.dataset.id)&&state.playing)runCard(el);
  });
  $('#visibleCount').textContent=count;$('#emptyState').hidden=count!==0;
}
function buildFilters(){
  const all=document.createElement('button');all.textContent='All · '+catalog.length;all.className='active';all.dataset.category='all';filters.append(all);
  categoryMeta.forEach(([id,label])=>{
    const b=document.createElement('button');b.dataset.category=id;b.textContent=label+' · '+catalog.filter(x=>x.category===id).length;filters.append(b);
  });
  filters.addEventListener('click',ev=>{
    const b=ev.target.closest('button[data-category]');if(!b)return;
    state.category=b.dataset.category;$$('button',filters).forEach(x=>x.classList.toggle('active',x===b));applyFilters();
  });
}

function openPreview(item,loop=false){
  const d=$('#previewDialog');d.dataset.id=item.id;
  $('#previewCategory').textContent=item.id+' · '+labelFor(item.category);
  $('#previewTitle').textContent=item.name;$('#previewDescription').textContent=item.description;
  $('#previewLoop').checked=loop;d.showModal();runMotion($('#previewStage'),item,{speed:state.speed,loop});
}
$('#previewReplay').addEventListener('click',()=>{const item=catalog.find(x=>x.id===$('#previewDialog').dataset.id);if(item)runMotion($('#previewStage'),item,{speed:state.speed,loop:$('#previewLoop').checked});});
$('#previewLoop').addEventListener('change',()=>$('#previewReplay').click());
$('#previewDialog').addEventListener('close',()=>stopMotion($('#previewStage')));

function openCompare(){
  const items=catalog.filter(x=>state.compare.has(x.id));
  if(items.length<2){announce('Choose 2–4 cards with Compare first.');return;}
  const grid=$('#compareGrid');grid.innerHTML='';
  items.forEach(item=>{
    const wrap=document.createElement('div');wrap.className='compare-item';wrap.dataset.id=item.id;
    wrap.innerHTML='<div class="animation-stage"></div><div class="compare-label"><b>'+item.id+'</b> · '+item.name+'</div>';
    grid.append(wrap);
  });
  $('#compareDialog').showModal();replayCompare();
}
function replayCompare(){
  $$('.compare-item').forEach(w=>{
    const item=catalog.find(x=>x.id===w.dataset.id);
    runMotion($('.animation-stage',w),item,{speed:state.speed,loop:state.globalLoop});
  });
}
$('#openCompare').addEventListener('click',openCompare);
$('#compareReplay').addEventListener('click',replayCompare);
$('#compareClear').addEventListener('click',()=>{
  state.compare.clear();$$('.compare-toggle').forEach(x=>x.checked=false);updateSummary();$('#compareDialog').close();
});
$('#compareDialog').addEventListener('close',()=>$$('#compareGrid .animation-stage').forEach(stopMotion));

$('#playAll').addEventListener('click',()=>{state.playing=true;$$('.motion-card:not([hidden])').forEach(el=>{if(state.visible.has(el.dataset.id))playMotion(el._stage);});});
$('#pauseAll').addEventListener('click',()=>{state.playing=false;$$('.motion-card').forEach(el=>pauseMotion(el._stage));});
$('#replayAll').addEventListener('click',()=>{state.playing=true;$$('.motion-card:not([hidden])').forEach(el=>{if(state.visible.has(el.dataset.id))runCard(el,true);});});
$('#globalLoop').addEventListener('change',ev=>{state.globalLoop=ev.target.checked;$$('.motion-card:not([hidden])').forEach(el=>{if(state.visible.has(el.dataset.id))runCard(el,true);});});
$$('[data-speed]').forEach(b=>b.addEventListener('click',()=>{
  state.speed=Number(b.dataset.speed);$$('[data-speed]').forEach(x=>x.classList.toggle('active',x===b));
  $$('.motion-card:not([hidden])').forEach(el=>{if(state.visible.has(el.dataset.id))runCard(el,true);});
}));
$('#backgroundMode').addEventListener('change',ev=>document.body.dataset.bg=ev.target.value);
$('#motionOverride').addEventListener('change',ev=>{state.override=ev.target.checked;document.body.classList.toggle('motion-override',state.override);if(state.override)$('#replayAll').click();else if(state.reduced)$('#pauseAll').click();});
$('#searchInput').addEventListener('input',ev=>{state.query=ev.target.value;applyFilters();});

function viewButton(mode,button){
  state.view=mode;$$('.selection-tools > button').slice(0,3).forEach(x=>x.classList.toggle('active',x===button));applyFilters();
}
$('#showAll').addEventListener('click',ev=>viewButton('all',ev.currentTarget));
$('#showSelected').addEventListener('click',ev=>viewButton('selected',ev.currentTarget));
$('#showLikes').addEventListener('click',ev=>viewButton('likes',ev.currentTarget));

function selectedText(){
  const items=selectedItems();
  return items.length?items.map(x=>x.id+' — '+x.name+' — '+String(state.ratings[x.id]).toUpperCase()).join('\n'):'No LIKE/MAYBE selections yet.';
}
$('#copySelected').addEventListener('click',async()=>{
  try{await navigator.clipboard.writeText(selectedText());announce('Selected Night motion IDs copied.');}
  catch{announce('Clipboard unavailable in this browser. Use Export .txt.');}
});
$('#downloadSelected').addEventListener('click',()=>{
  const blob=new Blob([selectedText()+'\n'],{type:'text/plain;charset=utf-8'}),a=document.createElement('a');
  a.href=URL.createObjectURL(blob);a.download='night-motion-shortlist.txt';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);
});

function renderDepth(){
  const defs=[
    ['flat','Nearly flat'],['subtle','Subtle shadow'],['medium','Medium depth'],['raised','Raised'],
    ['inset','Inset'],['directional','Directional'],['layered','Layered'],['rimmed','Rim-highlighted'],['embossed','Dark embossed']
  ];
  $('#depthGrid').innerHTML=defs.map(([cls,name])=>'<article class="depth-card '+cls+'"><h3>'+name+'</h3><div class="depth-sample"><div class="depth-lockup"><img src="./assets/night-logo.svg" alt=""><span>NIGHT</span></div></div></article>').join('');
}

buildFilters();renderDepth();renderGallery();
if(state.reduced&&!state.override){state.playing=false;announce('Reduced motion is active. Use the override to intentionally test animations.');}
