import { createHash } from 'node:crypto'
import { downloadCommandMedia, mediaToSticker } from '../../utils/media-conversion.js'
import {
  parsePsFile,
  parsePsRangeArgs,
  psPackNameFromFile,
} from '../../utils/ps-source.js'
import {
  parsePinterestSearchArgs,
  pinterestPackName,
  searchPinterestImages,
} from '../../utils/pinterest-search.js'
import { downloadImageUrl } from '../../utils/remote-image.js'
import { sendNativeStickerPacks } from '../../utils/sticker-pack.js'

const SEARCH_MAX = 150
const FILE_MAX = 400

function hash(buffer) {
  return createHash('sha256').update(buffer).digest('hex')
}

async function convertRemoteUrls(urls, {
  packName,
  wanted = urls.length,
} = {}) {
  const stickers = []
  const seen = new Set()

  for (const url of urls) {
    if (stickers.length >= wanted) break
    try {
      const image = await downloadImageUrl(url)
      const digest = hash(image.buffer)
      if (seen.has(digest)) continue
      seen.add(digest)

      const sticker = await mediaToSticker(image.buffer, {
        animated:image.contentType === 'image/gif',
        packName,
      })
      if (Buffer.isBuffer(sticker) && sticker.length) stickers.push(sticker)
    } catch (error) {
      console.warn('MSCC PS URL skipped:', error?.message || error)
    }
  }

  return stickers
}

async function convertZipImages(images, { packName } = {}) {
  const stickers = []
  const seen = new Set()

  for (const image of images) {
    try {
      const digest = hash(image.buffer)
      if (seen.has(digest)) continue
      seen.add(digest)

      const sticker = await mediaToSticker(image.buffer, {
        animated:image.animated === true,
        packName,
      })
      if (Buffer.isBuffer(sticker) && sticker.length) stickers.push(sticker)
    } catch (error) {
      console.warn('MSCC PS ZIP image skipped:', image?.name || '', error?.message || error)
    }
  }

  return stickers
}

async function runFileMode(ctx, source) {
  const fileName = String(source?.found?.media?.fileName || 'urls.txt').trim() || 'urls.txt'
  const parsed = parsePsFile(source.buffer, fileName)
  const items = parsed.images.length ? parsed.images : parsed.urls
  if (!items.length) {
    await ctx.reply('I could not find any usable image URLs or images in that file.')
    return
  }

  const { start, end, customPackName } = parsePsRangeArgs(ctx.args, items.length)
  const packName = customPackName || psPackNameFromFile(fileName)
  const selected = items.slice(start, end + 1).slice(0, FILE_MAX)

  if (!selected.length) {
    await ctx.reply('That range does not contain any usable items.')
    return
  }

  await ctx.reply(`Building *${packName}* from ${selected.length} item${selected.length === 1 ? '' : 's'}…`)

  const stickers = parsed.images.length
    ? await convertZipImages(selected, { packName })
    : await convertRemoteUrls(selected, { packName })

  if (stickers.length < 3) {
    await ctx.reply('I need at least 3 usable images to build the sticker pack.')
    return
  }

  const chat = ctx.message?.key?.remoteJid
  const sizes = await sendNativeStickerPacks({
    sock:ctx.account?.sock,
    chat,
    stickers,
    packName,
    quoted:ctx.message,
  })

  await ctx.reply(`Done — ${stickers.length} sticker${stickers.length === 1 ? '' : 's'} in ${sizes.length} pack${sizes.length === 1 ? '' : 's'}.`)
}

async function runSearchMode(ctx) {
  const { query, count } = parsePinterestSearchArgs(ctx.args, {
    defaultCount:30,
    minCount:3,
    maxCount:SEARCH_MAX,
  })

  if (!query) {
    const prefix = String(ctx.publicPrefix || '.')
    await ctx.reply(
      `Use ${prefix}ps <Pinterest search> [3-150], or reply to a URL file/ZIP with ${prefix}ps.`
    )
    return
  }

  const packName = pinterestPackName(query)
  await ctx.reply(`Finding ${count} stickers for *${query}*…`)

  const candidates = await searchPinterestImages(query, {
    limit:count,
    candidateMultiplier:3,
  })

  if (!candidates.length) {
    await ctx.reply('I could not find Pinterest images for that search.')
    return
  }

  const stickers = await convertRemoteUrls(
    candidates.map(item => item.imageUrl),
    { packName, wanted:count },
  )

  if (stickers.length < 3) {
    await ctx.reply('Pinterest did not return enough usable images to build a sticker pack.')
    return
  }

  const chat = ctx.message?.key?.remoteJid
  const sizes = await sendNativeStickerPacks({
    sock:ctx.account?.sock,
    chat,
    stickers,
    packName,
    quoted:ctx.message,
  })

  const shortfall = Math.max(0, count - stickers.length)
  await ctx.reply(
    shortfall
      ? `Done — ${stickers.length}/${count} usable stickers in ${sizes.length} pack${sizes.length === 1 ? '' : 's'}.`
      : `Done — ${stickers.length} stickers in ${sizes.length} pack${sizes.length === 1 ? '' : 's'}.`
  )
}

export default {
  name:'ps',
  description:'Build native sticker packs from a URL file/ZIP or a Pinterest search.',
  usage:'.ps <search> [amount] | reply to a file with .ps [pack name] [range]',
  async run(ctx) {
    try {
      const source = await downloadCommandMedia(ctx, ['document'])
      if (source) return runFileMode(ctx, source)
      return runSearchMode(ctx)
    } catch (error) {
      console.error('MSCC PS failed:', error)
      await ctx.reply(error?.message || 'I could not build that sticker pack.')
    }
  },
}
