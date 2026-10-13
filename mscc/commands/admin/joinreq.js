function jidOf(item) {
  return String(item?.jid || item?.participant || item?.id || '').trim()
}

function formatId(jid) {
  return jid.split('@')[0].replace(/[^\d]/g,'').slice(0,20) || jid.split('@')[0].slice(0,30)
}

export function requestRows(rows) {
  return (Array.isArray(rows) ? rows : [])
    .map(row => ({ jid:jidOf(row), raw:row }))
    .filter(row => row.jid.endsWith('@s.whatsapp.net') || row.jid.endsWith('@lid'))
}

export function pickJoinRequests(rows, selector) {
  const request = String(selector || '').trim().toLowerCase()
  if (request === 'all') return rows.slice(0,50)
  const numbers = request.split(',').map(x=>x.trim())
  if (!numbers.length || numbers.some(x=>!/^\d+$/.test(x))) return []
  const indexes = [...new Set(numbers.map(x=>Number(x)-1))]
  if (indexes.some(i=>i<0 || i>=rows.length)) return []
  return indexes.map(i=>rows[i])
}

export default {
  name:'joinreq',
  aliases:['joinrequests'],
  description:'List and manually approve or reject pending WhatsApp group join requests.',
  usage:'.joinreq [list|accept <number|all>|reject <number|all>|on|off]',
  adminOnly:true,
  async run(ctx) {
    const chat = String(ctx.groupKey || '')
    if (!chat) return ctx.reply('This one is for groups.')
    try {
      if (!(await ctx.isBotGroupAdmin?.())) return ctx.reply('Night must be a group admin to manage join requests.')
      const sock = ctx.account?.sock
      const action = String(ctx.args?.[0] || 'list').toLowerCase()
      if (action === 'on' || action === 'off') {
        if (typeof sock?.groupJoinApprovalMode !== 'function') throw new Error('Join approval settings are unavailable in this WhatsApp connection.')
        await sock.groupJoinApprovalMode(chat, action)
        return ctx.reply('Group join approval is now *' + action.toUpperCase() + '*.')
      }
      if (typeof sock?.groupRequestParticipantsList !== 'function') throw new Error('Join request listing is not supported by this WhatsApp connection.')
      const rows = requestRows(await sock.groupRequestParticipantsList(chat))
      if (action === 'list' || action === 'pending') {
        return ctx.reply([
          '👥 *Pending join requests (' + rows.length + ')*',
          '',
          ...rows.slice(0,50).map((row,i)=>(i+1) + '. ' + formatId(row.jid)),
          rows.length > 50 ? 'Showing the first 50.' : '',
          '',
          'Use .joinreq accept 1, .joinreq reject 2 or .joinreq accept all.',
          'Requests are never approved automatically.',
        ].filter(Boolean).join('\n'))
      }
      if (action !== 'accept' && action !== 'approve' && action !== 'reject') {
        return ctx.reply('Use .joinreq, .joinreq accept 1, .joinreq reject 2, or .joinreq on|off.')
      }
      const targets = pickJoinRequests(rows, ctx.args?.[1])
      if (!targets.length) return ctx.reply('Request not found. Run .joinreq to see current numbers.')
      const operation = action === 'reject' ? 'reject' : 'approve'
      if (typeof sock?.groupRequestParticipantsUpdate !== 'function') throw new Error('Join request updates are unsupported.')
      let success = 0
      let failed = 0
      // Small batches avoid applying stale requests and reduce protocol pressure.
      for (let i=0;i<targets.length;i+=10) {
        const slice = targets.slice(i,i+10)
        const results = await sock.groupRequestParticipantsUpdate(chat, slice.map(x=>x.jid), operation)
        if (!Array.isArray(results)) {
          failed += slice.length
          continue
        }
        const ok = results.filter(row => String(row.status) === '200').length
        success += ok
        failed += slice.length-ok
      }
      return ctx.reply('Join requests ' + (operation==='approve' ? 'approved' : 'rejected') +
        ': *' + success + '*.' + (failed ? ' Failed: ' + failed + '.' : ''))
    } catch(error) {
      return ctx.reply(error?.message || 'Could not manage join requests.')
    }
  },
}
