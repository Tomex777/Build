import { runMovieCommand } from '../../movie-flow.js'

export default {
  name: 'movie',
  aliases: ['movies', 'film'],
  description: 'Search movies, choose a result, then pick quality and delivery.',
  usage: '.movie <title>',
  async run(ctx) {
    return runMovieCommand(ctx, { args:ctx.args })
  },
}
