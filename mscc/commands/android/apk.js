import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'apk',
  aliases: ['app', 'apps', 'android'],
  description: 'Find Android apps and APKs using the configured Android sources.',
  usage: '.apk <app name>',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'android',
      commandName: 'apk',
      args: ctx.args,
      botName: ctx.sourceBrand?.('android') || 'Josiah',
      action: 'search',
      announceFallback: false,
    })
  },
}
