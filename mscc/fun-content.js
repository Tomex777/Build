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

export async function randomMeme(category = '', { fetchImpl = globalThis.fetch } = {}) {
  if (typeof fetchImpl !== 'function') throw new Error('Meme service is unavailable.')
  const key = clean(category, 30).toLowerCase()
  const subreddit = MEME_SUBREDDITS[key] || 'memes'
  const response = await fetchImpl(
    'https://www.reddit.com/r/' + encodeURIComponent(subreddit) + '/hot.json?limit=60&raw_json=1',
    {
      headers:{ accept:'application/json', 'user-agent':'Night/2.3 meme reader' },
      signal:AbortSignal.timeout(15000),
    },
  )
  const payload = await response.json()
  if (!response.ok) throw new Error('Meme feed returned HTTP ' + response.status + '.')
  const rows = parseRedditMemes(payload)
  if (!rows.length) throw new Error('No usable meme image was found right now.')
  return rows[Math.floor(Math.random() * rows.length)]
}
