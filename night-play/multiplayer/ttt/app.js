(() => {
 const $=id=>document.getElementById(id)
 let room=null,poll=null,transitionTimer=null,requesting=false,loadingRoom=false,listingVersion=0
 const setMessage=(text='',isError=false)=>{$('message').textContent=text;$('message').dataset.error=String(isError)}
 const show=key=>{for(const name of ['loading','connect','lobby','room'])$(name).hidden=name!==key}
 async function api(action='status',data={}){
  const request=action==='status'?{method:'GET'}:{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action,...data})}
  const res=await fetch('/api/account',{...request,credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(13000)})
  const json=await res.json()
  if(!res.ok)throw new Error(json.error||'Unable to reach NIGHT Play.')
  return json
 }
 function link(){return location.origin+'/multiplayer/ttt/?invite='+encodeURIComponent(room.code)}
 async function share(){
  if(!room)return
  try {
   if(navigator.share){await navigator.share({title:'Play Tic-Tac-Toe · NIGHT Play',url:link()});return}
   await navigator.clipboard.writeText(link())
   setMessage('Match link copied.')
  }catch(error){if(error.name!=='AbortError')setMessage('Copy this match code: '+room.code,true)}
 }
 async function lobby(){
  stopPoll();clearTimeout(transitionTimer);transitionTimer=null;room=null
  const version=++listingVersion
  $('lobbyActions').hidden=false;$('waitingPanel').hidden=true;show('lobby');setMessage('')
  try {
   const list=await api('match:list')
   if(version!==listingVersion||room)return
   const parent=$('matchList');parent.replaceChildren()
   if(!list.matches.length){const empty=document.createElement('p');empty.className='subtitle';empty.textContent='No games yet.';parent.append(empty)}
   for(const match of list.matches){
    const item=document.createElement('button');item.type='button';item.className='match-row'
    const text=document.createElement('span'),title=document.createElement('strong'),sub=document.createElement('small'),arrow=document.createElement('b')
    title.textContent=match.opponent?'vs '+match.opponent:match.mode==='public'?'Finding opponent…':'Waiting for your friend'
    sub.textContent=match.status==='finished'?(match.winner==='D'?'Draw':match.winner===match.side?'You won':'Match ended'):(match.status==='waiting'?'Waiting':'Your side: '+match.side)
    arrow.className='match-chevron';arrow.innerHTML='<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>';text.append(title,sub);item.append(text,arrow)
    item.addEventListener('click',()=>openRoom(match));parent.append(item)
   }
  }catch(error){if(version===listingVersion&&!room)setMessage(error.message,true)}
 }
 function stopPoll(){if(poll!==null)clearTimeout(poll);poll=null}
 function renderBoard(){
  const board=$('board');board.replaceChildren()
  for(let i=0;i<9;i++){
   const b=document.createElement('button');b.className='cell';b.type='button'
   const mark=room.board[i];b.dataset.mark=mark==='.'?'':mark
   b.textContent=mark==='.'?'':mark
   b.setAttribute('aria-label',mark==='.'?'Square '+(i+1)+', empty':'Square '+(i+1)+', '+mark)
   b.disabled=requesting||room.status!=='active'||room.turn!==room.side||mark!=='.'
   b.addEventListener('click',()=>move(i));board.append(b)
  }
 }
 function renderRoom(){
  if(!room)return
  if(room.status==='waiting'){
   // Keep invitation creation and matchmaking in the existing lobby.
   // Never mount a playable board until the server confirms another player.
   show('lobby')
   $('lobbyActions').hidden=true;$('waitingPanel').hidden=false
   const friend=room.mode==='invite'
   $('waitingLabel').textContent=friend?'INVITATION READY':'MATCHMAKING'
   $('waitingTitle').textContent=friend?'Waiting for your friend':'Finding an opponent'
   $('waitingDescription').textContent=friend
    ?'Share your invitation. We’ll open the board when your friend joins.'
    :'Looking for another real NIGHT player. The board opens once you are matched.'
   $('invitePanel').hidden=!friend
   $('cancelWaiting').textContent=friend?'Cancel invitation':'Stop searching'
   if(friend&&room.code)$('code').textContent=room.code.match(/.{1,3}/g).join(' ')
   return
  }
  // Confirmed active (or completed) match only: the board belongs here.
  $('lobbyActions').hidden=false;$('waitingPanel').hidden=true;show('room')
  $('mySide').textContent=room.side
  $('opponent').textContent=room.opponent||'Opponent'
  $('leaveBtn').hidden=room.status==='finished'
  $('againBtn').hidden=room.status!=='finished'
  if(room.status==='finished'){$('turnStatus').textContent=room.winner==='D'?"It's a draw":room.winner===room.side?'You won!':'Game over';$('matchDetails').textContent=room.winner==='D'?'Neither side wins this round.':room.winner===room.side?'Well played.':'Your opponent won.'}
  else{$('turnStatus').textContent=room.turn===room.side?'Your turn':'Opponent’s turn';$('matchDetails').textContent='You play as '+room.side+' · '+(room.turn===room.side?'Choose an empty square.':'Waiting for '+room.opponent)}
  renderBoard()
 }
 function openRoom(next){
  ++listingVersion;clearTimeout(transitionTimer);transitionTimer=null
  room=next;stopPoll();setMessage('');renderRoom();schedulePoll()
 }
 function schedulePoll(){
  stopPoll()
  if(room&&room.status!=='finished'&&transitionTimer===null)
   poll=setTimeout(refreshRoom,room.status==='waiting'?1600:1900)
 }
 async function refreshRoom(){
  if(!room)return
  if(document.hidden||requesting||loadingRoom){schedulePoll();return}
  loadingRoom=true;const id=room.matchId
  try {
   const next=await api('match:state',{matchId:id})
   if(room?.matchId!==id)return
   if(next.version>=room.version){
    const wasWaiting=room.status==='waiting'
    room=next
    if(wasWaiting&&next.status==='finished'&&!next.opponent){
     await lobby()
     setMessage('This invitation has ended.',true)
     return
    }
    if(wasWaiting&&next.status==='active'&&next.opponent){
     // Announce a real server-confirmed match, then enter the board.
     $('waitingLabel').textContent='MATCH FOUND'
     $('waitingTitle').textContent=next.mode==='invite'?'Friend joined!':'Opponent found!'
     $('waitingDescription').textContent=next.opponent+' is ready. Opening your board…'
     $('invitePanel').hidden=true
     $('waitingBack').hidden=true;$('cancelWaiting').hidden=true
     transitionTimer=setTimeout(()=>{
      transitionTimer=null
      if(room?.matchId!==id)return
      $('waitingBack').hidden=false;$('cancelWaiting').hidden=false
      renderRoom();schedulePoll()
     },700)
    }else if(transitionTimer===null)renderRoom()
   }
   setMessage('')
  }catch(error){if(room?.matchId===id)setMessage(error.message,true)}
  finally{loadingRoom=false;schedulePoll()}
 }
 async function run(action,data={}){
  if(requesting)return
  requesting=true
  setMessage(action==='match:create'?'Preparing your invitation…':action==='match:random'?'Finding a real opponent…':action==='match:join'?'Joining your friend…':'')
  try {const next=await api(action,data);openRoom(next)}
  catch(error){setMessage(error.message,true)}
  finally{requesting=false;if(room)renderRoom()}
 }
 async function move(cell){
  if(!room||requesting||room.status!=='active'||room.turn!==room.side||room.board[cell]!=='.')return
  const id=room.matchId;requesting=true;renderBoard()
  try {const next=await api('match:move',{matchId:id,cell});if(room?.matchId===id){room=next;renderRoom()}}
  catch(error){setMessage(error.message,true);if(room?.matchId===id)await refreshRoom()}
  finally{requesting=false;if(room?.matchId===id)renderBoard()}
 }
 $('inviteBtn').addEventListener('click',()=>run('match:create'))
 $('randomBtn').addEventListener('click',()=>run('match:random'))
 $('joinForm').addEventListener('submit',async e=>{e.preventDefault();await run('match:join',{code:$('inviteInput').value})})
 $('lobbyBtn').addEventListener('click',lobby)
 $('waitingBack').addEventListener('click',lobby)
 $('copyLink').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(link());setMessage('Match link copied.')}catch{setMessage('Copy this code: '+room.code)}})
 $('shareLink').addEventListener('click',share)
 $('againBtn').addEventListener('click',()=>run('match:create'))
 async function askToLeave(){
  const dialog=$('leaveDialog')
  dialog.returnValue='cancel'
  $('leaveTitle').textContent=room?.status==='waiting'?'Cancel invitation?':'Forfeit this match?'
  $('leaveDescription').textContent=room?.status==='waiting'
    ?'Your invitation will close and the other person will no longer be able to join.'
    :'Leaving forfeits this round for your opponent. This cannot be undone.'
  dialog.showModal()
  $('keepMatch').focus()
  return new Promise(resolve=>dialog.addEventListener('close',()=>resolve(dialog.returnValue==='leave'),{once:true}))
 }
 async function leaveMatch(){
  if(!room||requesting||!await askToLeave())return
  const id=room.matchId;requesting=true
  try{await api('match:leave',{matchId:id});if(room?.matchId===id)await lobby()}
  catch(error){setMessage(error.message,true)}
  finally{requesting=false}
 }
 $('leaveBtn').addEventListener('click',leaveMatch)
 $('cancelWaiting').addEventListener('click',leaveMatch)
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&room)refreshRoom()})
 window.addEventListener('pagehide',stopPoll)
 async function start(){
  try {
   const state=await api()
   if(!state.linked){show('connect');return}
   show('lobby')
   const code=new URLSearchParams(location.search).get('invite')
   if(code){
    $('inviteInput').value=code
    try {
     const match=await api('match:join',{code})
     history.replaceState(null,'',location.pathname)
     openRoom(match);return
    }catch(error){setMessage(error.message,true)}
   }
   await lobby()
  }catch(error){show('connect');setMessage(error.message,true)}
 }
 start()
})()
