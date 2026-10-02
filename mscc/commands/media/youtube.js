import { runYouTubeCommand } from '../../youtube-flow.js'

export default {
  name:'youtube',
  aliases:['yt'],
  description:'Search YouTube and download a selected video.',
  usage:'.youtube <search> [--doc]',
  async run(ctx) {
    return runYouTubeCommand(ctx, { args:ctx.args })
  },
}
