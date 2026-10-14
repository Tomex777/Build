import { downloadCommandMedia } from '../../utils/media-conversion.js'
import { imageExif, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'getexif',
  aliases:['exif'],
  description:'Read non-sensitive image EXIF metadata without exposing GPS coordinates.',
  usage:'.getexif (reply to an image)',
  async run(ctx) {
    try {
      const source=await downloadCommandMedia(ctx,['image'])
      if(!source) return ctx.reply('Reply to an image (preferably an original photo document) with .getexif.')
      const mimeType=String(source.found?.media?.mimetype||'image/jpeg')
      const info=await withExtendedMediaJob(()=>imageExif(source.buffer,{mimeType}))
      return ctx.reply(info)
    } catch(error) { return ctx.reply(error?.message||'Image metadata could not be read.') }
  },
}
