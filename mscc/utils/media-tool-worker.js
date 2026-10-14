import { parentPort, workerData } from 'node:worker_threads'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawn } from 'node:child_process'
import { PDFDocument } from 'pdf-lib'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import jsQR from 'jsqr'

const MAX_OUTPUT = 19 * 1024 * 1024
const MAX_PAGES = 180
const asBytes = data => Buffer.from(data || [])
const pdfOutput = (bytes, name, caption = '') => ({ bytes: asBytes(bytes), mime: 'application/pdf', name, caption })

function withinLimit(bytes) {
  if (!bytes?.length || bytes.length > MAX_OUTPUT) throw new Error('Output exceeds 19 MB. Use smaller files.')
  return bytes
}
async function loadPdf(bytes) {
  const data = asBytes(bytes)
  if (data.length < 10 || data.length > 10 * 1024 * 1024 || data.subarray(0, 5).toString() !== '%PDF-') {
    throw new Error('Provide a PDF smaller than 10 MB.')
  }
  const pdf = await PDFDocument.load(data, { updateMetadata: false })
  if (pdf.getPageCount() > MAX_PAGES) throw new Error('PDF exceeds the 180-page safety limit.')
  return pdf
}

function selection(raw, total, max = 40) {
  const arg = String(raw || '').replace(/\s+/g, '')
  if (!arg || !/^[\d,-]+$/.test(arg)) throw new Error('Use page numbers like 1-3,5,8.')
  const picked = new Set()
  for (const part of arg.split(',')) {
    if (!part) throw new Error('Invalid page selection.')
    const match = /^(\d+)(?:-(\d+))?$/.exec(part)
    if (!match) throw new Error('Invalid page selection: ' + part)
    const first = Number(match[1]), last = Number(match[2] || first)
    if (first < 1 || last < first || last > total) throw new Error('Page out of range (1-' + total + ').')
    for (let i = first; i <= last; i++) {
      picked.add(i)
      if (picked.size > max) throw new Error('Choose no more than ' + max + ' pages.')
    }
  }
  return [...picked].sort((a, b) => a - b)
}
async function copyPages(input, pageNumbers) {
  const result = await PDFDocument.create()
  const copies = await result.copyPages(input, pageNumbers.map(i => i - 1))
  for (const page of copies) result.addPage(page)
  return withinLimit(await result.save())
}
async function mergePdf({ inputs }) {
  if (!Array.isArray(inputs) || inputs.length < 2 || inputs.length > 8) throw new Error('Merge 2 to 8 PDFs.')
  const result = await PDFDocument.create()
  let count = 0
  for (const bytes of inputs) {
    const source = await loadPdf(bytes)
    count += source.getPageCount()
    if (count > MAX_PAGES) throw new Error('Merged PDF exceeds 180 pages.')
    for (const page of await result.copyPages(source, source.getPageIndices())) result.addPage(page)
  }
  return [pdfOutput(withinLimit(await result.save()), 'night-merged.pdf', '📄 Combined ' + inputs.length + ' PDFs.')]
}
async function splitPdf({ input, pages }) {
  const pdf = await loadPdf(input)
  const total = pdf.getPageCount()
  const arg = String(pages || '').trim().toLowerCase()
  if (arg.startsWith('every ')) {
    const n = Number(arg.slice(6))
    if (!Number.isInteger(n) || n < 1 || n > 50) throw new Error('Use .pdfsplit every <1-50>.')
    const chunks = Math.ceil(total / n)
    if (chunks > 8) throw new Error('That creates more than 8 files. Increase the pages per file.')
    const results = []
    for (let start = 1; start <= total; start += n) {
      const end = Math.min(total, start + n - 1)
      const indices = Array.from({ length: end - start + 1 }, (_, i) => start + i)
      results.push(pdfOutput(await copyPages(pdf, indices), 'night-pages-' + start + '-' + end + '.pdf', '📄 Pages ' + start + '–' + end))
    }
    return results
  }
  const chosen = selection(arg, total)
  return [pdfOutput(await copyPages(pdf, chosen), 'night-split.pdf', '📄 Extracted ' + chosen.length + ' page(s).')]
}

async function imagesToPdf({ inputs }) {
  if (!Array.isArray(inputs) || inputs.length < 1 || inputs.length > 8) throw new Error('Choose 1 to 8 images.')
  const doc = await PDFDocument.create()
  for (const raw of inputs) {
    const buffer = asBytes(raw)
    if (!buffer.length || buffer.length > 8 * 1024 * 1024) throw new Error('Each image must be smaller than 8 MB.')
    const image = await loadImage(buffer)
    const maxSide = 2200
    const ratio = Math.min(1, maxSide / Math.max(image.width, image.height))
    const w = Math.max(1, Math.round(image.width * ratio))
    const h = Math.max(1, Math.round(image.height * ratio))
    const canvas = createCanvas(w, h)
    const g = canvas.getContext('2d')
    g.fillStyle = '#ffffff'
    g.fillRect(0, 0, w, h)
    g.drawImage(image, 0, 0, w, h)
    const encoded = await doc.embedJpg(canvas.toBuffer('image/jpeg', 85))
    const page = doc.addPage([595.28, 841.89]) // A4
    const fit = Math.min((595.28 - 40) / w, (841.89 - 40) / h)
    const dw = w * fit, dh = h * fit
    page.drawImage(encoded, { x: (595.28 - dw) / 2, y: (841.89 - dh) / 2, width: dw, height: dh })
  }
  return [pdfOutput(withinLimit(await doc.save()), 'night-images.pdf', '📄 ' + inputs.length + ' image(s) converted to A4 PDF.')]
}

