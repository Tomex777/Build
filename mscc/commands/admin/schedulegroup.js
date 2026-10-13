import {
  addGroupSchedule, groupScheduleInfo, groupScheduleDaysLabel,
  removeGroupSchedule, setGroupScheduleTimezone, setGroupScheduleHold,
} from '../../group-schedules.js'

function render(state) {
  return [
    '🕒 *Group schedule*',
    'Timezone: *' + state.timezone + '*',
    'Manual lockdown: *' + (state.hold ? 'ON (automatic reopening paused)' : 'OFF') + '*',
    '',
    ...(state.entries.length ? state.entries.map(row=>
      row.id + ' · ' + row.action.toUpperCase() + ' ' + row.time +
      ' (' + groupScheduleDaysLabel(row.days) + ')'
    ) : ['No automated opening or closing is scheduled.']),
    '',
    'Set: .schedulegroup close 22:00 weekdays',
    'Set: .schedulegroup open 07:00 weekdays',
    'Timezone: .schedulegroup timezone Africa/Lagos',
    'Remove: .schedulegroup remove <id>',
    'Clear: .schedulegroup clear',
    'Hold: .schedulegroup hold on|off',
  ].join('\n')
}

export default {
  name:'schedulegroup',
  aliases:['groupschedule'],
  description:'Schedule automatic group opening/closing in the saved group timezone.',
  usage:'.schedulegroup [open|close <HH:MM> [days]|timezone <IANA>|hold on|off|remove <id>|clear]',
  adminOnly:true,
  async run(ctx) {
    const group = ctx.groupKey
    if (!group) return ctx.reply('This command only works in groups.')
    const action = String(ctx.args?.[0] || 'list').toLowerCase()
    if (!ctx.groupScheduleStorage) return ctx.reply('Group scheduling is unavailable.')
    const storage = ctx.groupScheduleStorage
    try {
      if (action === 'list' || action === 'status') return ctx.reply(render(groupScheduleInfo(storage,group)))
      if (!(await ctx.isBotGroupAdmin?.())) return ctx.reply('Night must be a group admin to change group schedules.')
      if (action === 'hold') {
        const value = String(ctx.args?.[1] || '').toLowerCase()
        if (!['on','off'].includes(value)) return ctx.reply('Use .schedulegroup hold on|off.')
        setGroupScheduleHold(storage,group,value === 'on')
        return ctx.reply(render(groupScheduleInfo(storage,group)))
      }
      if (action === 'timezone' || action === 'tz') {
        const tz = String(ctx.args?.[1] || '')
        if (!tz) return ctx.reply('Example: .schedulegroup timezone Africa/Lagos')
        return ctx.reply(render(setGroupScheduleTimezone(storage,group,tz)))
      }
      if (action === 'remove' || action === 'delete' || action === 'clear') {
        const id = action==='clear' ? 'all' : ctx.args?.[1]
        if (!id) return ctx.reply('Use .schedulegroup remove <id>, or .schedulegroup clear.')
        const result = removeGroupSchedule(storage,group,id)
        return ctx.reply(result ? render(groupScheduleInfo(storage,group)) : 'Schedule not found.')
      }
      if (action==='open' || action==='close') {
        const row = addGroupSchedule(storage, {
          group, accountId:ctx.account?.id, action,
          time:String(ctx.args?.[1] || ''),
          days:ctx.args?.[2] || 'daily',
        })
        const state = groupScheduleInfo(storage,group)
        return ctx.reply('✅ Group ' + action + ' scheduled for ' + row.time +
          ' (' + groupScheduleDaysLabel(row.days) + ') in ' + state.timezone + '.\n' +
          'ID: ' + row.id + '\nThe rule persists after Night restarts.')
      }
      return ctx.reply(render(groupScheduleInfo(storage,group)))
    } catch(error) {
      return ctx.reply(error?.message || 'Could not update the group schedule.')
    }
  },
}
