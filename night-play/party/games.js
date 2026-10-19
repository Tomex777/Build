(()=>{'use strict';
const $=id=>document.getElementById(id),box=$('content'),toast=$('toast'),dialog=$('settingsDialog'),packs=window.NIGHTPartyPacks;
const MODES={wyr:['Would You Rather','Impossible choices. See where your friends stand.'],mlt:['Most Likely To','Vote for the person who fits the question.'],tot:['This or That','Fast picks, surprising disagreements.'],nhie:['Never Have I Ever','Find out what everyone has tried.'],trivia:['Trivia','Get the answers right. Take the top spot.'],quiz:['Who Knows Me Best?','Create a quiz, then challenge your friends.']};
const KEY='night-play-party-v1',PREF='night-play-party-pref-v1',DRAFT='night-play-personal-quiz-v1',BIND='night-play-party-verified-player-v1';
const h=v=>String(v??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const read=(k,f)=>{try{return JSON.parse(localStorage.getItem(k))??f}catch{return f}},save=(k,v)=>{try{localStorage.setItem(k,JSON.stringify(v))}catch{}};
const saved=read(KEY,null),query=new URLSearchParams(location.search).get('game');
let selected=MODES[query]?query:MODES[saved?.mode]?saved.mode:null,phase=selected?'setup':'hub',state=null;
let people=Array.isArray(saved?.players)?saved.players.filter(x=>typeof x==='string'&&x.length>0&&x.length<=24).slice(0,8):[];
let rounds=read(PREF,10);if(![5,10].includes(rounds))rounds=10;
let draft=read(DRAFT,null);
let verifiedIdentity=null,linkedPartyPlayer=read(BIND,null);
if(!linkedPartyPlayer||typeof linkedPartyPlayer.id!=='string'||!Number.isInteger(linkedPartyPlayer.index)||linkedPartyPlayer.index<0||linkedPartyPlayer.index>=8)linkedPartyPlayer=null;
const setBinding=value=>{linkedPartyPlayer=value;save(BIND,value)};
if(!draft||!Array.isArray(draft.questions))draft={owner:'',title:'How well do you know me?',questions:[
 {prompt:'Which place would I choose?',options:['Mountains','Beach'],correct:0},
 {prompt:'Which would I rather do?',options:['Go out','Stay in'],correct:1},
 {prompt:'My ideal weekend would be…',options:['An adventure','A quiet afternoon'],correct:0}]};
const validSession=v=>v&&MODES[v.mode]&&['handoff','vote','reveal','finished'].includes(v.phase)&&Array.isArray(v.players)&&v.players.length>=2&&v.players.length<=8&&
 v.players.every(x=>typeof x==='string'&&x.length>0&&x.length<=24)&&Array.isArray(v.questions)&&v.questions.length>=3&&v.questions.length<=20&&
 Array.isArray(v.votes)&&v.votes.length===v.questions.length&&v.votes.every(x=>Array.isArray(x)&&x.length===v.players.length)&&
 Array.isArray(v.points)&&v.points.length===v.players.length&&Number.isInteger(v.index)&&v.index>=0&&v.index<v.questions.length&&
 Number.isInteger(v.voter)&&v.voter>=0&&v.voter<=v.players.length;
if(validSession(saved)&&(!query||query===saved.mode)){selected=saved.mode;phase=saved.phase;state=saved;people=saved.players.slice();}
function persist(){if(state){state.phase=phase;save(KEY,state)}}
function stop(){state=null;try{localStorage.removeItem(KEY)}catch{}}
function note(text){toast.textContent=text;clearTimeout(note.timer);note.timer=setTimeout(()=>toast.textContent='',4200)}
function title(a,b,c='PARTY GAMES'){$('title').textContent=a;$('subtitle').textContent=b;$('kicker').textContent=c;document.title=a+' · NIGHT Play'}
function button(label,action,cls='party-primary',attr=''){return '<button type="button" class="'+cls+'" data-action="'+action+'" '+attr+'>'+label+'</button>'}
function route(v){const u=new URL(location.href);v?u.searchParams.set('game',v):u.searchParams.delete('game');history.replaceState({},'',u.pathname+u.search)}
function hub(){
 title('Party games','Pick a game. Bring your friends.','PLAY TOGETHER');box.className='party-content party-hub';
 const cards=Object.entries(MODES).map(([id,m],i)=>'<button type="button" class="party-game-card mode-'+id+'" data-mode="'+id+'"><span class="party-game-art" aria-hidden="true">'+String(i+1).padStart(2,'0')+'</span><strong>'+h(m[0])+'</strong><small>'+h(m[1])+'</small><span class="party-card-arrow" aria-hidden="true">↗</span></button>').join('');
 box.innerHTML='<div class="party-intro"><span class="party-overline">THE PARTY ROOM</span><h2>Better with company.</h2><p>Quick games, private votes and real results. One phone, passed between friends.</p></div>'+
 '<div class="party-game-grid">'+cards+'</div><a class="party-nomi-bridge" href="https://nomi-tomex777.vercel.app/#/live" target="_blank" rel="noopener noreferrer"><span><strong>Online rooms on Nomi ↗</strong><small>Opens Nomi separately. Its account may be required.</small></span><b aria-hidden="true">↗</b></a>';
}
function setup(){
 const m=MODES[selected];title(m[0],m[1],'LOCAL PARTY');box.className='party-content';
 const players=people.map((x,i)=>'<span class="party-player-pill">'+h(x)+'<button type="button" data-remove="'+i+'" aria-label="Remove '+h(x)+'">×</button></span>').join('');
 box.innerHTML='<div class="party-topline">'+button('‹ All games','hub','party-text-btn')+'<span>2–8 PLAYERS · SAME PHONE</span></div>'+
 '<div class="party-panel"><h2>Who is playing?</h2><p>Each person votes privately, then the group sees the results.</p><div class="party-player-list">'+players+'</div>'+
 '<form id="partyAddPlayer" class="party-player-form"><label class="sr-only" for="playerName">Player name</label><input class="party-input" id="playerName" maxlength="24" placeholder="Enter a player name" value="'+h(verifiedIdentity?.displayName||'')+'" autocomplete="off" required><button type="submit" class="party-add-btn">Add</button></form>'+
 '<p class="party-field-help">'+people.length+' / 8 players · '+(selected==='quiz'?'Create the personal quiz first.':rounds+' questions')+'</p>'+
 (selected==='quiz'?button('Build personal quiz','editor','party-primary','id="quizBuilderButton"'):button('Start '+h(m[0]),'start','party-primary','id="startParty" '+(people.length<2?'disabled':'')))+'</div>';
}
function validQuiz(){return typeof draft.owner==='string'&&draft.owner.trim().length>0&&typeof draft.title==='string'&&draft.title.trim().length>0&&
 draft.questions.length>=3&&draft.questions.length<=12&&draft.questions.every(q=>typeof q.prompt==='string'&&q.prompt.trim().length>=3&&
 Array.isArray(q.options)&&q.options.length===2&&q.options.every(v=>typeof v==='string'&&v.trim().length>0)&&[0,1].includes(q.correct))}
function editor(){
 phase='editor';title('Create a personal quiz','Choose your answers. Your friends try to know you.','WHO KNOWS ME BEST?');box.className='party-content';
 const questions=draft.questions.map((q,i)=>'<article class="party-editor-question"><div class="party-editor-qhead"><strong>Question '+(i+1)+'</strong>'+
 (draft.questions.length>3?button('Remove','remove-question','party-text-btn','data-index="'+i+'"'):'')+'</div>'+
 '<label class="party-field">Question<input class="party-input" data-index="'+i+'" data-field="prompt" maxlength="180" value="'+h(q.prompt)+'" placeholder="Ask about yourself"></label>'+
 '<div class="party-two-fields">'+q.options.map((v,j)=>'<label class="party-field">Option '+(j+1)+'<input class="party-input" data-index="'+i+'" data-field="option-'+j+'" maxlength="90" value="'+h(v)+'"></label>').join('')+'</div>'+
 '<div class="party-correct">Correct answer <label><input type="radio" name="answer-'+i+'" data-correct="'+i+'" value="0" '+(q.correct===0?'checked':'')+'> Option 1</label>'+
 '<label><input type="radio" name="answer-'+i+'" data-correct="'+i+'" value="1" '+(q.correct===1?'checked':'')+'> Option 2</label></div></article>').join('');
 box.innerHTML='<div class="party-topline">'+button('‹ Back','setup','party-text-btn')+'<span>SAVED ON THIS DEVICE</span></div><section class="party-editor">'+
 '<label class="party-field">Your name<input class="party-input" id="quizOwner" maxlength="24" value="'+h(draft.owner)+'" placeholder="Quiz creator"></label>'+
 '<label class="party-field">Quiz title<input class="party-input" id="quizTitle" maxlength="75" value="'+h(draft.title)+'" placeholder="Quiz title"></label>'+
 '<div class="party-editor-list">'+questions+'</div>'+(draft.questions.length<12?button('Add question','add-question','party-secondary'):'')+
 '<p class="party-field-help">This quiz is saved on your phone. Online responses and remote leaderboards are not connected.</p>'+button('Play this quiz','play-quiz')+'</section>';
}
function start(){
 if(people.length<2){note('Add at least two players.');return}
 if(selected==='quiz'&&!validQuiz()){note('Complete your quiz first.');return}
 let questions=selected==='quiz'?draft.questions.map((q,i)=>({id:'personal-'+i,prompt:q.prompt.trim(),options:q.options.map(x=>x.trim()),correct:q.correct})):packs[selected].slice();
 if(selected!=='quiz'){for(let i=questions.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[questions[i],questions[j]]=[questions[j],questions[i]]}questions=questions.slice(0,rounds)}
 state={mode:selected,players:people.slice(),questions,index:0,voter:0,votes:questions.map(()=>Array(people.length).fill(null)),points:Array(people.length).fill(0),owner:selected==='quiz'?draft.owner.trim():'',startedAt:Date.now()};
 phase='handoff';persist();route(selected);render();
}
function progress(){return '<div class="party-progress"><span>ROUND '+(state.index+1)+' / '+state.questions.length+'</span><span>'+h(state.players[state.voter]||'Revealing')+'</span></div>'}
function current(){return state.questions[state.index]}
function choices(){if(state.mode==='mlt')return state.players.map((name,i)=>({value:i,label:name}));
 if(state.mode==='nhie')return [{value:0,label:'I have'},{value:1,label:'Never'}];
 return current().options.map((label,i)=>({value:i,label}))}
function handoff(){
 const name=state.players[state.voter];title(MODES[state.mode][0],'Ready for the next answer?','PRIVATE VOTE');box.className='party-content';
 box.innerHTML=progress()+'<div class="party-handoff"><span class="party-avatar">'+h(name[0].toUpperCase())+'</span><span class="party-overline">PASS THE PHONE</span><h2>'+h(name)+', you’re up.</h2>'+
 '<p>Your choice stays hidden until everybody finishes.</p>'+button('I’m ready','ready')+'</div>';
}
function vote(){
 const q=current(),name=state.players[state.voter],options=choices();title(MODES[state.mode][0],state.mode==='quiz'?'About '+state.owner:'Choose privately.','YOUR TURN');box.className='party-content';
 const entries=options.map((x,i)=>'<button type="button" class="party-vote-option" data-answer="'+x.value+'"><span class="party-choice-id">'+String.fromCharCode(65+i)+'</span><span>'+h(x.label)+'</span></button>').join('');
 box.innerHTML=progress()+'<div class="party-question"><span class="party-overline">'+h(name.toUpperCase())+' · CHOOSE PRIVATELY</span><h2>'+h(q.prompt)+'</h2>'+
 '<div class="party-vote-grid '+(options.length>2?'many':'')+'">'+entries+'</div><small class="party-vote-footnote">One vote each. No one sees your answer yet.</small></div>';
}
function cast(value){
 if(phase!=='vote'||!choices().some(x=>x.value===value))return;
 state.votes[state.index][state.voter]=value;if(['trivia','quiz'].includes(state.mode)&&value===current().correct)state.points[state.voter]++;
 state.voter++;phase=state.voter===state.players.length?'reveal':'handoff';persist();render();
}
function reveal(){
 const q=current(),mode=state.mode,total=state.players.length,votes=state.votes[state.index];
 const labels=mode==='mlt'?state.players:mode==='nhie'?['I have','Never']:q.options;
 const results=labels.map((label,i)=>({label,index:i,count:votes.filter(v=>v===i).length})).filter(x=>mode!=='mlt'||x.count>0).sort((a,b)=>b.count-a.count||a.index-b.index);
 const rows=results.map(x=>{const percent=Math.round(x.count/total*100),correct=['trivia','quiz'].includes(mode)&&x.index===q.correct;
  return '<div class="party-result-row"><div class="party-result-top"><span>'+h(x.label)+(correct?' <small>✓ Correct</small>':'')+'</span><strong>'+x.count+' / '+total+'</strong></div>'+
  '<div class="party-bar"><span style="width:'+percent+'%"></span></div><small>'+percent+'%</small></div>'}).join('');
 title(MODES[mode][0],'Everyone has answered.','THE RESULTS');box.className='party-content';
 box.innerHTML='<div class="party-progress"><span>ROUND '+(state.index+1)+' / '+state.questions.length+'</span><span>ALL VOTES IN</span></div>'+
 '<div class="party-results"><span class="party-overline">THE RESULTS</span><h2>'+h(q.prompt)+'</h2>'+rows+
 (['trivia','quiz'].includes(mode)?'<p class="party-field-help">Correct answers earn one point.</p>':'')+
 button(state.index===state.questions.length-1?'See final results':'Next question','next')+'</div>';
}
function finished(){
 const scored=['trivia','quiz'].includes(state.mode);title('Round complete','Good games. Better company.','PARTY RESULTS');box.className='party-content';
 let text='<div class="party-summary">';
 if(scored){const ranking=state.players.map((name,i)=>({name,score:state.points[i]})).sort((a,b)=>b.score-a.score);
  text+='<h2>Leaderboard</h2><div class="party-leaderboard">'+ranking.map((row,i)=>'<div><span>'+String(i+1).padStart(2,'0')+' · '+h(row.name)+'</span><b>'+row.score+' / '+state.questions.length+'</b></div>').join('')+'</div>';
 }else text+='<h2>That was a good round.</h2><p>'+state.players.length+' people, '+state.questions.length+' questions, and plenty to discuss.</p>';
 box.innerHTML=text+button('Play again','replay')+button('More party games','hub','party-secondary')+'</div>';
}
function render(){if(phase==='hub')hub();else if(phase==='setup')setup();else if(phase==='editor')editor();else if(phase==='handoff')handoff();else if(phase==='vote')vote();else if(phase==='reveal')reveal();else if(phase==='finished')finished()}
/* The verified name is an account field, not a cosmetic label. Update the
 * actual player rows in saved local rounds and the authored quiz.
 * Other players on the shared device are never renamed by accident.
 */
function adoptIdentity(snapshot){
 if(snapshot.status!=='linked'||!snapshot.profile)return;
 const next={id:snapshot.profile.id,displayName:snapshot.profile.displayName.trim().slice(0,24)};
 if(!next.displayName)return;
 verifiedIdentity=next;
 let changed=false;
 if(linkedPartyPlayer?.id!==next.id){
   // Never rename the previous WhatsApp account's local game entry on a device switch.
   setBinding(null);
   if(phase==='setup'&&people.length===0){
     people.push(next.displayName);setBinding({id:next.id,index:0,name:next.displayName});changed=true;
   }
 }else{
   const idx=linkedPartyPlayer.index,old=linkedPartyPlayer.name;
   if(people[idx]===old&&old!==next.displayName){people[idx]=next.displayName;changed=true}
   if(state?.players?.[idx]===old&&old!==next.displayName){state.players[idx]=next.displayName;changed=true}
   if(draft.owner===old&&old!==next.displayName){draft.owner=next.displayName;save(DRAFT,draft);changed=true}
   if(state?.owner===old&&old!==next.displayName){state.owner=next.displayName;changed=true}
   setBinding({...linkedPartyPlayer,name:next.displayName});
 }
 if(!draft.owner&&phase==='setup'){draft.owner=next.displayName;save(DRAFT,draft)}
 if(changed&&state)persist();
 if(phase==='setup'&&document.activeElement?.id==='playerName')return;
 if(changed||phase==='setup'||phase==='editor'||phase==='handoff'||phase==='vote'||phase==='reveal'||phase==='finished')render();
}
function chooseMode(mode){if(!MODES[mode])return;stop();selected=mode;phase='setup';route(mode);render()}
function action(type,src){
 switch(type){
 case 'hub':stop();selected=null;phase='hub';route(null);render();break;
 case 'setup':phase='setup';render();break;
 case 'editor':editor();break;
 case 'start':start();break;
 case 'play-quiz':if(!validQuiz())note('Complete your quiz first.');else{save(DRAFT,draft);start()}break;
 case 'ready':phase='vote';persist();render();break;
 case 'next':if(state.index+1<state.questions.length){state.index++;state.voter=0;phase='handoff'}else phase='finished';persist();render();break;
 case 'replay':stop();phase='setup';render();break;
 case 'add-question':if(draft.questions.length<12){draft.questions.push({prompt:'',options:['',''],correct:0});save(DRAFT,draft);editor()}break;
 case 'remove-question':if(draft.questions.length>3){draft.questions.splice(Number(src.dataset.index),1);save(DRAFT,draft);editor()}break;
 }}
box.addEventListener('click',e=>{
 const mode=e.target.closest('[data-mode]');if(mode){chooseMode(mode.dataset.mode);return}
 const remove=e.target.closest('[data-remove]');if(remove&&phase==='setup'){
   const index=Number(remove.dataset.remove);
   people.splice(index,1);
   if(linkedPartyPlayer){if(index===linkedPartyPlayer.index)setBinding(null);
     else if(index<linkedPartyPlayer.index)setBinding({...linkedPartyPlayer,index:linkedPartyPlayer.index-1})}
   render();return}
 const answer=e.target.closest('[data-answer]');if(answer&&phase==='vote'){cast(Number(answer.dataset.answer));return}
 const target=e.target.closest('[data-action]');if(target)action(target.dataset.action,target);
});
box.addEventListener('submit',e=>{
 if(e.target.id!=='partyAddPlayer')return;e.preventDefault();const field=e.target.querySelector('input'),name=field.value.replace(/\s+/g,' ').trim().slice(0,24);
 if(!name){note('Enter a name.');return}
 if(people.some(v=>v.toLowerCase()===name.toLowerCase())){note('That name is already playing.');return}
 if(people.length>=8){note('Maximum eight players on one phone.');return}
 people.push(name);
 if(verifiedIdentity&&name===verifiedIdentity.displayName&&!linkedPartyPlayer)
   setBinding({id:verifiedIdentity.id,index:people.length-1,name});
 render();box.querySelector('#playerName')?.focus();
});
box.addEventListener('input',e=>{
 const el=e.target;if(!el.matches('[data-field],#quizOwner,#quizTitle'))return;
 if(el.id==='quizOwner')draft.owner=el.value;else if(el.id==='quizTitle')draft.title=el.value;
 else{const q=draft.questions[Number(el.dataset.index)];if(!q)return;
 if(el.dataset.field==='prompt')q.prompt=el.value;
 else if(el.dataset.field?.startsWith('option-'))q.options[Number(el.dataset.field.slice(-1))]=el.value}
 save(DRAFT,draft);
});
box.addEventListener('change',e=>{const el=e.target;if(!el.matches('[data-correct]'))return;const q=draft.questions[Number(el.dataset.correct)];if(q){q.correct=Number(el.value);save(DRAFT,draft)}});
$('settingsButton').addEventListener('click',()=>{for(const b of $('roundOptions').querySelectorAll('button'))b.setAttribute('aria-pressed',String(Number(b.dataset.rounds)===rounds));dialog.showModal()});
$('settingsClose').addEventListener('click',()=>dialog.close());
dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close()});
$('roundOptions').addEventListener('click',e=>{const b=e.target.closest('[data-rounds]');if(!b)return;rounds=Number(b.dataset.rounds);save(PREF,rounds);for(const c of $('roundOptions').querySelectorAll('button'))c.setAttribute('aria-pressed',String(c===b))});
window.__nightPartyTest={snapshot:()=>JSON.parse(JSON.stringify({selected,phase,people,state,draft,linkedPartyPlayer,verifiedIdentity})),chooseMode,start};
window.NightPlayerIdentity?.onChange(adoptIdentity);
render();
})();