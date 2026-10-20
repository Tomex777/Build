/* Search off the UI thread; the page terminates stale jobs after undo/reset. */
'use strict';
importScripts('/strategy/chess-v0.10.3.js','/strategy/ai.js');
self.onmessage=({data})=>{
  const job=data?.job;
  try{
    if(!Array.isArray(data?.moves)||data.moves.length>350)throw Error('Invalid move history');
    const game=new Chess();
    for(const u of data.moves){
      if(typeof u!=='string'||!/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(u))throw Error('Invalid move');
      if(!game.move({from:u.slice(0,2),to:u.slice(2,4),promotion:u[4]||'q'}))throw Error('Illegal saved move');
    }
    const move=NightChessAI.chooseMove(game,data.difficulty);
    self.postMessage({job,move});
  }catch(error){self.postMessage({job,error:String(error?.message||error)})}
};