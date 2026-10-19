(()=>{'use strict';
const box=document.getElementById('content'),toast=document.getElementById('toast');
const MODES={
 wyr:['Would You Rather','Pick between two difficult choices.'],
 mlt:['Most Likely To','Find out who fits the question.'],
 tot:['This or That','Make your pick, then see everyone else’s.'],
 nhie:['Never Have I Ever','Discover what your friends have tried.'],
 trivia:['Trivia','Answer together. Compete for the highest score.'],
 quiz:['Who Knows Me Best?','Write a quiz about yourself and invite your friends.']
};
const DRAFT_KEY='night-play-personal-quiz-v1';
const h=v=>String(v??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
let draft;try{draft=JSON.parse(localStorage.getItem(DRAFT_KEY))}catch{}
if(!draft||!Array.isArray(draft.questions)||draft.questions.length<3||draft.questions.length>12)draft={
 questions:[
  {prompt:'Where would I rather spend a weekend?',options:['At the beach','In the mountains'],correct:0},
  {prompt:'What would I choose for a quiet evening?',options:['A movie','A good book'],correct:0},
  {prompt:'Which adventure would I pick?',options:['A road trip','A flight somewhere new'],correct:1}
 ]};
function persist(){try{localStorage.setItem(DRAFT_KEY,JSON.stringify(draft))}catch{}}
function notify(message){toast.textContent=message;clearTimeout(notify.timer);notify.timer=setTimeout(()=>toast.textContent='',4500)}
function heading(name,description,label='PARTY GAMES'){
 document.getElementById('title').textContent=name;
 document.getElementById('subtitle').textContent=description;
 document.getElementById('kicker').textContent=label;
 document.title=name+' · NIGHT Play';
}
const icon=id=>'<svg class="party-art-svg" aria-hidden="true"><use href="#party-icon-'+id+'"></use></svg>';
function hub(){
 heading('Play together.','Choose a game, host a room and invite your friends.');
 box.className='party-content party-hub';
 box.innerHTML='<div class="party-game-grid">'+Object.entries(MODES).map(([id,m])=>
  '<a class="party-game-card mode-'+id+'" href="'+(id==='quiz'?'/party/?game=quiz':'/party/room/?game='+id)+'" aria-label="'+h(m[0])+'">'+
  '<span class="party-game-art">'+icon(id)+'</span><span class="party-card-copy"><strong>'+h(m[0])+'</strong><small>'+h(m[1])+'</small></span></a>'
 ).join('')+'</div>';
}
function questionMarkup(q,i){
 const canRemove=draft.questions.length>3;
 return '<article class="party-editor-question"><div class="party-editor-qhead"><strong>Question '+(i+1)+'</strong>'+
 (canRemove?'<button type="button" class="party-remove-question" data-remove="'+i+'" aria-label="Remove question '+(i+1)+'"><svg class="party-inline-icon" aria-hidden="true"><use href="#party-icon-trash"></use></svg></button>':'')+
 '</div><label class="party-field">Question<input class="party-input" data-q="'+i+'" data-field="prompt" maxlength="180" required value="'+h(q.prompt)+'" placeholder="Ask your friends something about you"></label>'+
 '<div class="party-two-fields">'+q.options.map((value,j)=>'<label class="party-field">Option '+(j+1)+
 '<input class="party-input" data-q="'+i+'" data-field="option-'+j+'" maxlength="90" required value="'+h(value)+'" placeholder="Enter an answer"></label>').join('')+'</div>'+
 '<fieldset class="party-correct"><legend>Your answer</legend>'+
 q.options.map((_,j)=>'<label class="party-answer-pill"><input type="radio" name="correct-'+i+'" data-correct="'+i+'" value="'+j+'" '+(q.correct===j?'checked':'')+'><span>Option '+(j+1)+'</span></label>').join('')+
 '</fieldset></article>';
}
function valid(){
 return draft.questions.length>=3&&draft.questions.length<=10&&draft.questions.every(q=>q&&
  typeof q.prompt==='string'&&q.prompt.trim().length>=3&&q.prompt.length<=180&&
  Array.isArray(q.options)&&q.options.length===2&&
  q.options.every(o=>typeof o==='string'&&o.trim().length>0&&o.length<=90)&&[0,1].includes(q.correct));
}
function editor(){
 heading('Who knows you best?','Write the questions. Friends answer through your invitation link.','CREATE A QUIZ');
 box.className='party-content';
 box.innerHTML='<div class="party-editor-head"><a href="/party/" class="party-text-btn">All games</a>'+
 '<span>'+draft.questions.length+' / 10 questions</span></div>'+
 '<section class="party-editor"><div class="party-editor-list">'+draft.questions.map(questionMarkup).join('')+'</div>'+
 (draft.questions.length<10?'<button type="button" class="party-add-question" data-action="add"><svg class="party-inline-icon" aria-hidden="true"><use href="#party-icon-plus"></use></svg>Add question</button>':'')+
 '<button type="button" class="party-primary party-publish" data-action="publish">Create room</button></section>';
}
box.addEventListener('click',e=>{
 const remove=e.target.closest('[data-remove]');
 if(remove){if(draft.questions.length>3){draft.questions.splice(Number(remove.dataset.remove),1);persist();editor()}return}
 const action=e.target.closest('[data-action]')?.dataset.action;
 if(action==='add'){
  if(draft.questions.length>=10)return;
  draft.questions.push({prompt:'',options:['',''],correct:0});persist();editor();
  box.querySelector('[data-q="'+(draft.questions.length-1)+'"][data-field="prompt"]')?.focus();
 }
 if(action==='publish'){
  if(!valid()){notify('Complete all questions and both answer options before creating your room.');return}
  persist();location.assign('/party/room/?game=quiz');
 }
});
box.addEventListener('input',e=>{
 const el=e.target;if(!el.matches('[data-field]'))return;
 const q=draft.questions[Number(el.dataset.q)];if(!q)return;
 if(el.dataset.field==='prompt')q.prompt=el.value;
 else if(el.dataset.field.startsWith('option-'))q.options[Number(el.dataset.field.slice(-1))]=el.value;
 persist();
});
box.addEventListener('change',e=>{
 const el=e.target;if(!el.matches('[data-correct]'))return;
 const q=draft.questions[Number(el.dataset.correct)];
 if(q){q.correct=Number(el.value);persist()}
});
window.__nightPartyTest={snapshot:()=>({phase:location.search.includes('game=quiz')?'editor':'hub',draft:JSON.parse(JSON.stringify(draft))}),valid};
if(new URLSearchParams(location.search).get('game')==='quiz')editor();else hub();
})();