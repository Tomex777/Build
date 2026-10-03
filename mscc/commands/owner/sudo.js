function masked(value) {
  const phone = String(value || '').replace(/\D/g, '')
  if (!phone) return ''
  if (phone.length < 8) return phone
  return phone.slice(0,3) + '••••' + phone.slice(-4)
}

export default {
  name:'sudo',
  description:'Manage users who may run Night public owner commands.',
  usage:'.sudo <add|remove|list> [@user]',
  ownerOnly:true,
  async run(ctx) {
    if (ctx.isSupremeOwner !== true) {
      return ctx.reply('Only the primary Night owner can change sudo access.')
    }

    const action = String(ctx.args?.[0] || 'list').trim().toLowerCase()
    if (action === 'list') {
      const rows = ctx.sudoList?.() || []
      if (!rows.length) return ctx.reply('No public sudo users are configured.')
      return ctx.reply([
        '🔐 *Night public sudo*',
        ...rows.map((phone,index) => (index + 1) + '. ' + masked(phone)),
        '',
        'Sudo grants public owner commands only. Private control stays owner-only.',
      ].join('\n'))
    }

    if (!['add','remove','delete'].includes(action)) {
      return ctx.reply('Use .sudo add @user, .sudo remove @user, or .sudo list.')
    }

    const raw = String(ctx.args?.[1] || '').trim()
    try {
      const result = await ctx.sudoSet?.(raw, action === 'add')
      return ctx.reply(
        (action === 'add' ? 'Added ' : 'Removed ') +
        masked(result?.phoneNumber) +
        (action === 'add' ? ' as a public sudo user.' : ' from public sudo.')
      )
    } catch (error) {
      return ctx.reply(error?.message || 'I could not update sudo access.')
    }
  },
}
