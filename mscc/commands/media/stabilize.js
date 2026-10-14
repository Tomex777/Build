import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { stabilizeVideo, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'stabilize',
  aliases:['deshake'],
  description:'Reduce shaky camera movements in a video (first 24 seconds).',
  usage:'.stabilize (reply to a video)',
  async run(ctx) {
    try {
      const source=await downloadCommandMedia(ctx,['video'])
      if(!source) return ctx.reply('Reply to a video with .stabilize.')
      const result=await withExtendedMediaJob(()=>stabilizeVideo(source.buffer))
      return ctx.account.sock.sendMessage(ctx.message.key.remoteJid,{
        video:result.buffer,mimetype:'video/mp4',
        caption:'🎬 Video stabilized with '+result.method+'.',
      },{quoted:ctx.message})
    } catch(error) { return ctx.reply(error?.message||'Video stabilization failed.') }
  },
}
