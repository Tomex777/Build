import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8')
const vercel = JSON.parse(readFileSync(new URL('./vercel.json', import.meta.url), 'utf8'))
const scripts = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1])
assert.ok(scripts.length >= 1)
for (const script of scripts) new vm.Script(script, {filename:'night-play-inline.js'})
for (const view of ['welcome','dashboard','games','profile']) {
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
assert.ok(html.includes('id="snakeOverlay"')===false,'snake overlay is created dynamically')
assert.ok(html.includes('Tap to start ▶'))
assert.ok(html.includes('Tap to play again ↻'))
assert.ok(html.includes('snake-wrap'))
assert.ok(html.includes('2048-size'))
assert.ok(html.includes('ttt-difficulty'))
assert.ok(html.includes('minimaxTtt'))
assert.ok(html.includes('profile2048Best'))
assert.equal(/id="dashSnakeBest"/.test(html),false)
assert.equal(/id="dash2048Best"/.test(html),false)
assert.equal(/NIGHT is calling|night owls|Good games\.<br>Late nights\./.test(html),false,'Brand NIGHT need not imply nighttime')
const dynamicIds = new Set(['score','best','modeBtn','turnlabel','tttboard','board2048','snakeOverlay'])
const declared = new Set([...html.matchAll(/id="([^"]+)"/g)].map(x=>x[1]))
for(const id of [...html.matchAll(/getElementById\("([^"]+)"\)/g)].map(x=>x[1])){
 assert.ok(declared.has(id)||dynamicIds.has(id), 'missing element '+id)
}
const engine= scripts[0].split('function isWin(b,m)')[1].split('function botMoveTtt()')[0]
const sandbox={};vm.runInNewContext('const isWin = (b,m)=>'+
 '[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]].some(line=>line.every(i=>b[i]===m));\n'+
 'function minimaxTtt'+engine+'this.evalMove=(b,mark)=>minimaxTtt(b,mark);',sandbox)
assert.equal(sandbox.evalMove(['X','X','X','','','','','',''],'O'), -10)
assert.equal(sandbox.evalMove(['O','O','O','','','','','',''],'X'), 10)
const full=['X','O','X','X','O','O','O','X','X']
assert.equal(sandbox.evalMove(full,'O'),0)
const slideSrc='function slide'+scripts[0].split('function slide')[1].split('function move2048')[0]
const smod={};vm.runInNewContext('let grid=Array(4).fill(0).map(()=>Array(4).fill(0));let tileScore=0;\n'+slideSrc+'this.evalSlide=(row,len)=>{grid=Array(len).fill(0).map(()=>Array(len).fill(0));tileScore=0;const out=slide(row);return {out,points:tileScore}};',smod)
assert.equal(JSON.stringify(smod.evalSlide([2,2,4,0],4)),JSON.stringify({out:[4,4,0,0],points:4}))
assert.equal(JSON.stringify(smod.evalSlide([2,2,4,4,2,2],6)),JSON.stringify({out:[4,8,4,0,0,0],points:16}))
console.log('PASS NIGHT Play: 4 routes, announcements, profile, settings, DOM IDs, 2048 merge rules, tic-tac-toe evaluation')
