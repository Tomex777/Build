/* Chess against the computer. Uses third-party BSD-2-Clause chess.js for ALL rules.
 * Our rendering and opponent evaluation are original; see CHESS-LICENSE.txt.
 */
(()=>{'use strict';
const G=window.G;
G.add('chess','Chess','Play White against a rules-complete computer opponent. Castling, check, en passant, promotion and draws are supported.','Board',()=>{
 if(typeof Chess!=='function'){G.status('Chess rules engine unavailable');return}
 const game=new Chess();let selected=null,waiting=false,promotion='q',timer=null,last=null;
 const glyphs={
 p:'<path d="M26 49v-7c0-6 8-7 8-15a8 8 0 1 0-16 0c0 8 8 9 8 15v7M15 53h22l-2-8H17Z" />',
 r:'<path d="M13 52h34l-3-8H16ZM19 44V20h22v24M16 13h8v6h-8Zm12 0h8v6h-8Zm12 0h8v6h-8Z"/>',
 n:'<path d="M14 52h35l-6-10 1-16-9-10-9-3-11 12 2 9 7-4-3 14ZM22 25l2 2"/>',
 b:'<path d="M19 52h26l-3-8H22Zm13-34-6-7-6 7c-10 11-1 22 6 26 7-4 16-15 6-26ZM26 14v24"/>',
 q:'<path d="M10 52h44l-3-9H13ZM14 21l5 21h29l5-21-12 12-9-17-9 17ZM14 16a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM32 10a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM52 16a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z"/>',
 k:'<path d="M17 53h34l-4-9H21ZM34 10v26m-9-19h18M23 44c-6-8-10-18-3-23l14 13 14-13c7 5 3 15-3 23Z"/>'
 };
 const look=p=>{if(!p)return'';const color=p.color==='w'?'#f9f4e6':'#214533',stroke=p.color==='w'?'#52644e':'#d0d9b7';return '<svg viewBox="0 0 64 64" width="90%" height="90%" aria-hidden="true"><g fill="'+color+'" stroke="'+stroke+'" stroke-width="2.7" stroke-linejoin="round" stroke-linecap="round">'+glyphs[p.type]+'</g></svg>'};
 function term(){if(game.in_checkmate())return (game.turn()==='w'?'Computer wins by checkmate':'You win by checkmate');if(game.in_stalemate())return'Stalemate — draw';if(game.in_threefold_repetition())return'Threefold repetition — draw';if(game.insufficient_material())return'Insufficient material — draw';if(game.in_draw())return'Draw';return null}
 function evalBoard(){const w={p:100,n:320,b:340,r:510,q:940,k:0},a=game.board();let score=0;for(let y=0;y<8;y++)for(let x=0;x<8;x++){const p=a[y][x];if(p)score+=(p.color==='b'?1:-1)*(w[p.type]+((p.type!=='k'&&p.type!=='p')?(3.5-Math.abs(x-3.5)+3.5-Math.abs(y-3.5))*3:0))}return score}
 function computer(){if(game.game_over())return;const options=game.moves({verbose:true});let chosen=null,best=-Infinity;
 for(const move of options){game.move(move);let value;if(game.in_checkmate())value=100000;else{const reply=game.moves({verbose:true});if(!reply.length)value=evalBoard();else{let worst=Infinity;for(const rm of reply){game.move(rm);const v=game.in_checkmate()?-100000:evalBoard();worst=Math.min(worst,v);game.undo()}value=worst}}game.undo();value+=(move.captured?10:0)+(Math.random()*4);if(value>best){best=value;chosen=move}}
 if(chosen){game.move(chosen);last=[chosen.from,chosen.to]}waiting=false;draw()}
 function play(to){const possible=selected?game.moves({square:selected,verbose:true}).find(m=>m.to===to):null;
 if(!selected||!possible)return false;const m=game.move({from:selected,to,promotion});if(!m)return false;last=[m.from,m.to];selected=null;waiting=true;draw();if(!game.game_over())timer=setTimeout(computer,160);else waiting=false;return true}
 function draw(){const board=game.board(),valid=selected?game.moves({square:selected,verbose:true}):[],status=term();
 G.status(status||(waiting?'Computer thinking…':(game.in_check()?'Check! ':'')+'Your turn · White'));if(status&&status.startsWith('You win'))G.score('chess',1);
 G.grid(8,8,board.flat().map(p=>(p&&p.type)||''),index=>{
 if(waiting||game.game_over())return;const y=Math.floor(index/8),x=index%8,to='abcdefgh'[x]+(8-y);
 if(selected&&play(to))return;const piece=board[y][x];if((piece&&piece.color)==='w'){selected=to;draw()}else{selected=null;draw()}
 },(b,i)=>{
 const x=i%8,y=Math.floor(i/8),p=board[y][x],square='abcdefgh'[x]+(8-y);
 b.innerHTML=look(p);b.setAttribute('aria-label',square+(p?' '+(p.color==='w'?'White ':'Black ')+p.type:' empty'));
 b.style.background=(x+y)%2===1?'#96a58c':'#edeade';b.style.borderRadius='1px';b.style.border='0';
 if(valid.some(m=>m.to===square)){b.style.boxShadow='inset 0 0 0 3px #d0a251';if(!p)b.innerHTML='<svg width="22" height="22" viewBox="0 0 22 22"><circle cx="11" cy="11" r="6" fill="#3d6d4f" opacity=".6"/></svg>'}
 if(square===selected)b.style.outline='3px solid #bd8c4a';else if((last&&last.includes(square)))b.style.outline='2px solid #d3b878';
 });
 G.actions();if(!waiting&&!game.game_over()){G.action('Undo pair',()=>{if(game.history().length<2)return;game.undo();game.undo();last=null;selected=null;draw()});G.action('Promotion: '+promotion.toUpperCase(),()=>{promotion=({q:'r',r:'b',b:'n',n:'q'})[promotion];draw()})}G.note('Choose a white piece, then its destination. Promotions default to Queen; change the piece before moving a pawn to the final rank.')}
 draw();return()=>{if(timer)clearTimeout(timer)};
});
})();