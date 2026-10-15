import { randomDarkMeme } from '../../fun-content.js'

export default {
  name:'darkmeme',
  aliases:['dmeme'],
  description:'Send a random dark-humor meme from several communities (non-NSFW).',
  usage:'.darkmeme',
  async run(ctx) {
    try {
      const meme = await randomDarkMeme()
      const caption = [
        meme.title ? '*' + meme.title + '*' : '',
        meme.subreddit ? 'r/' + meme.subreddit : '',
        'Night',
      ].filter(Boolean).join('\n')
      return ctx.sendImageUrl?.(meme.url, caption)
    } catch (error) {
      return ctx.reply(error?.message || 'I could not fetch a dark meme right now.')
    }
  },
}
