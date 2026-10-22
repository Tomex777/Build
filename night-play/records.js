/* Shared device-only round records, using the Profile activity contract. */
(()=>{'use strict';
 window.NightRecords=Object.freeze({round(id,value){
  if(!/^[a-z0-9-]{1,60}$/.test(id)||!Number.isFinite(value)||value<0)return;
  try{
   const score=Math.round(value),historyKey='night-play-history-v1-'+id;
   let history;try{history=JSON.parse(localStorage.getItem(historyKey)||'[]')}catch{}
   history=Array.isArray(history)?history.filter(n=>Number.isFinite(n)&&n>=0).slice(-12):[];
   const countKey='night-play-history-count-v1-'+id,stored=Number(localStorage.getItem(countKey));
   const count=Number.isFinite(stored)&&stored>0?Math.floor(stored):history.length;
   const bestKey='night-play-v1-'+id,best=Number(localStorage.getItem(bestKey));
   if(!Number.isFinite(best)||score>best)localStorage.setItem(bestKey,String(score));
   localStorage.setItem(historyKey,JSON.stringify([...history,score].slice(-12)));
   localStorage.setItem(countKey,String(count+1));
  }catch{/* Private browsing or full storage must never interrupt a game. */}
 }});
})();
