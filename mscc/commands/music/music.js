import { runSongCommand } from '../../music-flow.js'

export default {
  name: 'song',
  aliases: ['music','play'],
  description: 'Search for songs, then reply with the result number(s) you want.',
  usage: '.song <song name>',
  async run(ctx) {
    return runSongCommand(ctx, { args:ctx.args })
  },
}
