import { randomMeme } from '../../fun-content.js'

export default {
  name:'meme',
  description:'Send a random meme.',
  usage:'.meme [anime|wholesome|programming|dank]',
  async run(ctx) {
    const category = String(ctx.args?.[0] || '').trim().toLowerCase()
    try {
      const meme = await randomMeme(category)
      const caption = [
        meme.title ? '*' + meme.title + '*' : '',
        meme.subreddit ? 'r/' + meme.subreddit : '',
        'Night',
      ].filter(Boolean).join('\n')
      return ctx.sendImageUrl?.(meme.url, caption)
    } catch (error) {
      return ctx.reply(error?.message || 'I could not fetch a meme right now.')
    }
  },
}
