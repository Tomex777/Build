/* NIGHT Play Club — phone-first local games. No remote players, no collection of personal data. */
(function(){
"use strict";
const $=id=>document.getElementById(id);
const board=$("board"),controls=$("controls"),status=$("status"),help=$("help"),picks=$("gamePicks");
const KEY="night-play-club-v1";
const state={};let active="ludo",timer=null,drawCtx=null;
const colors={red:"#df3439",green:"#328c4b",yellow:"#e8b32a",blue:"#2767bc"};
const titles={ludo:["Nigerian Ludo","The board you remember.","BOARD GAME"],estate:["NIGHT Estates","Buy, trade and collect rent.","PROPERTY GAME"],whot:["Shape Cards","Traditional shape-matching card play.","CARD GAME"],colors:["Colour Clash","A quick colour-matching card battle.","CARD GAME"],draw:["Draw & Guess","One draws. Everyone else guesses.","PARTY GAME"],snakes:["Snakes & Ladders","Climb fast. Slide carefully.","BOARD GAME"],checkers:["Draughts","A proper tabletop strategy game.","BOARD GAME"]};
function h(s){return String(s).replace(/[&<>"']/g,x=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[x]));}
function shuffle(a){for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
function die(){return Math.floor(Math.random()*6)+1;}
function save(){if(active==='ludo'&&(state.ludo?.rolling||state.ludo?.moving))return;try{const copy={...state};delete copy.draw;localStorage.setItem(KEY,JSON.stringify(copy));}catch{}}
function load(){try{const data=JSON.parse(localStorage.getItem(KEY)||"{}");for(const k of Object.keys(titles)){if(data[k]&&typeof data[k]==="object")state[k]=data[k];}}catch{}}
function setStatus(t){status.textContent=t;}
function controlsHTML(markup){controls.innerHTML=markup;}
function rules(title,desc){help.innerHTML="<h3>"+title+"</h3><p>"+desc+"</p>";}
function scoreList(players,getScore){return '<div class="score-list">'+players.map((p,i)=>'<div class="score-row"><span><b style="color:'+h(p.color)+'">●</b> '+h(p.name)+'</span><b>'+h(getScore(p,i))+'</b></div>').join("")+"</div>";}
function choosePlayers(id,selected,max){return '<label class="small">Players (local, sharing one phone)</label><select class="select" data-players="'+id+'">'+[2,3,4].filter(n=>n<=max).map(n=>'<option value="'+n+'"'+(n===selected?" selected":"")+'>'+n+' players</option>').join("")+"</select>";}
function pick(id){if(!titles[id])id="ludo";active=id;document.body.classList.toggle("ludo-mode",id==="ludo");$("ludoTopbar").hidden=id!=="ludo";$("ludoHud").hidden=id!=="ludo";if(id!=="ludo")closeLudoSettings();const d=titles[id];$("gameTitle").textContent=d[0];$("gameSubtitle").textContent=d[1];$("gameCategory").textContent=d[2];[...picks.querySelectorAll("button")].forEach(x=>x.setAttribute("aria-current",String(x.dataset.pick===id)));const url=new URL(location.href);url.searchParams.set("game",id);history.replaceState(null,"",url.pathname+url.search);if(id!=="ludo")window.NightLudo.cancel();if(id==="ludo"&&state.ludo){state.ludo=window.NightLudo.normalize(state.ludo);state.ludo.rolling=false;state.ludo.moving=false;}if(!state[id])reset(id);else render();}
function reset(id){
if(id==="ludo"){window.NightLudo.cancel();state.ludo=window.NightLudo.create();}
if(id==="estate"){state.estate={players:["red","blue"].map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],cash:1500,pos:0,bankrupt:false})),owners:{},upgrades:{},turn:0,rolled:false,last:0,pending:null,rounds:0,winner:null,msg:"Roll the dice to start buying properties.",inspect:0};}
if(id==="whot"){state.whot=newShapes();}
if(id==="colors"){state.colors=newColors();}
if(id==="draw"){state.draw={players:["Player 1","Player 2"],drawer:0,word:null,mode:"ready",points:[0,0],round:1,seconds:60,log:[]};}
if(id==="snakes"){state.snakes={players:[{name:"Red",color:colors.red,pos:1},{name:"Blue",color:colors.blue,pos:1}],turn:0,last:0,msg:"Roll to start climbing.",winner:null};}
if(id==="checkers"){const cells=Array(64).fill(null);for(let r=0;r<8;r++)for(let c=0;c<8;c++)if((r+c)%2===1){if(r<3)cells[r*8+c]="b";if(r>4)cells[r*8+c]="w";}state.checkers={cells,turn:"w",selected:null,forced:null,winner:null,msg:"White moves first."};}
save();render();
}
function render(){if(timer){clearInterval(timer);timer=null;}if(active==="ludo")renderLudo();if(active==="estate")renderEstate();if(active==="whot")renderCards("whot");if(active==="colors")renderCards("colors");if(active==="draw")renderDraw();if(active==="snakes")renderSnakes();if(active==="checkers")renderCheckers();save();}
const track=[[6,1],[6,2],[6,3],[6,4],[6,5],[5,6],[4,6],[3,6],[2,6],[1,6],[0,6],[0,7],[0,8],[1,8],[2,8],[3,8],[4,8],[5,8],[6,9],[6,10],[6,11],[6,12],[6,13],[6,14],[7,14],[8,14],[8,13],[8,12],[8,11],[8,10],[8,9],[9,8],[10,8],[11,8],[12,8],[13,8],[14,8],[14,7],[14,6],[13,6],[12,6],[11,6],[10,6],[9,6],[8,5],[8,4],[8,3],[8,2],[8,1],[8,0],[7,0],[6,0]];
const starts={red:0,green:13,yellow:26,blue:39};
const safe=new Set([0,8,13,21,26,34,39,47]);
const home={red:[[7,1],[7,2],[7,3],[7,4],[7,5],[7,7]],green:[[1,7],[2,7],[3,7],[4,7],[5,7],[7,7]],yellow:[[7,13],[7,12],[7,11],[7,10],[7,9],[7,7]],blue:[[13,7],[12,7],[11,7],[10,7],[9,7],[7,7]]};
function pieceCell(player,pos){if(pos<0)return null;if(pos>=51)return home[player.key][pos-51];return track[(starts[player.key]+pos)%52];}
function ludoValid(s,pi,ti){if(s.winner!==null||!s.rolled||s.turn!==pi)return false;const p=s.players[pi].tokens[ti];return p===-1?s.die===6:p<56&&p+s.die<=56;}
function renderLudo(){
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
  details.insertAdjacentHTML("beforeend",'<div class="nj-in-sheet-extra"><button type="button" class="secondary" data-action="ludo-new">↻ New match</button><button type="button" class="secondary" data-action="ludo-sound">'+(s.sound?'♫ Sound ON':'♪ Sound OFF')+'</button></div>');
 }
 if(controls.querySelector(".nj-settings[open]")){$("ludoSheetBack").hidden=false;$("ludoSettingsBtn").setAttribute("aria-expanded","true");}
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
function newShapes(){let deck=[];shapes.forEach((shape,s)=>{for(let n=1;n<=14;n++)if(!(s===4&&n>8))deck.push({s,n});});for(let i=0;i<5;i++)deck.push({s:5,n:20});shuffle(deck);return {hands:[deck.splice(0,7),deck.splice(0,7)],deck,discard:[deck.shift()],turn:0,winner:null,called:null,msg:"Match the shape or number.",round:0};}
function newColors(){let deck=[];for(let c=0;c<4;c++){for(let n=0;n<=9;n++)deck.push({c,n:String(n)});for(const n of ["SKIP","REV","+2"])for(let k=0;k<2;k++)deck.push({c,n});}for(let k=0;k<4;k++){deck.push({c:4,n:"WILD"},{c:4,n:"+4"});}shuffle(deck);return {hands:[deck.splice(0,7),deck.splice(0,7)],deck,discard:[deck.shift()],turn:0,winner:null,called:null,msg:"Match the colour or symbol.",round:0};}
function topCard(s){return s.discard[s.discard.length-1];}
function take(s,pi,n){for(let j=0;j<n;j++){if(!s.deck.length){if(s.discard.length<=1)break;const card=s.discard.pop();s.deck=shuffle(s.discard.splice(0));s.discard=[card];}if(s.deck.length)s.hands[pi].push(s.deck.pop());}}
function playable(kind,s,card){const top=topCard(s);return kind==="whot"?(card.s===5||card.s===(s.called===null?top.s:s.called)||card.n===top.n):(card.c===4||card.c===(s.called===null?top.c:s.called)||card.n===top.n);}
function nextCardTurn(kind,s,pi,card,selected){s.called=null;s.discard.push(card);s.round++;const hand=s.hands[pi];if(!hand.length){s.winner=pi;s.msg=(pi===0?"You win!":"Computer wins.");return;}
let opp=1-pi,again=false,draw=0;
if(kind==="whot"){
if(card.n===20)s.called=selected;
if(card.n===1||card.n===8)again=true;
if(card.n===2)draw=2;if(card.n===5)draw=3;if(card.n===14)draw=1;
}else{
if(card.c===4)s.called=selected;
if(card.n==="SKIP"||card.n==="REV")again=true;
if(card.n==="+2")draw=2;if(card.n==="+4")draw=4;
}
if(draw){take(s,opp,draw);again=true;}
s.msg=(pi===0?"You":"Computer")+" played "+(kind==="whot"?(card.n===20?"CHANGE":card.n+" "+shapes[card.s]):card.n)+ (draw?" · opponent draws "+draw:"")+(again?" · play again":"");
s.turn=again?pi:opp;
}
function cardPlay(kind,index){
const s=state[kind];if(s.turn!==0||s.winner!==null)return;const card=s.hands[0][index];if(!card||!playable(kind,s,card))return;
let chosen=null;if(kind==="whot"&&card.n===20)chosen=Number($("wishShape").value);if(kind==="colors"&&card.c===4)chosen=Number($("wishColor").value);
s.hands[0].splice(index,1);nextCardTurn(kind,s,0,card,chosen);render();if(s.turn===1)queueCPU(kind);
}
function cardDraw(kind){const s=state[kind];if(s.turn!==0||s.winner!==null)return;take(s,0,1);s.turn=1;s.msg="You drew a card. Computer's turn.";render();queueCPU(kind);}
function queueCPU(kind){setTimeout(()=>{if(active!==kind)return;const s=state[kind];if(!s||s.turn!==1||s.winner!==null)return;
const idx=s.hands[1].findIndex(card=>playable(kind,s,card));if(idx<0){take(s,1,1);s.turn=0;s.msg="Computer picked up a card.";render();return;}
const card=s.hands[1].splice(idx,1)[0],option=kind==="whot"?Math.floor(Math.random()*5):Math.floor(Math.random()*4);nextCardTurn(kind,s,1,card,option);render();if(s.turn===1)queueCPU(kind);
},470);}
function cardMarkup(kind,card,index,enabled){
if(kind==="whot"){const sym=card.n===20?"★":icons[card.s];return '<button class="playing-card'+(enabled?' playable':'')+'" data-card="'+index+'" type="button" '+(enabled?'':'disabled')+' aria-label="'+(card.n===20?'Change shape':card.n+" "+shapes[card.s])+'"><span class="rank">'+card.n+'</span><span class="symbol">'+sym+'</span><span class="shape-name">'+(card.n===20?"CHANGE":shapes[card.s].toUpperCase())+'</span></button>';}
const clr=["#d93739","#2e81c0","#2b8d61","#d7a925","#302d3a"][card.c];return '<button class="color-card'+(enabled?' playable':'')+'" style="--color:'+clr+'" data-card="'+index+'" '+(enabled?'':'disabled')+' aria-label="'+["Red","Blue","Green","Yellow","Wild"][card.c]+' '+h(card.n)+'"><span>'+h(card.n)+'</span></button>';
}
function renderCards(kind){
const s=state[kind],t=topCard(s),topSelected={...t};
if(s.called!==null)topSelected[kind==="whot"?"s":"c"]=s.called;
board.innerHTML='<div class="table-surface" aria-label="Card table"><h3>COMPUTER · '+s.hands[1].length+' CARDS</h3><div class="card-row"><div class="playing-card card-back" aria-label="Computer cards hidden"></div><span class="cards-counter">× '+s.hands[1].length+'</span></div><div class="card-pile"><span class="cards-counter">DECK '+s.deck.length+'</span>'+cardMarkup(kind,t,-1,false)+'</div><div class="hand"><div class="card-label">YOUR HAND · '+s.hands[0].length+' CARDS</div><div class="card-row">'+s.hands[0].map((card,i)=>cardMarkup(kind,card,i,s.turn===0&&s.winner===null&&playable(kind,s,card))).join("")+'</div></div></div>';
setStatus(s.winner!==null?s.msg:(s.turn===0?"Your turn. ":"Computer's turn. ")+s.msg);
let shape=kind==="whot"?'<label class="small" for="wishShape">Shape to call (change card)</label><select id="wishShape" class="select">'+shapes.map((sh,i)=>'<option value="'+i+'">'+sh+'</option>').join("")+'</select>':'<label class="small" for="wishColor">Colour to call (wild)</label><select id="wishColor" class="select">'+["Red","Blue","Green","Yellow"].map((sh,i)=>'<option value="'+i+'">'+sh+'</option>').join("")+'</select>';
controlsHTML('<div class="row"><button class="primary" type="button" data-action="card-draw" '+(s.turn!==0||s.winner!==null?"disabled":"")+'>Pick one card</button></div><p class="small">Cards left: you '+s.hands[0].length+' · computer '+s.hands[1].length+'</p>'+shape);
rules(kind==="whot"?"Shape Cards rules":"Colour Clash rules",kind==="whot"?"Play a matching <b>shape</b> or <b>number</b>. 1 & 8: play again; 2: opponent picks two; 5: opponent picks three; 14: opponent picks one; 20: choose a shape. Pick a card if you cannot play. This is our house-rules variant.":"Play a matching <b>colour</b> or <b>number/action</b>. Skip / reverse skips the computer, +2 and +4 draw penalties, and WILD lets you choose a colour. The first player to empty their hand wins. Original artwork; no UNO trademarks.");
}

/* A recognizable numbered board with every square and slide displayed. */
const ups={3:22,5:8,11:26,20:29,27:56,36:44,51:67,71:92,80:99};
const downs={17:4,19:7,21:9,43:34,47:27,54:31,62:18,64:60,87:24,93:73,95:75,98:79};
function renderSnakes(){
const s=state.snakes;let html='<div class="snakes-board" role="grid" aria-label="100 square snakes and ladders board">';
for(let row=0;row<10;row++)for(let c=0;c<10;c++){const base=(9-row)*10+1,num=(9-row)%2===0?base+c:base+(9-c);
let tokens=s.players.map((p,i)=>p.pos===num?'<span style="color:'+p.color+'" title="'+p.name+'">●</span>':"").join("");
let jump=ups[num]?"<span class='jump' aria-label='Ladder to "+ups[num]+"'>↗</span>":downs[num]?"<span class='jump' aria-label='Snake to "+downs[num]+"'>↘</span>":"";
html+='<div class="square" role="gridcell" aria-label="Square '+num+'" style="grid-row:'+(row+1)+';grid-column:'+(c+1)+'">'+num+jump+'<span class="marker">'+tokens+'</span></div>';
}board.innerHTML=html+"</div>";
setStatus(s.winner!==null?s.players[s.winner].name+" won the race to 100!":s.players[s.turn].name+"'s turn. "+s.msg);
controlsHTML('<div class="dice">'+("⚀⚁⚂⚃⚄⚅"[(s.last||1)-1])+'</div><button type="button" class="primary" data-action="snakes-roll" '+(s.winner!==null?"disabled":"")+'>Roll dice</button>'+scoreList(s.players,x=>"Square "+x.pos)+choosePlayers("snakes",s.players.length,4));
rules("Snakes & Ladders","Roll one die to advance. <b>↗</b> means climb a ladder to a higher square; <b>↘</b> means slide down a snake to a lower square. You must land on exactly 100 to win. Players share the same phone.");
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
const s=state.checkers,options=allCheckerMoves(s),valid=s.selected===null?[]:options.filter(m=>m.from===s.selected).map(m=>m.to);let html='<div class="checkerboard" role="group" aria-label="Eight by eight draughts board">';
for(let i=0;i<64;i++){const dark=(Math.floor(i/8)+i%8)%2===1,p=s.cells[i];html+='<button type="button" data-checker="'+i+'" class="checker-square '+(dark?"dark":"light")+(valid.includes(i)?" target":"")+'" aria-label="'+(p?(p.toLowerCase()==="w"?"White":"Black")+(p!==p.toLowerCase()?" king":" piece"):"Empty")+' row '+(Math.floor(i/8)+1)+' column '+(i%8+1)+'">'+(p?'<span class="checker-piece '+(p.toLowerCase()==="w"?"white":"black")+(s.selected===i?" selected":"")+'">'+(p===p.toLowerCase()?"":"♛")+'</span>':"")+'</button>'; }board.innerHTML=html+"</div>";
setStatus(s.winner!==null?(s.winner==="w"?"White":"Black")+" wins!":(s.turn==="w"?"White":"Black")+" to move. "+s.msg);
controlsHTML('<div class="row"><span class="badge">WHITE '+s.cells.filter(x=>x&&x.toLowerCase()==="w").length+'</span><span class="badge">BLACK '+s.cells.filter(x=>x&&x.toLowerCase()==="b").length+'</span></div><p class="small">Select a piece, then an empty square highlighted in yellow.</p><button class="secondary" data-action="checkers-reset">Restart draughts</button>');
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
const s=state.draw;if(s.mode!=="playing"){board.innerHTML='<div class="table-surface"><div class="secret">'+(s.mode==="reveal"?"Artist's word: "+h(s.word):"Ready for round "+s.round+"?")+'</div><p style="color:#f4f4eb;text-align:center;max-width:320px">The artist reads the secret word first. Everyone else should look away. Then hide the word and draw!</p></div>';}
else{
board.innerHTML='<div class="draw-board"><canvas id="drawCanvas" width="800" height="600" aria-label="Drawing canvas; draw with finger or mouse"></canvas></div><div class="toolbelt" id="brushes"><button type="button" data-brush="#202f2c" style="background:#202f2c" aria-label="Black ink" aria-pressed="true"></button><button type="button" data-brush="#e2343b" style="background:#e2343b" aria-label="Red ink"></button><button type="button" data-brush="#2876ca" style="background:#2876ca" aria-label="Blue ink"></button><button type="button" data-brush="#2a914e" style="background:#2a914e" aria-label="Green ink"></button><button type="button" data-brush="#e3b02a" style="background:#e3b02a" aria-label="Yellow ink"></button><button type="button" data-brush="#ffffff" aria-label="Eraser">⌫</button><button type="button" data-action="draw-clear">Clear drawing</button></div>';
const c=$("drawCanvas");drawCtx=c.getContext("2d");drawCtx.lineCap="round";drawCtx.lineJoin="round";
function pos(e){const box=c.getBoundingClientRect();return [Math.max(0,Math.min(800,(e.clientX-box.left)*800/box.width)),Math.max(0,Math.min(600,(e.clientY-box.top)*600/box.height))];}
c.addEventListener("pointerdown",e=>{painting=true;c.setPointerCapture(e.pointerId);lastPoint=pos(e);drawCtx.fillStyle=paintColor;drawCtx.beginPath();drawCtx.arc(lastPoint[0],lastPoint[1],brushWidth/2,0,Math.PI*2);drawCtx.fill();});
c.addEventListener("pointermove",e=>{if(!painting)return;const q=pos(e);drawCtx.beginPath();drawCtx.strokeStyle=paintColor;drawCtx.lineWidth=brushWidth;drawCtx.moveTo(lastPoint[0],lastPoint[1]);drawCtx.lineTo(q[0],q[1]);drawCtx.stroke();lastPoint=q;});
const stop=()=>{painting=false;lastPoint=null;};c.addEventListener("pointerup",stop);c.addEventListener("pointercancel",stop);c.addEventListener("lostpointercapture",stop);
$("brushes").addEventListener("click",e=>{const b=e.target.closest("[data-brush]");if(!b)return;paintColor=b.dataset.brush;brushWidth=paintColor==="#ffffff"?24:7;for(const btn of document.querySelectorAll("[data-brush]"))btn.setAttribute("aria-pressed",String(btn===b));});
}
const artist=s.players[s.drawer],sc='Player 1 '+s.points[0]+' · Player 2 '+s.points[1];setStatus(s.mode==="playing"?artist+" is drawing. "+s.seconds+" seconds left.":artist+"'s turn to draw.");
controlsHTML('<p class="badge">'+sc+'</p>'+(s.mode==="ready"?'<p class="small">Others look away while the artist reads their secret word.</p><button class="primary" data-action="draw-ready">Reveal my word</button>':s.mode==="reveal"?'<p class="small">Memorize the word, then hide it before showing the screen.</p><button class="primary" data-action="draw-ready">Hide word & start timer</button>':'<form id="guessForm"><label class="small" for="guessWord">Guess what the picture means</label><input class="guess-input" id="guessWord" maxlength="40" autocomplete="off" placeholder="Type a guess"><button class="primary" type="submit">Check guess</button></form><div class="small" id="drawTimer">Time left: '+s.seconds+'s</div><ul class="guess-log" id="guessLog"></ul>')+'<p><button class="secondary" data-action="draw-next">Next artist ↻</button></p>');
if(s.mode==="playing"){timer=setInterval(()=>{s.seconds=Math.max(0,s.seconds-1);const el=$("drawTimer");if(el)el.textContent="Time left: "+s.seconds+"s";if(s.seconds===0){clearInterval(timer);timer=null;s.mode="ready";s.drawer=1-s.drawer;s.round++;s.word=null;render();}},1000);}
rules("Party mode","One player sees a secret word and draws it. The other calls out guesses or types them into the box. Guess correctly before 60 seconds to earn a point. Swap artists after each round. <b>Local pass-and-play only</b>, not a cross-phone multiplayer room.");
}
function drawReady(){const s=state.draw;if(s.mode==="ready"){s.word=words[Math.floor(Math.random()*words.length)];s.mode="reveal";}else if(s.mode==="reveal"){s.mode="playing";s.seconds=60;paintColor="#202f2c";brushWidth=7;}render();}
function drawNext(){const s=state.draw;s.mode="ready";s.word=null;s.drawer=1-s.drawer;s.round++;render();}
function setBrush(color){paintColor=color;brushWidth=color==="#ffffff"?24:7;}
document.addEventListener("submit",e=>{if(e.target.id!=="guessForm")return;e.preventDefault();const s=state.draw;if(s.mode!=="playing")return;const input=$("guessWord");const guess=input.value.toLowerCase().trim().replace(/[^a-z0-9 ]/g,"");const answer=(s.word||"").toLowerCase().trim().replace(/[^a-z0-9 ]/g,"");if(!guess)return;if(guess===answer){s.points[1-s.drawer]++;const who=s.players[1-s.drawer];s.mode="ready";s.drawer=1-s.drawer;s.round++;alert("Correct! "+who+" gets a point. The word was "+s.word+".");s.word=null;render();return;}if($("guessLog"))$("guessLog").insertAdjacentHTML("afterbegin","<li>Not quite: "+h(guess)+"</li>");input.value="";});
document.addEventListener("click",e=>{const tile=e.target.closest("[data-checker]");if(tile&&active==="checkers")checkerClick(Number(tile.dataset.checker));});

load();picks.addEventListener("click",e=>{const btn=e.target.closest("[data-pick]");if(btn)pick(btn.dataset.pick);});
$("newRound").addEventListener("click",()=>{if(confirm("Start a new "+titles[active][0]+" match? Current progress will be cleared."))reset(active);});
document.addEventListener("change",e=>{
const opponent=e.target.closest("[data-ludo-opponent]");if(opponent&&active==="ludo"){window.NightLudo.changeOpponent(state.ludo,opponent.value);render();return;}
const difficulty=e.target.closest("[data-ludo-difficulty]");if(difficulty&&active==="ludo"){window.NightLudo.changeDifficulty(state.ludo,difficulty.value);render();return;}
const opt=e.target.closest("[data-ludo-option]");if(opt&&active==="ludo"){window.NightLudo.changeOption(state.ludo,opt.dataset.ludoOption,opt.checked);render();return;}
const speed=e.target.closest("[data-ludo-speed]");if(speed&&active==="ludo"){window.NightLudo.changeOption(state.ludo,"speed",speed.value);render();return;}
const dm=e.target.closest("[data-dice-mode]");if(dm&&active==="ludo"){window.NightLudo.changeDice(state.ludo,Number(dm.value));render();return;}const sel=e.target.closest("[data-players]");if(!sel)return;const id=sel.dataset.players,n=Math.max(2,Math.min(4,Number(sel.value)||2));if(id==="ludo"){window.NightLudo.changePlayers(state.ludo,n);}if(id==="estate"){const order=["red","blue","green","yellow"];reset("estate");state.estate.players=order.slice(0,n).map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],cash:1500,pos:0,bankrupt:false}));}if(id==="snakes"){const order=["red","blue","green","yellow"];reset("snakes");state.snakes.players=order.slice(0,n).map(k=>({name:k[0].toUpperCase()+k.slice(1),color:colors[k],pos:1}));}render();});
document.addEventListener("click",e=>{
const dice=e.target.closest("[data-ludo-die]");if(dice&&active==="ludo"){window.NightLudo.selectDie(state.ludo,Number(dice.dataset.ludoDie),render);return;}const piece=e.target.closest("[data-piece]");if(piece){const [pi,ti]=piece.dataset.piece.split("-").map(Number);ludoMove(pi,ti);return;}
const card=e.target.closest("[data-card]");if(card){if(Number(card.dataset.card)<0)return;cardPlay(active,Number(card.dataset.card));return;}
const sq=e.target.closest("[data-estate-info]");if(sq&&active==="estate"){state.estate.inspect=Number(sq.dataset.estateInfo);render();return;}
const action=e.target.closest("[data-action]");if(!action)return;
switch(action.dataset.action){case"ludo-roll":ludoRoll();break;case"ludo-new":if(confirm("Restart the Ludo match?")){closeLudoSettings();reset("ludo");}break;case"ludo-sound":state.ludo.sound=!state.ludo.sound;render();break;case"estate-roll":estateRoll();break;case"estate-buy":estateDecision(true);break;case"estate-pass":estateDecision(false);break;case"estate-end":estateEnd();break;case"card-draw":cardDraw(active);break;case"snakes-roll":snakesRoll();break;case"draw-ready":drawReady();break;case"draw-next":drawNext();break;case"draw-clear":if(drawCtx){drawCtx.clearRect(0,0,800,600);}break;case"draw-erase":setBrush("erase");break;case"checkers-reset":reset("checkers");break;}
});
function go(){pick(new URL(location.href).searchParams.get("game")||"ludo");}
window.addEventListener("popstate",go);
go();
})();
