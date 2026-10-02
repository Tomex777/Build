import { runLibraryWatchCommand } from '../../library-command.js'

export default {
  name:'watch',
  description:'Watch releases for a saved Library item.',
  usage:'.watch <library number>',
  async run(ctx) {
    return runLibraryWatchCommand(ctx, { args:ctx.args, enabled:true })
  },
}
