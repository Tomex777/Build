import { appendFile, rm, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { clean, fetchJson, fetchText, safeFileName, sendDocument, textFromHtml } from './_shared.js'

const API = 'https://api.novelbuddy.me'
const SITE = 'https://novelbuddy.me'

function searchItems(data) {
  const rows = Array.isArray(data?.data?.items) ? data.data.items : []
  return rows.slice(0, 25).flatMap(item => {
    const id = String(item?.id || '').trim()
    const title = clean(item?.name || '', 180)
    const url = String(item?.url || '').trim()
    if (!id || !title || !url) return []
    const author = Array.isArray(item?.authors)
      ? clean(item.authors.map(value => value?.name || value).filter(Boolean).join(', '), 140)
      : clean(item?.author || item?.authorName || '', 140)
    return [{
      id,
      title,
      author,
      cover:item?.cover ? (String(item.cover).startsWith('http') ? String(item.cover) : SITE + '/' + String(item.cover).replace(/^\//, '')) : '',
      url:url.startsWith('http') ? url : SITE + '/' + url.replace(/^\//, ''),
      slug:clean(item?.slug || '', 160),
      cv:Number(item?.cv || item?.content_version || 0) || 0,
    }]
  })
}

async function search(query) {
  const { data } = await fetchJson(API + '/titles/search?' + new URLSearchParams({
    q:query,
    limit:'24',
    page:'1',
  }), {
    headers:{ origin:SITE, referer:SITE + '/' },
  })
  const items = searchItems(data)
  if (!items.length) throw new Error('NovelBuddy returned no novels.')
  return items
}

function nextData(html) {
  const match = /<script[^>]*id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i.exec(String(html || ''))
  if (!match) return null
  try { return JSON.parse(match[1]) } catch { return null }
}

async function details(item = {}) {
  const url = String(item?.url || '').trim()
  if (!url) return { id:String(item?.id || ''), title:item?.title || '', author:item?.author || '', cv:Number(item?.cv || 0) || 0 }
  try {
    const { text } = await fetchText(url, {
      headers:{ accept:'text/html,application/xhtml+xml', referer:SITE + '/' },
      timeoutMs:15000,
    })
    const data = nextData(text)
    const manga = data?.props?.pageProps?.initialManga
    if (!manga) throw new Error('NovelBuddy details missing')
    return {
      id:String(manga.id || item?.id || ''),
      title:clean(manga.name || item?.title || '', 180),
      author:Array.isArray(manga.authors) ? clean(manga.authors.map(author => author?.name).filter(Boolean).join(', '), 140) : clean(item?.author || '', 140),
      cover:manga.cover ? (String(manga.cover).startsWith('http') ? String(manga.cover) : SITE + '/' + String(manga.cover).replace(/^\//, '')) : String(item?.cover || ''),
      cv:Number(manga.content_version || manga.cv || item?.cv || 0) || 0,
      url,
    }
  } catch {
    return { id:String(item?.id || ''), title:item?.title || '', author:item?.author || '', cover:String(item?.cover || ''), cv:Number(item?.cv || 0) || 0, url }
  }
}

async function chapters(novelId, cv = 0) {
  if (!novelId) throw new Error('NovelBuddy novel ID is missing.')
  const suffix = cv ? '?cv=' + encodeURIComponent(String(cv)) : ''
  const { data } = await fetchJson(API + '/titles/' + encodeURIComponent(novelId) + '/chapters' + suffix, {
    headers:{ origin:SITE, referer:SITE + '/' },
    timeoutMs:20000,
  })
  const rows = Array.isArray(data?.data?.chapters) ? data.data.chapters : []
  if (!rows.length) throw new Error('NovelBuddy returned no chapters.')
  return rows.map(chapter => ({
    id:String(chapter?.id || '').trim(),
    name:clean(chapter?.name || 'Chapter', 180),
    url:String(chapter?.url || '').trim(),
  })).filter(chapter => chapter.id).reverse()
}

async function chapterText(novelId, chapter) {
  try {
    const { data } = await fetchJson(
      API + '/titles/' + encodeURIComponent(novelId) + '/chapters/' + encodeURIComponent(chapter.id),
      {
        headers:{ origin:SITE, referer:SITE + '/' },
        timeoutMs:20000,
      },
    )
    const content = data?.data?.chapter?.content
    const text = textFromHtml(content || '')
    if (text) return text
  } catch {}

  const url = chapter.url
    ? (chapter.url.startsWith('http') ? chapter.url : SITE + '/' + chapter.url.replace(/^\//, ''))
    : ''
  if (!url) throw new Error('NovelBuddy chapter content is unavailable: ' + chapter.name)
  const { text:html } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml', referer:SITE + '/' },
    timeoutMs:20000,
  })
  const data = nextData(html)
  const content = data?.props?.pageProps?.initialChapter?.content || ''
  const text = textFromHtml(content)
  if (!text) throw new Error('NovelBuddy chapter content is unavailable: ' + chapter.name)
  return text
}

async function chapterRows(item) {
  const novel = await details(item)
  const rows = await chapters(novel.id, novel.cv)
  return { novel, chapters:rows }
}

async function buildNovelTxt(item, selectedChapters = []) {
  const novel = await details(item || {})
  const rows = (Array.isArray(selectedChapters) ? selectedChapters : [])
    .map(chapter => ({
      id:String(chapter?.id || '').trim(),
      name:clean(chapter?.name || chapter?.title || 'Chapter', 180),
      url:String(chapter?.url || '').trim(),
    }))
    .filter(chapter => chapter.id)
  if (!rows.length) throw new Error('No NovelBuddy chapters were selected.')

  const file = join(tmpdir(), 'mscc-novelbuddy-' + randomUUID() + '.txt')
  const heading = [
    novel.title || item?.title || 'Novel',
    novel.author ? 'By ' + novel.author : '',
    '',
    'Chapters: ' + rows.length,
    '',
  ].filter((value, index) => value || index === 2).join('\n')
  await writeFile(file, heading + '\n', 'utf8')

  try {
    const batchSize = 4
    for (let i = 0; i < rows.length; i += batchSize) {
      const batch = rows.slice(i, i + batchSize)
      const texts = await Promise.all(batch.map(chapter => chapterText(novel.id, chapter)))
      let block = ''
      for (let j = 0; j < batch.length; j++) {
        block += '\n\n' + batch[j].name + '\n' + '='.repeat(Math.min(72, Math.max(8, batch[j].name.length))) + '\n\n'
        block += texts[j].trim() + '\n'
      }
      await appendFile(file, block, 'utf8')
    }
    return {
      file,
      title:novel.title || item?.title || 'Novel',
      author:novel.author || item?.author || '',
    }
  } catch (error) {
    await rm(file, { force:true }).catch(() => {})
    throw error
  }
}

export default {
  id:'novelbuddy',
  name:'NovelBuddy',
  description:'Web-novel search and complete TXT export from the current chapter API.',

  async run({ action, query, item, chapters:selectedChapters = [], context }) {
    if (action === 'search') return { items:await search(clean(query, 180)) }
    if (action === 'editions') {
      const result = await chapterRows(item)
      return {
        chapters:result.chapters,
        chapterCount:result.chapters.length,
        novel:result.novel,
      }
    }

    if (action === 'download-chapters') {
      const built = await buildNovelTxt(item || {}, selectedChapters)
      try {
        return await sendDocument(context, {
          file:built.file,
          mimetype:'text/plain',
          fileName:safeFileName([built.title, built.author].filter(Boolean).join(' - '), 'txt'),
        })
      } finally {
        await rm(built.file, { force:true }).catch(() => {})
      }
    }

    throw new Error('Unsupported NovelBuddy action: ' + action)
  },

  _test:{ searchItems, nextData },
}
