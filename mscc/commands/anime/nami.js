import { sendProfileCommandMenu } from '../../profile-command-menu.js'

export default {
  name:'nami',
  description:"Show Nami's anime and manga command list.",
  usage:'.nami',
  profileOnly:'nami',
  async run(ctx) {
    return sendProfileCommandMenu(ctx, 'nami')
  },
}
