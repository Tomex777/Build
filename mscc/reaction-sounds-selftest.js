import assert from 'node:assert/strict';
import { createReactionSounds, parseReactionSoundDirective, resolveReactionSound } from './reaction-sounds.js';

const GROUP = '120363424499171529@g.us';
const OTHER_GROUP = '444@g.us';
const env = {
  MSCC_REACTION_SOUNDS_ENABLED:'true',
  MSCC_REACTION_SOUNDS_AUTO:'true',
  MSCC_REACTION_SOUND_GROUPS:GROUP,
  MSCC_REACTION_SOUNDS_DIR:'/private/group-sounds',
  MSCC_REACTION_SOUND_COOLDOWN_MS:'60000',
};

assert.equal(resolveReactionSound('authoritah')?.id, 'sp_authoritah');
assert.equal(resolveReactionSound('sp_video_d')?.auto, false);
assert.equal(resolveReactionSound('../../secrets'), null);
assert.deepEqual(parseReactionSoundDirective('What?! [[night_sound:sp_cry]]'),
  { text:'What?!', id:'sp_cry' });
assert.deepEqual(parseReactionSoundDirective('Fine [[night_sound:sp_video_a]]'),
  { text:'Fine', id:'' });
assert.deepEqual(parseReactionSoundDirective('Oops [[night_sound:sp_cry]] [[night_sound:sp_laugh]]'),
  { text:'Oops', id:'' });
assert.deepEqual(parseReactionSoundDirective('Hello [[night_sound:../password]]'),
  { text:'Hello', id:'' });

let clock = 100000;
const sent = [];
const sock = { sendMessage:async (jid, payload, options) => {
  sent.push({jid, payload, options});
  return {key:{id:'sent-' + sent.length}};
}};
const sounds = createReactionSounds({
  env, now:() => clock,
  accessFile:async () => {},
  read:async path => {
    assert.ok(path.startsWith('/private/group-sounds/'));
    return Buffer.from([1, 2, 3]);
  },
});
assert.equal(sounds.enabled, true);
assert.equal(sounds.allowed(GROUP), true);
assert.equal(sounds.allowed(OTHER_GROUP), false);
assert.equal(sounds.allowed('123@s.whatsapp.net'), false);
assert.match(await sounds.promptFor(GROUP), /YOUR OWN silly blunder/);
assert.doesNotMatch(await sounds.promptFor(GROUP), /sp_video_a/);
assert.equal(await sounds.promptFor(OTHER_GROUP), '');
assert.equal((await sounds.play(sock, OTHER_GROUP, 'laugh')).reason, 'group_not_enabled');
assert.equal((await sounds.play(sock, GROUP, '../passwd')).reason, 'unknown_sound');
assert.equal((await sounds.play(sock, GROUP, 'sp_video_a', {automatic:true})).ok, false);
assert.equal((await sounds.play(sock, GROUP, 'sp_cry', {automatic:true})).ok, true);
assert.equal(sent.at(-1).payload.ptt, true);
assert.equal(sent.at(-1).payload.mimetype, 'audio/ogg; codecs=opus');
assert.equal((await sounds.play(sock, GROUP, 'sp_laugh', {automatic:true})).reason, 'automatic_not_available');
assert.equal(await sounds.promptFor(GROUP), '');
assert.equal((await sounds.play(sock, GROUP, 'sp_why_not', {automatic:false})).ok, true);
clock += 61000;
assert.match(await sounds.promptFor(GROUP), /sp_laugh/);
assert.equal((await sounds.play(sock, GROUP, 'sp_laugh', {automatic:true})).ok, true);
assert.equal(sent.length, 3);

const missing = createReactionSounds({
  env, accessFile:async () => { throw Error('ENOENT'); },
  read:async () => { throw Error('ENOENT'); },
});
assert.equal(await missing.promptFor(GROUP), '');
const off = createReactionSounds({ env:{...env, MSCC_REACTION_SOUNDS_ENABLED:'false'} });
assert.equal(off.enabled, false);
assert.equal((await off.play(sock, GROUP, 'sp_cry')).ok, false);
console.log('reaction-sounds-selftest: PASS');
