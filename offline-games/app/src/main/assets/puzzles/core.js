/* Pure rules shared by the NIGHT puzzle room and Node regression tests.
 * No network, dependencies, or DOM. */
(function(root,factory){
  const core=factory();
  if(typeof module!=="undefined"&&module.exports)module.exports=core;
  root.NIGHTPuzzleCore=core;
})(typeof globalThis!=="undefined"?globalThis:this,function(){
"use strict";
function rng(seed){
 let x=(Number(seed)>>>0)||0x9e3779b9;
 return function(){x^=x<<13;x^=x>>>17;x^=x<<5;return (x>>>0)/4294967296;};
}
function shuffle(items,rand){const arr=items.slice();for(let i=arr.length-1;i>0;i--){const j=Math.floor(rand()*(i+1));[arr[i],arr[j]]=[arr[j],arr[i]];}return arr;}
function boxOf(r,c){return Math.floor(r/3)*3+Math.floor(c/3);}
function validSudoku(solution){
 if(!Array.isArray(solution)||solution.length!==81)return false;
 for(let i=0;i<9;i++){
  const row=new Set(),col=new Set(),box=new Set();
  for(let j=0;j<9;j++){
   row.add(solution[i*9+j]);col.add(solution[j*9+i]);
   const br=Math.floor(i/3)*3+Math.floor(j/3),bc=(i%3)*3+(j%3);
   box.add(solution[br*9+bc]);
  }
  if(row.size!==9||col.size!==9||box.size!==9)return false;
 }
 return solution.every(n=>Number.isInteger(n)&&n>=1&&n<=9);
}
function countSolutions(values,limit=2){
 if(!Array.isArray(values)||values.length!==81||values.some(x=>!Number.isInteger(x)||x<0||x>9))return 0;
 const rows=Array(9).fill(0),cols=Array(9).fill(0),boxes=Array(9).fill(0),board=values.slice();
 for(let i=0;i<81;i++)if(board[i]){
  const r=(i/9)|0,c=i%9,b=boxOf(r,c),bit=1<<board[i];
  if((rows[r]|cols[c]|boxes[b])&bit)return 0;
  rows[r]|=bit;cols[c]|=bit;boxes[b]|=bit;
 }
 let found=0;
 function search(){
  if(found>=limit)return;
  let target=-1,mask=0,n=10;
  for(let i=0;i<81;i++)if(board[i]===0){
   const r=(i/9)|0,c=i%9,b=boxOf(r,c),available=0x3fe&~(rows[r]|cols[c]|boxes[b]);
   let count=0;for(let m=available;m;m&=m-1)count++;
   if(!count)return;
   if(count<n){target=i;mask=available;n=count;if(n===1)break;}
  }
  if(target<0){found++;return;}
  const r=(target/9)|0,c=target%9,b=boxOf(r,c);
  for(let v=1;v<=9;v++){const bit=1<<v;if(!(mask&bit))continue;
   board[target]=v;rows[r]|=bit;cols[c]|=bit;boxes[b]|=bit;
   search();rows[r]^=bit;cols[c]^=bit;boxes[b]^=bit;board[target]=0;
   if(found>=limit)return;
  }
 }
 search();return found;
}
function createSudoku(level="easy",seed=Date.now()){
 const random=rng(seed),digits=shuffle([1,2,3,4,5,6,7,8,9],random);
 function axis(){return shuffle([0,1,2],random).flatMap(group=>shuffle([0,1,2],random).map(pos=>group*3+pos));}
 const row=axis(),col=axis(),solution=Array.from({length:81},(_,i)=>digits[(row[(i/9)|0]*3+Math.floor(row[(i/9)|0]/3)+col[i%9])%9]);
 const puzzle=solution.slice();
 const desired={easy:38,medium:47,hard:53}[level]||38;
 let removed=0;
 for(const i of shuffle(Array.from({length:81},(_,i)=>i),random)){
  const n=puzzle[i];puzzle[i]=0;
  if(countSolutions(puzzle,2)!==1)puzzle[i]=n;else removed++;
  if(removed>=desired)break;
 }
 return {solution,puzzle,removed};
}
function memoryDeck(pairs=8,seed=Date.now()){
 const size=Math.max(2,Math.min(12,Math.round(pairs)||8)),ids=Array.from({length:size},(_,i)=>i);
 return shuffle(ids.concat(ids),rng(seed));
}
function createConnectBoard(){return Array.from({length:6},()=>Array(7).fill(0));}
function validConnectBoard(board){return Array.isArray(board)&&board.length===6&&board.every(row=>Array.isArray(row)&&row.length===7&&row.every(x=>x===0||x===1||x===2));}
function drop(board,col,player){
 if(!validConnectBoard(board)||!Number.isInteger(col)||col<0||col>6||![1,2].includes(player))return -1;
 for(let row=5;row>=0;row--)if(board[row][col]===0){board[row][col]=player;return row;}
 return -1;
}
const windows=[];
for(let r=0;r<6;r++)for(let c=0;c<7;c++){
 for(const [dr,dc] of [[0,1],[1,0],[1,1],[1,-1]]){
  if(r+dr*3<0||r+dr*3>=6||c+dc*3<0||c+dc*3>=7)continue;
  windows.push(Array.from({length:4},(_,n)=>[r+dr*n,c+dc*n]));
 }
}
function winner(board){
 if(!validConnectBoard(board))return 0;
 for(const line of windows){
  const [r,c]=line[0],p=board[r][c];
  if(p&&line.every(([rr,cc])=>board[rr][cc]===p))return p;
 }
 return board[0].every(n=>n!==0)?3:0;
}
function openColumns(board){return [3,2,4,1,5,0,6].filter(c=>board[0][c]===0);}
function evaluate(board){
 let result=0;
 for(const cells of windows){
  let bot=0,human=0;
  for(const [r,c] of cells){if(board[r][c]===2)bot++;if(board[r][c]===1)human++;}
  if(bot&&human)continue;
  if(bot)result+=[0,1,8,45,5000][bot];
  if(human)result-=[0,1,10,53,5000][human];
 }
 for(let r=0;r<6;r++){if(board[r][3]===2)result+=3;if(board[r][3]===1)result-=3;}
 return result;
}
function botColumn(board,depth=3){
 if(!validConnectBoard(board)||winner(board))return -1;
 const legal=openColumns(board);if(!legal.length)return -1;
 for(const c of legal){const b=board.map(r=>r.slice());drop(b,c,2);if(winner(b)===2)return c;}
 for(const c of legal){const b=board.map(r=>r.slice());drop(b,c,1);if(winner(b)===1)return c;}
 const maxDepth=Math.max(1,Math.min(4,Math.trunc(depth)||3));
 function minimax(b,remaining,player,alpha,beta){
  const result=winner(b);
  if(result===2)return 100000+remaining;
  if(result===1)return -100000-remaining;
  if(result===3)return 0;
  if(remaining===0)return evaluate(b);
  let best=player===2?-Infinity:Infinity;
  for(const c of openColumns(b)){
   const child=b.map(row=>row.slice());drop(child,c,player);
   const value=minimax(child,remaining-1,player===2?1:2,alpha,beta);
   if(player===2){best=Math.max(best,value);alpha=Math.max(alpha,best);}
   else{best=Math.min(best,value);beta=Math.min(beta,best);}
   if(beta<=alpha)break;
  }
  return best;
 }
 let chosen=legal[0],best=-Infinity;
 for(const col of legal){const b=board.map(row=>row.slice());drop(b,col,2);
  const v=minimax(b,maxDepth-1,1,-Infinity,Infinity);
  if(v>best){best=v;chosen=col;}
 }
 return chosen;
}
return {rng,shuffle,validSudoku,countSolutions,createSudoku,memoryDeck,createConnectBoard,validConnectBoard,drop,winner,botColumn};
});
