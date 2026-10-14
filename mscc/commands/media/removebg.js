import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { removeImageBackground, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'removebg',
  aliases:['nobg'],
  description:'Remove an image background locally; return a transparent PNG document.',
  usage:'.removebg (reply to an image)',
  async run(ctx) {
    try {
      const source=await downloadCommandMedia(ctx,['image'])
      if(!source) return ctx.reply('Reply to an image with .removebg.')
      const buffer=await withExtendedMediaJob(()=>removeImageBackground(source.buffer))
      return ctx.account.sock.sendMessage(ctx.message.key.remoteJid,{
        document:buffer,mimetype:'image/png',fileName:'night-transparent.png',
        caption:'✨ Background removed. PNG transparency preserved.'
      },{quoted:ctx.message})
    } catch(error) { return ctx.reply(error?.message||'Background removal failed.') }
  },
}
