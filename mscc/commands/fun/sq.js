import { contextInfo } from '../../utils/whatsapp/messages.js'
import { downloadImageUrl } from '../../utils/remote-image.js'
import { extractQuotedCapture, renderScreenshotQuote } from '../../utils/screenshot-quote.js'

const THEMES=new Set(['light','dark'])

export default {
  name:'sq',
  aliases:['screenshotquote','quotepic','qpic'],
  description:'Turn a genuine replied WhatsApp message into a polished Night quote image.',
  usage:'.sq [dark|light] (reply to a message)',
  async run(ctx) {
    const theme=String(ctx.args?.[0]||'dark').toLowerCase()
    if(!THEMES.has(theme)) return ctx.reply('Use .sq or .sq light as a reply to a message.')
    const quoted=extractQuotedCapture(ctx.message)
    if(!quoted) return ctx.reply('💬 Reply to a message with .sq or .sq light.')
    if(quoted.error) return ctx.reply(quoted.error)
    try {
      const info=contextInfo(ctx.message?.message)
      // The reply's participant is authoritative for quoted-message authorship.
      // Never replace it with an arbitrary @mention supplied in command text.
      const target=typeof ctx.resolveCommandTarget==='function'
        ? await ctx.resolveCommandTarget('')
        : {phoneNumber:''}
      const phone=String(target?.phoneNumber||'')
      let profile=null
      if(phone && typeof ctx.getPublicUserProfile==='function') {
        profile=await ctx.getPublicUserProfile(phone)
      }
      const fallback=String(quoted.participant||'').split('@')[0]
      const authorName=String(profile?.displayName||fallback||'Quoted sender').slice(0,60)
      let avatarBuffer=null
      const avatarUrl=String(profile?.photoUrl||'')
      if(avatarUrl) {
        try {avatarBuffer=(await downloadImageUrl(avatarUrl))?.buffer||null} catch {}
      }
      const image=await renderScreenshotQuote({
        text:quoted.text,
        authorName,
        avatarBuffer,
        theme,
        mediaKind:quoted.kind,
        truncated:quoted.truncated,
      })
      const chat=ctx.message?.key?.remoteJid
      const sock=ctx.account?.sock
      if(!chat||!sock) return ctx.reply('WhatsApp connection is unavailable.')
      return sock.sendMessage(chat,{image,mimetype:'image/png'},{quoted:ctx.message})
    } catch(error) {
      console.warn('Night screenshot quote failed:',error?.message||error)
      return ctx.reply('I could not render the quoted message.')
    }
  },
}
