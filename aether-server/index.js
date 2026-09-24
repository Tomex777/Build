import express from 'express'

const app = express()
app.use(express.json({ limit: '1mb' }))

const PORT = Number(process.env.PORT || 8787)
const GROQ_KEYS = (process.env.GROQ_API_KEYS || '').split(',').map(x => x.trim()).filter(Boolean)
const GROQ_MODEL = process.env.GROQ_MODEL || 'llama-3.3-70b-versatile'
const GROQ_VISION_MODEL = process.env.GROQ_VISION_MODEL || 'qwen/qwen3.8-27b'
const DEEPSEEK_KEY = (process.env.DEEPSEEK_API_KEY || '').trim()
const DEEPSEEK_MODEL = process.env.DEEPSEEK_MODEL || 'deepseek-chat'
const UA = 'Aether/0.1 Android meme discovery service'
let groqCursor = 0

app.get('/api/health', (_req, res) => res.json({ ok: true, groqKeys: GROQ_KEYS.length, deepseek: Boolean(DEEPSEEK_KEY) }))

app.post('/api/discover', async (req, res) => {
  try {
    const prompt = String(req.body?.prompt || '').trim()
    if (!prompt) return res.status(400).json({ error: 'prompt is required' })
    const intent = await discoverIntent(prompt)
    const queries = [...new Set([...(intent.queries || []), prompt])].filter(Boolean).slice(0, 5)
    res.json({ categoryName: intent.categoryName || '', queries })
  } catch (error) {
    res.status(500).json({ error: safeError(error) })
  }
})

app.post('/api/vibe', async (req, res) => {
  try {
    const mood = String(req.body?.mood || '').trim()
    const categories = Array.isArray(req.body?.categories) ? req.body.categories.map(String).filter(Boolean) : []
    if (!mood) return res.status(400).json({ error: 'mood is required' })
    if (!categories.length) return res.status(400).json({ error: 'categories are required' })
    const system = `Pick exactly one category from this list for the user's current mood: ${categories.join(', ')}. Return ONLY JSON {"category":"EXACT LIST VALUE","reason":"one short sentence"}.`
    const raw = await chat(system, mood)
    const parsed = parseJsonObject(raw) || {}
    const picked = categories.find(x => x.toLowerCase() === String(parsed.category || '').toLowerCase()) || categories[0]
    res.json({ category: picked, reason: String(parsed.reason || '') })
  } catch (error) {
    res.status(500).json({ error: safeError(error) })
  }
})

for (const action of ['caption', 'explain', 'tags', 'similar']) {
  app.post(`/api/ai/${action}`, async (req, res) => {
    try {
      const title = String(req.body?.title || '')
      const subreddit = String(req.body?.subreddit || '')
      const imageUrl = String(req.body?.imageUrl || req.body?.mediaUrl || '')
      const result = await runMemeAction(action, { title, subreddit, imageUrl })
      res.json(result)
    } catch (error) {
      res.status(500).json({ error: safeError(error) })
    }
  })
}

async function discoverIntent(prompt) {
  const system = `You help an app discover Reddit communities. Return ONLY compact JSON: {"categoryName":"short label","queries":["query 1","query 2","query 3"]}. Do not claim a subreddit exists. The app will search and validate Reddit itself.`
  const raw = await chat(system, prompt).catch(() => '')
  const parsed = parseJsonObject(raw)
  if (parsed && Array.isArray(parsed.queries)) return parsed
  const words = prompt.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').split(/\s+/).filter(x => x.length > 2)
  return { categoryName: words.slice(0, 2).join(' '), queries: [prompt, words.slice(0, 4).join(' ')] }
}

