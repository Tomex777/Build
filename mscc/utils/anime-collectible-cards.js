// Anime collectible test: preserve the archived Artist Editions SVG compositions.
// Art is supplied by AniList's character catalogue or by the WhatsApp user.
// Character portraits are not licensed for commercial collectible sales.
import { readFile } from 'node:fs/promises'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import { renderCard } from './artist-edition-renderer.js'
import { downloadImageUrl } from './remote-image.js'

export const EDITIONS = ['nocturne', 'tidelight', 'verdant']
export const OUTPUT_WIDTH = 3000
export const OUTPUT_HEIGHT = 4200
const GRAPHQL = 'https://graphql.anilist.co'
const LOOKUP_TTL = 15 * 60 * 1000
const CHAR_QUERY = [
  'query ($search: String!) {',
  '  Page(page: 1, perPage: 10) {',
  '    characters(search: $search) {',
  '      id name { full native }',
  '      image { large medium }',
  '      siteUrl favourites',
  '    }',
  '  }',
  '}',
].join('\n')
let basePromise
const lookupCache = new Map()
let lookupCooldownUntil = 0
let renderTail = Promise.resolve()

async function base() {
  if (!basePromise) basePromise = readFile(new URL('../assets/cards/artist-editions-base.json', import.meta.url), 'utf8').then(JSON.parse)
  return basePromise
}

function stripped(s) {
  return String(s || '').normalize('NFKD').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim()
}

function candidateScore(c, name) {
  const a = stripped(c.name?.full), b = stripped(name)
  if (a === b) return 100000 + Math.min(999, Number(c.favourites || 0))
  if (a.startsWith(b + ' ') || a.endsWith(' ' + b)) return 20000 + Math.min(999, Number(c.favourites || 0))
  if (a.includes(b)) return 5000 + Math.min(999, Number(c.favourites || 0))
  return Math.min(999, Number(c.favourites || 0))
}

export function parseCardRequest(args) {
  const tokens = Array.isArray(args) ? args.map(x => String(x || '').trim()).filter(Boolean) : []
  const names = []
  let edition = 'all', side = 'front', asFile = false, characterId = 0
  for (const value of tokens) {
    const key = value.toLowerCase()
    const style = key.replace(/^--/, '')
    if (key.startsWith('--') && (EDITIONS.includes(style) || style === 'all')) {
      if (edition !== 'all' && style !== edition && style !== 'all') throw new Error('Choose one edition at a time.')
      edition = style
    } else if (key === '--back') side = 'back'
    else if (key === '--front') side = 'front'
    else if (key === '--file' || key === '--hd') asFile = true
    else if (/^--id=\d{1,9}$/.test(key)) characterId = Number(key.slice(5))
    else if (value.startsWith('--')) throw new Error('Unknown .card option: ' + value)
    else names.push(value)
  }
  const query = names.join(' ').replace(/\s+/g, ' ').trim()
  if (!query && !characterId) throw new Error('Name an anime character. Example: .card Naruto Uzumaki')
  if (query.length > 72) throw new Error('Use a character name shorter than 73 characters.')
  return { query, edition, side, asFile, characterId }
}

export async function lookupAnimeCharacter(search, { id = 0, fetchImpl = globalThis.fetch, now = Date.now } = {}) {
  const query = String(search || '').trim()
  if (!query && !id) throw new Error('A character name is required.')
  if (now() < lookupCooldownUntil) throw new Error('AniList is temporarily rate-limiting requests. Try again shortly, or reply to an image with .card <name>.')
  const key = id ? 'id:' + id : stripped(query)
  const hit = lookupCache.get(key)
  if (hit && now() < hit.expires) return hit.value
  const gql = id
    ? 'query($id:Int!){Character(id:$id){id name{full native} image{large medium} siteUrl favourites}}'
    : CHAR_QUERY
  let response
  try {
    response = await fetchImpl(GRAPHQL, {
      method: 'POST',
      headers: {
        accept: 'application/json',
        'content-type': 'application/json',
        'user-agent': 'NIGHT-MSCC/2.3 (collectible card preview)',
      },
      body: JSON.stringify({ query:gql, variables:id ? {id} : {search:query} }),
      signal: AbortSignal.timeout(9000),
    })
  } catch {
    throw new Error('Anime character lookup is temporarily unavailable. Reply to an image with .card <name> instead.')
  }
  if (response.status === 429) {
    const retrySeconds = Math.max(30, Math.min(300, Number(response.headers?.get?.('retry-after')) || 60))
    lookupCooldownUntil = now() + retrySeconds * 1000
    throw new Error('AniList is rate-limiting requests. Try again in about ' + retrySeconds + ' seconds.')
  }
  if (!response.ok) throw new Error('AniList character lookup failed (HTTP ' + response.status + '). Try again later.')
  let data
  try { data = await response.json() } catch { throw new Error('AniList returned invalid data.') }
  if (data?.errors?.length) throw new Error('AniList could not resolve this character.')
  const characters = id ? [data?.data?.Character].filter(Boolean) : (data?.data?.Page?.characters || [])
  const usable = characters.filter(c => c?.id && c?.name?.full && (c?.image?.large || c?.image?.medium))
  usable.sort((a,b) => candidateScore(b,query) - candidateScore(a,query))
  if (!usable.length) throw new Error('No matching anime character with an image. Try their full name or reply to an image.')
  const chosen = usable[0]
  const value = {
    id:chosen.id,
    name:chosen.name.full,
    imageUrl:chosen.image.large || chosen.image.medium,
    sourceUrl:chosen.siteUrl || ('https://anilist.co/character/' + chosen.id),
  }
  lookupCache.set(key, {value, expires:now()+LOOKUP_TTL})
  while (lookupCache.size > 128) lookupCache.delete(lookupCache.keys().next().value)
  return value
}

