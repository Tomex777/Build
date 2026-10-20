import assert from 'node:assert/strict';
import {createLights,toggleLights,createSlide,slide,validSlide,solvedSlide,matchSimon} from './rules.mjs';
for(const size of [3,4,5])for(let seed=1;seed<=100;seed++) {
  const {board,solution}=createLights(size,seed);
  assert.ok(board.some(Boolean));
  let work=board;
  for(const at of solution)work=toggleLights(work,size,at);
  assert.ok(work.every(x=>!x),'Generated lights board must be solvable');
  assert.deepEqual(toggleLights(toggleLights(board,size,0),size,0),board);
}
for(const size of [3,4])for(let seed=1;seed<=100;seed++) {
  const board=createSlide(size,seed);
  assert.ok(validSlide(board,size));
  assert.equal(solvedSlide(board),false);
  const blank=board.indexOf(0);
  for(let at=0;at<board.length;at++) {
    const distance=Math.abs(Math.floor(blank/size)-Math.floor(at/size))+
      Math.abs(blank%size-at%size);
    assert.equal(Boolean(slide(board,size,at)),distance===1);
  }
}
assert.equal(matchSimon([0,1,2],0,0),'next');
assert.equal(matchSimon([0,1,2],2,2),'complete');
assert.equal(matchSimon([0,1,2],1,3),'wrong');
assert.equal(matchSimon([0,1,2],3,3),'invalid');
console.log('PASS: 300 solvable Lights Out, 200 legal Sliding Puzzles, Simon sequence rules');
