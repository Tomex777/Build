import { searchImages, searchWeb } from './utils/web-search.js'
import { downloadImageUrl } from './utils/remote-image.js'

const web = await searchWeb('OpenAI ChatGPT', { limit:5 })
if (!web.length) throw new Error('Live web search returned no results')
if (!web.some(item => /^https?:\/\//.test(item.url))) throw new Error('Live web search returned no usable URLs')

const images = await searchImages('orange cat', { limit:8 })
if (!images.length) throw new Error('Live image search returned no results')

let delivered = null
let lastError = null
for (const item of images) {
  for (const url of [item.imageUrl, item.thumbnailUrl].filter(Boolean)) {
    try {
      const image = await downloadImageUrl(url)
      if (image?.buffer?.length && image?.contentType?.startsWith('image/')) {
        delivered = { url, bytes:image.buffer.length, contentType:image.contentType }
        break
      }
    } catch (error) {
      lastError = error
    }
  }
  if (delivered) break
}
if (!delivered) throw lastError || new Error('Live image search found results but none were downloadable')

console.log(JSON.stringify({
  ok:true,
  webResults:web.length,
  firstWeb:web[0],
  imageResults:images.length,
  imageProof:delivered,
}, null, 2))
