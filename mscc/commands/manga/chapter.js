import { runChapterShortcut } from '../../manga-flow.js'

export default {
  name:'chapter',
  description:'Download a known manga chapter directly by title and chapter number.',
  usage:'.chapter <manga title> <chapter number>',
  async run(ctx) {
    const args = [...ctx.args]
    const chapterNumber = String(args.pop() || '').trim()
    const query = args.join(' ').trim()
    return runChapterShortcut(ctx, { query, chapterNumber })
  },
}
