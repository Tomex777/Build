const JOKE_FALLBACKS = [
  'Why did the developer go broke? Because they used up all their cache.',
  'I told my code to behave. It threw an exception.',
  'Why do programmers prefer dark mode? Because light attracts bugs.',
  'My Wi-Fi and I are in a complicated relationship. It keeps dropping me.',
  'I tried to make a belt out of watches. Total waist of time.',
]

const MEME_POOLS = Object.freeze({
  // A wide default mix: general, niche, anime, gaming, and tech humor.
  general:[
    'memes','me_irl','dankmemes','AdviceAnimals','funny','MemeEconomy',
    'starterpacks','HistoryMemes','wholesomememes','PrequelMemes',
    'lotrmemes','technicallythetruth','antimeme','comedyheaven',
    'ProgrammerHumor','Animemes','bonehurtingjuice','surrealmemes',
    'facepalm','MinecraftMemes','comedyhomicide','memes_of_the_dank',
  ],
  anime:['Animemes','anime_irl','goodanimemes','animememes','wholesomeanimemes'],
  wholesome:['wholesomememes','wholesomeanimemes','MadeMeSmile','Eyebleach','AnimalsBeingBros'],
  programming:['ProgrammerHumor','programmingmemes','linuxmemes','codingmemes','techhumor'],
  dank:['dankmemes','memes_of_the_dank','shitposting','comedyheaven','surrealmemes','okbuddyretard'],
  // Dark humor has its own source pool. NSFW/spoiler posts are still excluded.
  dark:[
    'darkmemes','darkhumor','DarkHumorMemes','cursedcomments','cursedmemes',
    'cursedimages','HolUp','distressingmemes','ComedyNecrophilia',
    'shitposting','darkhumorandmemes','offensivememes',
  ],
})

const MEME_ALIASES = Object.freeze({
  '':'general', memes:'general', meme:'general', general:'general',
  programmer:'programming', coding:'programming', wholesome:'wholesome',
  anime:'anime', programming:'programming', dank:'dank', dark:'dark',
  darkmeme:'dark', darkmemes:'dark',
})

const RECENT_LIMIT = 150
const SUBREDDITS_PER_BATCH = 3
const MAX_SUBREDDITS_PER_REQUEST = 6
const recentByMode = new Map()

export function memeSubreddits(category = '') {
  const key = clean(category, 30).toLowerCase()
  return [...MEME_POOLS[MEME_ALIASES[key] || 'general']]
}

function clean(value, max = 500) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function imageUrl(value) {
  const raw = clean(value, 2000)
  return /^https?:\/\/.+\.(?:jpe?g|png|webp)(?:\?.*)?$/i.test(raw) ? raw : ''
}

export async function randomJoke({ fetchImpl = globalThis.fetch } = {}) {
  if (typeof fetchImpl === 'function') {
    try {
      const response = await fetchImpl('https://v2.jokeapi.dev/joke/Any?safe-mode', {
        headers:{ accept:'application/json', 'user-agent':'Night/2.3' },
        signal:AbortSignal.timeout(12000),
      })
      const data = await response.json()
      if (response.ok && data?.error !== true) {
        if (data.type === 'single' && clean(data.joke)) return clean(data.joke, 1200)
        if (data.type === 'twopart' && clean(data.setup) && clean(data.delivery)) {
          return clean(data.setup, 600) + '\n\n' + clean(data.delivery, 600)
        }
      }
    } catch {}
  }
  return JOKE_FALLBACKS[Math.floor(Math.random() * JOKE_FALLBACKS.length)]
}

export function parseRedditMemes(payload) {
  const rows = payload?.data?.children || []
  return rows
    .map(row => row?.data || {})
    .filter(row => row && row.over_18 !== true && row.stickied !== true && row.spoiler !== true && row.is_video !== true)
    .map(row => ({
      title:clean(row.title, 280),
      url:imageUrl(row.url_overridden_by_dest || row.url),
      permalink:row.permalink ? 'https://www.reddit.com' + row.permalink : '',
      subreddit:clean(row.subreddit, 80),
      author:clean(row.author, 80),
    }))
    .filter(row => row.title && row.url)
}

