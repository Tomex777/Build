function normalizeFilters(value) {
  return Array.isArray(value) ? value.filter(item => item?.trigger) : []
}

function parseAdd(args) {
  const joined = args.join(' ').trim()
  const parts = joined.split('|').map(value => value.trim())
  if (parts.length < 2) return null

  const trigger = parts.shift()
  const actionText = parts.join('|').trim()
  if (!trigger || !actionText) return null

  const lower = actionText.toLowerCase()
  if (lower === 'delete') return { trigger, action:'delete', response:'' }
  if (lower === 'warn') return { trigger, action:'warn', response:'' }
  if (lower.startsWith('reply ')) {
    return { trigger, action:'reply', response:actionText.slice(6).trim() }
  }
  if (lower.startsWith('reply:')) {
    return { trigger, action:'reply', response:actionText.slice(6).trim() }
  }
  return { trigger, action:'reply', response:actionText }
}

export default {
  name:'filter',
  aliases:['filters'],
  description:'Create group trigger rules that delete, warn, or auto-reply.',
  usage:'.filter [add <trigger> | <delete|warn|reply text> | remove <number|trigger> | clear]',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')

    const policy = ctx.groupPolicyGet?.() || {}
    const filters = normalizeFilters(policy.filters)
    const action = String(ctx.args?.[0] || '').toLowerCase()

    if (!action || action === 'list') {
      if (!filters.length) return ctx.reply('No custom group filters are set.')
      return ctx.reply([
        '🧰 *Group Filters*',
        '',
        ...filters.map((item,index) =>
          (index + 1) + '. *' + item.trigger + '* → ' +
          (item.action === 'reply' ? 'reply: ' + item.response : item.action)
        ),
      ].join('\n'))
    }

    if (action === 'clear') {
      ctx.groupPolicySet?.({ filters:[] })
      return ctx.reply('All custom group filters cleared.')
    }

    if (action === 'remove' || action === 'delete') {
      const wanted = ctx.args.slice(1).join(' ').trim()
      if (!wanted) return ctx.reply('Use .filter remove <number|trigger>.')
      let next = filters
      const index = Number(wanted)
      if (Number.isInteger(index) && index >= 1 && index <= filters.length) {
        next = filters.filter((_,i) => i !== index - 1)
      } else {
        next = filters.filter(item => String(item.trigger).toLowerCase() !== wanted.toLowerCase())
      }
      if (next.length === filters.length) return ctx.reply('I could not find that filter.')
      ctx.groupPolicySet?.({ filters:next })
      return ctx.reply('Filter removed.')
    }

    const input = action === 'add' ? ctx.args.slice(1) : ctx.args
    const item = parseAdd(input)
    if (!item || (item.action === 'reply' && !item.response)) {
      return ctx.reply([
        'Use one of these:',
        '.filter add spamword | delete',
        '.filter add spamword | warn',
        '.filter add hello night | reply Hey 👋',
      ].join('\n'))
    }

    const duplicate = filters.findIndex(row => String(row.trigger).toLowerCase() === item.trigger.toLowerCase())
    const next = [...filters]
    if (duplicate >= 0) next[duplicate] = item
    else next.push(item)
    ctx.groupPolicySet?.({ filters:next.slice(0,100) })
    return ctx.reply('Filter saved: *' + item.trigger + '* → ' + item.action + '.')
  },
}
