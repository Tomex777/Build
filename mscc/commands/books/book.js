import { runBookCommand } from '../../book-flow.js'

export default {
  name: 'book',
  aliases: ['books', 'novel', 'novels'],
  description: 'Search books or novels, choose an edition/format, and download it.',
  usage: '.book <title or author>',
  async run(ctx) {
    return runBookCommand(ctx, { args:ctx.args })
  },
}
