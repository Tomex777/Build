/* Four non-wagering offline card games. No payments, betting or network. */
(()=>{'use strict';const G=window.G,rand=a=>a[Math.floor(Math.random()*a.length)];
const suits=['♠','♥','♦','♣'],rank=n=>['','A','2','3','4','5','6','7','8','9','10','J','Q','K'][n],deck=()=>G.shuffle(Array.from({length:52},(_,i)=>({value:i%13+1,suit:suits[Math.floor(i/13)]})));
function card(c,back=false){return G.el('div','playing-card'+(back?' backside':c&&(c.suit==='♥'||c.suit==='♦')?' red':''),back?'◆':c?rank(c.value)+c.suit:'—')}
function two(a,b){let row=G.el('div','card-row');row.append(card(a),card(b));G.board().append(row)}
G.add('higher','Higher or Lower','Predict whether the next card is higher or lower. Equal ranks are a push.','Card',()=>{
 let pack=deck(),current=pack.pop(),streak=0,last=null,ended=false;
 function render(){const holder=G.board(),row=G.el('div','card-row');row.append(card(current),card(last));holder.append(row);G.status(ended?'Deck complete · best streak '+G.best('higher'):'Correct streak: '+streak+' · '+pack.length+' cards left');G.actions();if(!ended){for(const [label,guess] of [['Higher',1],['Lower',-1]])G.action(label,()=>{const old=current.value;current=pack.pop();last=current;const actual=Math.sign(current.value-old);if(actual===guess){streak++;G.score('higher',streak)}else if(actual!==0)streak=0;if(!pack.length)ended=true;render();if(!ended)G.status(actual===0?'Same rank — push':actual===guess?'Correct! Streak '+streak:'Incorrect — streak reset')},label==='Higher')}}
 render();G.note('Aces are low. A wrong prediction resets the streak.');});
G.add('war','War','Reveal one card each round. The higher rank collects the two cards.','Card',()=>{
 const all=deck();let player=all.slice(0,26),cpu=all.slice(26),won=[0,0],last=[null,null],ties=0;
 function flip(){if(!player.length||!cpu.length)return;let a=player.pop(),b=cpu.pop(),stake=2;last=[a,b];if(a.value===b.value){ties++;const pool=[a,b];while(player.length&&cpu.length){const burned=Math.min(3,player.length-1,cpu.length-1);for(let i=0;i<burned;i++)pool.push(player.pop(),cpu.pop());if(!player.length||!cpu.length)break;a=player.pop();b=cpu.pop();last=[a,b];pool.push(a,b);if(a.value!==b.value)break}stake=pool.length;if(a.value===b.value){won[0]+=Math.floor(stake/2);won[1]+=stake-Math.floor(stake/2)}}if(a.value>b.value)won[0]+=stake;else if(b.value>a.value)won[1]+=stake;render()}
 function render(){G.board().replaceChildren();two(...last);G.status(player.length&&cpu.length?'You '+won[0]+' · CPU '+won[1]+' · '+Math.min(player.length,cpu.length)+' pairs left':(won[0]===won[1]?'Draw':won[0]>won[1]?'You win':'Computer wins')+' · '+won[0]+' : '+won[1]);G.actions();if(player.length&&cpu.length)G.action('Flip cards',flip,true);else G.score('war',won[0])}
 render();G.note('Equal ranks cause a war: burn up to three cards each, then compare again.');});
G.add('snap','Snap','Spot identical ranks and call SNAP. A false call loses a point.','Card',()=>{
 let pack=deck(),pair=[null,null],score=0,round=0,ended=false;
 function draw(){G.board().replaceChildren();two(...pair);G.status(ended?'Deck complete · '+score+' points':'Round '+round+' · '+score+' points');G.actions();if(!ended){G.action('SNAP!',()=>{if((pair[0]&&pair[0].value)===(pair[1]&&pair[1].value))score+=3;else score=Math.max(0,score-1);G.score('snap',score);next()},true);G.action('Next pair',next)}}
 function next(){if(pack.length<2){ended=true;draw();return}pair=[pack.pop(),pack.pop()];round++;draw()}next();G.note('Only identical ranks match; suits do not matter.');});
G.add('crazyeights','Crazy Eights','Match the rank or suit; playing an eight lets you choose a new suit.','Card',()=>{
 let pack=deck(),hands=[[],[]],face=null,active='',ended=false;
 const take=()=>pack.length?pack.pop():null;for(let k=0;k<7;k++){hands[0].push(take());hands[1].push(take())}face=take();active=face.suit;
 const legal=c=>c.value===8||c.value===face.value||c.suit===active;
 function place(c,who,suitChoice){hands[who].splice(hands[who].indexOf(c),1);face=c;active=c.value===8?(suitChoice||rand(suits)):c.suit;ended=!hands[who].length}
 function computer(){if(ended)return;const choices=hands[1].filter(legal);if(choices.length)place(rand(choices),1);else{const c=take();if(c)hands[1].push(c);else ended=true}}
 function human(c,choice){if(ended||!legal(c))return;place(c,0,choice);if(!ended)computer();render()}
 function render(){G.status(ended?(hands[0].length===0?'You win!':hands[1].length===0?'CPU wins':'Draw pile exhausted'):'Match '+rank(face.value)+face.suit+' · Active suit '+active+' · Computer '+hands[1].length+' cards');
 const b=G.board(),row=G.el('div','card-row');row.append(card(face));b.append(row);b.append(G.el('p','eyebrow','YOUR HAND'));const hand=G.el('div','card-row');hand.style.flexWrap='wrap';hand.style.gap='6px';hands[0].forEach(c=>{const btn=G.el('button','playing-card'+(['♥','♦'].includes(c.suit)?' red':''),rank(c.value)+c.suit);btn.style.width='min(24%,95px)';btn.style.fontSize='25px';btn.disabled=ended||!legal(c);btn.onclick=()=>{if(c.value!==8){human(c);return}G.actions();suits.forEach(s=>G.action('Choose '+s,()=>human(c,s),true))};hand.append(btn)});b.append(hand);G.actions();if(!ended)G.action('Draw one',()=>{const c=take();if(c)hands[0].push(c);else ended=true;if(!ended)computer();render()},true)}
 render();G.note('An eight can be played on any suit or rank. Select its replacement suit.');});
})();