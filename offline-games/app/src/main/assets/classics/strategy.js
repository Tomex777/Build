/* Capturing board strategy: small-board Go and Nine Men's Morris. */
(()=>{'use strict';const G=window.G,rand=a=>a[Math.floor(Math.random()*a.length)];
G.add('go','Go 5 × 5','Surround stones to capture them. Pass twice to end and count stones plus territory.','Board',()=>{
 const N=5;let cells=Array(N*N).fill(0),history=[],passes=0,finished=false,last='';
 const adjacent=i=>{const x=i%N,y=Math.floor(i/N);return [[1,0],[-1,0],[0,1],[0,-1]].map(([dx,dy])=>[x+dx,y+dy]).filter(([a,b])=>a>=0&&a<N&&b>=0&&b<N).map(([a,b])=>b*N+a)};
 function group(a,i){const owner=a[i],seen=new Set([i]),todo=[i],liberties=new Set();while(todo.length){const p=todo.pop();for(const n of adjacent(p)){if(a[n]===0)liberties.add(n);else if(a[n]===owner&&!seen.has(n)){seen.add(n);todo.push(n)}}}return {members:[...seen],liberties:liberties.size}}
 function play(side,i,commit=true){if(cells[i])return null;const a=cells.slice();a[i]=side;let captured=0;for(const n of adjacent(i)){if(a[n]===3-side){const g=group(a,n);if(!g.liberties){g.members.forEach(x=>a[x]=0);captured+=g.members.length}}}if(!group(a,i).liberties)return null;const signature=a.join('');if(history.length&&history[history.length-2]===signature)return null;if(commit){cells=a;history.push(signature);passes=0;last=(side===1?'You':'Computer')+(captured?' captured '+captured:' placed a stone')}return {captured,index:i}}
 function allLegal(side){return cells.flatMap((v,i)=>!v&&play(side,i,false)?[i]:[])}
 function territory(){let points=[0,0];cells.forEach((v,i)=>{if(v)points[v-1]++});const visited=new Set();cells.forEach((v,i)=>{if(v||visited.has(i))return;const stack=[i],region=[],owners=new Set();visited.add(i);while(stack.length){const p=stack.pop();region.push(p);for(const n of adjacent(p)){if(cells[n])owners.add(cells[n]);else if(!visited.has(n)){visited.add(n);stack.push(n)}}}if(owners.size===1)points[[...owners][0]-1]+=region.length});return points}
 function endCheck(){if(passes>=2||cells.every(v=>v!==0)){finished=true;const scores=territory();last=(scores[0]===scores[1]?'Draw':scores[0]>scores[1]?'You win':'Computer wins')+' · '+scores.join(' : ');if(scores[0]>scores[1])G.score('go',scores[0])}}
 function cpu(){const legal=allLegal(2);if(!legal.length){passes++;last='Computer passed';endCheck();return}const options=legal.map(i=>({...play(2,i,false),i})).sort((a,b)=>b.captured-a.captured);if(!options.length)return;play(2,options[0].captured?options[0].i:rand(options).i)}
 function pass(){if(finished)return;passes++;last='You passed';endCheck();if(!finished){cpu();endCheck()}render()}
 function render(){G.status(finished?last:(last||'Your turn')+' · Black '+cells.filter(x=>x===1).length+' / White '+cells.filter(x=>x===2).length);G.grid(N,N,cells.map(v=>v?'●':''),i=>{if(finished)return;if(!play(1,i))return;cpu();endCheck();render()},(b,i)=>{b.dataset.active=String(cells[i]===1);b.dataset.secondary=String(cells[i]===2);b.disabled=finished||!!cells[i]||!play(1,i,false)});G.actions();if(!finished)G.action('Pass',pass)}
 history.push(cells.join(''));render();G.note('Simple-ko rule, captures and suicide checks are enforced. This five-by-five variant counts area.');});
G.add('morris',"Nine Men's Morris",'Place nine stones, form mills of three, then move along lines and capture your opponent.','Board',()=>{
 const coords=[];for(const d of [0,1,2])coords.push([d,d],[3,d],[6-d,d],[6-d,3],[6-d,6-d],[3,6-d],[d,6-d],[d,3]);
 const L=24;let board=Array(L).fill(0),reserve=[0,9,9],selection=-1,toRemove=false,win=0;
 const adj=Array.from({length:24},()=>new Set()),mills=[];
 const connect=(a,b)=>{adj[a].add(b);adj[b].add(a)};
 for(let ring=0;ring<3;ring++){for(let k=0;k<8;k++)connect(ring*8+k,ring*8+(k+1)%8);for(const k of [0,2,4,6])mills.push([ring*8+k,ring*8+k+1,ring*8+(k+2)%8])}
 for(const k of [1,3,5,7]){connect(k,8+k);connect(8+k,16+k);mills.push([k,8+k,16+k])}
 const inMill=(i,side)=>mills.some(line=>line.includes(i)&&line.every(x=>board[x]===side));
 const hasMill=(i,side)=>inMill(i,side);
 const others=side=>board.flatMap((v,i)=>v===3-side?[i]:[]);
 const availableCaptures=side=>{const all=others(side),notMill=all.filter(i=>!inMill(i,3-side));return notMill.length?notMill:all};
 const stones=side=>board.filter(v=>v===side).length;
 const movement=side=>reserve[side]===0;
 function legalFrom(i,side){if(board[i]!==side)return[];if(reserve[side]>0)return[];return board.flatMap((v,j)=>v===0&&(stones(side)===3||adj[i].has(j))?[j]:[])}
 function hasMove(side){if(reserve[side]>0)return board.includes(0);return board.some((v,i)=>v===side&&legalFrom(i,side).length>0)}
 function checkWin(){for(const side of [1,2])if(reserve[side]===0&&(stones(side)<3||!hasMove(side))){win=3-side;break}}
 function cpu(){if(win)return;checkWin();if(win)return;let options=[];
 if(reserve[2]>0)options=board.flatMap((v,j)=>v===0?[{from:-1,to:j}]:[]);
 else board.forEach((v,i)=>{if(v===2)legalFrom(i,2).forEach(j=>options.push({from:i,to:j}))});
 if(!options.length){win=1;return}
 let ranked=options.map(o=>{const before=board[o.to];if(o.from>=0)board[o.from]=0;board[o.to]=2;const mill=hasMill(o.to,2);board[o.to]=before;if(o.from>=0)board[o.from]=2;return {...o,mill}});
 const chosen=rand(ranked.filter(o=>o.mill).length?ranked.filter(o=>o.mill):ranked);
 if(chosen.from>=0)board[chosen.from]=0;board[chosen.to]=2;if(reserve[2]>0)reserve[2]--;if(chosen.mill){const captures=availableCaptures(2);if(captures.length)board[rand(captures)]=0}checkWin()}
 function play(i){if(win)return;
 if(toRemove){if(availableCaptures(1).includes(i)){board[i]=0;toRemove=false;cpu();checkWin()}render();return}
 if(reserve[1]>0){if(board[i])return;board[i]=1;reserve[1]--;if(hasMill(i,1)){toRemove=true}else{cpu();checkWin()}render();return}
 if(selection<0){if(board[i]===1)selection=i;render();return}
 if(board[i]===1){selection=i;render();return}
 if(board[i]===0&&legalFrom(selection,1).includes(i)){board[selection]=0;board[i]=1;selection=-1;if(hasMill(i,1)){toRemove=true}else{cpu();checkWin()}}render()}
 function render(){G.status(win?(win===1?'You win!':'Computer wins!'):toRemove?'Mill formed! Capture one of the highlighted stones':reserve[1]?'Place your stones · '+reserve[1]+' left':'Move one of your stones · '+stones(1)+' remain');
 const container=G.el('div','morris-board');
 const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 360 360');svg.setAttribute('aria-hidden','true');svg.style.cssText='width:100%;height:100%;position:absolute;left:0;top:0;pointer-events:none';
 const lines=G.el('span');const segments=[];
 for(let ring=0;ring<3;ring++){const d=ring*50;segments.push('<rect x="'+(30+d)+'" y="'+(30+d)+'" width="'+(300-2*d)+'" height="'+(300-2*d)+'"/>')}
 segments.push('<path d="M180 30 V130 M330 180 H230 M180 330 V230 M30 180 H130"/>');
 svg.innerHTML='<g fill="none" stroke="#9bad9d" stroke-width="3" stroke-linecap="round">'+segments.join('')+'</g>';container.append(svg);
 coords.forEach(([col,row],i)=>{const b=G.el('button','morris-node'+(board[i]===1?' yours':board[i]===2?' cpu':'')+(selection===i?' selected':''));
 b.type='button';b.style.left=(30+col*50)/360*100+'%';b.style.top=(30+row*50)/360*100+'%';
 b.setAttribute('aria-label','Node '+(i+1)+(board[i]===1?' yours':board[i]===2?' computer':''));if(toRemove&&availableCaptures(1).includes(i))b.classList.add('capture');
 b.onclick=()=>play(i);container.append(b)});G.board().append(container);
 if(win===1)G.score('morris',1)}
 render();G.note('The three concentric squares are connected at their midpoint bridges. Forming a mill grants a capture.');});
})();