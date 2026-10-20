// Pure rules shared by the offline browser games and their Node test suite.
export function seeded(seed) {
  let n=(seed>>>0)||1;
  return ()=>{n^=n<<13;n^=n>>>17;n^=n<<5;return (n>>>0)/4294967296;};
}
export function toggleLights(board,size,at) {
  if(!Array.isArray(board)||board.length!==size*size||at<0||at>=board.length||!Number.isInteger(at))
    throw new RangeError('Invalid Lights Out board or position');
  const next=board.slice(),r=Math.floor(at/size),c=at%size;
  for(const [dr,dc] of [[0,0],[-1,0],[1,0],[0,-1],[0,1]]) {
    const row=r+dr,col=c+dc;
    if(row>=0&&row<size&&col>=0&&col<size)next[row*size+col]=!next[row*size+col];
  }
  return next;
}
export function createLights(size,seed) {
  if(!Number.isInteger(size)||size<3||size>5)throw new RangeError('Invalid board size');
  const random=seeded(seed),count=size*size;
  for(let attempt=0;attempt<15;attempt++) {
    let board=Array(count).fill(false);
    const parity=Array(count).fill(false);
    for(let j=0;j<count+8;j++) {
      const at=Math.floor(random()*count);
      board=toggleLights(board,size,at);parity[at]=!parity[at];
    }
    if(board.filter(Boolean).length>=3)
      return {board,solution:parity.map((x,i)=>x?i:-1).filter(x=>x>=0)};
  }
  return {board:toggleLights(Array(count).fill(false),size,0),solution:[0]};
}
export function slide(board,size,at) {
  if(!Array.isArray(board)||board.length!==size*size)throw new RangeError('Invalid slide board');
  const blank=board.indexOf(0);
  if(!Number.isInteger(at)||at<0||at>=board.length||blank<0)return null;
  const dist=Math.abs(Math.floor(blank/size)-Math.floor(at/size))+Math.abs(blank%size-at%size);
  if(dist!==1)return null;
  const next=board.slice();[next[at],next[blank]]=[next[blank],next[at]];
  return next;
}
export function solvedSlide(board) {
  return board.every((value,i)=>value===(i+1)%board.length);
}
export function validSlide(board,size) {
  return Array.isArray(board)&&board.length===size*size&&
    board.every(value=>Number.isInteger(value)&&value>=0&&value<board.length)&&
    new Set(board).size===board.length;
}
export function createSlide(size,seed) {
  if(!Number.isInteger(size)||size<3||size>4)throw new RangeError('Invalid board size');
  const rand=seeded(seed),length=size*size;
  let board=Array.from({length},(_,i)=>(i+1)%length),previous=-1;
  for(let i=0;i<length*26;i++) {
    const blank=board.indexOf(0),options=[];
    for(let at=0;at<length;at++)if(at!==previous&&slide(board,size,at))options.push(at);
    const next=options[Math.floor(rand()*options.length)];
    previous=blank;board=slide(board,size,next);
  }
  return solvedSlide(board)?slide(board,size,length-2):board;
}
export function matchSimon(sequence,position,tap) {
  if(!Array.isArray(sequence)||!Number.isInteger(position)||position<0||position>=sequence.length)
    return 'invalid';
  if(sequence[position]!==tap)return 'wrong';
  return position+1===sequence.length?'complete':'next';
}
