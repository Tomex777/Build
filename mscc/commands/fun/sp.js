// Private South Park reactions. One-command owner setup from WhatsApp; no SSH required.
import { downloadCommandMedia, resolveCommandMedia } from '../../utils/media-conversion.js';

const group = jid => /^[^@\s]+@g\.us$/.test(String(jid || ''));
const setting = (ctx, jid) => ctx.shared?.get?.('reaction-sound-groups', jid) || null;
const formatStatus = config => {
  if (!config?.enabled) return 'Off';
  return config.auto === true ? 'On · AI reactions enabled' : 'On · manual clips only';
};
const OWNER_ACTIONS = new Set(['on','off','enable','disable','auto','import','install','status']);

export default {
  name:'sp',
  aliases:['southpark'],
  description:'South Park soundboard; owner can securely import ZIP audio and enable private-group AI reactions.',
  usage:'.sp list | .sp authoritah | .sp cry | .sp on | .sp off | .sp auto on|off | .sp import',
  async run(ctx) {
    const sounds = ctx.reactionSounds;
    const jid = ctx.message?.key?.remoteJid || '';
    const query = String(ctx.args?.[0] || 'list').trim().toLowerCase();
    const isOwnerAction = OWNER_ACTIONS.has(query);

    if (isOwnerAction) {
      if (ctx.isSupremeOwner !== true)
        return ctx.reply('Only the primary NIGHT owner can configure or import reaction sounds.');
      if (query === 'import' || query === 'install') {
        const resolved = resolveCommandMedia(ctx, ['document']);
        if (!resolved || resolved.kind !== 'document') {
          return ctx.reply('Send the private NIGHT South Park ZIP as a WhatsApp document with caption .sp import, or reply to the ZIP with .sp import.');
        }
        const size = Number(resolved.found?.media?.fileLength || 0);
        if (size > 5 * 1024 * 1024) return ctx.reply('The audio ZIP must be 5 MB or smaller.');
        try {
          const source = await downloadCommandMedia(ctx, ['document']);
          if (!source?.buffer) throw new Error('Could not download the attached ZIP.');
          const imported = await sounds.importPack(source.buffer);
          // Installing clips in a DM stays private; enable only in a group.
          if (group(jid)) ctx.shared?.set?.('reaction-sound-groups', jid, { enabled:true, auto:true });
          return ctx.reply('✅ Installed ' + imported.count + ' private voice recordings.' +
            (group(jid) ? '\nSouth Park reactions are enabled in this group, including optional AI-selected sounds.' :
              '\nGo to your WhatsApp group and send .sp on to enable them there.'));
        } catch (error) {
          return ctx.reply('Sound import failed: ' + (error?.message || 'invalid audio ZIP.'));
        }
      }
      if (!group(jid)) return ctx.reply('Use .sp on, .sp off, .sp auto or .sp status inside the WhatsApp group.');
      const prior = setting(ctx, jid);
      if (query === 'status') {
        return ctx.reply('🎙️ South Park sounds: ' + formatStatus(prior) +
          '\nStored clips: private server directory. Use .sp list to browse after enabling.');
      }
      let next;
      if (query === 'on' || query === 'enable') next = {enabled:true, auto:true};
      else if (query === 'off' || query === 'disable') next = {enabled:false, auto:false};
      else {
        const mode = String(ctx.args?.[1] || '').trim().toLowerCase();
        if (!['on','off'].includes(mode)) return ctx.reply('Use .sp auto on or .sp auto off.');
        next = { enabled:prior?.enabled !== false, auto:mode === 'on' };
      }
      ctx.shared?.set?.('reaction-sound-groups', jid, next);
      return ctx.reply('🎙️ South Park sounds: ' + formatStatus(next) +
        (next.enabled ? '\nTry .sp list or .sp authoritah.' : ''));
    }

    if (!sounds?.allowed(jid))
      return ctx.reply('South Park sounds are disabled here. The NIGHT owner can turn them on with .sp on.');
    if (query === 'list' || query === 'help') return ctx.reply(sounds.manualList(jid, ctx.publicPrefix || '.'));
    const sock = ctx.account?.sock;
    if (!sock) return ctx.reply('WhatsApp is not connected.');
    const result = await sounds.play(sock, jid, (ctx.args || []).join(' '), { quoted:ctx.message });
    if (result.ok) return;
    if (result.reason === 'unknown_sound') return ctx.reply('Unknown sound. Use .sp list.');
    if (result.reason === 'busy') return ctx.reply('Another clip is already being sent here.');
    return ctx.reply('That private recording is unavailable. The owner can import the ZIP with .sp import.');
  },
};
