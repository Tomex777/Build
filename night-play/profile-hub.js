/* NIGHT Play account hub. Progress is computed from real device records. */
(() => {
 'use strict';
 const TAB_KEY='night-play-account-tab-v1';
 const MOTION_KEY='night-play-reduce-motion-v1';
 const $=id=>document.getElementById(id);
 const read=key=>{try{return localStorage.getItem(key)}catch{return null}};
 const store=(key,value)=>{try{localStorage.setItem(key,String(value))}catch{}};
 const definitions=[
  {name:'First round',detail:'Complete one recorded round.',ready:a=>a.rounds>=1,svg:'<circle cx="12" cy="12" r="9"/><path d="m7 12 3 3 7-7"/>'},
  {name:'Getting started',detail:'Complete 10 recorded rounds.',ready:a=>a.rounds>=10,svg:'<path d="M8 3h8l3 5-7 5-7-5z"/><path d="m12 13 3 8-3-2-3 2z"/>'},
  {name:'Explorer',detail:'Record rounds in 3 different games.',ready:a=>a.games>=3,svg:'<circle cx="12" cy="12" r="9"/><path d="m15 9-2 4-4 2 2-4z"/>'},
  {name:'Regular',detail:'Complete 50 recorded rounds.',ready:a=>a.rounds>=50,svg:'<path d="m12 2 2.8 6.5L22 9l-5.5 4.8 1.6 7.1L12 17.3l-6.1 3.6 1.6-7.1L2 9l7.2-.5z"/>'}
 ];
 function achievements(){
  const activity=typeof recordedActivity==='function'?recordedActivity():[];
  const state={rounds:activity.reduce((n,g)=>n+g.rounds,0),games:activity.length};
  $('accountRounds').textContent=state.rounds.toLocaleString();
  $('accountGames').textContent=String(state.games);
  const grid=$('accountAchievements');grid.replaceChildren();
  let earnedCount=0;
  for(const badge of definitions){
   const earned=badge.ready(state);if(earned)earnedCount++;
   const card=document.createElement('article');card.className='account-achievement'+(earned?' earned':'');
   const icon=document.createElement('span');icon.className='mark';
   icon.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true">'+badge.svg+'</svg>';
   const content=document.createElement('div');
   const heading=document.createElement('strong');heading.textContent=badge.name;
   const description=document.createElement('p');description.textContent=badge.detail;
   const label=document.createElement('small');label.textContent=earned?'Earned':'Not earned yet';
   content.append(heading,description,label);card.append(icon,content);grid.append(card);
  }
  $('accountBadges').textContent=earnedCount+' / '+definitions.length;
 }
 const valid=['profile','games','channels','settings'];
 function selectTab(value,{focus=false}={}){
  const name=valid.includes(value)?value:'profile';
  for(const tab of document.querySelectorAll('[data-profile-tab]')){
   const chosen=tab.dataset.profileTab===name;
   tab.setAttribute('aria-selected',String(chosen));tab.tabIndex=chosen?0:-1;
   if(chosen&&focus)tab.focus();
  }
  for(const pane of document.querySelectorAll('[data-profile-pane]'))pane.hidden=pane.dataset.profilePane!==name;
  if(name==='profile')achievements();
  if(name==='games'&&typeof renderProfileRecords==='function')renderProfileRecords();
  store(TAB_KEY,name);
 }
 function updateAccount(){
  const linked=Boolean(window.NightAccount?.profile);
  $('accountSettingsDescription').textContent=linked
   ?'Disconnect this browser or remove your NIGHT Play account.'
   :'Connect your WhatsApp account from the site header to manage sessions.';
 }
 function render(){
  const active=document.querySelector('[data-profile-tab][aria-selected="true"]')?.dataset.profileTab||read(TAB_KEY)||'profile';
  selectTab(active);updateAccount();
 }
 const tabs=[...document.querySelectorAll('[data-profile-tab]')];
 for(const [index,tab] of tabs.entries()){
  tab.addEventListener('click',()=>selectTab(tab.dataset.profileTab));
  tab.addEventListener('keydown',event=>{
   let next=index;
   if(event.key==='ArrowRight')next=(index+1)%tabs.length;
   else if(event.key==='ArrowLeft')next=(index+tabs.length-1)%tabs.length;
   else if(event.key==='Home')next=0;
   else if(event.key==='End')next=tabs.length-1;
   else return;
   event.preventDefault();selectTab(tabs[next].dataset.profileTab,{focus:true});
  });
 }
 const reduced=$('accountReducedMotion');
 const apply=()=>{
  reduced.checked=read(MOTION_KEY)==='1';
  document.documentElement.dataset.nightReducedMotion=String(reduced.checked);
 };
 reduced.addEventListener('change',()=>{store(MOTION_KEY,reduced.checked?'1':'0');apply()});
 apply();render();
 window.NightProfileHub={render,selectTab};
})();
