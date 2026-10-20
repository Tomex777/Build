#!/usr/bin/env node
/* Integrity gate: catalog entries are NOT equal to native Android implementations. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=__dirname,java=path.join(root,'app/src/main/java/com/night/offlinegames');
const main=fs.readFileSync(path.join(java,'MainActivity.kt'),'utf8');
const entries=[...main.matchAll(/GameEntry\("([^"]+)"/g)].map(m=>m[1]);
const native=[
 ['2048','Game2048Screen','Game2048Screen.kt','Board2048.kt'],
 ['mines','NativeMinesScreen','NativeMinesScreen.kt','NativeMinesEngine.kt'],
 ['lights','NativeLightsScreen','NativeLightsScreen.kt','NativeLightsEngine.kt'],
 ['snake','NativeSnakeScreen','NativeSnakeScreen.kt','NativeSnakeEngine.kt'],
 ['reversi','NativeReversiScreen','NativeReversiScreen.kt','NativeReversiEngine.kt'],
 ['ttt','NativeTicTacToeScreen','NativeTicTacToeScreen.kt','NativeTicTacToe.kt'],
 ['colorsort','WaterSortScreen','WaterSortScreen.kt','WaterSortEngine.kt'],
 ['shark','SharkSurvivalScreen','SharkSurvivalScreen.kt','SharkEngine.kt']
];
for(const [id,route,screen,engine] of native){
 assert(entries.includes(id),'Native title not registered: '+id);
 assert(main.includes('selected == "'+id+'"'),'Missing native route '+id);
 assert(main.includes(route+'(onExit'),'Native route points to wrong Composable: '+id);
 for(const file of [screen,engine]){
   const source=fs.readFileSync(path.join(java,file),'utf8');
   assert(!/import android\.webkit|\bWebView\s*\(|\bAndroidView\s*\(|javaScriptEnabled/.test(source),
     id+'/'+file+' must not be a browser wrapper');
 }
}
assert.equal(entries.length,106,'Unexpected catalog size');
console.log('Genuine native Kotlin routes:',native.length);
console.log('Legacy HTML/WebView routes remaining:',entries.length-native.length);
console.log('No false claim of all-native support until legacy routes = 0');