async function command(bin, args, timeoutMs = 75000) {
  await new Promise((resolve, reject) => {
    const proc = spawn(bin, args, { stdio: ['ignore', 'ignore', 'pipe'] })
    let done = false, err = '', expired = false
    const finish = error => {
      if (done) return
      done = true
      clearTimeout(timer)
      if (error) reject(error)
      else resolve()
    }
    const timer = setTimeout(() => { expired = true; proc.kill('SIGKILL') }, timeoutMs)
    timer.unref?.()
    proc.stderr.on('data', chunk => { err = (err + chunk.toString()).slice(-1500) })
    proc.once('error', error => finish(error?.code === 'ENOENT'
      ? new Error(bin + ' is not installed on the Night server.') : error))
    proc.once('close', code => finish(expired
      ? new Error('Conversion timed out.')
      : code === 0 ? null : new Error(err.slice(-350) || (bin + ' failed.'))))
  })
}

async function withTemp(work) {
  const directory = await mkdtemp(join(tmpdir(), 'night-pdf-'))
  try { return await work(directory) }
  finally { await rm(directory, { recursive: true, force: true }).catch(() => {}) }
}
async function pdfToImages({ input, pages }) {
  const pdf = await loadPdf(input)
  const total = pdf.getPageCount()
  const arg = String(pages || '1').trim().toLowerCase()
  const chosen = arg === 'all' ? Array.from({ length: total }, (_, i) => i + 1) : selection(arg, total, 5)
  if (chosen.length > 5) throw new Error('Convert at most five pages per request.')
  return withTemp(async dir => {
    const source = join(dir, 'input.pdf')
    await writeFile(source, asBytes(input))
    const results = []
    for (const i of chosen) {
      const destination = join(dir, 'page-' + i)
      await command(process.env.PDFTOPPM_PATH || 'pdftoppm',
        ['-f', String(i), '-l', String(i), '-singlefile', '-scale-to', '1400', '-png', source, destination], 30000)
      const bytes = withinLimit(await readFile(destination + '.png'))
      results.push({ bytes, mime: 'image/png', name: 'page-' + i + '.png',
        caption: '🖼️ Page ' + i + '/' + total, asImage: true })
    }
    return results
  })
}
async function compressPdf({ input }) {
  await loadPdf(input)
  return withTemp(async dir => {
    const source = join(dir, 'in.pdf'), destination = join(dir, 'compressed.pdf')
    await writeFile(source, asBytes(input))
    await command(process.env.GS_PATH || 'gs', [
      '-dSAFER', '-dBATCH', '-dNOPAUSE', '-q', '-sDEVICE=pdfwrite',
      '-dCompatibilityLevel=1.5', '-dPDFSETTINGS=/ebook',
      '-dDetectDuplicateImages=true', '-dCompressFonts=true',
      '-sOutputFile=' + destination, source,
    ], 90000)
    const compressed = withinLimit(await readFile(destination))
    const original = asBytes(input)
    const saved = original.length - compressed.length
    return [pdfOutput(saved > 0 ? compressed : original, 'night-compressed.pdf',
      saved > 0 ? '📄 Reduced by ' + Math.round(saved / original.length * 100) + '%.' : '📄 This PDF is already sufficiently compressed.')]
  })
}
async function scanQr({ input }) {
  const bytes = asBytes(input)
  if (!bytes.length || bytes.length > 8 * 1024 * 1024) throw new Error('Image is too large.')
  const image = await loadImage(bytes)
  const factor = Math.min(1, 1500 / Math.max(image.width, image.height))
  const w = Math.max(1, Math.round(image.width * factor)), h = Math.max(1, Math.round(image.height * factor))
  const canvas = createCanvas(w, h), g = canvas.getContext('2d')
  g.drawImage(image, 0, 0, w, h)
  const rgba = g.getImageData(0, 0, w, h)
  const decoded = jsQR(rgba.data, w, h, { inversionAttempts: 'attemptBoth' })
  if (!decoded?.data) throw new Error('No readable QR code was detected.')
  return String(decoded.data).slice(0, 2500)
}

export async function runWorkerTask(task, payload) {
  switch (task) {
    case 'pdfmerge': return mergePdf(payload)
    case 'pdfsplit': return splitPdf(payload)
    case 'img2pdf': return imagesToPdf(payload)
    case 'pdf2img': return pdfToImages(payload)
    case 'pdfcompress': return compressPdf(payload)
    case 'scanqr': return scanQr(payload)
    default: throw new Error('Unknown document task.')
  }
}
if (parentPort) {
  runWorkerTask(workerData.task, workerData.payload)
    .then(result => parentPort.postMessage({ ok: true, result }))
    .catch(error => parentPort.postMessage({ ok: false, error: String(error?.message || error) }))
}
