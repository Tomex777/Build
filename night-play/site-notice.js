/* Public NIGHT Play site notice. Text-only rendering; unavailable backend preserves last static notice. */
(() => {
 'use strict';
 const bar=document.getElementById('announcementBar');
 if(!bar)return;
 const title=bar.querySelector('.announce-copy strong');
 const message=bar.querySelector('.announce-copy > span');
 const dismissed=document.getElementById('announcementDismiss');
 // Hiding a notice does not remove the user's access to the public website.
 (async()=>{
  try{
   const response=await fetch('/api/account?resource=notice',{method:'GET',credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(9000)});
   if(!response.ok)return;
   const payload=await response.json();
   if(!payload||typeof payload.enabled!=='boolean')return;
   if(!payload.enabled){
    bar.hidden=true;
    return;
   }
   if(typeof payload.title!=='string'||typeof payload.message!=='string'||!payload.title.trim()||!payload.message.trim())return;
   window.nightActiveNoticeKey='night-play-site-notice-v'+String(Number.isSafeInteger(payload.revision)?payload.revision:0);
   title.textContent=payload.title.slice(0,80);
   message.textContent=payload.message.slice(0,240);
   if(typeof updateBell==='function')updateBell();
  }catch{}
 })();
})();
