import { runSourceCommand } from '../../source-flow.js'

export default {
  name: 'book',
  aliases: ['books', 'novel', 'novels'],
  description: 'Find books and novels using the configured book sources.',
  usage: '.book <title or author>',
  async run(ctx) {
    return runSourceCommand(ctx, {
      capability: 'books',
      commandName: 'book',
      args: ctx.args,
      botName: ctx.sourceBrand?.('books') || 'Josiah',
      action: 'search',
      announceFallback: false,
    })
  },
}
