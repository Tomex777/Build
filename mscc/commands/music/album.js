import { runAlbumCommand } from '../../album-flow.js'

export default {
  name:'album',
  description:'Search albums, open the full track list, and download selected tracks or the whole album.',
  usage:'.album <album or artist name>',
  async run(ctx) {
    return runAlbumCommand(ctx, { args:ctx.args })
  },
}
