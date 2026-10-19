/* Original full-rule checkers and capture-based Go. */
(()=>{'use strict';const G=window.G;
G.add('checkers','Checkers','American checkers with forced captures, chain jumps, promotion and a computer rival.','Board',()=>{
 let cells=Array(64).fill(0),selected=-1,locked=-1,ended=false,winning=0;
 for(let r=0;r<3;r++)for(let c=0;c<8;c++)if((r+c)%2===1)cells[r*8+c]=2;
 for(let r=5;r<8;r++)for(let c=0;c<8;c++)if((r+c)%2===1)cells[r*8+c]=1;
 const side=v=>v%2===1?1:v?2:0;
 function options(who,at,forced=false){const piece=cells[at];if(side(piece)!==who)return[];const row=Math.floor(at/8),col=at%8,k=piece>2,dr=k?[-1,1]:who===1?[-1]:[1],moves=[];
 for(const dy of dr)for(const dx of [-1,1]){const r=row+dy,c=col+dx;if(r<0||r>7||c<0||c>7)continue;const index=r*8+c;if(!cells[index]&&!forced)moves.push({from:at,to:index,cap:-1});
 else if(side(cells[index])===3-who){const rr=r+dy,cc=c+dx;if(rr>=0&&rr<8&&cc>=0&&cc<8&&!cells[rr*8+cc])moves.push({from:at,to:rr*8+cc,cap:index})}}return moves}
 function available(who,forcedAt=-1){const all=[];for(let i=0;i<64;i++)if((forcedAt<0||i===forcedAt)&&side(cells[i])===who)all.push(...options(who,i,forcedAt>=0));const captures=all.filter(m=>m.cap>=0);return captures.length?captures:forcedAt<0?all:[]}
 function move(m,who){const piece=cells[m.from];cells[m.from]=0;cells[m.to]=piece;if(m.cap>=0)cells[m.cap]=0;let promoted=false;
 if(who===1&&Math.floor(m.to/8)===0&&piece===1){cells[m.to]=3;promoted=true}
 if(who===2&&Math.floor(m.to/8)===7&&piece===2){cells[m.to]=4;promoted=true}
 return m.cap>=0&&!promoted?available(who,m.to):[]}
 function finish(){const a=available(1),b=available(2);if(!a.length){ended=true;winning=2}else if(!b.length){ended=true;winning=1}if(ended&&winning===1)G.score('checkers',1)}
 function cpu(){if(ended)return;let opts=available(2),chain=0;while(opts.length&&chain++<20){const sorted=opts.map(m=>({m,v:(m.cap>=0?30:0)+(Math.floor(m.to/8)===7&&cells[m.from]===2?20:0)+(Math.random()*5)})).sort((x,y)=>y.v-x.v);opts=move(sorted[0].m,2)}finish()}
 function draw(){const legal=available(1,locked),valid=legal.filter(m=>m.from===selected);G.status(ended?(winning===1?'You win!':'Computer wins'):locked>=0?'Continue capturing with the same piece':'Your turn · Dark pieces');G.grid(8,8,cells.map(v=>v===1?'●':v===2?'●':v===3?'♛':v===4?'♛':''),i=>{
 if(ended)return;const found=valid.find(m=>m.to===i);if(found){const extra=move(found,1);selected=-1;locked=extra.length?found.to:-1;if(locked>=0){selected=locked;draw();return}finish();if(!ended)cpu();draw();return}
 if(side(cells[i])===1&&(locked<0||locked===i)&&legal.some(m=>m.from===i)){selected=i;draw()}},(b,i)=>{b.style.background=(Math.floor(i/8)+i%8)%2?'#d0dfcf':'#fbfbf6';b.style.borderRadius='2px';b.dataset.active=String(side(cells[i])===1);b.dataset.secondary=String(side(cells[i])===2);if(valid.some(m=>m.to===i)&&!cells[i]){b.textContent='•';b.style.boxShadow='inset 0 0 0 3px #548a63'}if(selected===i)b.style.outline='3px solid #cfad66'})}
 draw();G.note('Captures are mandatory. If another capture is possible, you must continue the jump. Kings move both directions.');});

})();