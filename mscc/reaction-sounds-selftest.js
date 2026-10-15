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

const {mkdtemp, readdir, readFile, rm} = await import('node:fs/promises');
const {tmpdir} = await import('node:os');
const {join} = await import('node:path');
const {default:AdmZip} = await import('adm-zip');

const temp = await mkdtemp(join(tmpdir(), 'night-sounds-test-'));
try {
  const settings = new Map();
  const dynamic = createReactionSounds({
    env:{
      MSCC_REACTION_SOUNDS_ENABLED:'false',
      MSCC_REACTION_SOUNDS_DIR:join(temp, 'clips'),
    },
    groupState:jid => settings.get(jid) || null,
  });
  assert.equal(dynamic.allowed(GROUP), false);
  settings.set(GROUP, {enabled:true, auto:true});
  assert.equal(dynamic.allowed(GROUP), true);
  assert.equal(dynamic.allowed(OTHER_GROUP), false);
  const zip = new AdmZip();
  const fakeOgg = Buffer.concat([
    Buffer.from('OggS'), Buffer.alloc(22), Buffer.from('OpusHead'), Buffer.alloc(30),
  ]);
  zip.addFile('NIGHT_SouthPark_Reactions_v2/audio/sp_cry.ogg', fakeOgg);
  zip.addFile('NIGHT_SouthPark_Reactions_v2/audio/sp_laugh.ogg', fakeOgg);
  zip.addFile('../not-allowed/private.env', Buffer.from('secret'));
  const imported = await dynamic.importPack(zip.toBuffer());
  assert.equal(imported.count, 2);
  assert.deepEqual((await readdir(join(temp, 'clips'))).sort(), ['sp_cry.ogg','sp_laugh.ogg']);
  assert.deepEqual(await readFile(join(temp,'clips','sp_cry.ogg')), fakeOgg);
  assert.match(await dynamic.promptFor(GROUP), /sp_cry/);
  const secondZip = new AdmZip();
  secondZip.addFile('audio/sp_sorry.ogg', fakeOgg);
  await dynamic.importPack(secondZip.toBuffer());
  assert.deepEqual((await readdir(join(temp, 'clips'))).sort(),
    ['sp_cry.ogg','sp_laugh.ogg','sp_sorry.ogg']);
  settings.set(GROUP, {enabled:false, auto:false});
  assert.equal(dynamic.allowed(GROUP), false);
  const invalidZip = new AdmZip();
  invalidZip.addFile('audio/sp_laugh.ogg', Buffer.from('not-an-ogg'));
  await assert.rejects(dynamic.importPack(invalidZip.toBuffer()), /Ogg\/Opus/);
  await assert.rejects(dynamic.importPack(Buffer.alloc(6 * 1024 * 1024)), /5 MB/);
} finally {
  await rm(temp, {recursive:true,force:true});
}
console.log('reaction-sounds-selftest: PASS');