async function runMemeAction(action, { title, subreddit, imageUrl }) {
  const context = `Meme title: ${title}\nSubreddit: r/${subreddit}`
  if (action === 'caption') {
    const instruction = 'Write one short witty alternative meme caption, maximum 12 words. Output only the caption.'
    const text = imageUrl ? await chatVision(instruction, context, imageUrl).catch(() => chat(instruction, context)) : await chat(instruction, context)
    return { text: text.trim() }
  }
  if (action === 'explain') {
    const instruction = 'Explain this meme in 2-3 concise sentences: the joke, format/trope, and useful cultural context. Do not invent context that is not visible.'
    const text = imageUrl ? await chatVision(instruction, context, imageUrl).catch(() => chat(instruction, context)) : await chat(instruction, context)
    return { text: text.trim() }
  }
  if (action === 'tags') {
    const raw = await chat('Return ONLY a JSON array of 4-7 short useful search tags for this meme.', context)
    return { tags: parseJsonArray(raw).slice(0, 7), text: '' }
  }
  const raw = await chat('Return ONLY a JSON array of 4-7 short search phrases that would find similar memes.', context)
  const tags = parseJsonArray(raw).slice(0, 7)
  return { tags, text: tags.length ? 'Search ideas based on this post.' : '' }
}

async function chatVision(system, user, imageUrl) {
  let lastError
  if (GROQ_KEYS.length) {
    for (let attempt = 0; attempt < GROQ_KEYS.length; attempt++) {
      const key = GROQ_KEYS[(groqCursor + attempt) % GROQ_KEYS.length]
      try {
        const text = await openAiVisionCompatible('https://api.groq.com/openai/v1/chat/completions', key, GROQ_VISION_MODEL, system, user, imageUrl)
        groqCursor = (groqCursor + attempt + 1) % GROQ_KEYS.length
        return text
      } catch (error) { lastError = error }
    }
  }
  throw lastError || new Error('No Groq vision key configured.')
}

async function openAiVisionCompatible(url, key, model, system, user, imageUrl) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
    body: JSON.stringify({
      model,
      temperature: 0.35,
      messages: [
        ...(system ? [{ role: 'system', content: system }] : []),
        {
          role: 'user',
          content: [
            { type: 'text', text: user },
            { type: 'image_url', image_url: { url: imageUrl } },
          ],
        },
      ],
    }),
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`Vision AI ${response.status}: ${text.slice(0, 220)}`)
  const json = JSON.parse(text)
  return String(json?.choices?.[0]?.message?.content || '')
}

async function chat(system, user) {
  let lastError
  if (GROQ_KEYS.length) {
    for (let attempt = 0; attempt < GROQ_KEYS.length; attempt++) {
      const key = GROQ_KEYS[(groqCursor + attempt) % GROQ_KEYS.length]
      try {
        const text = await openAiCompatible('https://api.groq.com/openai/v1/chat/completions', key, GROQ_MODEL, system, user)
        groqCursor = (groqCursor + attempt + 1) % GROQ_KEYS.length
        return text
      } catch (error) { lastError = error }
    }
  }
  if (DEEPSEEK_KEY) {
    try {
      return await openAiCompatible('https://api.deepseek.com/chat/completions', DEEPSEEK_KEY, DEEPSEEK_MODEL, system, user)
    } catch (error) { lastError = error }
  }
  throw lastError || new Error('No AI provider configured. Set GROQ_API_KEYS or DEEPSEEK_API_KEY.')
}

async function openAiCompatible(url, key, model, system, user) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
    body: JSON.stringify({
      model,
      temperature: 0.35,
      messages: [{ role: 'system', content: system }, { role: 'user', content: user }],
    }),
  })
  const text = await response.text()
  if (!response.ok) throw new Error('AI ' + response.status + ': ' + text.slice(0, 220))
  const json = JSON.parse(text)
  return String(json?.choices?.[0]?.message?.content || '')
}

