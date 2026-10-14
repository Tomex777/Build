/* NIGHT Naija Ludo: original artwork inspired by a familiar Nigerian tabletop game.
 * Fair dice are generated before animation; animated faces cannot affect results.
 * Pass-and-play, never represented as online multiplayer.
 */
(function(){
"use strict";
const COLORS={red:"#df3439",green:"#2e9655",yellow:"#e8b327",blue:"#2d73ba"};
const TRACK=[[6,1],[6,2],[6,3],[6,4],[6,5],[5,6],[4,6],[3,6],[2,6],[1,6],[0,6],[0,7],[0,8],[1,8],[2,8],[3,8],[4,8],[5,8],[6,9],[6,10],[6,11],[6,12],[6,13],[6,14],[7,14],[8,14],[8,13],[8,12],[8,11],[8,10],[8,9],[9,8],[10,8],[11,8],[12,8],[13,8],[14,8],[14,7],[14,6],[13,6],[12,6],[11,6],[10,6],[9,6],[8,5],[8,4],[8,3],[8,2],[8,1],[8,0],[7,0],[6,0]];
const START={green:0,yellow:13,blue:26,red:39};
const HOME={green:[[7,1],[7,2],[7,3],[7,4],[7,5]],yellow:[[1,7],[2,7],[3,7],[4,7],[5,7]],blue:[[7,13],[7,12],[7,11],[7,10],[7,9]],red:[[13,7],[12,7],[11,7],[10,7],[9,7]]};
const SAFE=new Set([8,21,34,47]);
const DEFAULT_OPTIONS={barrier:true,safeHouse:true,captureFinishes:true,bonusOnCapture:false,directCount:false,visualHand:true,speed:"normal"};
const makeOptions=()=>({...DEFAULT_OPTIONS});
const PIPS={1:[4],2:[0,8],3:[0,4,8],4:[0,2,6,8],5:[0,2,4,6,8],6:[0,2,3,5,6,8]};
const label=k=>k[0].toUpperCase()+k.slice(1);
let ticker=null,moveTimer=null,aiTimer=null,life=0,audio=null;
let riggedForTest=null;
const isTestHost=/^(localhost|127\.0\.0\.1)$/.test(location.hostname);
const reduced=()=>window.matchMedia&&window.matchMedia("(prefers-reduced-motion: reduce)").matches;
function rand6(){
 if(isTestHost&&riggedForTest&&riggedForTest.length)return riggedForTest.shift();
 if(window.crypto&&window.crypto.getRandomValues){
  const v=new Uint32Array(1);const top=4294967296-(4294967296%6);
  do{window.crypto.getRandomValues(v);}while(v[0]>=top);
  return(v[0]%6)+1;
 }
 return Math.floor(Math.random()*6)+1;
}
function sound(s,f=530,d=.055,volume=.035){
 if(!s.sound)return;
 try{
  const Audio=window.AudioContext||window.webkitAudioContext;if(!Audio)return;
  audio=audio||new Audio();if(audio.state==="suspended")audio.resume().catch(()=>{});
  const osc=audio.createOscillator(),gain=audio.createGain(),t=audio.currentTime;
  osc.type="triangle";osc.frequency.setValueAtTime(f,t);osc.frequency.exponentialRampToValueAtTime(f*.73,t+d);
  gain.gain.setValueAtTime(volume,t);gain.gain.exponentialRampToValueAtTime(.0001,t+d);
  osc.connect(gain);gain.connect(audio.destination);osc.start(t);osc.stop(t+d+.02);
 }catch{}
}
function create(count=2,diceCount=2){
 const groups=count===2?[["red","yellow"],["green","blue"]]:count===3?[["red"],["green"],["yellow"]]:[["red"],["green"],["yellow"],["blue"]];
 return{
  version:2,players:groups.map((houses,i)=>({name:count===2?(i===0?"Red & Yellow":"Green & Blue"):label(houses[0]),color:COLORS[houses[0]],key:houses[0],houses,tokens:Array(houses.length*4).fill(-1)})),
  turn:0,rolls:[],selectedDie:null,die:null,rolled:false,last:[1,1],diceCount:diceCount===1?1:2,
  rolling:false,moving:false,bonus:false,winner:null,turns:0,sound:true,options:makeOptions(),opponent:"local",difficulty:"normal",
  msg:"Tap the dice to throw. Roll a six to bring a counter out."
 };
}
function normalize(s){
 if(!s||s.version!==2||!Array.isArray(s.players)||s.players.some(p=>!Array.isArray(p.houses)||p.tokens.length!==p.houses.length*4))return create();
 s.diceCount=s.diceCount===1?1:2;if(typeof s.rolling!=="boolean")s.rolling=false;if(typeof s.moving!=="boolean")s.moving=false;
 s.options={...makeOptions(),...(s.options||{})};if(!["slow","normal","fast"].includes(s.options.speed))s.options.speed="normal";
 s.opponent=s.opponent==="computer"&&s.players.length===2?"computer":"local";
 if(!["easy","normal","hard","advanced"].includes(s.difficulty))s.difficulty="normal";
 s.rolls=Array.isArray(s.rolls)?s.rolls.filter(d=>d&&d.value>=1&&d.value<=6).slice(0,2):[];
 if(s.selectedDie!==null&&(!Number.isInteger(s.selectedDie)||!s.rolls[s.selectedDie]||s.rolls[s.selectedDie].used))s.selectedDie=null;
 return s;
}
function cancel(){window.NightLudoMotion?.cancel();life++;if(ticker!==null){clearInterval(ticker);ticker=null;}if(moveTimer!==null){clearTimeout(moveTimer);moveTimer=null;}if(aiTimer!==null){clearTimeout(aiTimer);aiTimer=null;}}
function tokenHouse(p,index){return p.houses[Math.floor(index/4)]}
function cell(k,pos){if(pos<0)return null;if(pos>=56)return [7,7];if(pos>=51)return HOME[k][pos-51];return TRACK[(START[k]+pos)%52];}
function currentIndex(k,pos){return(START[k]+pos)%52;}
function isSafe(s,idx){return !!(s.options.safeHouse&&SAFE.has(idx));}
function barrierAt(s,idx,pi){
 if(!s.options.barrier)return false;
 return Object.entries(START).some(([house,start])=>{
  if(start!==idx)return false;
  let rivals=0;
  for(let j=0;j<s.players.length;j++)if(j!==pi){const op=s.players[j];op.tokens.forEach((n,k)=>{if(n>=0&&n<51&&currentIndex(tokenHouse(op,k),n)===idx&&tokenHouse(op,k)===house)rivals++;});}
  return rivals>=2;
 });
}
function blockedByBarrier(s,pi,ti,n){
 const p=s.players[pi],pos=p.tokens[ti];if(!s.options.barrier||pos<0||n===6)return false;
 const house=tokenHouse(p,ti);
 for(let step=pos+1;step<=pos+n&&step<=50;step++)if(barrierAt(s,currentIndex(house,step),pi))return true;
 return false;
}
function legal(s,pi,ti,di){
 if(s.winner!==null||s.rolling||s.moving||pi!==s.turn)return false;
 if(!Number.isInteger(di)||!s.rolls[di]||s.rolls[di].used)return false;
 const pos=s.players[pi].tokens[ti],n=s.rolls[di].value;
 return pos===-1?n===6:pos<56&&pos+n<=56&&!blockedByBarrier(s,pi,ti,n);
}
function canForDie(s,di){return s.players[s.turn].tokens.some((_,ti)=>legal(s,s.turn,ti,di));}
function chooseAvailable(s){
 const choices=s.rolls.map((d,i)=>!d.used&&canForDie(s,i)?i:-1).filter(i=>i>=0);
 if(!choices.length)return null;
 if(choices.includes(s.selectedDie))return s.selectedDie;
 return choices[0];
}
function finishTurn(s){
 s.rolls=[];s.selectedDie=null;s.die=null;s.rolled=false;
 if(s.bonus&&s.winner===null){s.bonus=false;s.msg+=" Six! Roll again.";return;}
 s.bonus=false;s.turn=(s.turn+1)%s.players.length;s.turns++;
 s.msg+=" Pass the phone to "+s.players[s.turn].name+".";
}
function finishIfDone(s){
 if(s.winner!==null)return;
 // Crucial Nigerian dual-dice rule: an unused 2 may become playable AFTER
 // a 6 releases a piece. Never discard a currently unusable die prematurely.
 const ready=chooseAvailable(s);
 if(ready!==null){s.selectedDie=ready;s.die=s.rolls[ready].value;return;}
 s.rolls.forEach(d=>{d.used=true;});
 s.selectedDie=null;finishTurn(s);
}
function diceFace(n,cls="",opts=""){
 // Keep a physically consistent opposite-face arrangement while presenting the
 // final rolled value directly toward the player. 3D tumbling never changes result.
 const layouts={
 1:{front:1,back:6,right:3,left:4,top:2,bottom:5},
 6:{front:6,back:1,right:4,left:3,top:2,bottom:5},
 2:{front:2,back:5,right:3,left:4,top:6,bottom:1},
 5:{front:5,back:2,right:3,left:4,top:1,bottom:6},
 3:{front:3,back:4,right:6,left:1,top:2,bottom:5},
 4:{front:4,back:3,right:1,left:6,top:2,bottom:5}
 };
 const sides=Object.entries(layouts[n]||layouts[1]);
 return '<span class="nj-die '+cls+'" data-dice-value="'+n+'" '+opts+'><span class="nj-cube nj-value-'+n+'" aria-hidden="true">'+sides.map(([face,val])=>'<span class="nj-cube-face nj-face-'+face+'"><span class="nj-pips">'+Array.from({length:9},(_,i)=>'<i class="'+(PIPS[val].includes(i)?"lit":"")+'"></i>').join("")+'</span></span>').join("")+'</span></span>';
}
function avatarFor(house){
 // A small original football medallion keeps the Nigerian board character
 // without unfinished cartoon portraits or downloaded image assets.
 const ink=COLORS[house];
 return '<span class="nj-avatar" aria-hidden="true"><svg viewBox="0 0 100 100" focusable="false"><circle cx="50" cy="50" r="44" fill="none" stroke="'+ink+'" stroke-width="4"/><path d="m50 28 22 16-8 26H36l-8-26Z" fill="'+ink+'"/><path d="M50 28V6M72 44l20-7M64 70l12 17M36 70 24 87M28 44 8 37" fill="none" stroke="'+ink+'" stroke-width="4"/><path d="m18 18 10 26M82 18 72 44M91 65 64 70M50 94 36 70M9 65l19-21" fill="none" stroke="'+ink+'" stroke-width="2"/></svg></span>';
}
function drawBoard(s){
 const map=new Map();s.players.forEach((p,pi)=>p.tokens.forEach((pos,ti)=>{
  if(pos<0||pos>=56)return;const xy=cell(tokenHouse(p,ti),pos),key=xy.join(",");
  if(!map.has(key))map.set(key,[]);map.get(key).push({pi,ti,color:tokenHouse(p,ti)});
 }));
 const activeHouses=new Map();s.players.forEach((p,pi)=>p.houses.forEach((house,hi)=>activeHouses.set(house,{p,pi,hi})));
 const destinations=new Set();
 s.players[s.turn].tokens.forEach((pos,ti)=>{
  if(!legal(s,s.turn,ti,s.selectedDie))return;
  const n=s.rolls[s.selectedDie].value,xy=cell(tokenHouse(s.players[s.turn],ti),pos<0?0:pos+n);
  if(xy)destinations.add(xy.join(","));
 });
 let out='<div class="ludo nj-board" role="group" aria-label="Classic fifteen-by-fifteen Nigerian Ludo board">';
 for(let r=0;r<15;r++)for(let c=0;c<15;c++){
  const key=r+","+c,idx=TRACK.findIndex(x=>x[0]===r&&x[1]===c);
  const classes=["l-cell"],pieces=map.get(key)||[];
  if(idx>=0)classes.push("track");
  if(idx>=0&&Object.values(START).includes(idx))classes.push(Object.keys(START).find(k=>START[k]===idx));
  else if(idx>=0&&isSafe(s,idx))classes.push("nj-safe");
  if(r>=6&&r<=8&&c>=6&&c<=8)classes.push("nj-center-tile");
  for(const [k,a] of Object.entries(HOME))if(a.some(x=>x[0]===r&&x[1]===c))classes.push("nj-home-"+k);
  if(pieces.length>1)classes.push("nj-stacked");
  if(destinations.has(key))classes.push("nj-destination");
  let body=pieces.slice(0,4).map(v=>{
   const available=legal(s,v.pi,v.ti,s.selectedDie);
   return '<button type="button" class="l-token nj-piece'+(available?" eligible":"")+'" data-piece="'+v.pi+'-'+v.ti+'" data-progress="'+s.players[v.pi].tokens[v.ti]+'" style="--token:'+COLORS[v.color]+'" aria-label="'+label(v.color)+' counter '+(v.ti%4+1)+'" '+(available?"":"disabled")+'><span class="nj-piece-core">'+(v.ti%4+1)+'</span></button>';
  }).join("");
  if(!body&&isSafe(s,idx))body='<span class="star" aria-hidden="true">✦</span>';
  if(!body&&idx>=0&&[2,5,8,11,15,18,21,24,28,31,34,37,41,44,47,50].includes(idx)){
    const [y,x]=TRACK[idx], [nextY,nextX]=TRACK[(idx+1)%52],dir=nextY>y?"↓":nextY<y?"↑":nextX>x?"→":"←";
    body='<span class="nj-arrow" aria-hidden="true">'+dir+'</span>';
  }
  if(idx>=0&&barrierAt(s,idx,s.turn))classes.push("nj-barrier");
  out+='<div class="'+classes.join(" ")+'" style="grid-row:'+(r+1)+';grid-column:'+(c+1)+'">'+body+'</div>';
 }
 const yards=[["green",1,1],["yellow",1,10],["red",10,1],["blue",10,10]];
 for(const [k,row,col] of yards){
  const info=activeHouses.get(k);
  out+='<div class="l-base nj-yard nj-yard-'+k+(info&&s.turn===info.pi?' is-turn':'')+'" style="--house:'+COLORS[k]+';grid-row:'+row+'/span 6;grid-column:'+col+'/span 6">';
  out+='<div class="nj-yard-label">'+label(k).toUpperCase()+'</div><span class="nj-sport-icon" aria-hidden="true"><svg viewBox="0 0 90 90" focusable="false"><circle cx="45" cy="22" r="15" fill="#ffffff67"/><path d="M19 78c0-20 10-31 26-31s26 11 26 31Z" fill="#ffffff46"/><path d="m29 56 16 12 16-12" stroke="#ffffff90" stroke-width="4" fill="none"/></svg></span><div class="yard">';
  for(let ti=0;ti<4;ti++){
   const index=info?info.hi*4+ti:-1,atHome=info&&info.p.tokens[index]===-1,available=atHome&&legal(s,info.pi,index,s.selectedDie);
   out+='<button type="button" '+(atHome?'data-piece="'+info.pi+'-'+index+'" data-progress="-1"':'')+' aria-label="'+label(k)+' yard position '+(ti+1)+'" '+(available?'':'disabled')+'>';
   if(atHome)out+='<span class="l-token nj-piece'+(available?' eligible':'')+'" style="--token:'+COLORS[k]+'"><span class="nj-piece-core">'+(ti+1)+'</span></span>';
   out+='</button>';
  }
  out+=avatarFor(k)+'</div></div>';
 }
 const centerVals=s.rolling?(Array.isArray(s.preview)?s.preview:[1,2]):s.rolls.length?s.rolls.map(d=>d.value):s.last;
 out+='<div class="nj-dice-center'+(s.rolling?' is-rolling':'')+'" style="grid-row:7/span 3;grid-column:7/span 3" aria-label="Central dice tray"><div class="nj-dice-pair">';
 centerVals.slice(0,s.diceCount).forEach((n,i)=>{const action=s.rolls.length?'data-ludo-die="'+i+'"':'data-action="ludo-roll"';const enabled=!s.rolling&&!s.moving&&s.winner===null&&(!s.rolls.length||!!s.rolls[i]&&!s.rolls[i].used&&canForDie(s,i));const selected=s.rolls.length&&i===s.selectedDie,used=!!s.rolls[i]?.used;out+='<button type="button" class="nj-center-button'+(selected?' is-selected':'')+(used?' is-used':'')+'" '+action+' aria-label="'+(s.rolls.length?'Die '+(i+1)+': '+n+(used?', used':selected?', selected':''):'Throw dice')+'" aria-pressed="'+!!selected+'" '+(enabled?'':'disabled')+'>'+diceFace(n,s.rolling?"is-tumbling":"", 'data-center-die="'+i+'"')+'</button>';});
 out+='</div></div>'+(s.options.visualHand&&!s.rolls.length&&!s.rolling&&!s.moving&&s.winner===null?'<span class="nj-hand-cue" aria-hidden="true">☝</span>':'')+'</div>';
 return out;
}
function render(s,ui){
 s=normalize(s);
 const motion=window.NightLudoMotion;
 const before=motion?motion.capture(ui.board):null;
 ui.board.innerHTML=drawBoard(s);
 const p=s.players[s.turn];
 const phase=s.winner!==null?s.players[s.winner].name+" wins!":s.rolling?"Dice tumbling…":s.moving?"Counters moving…":s.rolls.length?"Choose a highlighted counter":p.name+"'s turn";
 ui.status.textContent=phase+". "+s.msg;
 const faces=s.rolling?s.preview:s.rolls.length?s.rolls.map(d=>d.value):s.last||[1,1];
 const locked=s.rolling||s.moving||s.winner!==null,canRoll=!locked&&!s.rolls.length;
 const big='<div class="nj-dice-controls" aria-label="Physical style dice">'+faces.slice(0,s.diceCount).map((n,i)=>{
  const active=!!s.rolls.length&&!s.rolls[i]?.used,selectable=active&&canForDie(s,i),selected=i===s.selectedDie;
  const action=s.rolls.length?'data-ludo-die="'+i+'"':'data-action="ludo-roll"';
  return '<button type="button" '+action+' class="nj-die-button'+(selected?' is-selected':'')+(s.rolling?' is-tumbling':'')+(s.rolls[i]?.used?' is-used':'')+'" aria-label="Die '+(i+1)+', '+n+(selectable?', choose this die':'')+'" '+((s.rolls.length?!selectable:!canRoll)||locked?'disabled':'')+'>'+diceFace(n)+'</button>';
 }).join("")+'</div>';
 const progress=ui.scoreList(s.players,x=>x.tokens.filter(pos=>pos===56).length+"/"+x.tokens.length+" home");
 const pending=s.rolls.length?'<div class="nj-active-rolls">Rolled: '+s.rolls.map((d,i)=>'<span class="nj-number'+(d.used?' is-used':'')+(i===s.selectedDie?' is-selected':'')+'">'+d.value+(d.used?' ✓':'')+'</span>').join("")+' <span class="small">Tap a die to choose which value to use.</span></div>':"";
 const soundToggle='<button type="button" class="nj-sound" data-action="ludo-sound" aria-pressed="'+s.sound+'">'+(s.sound?'♫ Sound on':'♪ Sound off')+'</button>';
 const switches=[["barrier","Barriers","Stacked counters block rivals unless a 6 is thrown"],["safeHouse","Safe houses","Protect the four marked squares"],["captureFinishes","Finish on capture","Your counter goes home after taking an opponent"],["bonusOnCapture","Roll again after capture","An extra throw when you capture"],["directCount","Direct count","Skip individual counter hops"],["visualHand","Show hand indicator","Finger points towards the dice"]];
 const optionDisabled=locked||s.rolls.length?'disabled':'';
 const config='<div class="nj-options"><label>Dice <select class="select" data-dice-mode="ludo" '+optionDisabled+'><option value="2"'+(s.diceCount===2?' selected':'')+'>2 dice · Naija</option><option value="1"'+(s.diceCount===1?' selected':'')+'>1 die · Classic</option></select></label>'+'<div class="nj-player-config">'+ui.choosePlayers("ludo",s.players.length,4)+'</div></div>';
 const settings='<details class="nj-settings"><summary>⚙ Game options <span>· Play like Naija Ludo</span></summary><div class="nj-switches"><label class="nj-speed"><span><b>Opponent</b><small>Local friends or offline computer</small></span><select class="select" data-ludo-opponent '+optionDisabled+'><option value="local"'+(s.opponent==="local"?" selected":"")+'>Friend (local)</option><option value="computer"'+(s.opponent==="computer"?" selected":"")+'>Computer</option></select></label>'+(s.opponent==="computer"?'<label class="nj-speed"><span><b>Computer difficulty</b><small>Four levels of tactical play</small></span><select class="select" data-ludo-difficulty '+optionDisabled+'>'+["easy","normal","hard","advanced"].map(d=>'<option value="'+d+'" '+(d===s.difficulty?"selected":"")+'>'+d[0].toUpperCase()+d.slice(1)+'</option>').join("")+'</select></label>':"")+switches.map(([key,name,desc])=>'<label class="nj-switch"><span><b>'+name+'</b><small>'+desc+'</small></span><input type="checkbox" data-ludo-option="'+key+'" '+(s.options[key]?'checked':'')+' '+optionDisabled+'></label>').join('')+'<label class="nj-speed"><span><b>Counter movement</b><small>Adjust animation speed</small></span><select class="select" data-ludo-speed '+optionDisabled+'><option value="slow"'+(s.options.speed==="slow"?' selected':'')+'>Slow</option><option value="normal"'+(s.options.speed==="normal"?' selected':'')+'>Normal</option><option value="fast"'+(s.options.speed==="fast"?' selected':'')+'>Fast</option></select></label></div></details>';
 const btn=s.rolls.length||s.moving?'<div class="nj-move-hint" role="note">'+(s.moving?'Counter moving…':'Tap a glowing counter to move')+'</div>':'<button class="primary nj-roll-btn" type="button" data-action="ludo-roll" '+(canRoll?'':'disabled')+'>'+(s.rolling?'Rolling…':'Throw dice 🎲')+'</button>';
 ui.controls.innerHTML='<div class="nj-roll-station"><div class="nj-dice-caption">'+(s.rolls.length?'CHOOSE A DIE, THEN MOVE':'TAP THE DICE TO ROLL')+'</div>'+big+btn+pending+'</div>'+(s.winner!==null?'<div class="nj-win-banner" role="status">🏆 '+s.players[s.winner].name+' wins!</div>':'')+progress+config+soundToggle+settings;
 ui.rules("Naija Ludo house rules","Two dice, two houses each in a two-player match, <b>eight counters each</b>. Roll a 6 to release a counter; play the dice before throwing again. By default, capturing sends your counter to the finish and the rival back to their yard. A pair stacked on its opening square forms a barrier. Configure safe houses, capture rules, movement speed and direct count in Game options. <b>Local play only.</b>");
 if(motion)motion.play(ui.board,before,s);
 ui.save();
 if(ui.draw)scheduleComputer(s,ui.draw);
}
function changePlayers(s,n){cancel();const opts={...s.options};const fresh=create(n,s.diceCount);fresh.sound=s.sound;Object.assign(s,fresh);s.options=opts;return s;}
function changeOpponent(s,mode){
 if(s.rolling||s.moving||s.rolls.length)return false;
 if(!["computer","local"].includes(mode))return false;
 cancel();const opts={...s.options},soundOn=s.sound,difficulty=s.difficulty;
 const fresh=create(2,s.diceCount);Object.assign(s,fresh);s.options=opts;s.sound=soundOn;s.opponent=mode;s.difficulty=difficulty;
 if(mode==="computer")s.players[1].name="Computer";s.msg=mode==="computer"?"Playing against the computer. Red starts.":"Pass the phone to a friend. Red starts.";return true;
}
function changeDifficulty(s,difficulty){if(!["easy","normal","hard","advanced"].includes(difficulty))return false;s.difficulty=difficulty;s.msg="Computer difficulty changed.";return true;}
function changeOption(s,key,value){if(s.rolling||s.moving||s.rolls.length||s.winner!==null)return false;
 if(key==="speed"){if(!["slow","normal","fast"].includes(value))return false;}
 else if(!(key in DEFAULT_OPTIONS)||key==="speed"||typeof value!=="boolean")return false;
 s.options[key]=value;s.msg="Game settings updated.";return true;}
function changeDice(s,n){if(s.rolling||s.moving||s.rolls.length)return false;s.diceCount=n===1?1:2;s.last=s.diceCount===1?[1]:[1,1];s.msg="Dice setting changed. Your next throw uses "+s.diceCount+(s.diceCount===1?" die.":" dice.");return true;}
function selectDie(s,i,draw){if(s.rolling||s.moving||s.winner!==null||!s.rolls[i]||s.rolls[i].used)return;if(canForDie(s,i)){s.selectedDie=i;s.die=s.rolls[i].value;s.msg="Die "+(i+1)+" selected — move "+s.rolls[i].value+" spaces.";draw();}}
function roll(s,draw){
 if(s.rolling||s.moving||s.rolls.length||s.winner!==null)return;
 cancel();const generation=life,actual=Array.from({length:s.diceCount},rand6);
 s.rolling=true;s.preview=actual;s.msg="Shaking…";draw();
 sound(s,310,.09,.08);
 let ticks=0;ticker=setInterval(()=>{if(life!==generation){clearInterval(ticker);ticker=null;return;}ticks++;if(ticks%3===0)sound(s,340+ticks*11,.04,.025);},74);
 const ms=reduced()?105:760;
 moveTimer=setTimeout(()=>{
  if(life!==generation)return;
  clearInterval(ticker);ticker=null;moveTimer=null;
  s.rolling=false;s.rolls=actual.map(value=>({value,used:false}));
  s.last=actual;s.rolled=true;s.bonus=actual.some(n=>n===6);
  s.msg="Dice: "+actual.join(" and ")+".";
  finishIfDone(s);
  if(!s.rolls.length&&!s.msg.includes("No moves"))s.msg="No legal moves this throw. "+s.msg;
  sound(s,590,.095,.06);draw();
 },ms);
}
function capture(s,pi,ti){
 const me=s.players[pi],pos=me.tokens[ti];
 if(pos<0||pos>=51)return {text:"",hit:false};
 const idx=currentIndex(tokenHouse(me,ti),pos);
 if(isSafe(s,idx))return {text:"",hit:false};
 let hit=false,victim="";
 for(let oi=0;oi<s.players.length&&!hit;oi++){
  if(oi===pi)continue;
  const other=s.players[oi];
  for(let j=0;j<other.tokens.length;j++){
   const n=other.tokens[j];
   if(n>=0&&n<51&&currentIndex(tokenHouse(other,j),n)===idx){
    other.tokens[j]=-1;hit=true;victim=other.name;break;
   }
  }
 }
 if(hit){
  if(s.options.captureFinishes)me.tokens[ti]=56;
  if(s.options.bonusOnCapture)s.bonus=true;
 }
 return {text:hit?" Knocked "+victim+"'s counter home!"+(s.options.captureFinishes?" Attacker also finished!":""):"",hit};
}
function move(s,pi,ti,draw){
 if(!legal(s,pi,ti,s.selectedDie))return;
 const di=s.selectedDie,steps=s.rolls[di].value,me=s.players[pi],before=me.tokens[ti];
 if(before<0){me.tokens[ti]=0;}else me.tokens[ti]=before;
 const remaining=before===-1?0:steps;
 const myLife=++life;
 s.moving=true;s.msg="Moving "+label(tokenHouse(me,ti))+" piece "+(ti%4+1)+"…";draw();
 const t=window.NightLudoMotion?window.NightLudoMotion.timing(s):{hop:reduced()?20:150,release:reduced()?30:320,capture:reduced()?30:440};
 function complete(){
  if(myLife!==life)return;
  s.rolls[di].used=true;s.die=null;
  const cap=capture(s,pi,ti),home=me.tokens[ti]===56;
  s.msg=(home?"Piece reached home!":before<0?"Piece entered the track!":"Moved "+steps+" squares.")+cap.text;
  function settle(){
   if(myLife!==life)return;
   s.moving=false;
   sound(s,home?740:cap.hit?280:540,home?.18:.07,home?.08:.05);
   if(me.tokens.every(x=>x===56)){s.winner=pi;s.rolls=[];s.selectedDie=null;s.msg="All pieces home. Champion!";draw();return;}
   finishIfDone(s);draw();
  }
  if(cap.hit||home){draw();moveTimer=setTimeout(settle,t.capture+20);}else settle();
 }
 if(!remaining){moveTimer=setTimeout(complete,t.release+20);return;}
 if(s.options.directCount){me.tokens[ti]+=remaining;draw();moveTimer=setTimeout(complete,t.release+20);return;}
 let count=0;const delay=t.hop+20;
 function hop(){
  if(myLife!==life)return;
  if(count<remaining){
   me.tokens[ti]++;count++;draw();
   setTimeout(()=>{if(myLife===life)sound(s,370+count*22,.035,.025);},t.hop);
   moveTimer=setTimeout(hop,delay);
  }else complete();
 }
 moveTimer=setTimeout(hop,delay);
}
function chooseComputerMove(s){
 const options=[];
 s.rolls.forEach((die,di)=>{if(die.used)return;s.players[1].tokens.forEach((at,ti)=>{
  if(!legal(s,1,ti,di))return;const house=tokenHouse(s.players[1],ti),destination=at<0?0:at+die.value;
  let score=at<0?22:Math.min(60,at)+Math.random()*18;
  if(destination>=51)score+=65;
  if(destination<51){
   const idx=currentIndex(house,destination);
   if(isSafe(s,idx))score+=20;
   const hit=s.players[0].tokens.some((n,j)=>n>=0&&n<51&&currentIndex(tokenHouse(s.players[0],j),n)===idx);
   if(hit&&!isSafe(s,idx))score+=125;
  }
  if(s.difficulty==="easy")score=Math.random();
  else if(s.difficulty==="normal")score=score*.65+Math.random()*33;
  else if(s.difficulty==="advanced"){score+=destination>=51?35:0;score+=die.value===6?5:0;}
  options.push({di,ti,score});
 });});
 return options.sort((a,b)=>b.score-a.score)[0]||null;
}
function scheduleComputer(s,draw){
 if(s.opponent!=="computer"||s.turn!==1||s.winner!==null||s.rolling||s.moving||aiTimer!==null)return;
 const generation=life;
 aiTimer=setTimeout(()=>{
  aiTimer=null;
  if(generation!==life||s.opponent!=="computer"||s.turn!==1||s.winner!==null||s.rolling||s.moving)return;
  if(!s.rolls.length){roll(s,draw);return;}
  const choice=chooseComputerMove(s);
  if(!choice){finishIfDone(s);draw();return;}
  s.selectedDie=choice.di;s.die=s.rolls[choice.di].value;
  move(s,1,choice.ti,draw);
 },reduced()?100:s.options.speed==="fast"?230:500);
}
const api={create,normalize,render,changePlayers,changeDice,changeOption,changeOpponent,changeDifficulty,selectDie,roll,move,cancel};
if(isTestHost){
 api.setTestRolls=values=>{if(!Array.isArray(values)||!values.every(x=>Number.isInteger(x)&&x>=1&&x<=6))throw Error("Invalid test dice");riggedForTest=[...values];};
 api.snapshot=s=>JSON.parse(JSON.stringify(s));
 api.testCapture=capture;api.testLegal=legal;api.testBarrier=blockedByBarrier;
}
window.NightLudo=Object.freeze(api);
})();
