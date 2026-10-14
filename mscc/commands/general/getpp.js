import { downloadImageUrl } from '../../utils/remote-image.js'

export default {
  name:'getpp',
  aliases:['getpic','gp'],
  description:'Retrieve a WhatsApp profile picture visible to the Night account.',
  usage:'.getpp (reply to a user or mention them; no target means yourself)',
  async run(ctx) {
    const raw=String(ctx.args?.[0]||'').trim()
    if(raw && !raw.startsWith('@')) {
      return ctx.reply('Use .getpp, reply to a message with .getpp, or mention somebody. Phone-number lookups are disabled.')
    }
    if(raw.startsWith('@') && !ctx.message?.message) return ctx.reply('Mention a WhatsApp participant with .getpp.')
    const resolved=typeof ctx.resolveCommandTarget==='function'
      ? await ctx.resolveCommandTarget(raw)
      : {phoneNumber:''}
    let phone=String(resolved?.phoneNumber||'').trim()
    if(!phone) {
      if(raw) return ctx.reply('I could not resolve that mention. Reply to their message instead.')
      // A quote without a resolvable sender must not accidentally return the
      // requesting user's picture.
      if(resolved?.source==='reply') return ctx.reply('I could not identify the quoted user.')
      phone=String(ctx.userKey||'').trim()
    }
    if(!/^\d{7,15}$/.test(phone)) return ctx.reply('Could not resolve the WhatsApp profile.')
    if(typeof ctx.getPublicUserProfile!=='function') return ctx.reply('Profile lookup is unavailable.')
    try {
      const profile=await ctx.getPublicUserProfile(phone)
      if(!profile?.photoUrl) {
        return ctx.reply('That profile picture is private, unavailable or not visible to Night.')
      }
      const image=await downloadImageUrl(profile.photoUrl)
      if(!image?.buffer?.length) throw new Error('The profile picture could not be downloaded.')
      const chat=ctx.message?.key?.remoteJid
      const sock=ctx.account?.sock
      if(!chat||!sock) return ctx.reply('WhatsApp connection is unavailable.')
      const name=String(profile.displayName||'WhatsApp user').slice(0,60)
      return sock.sendMessage(chat,{
        image:image.buffer,
        caption:'👤 Profile picture · '+name,
      },{quoted:ctx.message})
    } catch(error) {
      console.warn('Night getpp failed:',error?.message||error)
      return ctx.reply('That profile picture is private, unavailable or could not be downloaded.')
    }
  },
}
