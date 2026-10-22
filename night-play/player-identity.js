/* NIGHT Play verified game identity.
 * All game pages read the same server-backed WhatsApp account used by Profile.
 * The browser never invents a linked identity or stores auth credentials.
 */
(() => {
  'use strict';
  const NAME_KEY='night-play-player-name-v1';
  const SIGNAL_KEY='night-play-identity-updated-v1';
  let state={status:'checking',profile:null};
  const handlers=new Set();
  let pending=null, revision=0;
  const localName=()=>{try{return (localStorage.getItem(NAME_KEY)||'').trim().slice(0,28)}catch{return ''}};
  const notify=()=>{for(const fn of handlers)try{fn({...state})}catch{}};
  const profileName=()=>state.status==='linked'?state.profile.displayName:'';
  function getName(){return profileName()||localName()||'Player'}
  async function refresh(){
    if(pending){await pending;return {...state}}
    const current=++revision;
    pending=(async()=>{
      try{
        const res=await fetch('/api/account',{credentials:'same-origin',cache:'no-store',
          signal:AbortSignal.timeout(12000)});
        if(!res.ok)throw new Error('account status unavailable');
        const data=await res.json();
        if(current!==revision)return;
        const p=data?.linked&&data?.profile&&typeof data.profile.displayName==='string'&&
          typeof data.profile.id==='string' ? data.profile:null;
        state=p?{status:'linked',profile:{id:p.id,displayName:p.displayName.trim().slice(0,28)}}:
          {status:'guest',profile:null};
      }catch{
        if(current!==revision)return;
        // A network error must not quietly sign a verified user out.
        state={...state,status:state.profile?'linked':'unavailable'};
      }
      notify();
    })();
    try{await pending}finally{pending=null}
    return {...state};
  }
  function onChange(fn){handlers.add(fn);fn({...state});return()=>handlers.delete(fn)}
  const broadcast=()=>{try{localStorage.setItem(SIGNAL_KEY,String(Date.now()))}catch{}};
  function guestName(value){
    if(state.status==='linked')throw new Error('Edit your verified name in NIGHT Play Profile');
    const name=String(value||'').trim().replace(/[<>]/g,'').slice(0,28);
    try{if(name)localStorage.setItem(NAME_KEY,name);else localStorage.removeItem(NAME_KEY)}catch{}
    broadcast();notify();return name;
  }
  window.NightPlayerIdentity=Object.freeze({refresh,onChange,getName,getState:()=>({...state}),guestName});
  window.addEventListener('storage',e=>{
    if(e.key===SIGNAL_KEY)refresh();
    if(e.key===NAME_KEY&&state.status!=='linked')notify();
  });
  document.addEventListener('visibilitychange',()=>{
    if(document.visibilityState==='visible')refresh();
  });
  window.addEventListener('pageshow',e=>{if(e.persisted)refresh()});
  refresh();
})();