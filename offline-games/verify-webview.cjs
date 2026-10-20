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
// Exercise shims in a realm where modern Array/DOM methods do not exist.
const context=vm.createContext({});
vm.runInContext(`
  function FakeNode() { this.children=[]; }
  Object.defineProperty(FakeNode.prototype,'firstChild',{get:function(){return this.children[0]||null;}});
  FakeNode.prototype.appendChild=function(n){this.children.push(n);return n;};
  FakeNode.prototype.removeChild=function(n){this.children.splice(this.children.indexOf(n),1);return n;};
  function FakeElement(){FakeNode.call(this);}
  FakeElement.prototype=Object.create(FakeNode.prototype);
  function FakeDocument(){FakeNode.call(this);}
  FakeDocument.prototype=Object.create(FakeNode.prototype);
  function FakeFragment(){FakeNode.call(this);}
  FakeFragment.prototype=Object.create(FakeNode.prototype);
  this.Node=FakeNode;this.Element=FakeElement;this.Document=FakeDocument;
  this.DocumentFragment=FakeFragment;
  this.document={createTextNode:function(t){return {textContent:t};}};
  this.window={addEventListener:function(){}};
  delete Array.prototype.at;
  delete Array.prototype.flat;
  delete Array.prototype.flatMap;
  delete String.prototype.replaceAll;
`,context);
vm.runInContext(compatSource,context,{filename:'compat.js'});
assert.equal(vm.runInContext('[1,[2,[3]]].flat(2).join(",")',context),'1,2,3');
assert.equal(vm.runInContext('[1,2].flatMap(function(n){return [n,n+10]}).join(",")',context),'1,11,2,12');
assert.equal(vm.runInContext('[7,8,9].at(-1)',context),9);
assert.equal(vm.runInContext('"a-b-a".replaceAll("a","z")',context),'z-b-z');
assert.equal(vm.runInContext('var box=new Element();box.replaceChildren("hello",new Node());box.children.length',context),2);
assert.equal(vm.runInContext('box.replaceChildren();box.children.length',context),0);

console.log('WebView compatibility checked:',checked,'original game engines, three HTML entry points, and polyfills');
