import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const {Chess}=require('./chess-v0.10.3.js');
const piece=(game,at)=>game.get(at);

{
 const game=new Chess();
 assert.equal(game.moves().length,20,'Chess opening has exactly 20 legal moves');
 assert.equal(game.move({from:'e2',to:'e5'}),null,'three-square pawn move is illegal');
 assert.equal(game.turn(),'w','illegal move cannot consume turn');
 const sequence=[['e2','e4'],['e7','e5'],['f1','c4'],['b8','c6'],
 ['d1','h5'],['g8','f6'],['h5','f7']];
 for(const [from,to] of sequence)assert.ok(game.move({from,to}),'legal scholar mate '+from+to);
 assert.equal(game.in_checkmate(),true,'Scholar mate ends game');
 assert.equal(game.game_over(),true);
 assert.equal(game.move({from:'a7',to:'a6'}),null,'no turns accepted after mate');
 assert.ok(game.undo(),'undo a mating move');
 assert.equal(game.in_checkmate(),false,'undo restores live match');
}
{
 const game=new Chess();
 for(const [from,to] of [['e2','e4'],['a7','a6'],['e4','e5'],['d7','d5']]){
  assert.ok(game.move({from,to}));
 }
 const move=game.move({from:'e5',to:'d6'});
 assert.ok(move,'en passant is legal');
 assert.equal(move.flags.includes('e'),true,'engine marks capture as en passant');
 assert.equal(piece(game,'d5'),null,'captured pawn is removed from d5');
 assert.equal(piece(game,'d6').color,'w','capturing pawn reaches d6');
}
{
 const game=new Chess();
 for(const [from,to] of [['e2','e4'],['e7','e5'],['g1','f3'],['b8','c6'],['f1','c4'],['f8','c5']]){
  assert.ok(game.move({from,to}));
 }
 const castle=game.move({from:'e1',to:'g1'});
 assert.ok(castle,'white castling is legal');
 assert.equal(piece(game,'g1').type,'k','castled king reaches g1');
 assert.equal(piece(game,'f1').type,'r','rook repositions to f1');
}
{
 const game=new Chess('8/4P3/k7/8/8/8/8/4K3 w - - 0 1');
 const promotion=game.move({from:'e7',to:'e8',promotion:'n'});
 assert.ok(promotion,'player can select knight underpromotion');
 assert.equal(piece(game,'e8').type,'n');
}
{
 const game=new Chess('7k/5Q2/6K1/8/8/8/8/8 b - - 0 1');
 assert.equal(game.in_stalemate(),true,'stalemate is a draw');
 assert.equal(game.game_over(),true);
}
{
 const game=new Chess('8/8/8/8/8/5k2/8/4K3 w - - 0 1');
 assert.equal(game.insufficient_material(),true,'lone kings are a draw');
}
console.log('PASS NIGHT Play Chess rules: opening, illegal moves, checkmate, undo, en passant, castling, underpromotion and drawn endings');
