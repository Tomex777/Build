import { randomJoke } from '../../fun-content.js'

export default {
  name:'joke',
  description:'Tell a random safe joke.',
  usage:'.joke',
  async run(ctx) {
    try {
      return ctx.reply('😭 ' + await randomJoke())
    } catch {
      return ctx.reply('I told my code to behave. It threw an exception.')
    }
  },
}
