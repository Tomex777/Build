// NIGHT anime-reaction media providers. Each source has an explicit HTTPS
// domain allowlist; returned URLs are untrusted until matched and decoded.
import { randomInt } from 'node:crypto'

const UA = 'NIGHT-Anime-Stickers (https://github.com/Tomex777/Build)'
const ENKIDU_API = 'https://animegifs-enkidu.koyeb.app/v3/api/?category='
const OCI_HOST = 'frqs54dqeajn.objectstorage.eu-frankfurt-1.oci.customer-oci.com'
const NEKOS_API = 'https://nekos.best/api/v2/'
const WAIFU_API = 'https://api.waifu.pics/sfw/'
const GIFUKAI_API = 'https://api.gifukai.com/v1/'
const METADATA_TIMEOUT_MS = 9000

// The Enkidu categories come from its published animegifs category list.
// Do not pretend its unrelated categories are interchangeable reactions.
const ENKIDU = new Set((
  'attack bite bloodsuck blush bonk brofist cry cuddle dance disgust exploding ' +
  'facedesk facepalm flick flirt french_kiss handhold happy harass highfive hug ' +
  'icecream insult kill kiss lick love marry nod nosebleed note nuzzle pat peck ' +
  'poke popcorn pout punch punish run sad scared scold shoot shrug sip slap ' +
  'smack smirk sorry spank stare tease threat tickle tired wave yawn'
).split(' '))
const WAIFU = new Set((
  'bite yeet handhold slap hug kiss wink bonk smile poke highfive wave nom ' +
  'smug cuddle dance kill blush pat happy cry'
).split(' '))
// Only use categories documented to exist on the Gifukai service.
const GIFUKAI = new Set('pat hug kiss cry sleep'.split(' '))
const recent = new Map()

function secureGifUrl(value, source, category) {
  if (!value || typeof value !== 'string') throw new Error(source + ' returned no GIF URL')
  let url
  try { url = new URL(value) } catch { throw new Error(source + ' returned a malformed URL') }
  if (url.protocol !== 'https:' || url.username || url.password || url.port) {
    throw new Error(source + ' returned an unsafe URL')
  }
  if (!/\.gif$/i.test(url.pathname)) throw new Error(source + ' did not return an animated GIF')
  if (source === 'nekos') {
    if (!['nekos.best','www.nekos.best'].includes(url.hostname) ||
        !url.pathname.startsWith('/api/v2/' + category + '/')) throw new Error('Nekos returned a URL outside its approved category')
  } else if (source === 'enkidu') {
    if (url.hostname !== OCI_HOST) throw new Error('Enkidu returned an unapproved media host')
  } else if (source === 'waifu') {
    if (!['i.waifu.pics','waifu.pics'].includes(url.hostname)) throw new Error('Waifu returned an unapproved media host')
  } else if (source === 'gifukai') {
    if (url.hostname !== 'cdn.gifukai.com' || !url.pathname.startsWith('/' + category + '/')) {
      throw new Error('Gifukai returned media outside its approved category')
    }
  } else throw new Error('Unknown anime GIF source')
  return url.href
}

async function json(url, source) {
  const response = await fetch(url, {
    headers:{'User-Agent':UA, Accept:'application/json'},
    redirect:'error',
    signal:AbortSignal.timeout(METADATA_TIMEOUT_MS),
  })
  if (!response.ok) throw new Error(source + ' metadata HTTP ' + response.status)
  if (!String(response.headers.get('content-type') || '').toLowerCase().includes('json')) {
    throw new Error(source + ' returned non-JSON metadata')
  }
  return response.json()
}

function preferUnused(category, urls) {
  const distinct = [...new Set(urls)]
  const prev = recent.get(category) || []
  const unused = distinct.filter(url => !prev.includes(url))
  const pool = unused.length ? unused : distinct
  if (!pool.length) throw new Error('No valid GIFs returned')
  const chosen = pool[randomInt(pool.length)]
  recent.set(category,[chosen,...prev.filter(url => url !== chosen)].slice(0,30))
  return [chosen, ...distinct.filter(url => url !== chosen)].slice(0,4)
}

async function nekos(category) {
  const data = await json(NEKOS_API + encodeURIComponent(category) + '?amount=12','nekos')
  const urls = (Array.isArray(data?.results) ? data.results : []).flatMap(row => {
    try { return [secureGifUrl(row?.url,'nekos',category)] } catch { return [] }
  })
  return preferUnused(category,urls)
}

async function enkidu(category) {
  const data = await json(ENKIDU_API + encodeURIComponent(category),'enkidu')
  return [secureGifUrl(data?.gif,'enkidu',category)]
}

async function waifu(category) {
  const data = await json(WAIFU_API + encodeURIComponent(category),'waifu')
  return [secureGifUrl(data?.url,'waifu',category)]
}

async function gifukai(category) {
  const data = await json(GIFUKAI_API + encodeURIComponent(category),'gifukai')
  if (data?.action && data.action !== category) throw new Error('Gifukai returned the wrong action')
  return [secureGifUrl(data?.url,'gifukai',category)]
}

// The independently verified Enkidu host is preferred when available, rather
// than making 59/60 reactions depend on one API/CDN. If a source reports
// broken JSON, 403, a dead GIF, or FFmpeg rejects media, the command retries
// the next *semantically matching* provider before failing.
export function reactionProviders(category) {
  const providers = []
  if (ENKIDU.has(category)) providers.push({name:'enkidu',resolve:() => enkidu(category)})
  if (category !== 'kill') providers.push({name:'nekos',resolve:() => nekos(category)})
  if (WAIFU.has(category)) providers.push({name:'waifu',resolve:() => waifu(category)})
  if (GIFUKAI.has(category)) providers.push({name:'gifukai',resolve:() => gifukai(category)})
  return providers
}

export const APPROVED_REACTION_HOSTS = Object.freeze([
  'nekos.best','www.nekos.best',OCI_HOST,'i.waifu.pics','waifu.pics','cdn.gifukai.com',
])
export { secureGifUrl }
