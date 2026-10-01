import { runMangaCommand } from '../../manga-flow.js'

export default {
  name: 'manga',
  description: 'Search manga, choose chapters or ranges, and download with source fallback.',
  usage: '.manga <title>',
  async run(ctx) {
    return runMangaCommand(ctx, { args:ctx.args })
  },
}
