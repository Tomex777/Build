import { runMangaCommand } from '../../manga-flow.js'

export default {
  name: 'manga',
  description: 'Search manga using your selected source.',
  async run(ctx) {
    return runMangaCommand(ctx, { args:ctx.args })
  },
}
