#!/usr/bin/env node
/* Android 8+/API 26 may ship an older Android System WebView. Node's syntax
   check alone accepts newer JavaScript, so guard against syntax that would
   silently prevent every bundled game on an older WebView from booting. */
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assert=require('node:assert/strict');

const assets=path.join(__dirname,'app','src','main','assets');
const roots=['arcade','puzzles','classics'];
let checked=0;
for(const root of roots){
 const folder=path.join(assets,root);
 const html=fs.readFileSync(path.join(folder,'index.html'),'utf8');
 const compat='<script src="/compat.js"></script>';
 assert(html.includes(compat),root+' forgot to load WebView compatibility code');
 const gameJs=html.indexOf('<script src="/'+root+'/');
 assert(gameJs>html.indexOf(compat),root+' must load compatibility before the game');
 for(const file of fs.readdirSync(folder).filter(x=>x.endsWith('.js'))){
  const source=fs.readFileSync(path.join(folder,file),'utf8');
  new vm.Script(source,{filename:root+'/'+file});
  // Exclude the legal ES2015 ternary + decimal form: "value?.25" means "value ? .25".
  // Optional chaining and nullish coalescing only work in newer WebViews.
  const unsupported=/\?\.(?![0-9])|\?\?/g;
  const found=unsupported.exec(source);
  assert(!found,root+'/'+file+' contains modern syntax at character '+(found&&found.index));
  checked++;
 }
}
const compatSource=fs.readFileSync(path.join(assets,'compat.js'),'utf8');
new vm.Script(compatSource,{filename:'compat.js'});
for(const capability of ["replaceChildren","Array.prototype.at","String.prototype.replaceAll"])
 assert(compatSource.includes(capability),'Missing polyfill for '+capability);
assert(checked>=25,'Unexpectedly small game engine set: '+checked);
console.log('WebView compatibility checked:',checked,'original game engines, three HTML entry points, and polyfills');
