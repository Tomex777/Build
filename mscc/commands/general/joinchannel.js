import { ChannelError } from '../../night-play-channels.js'

export default {
  name:'joinchannel',
  aliases:['channeljoin'],
  description:'Connect a WhatsApp Channel to your NIGHT Play account, with consent and verified admin permissions.',
  usage:'.joinchannel <channel link|yes|no|verify|list>',
  async run(ctx) {
    const chat=String(ctx.message?.key?.remoteJid||'')
    if(ctx.groupKey||(!chat.endsWith('@s.whatsapp.net')&&!chat.endsWith('@lid')&&!chat.endsWith('@hosted.lid'))) {
      return ctx.reply('For channel security, message NIGHT privately. Never post a channel verification code in a group.')
    }
    const action=String(ctx.args?.[0]||'').toLowerCase()
    const usage='*NIGHT Play · Channels*\nSend the WhatsApp Channel link here first, then type .joinchannel. Alternatively, use .joinchannel <channel link>.\nYou will confirm Yes or No before NIGHT follows the channel. Connecting it to NIGHT Play also requires granting NIGHT admin rights in WhatsApp and proving you can publish to the channel.'
    try {
      const recent=!action?ctx.channelRecentLink?.():''
      if(!action&&!recent)return ctx.reply(usage)
      if(action==='yes') {
        const result=await ctx.channelAccept()
        if(result.already)return ctx.reply('*Already connected*\n'+result.name+'\nManage it in NIGHT Play.')
        return ctx.reply('*'+result.name+'*\nYou agreed to let NIGHT follow this channel for NIGHT Play.\n\n*Next:* In WhatsApp, invite this NIGHT account as a channel admin and accept the invitation there. Following alone does not give NIGHT posting permission.\n\nAfter NIGHT becomes an admin, publish this one-time text as a channel post:\n\n'+result.proof+'\n\nThen send *.joinchannel verify* here. Code expires after 24 hours. If the channel already has NIGHT as an admin, you can go straight to posting the code.')
      }
      if(action==='no'||action==='cancel') {
        ctx.channelCancel()
        return ctx.reply('Cancelled. NIGHT has not followed the requested channel.')
      }
      if(action==='verify') {
        const result=await ctx.channelVerify()
        if(result.connected)return ctx.reply('*Channel connected to NIGHT Play*\n'+result.name+'\n\nOpen NIGHT Play → Your Channels to manage it. Publishing remains unavailable until Anonymous Nights goes live.')
        if(result.reason==='admin')return ctx.reply('*Waiting for channel admin permission*\n'+result.name+'\nFollowing is not admin access. Invite this NIGHT WhatsApp account as a channel admin, approve it inside WhatsApp, then send *.joinchannel verify* again.\n\nYour proof code is: '+result.proof)
        return ctx.reply('*Admin access verified, ownership proof still needed*\nPublish this exact code as a new post in your channel:\n'+result.proof+'\n\nThen send *.joinchannel verify* again.')
      }
      if(action==='list'){
        const result=ctx.channelList()
        if(!result.channels.length)return ctx.reply('No channels linked yet. '+usage)
        return ctx.reply('*Your NIGHT Play channels*\n'+result.channels.map(r=>'• '+r.name+' — '+(r.status==='connected'?'Verified':r.status==='awaiting_admin'?'Waiting for NIGHT admin rights':'Waiting for your proof post')).join('\n')+'\n\nManage these on NIGHT Play.')
      }
      const data=await ctx.channelBegin(recent||ctx.args.join(' ').trim())
      const notice='*Connect '+data.name+'?*\n\nNIGHT will follow this WhatsApp Channel only if you agree. Following is not becoming an admin.\n\nTo let NIGHT publish approved Anonymous Nights cards later, you must separately invite this NIGHT account as a WhatsApp Channel administrator. You must also prove that you can publish to it.\n\nReply .joinchannel yes to agree or .joinchannel no to cancel. Confirmation expires in 15 minutes.'
      if(ctx.ui?.acceptDecline) {
        try {
          return await ctx.ui.acceptDecline({
            title:'NIGHT Channels · Consent',text:notice,buttonText:'Yes or No',
            acceptId:'.joinchannel yes',declineId:'.joinchannel no',
            acceptText:'Yes, follow channel',declineText:'No, cancel',
          })
        } catch { /* Older WhatsApp clients still receive the text flow. */ }
      }
      return ctx.reply(notice)
    } catch(error) {
      return ctx.reply(error instanceof ChannelError ? error.message : 'WhatsApp could not complete channel onboarding. Please retry later.')
    }
  },
}
