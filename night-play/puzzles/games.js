/* NIGHT Play Puzzle Room — DOM-only responsive games, local save/resume. */
(function(){
"use strict";
const core=window.NIGHTPuzzleCore;
if(!core)throw new Error("Puzzle game rules did not load.");
const $=id=>document.getElementById(id);
const board=$("board"),tools=$("tools"),status=$("statusbar"),msg=$("message"),dialog=$("settingsDialog");
const catalog={
 sudoku:{title:"Sudoku",sub:"Fill every row, column, and 3×3 box.",category:"NUMBER PUZZLE",rules:"Each row, column and 3×3 square must contain 1–9 once each. Tap a cell, then enter a number. Notes let you pencil possible numbers. Desktop: arrows, 1–9, Backspace, and N for Notes."},
 memory:{title:"Memory Match",sub:"Find the pairs. Remember what you saw.",category:"MEMORY GAME",rules:"Turn over two cards at a time. Matching shapes stay open; mismatched shapes turn back. Clear all pairs in as few turns as possible."},
 connect:{title:"Connect Four",sub:"Think ahead. Connect four to win.",category:"STRATEGY BOARD",rules:"Tap a column to drop a disc. Four discs of your colour in a horizontal, vertical, or diagonal line wins. Choose the computer or local two-player mode in Settings."}
};
const SAVE="night-play-puzzle-room-v1-";
const allowed=["sudoku","memory","connect"];
let kind="sudoku",s=null,delay=null;
function getSaved(id){try{return JSON.parse(localStorage.getItem(SAVE+id)||"null");}catch{return null;}}
function putSaved(){if(!s)return;try{
 let data=JSON.parse(JSON.stringify(s));
 if(kind==="memory"){data.flipped=[];data.locked=false;}
 localStorage.setItem(SAVE+kind,JSON.stringify(data));
}catch{}}
function updateMessage(t){msg.textContent=t||"";}
function chip(label){const x=document.createElement("span");x.textContent=label;status.append(x);}
function time(t){const n=Math.max(0,Math.floor(t)||0);return String(Math.floor(n/60)).padStart(2,"0")+":"+String(n%60).padStart(2,"0");}
function setTitle(){const m=catalog[kind];$("title").textContent=m.title;$("subtitle").textContent=m.sub;$("eyebrow").textContent=m.category;$("rules").textContent=m.rules;document.title=m.title+" · NIGHT Play";$("newGame").textContent="";$("newGame").insertAdjacentHTML("beforeend",'<svg class="night-ico" aria-hidden="true"><use href="#icon-restart"></use></svg> New game');}
function validSaved(id,data){
 if(!data||data.type!==id||typeof data.elapsed!=="number"||data.elapsed<0||data.elapsed>10000000)return false;
 const vec=(v,n,max=9)=>Array.isArray(v)&&v.length===n&&v.every(x=>Number.isInteger(x)&&x>=0&&x<=max);
 if(id==="sudoku")return ["easy","medium","hard"].includes(data.level)&&vec(data.givens,81)&&vec(data.board,81)&&core.validSudoku(data.solution)&&Array.isArray(data.notes)&&data.notes.length===81&&data.notes.every(x=>typeof x==="string"&&/^[1-9]*$/.test(x))&&data.givens.every((n,i)=>!n||n===data.solution[i])&&data.board.every((n,i)=>!data.givens[i]||n===data.givens[i])&&Number.isInteger(data.hints)&&data.hints>=0&&data.hints<=2;
 if(id==="memory")return ["easy","medium","hard"].includes(data.level)&&Array.isArray(data.deck)&&data.deck.length===({easy:16,medium:20,hard:24}[data.level])&&data.deck.every(v=>Number.isInteger(v)&&v>=0&&v<12)&&Array.isArray(data.matched)&&data.matched.length===data.deck.length&&data.matched.every(v=>typeof v==="boolean")&&Number.isInteger(data.tries)&&data.tries>=0;
 if(id==="connect")return ["cpu","local"].includes(data.mode)&&core.validConnectBoard(data.cells)&&[1,2].includes(data.turn)&&[0,1,2,3].includes(data.ended);
 return false;
}
function newSession(id,setting){
 if(id==="sudoku"){
  const level=["easy","medium","hard"].includes(setting)?setting:"easy";
  const made=core.createSudoku(level,Date.now()+(Math.random()*4294967295>>>0));
  s={type:id,level,givens:made.puzzle,solution:made.solution,board:made.puzzle.slice(),notes:Array(81).fill(""),selected:made.puzzle.indexOf(0),noteMode:false,mistakes:0,hints:2,elapsed:0,done:false};
 }else if(id==="memory"){
  const level=["easy","medium","hard"].includes(setting)?setting:"easy";
  const pairs={easy:8,medium:10,hard:12}[level],seed=(Date.now()^(Math.random()*4294967295))>>>0;
  s={type:id,level,deck:core.memoryDeck(pairs,seed),matched:Array(pairs*2).fill(false),flipped:[],locked:false,tries:0,elapsed:0,done:false};
 }else{
  const mode=setting==="local"?"local":"cpu";
  s={type:id,mode,cells:core.createConnectBoard(),turn:1,ended:0,elapsed:0};
 }
 putSaved();
}
function cancelPending(){if(delay!==null){clearTimeout(delay);delay=null;}if(s&&s.type==="memory"){s.flipped=[];s.locked=false;}}
function switchGame(next){
 if(!catalog[next])next="sudoku";
 cancelPending();putSaved();kind=next;
 const previous=getSaved(next);
 if(validSaved(next,previous)){s=previous;if(next==="memory"){s.flipped=[];s.locked=false;}if(next==="sudoku"&&(!Number.isInteger(s.selected)||s.selected<0||s.selected>80))s.selected=s.board.findIndex(n=>n===0);}
 else newSession(next);
 const url=new URL(location.href);url.searchParams.set("game",kind);history.replaceState({},"",url.pathname+url.search);
 setTitle();renderOptions();render(true);updateMessage("");
 if(dialog.open)dialog.close();
 if(kind==="connect"&&s.mode==="cpu"&&s.turn===2&&!s.ended)queueBot();
}
function startOver(setting){cancelPending();newSession(kind,setting);renderOptions();render(true);updateMessage("A new round is ready.");if(dialog.open)dialog.close();}
function renderOptions(){
 const x=$("gameOptions");x.replaceChildren();
 const label=document.createElement("span");label.className="puzzle-options-label";label.textContent=kind==="connect"?"PLAY MODE":"DIFFICULTY";x.append(label);
 const row=document.createElement("div");row.className="puzzle-options";
 const choices=kind==="connect"?[["cpu","Versus computer"],["local","2 players · same phone"]]:kind==="memory"?[["easy","4 × 4"],["medium","4 × 5"],["hard","6 × 4"]]:[["easy","Easy"],["medium","Medium"],["hard","Hard"]];
 for(const [id,title] of choices){const b=document.createElement("button");b.type="button";b.textContent=title;b.dataset.option=id;b.setAttribute("aria-pressed",String((s.level||s.mode)===id));row.append(b);}
 x.append(row);
}
function render(clearBoard=false){
 if(clearBoard)board.replaceChildren();
 if(kind==="sudoku")renderSudoku();
 if(kind==="memory")renderMemory(clearBoard);
 if(kind==="connect")renderConnect();
}
function sudokuConflict(i){
 const value=s.board[i];return value!==0&&value!==s.solution[i];
}
function renderSudoku(){
 board.className="puzzle-board sudoku-grid";
 const fragment=document.createDocumentFragment(),selected=s.selected,selectDigit=s.board[selected];
 for(let i=0;i<81;i++){
  const b=document.createElement("button");b.type="button";b.className="sudoku-cell";b.dataset.cell=String(i);
  const r=(i/9)|0,c=i%9,sr=(selected/9)|0,sc=selected%9;
  if(c===2||c===5)b.classList.add("block-right");
  if(r===2||r===5)b.classList.add("block-bottom");
  if(s.givens[i])b.classList.add("given");
  if(selected!==i&&(r===sr||c===sc||(Math.floor(r/3)===Math.floor(sr/3)&&Math.floor(c/3)===Math.floor(sc/3))))b.classList.add("peer");
  if(selectDigit&&s.board[i]===selectDigit)b.classList.add("matched-digit");
  if(sudokuConflict(i))b.classList.add("conflict");
  if(selected===i)b.classList.add("selected");
  b.setAttribute("aria-label","Row "+(r+1)+", column "+(c+1)+", "+(s.board[i]||"empty")+(s.givens[i]?", given":", editable"));
  b.setAttribute("aria-pressed",String(selected===i));
  if(s.board[i])b.textContent=s.board[i];
  else if(s.notes[i]){const n=document.createElement("span");n.className="notes";for(let v=1;v<=9;v++){const cell=document.createElement("i");cell.textContent=s.notes[i].includes(String(v))?v:"";n.append(cell);}b.append(n);}
  fragment.append(b);
 }
 board.replaceChildren(fragment);
 tools.replaceChildren();
 const keypad=document.createElement("div");keypad.className="sudoku-keypad";keypad.setAttribute("aria-label","Number pad");
 for(let n=1;n<=9;n++){const b=document.createElement("button");b.type="button";b.dataset.number=String(n);b.textContent=n;b.disabled=s.done;keypad.append(b);}
 tools.append(keypad);
 const row=document.createElement("div");row.className="sudoku-actions";
 const erase=document.createElement("button");erase.type="button";erase.dataset.action="erase";erase.disabled=s.done;erase.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><use href="#icon-erase"></use></svg> Erase';
 const notes=document.createElement("button");notes.type="button";notes.dataset.action="notes";notes.disabled=s.done;notes.setAttribute("aria-pressed",String(s.noteMode));notes.textContent="Notes "+(s.noteMode?"ON":"OFF");
 const hint=document.createElement("button");hint.type="button";hint.dataset.action="hint";hint.disabled=s.done||s.hints===0;hint.textContent="Hint ("+s.hints+")";
 row.append(erase,notes,hint);tools.append(row);
 status.replaceChildren();chip(s.level.toUpperCase());chip(time(s.elapsed));chip("Mistakes "+s.mistakes);
 if(s.done)updateMessage("Puzzle complete! Brilliantly solved.");
}
function sudokuEnter(value){
 if(s.done||s.selected<0||s.givens[s.selected])return;
 const i=s.selected,n=Number(value);
 if(value==="erase"){s.board[i]=0;s.notes[i]="";}
 else if(!Number.isInteger(n)||n<1||n>9)return;
 else if(s.noteMode){if(s.board[i])return;const str=String(n);s.notes[i]=s.notes[i].includes(str)?s.notes[i].replace(str,""):s.notes[i]+str;}
 else{s.board[i]=n;s.notes[i]="";if(n!==s.solution[i])s.mistakes++;}
 if(s.board.every((n,j)=>n===s.solution[j])){s.done=true;}
 putSaved();renderSudoku();
}
function sudokuHint(){
 if(s.done||s.hints===0)return;
 let i=s.selected;if(i<0||s.givens[i]||s.board[i]===s.solution[i])i=s.board.findIndex((n,j)=>n!==s.solution[j]);
 if(i<0)return;
 s.board[i]=s.solution[i];s.notes[i]="";s.hints--;s.selected=i;
 if(s.board.every((n,j)=>n===s.solution[j]))s.done=true;
 putSaved();renderSudoku();
}
const iconPaths=[
 '<circle cx="32" cy="32" r="23"/>','<path d="M32 5 59 32 32 59 5 32Z"/>','<path d="M32 7 61 56H3Z"/>','<path d="m32 4 7 19 20 0-16 13 6 21-17-12-17 12 6-21L5 23h20Z"/>',
 '<path d="M17 7h30l15 25-15 25H17L2 32Z"/>','<path d="M9 13h46v38H9Z"/>','<circle cx="32" cy="32" r="22"/><circle cx="32" cy="32" r="10" fill="white"/>','<path d="M4 24q14-27 28 0t28 0v16q-14 27-28 0T4 40Z"/>',
 '<path d="M32 3 59 20v25L32 61 5 45V20Z"/>','<path d="M9 5h46v54H9Zm14 14h18v26H23Z" fill-rule="evenodd"/>','<path d="M32 4c-12 0-21 10-21 21 0 16 21 35 21 35s21-19 21-35C53 14 44 4 32 4Z"/>','<path d="M10 13c3-9 13-12 22-5 9-7 19-4 22 5 6 16-7 27-22 42C17 40 4 29 10 13Z"/>'
];
const iconPalette=["#6a9e8e","#d48b6d","#d9b454","#779ab7","#a08fc1","#cb8f9c","#608b7f","#cf9b67","#859ab1","#ac806e","#89aa72","#cb7372"];
function shapeSvg(id){return '<svg role="img" aria-hidden="true" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg"><g fill="'+iconPalette[id]+'" stroke="#24332e" stroke-opacity=".12" stroke-width="1.5">'+iconPaths[id]+'</g></svg>';}
function renderMemory(){
 board.className="puzzle-board memory-grid"+(s.level==="hard"?" cols-6":"");
 if(board.children.length!==s.deck.length||board.firstElementChild?.dataset.memory===undefined){
  const frag=document.createDocumentFragment();
  s.deck.forEach((shape,i)=>{
   const button=document.createElement("button");button.type="button";button.className="memory-card";button.dataset.memory=String(i);
   const face=document.createElement("span");face.className="memory-shape";face.innerHTML=shapeSvg(shape);button.append(face);frag.append(button);
  });board.replaceChildren(frag);
 }
 for(let i=0;i<s.deck.length;i++){
  const b=board.children[i],opened=s.flipped.includes(i)||s.matched[i];
  b.classList.toggle("flipped",s.flipped.includes(i));b.classList.toggle("matched",s.matched[i]);
  b.disabled=s.done||s.matched[i]||s.locked;
  b.setAttribute("aria-label","Card "+(i+1)+(s.matched[i]?", matched":opened?", revealed":", face down"));
 }
 tools.replaceChildren();
 status.replaceChildren();
 chip(s.level==="easy"?"8 PAIRS":s.level==="medium"?"10 PAIRS":"12 PAIRS");
 chip("Moves "+s.tries);chip("Matched "+(s.matched.filter(Boolean).length/2));
 chip(time(s.elapsed));
 if(s.done)updateMessage("All pairs found in "+s.tries+" moves!");
}
function flipMemory(i){
 if(s.done||s.locked||s.matched[i]||s.flipped.includes(i))return;
 s.flipped.push(i);renderMemory();
 if(s.flipped.length!==2)return;
 s.tries++;
 const [a,b]=s.flipped;
 if(s.deck[a]===s.deck[b]){
  s.matched[a]=true;s.matched[b]=true;s.flipped=[];
  if(s.matched.every(Boolean))s.done=true;
  putSaved();renderMemory();return;
 }
 s.locked=true;renderMemory();
 delay=setTimeout(()=>{delay=null;if(kind!=="memory")return;s.flipped=[];s.locked=false;putSaved();renderMemory();},720);
 putSaved();
}
function renderConnect(){
 board.className="puzzle-board connect-board";
 const frag=document.createDocumentFragment();
 for(let c=0;c<7;c++){
  const button=document.createElement("button");button.type="button";button.dataset.column=String(c);button.className="connect-col";
  button.disabled=s.ended!==0||s.cells[0][c]!==0||(s.mode==="cpu"&&s.turn===2);
  button.setAttribute("aria-label","Drop disc in column "+(c+1));
  for(let r=0;r<6;r++){const cell=document.createElement("span");cell.className="connect-cell"+(s.cells[r][c]===1?" red":s.cells[r][c]===2?" yellow":"");cell.setAttribute("aria-hidden","true");button.append(cell);}
  frag.append(button);
 }
 board.replaceChildren(frag);tools.replaceChildren();
 status.replaceChildren();
 chip(s.mode==="cpu"?"VS COMPUTER":"2 PLAYERS · LOCAL");
 if(s.ended){chip(s.ended===3?"DRAW":s.ended===1?"CORAL WINS":"GOLD WINS");updateMessage(s.ended===3?"Board full · it's a draw!":s.ended===1?"Coral takes the round!":"Gold takes the round!");}
 else chip(s.turn===1?"CORAL TO PLAY":"GOLD TO PLAY");
 chip(time(s.elapsed));
}
function playConnect(col){
 if(s.ended||(s.mode==="cpu"&&s.turn===2))return;
 if(core.drop(s.cells,col,s.turn)<0)return;
 s.ended=core.winner(s.cells);
 if(!s.ended)s.turn=s.turn===1?2:1;
 putSaved();renderConnect();
 if(s.mode==="cpu"&&s.turn===2&&!s.ended)queueBot();
}
function queueBot(){
 if(delay!==null)clearTimeout(delay);
 delay=setTimeout(()=>{
  delay=null;if(kind!=="connect"||s.mode!=="cpu"||s.turn!==2||s.ended)return;
  const col=core.botColumn(s.cells,3);
  if(col<0){s.ended=core.winner(s.cells)||3;renderConnect();return;}
  core.drop(s.cells,col,2);s.ended=core.winner(s.cells);
  if(!s.ended)s.turn=1;putSaved();renderConnect();
 },180);
}
board.addEventListener("click",e=>{
 const sudoku=e.target.closest("[data-cell]");
 if(kind==="sudoku"&&sudoku){s.selected=Number(sudoku.dataset.cell);putSaved();renderSudoku();return;}
 const memory=e.target.closest("[data-memory]");
 if(kind==="memory"&&memory){flipMemory(Number(memory.dataset.memory));return;}
 const connect=e.target.closest("[data-column]");
 if(kind==="connect"&&connect){playConnect(Number(connect.dataset.column));}
});
tools.addEventListener("click",e=>{
 if(kind!=="sudoku")return;
 const number=e.target.closest("[data-number]");if(number){sudokuEnter(number.dataset.number);return;}
 const action=e.target.closest("[data-action]");if(!action)return;
 if(action.dataset.action==="notes"){s.noteMode=!s.noteMode;putSaved();renderSudoku();}
 if(action.dataset.action==="erase")sudokuEnter("erase");
 if(action.dataset.action==="hint")sudokuHint();
});
document.addEventListener("keydown",e=>{
 if(kind!=="sudoku"||dialog.open||e.altKey||e.ctrlKey||e.metaKey||["INPUT","TEXTAREA"].includes(document.activeElement?.tagName))return;
 if(/^[1-9]$/.test(e.key)){e.preventDefault();sudokuEnter(e.key);return;}
 if(e.key==="Delete"||e.key==="Backspace"||e.key==="0"){e.preventDefault();sudokuEnter("erase");return;}
 if(e.key.toLowerCase()==="n"){e.preventDefault();s.noteMode=!s.noteMode;putSaved();renderSudoku();return;}
 const dirs={ArrowUp:-9,ArrowDown:9,ArrowLeft:-1,ArrowRight:1};
 if(dirs[e.key]!==undefined){
  e.preventDefault();const row=Math.floor(s.selected/9),col=s.selected%9;
  s.selected=e.key==="ArrowUp"?((row+8)%9)*9+col:e.key==="ArrowDown"?((row+1)%9)*9+col:e.key==="ArrowLeft"?row*9+(col+8)%9:row*9+(col+1)%9;
  putSaved();renderSudoku();board.querySelector('[data-cell="'+s.selected+'"]')?.focus({preventScroll:true});
 }
});
$("settingsButton").addEventListener("click",()=>{renderOptions();dialog.showModal();});
$("settingsClose").addEventListener("click",()=>dialog.close());
dialog.addEventListener("click",e=>{if(e.target===dialog)dialog.close();});
$("gameSwitch").addEventListener("click",e=>{const b=e.target.closest("[data-puzzle]");if(b)switchGame(b.dataset.puzzle);});
$("gameOptions").addEventListener("click",e=>{const b=e.target.closest("[data-option]");if(!b)return;const selected=s.level||s.mode;if(selected!==b.dataset.option)startOver(b.dataset.option);});
$("newGame").addEventListener("click",()=>startOver(s.level||s.mode));
document.addEventListener("visibilitychange",()=>{if(document.hidden){cancelPending();putSaved();}else if(kind==="connect"&&s.mode==="cpu"&&s.turn===2&&!s.ended)queueBot();});
window.addEventListener("pagehide",putSaved);
setInterval(()=>{if(!s||document.hidden||dialog.open||(s.type==="sudoku"&&s.done)||(s.type==="memory"&&s.done)||(s.type==="connect"&&s.ended))return;
 s.elapsed++;putSaved();
 const badges=status.querySelectorAll("span");const index=kind==="sudoku"?1:badges.length-1;if(badges[index])badges[index].textContent=time(s.elapsed);
},1000);
const query=new URLSearchParams(location.search).get("game");switchGame(allowed.includes(query)?query:"sudoku");
})();
