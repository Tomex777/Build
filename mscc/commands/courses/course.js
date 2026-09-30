import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'course',
  aliases: ['courses'],
  description: 'Find courses using the configured course sources.',
  usage: '.course <topic>',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'courses',
      commandName: 'course',
      args: ctx.args,
      botName: ctx.sourceBrand?.('courses') || 'Josiah',
      action: 'search',
      announceFallback: false,
    })
  },
}
