export default {
  name:'groupstatus',
  aliases:['gcs'],
  description:'Show Night moderation and posting status for this group.',
  usage:'.groupstatus',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const state = await ctx.groupStatusSnapshot?.()
    if (!state) return ctx.reply('I could not read the group status.')
    const p = state.policy || {}
    return ctx.reply([
      '👥 *' + state.subject + '*',
      'Members: ' + state.participants + ' · Admins: ' + state.admins,
      'Posting: ' + (state.announcement ? 'Admins only' : 'Everyone'),
      '',
      '*Night moderation*',
      'Anti-link: ' + (p.antiLink ? 'ON' : 'OFF'),
      'Anti-tag: ' + (p.antiTag ? 'ON' : 'OFF'),
      'Anti-group-mention: ' + (p.antiGroupMention ? 'ON' : 'OFF'),
      'Anti-spam: ' + (p.antiSpam ? 'ON' : 'OFF'),
      'Filters: ' + (Array.isArray(p.filters) ? p.filters.length : 0),
      'Rules: ' + (String(p.rulesText || '').trim() ? 'SET' : 'NOT SET'),
      'Welcome: ' + (p.welcome ? 'ON' : 'OFF'),
      'Goodbye: ' + (p.goodbye ? 'ON' : 'OFF'),
      'AI greet: ' + (p.aiGreet ? 'ON' : 'OFF'),
    ].join('\n'))
  },
}