async function searchSubreddits(query, limit = 15) {
  const url = `https://www.reddit.com/subreddits/search.json?q=${encodeURIComponent(query)}&limit=${limit}&raw_json=1`
  const json = await redditJson(url)
  return (json?.data?.children || []).map(x => x.data).filter(Boolean).map(d => ({
    name: d.display_name,
    title: htmlDecode(d.title || ''),
    subscribers: Number(d.subscribers || 0),
    description: htmlDecode(d.public_description || ''),
    verified: true,
    over18: Boolean(d.over18),
  })).filter(x => x.name)
}

async function validateSubreddit(name) {
  const safe = String(name || '').replace(/^r\//i, '').trim()
  if (!safe) return null
  const response = await fetch(`https://www.reddit.com/r/${encodeURIComponent(safe)}/about.json?raw_json=1`, { headers: { 'user-agent': UA, accept: 'application/json' } })
  if (!response.ok) return null
  const json = await response.json()
  const d = json?.data
  if (!d?.display_name) return null
  return {
    name: d.display_name,
    title: htmlDecode(d.title || ''),
    subscribers: Number(d.subscribers || 0),
    description: htmlDecode(d.public_description || ''),
    verified: true,
    over18: Boolean(d.over18),
  }
}

async function profileSubreddit(candidate, query) {
  const json = await redditJson(`https://www.reddit.com/r/${encodeURIComponent(candidate.name)}/hot.json?limit=30&raw_json=1`)
  const posts = (json?.data?.children || []).map(x => x.data).filter(Boolean)
  const usable = posts.filter(p => !p.stickied)
  const imageGif = usable.filter(isImageOrGif).length
  const mediaFit = usable.length ? imageGif / usable.length : 0
  const qTerms = query.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').split(/\s+/).filter(x => x.length > 2)
  const haystack = `${candidate.name} ${candidate.title} ${candidate.description}`.toLowerCase()
  const relevance = qTerms.length ? qTerms.filter(t => haystack.includes(t)).length / qTerms.length : 0.4
  const activity = Math.min(usable.length, 30) / 30
  const subs = candidate.subscribers
  const subscriberSignal = subs >= 1_000_000 ? 1 : subs >= 100_000 ? .85 : subs >= 10_000 ? .65 : subs > 0 ? .45 : .2
  const matchScore = clamp(relevance * .42 + mediaFit * .33 + activity * .15 + subscriberSignal * .10)
  return { ...candidate, mediaFit, recentPosts: usable.length, matchScore }
}

function isImageOrGif(post) {
  const url = String(post.url_overridden_by_dest || post.url || '').split('?')[0].toLowerCase()
  return post.post_hint === 'image' || /\.(png|jpe?g|webp|gif)$/.test(url) || Boolean(post?.preview?.images?.[0]?.variants?.gif?.source?.url)
}

async function redditJson(url) {
  const response = await fetch(url, { headers: { 'user-agent': UA, accept: 'application/json' } })
  if (!response.ok) throw new Error(`Reddit ${response.status}`)
  return response.json()
}

function uniqueByName(items) {
  const seen = new Set()
  return items.filter(item => {
    const key = String(item.name || '').toLowerCase()
    if (!key || seen.has(key)) return false
    seen.add(key); return true
  })
}

function parseJsonObject(text) {
  try {
    const match = String(text).match(/\{[\s\S]*\}/)
    return match ? JSON.parse(match[0]) : null
  } catch { return null }
}

function parseJsonArray(text) {
  try {
    const match = String(text).match(/\[[\s\S]*\]/)
    const value = match ? JSON.parse(match[0]) : []
    return Array.isArray(value) ? value.map(String).filter(Boolean) : []
  } catch { return [] }
}

function htmlDecode(value) {
  return String(value).replaceAll('&amp;', '&').replaceAll('&quot;', '"').replaceAll('&#39;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>')
}

function clamp(n) { return Math.max(0, Math.min(1, n)) }
function safeError(error) { return error instanceof Error ? error.message : String(error) }

app.listen(PORT, () => console.log(`Aether AI server listening on :${PORT}`))
