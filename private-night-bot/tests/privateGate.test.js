'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const gate = require('../core/privateGate');

const OWNER = '2348064790531@s.whatsapp.net';
const OTHER = '2348000000000@s.whatsapp.net';
const GROUP = '120363000000000000@g.us';
const sock = { user: { id: '2348064790531:4@s.whatsapp.net' } };
const checks = {
  isOwner: jid => String(jid || '').replace(/:\\d+/, '').split('@')[0] === OWNER.split('@')[0],
  resolveOwnerJid: () => OWNER
};
const msg = (remoteJid, body, options = {}) => ({
  key: {
    remoteJid,
    id: options.id || 'test-message',
    fromMe: Boolean(options.fromMe),
    ...(options.participant ? { participant: options.participant } : {})
  },
  message: options.message || { conversation: body },
  messageTimestamp: Math.floor(Date.now() / 1000)
});

test('owner command in a group is rerouted to owner DM and marked hidden', () => {
  const result = gate.routeMessage(sock, msg(GROUP, '.play hello by Lionel Richie', { participant: OWNER }), checks);
  assert.equal(result.allowed, true);
  assert.equal(result.routed, true);
  assert.equal(result.message.key.remoteJid, OWNER);
  assert.equal(result.message.__privateNightRouted, true);
  assert.equal(result.message.message.conversation, '.play hello by Lionel Richie');
});

test('owner command in another private chat is rerouted to owner DM', () => {
  const result = gate.routeMessage(sock, msg(OTHER, '.play hello', { fromMe: true }), checks);
  assert.equal(result.allowed, true);
  assert.equal(result.routed, true);
  assert.equal(result.message.key.remoteJid, OWNER);
});

test('owner command in owner DM stays in owner DM', () => {
  const result = gate.routeMessage(sock, msg(OWNER, '.menu'), checks);
  assert.equal(result.allowed, true);
  assert.equal(result.routed, false);
  assert.equal(result.message.key.remoteJid, OWNER);
});

test('non-owner command is silently denied in group and DM', () => {
  assert.equal(gate.routeMessage(sock, msg(GROUP, '.play song', { participant: OTHER }), checks).allowed, false);
  assert.equal(gate.routeMessage(sock, msg(OTHER, '.play song'), checks).allowed, false);
});

test('a secondary entry in the legacy owner list is denied by the private gate', () => {
  const secondaryChecks = { isOwner: () => true, resolveOwnerJid: () => OTHER };
  assert.equal(gate.routeMessage(sock, msg(OTHER, '.play song'), secondaryChecks).allowed, false);
});

test('ordinary text and AI-style prompts are denied even from owner', () => {
  assert.equal(gate.routeMessage(sock, msg(OWNER, 'hello Private Night'), checks).allowed, false);
  assert.equal(gate.routeMessage(sock, msg(GROUP, '@Private Night what do you think?', { participant: OWNER }), checks).allowed, false);
});

test('button replies are allowed only inside owner DM; group events are always disabled', () => {
  const button = { buttonsResponseMessage: { selectedButtonId: 'btn_menu' } };
  assert.equal(gate.routeMessage(sock, msg(OWNER, '', { message: button }), checks).allowed, true);
  assert.equal(gate.routeMessage(sock, msg(GROUP, '', { participant: OWNER, message: button }), checks).allowed, false);
  assert.equal(gate.allowGroupEvents(), false);
});

test('rerouted sendMessage calls go only to owner DM, strips quotes, and suppresses reactions', async () => {
  const calls = [];
  const fakeSock = {
    sendMessage: async (jid, content, options) => { calls.push({ jid, content, options }); return { ok: true }; },
    user: sock.user
  };
  const wrapped = gate.createOwnerDmSocket(fakeSock, OWNER);
  await wrapped.sendMessage(GROUP, { text: 'private result' }, { quoted: { key: { id: 'public-id' } }, ephemeralExpiration: 60 });
  await wrapped.sendMessage(GROUP, { react: { text: '✅', key: { id: 'public-id' } } });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].jid, OWNER);
  assert.deepEqual(calls[0].content, { text: 'private result' });
  assert.equal('quoted' in calls[0].options, false);
});
