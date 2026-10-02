import { sendProfileCommandMenu } from '../../profile-command-menu.js'

export default {
  name:'mimi',
  description:"Show MiMi's music, movie, and TV command list.",
  usage:'.mimi',
  profileOnly:'mimi',
  async run(ctx) {
    return sendProfileCommandMenu(ctx, 'mimi')
  },
}
