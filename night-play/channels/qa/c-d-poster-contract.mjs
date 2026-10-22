// Independent cross-agent E check: C's actual server config accepts only D-sendable posters.
// This intentionally fails while signed direct RPC can persist an unrenderable persona.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {pathToFileURL} from 'node:url';
const c=path.resolve(process.env.NIGHT_C_SOURCE_ROOT||path.join(process.cwd(),'agentC/mscc'));
const d=path.resolve(process.env.NIGHT_D_SOURCE_ROOT||path.join(process.cwd(),'agentD/mscc/channel-branding'));
const server=fs.readFileSync(path.join(c,'channel-publisher/service.js'),'utf8');
const start=server.indexOf('const JID ='),end=server.indexOf('const digest =');
assert.ok(start>=0&&end>start);
const snippet='class ChannelError extends Error {constructor(status,msg){super(msg);this.status=status}};\n'+server.slice(start,end)+'\n({patchConfig,defaultConfig})';
const {patchConfig,defaultConfig}=vm.runInNewContext(snippet,{Intl,Buffer});
const {composeChannelCaption}=await import(pathToFileURL(path.join(d,'caption.mjs')).href);
const person='P'.repeat(90);
const cfg=patchConfig(defaultConfig(),{branding:{signatureEnabled:true,posterName:person}});
let formatterRejected=false;
try{
 composeChannelCaption({text:'Licensed meme',poster:{enabled:cfg.branding.signatureEnabled,name:cfg.branding.posterName}});
}catch(error){formatterRejected=true;assert.match(error.message,/Invalid poster display name/);}
if(formatterRejected){
 console.error('FAIL C/D BRANDING: C persisted 90-codepoint poster name but D rejects it at send; align max<=80 graphemes/codepoints (A UI already 70).');
 process.exitCode=1;
}else console.log('PASS C/D branding: server only persists format-safe signed personas');
