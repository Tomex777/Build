import assert from 'node:assert/strict';
import command from './commands/fun/sp.js';

const group = '1234567890@g.us';
const state = new Map();
const replies = [];
let played = '';
const ctx = {
  account:{sock:{sendMessage:async () => {}}},
  message:{key:{remoteJid:group}},
  isSupremeOwner:true,
  args:[],
  shared:{
    get:(name, jid) => name === 'reaction-sound-groups' ? state.get(jid) : null,
    set:(name, jid, value) => {
      assert.equal(name, 'reaction-sound-groups');
      state.set(jid,value);
    },
  },
  reactionSounds:{
    allowed:jid=>state.get(jid)?.enabled===true,
    manualList:()=>'*NIGHT sound menu*',
    play:async (sock,jid,clip)=>{ played=clip; return {ok:true}; },
  },
  reply:async text => { replies.push(text); },
};
const run = async (...args) => { ctx.args=args; await command.run(ctx); };

await run('on');
assert.deepEqual(state.get(group), {enabled:true,auto:true});
await run('list');
assert.match(replies.at(-1), /sound menu/);
await run('laugh');
assert.equal(played,'laugh');
await run('auto','off');
assert.deepEqual(state.get(group), {enabled:true,auto:false});
await run('auto','on');
assert.equal(state.get(group).auto,true);
await run('off');
assert.deepEqual(state.get(group), {enabled:false,auto:false});
await run('laugh');
assert.match(replies.at(-1), /disabled here/);
ctx.isSupremeOwner=false;
await run('on');
assert.equal(state.get(group).enabled,false);
assert.match(replies.at(-1), /primary NIGHT owner/);
ctx.isSupremeOwner=true;
ctx.message={key:{remoteJid:'123@s.whatsapp.net'}};
await run('on');
assert.match(replies.at(-1), /inside the WhatsApp group/);
console.log('sp-command-selftest: PASS');
