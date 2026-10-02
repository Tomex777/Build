import { absoluteUrl, clean, fetchText, safeFileName, sendDocument, textFromHtml } from './_shared.js'

const BASE = 'https://www.open.edu'

function isCourseUrl(value = '') {
  try {
    const url = new URL(String(value || ''), BASE)
    if (url.hostname !== 'www.open.edu') return false
    if (!url.pathname.startsWith('/openlearn/')) return false
    return !/\/local\/ocwglobalsearch\/search\.php$/i.test(url.pathname)
  } catch {
    return false
  }
}

function parseSearchHtml(html = '', base = BASE) {
  const source = String(html || '')
  const out = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const label = clean(textFromHtml(match[2]), 240)
    const titleMatch = /^View course\s+(.+)$/i.exec(label)
    if (!titleMatch) continue
    const url = absoluteUrl(base, match[1])
    if (!url || seen.has(url)) continue
    seen.add(url)
    out.push({
      id:url,
      title:clean(titleMatch[1], 180),
      instructor:'The Open University',
      description:'Free OpenLearn course',
      url,
    })
    if (out.length >= 25) break
  }
  return out
}

async function search(query) {
  const url = BASE + '/openlearn/local/ocwglobalsearch/search.php?' + new URLSearchParams({
    q:query,
    sort:'relevant',
  })
  const { text, response } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const items = parseSearchHtml(text, response.url || url)
  if (!items.length) throw new Error('OpenLearn returned no free-course results.')
  return items
}

function parseCourseSections(html = '', base = BASE) {
  const source = String(html || '')
  const out = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const href = match[1]
    if (!/content-section-(?!overview)[a-z0-9._-]+|\/mod\/oucontent\/view\.php\?[^"']*\bsection=/i.test(href)) continue
    const url = absoluteUrl(base, href)
    if (!url || !isCourseUrl(url) || seen.has(url)) continue
    const title = clean(textFromHtml(match[2]), 180)
    if (!title || /^(next|previous|back|continue)$/i.test(title)) continue
    seen.add(url)
    out.push({
      id:url,
      title,
      section:'',
      type:'text',
      url,
    })
    if (out.length >= 100) break
  }
  return out
}

async function contents(item = {}) {
  const url = absoluteUrl(BASE, item?.url || item?.id || '')
  if (!url) throw new Error('OpenLearn course URL is missing.')
  const { text, response } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const finalUrl = response.url || url
  const entries = parseCourseSections(text, finalUrl)

  if (entries.length) return entries

  const overview = finalUrl.replace(/\/?$/, '/')
  return [{
    id:overview,
    title:clean(item?.title || 'Course overview', 180),
    section:'',
    type:'text',
    url:overview,
  }]
}

function mainContentHtml(html = '') {
  const source = String(html || '')
  for (const tag of ['main','article']) {
    const match = new RegExp(`<${tag}\\b[^>]*>([\\s\\S]*?)<\\/${tag}>`, 'i').exec(source)
    if (match?.[1]) return match[1]
  }
  const body = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(source)
  return body?.[1] || source
}

async function documentFor(url, title) {
  const { text, response } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const finalUrl = response.url || url
  const body = textFromHtml(mainContentHtml(text))
  if (body.length < 100) throw new Error('OpenLearn returned an empty course section.')
  const data = Buffer.from([
    clean(title || 'OpenLearn course', 180),
    'Source: ' + finalUrl,
    '',
    body,
    '',
  ].join('\n'), 'utf8')
  return { data, finalUrl }
}

export default {
  id:'openlearn',
  name:'OpenLearn',
  description:'Free Open University courses with selectable course sections.',
  fallbackOrder:20,

  async run({ action, query, item, content, context }) {
    if (action === 'search') return { items:await search(clean(query, 120)) }
    if (action === 'contents') return { contents:await contents(item || {}) }
    if (action === 'download') {
      const chosen = content || {}
      const url = absoluteUrl(BASE, chosen?.url || chosen?.id || '')
      if (!url) throw new Error('OpenLearn course section URL is missing.')
      const doc = await documentFor(url, chosen?.title || item?.title)
      return sendDocument(context, {
        data:doc.data,
        fileName:safeFileName(
          [clean(item?.title || 'OpenLearn course', 100), clean(chosen?.title || 'section', 80)].filter(Boolean).join(' - '),
          'txt',
        ),
        mimetype:'text/plain',
      })
    }
    if (action === 'downloadCourse') {
      const url = absoluteUrl(BASE, item?.url || item?.id || '')
      if (!url) throw new Error('OpenLearn course URL is missing.')
      const doc = await documentFor(url, item?.title || 'OpenLearn course')
      return sendDocument(context, {
        data:doc.data,
        fileName:safeFileName(clean(item?.title || 'OpenLearn course', 140), 'txt'),
        mimetype:'text/plain',
      })
    }
    throw new Error('Unsupported OpenLearn action: ' + action)
  },

  _test:{ isCourseUrl, parseSearchHtml, parseCourseSections, mainContentHtml },
}
