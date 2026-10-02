import {
  absoluteUrl,
  clean,
  decodeHtml,
  fetchText,
  safeFileName,
  sendDocument,
  textFromHtml,
} from './_shared.js'

const BASE = 'https://freecoursesites.com/'
const DRIVE = 'https://drive.google.com'
const MAX_FILES = 160
const MAX_DEPTH = 6

function isCoursePage(value = '') {
  try {
    const url = new URL(String(value || ''), BASE)
    if (!/(?:^|\.)freecoursesites\.com$/i.test(url.hostname)) return false
    if (url.pathname === '/') return false
    if (/\/(?:category|tag|author|page|wp-admin|wp-content|wp-json)\//i.test(url.pathname)) return false
    return /^\/[a-z0-9][a-z0-9-]+\/?$/i.test(url.pathname)
  } catch {
    return false
  }
}

function parseSearchHtml(html = '', base = BASE) {
  const out = []
  const seen = new Set()
  const source = String(html || '')
  const patterns = [
    /<h[1-3]\b[^>]*class=["'][^"']*(?:entry-title|post-title)[^"']*["'][^>]*>[\s\S]*?<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>[\s\S]*?<\/h[1-3]>/gi,
    /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi,
  ]
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      const url = absoluteUrl(base, decodeHtml(match[1]))
      if (!url || !isCoursePage(url) || seen.has(url)) continue
      const title = clean(textFromHtml(match[2]), 180)
      if (!title || title.length < 8 || /^(?:read more|get course now|freecoursesite)$/i.test(title)) continue
      seen.add(url)
      out.push({
        id:url,
        title,
        instructor:'',
        description:'Course listing with downloadable public Google Drive content',
        url,
      })
      if (out.length >= 25) return out
    }
    if (out.length) break
  }
  return out
}