function mimeOfImage(buffer) {
  if (buffer.length > 8 && buffer.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png'
  if (buffer[0] === 255 && buffer[1] === 216 && buffer[2] === 255) return 'image/jpeg'
  if (buffer.subarray(0,4).toString() === 'RIFF' && buffer.subarray(8,12).toString() === 'WEBP') return 'image/webp'
  return ''
}

export async function imageAsset(buffer, { sourceUrl, userSupplied = false } = {}) {
  if (!Buffer.isBuffer(buffer) || !buffer.length || buffer.length > 12 * 1024 * 1024) throw new Error('Artwork must be an image under 12 MB.')
  let image
  try { image = await loadImage(buffer) } catch { throw new Error('The character artwork is not a supported image.') }
  if (!image?.width || !image?.height) throw new Error('Image dimensions were not detected.')
  if (image.width * image.height > 30_000_000) throw new Error('Artwork dimensions are too large for safe rendering.')
  let input = buffer, mime = mimeOfImage(buffer)
  if (!mime) {
    const canvas = createCanvas(image.width,image.height)
    canvas.getContext('2d').drawImage(image,0,0)
    input = canvas.toBuffer('image/png')
    mime = 'image/png'
  }
  const validUrl = /^https:\/\/\S+/.test(sourceUrl || '') ? sourceUrl : 'https://anilist.co/'
  return {
    art:'data:' + mime + ';base64,' + input.toString('base64'),
    artWidth:image.width, artHeight:image.height,
    artist:userSupplied ? 'Artwork supplied by user' : 'Anime character image via AniList',
    license:'Artwork rights unverified',
    outputLicense:'Artwork rights unverified',
    sourceUrl:validUrl, licenseUrl:validUrl, outputLicenseUrl:validUrl,
  }
}

function charLabel(value,max=24) {
  const clean = String(value || '').replace(/[\u0000-\u001F]+/g, ' ').replace(/\s+/g, ' ').trim()
  if (clean.length<=max) return clean
  return clean.slice(0,max-1).trimEnd()+'…'
}

export async function editionDefinition(character, edition) {
  const templates = await base()
  const original = templates.cards.find(c => c.key === edition)
  if (!original) throw new Error('Unknown edition: ' + edition)
  const {key, ...fields} = original
  return {
    ...fields,
    id:'anime-' + edition,
    name:charLabel(character.name,24),
    subtitle:charLabel(character.name.toUpperCase(),40),
    artwork:'anime-character-' + String(character.id || 'manual'),
    description:'NIGHT Artist Editions collectible preview of ' + character.name + '.',
  }
}

export async function makeEditionSvg(character, edition, artwork, { side = 'front' } = {}) {
  const [card, config] = await Promise.all([editionDefinition(character,edition),base()])
  return renderCard(card,{...artwork, fonts:config.fonts},{side, foil:1, light:[0.28,0.2], prefix:'night-'+edition+'-'})
}

// The original renderer is used without redrawing its geometry. Canvas only
// rasterizes its finished SVG at 3000 x 4200 (not an upscale of an old PNG).
async function svgToPng(svg, { width=OUTPUT_WIDTH }={}) {
  const height = Math.round(width * OUTPUT_HEIGHT / OUTPUT_WIDTH)
  // Match the SVG viewport to the target pixel size before decoding so
  // the master vector layers stay sharp (no 1000px bitmap upscaling).
  const scaledSvg = svg.replace(/(<svg\s[^>]*?)width="1000" height="1400"/, '$1width="' + width + '" height="' + height + '"')
  if (scaledSvg === svg) throw new Error('Unexpected archived SVG master dimensions')
  const image = await loadImage(Buffer.from(scaledSvg,'utf8'))
  const canvas = createCanvas(width,height)
  canvas.getContext('2d').drawImage(image,0,0,width,height)
  return canvas.toBuffer('image/png')
}

export function renderEditionPng(character, edition, artwork, options={}) {
  const run = async () => svgToPng(await makeEditionSvg(character,edition,artwork,options),options)
  const task = renderTail.then(run,run)
  renderTail = task.then(()=>undefined,()=>undefined)
  return task
}

export async function getArtworkForCharacter(character, { downloadImpl=downloadImageUrl }={}) {
  const image = await downloadImpl(character.imageUrl)
  return imageAsset(image.buffer,{sourceUrl:character.sourceUrl})
}
