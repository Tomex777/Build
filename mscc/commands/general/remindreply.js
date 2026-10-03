import { commandText, contextInfo, quotedMessage } from '../../utils/whatsapp/messages.js'

export default {
  name:'remindreply',
  aliases:['rr'],
  description:'Reply to a message and have Night remind you about that exact message later.',
  usage:'.remindreply <duration>',
  async run(ctx) {
    const duration = ctx.parseDuration?.(ctx.args?.[0]) || 0
    if (!duration) return ctx.reply('Reply to a message with .remindreply <duration>, for example .rr 2h.')

    const info = contextInfo(ctx.message?.message)
    const quoted = quotedMessage(ctx.message, info, ctx.message?.key?.remoteJid)
    if (!quoted?.message) return ctx.reply('Reply to the message you want me to remind you about.')

    const messageText = commandText(quoted.message) || '[media/message with no text]'
    const target = await ctx.resolveCommandTarget?.('')
    const profile = target?.phoneNumber ? await ctx.getPublicUserProfile?.(target.phoneNumber) : null
    const senderName = String(profile?.displayName || target?.phoneNumber || 'Someone')
    const chatLabel = await ctx.currentChatLabel?.()

    try {
      const task = ctx.scheduleAdd?.({
        kind:'reply-reminder',
        text:messageText,
        dueAt:Date.now() + duration,
        meta:{
          senderName,
          senderPhone:String(target?.phoneNumber || ''),
          chatLabel:String(chatLabel || ''),
          messageText,
          messageId:String(quoted.key?.id || ''),
        },
      })
      return ctx.reply('⏰ I’ll remind you about that message in *' + (ctx.formatDue?.(task.dueAt) || ctx.args[0]) + '*.')
    } catch (error) {
      return ctx.reply(error?.message || 'I could not set that reply reminder.')
    }
  },
}
