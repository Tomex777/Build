import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { bestVideoFrames, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'bestframe',
  aliases:['sharpframe'],
  description:'Pick the sharpest frames from the first 24 seconds of a video.',
  usage:'.bestframe [1-5] (reply to a video)',
  async run(ctx) {
    try {
      const count=ctx.args?.length?Number(ctx.args[0]):1
      if (!Number.isInteger(count)||count<1||count>5) return ctx.reply('Use .bestframe or .bestframe 2 to 5.')
      const source=await downloadCommandMedia(ctx,['video'])
      if(!source) return ctx.reply('Reply to a video with .bestframe [1-5].')
      const frames=await withExtendedMediaJob(()=>bestVideoFrames(source.buffer,count))
      for(const row of frames) {
        await ctx.account.sock.sendMessage(ctx.message.key.remoteJid,{
          image:row.buffer,mimetype:'image/jpeg',
          caption:'📷 Best frame at ~'+row.at.toFixed(1)+'s (sharpness-ranked).',
        },{quoted:ctx.message})
      }
    } catch(error) { return ctx.reply(error?.message||'Frame extraction failed.') }
  },
}
