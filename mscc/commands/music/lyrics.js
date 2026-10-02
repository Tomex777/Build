import { runLyricsCommand } from '../../lyrics-flow.js'

export default {
  name:'lyrics',
  aliases:['lyric'],
  description:'Find lyrics for a song.',
  usage:'.lyrics <song name>',
  async run(ctx) {
    return runLyricsCommand(ctx, { args:ctx.args })
  },
}
