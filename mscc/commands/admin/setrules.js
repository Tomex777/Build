function bodyFromRaw(ctx) {
  const raw = String(ctx.rawCommandText || '')
  if (!raw) return ctx.args.join(' ').trim()
  const prefix = String(ctx.publicPrefix || '.')
  const start = raw.toLowerCase().indexOf((prefix + 'setrules').toLowerCase())
  if (start < 0) return ctx.args.join(' ').trim()
  return raw.slice(start + prefix.length + 'setrules'.length).trim()
}

export default {
  name:'setrules',
  description:'Set or clear the rules Night shows in this group and sends to new members.',
  usage:'.setrules <rules|clear>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const body = bodyFromRaw(ctx)
    if (!body) return ctx.reply('Use .setrules <rules>. Multiline rules are supported.')
    if (body.toLowerCase() === 'clear') {
      ctx.groupPolicySet?.({ rulesText:'' })
      return ctx.reply('Group rules cleared.')
    }
    ctx.groupPolicySet?.({ rulesText:body.slice(0,6000) })
    return ctx.reply('Group rules updated. New members will receive them automatically.')
  },
}
