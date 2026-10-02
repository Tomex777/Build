import { absoluteUrl, clean, fetchJson, fetchText, safeFileName, sendDocument, textFromHtml } from './_shared.js'

const OCW = 'https://ocw.mit.edu'
const MIT_LEARN_SEARCH = 'https://api.learn.mit.edu/api/v1/learning_resources_search/'

function courseUrl(value = '') {
  try {
    const url = new URL(String(value || ''), OCW)
    if (!/\.mit\.edu$/i.test(url.hostname) && url.hostname !== 'ocw.mit.edu') return ''
    const match = /\/courses\/([^/?#]+)\/?/i.exec(url.pathname)
    if (!match) return ''
    return OCW + '/courses/' + match[1] + '/'
  } catch {
    return ''
  }
}

function normalizeApiRow(row = {}) {
  const url = courseUrl(row?.url || row?.website || row?.course_url || '')
  const platform = String(row?.platform?.code || row?.platform?.name || row?.platform || '').toLowerCase()
  if (!url || (platform && !platform.includes('ocw') && !platform.includes('open course'))) return null
  const instructors = Array.isArray(row?.runs)
    ? row.runs.flatMap(run => run?.instructors || []).map(x => x?.full_name || x?.name || x).filter(Boolean)
    : []
  const courseNumbers = row?.course?.course_numbers || row?.course_numbers || []
  const number = Array.isArray(courseNumbers)
    ? clean(courseNumbers.map(x => x?.value || x).filter(Boolean).join(', '), 80)
    : ''
  return {
    id:String(row?.readable_id || row?.id || url),
    title:clean(row?.title || number || 'MIT OpenCourseWare course', 180),
    instructor:clean(instructors.join(', '), 180),
    description:clean(row?.description || row?.full_description || number, 500),
    url,
    readableId:String(row?.readable_id || ''),
    raw:row,
  }
}

function parseSearchHtml(html = '') {
  const source = String(html || '')
  const out = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']*\/courses\/[^"'?#]+\/?(?:[?#][^"']*)?)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const url = courseUrl(match[1])
    if (!url || seen.has(url)) continue
    const title = clean(textFromHtml(match[2]), 180)
    if (!title || /^image$/i.test(title) || title.length < 3) continue
    seen.add(url)
    out.push({ id:url, title, instructor:'', description:'', url })
    if (out.length >= 25) break
  }
  return out
}

async function searchApi(query) {
  const params = new URLSearchParams({
    q:query,
    platform:'ocw',
    resource_type:'course',
    limit:'25',
    offset:'0',
  })
  const { data } = await fetchJson(MIT_LEARN_SEARCH + '?' + params)
  const rows = Array.isArray(data?.results) ? data.results : []
  return rows.map(normalizeApiRow).filter(Boolean)
}

async function searchHtml(query) {
  const { text } = await fetchText(
    OCW + '/search/?' + new URLSearchParams({ q:query, type:'course' }),
    { headers:{ accept:'text/html,application/xhtml+xml' } },
  )
  return parseSearchHtml(text)
}

async function search(query) {
  try {
    const api = await searchApi(query)
    if (api.length) return api
  } catch {}
  const html = await searchHtml(query)
  if (!html.length) throw new Error('MIT OpenCourseWare returned no course results.')
  return html
}

function parseDownloadPage(html = '', base = OCW) {
  const source = String(html || '')
  const links = []
  const seen = new Set()
  const re = /<a\b[^>]*href=["']([^"']+\.zip(?:\?[^"']*)?)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const url = absoluteUrl(base, match[1])
    if (!url || seen.has(url)) continue
    seen.add(url)
    links.push({
      url,
      label:clean(textFromHtml(match[2]), 100) || 'Download course',
    })
  }
  return links
}

async function resolveCourseZip(item = {}) {
  const url = courseUrl(item?.url || item?.id || item?.raw?.url || '')
  if (!url) throw new Error('MIT OpenCourseWare course URL is missing.')
  const downloadPage = url + 'download/'
  const { text, response } = await fetchText(downloadPage, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const links = parseDownloadPage(text, response.url || downloadPage)
  const preferred = links.find(link => /download course/i.test(link.label)) || links[0]
  if (preferred?.url) return preferred.url

  const slug = new URL(url).pathname.split('/').filter(Boolean).at(-1)
  if (!slug) throw new Error('MIT OpenCourseWare course slug is missing.')
  return url + slug + '.zip'
}

export default {
  id:'mit-ocw',
  name:'MIT OpenCourseWare',
  description:'MIT course materials with official downloadable course packages.',
  fallbackOrder:10,

  async run({ action, query, item, context }) {
    if (action === 'search') return { items:await search(clean(query, 120)) }
    if (action === 'contents') return { contents:[] }
    if (action === 'downloadCourse') {
      const chosen = item || {}
      const url = await resolveCourseZip(chosen)
      return sendDocument(context, {
        url,
        fileName:safeFileName(clean(chosen?.title || 'MIT OpenCourseWare course', 140), 'zip'),
        mimetype:'application/zip',
      })
    }
    if (action === 'download') {
      throw new Error('MIT OpenCourseWare uses whole-course package delivery.')
    }
    throw new Error('Unsupported MIT OpenCourseWare action: ' + action)
  },

  _test:{ courseUrl, normalizeApiRow, parseSearchHtml, parseDownloadPage },
}
