import { searchWeb } from './utils/web-search.js'

const UA = 'Night/2.3 (+WhatsApp utility bot)'

async function json(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers:{ 'user-agent':UA, accept:'application/json', ...(options.headers || {}) },
    signal:options.signal || AbortSignal.timeout(15000),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new Error('Service HTTP ' + response.status)
  return payload
}

export async function weatherFor(place) {
  const query = String(place || '').trim()
  if (!query) throw new Error('Give me a place.')
  const geo = await json('https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=' + encodeURIComponent(query))
  const row = geo?.results?.[0]
  if (!row) throw new Error('I could not find that place.')

  const params = new URLSearchParams({
    latitude:String(row.latitude),
    longitude:String(row.longitude),
    current:'temperature_2m,apparent_temperature,weather_code,wind_speed_10m',
    daily:'temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code',
    timezone:'auto',
    forecast_days:'3',
  })
  const data = await json('https://api.open-meteo.com/v1/forecast?' + params)
  return {
    place:[row.name, row.admin1, row.country].filter(Boolean).join(', '),
    current:data.current || {},
    daily:data.daily || {},
  }
}

const WEATHER = new Map([
  [0,'Clear'],[1,'Mostly clear'],[2,'Partly cloudy'],[3,'Overcast'],
  [45,'Fog'],[48,'Rime fog'],[51,'Light drizzle'],[53,'Drizzle'],[55,'Heavy drizzle'],
  [61,'Light rain'],[63,'Rain'],[65,'Heavy rain'],[71,'Light snow'],[73,'Snow'],[75,'Heavy snow'],
  [80,'Rain showers'],[81,'Rain showers'],[82,'Heavy showers'],[95,'Thunderstorm'],[96,'Thunderstorm with hail'],[99,'Severe thunderstorm with hail'],
])
export const weatherLabel = code => WEATHER.get(Number(code)) || 'Weather code ' + code

export async function defineWord(word) {
  const term = String(word || '').trim()
  if (!term) throw new Error('Give me a word.')
  const rows = await json('https://api.dictionaryapi.dev/api/v2/entries/en/' + encodeURIComponent(term))
  const entry = Array.isArray(rows) ? rows[0] : null
  if (!entry) throw new Error('No definition found.')
  const meanings = []
  for (const meaning of entry.meanings || []) {
    for (const def of (meaning.definitions || []).slice(0,2)) {
      if (def?.definition) meanings.push({
        partOfSpeech:String(meaning.partOfSpeech || ''),
        definition:String(def.definition),
        example:String(def.example || ''),
      })
    }
    if (meanings.length >= 4) break
  }
  return {
    word:String(entry.word || term),
    phonetic:String(entry.phonetic || entry.phonetics?.find(x => x?.text)?.text || ''),
    meanings,
  }
}

export async function githubLookup(input) {
  const query = String(input || '').trim().replace(/^https?:\/\/github\.com\//i,'').replace(/^@/,'').replace(/\/$/,'')
  if (!query) throw new Error('Give me a GitHub user or owner/repo.')

  if (query.includes('/')) {
    const [owner, repo] = query.split('/').filter(Boolean)
    const data = await json('https://api.github.com/repos/' + encodeURIComponent(owner) + '/' + encodeURIComponent(repo), {
      headers:{ 'x-github-api-version':'2022-11-28' },
    })
    return { type:'repo', data }
  }

  const data = await json('https://api.github.com/users/' + encodeURIComponent(query), {
    headers:{ 'x-github-api-version':'2022-11-28' },
  })
  return { type:'user', data }
}

export async function newsSearch(topic = '') {
  const query = [String(topic || '').trim(), 'latest news'].filter(Boolean).join(' ')
  return searchWeb(query, { limit:6 })
}

export async function translateWithMyMemory(text, target, source = 'autodetect') {
  const body = String(text || '').trim()
  const to = String(target || '').trim().toLowerCase()
  if (!body || !to) throw new Error('Translation needs text and a target language.')
  const url = 'https://api.mymemory.translated.net/get?q=' + encodeURIComponent(body.slice(0,500)) +
    '&langpair=' + encodeURIComponent(source + '|' + to)
  const data = await json(url)
  const translated = String(data?.responseData?.translatedText || '').trim()
  if (!translated) throw new Error('Translation service returned no text.')
  return translated
}

export function randomValue(args = []) {
  const parts = args.map(v => String(v || '').trim()).filter(Boolean)
  const mode = String(parts[0] || '').toLowerCase()
  if (mode === 'number') {
    const min = Number(parts[1] ?? 1)
    const max = Number(parts[2] ?? 100)
    if (!Number.isFinite(min) || !Number.isFinite(max)) throw new Error('Use .random number <min> <max>.')
    const lo = Math.ceil(Math.min(min,max))
    const hi = Math.floor(Math.max(min,max))
    return String(Math.floor(Math.random() * (hi - lo + 1)) + lo)
  }
  if (parts.length > 1) return parts[Math.floor(Math.random() * parts.length)]
  return String(Math.floor(Math.random() * 100) + 1)
}
