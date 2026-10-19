(() => {
'use strict';
const $=id=>document.getElementById(id),panel=$('content'),toast=$('toast');
const modes={wyr:'Would You Rather',mlt:'Most Likely To',tot:'This or That',nhie:'Never Have I Ever',trivia:'Trivia',quiz:'Who Knows Me Best?'};
const params=new URLSearchParams(location.search);
let code=params.get('code')?.trim().toUpperCase()||'',mode=params.get('game')||'';
let room=null,pending=false,refreshTimer=null,lastState='',identity=null;
const esc=x=>String(x??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const btn=(text,action,style='party-primary')=>'<button type="button" class="'+style+'" data-action="'+action+'">'+text+'</button>';
function alertUser(s){toast.textContent=s;clearTimeout(alertUser.timer);alertUser.timer=setTimeout(()=>toast.textContent='',5500)}
function title(t,sub,tag='LIVE PARTY'){$('title').textContent=t;$('subtitle').textContent=sub;$('kicker').textContent=tag;document.title=t+' · NIGHT Play'}
function panelMessage(head,body,back){panel.innerHTML='<div class="party-panel"><h2>'+esc(head)+'</h2><p>'+esc(body)+'</p>'+(back==='/profile'?'<a class="party-primary party-online-return" href="/profile">Connect WhatsApp</a>':'')+'</div>'}
const shareLink=c=>location.origin+'/party/room/?code='+encodeURIComponent(c);
async function request(action,body={}){
  const response=await fetch('/api/account',{method:'POST',credentials:'same-origin',cache:'no-store',
    headers:{'content-type':'application/json'},body:JSON.stringify({action, ...body})});
  const data=await response.json().catch(()=>({error:'Room service did not return a response.'}));
  if(!response.ok)throw Object.assign(new Error(data.error||'Room service temporarily unavailable.'),{status:response.status});
  return data;
}
function questionsForGame(selected){
  if(selected==='quiz'){
    let draft;
    try{draft=JSON.parse(localStorage.getItem('night-play-personal-quiz-v1'))}catch{}
    if(!draft||!Array.isArray(draft.questions)||draft.questions.length<3||draft.questions.length>10 ||
      draft.questions.some(q=>typeof q.prompt!=='string'||q.prompt.trim().length<3||
      !Array.isArray(q.options)||q.options.length!==2||q.options.some(x=>!x.trim())||![0,1].includes(q.correct)))return null;
    return draft.questions.map(q=>({prompt:q.prompt,options:q.options,correct:q.correct}));
  }
  const source=window.NIGHTPartyPacks?.[selected];
  if(!Array.isArray(source))return null;
  // Keep real online rounds short enough for phone guests to enjoy together.
  return [...source].sort(()=>Math.random()-.5).slice(0,5).map(q=>({
    prompt:q.prompt,options:q.options||[],...(q.correct!==undefined?{correct:q.correct}:{})
  }));
}
function invitePanel(){
  const link=shareLink(room.code);
  const chips=room.players.map(p=>'<span class="party-player-pill">'+esc(p.name)+'</span>').join('');
  return '<div class="party-invite"><span class="party-overline">INVITATION</span><h2>Your room is ready.</h2>'+
    '<p>Send this link to your friends. They join and enter their own names.</p>'+
    '<div class="party-invite-url" aria-label="Invitation link">'+esc(link)+'</div>'+
    '<div class="party-room-tools"><button type="button" class="party-primary" data-action="share"><svg class="party-inline-icon" aria-hidden="true"><use href="#party-icon-share"></use></svg>Share invitation</button></div></div>'+
    '<div class="party-room-members"><strong>Players · '+room.players.length+' / 16</strong><div class="party-player-list">'+chips+'</div></div>';
}
function renderJoin(){
  title('Join the game','Enter your name to join the online room.','YOU ARE INVITED');
  const verified=identity?.status==='linked'&&identity.profile;
  const name=verified?identity.profile.displayName:'';
  panel.innerHTML='<div class="party-panel"><span class="party-overline">INVITATION · '+esc(code)+'</span><h2>Ready to join?</h2>'+
    (verified?'<p>Your NIGHT Play account will join as <strong>'+esc(name)+'</strong>.</p>':
      '<p>The host does not add participant names.</p>'+
      '<form id="joinForm"><label class="party-field" for="guestName">Your name<input id="guestName" name="guestName" class="party-input" maxlength="24" autocomplete="nickname" placeholder="How should friends call you?" required></label>'+
      '<button class="party-primary" type="submit">Join this game</button></form>')+
      (verified?btn('Join as '+esc(name),'join'):'')+'</div>';
}
function renderRoom(){
  if(!room)return;
  const host=room.host,playing=Boolean(room.me),phase=room.phase;
  title(modes[room.mode]||'Party game',host?'Invite friends, then start the game.':'Hosted by '+room.hostName,'LIVE ROOM');
  let html='';
  if(phase==='lobby'){
    html+='<div class="party-panel">'+(host?invitePanel():'<span class="party-overline">WAITING ROOM</span><h2>You’re in.</h2><p>Waiting for '+esc(room.hostName)+' to start.</p><div class="party-player-list">'+room.players.map(p=>'<span class="party-player-pill">'+esc(p.name)+'</span>').join('')+'</div>')+
    (host?btn('Start game','start'):'' )+'</div>';
    if(host&&room.players.length<2)html=html.replace('data-action="start"','data-action="start" disabled');
  }else if(phase==='finished'){
    const ranks=room.scores||[];
    html+='<div class="party-summary"><span class="party-overline">GAME COMPLETE</span><h2>Thanks for playing.</h2>'+
      ((room.mode==='quiz'||room.mode==='trivia')?'<div class="party-leaderboard">'+ranks.map((p,i)=>'<div><span>'+esc(i+1)+'. '+esc(p.name)+'</span><b>'+p.points+' / '+room.total+'</b></div>').join('')+'</div>':
       '<p>'+room.players.length+' players · '+room.total+' rounds together.</p>')+
      '<a class="party-secondary party-online-return" href="/party/">All games</a></div>';
  }else{
    const q=room.question,choices=q?.choices||[];
    html+='<div class="party-progress"><span>ROUND '+(room.round+1)+' / '+room.total+'</span><span>'+room.votedCount+' / '+room.players.length+' ANSWERED</span></div>';
    if(phase==='vote'){
      html+='<div class="party-question"><span class="party-overline">'+(host?'HOST VIEW':room.voted?'ANSWER LOCKED':'YOUR VOTE')+'</span><h2>'+esc(q.prompt)+'</h2>';
      if(playing&&!room.voted){
        html+='<div class="party-vote-grid '+(choices.length>2?'many':'')+'">'+choices.map((c,i)=>
          '<button type="button" class="party-vote-option" data-answer="'+i+'"><span class="party-choice-id">'+String.fromCharCode(65+i)+'</span>'+esc(c)+'</button>').join('')+'</div>';
      }else html+='<p class="party-field-help">'+(host?'The reveal starts automatically when every guest answers.':'Answer saved. Waiting for everyone else.')+'</p>';
      html+='</div>';
    }else {
      html+='<div class="party-results"><span class="party-overline">EVERYONE ANSWERED</span><h2>'+esc(q.prompt)+'</h2>'+
        choices.map((c,i)=>'<div class="party-result-row"><div class="party-result-top"><span>'+esc(c)+(q.correct===i?' <small>✓ Correct</small>':'')+
        '</span><strong>'+room.results[i]+' / '+room.players.length+'</strong></div><div class="party-bar"><span style="width:'+(100*room.results[i]/room.players.length)+'%"></span></div></div>').join('')+
        (host?btn(room.round===room.total-1?'See final results':'Next question','advance'):'<p class="party-field-help">Waiting for the host to continue.</p>')+'</div>';
    }
  }
  panel.innerHTML=html;
}
function apply(data){
  const serialized=JSON.stringify(data);
  if(serialized===lastState)return;
  lastState=serialized;room=data;mode=data.mode;code=data.code;
  renderRoom();
}
async function synchronize(){
  if(!code||!room||pending)return;
  try{const v=await request('party:state',{code});apply(v)}catch(e){
    if(e.status===410||e.status===404){room=null;clearInterval(refreshTimer);panelMessage('Room unavailable',e.message);}
    else if(e.status!==503)alertUser(e.message);
  }
}
async function act(action,details={}){
  if(pending)return;
  pending=true;
  try{const data=await request('party:'+action,{code,...details});apply(data);if(action==='join'&&!refreshTimer)refreshTimer=setInterval(synchronize,1750)}
  catch(e){alertUser(e.message)}
  finally{pending=false}
}
async function share(){
  const url=shareLink(code);
  try{
    if(navigator.share){await navigator.share({title:'Join my NIGHT Play game',url});return;}
    await navigator.clipboard.writeText(url);alertUser('Invitation link copied.');
  }catch(e){
    if(e.name==='AbortError')return;
    try{await navigator.clipboard.writeText(url);alertUser('Invitation link copied.')}
    catch{alertUser('Select the invitation link above to copy it.')}
  }
}
panel.addEventListener('click',async e=>{
  const answer=e.target.closest('[data-answer]');
  if(answer){await act('vote',{answer:Number(answer.dataset.answer)});return}
  const b=e.target.closest('[data-action]');if(!b)return;
  const action=b.dataset.action;
  if(action==='share'){await share();return}
  if(action==='join'){await act('join');return}
  if(['start','advance'].includes(action))await act(action);
});
panel.addEventListener('submit',e=>{
  if(e.target.id!=='joinForm')return;
  e.preventDefault();const name=$('guestName').value.trim().replace(/\s+/g,' ').slice(0,24);
  if(!name)return alertUser('Enter your name.');
  act('join',{guestName:name});
});
async function boot(){
  identity=await window.NightPlayerIdentity.refresh();
  if(code){
    try{apply(await request('party:state',{code}))}
    catch(e){if(e.status===403)renderJoin();else panelMessage(e.status===410?'Room expired':'Could not open room',e.message)}
  }else if(modes[mode]){
    title('Creating your '+modes[mode]+' room','Your WhatsApp name is taken from NIGHT Play Profile.');
    if(identity?.status!=='linked'){
      panelMessage('Connect WhatsApp to host','Open NIGHT Play Profile and connect WhatsApp. Guests can still join your room using a link.','/profile');
      return;
    }
    const questions=questionsForGame(mode);
    if(!questions){panelMessage('Finish your quiz first','Complete at least three questions in the quiz editor before creating an online invitation.','/party/?game=quiz');return}
    panel.innerHTML='<div class="party-panel"><h2>Creating your room…</h2><p>Preparing a private invite link.</p></div>';
    try {
      const created=await request('party:create',{mode,questions});
      history.replaceState({},'', '/party/room/?code='+encodeURIComponent(created.code));
      apply(created);
    }catch(e){panelMessage('Could not create a room',e.message,'/party/?game='+encodeURIComponent(mode))}
  }else panelMessage('Choose a party game','Start from the party game library.');
  if(room)refreshTimer=setInterval(synchronize,1750);
}
boot().catch(e=>panelMessage('Could not load this room',e.message));
window.addEventListener('pagehide',()=>clearInterval(refreshTimer));
})();
