import { reactionCatalog } from '../../anime-reaction-stickers.js'

export default {
  name:'reactions',
  description:'List verified NIGHT animated anime reaction stickers.',
  usage:'.reactions',
  async run(ctx) { return ctx.reply(reactionCatalog(ctx.publicPrefix || '.')) },
}
