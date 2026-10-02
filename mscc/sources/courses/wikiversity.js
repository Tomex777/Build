import { clean, fetchJson, safeFileName, sendDocument, textFromHtml } from './_shared.js'

const API = 'https://en.wikiversity.org/w/api.php'
const SITE = 'https://en.wikiversity.org/wiki/'

function pageUrl(title = '') {
  const name = String(title || '').trim()
  if (!name) return ''
  return SITE + encodeURIComponent(name.replace(/ /g, '_')).replace(/%2F/gi, '/')
}

function normalizeSearchRow(row = {}) {
  const title = clean(row?.title || '', 180)
  if (!title) return null
  return {
    id:title,
    title,
    instructor:'Wikiversity contributors',
    description:clean(textFromHtml(row?.snippet || ''), 500),
    pageTitle:title,
    url:pageUrl(title),
    raw:row,
  }
}

async function api(params = {}) {
  const query = new URLSearchParams({
    format:'json',
    formatversion:'2',
    origin:'*',
    ...Object.fromEntries(
      Object.entries(params).map(([key, value]) => [key, String(value)])
    ),
  })
  const { data } = await fetchJson(API + '?' + query)
  if (data?.error) throw new Error('Wikiversity API: ' + (data.error.info || data.error.code || 'request failed'))
  return data
}

async function search(query) {
  const data = await api({
    action:'query',
    list:'search',
    srsearch:query,
    srnamespace:0,
    srlimit:25,
    srprop:'snippet|wordcount|timestamp',
  })
  const items = (data?.query?.search || []).map(normalizeSearchRow).filter(Boolean)
  if (!items.length) throw new Error('Wikiversity returned no learning pages.')
  return items
}

function normalizeSections(data = {}) {
  const sections = Array.isArray(data?.parse?.sections) ? data.parse.sections : []
  return sections.slice(0, 100).flatMap(section => {
    const index = String(section?.index || '').trim()
    const title = clean(textFromHtml(section?.line || ''), 180)
    if (!index || !title) return []
    return [{
      id:index,
      title,
      section:clean(section?.anchor || '', 120),
      type:'text',
      pageTitle:String(data?.parse?.title || ''),
      sectionIndex:index,
    }]
  })
}

async function contents(item = {}) {
  const title = String(item?.pageTitle || item?.id || item?.title || '').trim()
  if (!title) throw new Error('Wikiversity page title is missing.')
  const data = await api({
    action:'parse',
    page:title,
    prop:'sections',
  })
  const sections = normalizeSections(data)
  if (sections.length) return sections
  return [{
    id:'0',
    title:clean(item?.title || title, 180),
    section:'',
    type:'text',
    pageTitle:title,
    sectionIndex:'0',
  }]
}

function parseBody(data = {}) {
  const html = String(data?.parse?.text || '')
  return textFromHtml(html)
}

async function pageDocument({ title, section = '' }) {
  const params = {
    action:'parse',
    page:title,
    prop:'text',
  }
  if (section) params.section = section
  const data = await api(params)
  const body = parseBody(data)
  if (body.length < 40) throw new Error('Wikiversity returned an empty learning section.')
  const sourceUrl = pageUrl(data?.parse?.title || title)
  const document = [
    clean(data?.parse?.title || title, 180),
    sourceUrl ? 'Source: ' + sourceUrl : '',
    'Attribution: Wikiversity contributors • CC BY-SA',
    '',
    body,
    '',
  ].filter((line, index) => line || index >= 3).join('\n')
  return Buffer.from(document, 'utf8')
}

export default {
  id:'wikiversity',
  name:'Wikiversity',
  description:'Open Wikimedia learning materials with selectable course/page sections.',
  fallbackOrder:20,

  async run({ action, query, item, content, context }) {
    if (action === 'search') return { items:await search(clean(query, 120)) }
    if (action === 'contents') return { contents:await contents(item || {}) }
    if (action === 'download') {
      const chosen = content || {}
      const title = String(chosen?.pageTitle || item?.pageTitle || item?.id || item?.title || '').trim()
      const section = String(chosen?.sectionIndex || chosen?.id || '').trim()
      const data = await pageDocument({ title, section })
      return sendDocument(context, {
        data,
        fileName:safeFileName(
          [clean(item?.title || title, 100), clean(chosen?.title || 'section', 80)].filter(Boolean).join(' - '),
          'txt',
        ),
        mimetype:'text/plain',
      })
    }
    if (action === 'downloadCourse') {
      const title = String(item?.pageTitle || item?.id || item?.title || '').trim()
      const data = await pageDocument({ title })
      return sendDocument(context, {
        data,
        fileName:safeFileName(clean(item?.title || title || 'Wikiversity course', 140), 'txt'),
        mimetype:'text/plain',
      })
    }
    throw new Error('Unsupported Wikiversity action: ' + action)
  },

  _test:{ pageUrl, normalizeSearchRow, normalizeSections, parseBody },
}
