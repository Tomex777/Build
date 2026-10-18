import { executeTestAll } from '../command-audit.js'
export default {
  name:'testall',
  aliases:['commandaudit'],
  description:'Run and report the complete NIGHT command audit (safe and non-destructive).',
  usage:'.testall [status|cancel]',
  async run(ctx) { return executeTestAll(ctx) },
}
