/* Two complete offline visual puzzle engines — drag/path logic and block placement. */
(()=>{'use strict';const G=window.G;
G.add('patternlock','Pattern Lock','Remember the connected dot path, then repeat its nodes in order.','Puzzle',()=>{
 let sequence=[],entered=[],round=0,phase='intro',timers=[],mistakes=0;
 const area=G.board(),board=G.el('div','memory-pattern'),svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
 svg.setAttribute('viewBox','0 0 300 300');svg.style.cssText='position:absolute;inset:0;width:100%;height:100%;pointer-events:none';board.style.cssText='position:relative;width:min(100%,430px);aspect-ratio:1;margin:auto;display:grid;grid-template-columns:repeat(3,1fr);gap:14px;padding:20px';
 board.append(svg);area.append(board);let buttons=[];
 for(let i=0;i<9;i++){const b=G.el('button','cell','');b.type='button';b.style.cssText='border-radius:50%;width:52%;height:52%;align-self:center;justify-self:center;background:#e5efe4;border:5px solid #9fbaa6;min-height:0;aspect-ratio:1';b.setAttribute('aria-label','Dot '+(i+1));b.onclick=()=>tap(i);board.append(b);buttons.push(b)}
 function showLine(list,color){svg.replaceChildren();for(let n=0;n<list.length-1;n++){const a=list[n],b=list[n+1],line=document.createElementNS('http://www.w3.org/2000/svg','line');line.setAttribute('x1',String(50+a%3*100));line.setAttribute('y1',String(50+Math.floor(a/3)*100));line.setAttribute('x2',String(50+b%3*100));line.setAttribute('y2',String(50+Math.floor(b/3)*100));line.setAttribute('stroke',color);line.setAttribute('stroke-width','11');line.setAttribute('stroke-linecap','round');svg.append(line)}
 buttons.forEach((btn,i)=>{btn.style.background=list.includes(i)?color:'#e5efe4'})}
 function clearTimers(){timers.forEach(clearTimeout);timers=[]}
 function preview(){clearTimers();phase='preview';entered=[];G.status('Watch the highlighted path · Round '+round);showLine([],'#649878');sequence.forEach((id,i)=>timers.push(setTimeout(()=>showLine(sequence.slice(0,i+1),'#d3b36a'),300+i*650)));timers.push(setTimeout(()=>{showLine([],'#649878');phase='input';G.status('Your turn · '+sequence.length+' dots')},450+sequence.length*650))}
 function next(){round++;const unused=Array.from({length:9},(_,i)=>i).filter(i=>!sequence.includes(i));if(unused.length)sequence.push(unused[Math.floor(Math.random()*unused.length)]);G.score('patternlock',round-1);preview()}
 function tap(i){if(phase!=='input')return;const expected=sequence[entered.length];if(i!==expected){mistakes++;entered=[];showLine([],'#649878');G.status('Wrong path · '+mistakes+' errors');return}entered.push(i);showLine(entered,'#4c946b');if(entered.length===sequence.length){G.status('Round '+round+' cleared!');phase='wait';if(round>=6){phase='done';G.score('patternlock',round);G.status('Six patterns mastered!')}else timers.push(setTimeout(next,600))}}
 G.action('Start',()=>{clearTimers();round=0;mistakes=0;sequence=[];next()},true);G.status('Memorise the path, then trace it');G.note('Tap the dots in exactly the same order as the highlighted path. Later rounds add nodes.');return clearTimers});
G.add('brickbuilder','Brick Builder','Place shapes on a 9 × 9 board. Complete lines or 3 × 3 squares to clear space.','Puzzle',()=>{
 const patterns=[
 [[0,0]],[[0,0],[1,0]],[[0,0],[1,0],[2,0]],
 [[0,0],[0,1],[1,0],[1,1]],[[0,0],[0,1],[0,2]],
 [[0,0],[1,0],[0,1]],[[0,0],[1,0],[2,0],[2,1]],
 [[0,0],[1,0],[1,1]],[[0,0],[1,0],[2,0],[0,1],[1,1],[2,1]],
 [[0,0],[1,0],[2,0],[1,1]]
 ];let board=Array(81).fill(0),choices=[null,null,null],selected=0,score=0,ended=false;
 const newChoice=()=>patterns[Math.floor(Math.random()*patterns.length)];
 function regenerate(){choices=[newChoice(),newChoice(),newChoice()];selected=0}
 function fits(shape,x,y){return shape.every(([dx,dy])=>x+dx<9&&y+dy<9&&board[(y+dy)*9+x+dx]===0)}
 function hasMove(){return choices.some(shape=>shape&&Array.from({length:81},(_,i)=>i).some(i=>fits(shape,i%9,Math.floor(i/9))))}
 function put(i){if(ended||!choices[selected])return;const x=i%9,y=Math.floor(i/9),shape=choices[selected];if(!fits(shape,x,y))return;for(const [dx,dy] of shape)board[(y+dy)*9+x+dx]=1;score+=shape.length;choices[selected]=null;const filled=new Set();
 for(let row=0;row<9;row++)if(Array.from({length:9},(_,col)=>board[row*9+col]).every(Boolean))for(let col=0;col<9;col++)filled.add(row*9+col);
 for(let col=0;col<9;col++)if(Array.from({length:9},(_,row)=>board[row*9+col]).every(Boolean))for(let row=0;row<9;row++)filled.add(row*9+col);
 for(let y=0;y<9;y+=3)for(let x=0;x<9;x+=3)if(Array.from({length:9},(_,n)=>board[(y+Math.floor(n/3))*9+x+n%3]).every(Boolean))for(let dy=0;dy<3;dy++)for(let dx=0;dx<3;dx++)filled.add((y+dy)*9+x+dx);
 filled.forEach(i=>board[i]=0);score+=filled.size*2;G.score('brickbuilder',score);
 if(choices.every(x=>!x))regenerate();else if(!choices[selected])selected=choices.findIndex(x=>x);
 ended=!hasMove();draw()}
 function shapeIcon(shape){const squares=shape.map(([x,y])=>'<rect x="'+(x*14+2)+'" y="'+(y*14+2)+'" width="12" height="12" rx="2" fill="#518c68"/>').join('');return'<svg aria-hidden="true" viewBox="0 0 52 52" width="48" height="48">'+squares+'</svg>'}
 function draw(){G.status(ended?'No more legal shapes · '+score+' points':'Score '+score+' · Best '+G.best('brickbuilder'));G.grid(9,9,board.map(v=>v?'■':''),put,(b,i)=>{b.dataset.active=String(board[i]===1);b.style.borderRadius='1px';b.style.border='1px solid #d3ded1';b.setAttribute('aria-label','Row '+(Math.floor(i/9)+1)+' column '+(i%9+1)+(board[i]?' occupied':' empty'))});
 G.actions();choices.forEach((shape,i)=>{if(!shape)return;const b=G.action('Shape '+(i+1),()=>{selected=i;draw()},i===selected);b.innerHTML=shapeIcon(shape);b.setAttribute('aria-label','Select shape '+(i+1));b.setAttribute('aria-pressed',String(selected===i))})}
 regenerate();draw();G.note('Choose a piece below, then tap its top-left destination on the board. Full rows, columns and 3×3 blocks disappear.');});
})();