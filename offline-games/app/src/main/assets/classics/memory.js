/* NIGHT Memory Match — original offline touch engine.
 * Individually licensed Twemoji SVG artwork is bundled in the APK.
 * CC BY 4.0 credit: Twitter/Twemoji, see assets/memory/ARTWORK-LICENSE.txt.
 */
(()=>{'use strict';const G=window.G;
const collections={
 animals:['cat','dog','fox','panda','koala','frog','lion','tiger','pig','cow','rabbit','bear','elephant','monkey','penguin','owl','butterfly','dolphin'],
 vehicles:['car','suv','taxi','bus','truck','semi','motorcycle','scooter','bicycle','tractor','firetruck','ambulance','police','helicopter','plane','rocket','ship','boat'],
 space:['earth','asia','americas','ringed','moon','crescent','sun','sunface','milkyway','shootingstar','glowingstar','star','telescope','satellite','alien','ufo','rocket','comet']
};
const layouts={'4×4':[4,4],'6×4':[6,4],'6×5':[6,5],'6×6':[6,6]};
const configKey='night-offline-memory-config-v1',saveKey='night-offline-memory-round-v1';
function storage(key){try{return JSON.parse(localStorage.getItem(key)||'null')}catch{return null}}
function put(key,x){try{localStorage.setItem(key,JSON.stringify(x))}catch{}}
G.add('memory','Memory Match','Flip beautifully illustrated cards to find matching pairs. Customise your board and artwork in Settings.','Memory',()=>{
 const pref=storage(configKey)||{},opts={theme:collections[pref.theme]?pref.theme:'animals',size:layouts[pref.size]?pref.size:'6×4'};
 let [columns,rows]=layouts[opts.size],count=columns*rows;
 let restored=storage(saveKey),valid=restored?.theme===opts.theme&&restored?.size===opts.size&&Array.isArray(restored.deck)&&restored.deck.length===count&&Array.isArray(restored.matched)&&restored.matched.length===count&&!restored.won;
 const assets=collections[opts.theme];
 const images=valid?restored.deck.slice():G.shuffle(assets.slice(0,count/2).flatMap(name=>[name,name]));
 let matched=valid?restored.matched.slice():Array(count).fill(false),flipped=[],moves=valid?restored.moves:0,elapsed=valid?Math.max(0,restored.elapsed||0):0,pending=null,won=false,layout=G.el('div','memory-grid');
 let cells=[],board=G.board(),interval=null,playing=true,tickAt=Date.now(),shownSecond=Math.floor(elapsed);
 layout.style.gridTemplateColumns='repeat('+columns+', minmax(0, 1fr))';
 layout.dataset.columns=columns;layout.setAttribute('aria-label',opts.size+' memory board, '+count/2+' pairs');
 function iconSrc(name){return '/memory/icons/'+opts.theme+'/'+name+'.svg'}
 function cardHTML(name){return '<span class="memory-inner"><span class="memory-back" aria-hidden="true"><svg viewBox="0 0 64 64" width="46%" height="46%"><path fill="none" stroke="#c6e2ca" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" d="M20 18h24v28H20z M20 31h24M32 18v28"/></svg></span><span class="memory-front"><img loading="eager" decoding="async" src="'+iconSrc(name)+'" alt=""></span></span>'}
 for(let i=0;i<count;i++){const btn=G.el('button','memory-card');btn.type='button';btn.innerHTML=cardHTML(images[i]);btn.dataset.show=String(!!matched[i]);btn.dataset.matched=String(!!matched[i]);btn.setAttribute('aria-label',matched[i]?'Matched '+images[i]:'Face-down card '+(i+1));btn.addEventListener('click',()=>pick(i));layout.append(btn);cells.push(btn)}
 board.append(layout);const statusLine=G.el('div','memory-stats');board.append(statusLine);
 function save(){put(saveKey,{theme:opts.theme,size:opts.size,deck:images,matched,moves,elapsed,won})}
 // Use measured foreground time, not a fixed 250ms tick (timers are throttled on Android).
 // Commit progress every second so an interrupted game resumes with an accurate clock.
 function now(){
   if(!playing||won)return;
   const instant=Date.now(),seconds=Math.min(2,Math.max(0,(instant-tickAt)/1000));
   tickAt=instant;
   if(document.hidden)return;
   elapsed+=seconds;
   const currentSecond=Math.floor(elapsed);
   if(currentSecond!==shownSecond){shownSecond=currentSecond;progress()}
 }
 function onVisibility(){tickAt=Date.now();if(!won)progress()}
 function progress(shouldSave=true){const pairs=matched.filter(Boolean).length/2;statusLine.textContent='PAIRS '+pairs+'/'+(count/2)+'     MOVES '+moves+'     TIME '+Math.floor(elapsed/60)+':'+String(Math.floor(elapsed)%60).padStart(2,'0');if(shouldSave)save()}
 function show(i,on,complete=false){const btn=cells[i];btn.dataset.show=String(on);btn.dataset.matched=String(complete);btn.setAttribute('aria-label',complete?'Matched '+images[i]:on?images[i]:'Face-down card '+(i+1))}
 function pick(i){if(won||pending!==null||matched[i]||flipped.includes(i))return;show(i,true,false);flipped.push(i);if(flipped.length===2){moves++;const [a,b]=flipped;if(images[a]===images[b]){matched[a]=true;matched[b]=true;show(a,true,true);show(b,true,true);flipped=[];const complete=matched.every(Boolean);if(complete){won=true;clearInterval(interval);try{localStorage.removeItem(saveKey)}catch{}const points=Math.max(1,1500-Math.round(elapsed*2)-moves*15);G.score('memory',points);G.status('All '+count/2+' pairs found in '+moves+' moves!');progress(false);if(settings.open)settings.close()}else G.status('Pair found! '+matched.filter(Boolean).length/2+'/'+(count/2))}else{pending=setTimeout(()=>{show(a,false);show(b,false);flipped=[];pending=null;progress()},750)}}progress()}
 const settingsButton=document.getElementById('gameSettings');if(settingsButton){settingsButton.hidden=false}
 const settings=G.el('dialog','memory-settings');settings.setAttribute('aria-label','Memory Match settings');
 const heading=G.el('h2','', 'Memory Match settings');settings.append(heading);
 settings.append(G.el('p','memory-label','PICTURE COLLECTION'));
 const themes=G.el('div','memory-picks');
 for(const [id,label] of [['animals','Animals'],['vehicles','Vehicles'],['space','Space']]){const b=G.el('button','',label);b.type='button';b.setAttribute('aria-pressed',String(opts.theme===id));b.onclick=()=>{opts.theme=id;settings.close();applySettings()};themes.append(b)}settings.append(themes);
 settings.append(G.el('p','memory-label','BOARD SIZE'));
 const sizes=G.el('div','memory-picks');
 for(const name of Object.keys(layouts)){const b=G.el('button','',name);b.type='button';b.setAttribute('aria-pressed',String(opts.size===name));b.onclick=()=>{opts.size=name;settings.close();applySettings()};sizes.append(b)}settings.append(sizes);
 settings.append(G.el('p','memory-credit','Illustration artwork: Twemoji · CC BY 4.0. All pictures are stored on your phone.'));
 const close=G.el('button','memory-close','Done');close.type='button';close.onclick=()=>settings.close();settings.append(close);document.body.append(settings);
 function applySettings(){put(configKey,opts);try{localStorage.removeItem(saveKey)}catch{}G.run('memory')}
 function openSettings(){if(!settings.open)settings.showModal()}
 if(settingsButton)settingsButton.addEventListener('click',openSettings);
 progress();document.addEventListener('visibilitychange',onVisibility);interval=setInterval(now,250);G.status('Find '+count/2+' matching pairs');
 G.note('Tap two cards. If they match, they stay revealed. Set size and theme with the top-right Settings button.');
 return()=>{playing=false;clearInterval(interval);document.removeEventListener('visibilitychange',onVisibility);if(!won)save();if(pending!==null)clearTimeout(pending);if(settingsButton){settingsButton.hidden=true;settingsButton.removeEventListener('click',openSettings)}settings.remove()};
});
})();