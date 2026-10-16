import { downloadCommandMedia } from '../../utils/media-conversion.js'
import {
  EDITIONS, parseCardRequest, lookupAnimeCharacter, imageAsset,
  getArtworkForCharacter, renderEditionPng,
} from '../../utils/anime-collectible-cards.js'

const GUIDE = [
  '🎴 *NIGHT · ARTIST EDITIONS — ANIME TEST*',
  'Create real rendered collectible cards from any searchable anime character.',
  '',
  '*.card Naruto Uzumaki* — all 3 original Artist Editions',
  '*.card Roronoa Zoro --nocturne* — one design',
  '*.card Gojo Satoru --tidelight* — one design',
  '*.card Luffy --verdant* — one design',
  '*.card Naruto --file* — full-resolution PNG documents',
  '*.card Naruto --back* — show the designed card backs',
  '*.card --id=17* — select an exact AniList character ID',
  '',
  'Tip: Reply to your own high-resolution artwork with .card <character name> to use it instead of the catalogue portrait.',
  '',
  'No image AI, payments, packs, or trading. This is only a visual test.',
].join('\n')

export default {
  name:'card',
  description:'Test three archived premium Anime Artist Editions as collectible cards.',
  usage:'.card <anime character> [--nocturne|--tidelight|--verdant|--all] [--file] [--back]',
  help:'Sends all 3 editions by default. AniList automatically finds character portraits, or reply to an image to use your own art. Catalogue artwork is not licensed for resale.',
  async run(ctx) {
    let request
    try { request = parseCardRequest(ctx.args) }
    catch (error) { return ctx.reply(GUIDE + '\n\n' + (error?.message || 'Invalid options.')) }

    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) return ctx.reply('NIGHT cannot send cards without a connected WhatsApp account.')
    const editions = request.edition === 'all' ? EDITIONS : [request.edition]
    let character, artwork
    try {
      // Preserve the user's preferred HD artwork whenever a message is quoted.
      // No manual artwork upload is necessary for ordinary character searches.
      const uploaded = await downloadCommandMedia(ctx,['image'])
      if (uploaded?.buffer?.length) {
        if (!request.query) return ctx.reply('Name your character when replying to a picture, e.g. .card Kakashi Hatake')
        character = {name:request.query, id:'manual', sourceUrl:'https://anilist.co/'}
        artwork = await imageAsset(uploaded.buffer, {sourceUrl:character.sourceUrl, userSupplied:true})
      } else {
        character = await lookupAnimeCharacter(request.query,{id:request.characterId})
        artwork = await getArtworkForCharacter(character)
      }
    } catch (error) {
      console.warn('[card] artwork/lookup:', error?.message || error)
      return ctx.reply('🎴 Could not prepare the character art.\n' + (error?.message || 'Please retry with a clear character name or reply to an image.'))
    }

    let delivered = 0
    for (const edition of editions) {
      try {
        const png = await renderEditionPng(character, edition, artwork,{side:request.side})
        const filename = 'NIGHT-' + String(character.name || 'character').replace(/[^\p{L}\p{N}-]+/gu,'-').slice(0,36) + '-' + edition + '-' + request.side + '.png'
        const caption = [
          '🎴 *NIGHT Artist Editions*',
          '*' + character.name + '* — ' + edition.toUpperCase() + (request.side === 'back' ? ' (Reverse)' : ''),
          '3000 × 4200 • original premium card renderer',
          delivered === 0 && !artwork.artist.includes('supplied') ? 'Art reference: ' + character.sourceUrl : '',
          delivered === 0 ? 'Preview only: source art rights are unverified; not cleared for selling.' : '',
        ].filter(Boolean).join('\n')
        if (request.asFile) {
          await sock.sendMessage(chat, {
            document:png, mimetype:'image/png', fileName:filename, caption,
          }, {quoted:delivered===0?ctx.message:undefined})
        } else {
          await sock.sendMessage(chat, {
            image:png, mimetype:'image/png', caption,
          }, {quoted:delivered===0?ctx.message:undefined})
        }
        delivered += 1
      } catch (error) {
        console.error('[card] edition failed:',edition,error?.stack || error)
        if (!delivered) return ctx.reply('Card rendering failed for ' + edition + '. No image was sent. Try .card ' + request.query + ' --' + edition + ' to retry just this style.')
        return ctx.reply('Sent ' + delivered + ' of ' + editions.length + ' cards. ' + edition + ' failed to render; please retry that edition.')
      }
    }
    return true
  },
}
