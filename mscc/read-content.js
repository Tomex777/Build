import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import AdmZip from 'adm-zip'
import XLSX from 'xlsx'
import * as cheerio from 'cheerio'
import { downloadCommandMedia, resolveCommandMedia } from './utils/media-conversion.js'
import { azureVisionAsk } from './azure-media.js'

function clean(value, max = 32000) {
  return String(value || '').replace(/\u0000/g,'').trim().slice(0,max)
}

async function withTemp(label, work) {
  const dir = await mkdtemp(join(tmpdir(), 'night-' + label + '-'))
  try { return await work(dir) }
  finally { await rm(dir, { recursive:true, force:true }).catch(() => {}) }
}

function stripXml(value) {
  const $ = cheerio.load(String(value || ''), { xmlMode:true })
  $('w\\:tab').replaceWith(' ')
  $('w\\:br').replaceWith('\n')
  const rows = []
  $('w\\:p').each((_, node) => {
    const text = $(node).find('w\\:t').map((__, t) => $(t).text()).get().join('')
    if (text.trim()) rows.push(text.trim())
  })
  return rows.join('\n')
}

async function pdfText(buffer) {
  return withTemp('pdf', async dir => {
    const input = join(dir, 'input.pdf')
    const output = join(dir, 'output.txt')
    await writeFile(input, buffer)
    await new Promise((resolve, reject) => {
      const child = spawn(String(process.env.PDFTOTEXT_PATH || 'pdftotext'), ['-layout', input, output], {
        stdio:['ignore','ignore','pipe'],
      })
      let stderr = ''
      child.stderr.on('data', chunk => { stderr += chunk.toString() })
      child.once('error', error => {
        if (error?.code === 'ENOENT') reject(new Error('PDF text extraction is not installed on the Night host yet.'))
        else reject(error)
      })
      child.once('close', code => code === 0 ? resolve() : reject(new Error('PDF extraction failed: ' + stderr.trim())))
    })
    return clean(await readFile(output, 'utf8'))
  })
}

function docxText(buffer) {
  const zip = new AdmZip(buffer)
  const entry = zip.getEntry('word/document.xml')
  if (!entry) throw new Error('That DOCX has no readable document body.')
  return clean(stripXml(entry.getData().toString('utf8')))
}

function xlsxText(buffer) {
  const workbook = XLSX.read(buffer, { type:'buffer' })
  const chunks = []
  for (const name of workbook.SheetNames.slice(0,5)) {
    const sheet = workbook.Sheets[name]
    chunks.push('[' + name + ']\n' + XLSX.utils.sheet_to_csv(sheet))
  }
  return clean(chunks.join('\n\n'))
}

async function urlText(url) {
  const parsed = new URL(String(url || '').trim())
  if (!['http:','https:'].includes(parsed.protocol)) throw new Error('Only HTTP(S) article links are supported.')
  const response = await fetch(parsed, {
    headers:{ 'user-agent':'Mozilla/5.0 Night/2.3', accept:'text/html,text/plain;q=0.9,*/*;q=0.1' },
    redirect:'follow',
    signal:AbortSignal.timeout(20000),
  })
  const body = await response.text()
  if (!response.ok) throw new Error('Article returned HTTP ' + response.status + '.')
  const type = String(response.headers.get('content-type') || '')
  if (type.includes('text/plain')) return clean(body)
  const $ = cheerio.load(body)
  $('script,style,noscript,nav,footer,header,aside').remove()
  const title = $('title').first().text().trim()
  const text = $('article').first().text().trim() || $('main').first().text().trim() || $('body').text().trim()
  return clean((title ? title + '\n\n' : '') + text.replace(/\s+/g,' '))
}

export async function readCommandContent(ctx, {
  question = '',
} = {}) {
  const raw = String(ctx.args?.[0] || '').trim()
  if (/^https?:\/\//i.test(raw)) {
    return { kind:'text', text:await urlText(raw), source:'url' }
  }

  const resolved = resolveCommandMedia(ctx)
  if (!resolved) throw new Error('Reply to an image/document, or give me an article URL.')

  if (resolved.kind === 'image') {
    const media = await downloadCommandMedia(ctx, ['image'])
    const mime = String(media?.found?.media?.mimetype || 'image/jpeg')
    const answer = await azureVisionAsk(media.buffer, {
      question:question || 'Read the visible text and summarize the important content. Be precise and do not invent missing text.',
      mimeType:mime,
    })
    return { kind:'answer', text:answer, source:'image' }
  }

  if (resolved.kind !== 'document') throw new Error('Reply to an image or document for .read.')

  const media = await downloadCommandMedia(ctx, ['document'])
  const mime = String(media?.found?.media?.mimetype || '').toLowerCase()
  const name = String(media?.found?.media?.fileName || '').toLowerCase()
  let text = ''

  if (mime.includes('pdf') || name.endsWith('.pdf')) text = await pdfText(media.buffer)
  else if (mime.includes('wordprocessingml') || name.endsWith('.docx')) text = docxText(media.buffer)
  else if (mime.includes('spreadsheetml') || name.endsWith('.xlsx') || name.endsWith('.xls')) text = xlsxText(media.buffer)
  else if (
    mime.startsWith('text/') ||
    mime.includes('json') ||
    mime.includes('csv') ||
    /\.(txt|md|csv|json|log|xml|html?)$/.test(name)
  ) text = clean(media.buffer.toString('utf8'))
  else throw new Error('That document type is not readable yet.')

  if (!text) throw new Error('I could not extract readable text from that document.')
  return { kind:'text', text, source:'document' }
}
