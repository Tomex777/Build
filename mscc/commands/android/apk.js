import { runApkCommand } from '../../apk-flow.js'

export default {
  name: 'apk',
  aliases: ['app', 'apps', 'android'],
  description: 'Search Android apps, then choose app, version, and APK variant.',
  usage: '.apk <app name>',
  async run(ctx) {
    return runApkCommand(ctx, { args:ctx.args })
  },
}
