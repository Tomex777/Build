// Play a locally installed, user-supplied sound in explicitly allowlisted groups.
export default {
  name:'sp',
  aliases:['southpark'],
  description:'Play a South Park reaction voice note from the private NIGHT sound pack.',
  usage:'.sp list | .sp authoritah | .sp cry | .sp video_a',
  async run(ctx) {
    const sounds = ctx.reactionSounds;
    const jid = ctx.message?.key?.remoteJid || '';
    if (!sounds?.allowed(jid)) {
      return ctx.reply('South Park sounds are only available in groups explicitly enabled by the owner.');
    }
    const query = (ctx.args || []).join(' ').trim();
    if (!query || /^(list|help)$/i.test(query)) {
      return ctx.reply(sounds.manualList(jid));
    }
    const sock = ctx.account?.sock;
    if (!sock) return ctx.reply('WhatsApp is not connected.');
    const result = await sounds.play(sock, jid, query, { quoted:ctx.message });
    if (result.ok) return;
    if (result.reason === 'unknown_sound') return ctx.reply('Unknown sound. Use .sp list.');
    if (result.reason === 'busy') return ctx.reply('A sound is already being sent here.');
    return ctx.reply('That recording is not available on NIGHT’s private server yet.');
  },
};
