/* Online drawing uses signed NIGHT account RPC and authoritative room state.
   Pointer strokes are rendered immediately, then sent in bounded retryable
   batches. Polls fetch only missing strokes. No pass-and-play fallback. */
(()=>{'use strict';
const $=id=>document.getElementById(id),canvas=$('canvas'),ctx=canvas.getContext('2d');
const params=new URLSearchParams(location.search),palette=['#202f2c','#e2343b','#2876ca','#2a914e','#e3b02a','#ffffff'];
let code=params.get('code')?.trim().toUpperCase()||'',room=null,ink=palette[0],pointer=null,points=[],queue=[],sending=false,polling=false,known={canvasVersion:-1,count:0},offset=0,timer=null,epoch=0,nextSendAt=0;
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const link=()=>location.origin+'/draw/?code='+encodeURIComponent(code);
function error(message,network=false){$('error').textContent=message;$('error').dataset.network=String(network)}
async function request(action,body={}){let response;try{response=await fetch('/api/account',{method:'POST',credentials:'same-origin',cache:'no-store',headers:{'content-type':'application/json'},body:JSON.stringify({action:'draw:'+action,...(code?{code}:{}),since:known,...body}),signal:AbortSignal.timeout(12000)})}catch{throw Object.assign(new Error('Connection interrupted. Reconnecting; your drawing will retry automatically.'),{network:true})}const data=await response.json().catch(()=>({error:'Drawing service did not respond.'}));if(!response.ok)throw Object.assign(new Error(data.error||'Drawing room unavailable.'),{network:response.status>=500,status:response.status});return data}
function draw(stroke){const p=stroke.points;if(!p.length)return;ctx.strokeStyle=ctx.fillStyle=stroke.color;ctx.lineWidth=stroke.color==='#ffffff'?24:6;ctx.lineCap=ctx.lineJoin='round';ctx.beginPath();ctx.moveTo(p[0][0]*800,p[0][1]*600);if(p.length===1){ctx.arc(p[0][0]*800,p[0][1]*600,ctx.lineWidth/2,0,Math.PI*2);ctx.fill()}else{for(const q of p.slice(1))ctx.lineTo(q[0]*800,q[1]*600);ctx.stroke()}}
function clearCanvas(){ctx.fillStyle='#ffffff';ctx.fillRect(0,0,800,600)}
function apply(data){
 const previous=room;room=data;code=data.code;offset=data.serverNow-Date.now();
 if(data.canvasVersion!==known.canvasVersion||data.strokeOffset===0&&known.count>0){clearCanvas();known={canvasVersion:data.canvasVersion,count:0}}
 for(const stroke of data.strokes)draw(stroke);known={canvasVersion:data.canvasVersion,count:data.strokeCount};
 if(previous&&(previous.round!==data.round||data.phase!=='drawing')){pointer=null;points=[];queue=[]}
 $('players').innerHTML=data.players.map(p=>'<span class="player">'+esc(p.name)+(p.id===data.drawer&&data.phase==='drawing'?' · Artist':'')+'<b>'+p.points+'</b></span>').join('');
 if(data.phase==='lobby'){
  $('game').hidden=true;$('lobby').hidden=false;
  const markup='<div class="panel"><h2>'+(data.host?'Invite your friends.':'You’re in.')+'</h2><p>'+(data.host?'Send this invitation. Everyone joins on their own phone. Each player draws twice.':'Waiting for the host to start.')+'</p>'+(data.host?'<p class="invite">'+esc(link())+'</p><div class="actions"><button type="button" data-action="share">Share invitation</button><button type="button" data-action="start" '+(data.players.length<2?'disabled':'')+'>Start game</button></div>':'')+'</div>';
  if($('lobby').innerHTML!==markup)$('lobby').innerHTML=markup;
 }else if(data.phase==='finished'){
  $('game').hidden=true;$('lobby').hidden=false;const ranks=[...data.players].sort((a,b)=>b.points-a.points);$('lobby').innerHTML='<div class="panel"><h2>'+esc(ranks[0].name)+' wins!</h2><p>Everyone has drawn twice. Thanks for playing.</p>'+ranks.map((p,i)=>'<p><strong>'+esc(i+1)+'. '+esc(p.name)+'</strong> · '+p.points+' points</p>').join('')+'<a class="primary" href="/draw/">New room</a></div>';
 }else{
  $('lobby').hidden=true;$('game').hidden=false;const artist=data.me===data.drawer,live=data.phase==='drawing',guessed=data.guessed.includes(data.me),drawer=data.players.find(p=>p.id===data.drawer);
  $('word').textContent=data.word||Array(data.wordLength).fill('＿').join(' ');$('round').textContent=data.round+' / '+data.total;$('tools').hidden=!(artist&&live);$('guessForm').hidden=artist||!live||guessed;
  $('roundStatus').textContent=!live?'The word was '+data.word+'.':artist?'Your word: '+data.word+'. Draw for everyone else.':guessed?'Correct! Waiting for the others.':drawer.name+' is drawing. Type your guess.';
  $('next').hidden=!(data.host&&!live);$('next').textContent=data.round===data.total?'Final scores':'Next artist';$('log').innerHTML=[...data.log].reverse().map(item=>'<li><strong>'+esc(item.name)+'</strong> · '+esc(item.text)+'</li>').join('');
 }
 if(!previous){history.replaceState(null,'','/draw/?code='+encodeURIComponent(code));startPolling()}
 error('');clock();
}
function clock(){if(!room)return;const seconds=Math.max(0,Math.ceil((room.endsAt-Date.now()-offset)/1000));$('clock').textContent=room.phase==='drawing'?seconds+'s':''}
async function refresh(){if(!room||sending||polling||document.hidden)return;polling=true;const version=epoch;try{const data=await request('state');if(version===epoch)apply(data)}catch(e){error(e.message,e.network)}finally{polling=false}}
function startPolling(){clearInterval(timer);timer=setInterval(refresh,1500)}
async function action(name,body={}){if(sending)return;epoch++;sending=true;try{apply(await request(name,body))}catch(e){error(e.message,e.network)}finally{sending=false;flush()}}
function enqueue(){if(points.length){queue.push({stroke:{color:ink,points},mutationId:crypto.randomUUID().replaceAll('-','')});points=[];flush()}}
async function flush(){if(sending||polling||!queue.length||document.hidden||Date.now()<nextSendAt)return;nextSendAt=Date.now()+500;epoch++;sending=true;const batch=queue[0];try{const data=await request('stroke',batch);queue.shift();apply(data)}catch(e){error(e.message,e.network);if(!e.network)queue=[]}finally{sending=false}}
function point(e){const r=canvas.getBoundingClientRect();return [Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),Math.max(0,Math.min(1,(e.clientY-r.top)/r.height))]}
canvas.addEventListener('pointerdown',e=>{if(!room||room.phase!=='drawing'||room.me!==room.drawer||queue.length>=120)return;e.preventDefault();pointer=e.pointerId;canvas.setPointerCapture(pointer);points=[point(e)];draw({color:ink,points})});
canvas.addEventListener('pointermove',e=>{if(pointer!==e.pointerId)return;const p=point(e);draw({color:ink,points:[points.at(-1)||p,p]});points.push(p);if(points.length>=48){const last=points.at(-1);enqueue();points=[last]}});
for(const event of ['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,e=>{if(pointer===e.pointerId){pointer=null;enqueue()}});
setInterval(()=>{if(pointer!==null&&points.length>1){const last=points.at(-1);enqueue();points=[last]}flush();clock()},500);
document.querySelector('.inks').innerHTML=palette.map((color,i)=>'<button type="button" data-ink="'+color+'" style="--ink:'+color+'" aria-label="'+['Black ink','Red ink','Blue ink','Green ink','Yellow ink','Eraser'][i]+'" aria-pressed="'+(i===0)+'"></button>').join('');
document.querySelector('.inks').addEventListener('click',e=>{const button=e.target.closest('[data-ink]');if(!button)return;enqueue();ink=button.dataset.ink;for(const b of document.querySelectorAll('[data-ink]'))b.setAttribute('aria-pressed',String(b===button))});
$('clear').addEventListener('click',()=>{if(sending||queue.length){error('Wait for the drawing to finish syncing before clearing.');return}action('clear')});
$('next').addEventListener('click',()=>action('advance'));
$('guessForm').addEventListener('submit',e=>{e.preventDefault();const text=$('guess').value.trim();if(text&&!sending){$('guess').value='';action('guess',{guess:text})}});
$('lobby').addEventListener('submit',e=>{e.preventDefault();if(e.target.id==='joinForm')action('join',{guestName:$('guestName').value.trim()});if(e.target.id==='codeForm'){code=$('inviteCode').value.trim().toUpperCase().replace(/[\s-]/g,'');showJoin()}});
$('lobby').addEventListener('click',async e=>{const a=e.target.closest('[data-action]')?.dataset.action;if(a==='create')action('create');else if(a==='start')action('start');else if(a==='share'){try{if(navigator.share)await navigator.share({title:'Draw & Guess · NIGHT Play',url:link()});else{await navigator.clipboard.writeText(link());error('Invitation copied.')}}catch(e){if(e.name!=='AbortError')error('Copy the invitation link above.')}}else if(a==='retry')boot()});
function showJoin(){$('lobby').innerHTML='<div class="panel"><h2>Join your friends.</h2><form id="joinForm"><label for="guestName">Your name<input id="guestName" maxlength="24" autocomplete="nickname" required placeholder="Player name"></label><button type="submit">Join game</button></form></div>'}
async function boot(){try{const response=await fetch('/api/account',{cache:'no-store',credentials:'same-origin'});const account=await response.json();if(!response.ok)throw Error(account.error);if(code){try{apply(await request('state'));return}catch(e){if(e.status!==403&&e.status!==401)throw e}showJoin();return}if(account.features?.draw!==true){$('lobby').innerHTML='<div class="panel"><h2>Drawing rooms are being updated.</h2><p>Online drawing needs the NIGHT service update before rooms can open.</p><button type="button" data-action="retry">Check again</button></div>';return}$('lobby').innerHTML='<div class="panel"><h2>Draw together, from anywhere.</h2><p>Two to eight players. Everyone takes a turn as the artist.</p>'+(account.linked?'<button type="button" data-action="create">Create room</button>':'<a class="primary" href="/profile">Connect WhatsApp to host</a>')+'<form id="codeForm"><label for="inviteCode">Have an invitation?<input id="inviteCode" maxlength="12" autocomplete="off" required placeholder="Room code"></label><button class="secondary" type="submit">Join room</button></form></div>'}catch(e){error(e.message,true);$('lobby').innerHTML='<div class="panel"><h2>Connection interrupted.</h2><button type="button" data-action="retry">Try again</button></div>'}}
document.addEventListener('visibilitychange',()=>{if(document.hidden){pointer=null;enqueue()}else{refresh();flush()}});clearCanvas();boot();
})();
