const JOKE_FALLBACKS = [
  'Why did the developer go broke? Because they used up all their cache.',
  'I told my code to behave. It threw an exception.',
  'Why do programmers prefer dark mode? Because light attracts bugs.',
  'My Wi-Fi and I are in a complicated relationship. It keeps dropping me.',
  'I tried to make a belt out of watches. Total waist of time.',
]

const MEME_SUBREDDITS = {
  anime:'animemes',
  wholesome:'wholesomememes',
  programming:'ProgrammerHumor',
  programmer:'ProgrammerHumor',
  dank:'dankmemes',
  memes:'memes',
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
    .filter(row => row && row.over_18 !== true && row.stickied !== true)
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

export async function randomMeme(category = '', { fetchImpl = globalThis.fetch } = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('Meme service is unavailable.')
  const key = clean(category, 30).toLowerCase()
  const subreddit = MEME_SUBREDDITS[key] || 'memes'
  const sources = [
    {
      url:'https://meme-api.com/gimme/' + encodeURIComponent(subreddit) + '/10',
      parse:parseMemeApi,
    },
    {
      url:'https://www.reddit.com/r/' + encodeURIComponent(subreddit) + '/hot.json?limit=60&raw_json=1',
      parse:parseRedditMemes,
    },
  ]

  for (const source of sources) {
    try {
      const response = await fetchImpl(source.url, {
        headers:{ accept:'application/json', 'user-agent':'Night/2.3 meme reader' },
        signal:AbortSignal.timeout(8500),
      })
      const payload = await safeFeedJson(response)
      if (!payload) continue
      const rows = source.parse(payload)
      if (rows.length) return rows[Math.floor(Math.random() * rows.length)]
    } catch {
      // Try the next provider when this one is blocked, malformed, or offline.
    }
  }
  throw new Error('Meme feeds are temporarily unavailable. Try .meme again shortly.')
}
