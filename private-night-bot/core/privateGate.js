'use strict';

/**
 * Private Night hard gate.
 *
 * Only the configured primary owner may invoke commands. Ordinary chat,
 * AI mentions, group messages from anybody else, and commands from anybody
 * except the owner are silently ignored. A command sent by the owner outside
 * the owner's own DM is retargeted to that owner's DM before any processing;
 * the original chat never receives a reply. This is intentionally not a
 * registered command and therefore does not appear in the menu.
 */
const config = require('../config');

const isLocked = () => true;
const groupsAllowed = () => false;

function unwrap(message) {
  let value = message?.message || {};
  for (const wrapper of ['ephemeralMessage', 'viewOnceMessageV2', 'viewOnceMessage', 'documentWithCaptionMessage']) {
    if (value?.[wrapper]?.message) value = value[wrapper].message;
  }
  return value;
}

function getBody(msg) {
  const content = unwrap(msg);
  return String(
    content.conversation ||
    content.extendedTextMessage?.text ||
    content.imageMessage?.caption ||
    content.videoMessage?.caption ||
    content.documentMessage?.caption ||
    content.buttonsResponseMessage?.selectedButtonId ||
    content.listResponseMessage?.singleSelectReply?.selectedRowId ||
    content.templateButtonReplyMessage?.selectedId ||
    ''
  ).trim();
}

function isCommand(msg) {
  const body = getBody(msg);
  const prefix = String(config.prefix || '.');
  return body.startsWith(prefix) && body.slice(prefix.length).trim().length > 0;
}

function isInteractiveReply(msg) {
  const content = unwrap(msg);
  return Boolean(
    content.buttonsResponseMessage ||
    content.listResponseMessage ||
    content.templateButtonReplyMessage ||
    content.interactiveResponseMessage
  );
}

function senderJid(sock, msg) {
  if (msg?.key?.fromMe) {
    return String(sock?.user?.id || '').split(':')[0];
  }
  return String(msg?.key?.participant || msg?.key?.remoteJid || '').split(':')[0];
}

function jidEquals(a, b) {
  if (!a || !b) return false;
  const normal = value => String(value).split(':')[0].replace(/@c\.us$/, '@s.whatsapp.net').toLowerCase();
  return normal(a) === normal(b);
}

// ownerNumber may historically contain more than one trusted number. Private
// Night intentionally honors only the first real phone number in that list.
// Placeholder entries (for example, '91xxxxxxxxxxx') are ignored. This keeps
// legacy owner checks from accidentally granting another person access.
function configuredPrimaryOwnerJid() {
  const configured = Array.isArray(config.ownerNumber) ? config.ownerNumber : [config.ownerNumber];
  for (const item of configured) {
    const digits = String(item || '').split('@')[0].replace(/[^0-9]/g, '');
    if (digits.length >= 10 && digits.length <= 15) return `${digits}@s.whatsapp.net`;
  }
  return null;
}

function resolvePrimaryOwnerJid(sender, checks = {}) {
  if (typeof checks.isOwner !== 'function' || !checks.isOwner(sender)) return null;
  if (typeof checks.resolveOwnerJid !== 'function') return null;
  const resolved = checks.resolveOwnerJid(sender);
  const primary = configuredPrimaryOwnerJid();
  return resolved && primary && jidEquals(resolved, primary) ? primary : null;
}

/**
 * The owner identity resolver is supplied by handler.js so WhatsApp LID
 * senders resolve to the configured phone number, not an ambiguous LID.
 */
function routeMessage(sock, msg, checks = {}) {
  const from = msg?.key?.remoteJid || '';
  if (!from || from.includes('@broadcast') || from.includes('@newsletter')) {
    return { allowed: false };
  }

  const sender = senderJid(sock, msg);
  const ownerJid = resolvePrimaryOwnerJid(sender, checks);
  if (!ownerJid) return { allowed: false };

  const command = isCommand(msg);
  const interactive = isInteractiveReply(msg);
  const isGroup = from.endsWith('@g.us');
  const isOwnerDm = !isGroup && jidEquals(from, ownerJid);

  // Interactive menu/button responses are supported only in the owner's DM.
  if (interactive && !isOwnerDm) return { allowed: false };
  // No open-ended AI chat, greetings, or other ordinary message responses.
  if (!command && !interactive) return { allowed: false };

  if (isOwnerDm) return { allowed: true, message: msg, routed: false, ownerJid };

  // Reroute only owner-issued commands. Clone rather than mutate the event
  // object so no other Baileys listener can confuse the source chat.
  const routed = {
    ...msg,
    key: {
      ...msg.key,
      remoteJid: ownerJid,
      participant: undefined,
      remoteJidAlt: undefined
    },
    __privateNightRouted: true,
    __privateNightOwnerJid: ownerJid
  };
  return { allowed: true, message: routed, routed: true, ownerJid };
}

/** Defense-in-depth check inside handler.js, after index.js has gated/routed. */
function isAllowed(sock, msg, checks = {}) {
  const from = msg?.key?.remoteJid || '';
  if (!from || from.endsWith('@g.us')) return false;
  const sender = senderJid(sock, msg);
  const ownerJid = resolvePrimaryOwnerJid(sender, checks);
  if (!ownerJid || !jidEquals(from, ownerJid)) return false;
  return isCommand(msg) || isInteractiveReply(msg);
}

/**
 * During a rerouted command, funnel every command-generated sendMessage call
 * into the owner DM and remove invalid quote/reaction references to the
 * original public message. Non-message socket APIs remain bound to Baileys.
 */
function createOwnerDmSocket(sock, ownerJid) {
  return new Proxy(sock, {
    get(target, property) {
      if (property === 'sendMessage') {
        return async (_requestedJid, content, options = {}) => {
          if (content?.react) return undefined;
          const safeOptions = { ...(options || {}) };
          delete safeOptions.quoted;
          return target.sendMessage(ownerJid, content, safeOptions);
        };
      }
      const value = Reflect.get(target, property, target);
      return typeof value === 'function' ? value.bind(target) : value;
    }
  });
}

function allowGroupEvents() {
  return false;
}

module.exports = {
  isLocked,
  groupsAllowed,
  isAllowed,
  routeMessage,
  createOwnerDmSocket,
  allowGroupEvents,
  getBody,
  isCommand
};