export function parseMemeApi(payload) {
  const entries = Array.isArray(payload?.memes) ? payload.memes : payload ? [payload] : []
  return entries
    .filter(row => row && row.nsfw !== true && row.spoiler !== true)
    .map(row => ({
      title:clean(row.title, 280),
      url:imageUrl(row.url),
      permalink:clean(row.postLink, 500),
      subreddit:clean(row.subreddit, 80),
      author:clean(row.author, 80),
    }))
    .filter(row => row.title && row.url)
}

// Providers sometimes return an HTML block/rate-limit page in place of JSON.
// Never leak JSON parsing errors (or the HTML body) into WhatsApp replies.
async function safeFeedJson(response) {
  if (!response?.ok) return null
  const contentType = String(response.headers?.get?.('content-type') || '').toLowerCase()
  if (contentType && !contentType.includes('json')) return null
  try {
    return await response.json()
  } catch {
    return null
  }
}

function shuffledSubreddits(pool, random) {
  const copy = [...pool]
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const roll = Number(random())
    const j = Math.min(i, Math.max(0, Math.floor((Number.isFinite(roll) ? roll : 0) * (i + 1))))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy.slice(0, MAX_SUBREDDITS_PER_REQUEST)
}

async function fetchMemes(subreddits, provider, fetchImpl) {
  const results = await Promise.all(subreddits.map(async subreddit => {
    const url = provider === 'api'
      ? 'https://meme-api.com/gimme/' + encodeURIComponent(subreddit) + '/10'
      : 'https://www.reddit.com/r/' + encodeURIComponent(subreddit) + '/hot.json?limit=75&raw_json=1'
    try {
      const response = await fetchImpl(url, {
        headers:{ accept:'application/json', 'user-agent':'Night/2.3 meme reader' },
        signal:AbortSignal.timeout(5500),
      })
      const payload = await safeFeedJson(response)
      if (!payload) return []
      return provider === 'api' ? parseMemeApi(payload) : parseRedditMemes(payload)
    } catch {
      // A blocked, rate-limited, malformed, or offline source must not break the command.
      return []
    }
  }))
  return results.flat()
}

function chooseMeme(rows, mode, random, { allowRecent = false } = {}) {
  const recent = recentByMode.get(mode) || []
  const seen = new Set(recent)
  // Posts often appear in several communities; do not send duplicates in one batch.
  const unique = [...new Map(rows.filter(row => row?.url).map(row => [row.url, row])).values()]
  const candidates = allowRecent ? unique : unique.filter(row => !seen.has(row.url))
  if (!candidates.length) return null
  const roll = Number(random())
  const position = Math.min(candidates.length - 1, Math.max(0, Math.floor((Number.isFinite(roll) ? roll : 0) * candidates.length)))
  const result = candidates[position]
  const next = recent.filter(url => url !== result.url)
  next.push(result.url)
  recentByMode.set(mode, next.slice(-RECENT_LIMIT))
  return result
}

export async function randomMeme(category = '', { fetchImpl = globalThis.fetch, random = Math.random } = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('Meme service is unavailable.')
  const key = clean(category, 30).toLowerCase()
  const mode = MEME_ALIASES[key] === 'dark' ? 'dark' : 'regular'
  const subredditPool = memeSubreddits(category)
  const selected = shuffledSubreddits(subredditPool, random)
  const repeated = []

  // Each invocation mixes up to three different subreddits at a time.
  // Try the API first, Reddit JSON second, then a different set of communities.
  // The provider choice is never tied to one hard-coded subreddit.
  for (let index = 0; index < selected.length; index += SUBREDDITS_PER_BATCH) {
    const batch = selected.slice(index, index + SUBREDDITS_PER_BATCH)
    for (const provider of ['api','reddit']) {
      const rows = await fetchMemes(batch, provider, fetchImpl)
      const fresh = chooseMeme(rows, mode, random)
      if (fresh) return fresh
      repeated.push(...rows)
    }
  }

  // Prefer not to repeat, but a previously shown image beats total failure.
  const fallback = chooseMeme(repeated, mode, random, { allowRecent:true })
  if (fallback) return fallback
  throw new Error('Meme feeds are temporarily unavailable. Please try again shortly.')
}

export function randomDarkMeme(options = {}) {
  return randomMeme('dark', options)
}
