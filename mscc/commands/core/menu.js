import { sendProfileCommandMenu } from '../../profile-command-menu.js'

export default {
  name:'menu',
  description:"Show Josia's general public command menu.",
  usage:'.menu',
  profileOnly:'josiah',
  async run(ctx) {
    return sendProfileCommandMenu(ctx, 'josiah')
  },
}
