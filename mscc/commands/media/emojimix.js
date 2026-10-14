import { emojiPairFromText, combinedEmojiSticker, withExtendedMediaJob } from '../../utils/extended-media.js'

export default {
  name:'emojimix',
  aliases:['emojicombo'],
  description:'Combine two Twemoji illustrations in a single rounded WhatsApp sticker.',
  usage:'.emojimix 😍 🔥',
  async run(ctx) {
    try {
      const pair=emojiPairFromText(ctx.args?.join(' ')||'')
      const sticker=await withExtendedMediaJob(()=>combinedEmojiSticker(pair))
      return ctx.account.sock.sendMessage(ctx.message.key.remoteJid,{sticker},{quoted:ctx.message})
    } catch(error) { return ctx.reply(error?.message||'Emoji combination failed.') }
  },
}
