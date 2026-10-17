(() => {
 const $=id=>document.getElementById(id)
 let room=null,poll=null,requesting=false,loadingRoom=false
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
  stopPoll();room=null;show('lobby');setMessage('')
  try {
   const list=await api('match:list')
   const parent=$('matchList');parent.replaceChildren()
   if(!list.matches.length){const empty=document.createElement('p');empty.className='subtitle';empty.textContent='No games yet.';parent.append(empty)}
   for(const match of list.matches){
    const item=document.createElement('button');item.type='button';item.className='match-row'
    const text=document.createElement('span'),title=document.createElement('strong'),sub=document.createElement('small'),arrow=document.createElement('b')
    title.textContent=match.opponent?'vs '+match.opponent:match.mode==='public'?'Finding opponent…':'Waiting for your friend'
    sub.textContent=match.status==='finished'?(match.winner==='D'?'Draw':match.winner===match.side?'You won':'Match ended'):(match.status==='waiting'?'Waiting':'Your side: '+match.side)
    arrow.textContent='→';text.append(title,sub);item.append(text,arrow)
    item.addEventListener('click',()=>openRoom(match));parent.append(item)
   }
  }catch(error){setMessage(error.message,true)}
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
  show('room')
  $('mySide').textContent=room.side
  $('opponent').textContent=room.opponent||'Waiting…'
  $('invitePanel').hidden=room.status!=='waiting'||room.mode!=='invite'
  $('code').textContent=room.code.match(/.{3}/g).join(' ')
  $('leaveBtn').hidden=room.status==='finished'
  $('againBtn').hidden=room.status!=='finished'
  if(room.status==='waiting'){$('turnStatus').textContent=room.mode==='public'?'Finding a real opponent…':'Waiting for your friend';$('matchDetails').textContent='Your game stays saved while you wait.'}
  else if(room.status==='finished'){$('turnStatus').textContent=room.winner==='D'?"It's a draw":room.winner===room.side?'You won!':'Game over';$('matchDetails').textContent=room.winner==='D'?'Neither side wins this round.':room.winner===room.side?'Well played.':'Your opponent won.'}
  else{$('turnStatus').textContent=room.turn===room.side?'Your turn':'Opponent’s turn';$('matchDetails').textContent='You play as '+room.side+' · '+(room.turn===room.side?'Choose an empty square.':'Waiting for '+room.opponent)}
  renderBoard()
 }
 function openRoom(next){room=next;stopPoll();renderRoom();schedulePoll()}
 function schedulePoll(){
  stopPoll()
  if(room&&room.status!=='finished')poll=setTimeout(refreshRoom,room.status==='waiting'?2600:1900)
 }
 async function refreshRoom(){
  if(!room)return
  if(document.hidden){schedulePoll();return}
  if(requesting||loadingRoom){schedulePoll();return}
  loadingRoom=true;const id=room.matchId
  try {
   const next=await api('match:state',{matchId:id})
   if(room?.matchId===id&&next.version>=room.version){room=next;renderRoom()}
   setMessage('')
  }catch(error){if(room?.matchId===id)setMessage(error.message,true)}
  finally{loadingRoom=false;schedulePoll()}
 }
 async function run(action,data={}){
  if(requesting)return
  requesting=true;setMessage('')
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
 $('copyLink').addEventListener('click',async()=>{try{await navigator.clipboard.writeText(link());setMessage('Match link copied.')}catch{setMessage('Copy this code: '+room.code)}})
 $('shareLink').addEventListener('click',share)
 $('againBtn').addEventListener('click',()=>run('match:create'))
 $('leaveBtn').addEventListener('click',async()=>{
  if(!room||!confirm(room.status==='waiting'?'Cancel this invitation?':'Forfeit this match?'))return
  try{await api('match:leave',{matchId:room.matchId});await lobby()}
  catch(error){setMessage(error.message,true)}
 })
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
