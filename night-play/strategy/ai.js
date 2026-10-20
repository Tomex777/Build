/* NIGHT Play chess opponent. Offline, time-limited alpha-beta search.
 * Main-thread rendering never invokes this directly: ai-worker.js owns the search.
 * The algorithm is intentionally shared with Node rule tests. */
(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.NightChessAI=api;
})(typeof self!=='undefined'?self:globalThis,function(){
  'use strict';
  const VALUE={p:100,n:320,b:335,r:510,q:920,k:0};
  const PROFILES={
    easy:{depth:2,time:140,nodes:3500},
    normal:{depth:3,time:500,nodes:14000},
    hard:{depth:4,time:1100,nodes:40000}
  };
  const MATE=1000000,STOP=Symbol('search timeout');
  function value(game){
    const board=game.board();
    let score=0;
    for(let r=0;r<8;r++)for(let c=0;c<8;c++){
      const p=board[r][c];if(!p)continue;
      const central=7-Math.abs(3.5-c)-Math.abs(3.5-r);
      const pawnAdvance=p.type==='p'?(p.color==='b'?r:7-r):0;
      const centerWeight=p.type==='p'?2:p.type==='k'?-1:5;
      const bonus=central*centerWeight+pawnAdvance*3;
      score+=(p.color==='b'?1:-1)*(VALUE[p.type]+bonus);
    }
    return score;
  }
  function rank(m){
    const captured=m.captured?(10*VALUE[m.captured]-VALUE[m.piece]):0;
    return captured+(m.promotion?VALUE[m.promotion]:0)+(m.flags?.includes('k')?25:0);
  }
  function order(moves){return moves.sort((a,b)=>rank(b)-rank(a))}
  function checkBudget(ctx){
    ctx.nodes++;
    if(ctx.nodes>=ctx.maxNodes||((ctx.nodes&127)===0&&Date.now()>=ctx.deadline))throw STOP;
  }
  function emptyPosition(game,ply){
    return game.in_check()?(game.turn()==='b'?-MATE+ply:MATE-ply):0;
  }
  // A bounded one-ply capture extension makes static search less vulnerable to
  // obvious hanging-piece traps without making mid-game turns painfully slow.
  function quiet(game,alpha,beta,ply,remaining,ctx){
    checkBudget(ctx);
    const moves=game.moves({verbose:true});
    if(!moves.length)return emptyPosition(game,ply);
    if(game.insufficient_material())return 0;
    const maximize=game.turn()==='b';
    let best=value(game);
    if(maximize){if(best>=beta)return best;alpha=Math.max(alpha,best)}
    else{if(best<=alpha)return best;beta=Math.min(beta,best)}
    if(remaining<=0)return best;
    for(const move of order(moves.filter(m=>!!m.captured||!!m.promotion))){
      game.move(move);
      let score;
      try{score=quiet(game,alpha,beta,ply+1,remaining-1,ctx)}
      finally{game.undo()}
      best=maximize?Math.max(best,score):Math.min(best,score);
      if(maximize)alpha=Math.max(alpha,best);else beta=Math.min(beta,best);
      if(beta<=alpha)break;
    }
    return best;
  }
  function search(game,depth,alpha,beta,ply,ctx){
    checkBudget(ctx);
    if(depth===0)return quiet(game,alpha,beta,ply,1,ctx);
    const moves=order(game.moves({verbose:true}));
    if(!moves.length)return emptyPosition(game,ply);
    if(game.insufficient_material())return 0;
    const maximize=game.turn()==='b';
    let best=maximize?-Infinity:Infinity;
    for(const move of moves){
      game.move(move);
      let score;
      try{score=search(game,depth-1,alpha,beta,ply+1,ctx)}
      finally{game.undo()}
      best=maximize?Math.max(best,score):Math.min(best,score);
      if(maximize)alpha=Math.max(alpha,best);else beta=Math.min(beta,best);
      if(beta<=alpha)break;
    }
    return best;
  }
  function chooseMove(game,level='normal',options={}){
    const profile=PROFILES[level]||PROFILES.normal;
    const moves=order(game.moves({verbose:true}));
    if(!moves.length)return null;
    const original=game.fen();
    // Black is the computer seat. Do not silently play the human's position.
    if(game.turn()!=='b'||game.game_over())return null;
    const ctx={nodes:0,maxNodes:options.maxNodes??profile.nodes,deadline:Date.now()+(options.time??profile.time)};
    let chosen=moves[0],depthDone=0;
    const maxDepth=options.depth??profile.depth;
    // Iterative deepening: only adopt a FULLY completed search iteration.
    for(let depth=1;depth<=maxDepth;depth++){
      let best=-Infinity,top=chosen,alpha=-Infinity,finished=true;
      // The prior best move is searched first for earlier alpha-beta pruning.
      const candidates=[chosen,...moves.filter(m=>m.from!==chosen.from||m.to!==chosen.to||m.promotion!==chosen.promotion)];
      try{
        for(const move of candidates){
          game.move(move);
          let score;
          try{score=search(game,depth-1,alpha,Infinity,1,ctx)}
          finally{game.undo()}
          if(score>best){best=score;top=move}
          alpha=Math.max(alpha,best);
        }
      }catch(err){
        if(err!==STOP)throw err;
        finished=false;
      }
      if(!finished)break;
      chosen=top;depthDone=depth;
      if(best>=MATE-10)break;
    }
    if(game.fen()!==original)throw Error('Chess AI corrupted board state');
    return {from:chosen.from,to:chosen.to,promotion:chosen.promotion||'q',depth:depthDone,nodes:ctx.nodes};
  }
  return {chooseMove,PROFILES};
});