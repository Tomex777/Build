'use strict';
// Independent E regression gate against A's *actual HTML* and C's *actual
// validation function*. Never pass a UI option by silently coercing it.
// Source-only compatibility test; a passing result is NOT live signed API QA.
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const a=path.resolve(process.env.NIGHT_A_SOURCE_ROOT||path.join(__dirname,'../../../agentA/night-play'));
const c=path.resolve(process.env.NIGHT_C_SOURCE_ROOT||path.join(__dirname,'../../../agentC/mscc'));
const html=fs.readFileSync(path.join(a,'channels/config/index.html'),'utf8');
const server=fs.readFileSync(path.join(c,'channel-publisher/service.js'),'utf8');
const begin=server.indexOf('const JID ='),end=server.indexOf('const digest =');
assert.ok(begin>0&&end>begin,'Locate actual backend config validator, do not emulate it');
const harness='class ChannelError extends Error {constructor(status,message){super(message);this.status=status}};\n'+
 server.slice(begin,end)+'\n({patchConfig,defaultConfig})';
const {patchConfig,defaultConfig}=vm.runInNewContext(harness,{Intl,Buffer});
function options(id){
 const start=html.indexOf('id="'+id+'"');
 assert.ok(start>=0,'Missing UI selector '+id);
 const segment=html.slice(start,html.indexOf('</select>',start));
 return [...segment.matchAll(/<option\s+value="([^"]+)"/g)].map(m=>m[1]);
}
let failures=[];
for(const [id,key,convert] of [
 ['watermarkPosition','watermarkPosition',v=>v],
 ['watermarkOpacity','watermarkOpacity',Number]
]){
 for(const value of options(id)){
  try{
   const conf=patchConfig(defaultConfig(),{branding:{[key]:convert(value)}});
   assert.equal(conf.branding[key],convert(value));
  }catch(err){
   failures.push(id+': UI option '+JSON.stringify(value)+' rejected by C: '+err.message);
  }
 }
}
for(const mode of options('memeMode')){
 if(mode==='automatic'){
  try{patchConfig(defaultConfig(),{memes:{mode}});failures.push('Automatic mode unexpectedly active before approved scheduler');}
  catch(e){if(e.status!==503)failures.push('Automatic mode expected 503 fail-closed, got '+e.status);}
 }
}
if(failures.length){console.error('FAIL A/C SCHEMA CONTRACT\n'+failures.join('\n'));process.exitCode=1;}
else console.log('PASS A/C UI branding options accepted by C, scheduler automatic remains disabled');
