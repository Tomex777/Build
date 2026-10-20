import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url);
const c=require('./core.js');
for(const level of ['easy','medium','hard']){
 for(const seed of [1,138,986234,20261018]){
  const {puzzle,solution,removed}=c.createSudoku(level,seed);
  assert.ok(c.validSudoku(solution),level+' generated a valid solution');
  assert.equal(c.countSolutions(puzzle,2),1,level+' is unambiguously solvable');
  assert.ok(removed>=(level==='hard'?45:level==='medium'?40:33),level+' has sufficient challenge');
  assert.ok(puzzle.every((x,i)=>!x||x===solution[i]));
 }
}

// Memory collection invariants: thirty real pairs per pack and a fixed max width
// while the board expands downward to ten rows.
require('./memory-art.js');
require('./memory.js');
const art=globalThis.NIGHTMemoryArt;
const memory=globalThis.NIGHTMemory;
assert.equal(art.themes.length,5);
for(const theme of art.themes){
 if(theme.type==='photo'){
  assert.equal(art.photos[theme.id].length,30,theme.id+' needs 30 distinct art sources');
  assert.equal(new Set(art.photos[theme.id].map(e=>e[1])).size,30,'no repeated photograph in '+theme.id);
  for(let i=0;i<30;i++){
   assert.match(art.photoUrl(theme.id,i),/^https:\/\/images\.unsplash\.com\/photo-/);
   assert.ok(art.face(theme.id,i,true).includes('<img'));
  }
 }else{
  for(let i=0;i<30;i++)assert.match(art.face(theme.id,i,true),/<svg/);
 }
}
assert.equal(memory.SIZES.length,10);
assert.ok(memory.SIZES.some(([cols,rows])=>cols===6&&rows===5));
assert.ok(memory.SIZES.some(([cols,rows])=>cols===6&&rows===10));
for(const [cols,rows] of memory.SIZES){
 assert.ok(cols<=6&&rows>=4&&(cols*rows)%2===0);
 const deck=c.memoryDeck(cols*rows/2,20261018);
 assert.equal(deck.length,cols*rows);
 assert.equal(new Set(deck).size,cols*rows/2);
 for(const key of new Set(deck))assert.equal(deck.filter(v=>v===key).length,2);
}

assert.equal(c.countSolutions(Array(81).fill(1),2),0,'reject duplicate clues');
assert.equal(c.countSolutions(Array(81).fill(-1),2),0,'reject invalid grid');
for(const pairs of [8,10,12]){
 const deck=c.memoryDeck(pairs,20261018);
 assert.equal(deck.length,pairs*2);
 assert.equal(new Set(deck).size,pairs);
 for(const id of new Set(deck))assert.equal(deck.filter(x=>x===id).length,2);
 assert.deepEqual(deck,c.memoryDeck(pairs,20261018),'shuffle must be reproducible');
}
const board=c.createConnectBoard();
assert.equal(c.botColumn(board),3,'CPU favours strategic center opening');
assert.equal(c.drop(board,3,1),5);
assert.equal(c.drop(board,3,2),4);
for(let n=0;n<6;n++)c.drop(board,0,1);
assert.equal(c.drop(board,0,2),-1,'full columns cannot accept moves');
const horizontal=c.createConnectBoard();
for(let col=1;col<=4;col++)c.drop(horizontal,col,1);
assert.equal(c.winner(horizontal),1,'horizontal connect four');
const vertical=c.createConnectBoard();
for(let n=0;n<4;n++)c.drop(vertical,2,2);
assert.equal(c.winner(vertical),2,'vertical connect four');
const diagonal=c.createConnectBoard();
for(let col=0;col<4;col++){
 for(let n=0;n<3-col;n++)c.drop(diagonal,col,1);
 c.drop(diagonal,col,2);
}
assert.equal(c.winner(diagonal),2,'diagonal four');
const threat=c.createConnectBoard();
for(let col=0;col<3;col++)c.drop(threat,col,1);
assert.equal(c.botColumn(threat),3,'CPU must block immediate human win');
const finisher=c.createConnectBoard();
for(let col=0;col<3;col++)c.drop(finisher,col,2);
assert.equal(c.botColumn(finisher),3,'CPU must finish an immediate win');
const site=readFileSync(new URL('../index.html',import.meta.url),'utf8');
for(const game of ['sudoku','memory','connect']){
 assert.ok(site.includes('href="/puzzles/?game='+game+'"'),game+' is linked directly from library');
}
assert.ok(readFileSync(new URL('./index.html',import.meta.url),'utf8').includes('src="/puzzles/core.js"'));
console.log('NIGHT Puzzle Room: unique Sudoku generation, Memory Match, Connect Four and library routes passed.');
