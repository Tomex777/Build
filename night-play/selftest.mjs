import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8')
const vercel = JSON.parse(readFileSync(new URL('./vercel.json', import.meta.url), 'utf8'))
/* ABR: navigational arrows are SVG, not text characters or generated CSS labels. */
for(const asset of [
 './index.html','./jigsaw/index.html','./jigsaw/style.css','./club/games.js',
 './arcade/index.html','./stories/index.html','./stories/library.html',
 './stories/short-edition.html','./multiplayer/ttt/index.html'
]){
 const source=readFileSync(new URL(asset,import.meta.url),'utf8')
 assert.doesNotMatch(source,/[\u2190-\u21ff\u2794-\u27bf]/u,asset+' must use real vector SVGs for arrow icons')
}
new vm.Script(readFileSync(new URL('./club/games.js',import.meta.url),'utf8'),{filename:'night-play/club/games.js'})
assert.ok(html.includes('class="jigsaw-cover-photo"'),'Jigsaw game tile should not use the old synthetic puzzle image')
assert.ok(!html.includes('jthumb-sky'),'Remove original procedural library tile image')
const jigsawCss=readFileSync(new URL('./jigsaw/style.css',import.meta.url),'utf8')
assert.ok(!jigsawCss.includes('START PUZZLE'),'The gallery picture is the button: no redundant Start Puzzle badge')

