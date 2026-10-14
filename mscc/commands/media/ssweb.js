import { captureWebsite } from '../../utils/website-snapshot.js'
import { withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'ssweb',
  aliases:['sitephoto'],
  description:'Capture a public allowlisted webpage without exposing the Night host.',
  usage:'.ssweb https://example.com',
  async run(ctx) {
    try {
      const url=String(ctx.args?.[0]||'')
      const result=await withExtendedMediaJob(()=>captureWebsite(url))
      return ctx.account.sock.sendMessage(ctx.message.key.remoteJid,{
        image:result.buffer,mimetype:'image/jpeg',
        caption:'🌐 Website screenshot: '+result.url.slice(0,180),
      },{quoted:ctx.message})
    } catch(error) { return ctx.reply(error?.message||'Website screenshot failed.') }
  },
}
