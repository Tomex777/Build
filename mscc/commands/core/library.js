import { runLibraryCommand } from '../../library-command.js'

export default {
  name:'library',
  aliases:['lib'],
  description:'Open your saved Anime, Manga, Movies, and TV Series library.',
  usage:'.library [anime|manga|movie|tv|number|remove <number>]',
  async run(ctx) {
    return runLibraryCommand(ctx, { args:ctx.args })
  },
}
