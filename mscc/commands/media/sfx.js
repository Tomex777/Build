import { repliedAudio, sendProcessedAudio, withMediaToolJob } from '../../utils/media-toolkit.js'
import { parseSfxRequest, renderSfx, sfxHelp } from '../../utils/sfx-engine.js'

export default {
  name: 'sfx',
  description: 'Transform a replied voice note using distinctive, offline sound effects.',
  usage: '.sfx <effect>[+effect] [intensity 0-100] (reply to audio)',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    let request
    try { request = parseSfxRequest(ctx.args || []) }
    catch (error) { return ctx.reply(error.message) }
    if (request.type === 'help') return ctx.reply(sfxHelp(prefix))
    try {
      const file = await repliedAudio(ctx)
      if (!file) return ctx.reply(`Reply to a voice note or audio file with ${prefix}sfx ${request.names.join('+')}.`)
      const result = await withMediaToolJob(() => renderSfx(file.bytes, request))
      return sendProcessedAudio(ctx, result, {
        isVoice: file.isVoice,
        caption: `🎛️ NIGHT SFX • ${request.names.join(' + ')} • ${request.intensity}%`,
      })
    } catch (error) { return ctx.reply(error?.message || 'NIGHT could not process that voice note.') }
  },
}
