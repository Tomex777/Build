/* Offline non-wagering card and dice games. Original gameplay + styling. */
(()=>{'use strict';const G=window.G,suits=['♠','♥','♦','♣'],rank=n=>['','A','2','3','4','5','6','7','8','9','10','J','Q','K'][n];
const fresh=()=>G.shuffle(Array.from({length:52},(_,i)=>({n:i%13+1,s:suits[Math.floor(i/13)]})));
function card(c){return G.el('div','playing-card'+(((c&&c.s)==='♥'||(c&&c.s)==='♦')?' red':''),c?rank(c.n)+c.s:'—')}
function row(parent,values){const r=G.el('div','card-row');r.style.flexWrap='wrap';for(const x of values)r.append(card(x));parent.append(r);return r}
G.add('blackjack','Blackjack','Reach 21 without going over. The dealer automatically stands on 17. No betting.','Card',()=>{
 let shoe=fresh(),hand=[],dealer=[],wins=0,rounds=0,ended=false;
 const take=()=>{if(shoe.length<1)shoe=fresh();return shoe.pop()},value=cs=>{let score=cs.reduce((a,c)=>a+(c.n>10?10:c.n),0),aces=cs.filter(c=>c.n===1).length;while(aces--&&score+10<=21)score+=10;return score};
 function deal(){hand=[take(),take()];dealer=[take(),take()];ended=false;render()}
 function stand(){if(ended)return;while(value(dealer)<17)dealer.push(take());end()}
 function end(){ended=true;rounds++;const a=value(hand),b=value(dealer);if(a<=21&&(b>21||a>b)){wins++;G.score('blackjack',wins)}render()}
 function render(){const a=value(hand),b=value(dealer);G.status((ended?(a>21?'Bust':b>21||a>b?'You win':a===b?'Draw':'Dealer wins'):'Your turn')+' · '+wins+'/'+rounds+' rounds won');const board=G.board();board.append(G.el('p','eyebrow','DEALER'));row(board,ended?dealer:[dealer[0],null]);board.append(G.el('p','hint',ended?'Total '+b:'Second card hidden'));board.append(G.el('p','eyebrow','YOUR HAND · '+a));row(board,hand);G.actions();if(ended)G.action('Deal next round',deal,true);else{G.action('Hit',()=>{hand.push(take());if(value(hand)>21)end();else render()},true);G.action('Stand',stand)}}
 deal();G.note('Aces count as one or eleven. The dealer reveals its hidden card when you stand.');});
G.add('pokerdice','Poker Dice','Keep or reroll five dice up to three times, then record the best combination.','Card',()=>{
 let dice=[1,1,1,1,1],kept=Array(5).fill(false),rolls=0,points=0,hands=0;
 function rating(){const counts=Array.from({length:6},(_,i)=>dice.filter(x=>x===i+1).length).sort((a,b)=>b-a),sorted=dice.slice().sort().join('');if(counts[0]===5)return[50,'Five of a kind'];if(counts[0]===4)return[35,'Four of a kind'];if(counts[0]===3&&counts[1]===2)return[30,'Full house'];if(sorted==='12345'||sorted==='23456')return[25,'Straight'];if(counts[0]===3)return[20,'Three of a kind'];if(counts[0]===2&&counts[1]===2)return[15,'Two pairs'];if(counts[0]===2)return[7,'One pair'];return[3,'High die']}
 function roll(){if(rolls>=3)return;dice=dice.map((v,i)=>kept[i]?v:Math.floor(Math.random()*6)+1);rolls++;draw()}
 function save(){if(!rolls)return;const p=rating()[0];points+=p;hands++;G.score('pokerdice',points);rolls=0;kept.fill(false);dice.fill(1);draw()}
 function draw(){G.status('Total '+points+' · Hand '+(hands+1)+' · Roll '+rolls+'/3');const board=G.board(),r=G.el('div','card-row');r.style.flexWrap='wrap';dice.forEach((v,i)=>{const b=G.el('button','playing-card',String(v));b.type='button';b.style.width='min(17%,72px)';b.style.fontSize='30px';b.style.background=kept[i]?'#c9e1d0':'#fff';b.setAttribute('aria-pressed',String(kept[i]));b.onclick=()=>{if(rolls){kept[i]=!kept[i];draw()}};r.append(b)});board.append(r);G.actions();if(rolls<3)G.action(rolls?'Reroll free dice':'Roll all dice',roll,true);if(rolls){const [p,name]=rating();G.action('Score '+name+' ('+p+')',save)}}
 draw();G.note('Tap a die to keep it between throws. Score your result after any of three rolls.');});
G.add('speed','Speed','Rapidly place cards one rank above or below either centre pile, racing a computer.','Card',()=>{
 const all=fresh(),center=[all.pop(),all.pop()],mine={hand:all.splice(0,5),stock:all.splice(0,20)},cpu={hand:all.splice(0,5),stock:all.splice(0,20)};let ended=false,seconds=0,timer=null;
 const adjacent=(a,b)=>Math.abs(a.n-b.n)===1||(a.n===1&&b.n===13)||(a.n===13&&b.n===1);
 function legal(c){return center.some(p=>adjacent(c,p))}
 function place(who,index){if(ended)return false;const side=who===0?mine:cpu,card=side.hand[index];if(!card)return false;const pile=center.findIndex(c=>adjacent(card,c));if(pile<0)return false;center[pile]=card;side.hand.splice(index,1);if(side.stock.length)side.hand.push(side.stock.pop());if(!side.hand.length&&!side.stock.length){ended=true;G.status(who===0?'You win the speed round!':'Computer won this round');if(who===0)G.score('speed',Math.max(1,100-Math.floor(seconds)))}return true}
 function blocked(){return !mine.hand.some(legal)&&!cpu.hand.some(legal)}
 function refresh(){if(!blocked()||ended)return;center.splice(0,2,{n:Math.floor(Math.random()*13)+1,s:suits[0]},{n:Math.floor(Math.random()*13)+1,s:suits[1]});render()}
 function render(){const board=G.board();board.append(G.el('p','eyebrow','COMPUTER · '+(cpu.hand.length+cpu.stock.length)+' CARDS'));const covered=G.el('div','card-row');for(let i=0;i<cpu.hand.length;i++)covered.append(G.el('div','playing-card backside','◆'));board.append(covered);
 board.append(G.el('p','eyebrow','CENTRE PILES'));row(board,center);board.append(G.el('p','eyebrow','YOUR CARDS · '+(mine.hand.length+mine.stock.length)+' LEFT'));
 const hand=G.el('div','card-row');hand.style.flexWrap='wrap';mine.hand.forEach((c,i)=>{const btn=G.el('button','playing-card'+(['♥','♦'].includes(c.s)?' red':''),rank(c.n)+c.s);btn.type='button';btn.style.width='min(18%,90px)';btn.style.fontSize='25px';btn.disabled=!legal(c)||ended;btn.onclick=()=>{place(0,i);render()};hand.append(btn)});board.append(hand);G.actions();if(blocked()&&!ended)G.action('Refresh centre cards',refresh,true);if(!ended)G.status('Play a rank one higher or lower · '+Math.ceil(seconds)+'s')}
 timer=setInterval(()=>{if(ended){clearInterval(timer);return}seconds+=.65;const choices=cpu.hand.map((c,i)=>legal(c)?i:-1).filter(i=>i>=0);if(choices.length)place(1,choices[Math.floor(Math.random()*choices.length)]);render()},650);
 render();G.note('You and the computer play at the same time. Suits do not matter. A and K are adjacent.');return()=>clearInterval(timer)});
})();