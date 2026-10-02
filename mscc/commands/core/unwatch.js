import { runLibraryWatchCommand } from '../../library-command.js'

export default {
  name:'unwatch',
  description:'Stop watching releases for a saved Library item.',
  usage:'.unwatch <library number>',
  async run(ctx) {
    return runLibraryWatchCommand(ctx, { args:ctx.args, enabled:false })
  },
}
