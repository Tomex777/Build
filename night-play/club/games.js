/* NIGHT Play Club — phone-first local games. No remote players, no collection of personal data. */
(function(){
"use strict";
const $=id=>document.getElementById(id);
const board=$("board"),controls=$("controls"),status=$("status"),help=$("help"),picks=$("gamePicks");
const KEY="night-play-club-v1";
const state={};let active="checkers",timer=null,drawCtx=null;
let cardMotionBusy=false,cardMotionEpoch=0,pendingWhotDeal=false;
// Speed is intentionally unified: interaction should feel immediate,
// while cards still lift, glide, land and settle.
const whotMotion={
 player:465,cpu:480,draw:460,cpuDelay:235,
 deal:410,dealDelay:55,dealStagger:66,opponentDeal:440,
 settle:180,recenter:360,drawFlip:560
};
const cardMotionMetrics={deals:0,plays:0,cpuPlays:0,draws:0,activeFlights:0,lastFlightDuration:0,lastFlightAction:null};
const motionEnabled=()=>!matchMedia("(prefers-reduced-motion: reduce)").matches;
const colors={red:"#df3439",green:"#328c4b",yellow:"#e8b32a",blue:"#2767bc"};
const titles={ludo:["Nigerian Ludo","The board you remember.","BOARD GAME"],estate:["NIGHT Estates","Buy, trade and collect rent.","PROPERTY GAME"],whot:["WHOT","Classic Nigerian shape-and-number card play.","CARD GAME"],colors:["Colour Clash","A quick colour-matching card battle.","CARD GAME"],draw:["Draw & Guess","One draws. Everyone else guesses.","PARTY GAME"],snakes:["Snakes & Ladders","Climb fast. Slide carefully.","BOARD GAME"],checkers:["Draughts","A proper tabletop strategy game.","BOARD GAME"]};
function h(s){return String(s).replace(/[&<>"']/g,x=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[x]));}
function shuffle(a){for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
function die(){return Math.floor(Math.random()*6)+1;}
function save(){if(active==='ludo'&&(state.ludo?.rolling||state.ludo?.moving))return;try{const copy={...state};delete copy.draw;localStorage.setItem(KEY,JSON.stringify(copy));}catch{}}
function load(){try{
 const data=JSON.parse(localStorage.getItem(KEY)||"{}");
 for(const k of Object.keys(titles))if(data[k]&&typeof data[k]==="object")state[k]=data[k];
 // Replace older Shape Cards saves (69-card fictional deck) with the true
 // 54-card traditional WHOT deck, rather than continuing a corrupted match.
 const old=state.whot;
 if(old){
  const cards=[...(old.hands?.[0]||[]),...(old.hands?.[1]||[]),...(old.deck||[]),...(old.discard||[])];
  if(cards.length!==54||cards.some(card=>card.n!==20&&!whotNumbers[card.s]?.includes(card.n)))delete state.whot;
  else if(!Number.isInteger(old.pendingWhot)||old.turn!==0||old.hands[0]?.[old.pendingWhot]?.n!==20)old.pendingWhot=null;
 }
}catch{}}
function setStatus(t){status.textContent=t;}
function controlsHTML(markup){if(controls.innerHTML!==markup)controls.innerHTML=markup;}
function rules(title,desc){const markup="<h3>"+title+"</h3><p>"+desc+"</p>";if(help.innerHTML!==markup)help.innerHTML=markup;}
function scoreList(players,getScore){return '<div class="score-list">'+players.map((p,i)=>'<div class="score-row"><span><b style="color:'+h(p.color)+'">●</b> '+h(p.name)+'</span><b>'+h(getScore(p,i))+'</b></div>').join("")+"</div>";}
function choosePlayers(id,selected,max){return '<label class="small">Players (local, sharing one phone)</label><select class="select" data-players="'+id+'">'+[2,3,4].filter(n=>n<=max).map(n=>'<option value="'+n+'"'+(n===selected?" selected":"")+'>'+n+' players</option>').join("")+"</select>";}
function pick(id){if(!titles[id])id="checkers";cardMotionEpoch++;cardMotionBusy=false;active=id;if(id==="ludo")window.NightLudo3D?.warmup();document.body.classList.toggle("ludo-mode",id==="ludo");document.body.classList.toggle("checkers-mode",id==="checkers");document.body.classList.toggle("whot-mode",id==="whot");$("whotNewRoundMenu").hidden=id!=="whot";$("whotModeBadge").hidden=id!=="whot";$("ludoTopbar").hidden=id!=="ludo";$("ludoHud").hidden=id!=="ludo";if(id!=="ludo")closeLudoSettings();const d=titles[id];document.title=d[0]+" · NIGHT Play";if($("settingsDialog").open)$("settingsDialog").close();$("gameTitle").textContent=d[0];$("gameSubtitle").textContent=d[1];$("gameCategory").textContent=d[2];[...picks.querySelectorAll("button")].forEach(x=>x.setAttribute("aria-current",String(x.dataset.pick===id)));const url=new URL(location.href);url.searchParams.set("game",id);history.replaceState(null,"",url.pathname+url.search);window.NightLudo.cancel();if(id==="ludo"&&state.ludo)state.ludo=window.NightLudo.resume(state.ludo);if(!state[id])reset(id);else render();whotConfigUI();}
function reset(id){
if(id==="ludo"){window.NightLudo.cancel();state.ludo=window.NightLudo.create();}
if(id==="estate"){state.estate={players:["red","blue"].map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],cash:1500,pos:0,bankrupt:false})),owners:{},upgrades:{},turn:0,rolled:false,last:0,pending:null,rounds:0,winner:null,msg:"Roll the dice to start buying properties.",inspect:0};}
if(id==="whot"){state.whot=newShapes();pendingWhotDeal=true;cardMotionEpoch++;cardMotionBusy=false;}
if(id==="colors"){state.colors=newColors();}
if(id==="draw"){state.draw={players:["Player 1","Player 2"],drawer:0,word:null,mode:"ready",points:[0,0],round:1,seconds:60,log:[]};}
if(id==="snakes"){state.snakes={players:[{name:"Red",color:colors.red,pos:1},{name:"Blue",color:colors.blue,pos:1}],turn:0,last:0,msg:"Roll to start climbing.",winner:null};}
if(id==="checkers"){const cells=Array(64).fill(null);for(let r=0;r<8;r++)for(let c=0;c<8;c++)if((r+c)%2===1){if(r<3)cells[r*8+c]="b";if(r>4)cells[r*8+c]="w";}state.checkers={cells,turn:"w",selected:null,forced:null,winner:null,msg:"White moves first."};}
save();render();
}
function render(){if(timer){clearInterval(timer);timer=null;}if(active==="ludo")renderLudo();if(active==="estate")renderEstate();if(active==="whot"){renderCards("whot");$("whotModeBadge").textContent=whotOptions.mode==="career"?whotRank().name.toUpperCase():whotOptions.mode==="tournament"?"TOURNAMENT":"VS CPU";}if(active==="colors")renderCards("colors");if(active==="draw")renderDraw();if(active==="snakes")renderSnakes();if(active==="checkers")renderCheckers();save();}
const track=[[6,1],[6,2],[6,3],[6,4],[6,5],[5,6],[4,6],[3,6],[2,6],[1,6],[0,6],[0,7],[0,8],[1,8],[2,8],[3,8],[4,8],[5,8],[6,9],[6,10],[6,11],[6,12],[6,13],[6,14],[7,14],[8,14],[8,13],[8,12],[8,11],[8,10],[8,9],[9,8],[10,8],[11,8],[12,8],[13,8],[14,8],[14,7],[14,6],[13,6],[12,6],[11,6],[10,6],[9,6],[8,5],[8,4],[8,3],[8,2],[8,1],[8,0],[7,0],[6,0]];
const starts={red:0,green:13,yellow:26,blue:39};
const safe=new Set([0,8,13,21,26,34,39,47]);
const home={red:[[7,1],[7,2],[7,3],[7,4],[7,5],[7,7]],green:[[1,7],[2,7],[3,7],[4,7],[5,7],[7,7]],yellow:[[7,13],[7,12],[7,11],[7,10],[7,9],[7,7]],blue:[[13,7],[12,7],[11,7],[10,7],[9,7],[7,7]]};
function pieceCell(player,pos){if(pos<0)return null;if(pos>=51)return home[player.key][pos-51];return track[(starts[player.key]+pos)%52];}
function ludoValid(s,pi,ti){if(s.winner!==null||!s.rolled||s.turn!==pi)return false;const p=s.players[pi].tokens[ti];return p===-1?s.die===6:p<56&&p+s.die<=56;}
function renderLudo(){
 state.ludo=window.NightLudo.normalize(state.ludo);
 const wasOpen=!!controls.querySelector(".nj-settings[open]");
 const oldHud=window.NightLudoMotion.captureHud($("ludoHud"));
 window.NightLudo.render(state.ludo,{board,controls,status,help,save,scoreList,choosePlayers,rules,draw:render});
 if(wasOpen){const d=controls.querySelector(".nj-settings");if(d)d.open=true;}
 const s=state.ludo,players=s.players,turn=players[s.turn],hud=$("ludoHud");
 const shown=players;
 hud.dataset.players=String(players.length);
 hud.innerHTML=shown.map((p,idx)=>{
  const count=p.tokens.filter(n=>n===56).length,active=idx===s.turn,team=p.houses||[p.key];
  return '<div class="player'+(active?' turn':'')+'"><div class="playername">'+team.map(k=>'<span class="p-dot" style="--team:'+({red:"#e7493f",green:"#409e51",blue:"#2c82c8",yellow:"#ffcf23"}[k]||"#eee")+'"></span>').join('')+h(p.name)+'</div><div class="value">'+count+'<span class="ludo-hud-progress"> / '+p.tokens.length+'</span></div></div>';
 }).join('');
 hud.insertAdjacentHTML('beforeend','<div class="turnmid"><span>'+(s.winner!==null?'WINNER':s.rolling?'ROLLING':s.moving?'MOVING':'TURN')+'</span>'+(s.winner!==null?'🏆':s.rolls.length?s.rolls.filter(d=>!d.used).map(d=>d.value).join(' + '):'🎲')+'</div>');
 window.NightLudoMotion.playHud(hud,oldHud,s);
 const details=controls.querySelector(".nj-settings");
 if(details){
  details.addEventListener("toggle",()=>{if(!details.isConnected||details.dataset.closing==='true')return;const open=details.open;$("ludoSheetBack").hidden=!open;$("ludoSettingsBtn").setAttribute("aria-expanded",String(open));});
  details.querySelector('summary').addEventListener('click',e=>{e.preventDefault();toggleLudoSettings();});
 }
 const options=controls.querySelector(".nj-options");
 if(details&&options)details.insertBefore(options,details.querySelector(".nj-switches"));
 if(details&&!details.querySelector(".nj-in-sheet-extra")){
  details.insertAdjacentHTML("beforeend",'<div class="nj-in-sheet-extra"><button type="button" class="secondary" data-action="ludo-new"><svg class="action-svg" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11a9 9 0 1 0 3-6.7L3 7m0-5v5h5"/></svg> New match</button><button type="button" class="secondary" data-action="ludo-sound">'+(s.sound?'♫ Sound ON':'♪ Sound OFF')+'</button></div>');
 }
 if(controls.querySelector(".nj-settings[open]")){$("ludoSheetBack").hidden=false;$("ludoSettingsBtn").setAttribute("aria-expanded","true");}
 window.NightLudo3D?.sync();
}
function closeLudoSettings(){const d=controls.querySelector(".nj-settings");if(d?.open)window.NightLudoMotion.sheet(d,$("ludoSheetBack"),false);else $("ludoSheetBack").hidden=true;$("ludoSettingsBtn").setAttribute("aria-expanded","false");}
function toggleLudoSettings(){const d=controls.querySelector('.nj-settings');if(!d)return;const open=!d.open||d.dataset.closing==='true';window.NightLudoMotion.sheet(d,$('ludoSheetBack'),open);$('ludoSettingsBtn').setAttribute('aria-expanded',String(open));}
$("ludoSettingsBtn").addEventListener("click",toggleLudoSettings);
$("ludoSheetBack").addEventListener("click",closeLudoSettings);
document.addEventListener("keydown",e=>{if(e.key==="Escape")closeLudoSettings();});
function ludoRoll(){if(state.ludo.opponent==='computer'&&state.ludo.turn===1)return;window.NightLudo.roll(state.ludo,render);}
function ludoMove(pi,ti){if(state.ludo.opponent==='computer'&&state.ludo.turn===1)return;window.NightLudo.move(state.ludo,pi,ti,render);}

const places=["START","Eko Marina","Yaba","Community Fund","Surulere","Kaduna Road","Transit","Lekki","Sabo","Ikeja","VISIT","Ibadan","Bodija","Market Bonus","Akure","Warri","Coastal Rail","Benin","Asaba","Sapele","REST","Enugu","Nsukka","Electric Bill","Aba","Owerri","Metro Rail","Uyo","Calabar","Ikot Ekpene","HOLD","Jos","Bukuru","Lottery","Minna","Abuja Central","Capital Rail","Gwarinpa","Maitama","Luxury Tax"];
const skipSpaces=new Set([0,3,6,10,13,16,20,23,26,30,33,36,39]);const groupColors=["#ac7c52","#8bc7d5","#e1a7b7","#e7a13e","#e64d46","#daca6f","#7cb875","#3980ba"];
function estateType(i){if(i===0)return "start";if(i===10||i===20)return "free";if(i===30)return "hold";if(i===3||i===13||i===33)return "bonus";if(i===23||i===39)return "tax";if(i===6||i===16||i===26||i===36)return "rail";return "property";}
function estateColor(i){return groupColors[Math.min(7,Math.floor(Math.max(0,i-1)/5))];}
function estatePrice(i){return estateType(i)==="rail"?180:100+Math.floor(i/5)*35;}
function estateRent(i,houses){return Math.round(estatePrice(i)*(houses+1)*.14);}
function estateTurn(s){s.turn=(s.turn+1)%s.players.length;s.rounds++;s.rolled=false;s.pending=null;let count=0;while(s.players[s.turn].bankrupt&&count++<s.players.length)s.turn=(s.turn+1)%s.players.length;const remaining=s.players.filter(p=>!p.bankrupt);if(remaining.length===1)s.winner=s.players.indexOf(remaining[0]);}
function estateAuto(s){
let p=s.players[s.turn],sq=p.pos,t=estateType(sq);s.inspect=sq;
if(t==="property"||t==="rail"){
const owner=s.owners[sq];if(owner===undefined){if(p.cash>=estatePrice(sq)){s.pending=sq;s.msg="Would you like to buy "+places[sq]+"?";return;}s.msg="You cannot afford this property.";}
else if(owner!==s.turn){const rent=estateRent(sq,s.upgrades[sq]||0);p.cash-=rent;s.players[owner].cash+=rent;s.msg="Paid ₦"+rent+" rent to "+s.players[owner].name+".";if(p.cash<0){p.bankrupt=true;s.msg+=" Bankrupt!";}}
else{s.msg="Your property!";if((s.upgrades[sq]||0)<3&&p.cash>=130){s.pending="upgrade:"+sq;return;}}
}
else if(t==="bonus"){p.cash+=150;s.msg="Bonus! Collect ₦150.";}
else if(t==="tax"){p.cash-=100;s.msg="Pay ₦100 in city taxes.";}
else if(t==="hold"){p.cash-=80;s.msg="Pay ₦80 to leave the holding area.";}
else if(t==="start"){p.cash+=100;s.msg="Back at Start! Collect ₦100.";}
else{s.msg="Safe landing.";}
if(p.cash<0){p.bankrupt=true;s.msg+=" Bankrupt!";}estateTurn(s);
}
function estateRoll(){const s=state.estate;if(s.rolled||s.pending!==null||s.winner!==null)return;const steps=die()+die(),p=s.players[s.turn],was=p.pos;p.pos=(p.pos+steps)%40;if(p.pos<was)p.cash+=200;s.last=steps;s.rolled=true;s.msg=p.name+" rolled "+steps+". ";estateAuto(s);render();}
function estateDecision(buy){const s=state.estate;if(s.pending===null)return;const p=s.players[s.turn];if(typeof s.pending==="number"&&buy){const i=s.pending,pay=estatePrice(i);if(p.cash>=pay){p.cash-=pay;s.owners[i]=s.turn;s.msg="Purchased "+places[i]+" for ₦"+pay+".";}}
if(typeof s.pending==="string"&&buy){const i=+s.pending.split(":")[1];if(p.cash>=130){p.cash-=130;s.upgrades[i]=(s.upgrades[i]||0)+1;s.msg="Built a house at "+places[i]+".";}}
if(!buy)s.msg="Skipped purchase.";estateTurn(s);render();}
function estateNet(s,p){return p.cash+Object.keys(s.owners).reduce((sum,i)=>sum+(s.owners[i]===s.players.indexOf(p)?estatePrice(+i)+130*(s.upgrades[i]||0):0),0);}
function estateEnd(){const s=state.estate;if(s.winner!==null)return;let best=-Infinity;for(let i=0;i<s.players.length;i++){let amount=estateNet(s,s.players[i]);if(amount>best){best=amount;s.winner=i;}}s.msg="Highest total wealth wins.";render();}
function renderEstate(){
const s=state.estate;let html='<div class="estate-board" aria-label="40-space property trading board">';
for(let i=0;i<40;i++){let r,c;if(i<=10){r=11;c=11-i;}else if(i<=20){r=21-i;c=1;}else if(i<=30){r=1;c=i-19;}else{r=i-29;c=11;}
const owner=s.owners[i],t=estateType(i);let markers=s.players.map((p,pi)=>p.pos===i&&!p.bankrupt?'<span style="color:'+p.color+'">●</span>':"").join("");
html+='<button type="button" class="estate-tile'+(s.players[s.turn].pos===i?' current':'')+'" style="grid-row:'+r+';grid-column:'+c+';--tile-color:'+estateColor(i)+'" data-estate-info="'+i+'" aria-label="'+h(places[i])+', space '+i+'"><span class="strip"></span><span class="tile-label">'+h(places[i])+'</span><span class="players">'+markers+'</span>'+((owner!==undefined)?'<span class="house-tag">■'+(s.upgrades[i]||0)+'</span>':'')+'</button>';
}
html+='<div class="estate-center"><div><b>NIGHT<br>ESTATES</b><small>BUY · TRADE · BUILD</small><p>Own the neighbourhood.</p></div></div></div>';board.innerHTML=html;
const p=s.players[s.turn],info=s.inspect;setStatus(s.winner!==null?s.players[s.winner].name+" wins with ₦"+estateNet(s,s.players[s.winner])+" in wealth!":p.name+"'s turn. "+s.msg);
let action=s.pending!==null?'<div class="row"><button class="primary" data-action="estate-buy">Buy / Build</button><button class="secondary" data-action="estate-pass">Pass</button></div>':'<button class="primary" data-action="estate-roll" '+(s.winner!==null?"disabled":"")+'>Roll two dice</button>';
controlsHTML('<div class="dice">'+(s.last||"🎲")+'</div>'+action+scoreList(s.players,x=>"₦"+x.cash+" · ₦"+estateNet(s,x)+" total")+'<p class="small">Round '+s.rounds+' · Last roll '+s.last+'</p><p class="small"><b>Inspect:</b> '+h(places[info])+' · '+(skipSpaces.has(info)?estateType(info):'₦'+estatePrice(info))+(s.owners[info]!==undefined?' · owned by '+s.players[s.owners[info]].name:'')+'</p><button type="button" class="secondary" data-action="estate-end">End & score game</button><div style="margin-top:14px">'+choosePlayers("estate",s.players.length,4)+'</div>');
rules("NIGHT Estates rules","Roll two dice, buy unowned locations, collect rent when opponents land on your properties and build houses to increase rent. Passing START gives ₦200. Landed-property purchases are optional. Use <b>End & score game</b> to compare cash and property value. This is an original property-trading board, not Hasbro's Monopoly.");
}

const shapes=["Circle","Triangle","Cross","Square","Star"],icons=["●","▲","✚","■","★"];
// Traditional Nigerian WHOT has 49 shape cards plus five WHOT 20 wildcards.
// Earlier Shape Cards incorrectly allowed non-existent values (e.g. Square 6).
const whotNumbers=[
 [1,2,3,4,5,7,8,10,11,12,13,14], // Circle
 [1,2,3,4,5,7,8,10,11,12,13,14], // Triangle
 [1,2,3,5,7,10,11,13,14],         // Cross
 [1,2,3,5,7,10,11,13,14],         // Square
 [1,2,3,4,5,7,8]                 // Star
];
// NIGHT-specific rule presets. Nigerian WHOT has regional house rules;
// strict check-up and penalty-defence are explicit, configurable choices.
const WHOT_OPTIONS_KEY="night-play-whot-options-v2",WHOT_CAREER_KEY="night-play-whot-career-v1";
const whotRankNames=["Kindergarten","Primary","Secondary","Academy","Rookie","Soldier","Corporal","Sergeant","Captain","Major","General"];
function whotRead(key,fallback){try{return {...fallback,...JSON.parse(localStorage.getItem(key)||"{}")}}catch{return {...fallback}}}
const whotOptions=whotRead(WHOT_OPTIONS_KEY,{mode:"quick",strictFinish:true,defend:true,announce:false,difficulty:"normal"});
const whotCareer=whotRead(WHOT_CAREER_KEY,{wins:0,losses:0,streak:0,bestStreak:0,tournamentWins:[0,0]});
function whotStore(){try{localStorage.setItem(WHOT_OPTIONS_KEY,JSON.stringify(whotOptions));localStorage.setItem(WHOT_CAREER_KEY,JSON.stringify(whotCareer))}catch{}}
function whotRank(){const level=Math.min(whotRankNames.length-1,Math.floor(whotCareer.wins/3));return {name:whotRankNames[level],level,next:level===whotRankNames.length-1?null:3-whotCareer.wins%3}}
function whotAward(s,pi){
 if(s.winner!==null)return;
 s.winner=pi;s.msg=pi===0?"You win! Check up!":"Computer wins.";
 if(whotOptions.mode==="career"){
  if(pi===0){const oldRank=whotRank().level;whotCareer.wins++;whotCareer.streak++;whotCareer.bestStreak=Math.max(whotCareer.bestStreak,whotCareer.streak);const nowRank=whotRank();if(nowRank.level>oldRank)s.promotion=nowRank.name}
  else{whotCareer.losses++;whotCareer.streak=0}
 }else if(whotOptions.mode==="tournament"){
  if(!Array.isArray(whotCareer.tournamentWins))whotCareer.tournamentWins=[0,0];
  whotCareer.tournamentWins[pi]++;
  const score=whotCareer.tournamentWins;
  s.msg+=score[0]>=2?" You are the tournament champion!":score[1]>=2?" CPU takes the tournament!":" Tournament: "+score[0]+"–"+score[1]+".";
 }
 whotStore();
}
function whotNeedsFinishCard(card){return whotOptions.strictFinish&&[1,2,5,8,14,20].includes(card.n)}
function whotPending(s){return s.pendingPenalty&&s.pendingPenalty.target===s.turn?s.pendingPenalty:null}
function whotCanPlay(s,card){
 const pending=whotPending(s);
 if(pending)return whotOptions.defend&&card.n===pending.n;
 const top=topCard(s);
 return card.n===20||card.s===(s.called===null?top.s:s.called)||card.n===top.n;
}
function whotConfigUI(){
 const root=$("whotModeConfig");if(!root)return;const rank=whotRank();
 root.hidden=active!=="whot";
 if(root.hidden)return;
 for(const sel of root.querySelectorAll("[data-whot-option]")){
  const key=sel.dataset.whotOption;if(sel.type==="checkbox")sel.checked=Boolean(whotOptions[key]);else sel.value=whotOptions[key];
 }
 const label=$("whotCareerProgress");if(label)label.textContent=
  whotOptions.mode==="career"?"RANK "+(rank.level+1)+" · "+rank.name+" · "+whotCareer.wins+" wins"+(rank.next?" · "+rank.next+" to promotion":" · Max rank"):
  whotOptions.mode==="tournament"?"FIRST TO TWO · YOU "+(whotCareer.tournamentWins?.[0]||0)+" – CPU "+(whotCareer.tournamentWins?.[1]||0):
  "Classic one-round game against CPU.";
 const pill=$("whotModeBadge");if(pill)pill.textContent=whotOptions.mode==="career"?rank.name.toUpperCase():whotOptions.mode==="tournament"?"TOURNAMENT":"VS CPU";
}

function newShapes(){
 const deck=whotNumbers.flatMap((values,s)=>values.map(n=>({s,n})));
 for(let i=0;i<5;i++)deck.push({s:5,n:20});
 shuffle(deck);
 const hands=[deck.splice(0,5),deck.splice(0,5)];
 // The opening card should be a numbered shape so a called suit is never
 // undefined. The wild 20 card still remains inside the playable draw deck.
 const first=deck.findIndex(c=>c.n!==20);
 const initial=deck.splice(first,1)[0];
 return {hands,deck,discard:[initial],turn:0,winner:null,called:null,pendingWhot:null,pendingPenalty:null,pendingFinish:null,calledLast:[false,false],msg:"Match the shape or number. Go to market if needed.",round:0};
}
function newColors(){let deck=[];for(let c=0;c<4;c++){for(let n=0;n<=9;n++)deck.push({c,n:String(n)});for(const n of ["SKIP","REV","+2"])for(let k=0;k<2;k++)deck.push({c,n});}for(let k=0;k<4;k++){deck.push({c:4,n:"WILD"},{c:4,n:"+4"});}shuffle(deck);return {hands:[deck.splice(0,7),deck.splice(0,7)],deck,discard:[deck.shift()],turn:0,winner:null,called:null,msg:"Match the colour or symbol.",round:0};}
function topCard(s){return s.discard[s.discard.length-1];}
function take(s,pi,n){for(let j=0;j<n;j++){if(!s.deck.length){if(s.discard.length<=1)break;const card=s.discard.pop();s.deck=shuffle(s.discard.splice(0));s.discard=[card];}if(s.deck.length)s.hands[pi].push(s.deck.pop());}}
function playable(kind,s,card){const top=topCard(s);return kind==="whot"?whotCanPlay(s,card):(card.c===4||card.c===(s.called===null?top.c:s.called)||card.n===top.n);}
function nextCardTurn(kind,s,pi,card,selected){
 s.called=null;s.pendingWhot=null;s.discard.push(card);s.round++;
 const hand=s.hands[pi],opp=1-pi;
 if(kind!=="whot"){
  if(!hand.length){s.winner=pi;s.msg=pi===0?"You win!":"Computer wins.";return}
  let again=false,draw=0;
  if(card.c===4)s.called=selected;
  if(card.n==="SKIP"||card.n==="REV")again=true;
  if(card.n==="+2")draw=2;if(card.n==="+4")draw=4;
  if(draw){take(s,opp,draw);again=true}
  s.turn=again?pi:opp;s.msg=(pi===0?"You":"Computer")+" played "+card.n;return;
 }
 if(card.n===20)s.called=selected;
 let again=card.n===1||card.n===8,draw=0;
 if(card.n===14){take(s,opp,1);again=true;draw=1}
 if(card.n===2||card.n===5){
  const pending=s.pendingPenalty&&s.pendingPenalty.n===card.n?s.pendingPenalty.amount:0;
  s.pendingPenalty={n:card.n,target:opp,amount:pending+(card.n===2?2:3)};
 }else{s.pendingPenalty=null}
 // Optional spoken 'Last card' / 'Check up' house rule. Don't penalize CPU
 // unfairly: it announces its last card automatically.
 if(hand.length===1&&pi===1){s.calledLast=[...(s.calledLast||[false,false])];s.calledLast[1]=true}
 if(!hand.length){
  if(whotNeedsFinishCard(card)){
   // A final action card does not end strict-check-up rounds. The player
   // must go to market before the round can end, including on a final 5.
   s.pendingFinish=pi;s.turn=pi;
   s.msg=(pi===0?"You":"CPU")+" played a final "+card.n+" — no check-up on action cards. "+(pi===0?"Pick from market to continue.":"CPU must pick from market.");
   return;
  }
  if(pi===0&&whotOptions.announce&&!(s.calledLast?.[0])){
   take(s,0,2);s.turn=opp;s.msg="Missing LAST CARD call: pick two penalty cards.";return;
  }
  whotAward(s,pi);return;
 }
 if(hand.length===1&&pi===0)s.calledLast=[false,(s.calledLast||[])[1]||false];
 s.msg=(pi===0?"You":"CPU")+" played "+(card.n===20?"WHOT 20 · called "+shapes[selected]:card.n+" "+shapes[card.s])+
  (draw?" · opponent picks "+draw:card.n===2||card.n===5?" · "+(card.n===2?"Pick Two":"Pick Three"):"")+
  (again?" · play again":"")+(hand.length===1&&pi===0?" · LAST CARD!":"");
 s.turn=again?pi:opp;
}
/* GPU-composited, hardcoded card motion. No re-render on animation frames. */
// Independent implementation of the visual principles used by FLIP card UIs.
// The referenced React Whot application has no published code license, so no
// AkpetiTrust code or art is imported; NIGHT keeps its existing vanilla engine.
async function whotFly(origin,destination,{face=null,action="play"}={}){
 if(!origin||!destination||matchMedia('(prefers-reduced-motion: reduce)').matches)return;
 const a=origin.getBoundingClientRect(),b=destination.getBoundingClientRect();
 if(!a.width||!b.width)return;
 const flip=action==='draw'||action==='cpu';
 let ghost;
 let flipCore=null;
 if(flip){
  ghost=document.createElement('div');ghost.className='whot-flight whot-flight-flip';
  flipCore=document.createElement('div');flipCore.className='whot-flip-core';
  const back=document.createElement('div');back.className='whot-flip-side whot-flip-back';
  back.appendChild(origin.cloneNode(true));
  const front=document.createElement('div');front.className='whot-flip-side whot-flip-front';
  const safe=document.createElement('div');
  if(action==='cpu'||(action==='draw'&&face))safe.innerHTML=whotFace(face,-1,false);
  else safe.appendChild(destination.cloneNode(true));
  const card=safe.firstElementChild;if(card){
   card.removeAttribute('data-card');card.disabled=true;
   front.appendChild(card);
  }
  flipCore.append(back,front);ghost.appendChild(flipCore);
 }else{
  ghost=origin.cloneNode(true);ghost.classList.add('whot-flight');ghost.removeAttribute('data-card');
 }
 Object.assign(ghost.style,{position:'fixed',left:a.left+'px',top:a.top+'px',
  width:a.width+'px',height:a.height+'px',margin:'0',zIndex:'9999',
  visibility:'visible',pointerEvents:'none'});
 document.body.appendChild(ghost);
 const oldVisibility=origin.style.visibility;
 const previousDestVisibility=destination.style.visibility;
 if(action==='play')origin.style.visibility='hidden';
 if(action==='draw')destination.style.visibility='hidden';
 const dx=b.left+b.width/2-a.left-a.width/2;
 const dy=b.top+b.height/2-a.top-a.height/2;
 const scale=b.width/a.width;
 const duration=action==='cpu'?whotMotion.cpu:action==='draw'?whotMotion.draw:whotMotion.player;
 cardMotionMetrics.lastFlightDuration=duration;cardMotionMetrics.lastFlightAction=action;
 cardMotionMetrics.activeFlights++;
 try{
  // The card reacts immediately: a short lift, measured glide and cushioned
  // landing. No timeout is inserted between tapping and visual feedback.
  const flight=ghost.animate([
   {offset:0,transform:'translate3d(0,0,0) scale(1)',opacity:1},
   {offset:.16,transform:'translate3d('+(dx*.12)+'px,'+(dy*.12-6)+'px,0) scale(1.025)',opacity:1},
   {offset:.72,transform:'translate3d('+(dx*.73)+'px,'+(dy*.73-4)+'px,0) scale('+(1.025+(scale-1)*.7)+')',opacity:1},
   {offset:1,transform:'translate3d('+dx+'px,'+dy+'px,0) scale('+scale+')',opacity:1}
  ],{duration,easing:'cubic-bezier(.22,.7,.19,1)',fill:'forwards'});
  let flipEffect=null;
  if(flipCore){
   // Keep the reference's measured, substantial 3D turn concurrent with
   // position motion; never remove the moving card before its face settles.
   flipEffect=flipCore.animate([
    {transform:'rotateY(0deg)'},
    {offset:.3,transform:'rotateY(27deg)'},
    {offset:1,transform:'rotateY(180deg)'}
   ],{delay:action==='draw'?52:25,duration:whotMotion.drawFlip,
     easing:'cubic-bezier(.31,.63,.19,1)',fill:'forwards'});
  }
  await Promise.all([flight.finished.catch(()=>{}),flipEffect?flipEffect.finished.catch(()=>{}):Promise.resolve()]);
 }finally{
  origin.style.visibility=oldVisibility;
  if(action==='draw')destination.style.visibility=previousDestVisibility;
  ghost.remove();cardMotionMetrics.activeFlights--;
 }
}
function whotDeal(){
 const deck=board.querySelector('.whot-deck-back');
 if(!deck||!motionEnabled()||active!=='whot')return;
 const a=deck.getBoundingClientRect();
 [...board.querySelectorAll('.hand .whot-card')].forEach((card,index)=>{
  const b=card.getBoundingClientRect(),dx=a.left+a.width/2-b.left-b.width/2,
  dy=a.top+a.height/2-b.top-b.height/2;
  cardMotionMetrics.deals++;
  card.animate([
   {transform:'translate3d('+dx+'px,'+dy+'px,0) scale(.58) rotate(-12deg)',opacity:.05},
   {offset:.72,transform:'translate3d('+(dx*.12)+'px,'+(dy*.12-11)+'px,0) scale(1.06) rotate(3deg)',opacity:1},
   {transform:'translate3d(0,0,0) scale(1) rotate(0)',opacity:1}
  ],{delay:whotMotion.dealDelay+index*whotMotion.dealStagger,duration:whotMotion.deal,easing:'cubic-bezier(.23,.8,.2,1)'});
 });
 [...board.querySelectorAll('.table-surface > .whot-opponent-hand .card-back')].forEach((opponent,index)=>{
  const b=opponent.getBoundingClientRect();
  opponent.animate([
   {transform:'translate3d('+(a.left+a.width/2-b.left-b.width/2)+'px,'+(a.top+a.height/2-b.top-b.height/2)+'px,0) scale(.7)',opacity:.1},
   {transform:'translate3d(0,0,0) scale(1)',opacity:1}
  ],{delay:whotMotion.dealDelay+index*whotMotion.dealStagger,duration:whotMotion.opponentDeal,easing:'cubic-bezier(.2,.72,.24,1)'});
 });
}
function whotSettle(){
 if(!motionEnabled())return;
 const p=board.querySelector('.card-pile .whot-card');
 p?.animate([
  {offset:0,transform:'scale(1.13) rotate(3deg)'},
  {offset:.55,transform:'scale(.975) rotate(-1deg)'},
  {offset:1,transform:'scale(1) rotate(0)'}
 ],{duration:whotMotion.settle,easing:'cubic-bezier(.2,.82,.28,1)'});
}
async function cardPlay(kind,index,requestedShape=null,visualOrigin=null){
 const s=state[kind];if(cardMotionBusy||s.turn!==0||s.winner!==null)return;
 const card=s.hands[0][index];if(kind==='whot'&&s.pendingFinish===0)return;if(!card||!playable(kind,s,card))return;
 if(kind==='whot'){
  if(s.pendingWhot!=null&&s.pendingWhot!==index)return;
  if(card.n===20&&requestedShape===null){
   // Do NOT commit a 20 or advance the turn until its owner makes the call.
   s.pendingWhot=index;render();
   board.querySelector('.whot-call-choice')?.focus({preventScroll:true});
   return;
  }
  if(card.n===20&&(!Number.isInteger(requestedShape)||requestedShape<0||requestedShape>=shapes.length))return;
  if(card.n!==20&&s.pendingWhot!=null)return;
  s.pendingWhot=null;
 }
 const chosen=kind==='whot'&&card.n===20?requestedShape:
 kind==='colors'&&card.c===4?Number($('wishColor').value):null;
 const epoch=cardMotionEpoch;cardMotionBusy=true;
 try{
  if(kind==='whot'){cardMotionMetrics.plays++;
   await whotFly(visualOrigin||board.querySelector('.hand [data-card="'+index+'"]'),
    board.querySelector('.card-pile .whot-card'),{action:'play'});
  }
  if(epoch!==cardMotionEpoch||active!==kind||state[kind]!==s)return;
  s.hands[0].splice(index,1);nextCardTurn(kind,s,0,card,chosen);
  render();if(kind==='whot')whotSettle();if(s.turn===1)queueCPU(kind);
 }finally{if(epoch===cardMotionEpoch)cardMotionBusy=false;}
}
async function cardDraw(kind){
 const s=state[kind];if(cardMotionBusy||s.turn!==0||s.winner!==null||(kind==='whot'&&s.pendingWhot!=null))return;
 const oldCount=s.hands[0].length,epoch=cardMotionEpoch;cardMotionBusy=true;
 try{
  if(kind==='whot'){
   const wasFinish=s.pendingFinish===0,penalty=whotPending(s);
   const count=wasFinish?1:penalty?penalty.amount:1;
   const before=s.hands[0].length;
   cardMotionMetrics.draws++;
   take(s,0,count);
   if(wasFinish)s.pendingFinish=null;
   if(penalty)s.pendingPenalty=null;
   s.turn=1;
   s.msg=wasFinish?"No action-card check-up. Picked one — game continues.":
     penalty?"You picked "+count+" penalty cards.":"You picked a card. Computer's turn.";
   render();
   const newCard=board.querySelectorAll('.hand .whot-card')[before];
   const landing=newCard&&getComputedStyle(newCard).display!=='none'?newCard:board.querySelector('.whot-view-hand:not([hidden])');
   await whotFly(board.querySelector('.whot-deck-back'),landing,{face:s.hands[0][before],action:'draw'});
   if(epoch!==cardMotionEpoch||active!==kind||state[kind]!==s)return;
   queueCPU(kind);
   return;
  }
  take(s,0,1);s.turn=1;s.msg="You drew a card. Computer's turn.";
  render();queueCPU(kind);
 }finally{if(epoch===cardMotionEpoch)cardMotionBusy=false}
}
function whotChooseCPU(s){
 const cards=s.hands[1],legal=cards.map((card,i)=>({card,i})).filter(x=>whotCanPlay(s,x.card));
 if(!legal.length)return -1;
 if(whotOptions.difficulty==="easy")return legal[Math.floor(Math.random()*legal.length)].i;
 if(whotOptions.difficulty==="normal"&&(whotOptions.mode!=="career"||whotRank().level<2))return legal[0].i;
 const danger=s.hands[0].length<=2;
 legal.sort((a,b)=>{
  const score=x=>{
   const c=x.card;
   return (danger&&[2,5,8,14].includes(c.n)?32:0)+
    (c.n===20?(legal.length===1?12:-16):0)+
    (c.n===2||c.n===5?9:0)+
    cards.filter(v=>v.s===c.s).length*2+
    (s.hands[1].length===1&&whotNeedsFinishCard(c)?-60:0);
  };
  return score(b)-score(a);
 });
 return legal[0].i;
}
function queueCPU(kind){
 const round=state[kind],epoch=cardMotionEpoch;
 setTimeout(async ()=>{
  if(active!==kind||round!==state[kind]||cardMotionEpoch!==epoch||round.turn!==1||round.winner!==null)return;
  const s=round;
  if(kind==='whot'&&s.pendingFinish===1){
   take(s,1,1);s.pendingFinish=null;
   s.turn=0;
   s.msg="Computer cannot check up on a final action card. CPU picks one.";
   render();return;
  }
  const penalty=kind==='whot'?whotPending(s):null;
  const idx=kind==='whot'?whotChooseCPU(s):s.hands[1].findIndex(card=>playable(kind,s,card));
  if(idx<0){
   if(kind==='whot'){
    cardMotionMetrics.draws++;
    await whotFly(board.querySelector('.whot-deck-back'),board.querySelector('.card-back'),{action:'draw'});
   }
   if(active!==kind||state[kind]!==s||cardMotionEpoch!==epoch)return;
   const count=penalty?penalty.amount:1;
   take(s,1,count);
   if(penalty)s.pendingPenalty=null;
   s.turn=0;s.msg=penalty?"Computer picks "+count+" penalty cards.":"Computer picks from market.";
   render();return;
  }
  const card=s.hands[1][idx];
  if(kind==='whot'){
   cardMotionMetrics.cpuPlays++;
   await whotFly(board.querySelector('.card-back'),board.querySelector('.card-pile .whot-card'),{face:card,action:'cpu'});
  }
  if(active!==kind||state[kind]!==s||cardMotionEpoch!==epoch)return;
  s.hands[1].splice(idx,1);
  const suitCounts=kind==='whot'?shapes.map((_,shape)=>s.hands[1].filter(c=>c.s===shape).length):[];
  const cpuCall=kind==='whot'?suitCounts.indexOf(Math.max(...suitCounts)):Math.floor(Math.random()*4);
  nextCardTurn(kind,s,1,card,cpuCall);
  render();if(kind==='whot')whotSettle();if(s.turn===1)queueCPU(kind);
 },kind==='whot'?whotMotion.cpuDelay:470);
}
function whotShapeSvg(s,n,mini=false){
 // Entirely original vector suits, drawn from geometric shapes; no third-party
 // logos or unlicensed game art. Intrinsic geometry scales crisply on Android.
 const tags=[
  '<circle cx="50" cy="50" r="37"/>',
  '<path d="M50 9 92 87H8Z"/>',
  '<path d="M34 8h32v26h26v32H66v26H34V66H8V34h26Z"/>',
  '<rect x="12" y="12" width="76" height="76"/>',
  '<path d="M50 7 62.5 34 92 37.5 70 58.5 77 89 50 74 23 89 30 58.5 8 37.5 37.5 34Z"/>'
 ];
 if(s===5)return '';
 return '<svg class="'+(mini?'whot-suit-small':'whot-suit-big')+'" viewBox="0 0 100 100" focusable="false" aria-hidden="true">'+tags[s]+
 (s===4&&!mini?'<text x="50" y="58" fill="#fffdf8" font-size="23" font-weight="850" text-anchor="middle" font-family="Inter,system-ui,sans-serif">'+(n*2)+'</text>':'')+
 '</svg>';
}
function whotFace(card,index,enabled){
 const wild=card.n===20;
 const cornerSymbol=wild?'<span class="whot-wild-mini">w</span>':whotShapeSvg(card.s,card.n,true);
 const large=wild
  ?'<span class="whot-twenty"><b>WHOT</b><i class="whot-swirl" aria-hidden="true">⌁</i><b class="whot-reversed">WHOT</b></span>'
  :whotShapeSvg(card.s,card.n);
 return '<button class="playing-card whot-card'+(wild?' whot-wild':'')+(enabled?' playable':'')+'" data-card="'+index+'" type="button" '+(enabled?'':'disabled')+
 ' data-whot-key="'+card.s+'-'+card.n+'" aria-label="'+(wild?'WHOT 20, call a shape':card.n+" "+shapes[card.s])+'">'+
 '<span class="whot-corner whot-corner-top"><strong>'+card.n+'</strong>'+cornerSymbol+'</span>'+
 '<span class="whot-face">'+large+'</span>'+
 '<span class="whot-corner whot-corner-bottom"><strong>'+card.n+'</strong>'+cornerSymbol+'</span></button>';
}
function cardMarkup(kind,card,index,enabled){
 if(kind==="whot")return whotFace(card,index,enabled);
 const clr=["#d93739","#2e81c0","#2b8d61","#d7a925","#302d3a"][card.c];
 return '<button class="color-card'+(enabled?' playable':'')+'" style="--color:'+clr+'" data-card="'+index+'" '+(enabled?'':'disabled')+
 ' aria-label="'+["Red","Blue","Green","Yellow","Wild"][card.c]+' '+h(card.n)+'"><span>'+h(card.n)+'</span></button>';
}

// A centered player hand should *remain* centered at 5, 4, 3, 2 and 1
// cards instead of becoming a left-justified strip. Only calculate layout
// on state changes or viewport resize, never on animation frames.
function whotHandPositions(){
 const buckets=new Map();
 const row=board.querySelector('.hand .card-row');
 if(!row)return buckets;
 for(const el of row.querySelectorAll('.whot-card')){
  const key=el.dataset.whotKey,r=el.getBoundingClientRect();
  if(!buckets.has(key))buckets.set(key,[]);
  buckets.get(key).push({x:r.left,y:r.top});
 }
 return buckets;
}
function whotHandMode(n){return n<=4?'spaced':n<=7?'fan':'double'}
function whotCloseExpanded(){board.querySelector('.whot-hand-overlay')?.remove()}
function whotOpenExpanded(){
 if(active!=='whot'||board.querySelector('.whot-hand-overlay'))return;
 const s=state.whot,table=board.querySelector('.table-surface');
 if(!table)return;
 const overlay=document.createElement('div');
 overlay.className='whot-hand-overlay';
 overlay.setAttribute('role','dialog');
 overlay.setAttribute('aria-modal','true');
 overlay.setAttribute('aria-label','All your WHOT cards');
 overlay.innerHTML='<section class="whot-hand-sheet">'+
 '<header class="whot-hand-sheet-header"><div><small>YOUR HAND</small><h3>All '+s.hands[0].length+' cards</h3></div>'+
 '<button type="button" class="whot-hand-close" data-action="whot-hand-close" aria-label="Close full hand">✕</button></header>'+
 '<div class="whot-hand-sheet-tools"><label for="whot-hand-sort">Arrange cards</label>'+
 '<select id="whot-hand-sort" data-whot-hand-sort>'+
 '<option value="original">Dealt order</option><option value="shape">By shape</option><option value="number">By number</option></select></div>'+
 '<div class="whot-hand-gallery" aria-label="All cards, vertically scrollable">'+
 s.hands[0].map((c,i)=>whotFace(c,i,s.turn===0&&s.winner===null&&s.pendingFinish!==0&&s.pendingWhot==null&&playable("whot",s,c))).join('')+
 '</div><p class="whot-hand-help">Tap a playable card to place it on the table.</p></section>';
 table.appendChild(overlay);
 overlay.querySelector('.whot-hand-close').focus({preventScroll:true});
}
function whotSortExpanded(order){
 const gallery=board.querySelector('.whot-hand-gallery');if(!gallery)return;
 const nodes=[...gallery.querySelectorAll('.whot-card')];
 nodes.sort((a,b)=>{
  const i=Number(a.dataset.card),j=Number(b.dataset.card);
  if(order==='original')return i-j;
  const x=state.whot.hands[0][i],y=state.whot.hands[0][j];
  return order==='number'?x.n-y.n||x.s-y.s||i-j:x.s-y.s||x.n-y.n||i-j;
 });
 gallery.replaceChildren(...nodes);
}
function layoutWhotHand(before=null){
 const hand=board.querySelector('.table-surface>.hand'),row=hand?.querySelector('.card-row');
 if(!row||active!=='whot')return;
 const cards=[...row.querySelectorAll('.whot-card')],n=cards.length;
 const table=board.querySelector('.table-surface');
 if(!n){table?.classList.remove('whot-double-hand');return}
 const mode=whotHandMode(n),double=mode==='double',large=n>=13,shown=Math.min(n,16);
 const available=row.clientWidth||row.getBoundingClientRect().width;
 const cs=getComputedStyle(cards[0]),w=parseFloat(cs.width)||80,h=parseFloat(cs.height)||105;
 const cardWidth=mode==='spaced'&&n>1&&n*w>available?Math.max(58,Math.floor(available/n)):w;
 const rowGap=14,height=double?h*2+rowGap:h;
 hand.dataset.whotLayout=mode;
 table?.classList.toggle('whot-double-hand',double);
 table?.classList.toggle('whot-large-hand',large);
 hand.style.setProperty('--whot-hand-height',(height+(large?42:0))+'px');
 row.style.setProperty('--whot-row-height',height+'px');
 const view=hand.querySelector('.whot-view-hand');
 if(view){view.hidden=!large;view.textContent='View all '+n+' cards'}
 const firstRow=double?Math.ceil(shown/2):shown;
 cards.forEach((card,index)=>{
  if(index>=shown){card.style.setProperty('display','none','important');return}
  card.style.removeProperty('display');
  const rowIndex=double&&index>=firstRow?1:0;
  const count=double?(rowIndex?shown-firstRow:firstRow):shown;
  const slot=double&&rowIndex?index-firstRow:index;
  const spacing=count>1?mode==='spaced'
   ?Math.min(20,Math.max(0,(available-count*cardWidth)/(count-1)))
   :Math.min(cardWidth+10,(available-cardWidth)/(count-1)):0;
  const step=mode==='spaced'?cardWidth+spacing:spacing;
  const groupWidth=cardWidth+(count-1)*step;
  const left=Math.max(0,(available-groupWidth)/2)+slot*step;
  const tilt=mode==='fan'?(slot-(count-1)/2)*1.05:0;
  const y=rowIndex*(h+rowGap)+(mode==='fan'?Math.abs(slot-(count-1)/2)*2:0);
  card.style.left=left.toFixed(2)+'px';
  card.style.top=y.toFixed(2)+'px';
  card.style.zIndex=String(10+index);
  card.style.setProperty('--whot-hand-tilt',tilt.toFixed(2)+'deg');
  if(cardWidth!==w)card.style.setProperty('--whot-card-width',cardWidth+'px');
  else card.style.removeProperty('--whot-card-width');
 });
 if(!before||!motionEnabled())return;
 for(const card of cards){
  if(card.style.display==='none')continue;
  const prior=before.get(card.dataset.whotKey)?.shift();
  if(!prior)continue;
  const current=card.getBoundingClientRect(),dx=prior.x-current.left,dy=prior.y-current.top;
  if(Math.abs(dx)<.7&&Math.abs(dy)<.7)continue;
  const tilt=card.style.getPropertyValue('--whot-hand-tilt')||'0deg';
  card.animate([
   {transform:'translate3d('+dx+'px,'+dy+'px,0) rotate('+tilt+')'},
   {transform:'translate3d(0,0,0) rotate('+tilt+')'}
  ],{duration:whotMotion.recenter,easing:'cubic-bezier(.19,.78,.22,1)'});
 }
}
let whotResizeFrame=0;
window.addEventListener('resize',()=>{
 cancelAnimationFrame(whotResizeFrame);
 whotResizeFrame=requestAnimationFrame(()=>layoutWhotHand());
});
// Keep the felt board and cards alive across turns. A full innerHTML reset
// triggers a costly repaint of gradients, wooden borders and card faces.
function whotPatchTable(markup){
 const table=board.firstElementChild;
 if(!table||!table.classList.contains('table-surface')||board.childElementCount!==1)return false;
 const tpl=document.createElement('template');tpl.innerHTML=markup;
 const next=tpl.content.firstElementChild;
 if(!next||!next.classList.contains('table-surface'))return false;
 const liveHud=table.querySelector('.whot-scoreboard'),newHud=next.querySelector('.whot-scoreboard');
 const oldPile=table.querySelector('.card-pile'),newPile=next.querySelector('.card-pile');
 const liveHand=table.querySelector('.hand .card-row'),newHand=next.querySelector('.hand .card-row');
 if(!liveHud||!newHud||!oldPile||!newPile||!liveHand||!newHand)return false;
 table.className=next.className;
 for(const selector of ['.whot-seat-cpu strong','.whot-seat-you strong','.whot-turn-indicator']){
  const el=liveHud.querySelector(selector),fresh=newHud.querySelector(selector);
  if(!el||!fresh)continue;
  if(el.textContent!==fresh.textContent)el.textContent=fresh.textContent;
  if(el.className!==fresh.className)el.className=fresh.className;
 }
 // Reconcile optional mode score and Last Card control too. Previously
 // these were omitted by the incremental renderer, leaving stale series
 // scores and hiding the announcement button after the hand reached one.
 for(const selector of ['.whot-series-score','.whot-last-call']){
  const live=liveHud.querySelector(selector),fresh=newHud.querySelector(selector);
  if(live&&!fresh)live.remove();
  else if(fresh&&!live)liveHud.appendChild(fresh.cloneNode(true));
  else if(live&&fresh){
   if(live.textContent!==fresh.textContent)live.textContent=fresh.textContent;
   if(live.disabled!==fresh.disabled)live.disabled=fresh.disabled;
  }
 }
 const oldOpponent=table.querySelector('.whot-opponent-hand'),newOpponent=next.querySelector('.whot-opponent-hand');
 if(oldOpponent&&newOpponent){
  const oldBacks=[...oldOpponent.querySelectorAll('.card-back')];
  const needed=newOpponent.querySelectorAll('.card-back').length;
  if(needed<oldBacks.length){for(let i=oldBacks.length-1;i>=needed;i--)oldBacks[i].remove()}
  else if(needed>oldBacks.length){
   for(let i=oldBacks.length;i<needed;i++)oldOpponent.appendChild(newOpponent.children[i].cloneNode(true));
  }
  for(const [i,back] of [...oldOpponent.querySelectorAll('.card-back')].entries()){
   back.setAttribute('aria-label','Computer card '+(i+1)+' of '+needed);
  }
 }
 const oldDeck=oldPile.querySelector('.whot-deck'),newDeck=newPile.querySelector('.whot-deck');
 if(oldDeck&&newDeck){
  oldDeck.disabled=newDeck.disabled;
  const counter=oldDeck.querySelector('.cards-counter'),newCounter=newDeck.querySelector('.cards-counter');
  if(counter&&newCounter&&counter.textContent!==newCounter.textContent)counter.textContent=newCounter.textContent;
 }
 const oldTop=oldPile.querySelector('.whot-card'),newTop=newPile.querySelector('.whot-card');
 if(oldTop&&newTop&&oldTop.dataset.whotKey!==newTop.dataset.whotKey)oldTop.replaceWith(newTop);
 const oldCalled=oldPile.querySelector('.whot-called'),newCalled=newPile.querySelector('.whot-called');
 if(oldCalled&&!newCalled)oldCalled.remove();
 else if(newCalled&&!oldCalled)oldPile.appendChild(newCalled);
 else if(oldCalled&&newCalled&&oldCalled.textContent!==newCalled.textContent)oldCalled.textContent=newCalled.textContent;
 const was=[...liveHand.querySelectorAll('.whot-card')],buckets=new Map();
 for(const card of was){const k=card.dataset.whotKey;if(!buckets.has(k))buckets.set(k,[]);buckets.get(k).push(card)}
 const cards=[...newHand.querySelectorAll('.whot-card')].map(fresh=>{
  const reused=buckets.get(fresh.dataset.whotKey)?.shift()||fresh;
  if(reused!==fresh){
   if(reused.className!==fresh.className)reused.className=fresh.className;
   reused.disabled=fresh.disabled;
   reused.dataset.card=fresh.dataset.card;
   reused.setAttribute('aria-label',fresh.getAttribute('aria-label'));
  }
  return reused;
 });
 if(was.length!==cards.length||was.some((node,i)=>node!==cards[i])){
  liveHand.replaceChildren(...cards);
 }
 const oldPicker=table.querySelector('.whot-call-panel'),newPicker=next.querySelector('.whot-call-panel');
 if(oldPicker&&!newPicker)oldPicker.remove();
 else if(newPicker&&!oldPicker)table.insertBefore(newPicker,table.querySelector('.hand'));
 const oldWin=table.querySelector('.whot-win-layer'),newWin=next.querySelector('.whot-win-layer');
 if(oldWin&&!newWin)oldWin.remove();
 else if(newWin&&!oldWin)table.appendChild(newWin);
 return true;
}
function renderCards(kind){
const previousHand=kind==="whot"?whotHandPositions():null;
const s=state[kind],t=topCard(s),topSelected={...t};
if(s.called!==null)topSelected[kind==="whot"?"s":"c"]=s.called;
const currentCall=s.called===null?'':kind==="whot"
 ?'<div class="whot-called" role="status">WHOT 20 · CALLED '+shapes[s.called].toUpperCase()+'</div>'
 :'<div class="whot-called" role="status">WILD · '+["RED","BLUE","GREEN","YELLOW"][s.called]+'</div>';
const showCall=kind==='whot'&&s.turn===0&&s.winner===null&&Number.isInteger(s.pendingWhot)&&s.hands[0][s.pendingWhot]?.n===20;
const naijaShapes=["Ball","Angle","Cross","Carpet","Star"];
const callPanel=showCall?
 '<section class="whot-call-panel" role="group" aria-labelledby="whot-call-title"><div class="whot-call-head"><div><small>20 · WHOT</small><h3 id="whot-call-title">Call your shape</h3></div><button type="button" class="whot-call-cancel" data-action="whot-call-cancel" aria-label="Cancel WHOT 20">✕</button></div>'+
 '<div class="whot-call-grid">'+shapes.map((shape,i)=>
 '<button type="button" class="whot-call-choice" data-whot-call="'+i+'" aria-label="Call '+shape+'">'+whotShapeSvg(i,1)+'<strong>'+shape+'</strong><small>'+naijaShapes[i]+'</small></button>').join('')+'</div></section>':'';
const whotHud=kind==='whot'?
 '<div class="whot-scoreboard" aria-label="Match status"><div class="whot-seat whot-seat-cpu"><span>CPU</span><strong>'+s.hands[1].length+'</strong></div>'+
 '<div class="whot-turn-indicator'+(s.turn===0&&s.winner===null?' player-turn':'')+'"><span class="whot-indicator-light"></span>'+
 (s.winner!==null?'MATCH OVER':s.pendingFinish===0?'PICK TO CONTINUE':whotPending(s)&&s.turn===0?'DEFEND / PICK '+whotPending(s).amount:showCall?'CALL A SHAPE':s.turn===0?'YOUR TURN':'CPU TURN')+'</div>'+
 '<div class="whot-seat whot-seat-you"><strong>'+s.hands[0].length+'</strong><span>YOU</span></div>'+
 (whotOptions.mode==="tournament"?'<span class="whot-series-score">SERIES · '+(whotCareer.tournamentWins?.[0]||0)+' — '+(whotCareer.tournamentWins?.[1]||0)+'</span>':'')+
 (whotOptions.announce&&s.hands[0].length===1&&s.turn===0&&s.winner===null?
 '<button type="button" class="whot-last-call" data-action="whot-last-call" '+(s.calledLast?.[0]?'disabled':'')+'>'+(s.calledLast?.[0]?'LAST CARD ✓':'DECLARE LAST CARD')+'</button>':'')+'</div>':'';
const whotVictory=kind==='whot'&&s.winner!==null?
 '<div class="whot-win-layer '+(s.winner===0?'player-victory':'computer-victory')+'" role="status" aria-live="assertive">'+
 '<div class="whot-win-particles" aria-hidden="true">'+(s.winner===0?Array.from({length:18},(_,i)=>'<i style="--index:'+i+'"></i>').join(''):'')+'</div>'+
 '<div class="whot-win-content"><div class="whot-win-medal" aria-hidden="true"><svg viewBox="0 0 64 64" fill="none"><path d="M20 9h24v15c0 11-5 18-12 20-7-2-12-9-12-20V9Z" fill="currentColor" opacity=".9"/><path d="M20 13H8v8c0 10 6 13 15 13m21-21h12v8c0 10-6 13-15 13" stroke="currentColor" stroke-width="4" stroke-linejoin="round"/><path d="M32 45v9m-13 2h26" stroke="currentColor" stroke-width="5" stroke-linecap="round"/></svg></div>'+
 '<p class="whot-win-eyebrow">'+(s.promotion?'RANK PROMOTION':whotOptions.mode==="tournament"?"TOURNAMENT ROUND":"MATCH COMPLETE")+'</p><h2>'+(s.winner===0?'YOU WIN':'CPU WINS')+'</h2>'+
 '<p class="whot-win-count">'+(s.promotion?'PROMOTED TO '+s.promotion.toUpperCase()+' · ':whotOptions.mode==="career"?'CAREER · '+whotCareer.wins+' WINS · ':'')+(s.winner===0?'CPU':'YOU')+' · '+s.hands[1-s.winner].length+' CARDS LEFT'+(whotOptions.mode==="tournament"?' · SERIES '+whotCareer.tournamentWins[0]+'—'+whotCareer.tournamentWins[1]:'')+'</p>'+
 '<button type="button" class="whot-rematch" data-action="whot-rematch">Play again</button></div></div>':'';
const tableMarkup='<div class="table-surface'+(kind==='whot'&&s.winner!==null?' whot-finished':'')+'" aria-label="Card table">'+(kind==='whot'?whotHud:'<h3>COMPUTER · '+s.hands[1].length+' CARDS</h3>')+
 '<div class="card-row whot-opponent-hand">'+(kind==='whot'
 ?Array.from({length:s.hands[1].length},(_,i)=>'<div class="playing-card card-back" aria-label="Computer card '+(i+1)+' of '+s.hands[1].length+'"><span>WHOT</span><span>WHOT</span></div>').join('')
 :'<div class="playing-card card-back" aria-label="Computer cards hidden"></div><span class="cards-counter">× '+s.hands[1].length+'</span>')+'</div>'+
 '<div class="card-pile">'+(kind==='whot'?'<button type="button" class="whot-deck" data-action="card-draw" '+(s.turn!==0||s.winner!==null||s.pendingWhot!=null?'disabled':'')+' aria-label="Pick one card from the market"><span class="whot-deck-back"><span class="whot-deck-word flip-top">WHOT</span><span class="whot-deck-word flip-bottom">WHOT</span></span><span class="cards-counter">MARKET · '+s.deck.length+'</span></button>':'<span class="cards-counter">DECK '+s.deck.length+'</span>')+
 cardMarkup(kind,t,-1,false)+currentCall+'</div>'+callPanel+
 '<div class="hand">'+(kind==='whot'?'':'<div class="card-label">YOUR HAND · '+s.hands[0].length+' CARDS</div>')+'<div class="card-row">'+s.hands[0].map((card,i)=>cardMarkup(kind,card,i,s.turn===0&&s.winner===null&&!(kind==='whot'&&s.pendingWhot!=null)&&playable(kind,s,card))).join('')+'</div>'+(kind==='whot'?'<button type="button" class="whot-view-hand" data-action="whot-hand-open" hidden>View all '+s.hands[0].length+' cards</button>':'')+'</div>'+whotVictory+'</div>';
if(kind!=='whot'||!whotPatchTable(tableMarkup))board.innerHTML=tableMarkup;
if(kind==='whot')whotCloseExpanded();
document.body.classList.toggle('whot-has-winner',kind==='whot'&&s.winner!==null);
if(showCall)board.querySelector('.hand [data-card="'+s.pendingWhot+'"]')?.classList.add('whot-pending');
if(kind==='whot')layoutWhotHand(previousHand);
if(kind==='whot'&&pendingWhotDeal){pendingWhotDeal=false;const token=cardMotionEpoch;
 requestAnimationFrame(()=>{if(token===cardMotionEpoch&&active==='whot')whotDeal()});}
setStatus(s.winner!==null?s.msg:(s.turn===0?"Your turn. ":"Computer's turn. ")+s.msg);
// WHOT uses the market pile itself for drawing and an in-table 20 picker.
 // Avoid duplicating either action in the sidebar.
if(kind==='whot')controlsHTML('');
else controlsHTML('<div class="row"><button class="primary" type="button" data-action="card-draw" '+(s.turn!==0||s.winner!==null?"disabled":"")+'>Pick one card</button></div><p class="small">Cards left: you '+s.hands[0].length+' · computer '+s.hands[1].length+'</p><label class="small" for="wishColor">Colour to call (wild)</label><select id="wishColor" class="select">'+["Red","Blue","Green","Yellow"].map((sh,i)=>'<option value="'+i+'">'+sh+'</option>').join("")+'</select>');
rules(kind==="whot"?"Classic WHOT rules":"Colour Clash rules",kind==="whot"?
 "Nigerian WHOT: match shape or number; otherwise pick from market. 1 Hold On, 2 Pick Two, 5 Pick Three, 8 Suspension, 14 General Market, 20 calls any shape. Strict Check Up means final action cards cannot win: pick one and continue. Defensive stacking (2 on 2 or 5 on 5), Last Card declaration, Career and Tournament are available from settings.":
 "Play a matching <b>colour</b> or <b>number/action</b>. Skip / reverse skips the computer, +2 and +4 draw penalties, and WILD lets you choose a colour. The first player to empty their hand wins. Original artwork; no UNO trademarks.");
}

/* A recognizable numbered board with every square and slide displayed. */
const ups={3:22,5:8,11:26,20:29,27:56,36:44,51:67,71:92,80:99};
const downs={17:4,19:7,21:9,43:34,47:27,54:31,62:18,64:60,87:24,93:73,95:75,98:79};
function renderSnakes(){
const s=state.snakes;let html='<div class="snakes-board" role="grid" aria-label="100 square snakes and ladders board">';
for(let row=0;row<10;row++)for(let c=0;c<10;c++){const base=(9-row)*10+1,num=(9-row)%2===0?base+c:base+(9-c);
let tokens=s.players.map((p,i)=>p.pos===num?'<span style="color:'+p.color+'" title="'+p.name+'">●</span>':"").join("");
let jump=ups[num]?"<span class='jump' aria-label='Ladder to "+ups[num]+"'><svg class='direction-svg' aria-hidden='true' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.1' stroke-linecap='round' stroke-linejoin='round'><path d='M5 19 19 5M9 5h10v10'/></svg></span>":downs[num]?"<span class='jump' aria-label='Snake to "+downs[num]+"'><svg class='direction-svg' aria-hidden='true' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2.1' stroke-linecap='round' stroke-linejoin='round'><path d='m5 5 14 14M19 9v10H9'/></svg></span>":"";
html+='<div class="square" role="gridcell" aria-label="Square '+num+'" style="grid-row:'+(row+1)+';grid-column:'+(c+1)+'">'+num+jump+'<span class="marker">'+tokens+'</span></div>';
}board.innerHTML=html+"</div>";
setStatus(s.winner!==null?s.players[s.winner].name+" won the race to 100!":s.players[s.turn].name+"'s turn. "+s.msg);
controlsHTML('<div class="dice">'+("⚀⚁⚂⚃⚄⚅"[(s.last||1)-1])+'</div><button type="button" class="primary" data-action="snakes-roll" '+(s.winner!==null?"disabled":"")+'>Roll dice</button>'+scoreList(s.players,x=>"Square "+x.pos)+choosePlayers("snakes",s.players.length,4));
rules("Snakes & Ladders","Roll one die to advance. Ladders take you up to higher squares; snakes send you down to lower squares. You must land on exactly 100 to win. Players share the same phone.");
}
function snakesRoll(){const s=state.snakes;if(s.winner!==null)return;const p=s.players[s.turn],n=die();s.last=n;const old=p.pos;if(p.pos+n<=100)p.pos+=n;s.msg=(p.pos===old?"Exact roll needed. ":"Rolled "+n+". ");if(ups[p.pos]){s.msg+="Climbed to "+ups[p.pos]+"!";p.pos=ups[p.pos];}if(downs[p.pos]){s.msg+="Slid to "+downs[p.pos]+"!";p.pos=downs[p.pos];}if(p.pos===100)s.winner=s.turn;else s.turn=(s.turn+1)%s.players.length;render();}

/* Draughts: mandatory captures, chained jumps, kings and no-move loss. */
function checkerOptions(s,at,captureOnly){
const piece=s.cells[at];if(!piece)return[];const r=Math.floor(at/8),c=at%8;const dirs=piece===piece.toUpperCase()?[-1,1]:[piece==="w"?-1:1],list=[];
for(const dr of dirs)for(const dc of [-1,1]){
const ny=r+dr,nx=c+dc;if(ny<0||ny>7||nx<0||nx>7)continue;
const ix=ny*8+nx,other=s.cells[ix];if(other&&other.toLowerCase()!==piece.toLowerCase()){
const ty=r+2*dr,tx=c+2*dc;if(ty>=0&&ty<8&&tx>=0&&tx<8&&s.cells[ty*8+tx]===null)list.push({from:at,to:ty*8+tx,capture:ix});}
else if(!captureOnly&&other===null)list.push({from:at,to:ix,capture:null});
}
return list;}
function allCheckerMoves(s){let entries=[];for(let i=0;i<64;i++){const p=s.cells[i];if(p&&p.toLowerCase()===s.turn&&(s.forced===null||s.forced===i))entries.push(...checkerOptions(s,i,s.forced!==null));}if(entries.some(m=>m.capture!==null))entries=entries.filter(m=>m.capture!==null);return entries;}
function renderCheckers(){
 const s=state.checkers,options=allCheckerMoves(s),legalFrom=new Set(options.map(m=>m.from));
 const valid=s.selected===null?[]:options.filter(m=>m.from===s.selected).map(m=>m.to);
 const whites=s.cells.filter(x=>x&&x.toLowerCase()==="w").length,blacks=s.cells.filter(x=>x&&x.toLowerCase()==="b").length;
 const label=s.winner!==null?(s.winner==="w"?"White wins":"Black wins"):(s.turn==="w"?"White to play":"Black to play");
 let html='<div class="checker-stage"><div class="checker-scoreline"><div class="checker-seat white-seat'+(s.turn==="w"&&s.winner===null?" active":"")+'"><span class="checker-seat-token"></span><span><b>WHITE</b><small>'+whites+' pieces left</small></span></div><div class="checker-game-call"><span class="checker-match-tag">LOCAL · 2 PLAYERS</span><strong>'+label+'</strong></div><div class="checker-seat black-seat'+(s.turn==="b"&&s.winner===null?" active":"")+'"><span class="checker-seat-token"></span><span><b>BLACK</b><small>'+blacks+' pieces left</small></span></div></div><div class="checker-frame"><div class="checkerboard" role="group" aria-label="Eight by eight draughts board">';
 for(let i=0;i<64;i++){
  const dark=(Math.floor(i/8)+i%8)%2===1,p=s.cells[i],from=legalFrom.has(i)&&s.selected===null;
  const isTarget=valid.includes(i),isKing=Boolean(p&&p!==p.toLowerCase());
  const piece=p?'<span class="checker-piece '+(p.toLowerCase()==="w"?"white":"black")+(s.selected===i?" selected":"")+(isKing?" king":"")+'">'+(isKing?'<svg class="checker-crown" viewBox="0 0 40 40" aria-hidden="true"><path d="M7 27 4 11l11 9 5-13 6 13 11-9-4 16ZM8 31h24v4H8z"/></svg>':"")+'</span>':"";
  html+='<button type="button" data-checker="'+i+'" class="checker-square '+(dark?"dark":"light")+(isTarget?" target":"")+(from?" movable":"")+(s.selected===i?" origin":"")+'" aria-label="'+(p?(p.toLowerCase()==="w"?"White":"Black")+(isKing?" king":" piece"):"Empty")+' row '+(Math.floor(i/8)+1)+' column '+(i%8+1)+'"'+(!dark?' tabindex="-1"':'')+'>'+piece+'</button>';
 }
 const markup=html+'</div></div><p class="checker-instruction">Tap your piece, then choose a glowing square <span>•</span> Captures are mandatory</p></div>';
 const mounted=board.querySelector(".checker-stage"),oldCells=mounted?.querySelectorAll("[data-checker]");
 if(oldCells?.length===64){
  // Do not destroy/recreate the 64 counters whenever a player selects a square.
  // Patch only changed counters/highlights and the small turn HUD.
  const staging=document.createElement("div");staging.innerHTML=markup;
  const next=staging.firstElementChild,nextCells=next.querySelectorAll("[data-checker]");
  for(let i=0;i<64;i++){
   const before=oldCells[i],after=nextCells[i];
   if(before.className!==after.className)before.className=after.className;
   if(before.getAttribute("aria-label")!==after.getAttribute("aria-label"))
    before.setAttribute("aria-label",after.getAttribute("aria-label"));
   if(before.innerHTML!==after.innerHTML)before.innerHTML=after.innerHTML;
  }
  const oldHud=mounted.querySelector(".checker-scoreline"),newHud=next.querySelector(".checker-scoreline");
  if(oldHud.innerHTML!==newHud.innerHTML)oldHud.innerHTML=newHud.innerHTML;
 }else board.innerHTML=markup;
 setStatus(s.winner!==null?label+"!":label+". "+s.msg);
 controlsHTML('<div class="checker-sidebar-head"><span class="badge">WHITE '+whites+'</span><span class="badge">BLACK '+blacks+'</span></div>');
 rules("Draughts rules","<b>Captures are mandatory.</b> Move diagonally on dark squares; jump an opponent's piece to capture it. When a capture is available, other moves are disabled. Continue a capture chain with the same piece. Reach the opposite edge to crown a king. If the opponent has no moves, you win.");
}
function checkerClick(at){const s=state.checkers;if(s.winner!==null)return;const legal=allCheckerMoves(s);
if(s.selected!==null){const move=legal.find(m=>m.from===s.selected&&m.to===at);if(move){const piece=s.cells[move.from];s.cells[move.from]=null;s.cells[move.to]=piece;if(move.capture!==null)s.cells[move.capture]=null;
const row=Math.floor(move.to/8);if(row===0&&piece==="w")s.cells[move.to]="W";if(row===7&&piece==="b")s.cells[move.to]="B";
if(move.capture!==null&&s.cells[move.to]===piece){const follow=checkerOptions(s,move.to,true).filter(m=>m.capture!==null);if(follow.length){s.forced=move.to;s.selected=move.to;s.msg="Capture again with the same piece.";render();return;}}
s.selected=null;s.forced=null;s.turn=s.turn==="w"?"b":"w";s.msg=move.capture!==null?"Captured!":"Piece moved.";if(!allCheckerMoves(s).length)s.winner=s.turn==="w"?"b":"w";render();return;}}
if(s.forced!==null)return;
if(s.cells[at]&&s.cells[at].toLowerCase()===s.turn&&legal.some(m=>m.from===at)){s.selected=at;s.msg="Choose a highlighted destination.";render();}}
 
/* Draw & Guess: one phone, one artist, friendly spoken guesses. */
const words=["football","umbrella","elephant","jollof rice","airplane","bicycle","lion","pineapple","school bag","microphone","volcano","ice cream","traffic light","watermelon","motorbike","headphones","telephone","palm tree","basketball","sunflower","crocodile","laptop","octopus","birthday cake","rainbow","rocket","guitar","market","football boot","computer","mountain","butterfly","camera","book","candle","shark","raincoat","sunglasses","spider","banana"];
let paintColor="#202f2c",painting=false,lastPoint=null,brushWidth=7;
function renderDraw(){
const s=state.draw;if(s.mode!=="playing"&&s.mode!=="guessing"){board.innerHTML='<div class="table-surface"><div class="secret">'+(s.mode==="reveal"?"Artist's word: "+h(s.word):"Ready for round "+s.round+"?")+'</div><p style="color:#f4f4eb;text-align:center;max-width:320px">The artist reads the secret word first. Everyone else should look away. Then hide the word and draw!</p></div>';}
else{
board.innerHTML='<div class="draw-board"><canvas id="drawCanvas" width="800" height="600" aria-label="Drawing canvas; draw with finger or mouse"></canvas></div><div class="toolbelt" id="brushes"><button type="button" data-brush="#202f2c" style="background:#202f2c" aria-label="Black ink" aria-pressed="true"></button><button type="button" data-brush="#e2343b" style="background:#e2343b" aria-label="Red ink"></button><button type="button" data-brush="#2876ca" style="background:#2876ca" aria-label="Blue ink"></button><button type="button" data-brush="#2a914e" style="background:#2a914e" aria-label="Green ink"></button><button type="button" data-brush="#e3b02a" style="background:#e3b02a" aria-label="Yellow ink"></button><button type="button" data-brush="#ffffff" aria-label="Eraser">⌫</button><button type="button" data-action="draw-clear">Clear drawing</button></div>';
const c=$("drawCanvas");drawCtx=c.getContext("2d");drawCtx.lineCap="round";drawCtx.lineJoin="round";
function pos(e){const box=c.getBoundingClientRect();return [Math.max(0,Math.min(800,(e.clientX-box.left)*800/box.width)),Math.max(0,Math.min(600,(e.clientY-box.top)*600/box.height))];}
c.addEventListener("pointerdown",e=>{if(s.mode!=="playing")return;painting=true;c.setPointerCapture(e.pointerId);lastPoint=pos(e);drawCtx.fillStyle=paintColor;drawCtx.beginPath();drawCtx.arc(lastPoint[0],lastPoint[1],brushWidth/2,0,Math.PI*2);drawCtx.fill();});
c.addEventListener("pointermove",e=>{if(!painting||s.mode!=="playing")return;const q=pos(e);drawCtx.beginPath();drawCtx.strokeStyle=paintColor;drawCtx.lineWidth=brushWidth;drawCtx.moveTo(lastPoint[0],lastPoint[1]);drawCtx.lineTo(q[0],q[1]);drawCtx.stroke();lastPoint=q;});
const stop=()=>{painting=false;lastPoint=null;};c.addEventListener("pointerup",stop);c.addEventListener("pointercancel",stop);c.addEventListener("lostpointercapture",stop);
$("brushes").addEventListener("click",e=>{const b=e.target.closest("[data-brush]");if(!b)return;paintColor=b.dataset.brush;brushWidth=paintColor==="#ffffff"?24:7;for(const btn of document.querySelectorAll("[data-brush]"))btn.setAttribute("aria-pressed",String(btn===b));});
}
const artist=s.players[s.drawer],sc='Player 1 '+s.points[0]+' · Player 2 '+s.points[1];setStatus(s.mode==="playing"?artist+" is drawing. "+s.seconds+" seconds left.":artist+"'s turn to draw.");
const actions=s.mode==="ready"?'<p class="small">Pass the phone to the artist; the guesser should look away.</p><button class="primary" data-action="draw-ready">Reveal my word</button>'
 :s.mode==="reveal"?'<p class="small">Memorize the word before hiding it.</p><button class="primary" data-action="draw-ready">Hide word & start drawing</button>'
 :s.mode==="playing"?'<p class="small">Draw with your finger. When ready, hand the phone to the guesser.</p><button class="primary" data-action="draw-guess">Done drawing · Let them guess <svg class="action-svg" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12h16m-6-6 6 6-6 6"/></svg></button><div class="small" id="drawTimer">Time left: '+s.seconds+'s</div>'
 :'<p class="small">The drawing is locked. The other player guesses now.</p><form id="guessForm"><label class="small" for="guessWord">What does the picture show?</label><input class="guess-input" id="guessWord" maxlength="40" autocomplete="off" placeholder="Your answer"><button class="primary" type="submit">Check guess</button></form><div class="small" id="drawTimer">Time left: '+s.seconds+'s</div><ul class="guess-log" id="guessLog"></ul>';
controlsHTML('<p class="badge">'+sc+'</p>'+actions+'<p><button class="secondary" data-action="draw-next">Skip / next artist <svg class="action-svg" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11a9 9 0 1 0 3-6.7L3 7m0-5v5h5"/></svg></button></p>');
if(s.mode==="playing"||s.mode==="guessing"){timer=setInterval(()=>{s.seconds=Math.max(0,s.seconds-1);const el=$("drawTimer");if(el)el.textContent="Time left: "+s.seconds+"s";if(s.seconds===0){clearInterval(timer);timer=null;s.mode="ready";s.drawer=1-s.drawer;s.round++;s.word=null;render();}},1000);}
rules("Draw & Guess","The artist sees a secret word and draws it with a finger. Tap <b>Done drawing</b> and hand the phone over so the second person can guess. Correct guesses earn points. Players swap after every round. <b>Two people sharing one device</b> — no online room yet.");
}
function drawReady(){const s=state.draw;if(s.mode==="ready"){s.word=words[Math.floor(Math.random()*words.length)];s.mode="reveal";}else if(s.mode==="reveal"){s.mode="playing";s.seconds=60;paintColor="#202f2c";brushWidth=7;}render();}
function drawReadyToGuess(){
 const s=state.draw;if(s.mode!=="playing")return;
 s.mode="guessing";painting=false;lastPoint=null;
 const sc='Player 1 '+s.points[0]+' · Player 2 '+s.points[1];
 setStatus('Drawing finished. Pass the phone to the other player.');
 controlsHTML('<p class="badge">'+sc+'</p><p class="small">The drawing is locked. Try to guess what the artist drew.</p><form id="guessForm"><label class="small" for="guessWord">Your answer</label><input class="guess-input" id="guessWord" maxlength="40" autocomplete="off" placeholder="Type a guess"><button class="primary" type="submit">Check guess</button></form><div class="small" id="drawTimer">Time left: '+s.seconds+'s</div><ul class="guess-log" id="guessLog"></ul><p><button class="secondary" data-action="draw-next">Skip / next artist <svg class="action-svg" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11a9 9 0 1 0 3-6.7L3 7m0-5v5h5"/></svg></button></p>');
}
function drawNext(){const s=state.draw;s.mode="ready";s.word=null;s.drawer=1-s.drawer;s.round++;render();}
function setBrush(color){paintColor=color;brushWidth=color==="#ffffff"?24:7;}
document.addEventListener("submit",e=>{if(e.target.id!=="guessForm")return;e.preventDefault();const s=state.draw;if(s.mode!=="guessing")return;const input=$("guessWord");const guess=input.value.toLowerCase().trim().replace(/[^a-z0-9 ]/g,"");const answer=(s.word||"").toLowerCase().trim().replace(/[^a-z0-9 ]/g,"");if(!guess)return;if(guess===answer){s.points[1-s.drawer]++;const who=s.players[1-s.drawer];s.mode="ready";s.drawer=1-s.drawer;s.round++;alert("Correct! "+who+" gets a point. The word was "+s.word+".");s.word=null;render();return;}if($("guessLog"))$("guessLog").insertAdjacentHTML("afterbegin","<li>Not quite: "+h(guess)+"</li>");input.value="";});
document.addEventListener("click",e=>{const tile=e.target.closest("[data-checker]");if(tile&&active==="checkers")checkerClick(Number(tile.dataset.checker));});

load();picks.addEventListener("click",e=>{const btn=e.target.closest("[data-pick]");if(btn)pick(btn.dataset.pick);});
$("settingsButton").addEventListener("click",()=>{whotConfigUI();$("settingsDialog").showModal()});
$("settingsClose").addEventListener("click",()=>$("settingsDialog").close());
$("newRound").addEventListener("click",()=>{if(confirm("Start a new "+titles[active][0]+" match? Current progress will be cleared."))reset(active);});
$("whotNewRoundMenu").addEventListener("click",()=>{if(active==="whot"&&confirm("Start a new WHOT round?")){reset("whot");$("settingsDialog").close()}});

$("whotApplySettings").addEventListener("click",()=>{
 if(active!=="whot")return;
 for(const el of $("whotModeConfig").querySelectorAll("[data-whot-option]")){
  const name=el.dataset.whotOption;if(!["mode","strictFinish","defend","announce","difficulty"].includes(name))continue;
  whotOptions[name]=el.type==="checkbox"?el.checked:el.value;
 }
 if(!["quick","career","tournament"].includes(whotOptions.mode))whotOptions.mode="quick";
 if(!["easy","normal","hard"].includes(whotOptions.difficulty))whotOptions.difficulty="normal";
 whotStore();$("settingsDialog").close();reset("whot");whotConfigUI();
});
$("whotResetCareer").addEventListener("click",()=>{
 if(!confirm("Reset WHOT career and tournament records?"))return;
 Object.assign(whotCareer,{wins:0,losses:0,streak:0,bestStreak:0,tournamentWins:[0,0]});
 whotStore();whotConfigUI();
});
document.addEventListener("change",e=>{
const handSort=e.target.closest("[data-whot-hand-sort]");if(handSort&&active==='whot'){whotSortExpanded(handSort.value);return;}
const opponent=e.target.closest("[data-ludo-opponent]");if(opponent&&active==="ludo"){window.NightLudo.changeOpponent(state.ludo,opponent.value);render();return;}
const difficulty=e.target.closest("[data-ludo-difficulty]");if(difficulty&&active==="ludo"){window.NightLudo.changeDifficulty(state.ludo,difficulty.value);render();return;}
const opt=e.target.closest("[data-ludo-option]");if(opt&&active==="ludo"){window.NightLudo.changeOption(state.ludo,opt.dataset.ludoOption,opt.checked);render();return;}
const speed=e.target.closest("[data-ludo-speed]");if(speed&&active==="ludo"){window.NightLudo.changeOption(state.ludo,"speed",speed.value);render();return;}
const dm=e.target.closest("[data-dice-mode]");if(dm&&active==="ludo"){window.NightLudo.changeDice(state.ludo,Number(dm.value));render();return;}const sel=e.target.closest("[data-players]");if(!sel)return;const id=sel.dataset.players,n=Math.max(2,Math.min(4,Number(sel.value)||2));if(id==="ludo"){window.NightLudo.changePlayers(state.ludo,n);}if(id==="estate"){const order=["red","blue","green","yellow"];reset("estate");state.estate.players=order.slice(0,n).map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],cash:1500,pos:0,bankrupt:false}));}if(id==="snakes"){const order=["red","blue","green","yellow"];reset("snakes");state.snakes.players=order.slice(0,n).map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],pos:1}));}render();});
document.addEventListener("keydown",e=>{if(e.key==='Escape'&&board.querySelector('.whot-hand-overlay')){e.preventDefault();whotCloseExpanded();}});
document.addEventListener("click",e=>{
const dice=e.target.closest("[data-ludo-die]");if(dice&&active==="ludo"){if(state.ludo.opponent==='computer'&&state.ludo.turn===1)return;window.NightLudo.selectDie(state.ludo,Number(dice.dataset.ludoDie),render);return;}const piece=e.target.closest("[data-piece]");if(piece&&active==="ludo"){const [pi,ti]=piece.dataset.piece.split("-").map(Number);ludoMove(pi,ti);return;}
const whotCall=e.target.closest("[data-whot-call]");if(whotCall&&active==='whot'){
 const selected=Number(whotCall.dataset.whotCall),s=state.whot;
 if(!cardMotionBusy&&Number.isInteger(s.pendingWhot)&&Number.isInteger(selected)&&selected>=0&&selected<5){
  cardPlay('whot',s.pendingWhot,selected);
 }
 return;
}
const card=e.target.closest("[data-card]");if(card){if(Number(card.dataset.card)<0)return;cardPlay(active,Number(card.dataset.card),null,card.closest('.whot-hand-overlay')?card:null);return;}
const sq=e.target.closest("[data-estate-info]");if(sq&&active==="estate"){state.estate.inspect=Number(sq.dataset.estateInfo);render();return;}
const action=e.target.closest("[data-action]");if(!action)return;
switch(action.dataset.action){case"whot-hand-open":whotOpenExpanded();break;case"whot-hand-close":whotCloseExpanded();break;case"ludo-roll":ludoRoll();break;case"ludo-new":if(confirm("Restart the Ludo match?")){closeLudoSettings();reset("ludo");}break;case"ludo-sound":state.ludo.sound=!state.ludo.sound;render();break;case"estate-roll":estateRoll();break;case"estate-buy":estateDecision(true);break;case"estate-pass":estateDecision(false);break;case"estate-end":estateEnd();break;case"card-draw":cardDraw(active);break;case"whot-rematch":if(active==="whot"&&state.whot.winner!==null){if(whotOptions.mode==="tournament"&&Math.max(...(whotCareer.tournamentWins||[0,0]))>=2){whotCareer.tournamentWins=[0,0];whotStore()}reset("whot")}break;
case"whot-last-call":if(active==="whot"&&state.whot.turn===0&&state.whot.hands[0].length===1){state.whot.calledLast=[true,Boolean(state.whot.calledLast?.[1])];state.whot.msg="Last card declared!";render()}break;case"whot-call-cancel":if(active==='whot'&&!cardMotionBusy){state.whot.pendingWhot=null;render();}break;case"snakes-roll":snakesRoll();break;case"draw-ready":drawReady();break;case"draw-next":drawNext();break;case"draw-guess":drawReadyToGuess();break;case"draw-clear":if(drawCtx){drawCtx.clearRect(0,0,800,600);}break;case"draw-erase":setBrush("erase");break;case"checkers-reset":reset("checkers");break;}
});
// Read-only motion diagnostics for real Chromium touch and frame tests.
window.__nightWhotMotion=()=>({...cardMotionMetrics,timings:{...whotMotion},busy:cardMotionBusy,waitingForCall:state.whot?.pendingWhot!=null,
 visibleFlights:document.querySelectorAll('.whot-flight').length,
 cardNodes:board.querySelectorAll('.hand .whot-card').length,handLayout:board.querySelector('.hand')?.dataset.whotLayout||null,expandedAvailable:!!board.querySelector('.whot-view-hand:not([hidden])')});
function go(){pick(new URL(location.href).searchParams.get("game")||"checkers");}
window.addEventListener("popstate",go);
go();
})();