const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1])
assert.ok(scripts.length >= 1)
for (const script of scripts) new vm.Script(script, {filename:'night-play-inline.js'})
for (const view of ['welcome','dashboard','games','profile','terms','privacy']) {
 assert.match(html,new RegExp('data-view="'+view+'"'))
 assert.ok(vercel.rewrites.some(x=>x.source==='/'+view), 'missing refresh route '+view)
}
assert.ok(vercel.rewrites.some(x=>x.source==='/play/:game'))
assert.equal(/data-view="updates"/.test(html),false,'Updates should not be a redundant navigation page')
assert.ok(html.includes('id="announcementBar"'))
assert.ok(html.includes('id="announcementHistory"'))
assert.ok(html.includes('id="announcementMore"'))
assert.ok(html.includes('id="announcementDismiss"'))
assert.ok(html.includes('id="view-profile"'))
assert.ok(html.includes('id="settingsDialog"'))
assert.ok(html.includes('overlay.id="snakeOverlay"'),'snake overlay should be created dynamically')
assert.ok(html.includes('Tap to start '))
assert.ok(html.includes('Tap to play again '))
assert.ok(html.includes('snake-mode'))
assert.ok(html.includes('snake-speed'))
assert.ok(html.includes('snake-style'))
assert.ok(html.includes('startSnakePreviews'))
assert.ok(html.includes('function greetingText(hour)'))
assert.ok(html.includes('id="homeGreeting"'))
assert.ok(html.includes('id="analyticsGraph"'))
assert.ok(html.includes('id="recordsMenu"'))
assert.ok(html.includes('id="recordSearch"'))
assert.ok(html.includes('data-view="profile"'))
assert.ok(!html.includes('Account <span class="muted">profile.'))
assert.ok(!html.includes('NOT CONNECTED</span>'))
assert.ok(html.includes('id="editPlayerName"'))
assert.ok(html.includes('id="playerNameForm"'))
assert.ok(html.includes('function playerName()'))
assert.ok(html.includes('id="view-privacy"'))
assert.ok(html.includes('id="view-terms"'))
assert.ok(html.includes('href="/privacy" data-route'))
assert.ok(html.includes('href="/terms" data-route'))
assert.ok(html.includes('let selectedRecordGame=getSaved("night-play-selected-record-v7")||"overview"'))
assert.ok(html.includes('function drawActivityChart('))
assert.ok(html.includes('function recordedActivity()'))
assert.ok(html.includes('Most played games'))
assert.ok(html.includes('function availableRecordGames()'))
assert.ok(html.includes('function plotSVG('))
assert.ok(html.includes('document.body.dataset.view=current'))
assert.ok(html.includes('class="site-footer"'))
assert.ok(html.includes('body[data-view="welcome"] .mobile-nav'))
assert.ok((html.match(/class="game-art(?: premium-game-art)?"/g)||[]).length>=9,'original non-Club SVG artwork stays present as playable games are added')
assert.match(html,/data-external-game="\/strategy\/"/,'Chess card opens its real legal-move board')
assert.match(html,/data-external-game="\/piano-tiles\/"/,'Piano Tiles card opens the complete rhythm game')
assert.match(html,/data-jigsaw[\s\S]*?class="jigsaw-cover-photo"/,'Jigsaw thumbnail uses a real photograph rather than the removed puzzle illustration')
assert.match(html,/class="wing-preview-bird"/,'Wing Hop presents real Kenney character artwork')
assert.doesNotMatch(html,/data-club="ludo"/,'Ludo is not listed in the game library')
assert.match(html,/data-jigsaw/,'Jigsaw is discoverable in the puzzle library')
assert.equal((html.match(/<symbol id="art-/g)||[]).length,12,'all game SVG illustrations must be defined locally')
assert.ok((html.match(/<symbol id="icon-/g)||[]).length>=20,'navigation and actions use local SVG icons')
assert.equal(/class="game-emoji"/.test(html),false,'game preview artwork must not rely on emoji fonts')
assert.equal(/<div class="floating/.test(html),false,'remove generic floating overlay cards')
assert.ok(html.includes('gameDialog.dataset.skin=kind'),'game boards must be independently themeable')
assert.ok(!html.includes('root.appendChild(control)'),'Snake no longer uses a directional pad')
assert.ok(!html.includes('control.className="dpad"'),'Snake has no generated D-pad buttons')
assert.ok(!html.includes('snakeFrame=requestAnimationFrame(renderFrame)'),'Snake must not redraw each frame or interpolate step-by-step')
assert.ok(!html.includes('snakePrevBody=snake.body.map'),'Restore the previous immediate tile/tick movement')
assert.ok(html.includes('turbo:66'),'Snake turbo speed is available')
assert.ok(html.includes('const size=18,rows=25'),'Snake is 18 columns by 25 rows, not a stretched 18×18 square')
assert.ok(html.includes('c.height=rows*20'),'Snake canvas is actually taller, not simply CSS stretched')
assert.ok(html.includes('p.y>=snake.rows'),'Snake vertical collision uses portrait board height')
assert.ok(html.includes('p.y=(p.y+snake.rows)%snake.rows'),'Wrap mode uses portrait board height')
assert.ok(html.includes('--snake-field-ratio:18 / 25'),'Snake portrait proportions are explicit')
assert.ok(html.includes('function bindSwipeGesture('),'Games share prompt pointer gesture handling')
assert.ok(html.includes('snake.turnQueue=[]'),'Snake uses a two-step input queue for tight turns')
assert.ok(html.includes('pointermove'),'Handle input during a gesture, not at delayed touchend')
assert.ok(html.includes('--locked-board-size'),'Focus must not expand the board')

assert.ok(html.includes('const focusButton=document.getElementById("gameFocus")'),'Focus button must expand arena')
assert.ok(html.includes('M24 54 C24 37.43'),'Use the actual NIGHT logo paths, not the invented game badge')
assert.ok(html.includes('function tileMovePaths(before,dir)'),'2048 slides animate through tile move paths')
assert.ok(html.includes('id="undo2048"'),'2048 must have working Undo control')
assert.ok(html.includes('--board-size:min(calc(100vw - 10px)'),'V10 mobile near edge-to-edge sizing')
assert.ok(html.includes('class="ttt-mark"'),'TTT mark SVG')
assert.ok(html.includes('class="ttt-win-line"'),'TTT win line')
assert.ok(html.includes('NIGHT Play · Updated 16 October 2026'))
assert.equal(/<span class="status-pill">PREVIEW<\/span>/.test(html),false)
assert.equal(/class="gamecard library-card"[^>]+\bdisabled/.test(html),false)

assert.match(html,/\.hero-game-art\{filter:none;transform:none/,'no decorative fake 3D tilt')
assert.ok(html.includes('.mobile-nav .ui-icon'),'mobile tabs should have icon-specific responsive sizing')
assert.ok(html.includes('@media(prefers-reduced-motion:reduce)'),'honour reduced motion for visual treatment')

assert.ok(html.includes('.announcement-bar.dismissed{display:none!important}'))
assert.ok(html.includes('position:sticky'))
assert.ok(html.includes('2048-size'))
assert.ok(html.includes('ttt-difficulty'))
assert.ok(html.includes('minimaxTtt'))
assert.ok(html.includes('id="analyticsBest"'))
assert.equal(/id="dashSnakeBest"/.test(html),false)
assert.equal(/id="dash2048Best"/.test(html),false)
assert.equal(/NIGHT is calling|night owls|Good games\.<br>Late nights\./.test(html),false,'Brand NIGHT need not imply nighttime')
const dynamicIds = new Set(['score','best','modeBtn','turnlabel','tttboard','board2048','snakeOverlay'])
const declared = new Set([...html.matchAll(/id="([^"]+)"/g)].map(x=>x[1]))
for(const id of [...html.matchAll(/getElementById\("([^"]+)"\)/g)].map(x=>x[1])){
 assert.ok(declared.has(id)||dynamicIds.has(id), 'missing element '+id)
}
// Multiple independent inline scripts may run before the game engine (for
// example an early cookie-session prefetch). Locate the engine by content.
const gameScript=scripts.find(source=>source.includes('function minimaxTtt(')&&source.includes('function move2048'))
assert.ok(gameScript,'missing primary arcade game script')
const engine = 'function minimaxTtt('+gameScript.split('function minimaxTtt(')[1].split('function botMoveTtt()')[0]
const sandbox={};vm.runInNewContext('const isWin = (b,m)=>'+
 '[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]].some(line=>line.every(i=>b[i]===m));\n'+
 engine+'this.evalMove=(b,mark)=>minimaxTtt(b,mark);',sandbox)
assert.equal(sandbox.evalMove(['X','X','X','','','','','',''],'O'), -10)
assert.equal(sandbox.evalMove(['O','O','O','','','','','',''],'X'), 10)
const full=['X','O','X','X','O','O','O','X','X']
assert.equal(sandbox.evalMove(full,'O'),0)
const slideSrc='function slide'+gameScript.split('function slide')[1].split('function move2048')[0]
const smod={};vm.runInNewContext('let grid=Array(4).fill(0).map(()=>Array(4).fill(0));let tileScore=0;\n'+slideSrc+'this.evalSlide=(row,len)=>{grid=Array(len).fill(0).map(()=>Array(len).fill(0));tileScore=0;const out=slide(row);return {out,points:tileScore}};',smod)
assert.equal(JSON.stringify(smod.evalSlide([2,2,4,0],4)),JSON.stringify({out:[4,4,0,0],points:4}))
assert.equal(JSON.stringify(smod.evalSlide([2,2,4,4,2,2],6)),JSON.stringify({out:[4,8,4,0,0,0],points:16}))
const mazeSource='function makeSnakeMaze('+gameScript.split('function makeSnakeMaze(')[1].split('function snakeModeName(')[0]
const mazeSandbox={}
vm.runInNewContext(mazeSource+'this.build=(mode,size)=>makeSnakeMaze(mode,size);',mazeSandbox)
assert.equal(mazeSandbox.build('wrap',18).length,0)
assert.equal(mazeSandbox.build('walls',18).length,0)
assert.ok(mazeSandbox.build('maze1',18).length>10)
assert.ok(mazeSandbox.build('maze2',18).length>10)
assert.ok(mazeSandbox.build('maze3',18).length>10)
console.log('PASS NIGHT Play V9: identity, 11 SVG artworks, 2048 motion, immersive boards and unchanged gameplay rules')
await import('./club/logic-test.mjs');
await import('./jigsaw/board-logic-test.mjs');