function parseDriveFolders(html = '') {
  const out = []
  const seen = new Set()
  const source = decodeHtml(String(html || ''))
  for (const match of source.matchAll(/https:\/\/drive\.google\.com\/drive\/folders\/([a-zA-Z0-9_-]{10,})[^"'<>\s]*/g)) {
    const id = match[1]
    if (seen.has(id)) continue
    let resourceKey = ''
    try {
      const url = new URL(match[0])
      resourceKey = clean(url.searchParams.get('resourcekey') || '', 200)
    } catch {}
    seen.add(id)
    out.push({ id, resourceKey, url:match[0] })
  }
  return out
}

function driveEntry(href = '', titleHtml = '') {
  const url = absoluteUrl(DRIVE, decodeHtml(href))
  if (!url) return null
  let parsed
  try { parsed = new URL(url) } catch { return null }
  if (parsed.hostname !== 'drive.google.com') return null

  const title = clean(textFromHtml(titleHtml), 220)
  if (!title) return null
  const resourceKey = clean(parsed.searchParams.get('resourcekey') || '', 200)

  let match = /\/file\/d\/([a-zA-Z0-9_-]{10,})/i.exec(parsed.pathname)
  if (match) {
    return {
      kind:'file',
      id:match[1],
      driveFileId:match[1],
      resourceKey,
      title,
      viewUrl:url,
    }
  }

  match = /\/drive\/folders\/([a-zA-Z0-9_-]{10,})/i.exec(parsed.pathname)
    || /\/folders\/([a-zA-Z0-9_-]{10,})/i.exec(parsed.pathname)
  if (match) {
    return {
      kind:'folder',
      id:match[1],
      driveFolderId:match[1],
      resourceKey,
      title,
      url,
    }
  }
  return null
}

function parseDriveEntries(html = '') {
  const out = []
  const seen = new Set()
  const source = decodeHtml(String(html || ''))
  const re = /<a\b[^>]*href=["'](https:\/\/drive\.google\.com\/[^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const body = match[2]
    const titleMatch = /<div\b[^>]*class=["'][^"']*\bflip-entry-title\b[^"']*["'][^>]*>([\s\S]*?)<\/div>/i.exec(body)
    if (!titleMatch) continue
    const entry = driveEntry(match[1], titleMatch[1])
    if (!entry) continue
    const key = `${entry.kind}:${entry.id}`
    if (seen.has(key)) continue
    seen.add(key)
    out.push(entry)
  }
  return out
}

function embeddedFolderUrl(folder = {}) {
  const params = new URLSearchParams({ id:String(folder.id || folder.driveFolderId || '') })
  const resourceKey = clean(folder.resourceKey || '', 200)
  if (resourceKey) params.set('resourcekey', resourceKey)
  return `${DRIVE}/embeddedfolderview?${params}#list`
}

function driveDownloadUrl(fileId, resourceKey = '') {
  const params = new URLSearchParams({
    id:String(fileId || ''),
    export:'download',
    confirm:'t',
  })
  if (resourceKey) params.set('resourcekey', clean(resourceKey, 200))
  return `https://drive.usercontent.google.com/download?${params}`
}

function fileExtension(name = '') {
  return /\.([a-z0-9]{1,8})$/i.exec(clean(name, 220))?.[1]?.toLowerCase() || ''
}

function mimeFor(name = '') {
  const map = {
    txt:'text/plain',
    pdf:'application/pdf',
    zip:'application/zip',
    rar:'application/vnd.rar',
    '7z':'application/x-7z-compressed',
    mp4:'video/mp4',
    mkv:'video/x-matroska',
    webm:'video/webm',
    mp3:'audio/mpeg',
    m4a:'audio/mp4',
    vtt:'text/vtt',
    srt:'application/x-subrip',
    epub:'application/epub+zip',
  }
  return map[fileExtension(name)] || 'application/octet-stream'
}

function deliveryFileName(name = 'course file') {
  const title = clean(name, 180) || 'course file'
  const ext = fileExtension(title)
  if (!ext) return safeFileName(title)
  return safeFileName(title.slice(0, -(ext.length + 1)), ext)
}

async function search(query) {
  const url = BASE + '?' + new URLSearchParams({ s:query })
  const { text, response } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const items = parseSearchHtml(text, response.url || url)
  if (!items.length) throw new Error('FreeCourseSite returned no course results.')
  return items
}

async function courseFolders(item = {}) {
  const url = absoluteUrl(BASE, item?.url || item?.id || '')
  if (!url || !isCoursePage(url)) throw new Error('FreeCourseSite course URL is missing.')
  const { text } = await fetchText(url, {
    headers:{ accept:'text/html,application/xhtml+xml' },
  })
  const folders = parseDriveFolders(text)
  if (!folders.length) throw new Error('FreeCourseSite course returned no public Google Drive folder.')
  return folders
}

async function walkFolder(folder, {
  path = '',
  depth = 0,
  seenFolders = new Set(),
  files = [],
} = {}) {
  if (depth > MAX_DEPTH || files.length >= MAX_FILES) return files
  const id = String(folder?.id || folder?.driveFolderId || '')
  if (!id || seenFolders.has(id)) return files
  seenFolders.add(id)

  const { text } = await fetchText(embeddedFolderUrl(folder), {
    headers:{ accept:'text/html,application/xhtml+xml' },
    timeoutMs:30000,
  })
  const entries = parseDriveEntries(text)
  for (const entry of entries) {
    if (files.length >= MAX_FILES) break
    if (entry.kind === 'folder') {
      const nextPath = [path, entry.title].filter(Boolean).join(' / ')
      await walkFolder(entry, { path:nextPath, depth:depth + 1, seenFolders, files })
      continue
    }
    const downloadUrl = driveDownloadUrl(entry.driveFileId, entry.resourceKey)
    files.push({
      id:entry.driveFileId,
      title:entry.title,
      section:path,
      type:fileExtension(entry.title) || 'file',
      url:downloadUrl,
      downloadUrl,
      viewUrl:entry.viewUrl,
      driveFileId:entry.driveFileId,
      resourceKey:entry.resourceKey,
    })
  }
  return files
}

async function contents(item = {}) {
  const folders = await courseFolders(item)
  const files = []
  const seenFolders = new Set()
  for (let i = 0; i < folders.length && files.length < MAX_FILES; i += 1) {
    await walkFolder(folders[i], {
      path:folders.length > 1 ? `Folder ${i + 1}` : '',
      seenFolders,
      files,
    })
  }
  if (!files.length) throw new Error('FreeCourseSite course folder returned no downloadable files.')
  return files
}

async function download(content = {}, context = {}) {
  const fileId = clean(content?.driveFileId || content?.id || '', 200)
  const url = content?.downloadUrl || (fileId ? driveDownloadUrl(fileId, content?.resourceKey || '') : '')
  if (!url) throw new Error('FreeCourseSite course file URL is missing.')
  const title = clean(content?.title || 'course file', 180)
  return sendDocument(context, {
    url,
    fileName:deliveryFileName(title),
    mimetype:mimeFor(title),
  })
}

export default {
  id:'freecoursesite',
  name:'FreeCourseSite',
  description:'Course catalog resolving selectable files from public Google Drive folders.',
  fallbackOrder:40,

  async run({ action, query, item, content, context }) {
    if (action === 'search') return { items:await search(clean(query, 120)) }
    if (action === 'contents') return { contents:await contents(item || {}) }
    if (action === 'download') return download(content || {}, context)
    if (action === 'downloadCourse') {
      const files = await contents(item || {})
      if (files.length === 1) return download(files[0], context)
      return { text:'Choose the course part(s) you want to download.' }
    }
    throw new Error('Unsupported FreeCourseSite action: ' + action)
  },

  _test:{
    isCoursePage,
    parseSearchHtml,
    parseDriveFolders,
    parseDriveEntries,
    embeddedFolderUrl,
    driveDownloadUrl,
    mimeFor,
    deliveryFileName,
  },
}
