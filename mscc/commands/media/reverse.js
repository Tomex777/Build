import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { reverseClip, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'reverse',
  aliases:['reversemedia'],
  description:'Reverse the first 14 seconds of a video or first 40 seconds of audio.',
  usage:'.reverse (reply to audio or video)',
  async run(ctx) {
    try {
      const source=await downloadCommandMedia(ctx,['audio','video'])
      if(!source) return ctx.reply('Reply to an audio or video clip with .reverse.')
      const kind=source.kind
      const buffer=await withExtendedMediaJob(()=>reverseClip(source.buffer,{kind}))
      const content=kind==='video'
        ? {video:buffer,mimetype:'video/mp4',caption:'🔁 Reversed video (up to 14 seconds).'}
        : {document:buffer,mimetype:'audio/mpeg',fileName:'night-reverse.mp3',
          caption:'🔁 Reversed audio (up to 40 seconds).'}
      return ctx.account.sock.sendMessage(ctx.message.key.remoteJid,content,{quoted:ctx.message})
    } catch(error) { return ctx.reply(error?.message||'Reverse conversion failed.') }
  },
}
