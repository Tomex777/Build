import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { PDFDocument } from 'pdf-lib'
import QRCode from 'qrcode'
import { runMediaWorker, processAudio, cutAudioSilence } from './utils/media-toolkit.js'
import { makeLocalSubtitles } from './utils/subtitle-tools.js'
import { writeFile, chmod } from 'node:fs/promises'
import { loadCommands } from './command-registry.js'

const commands = await loadCommands(new URL('./commands/', import.meta.url), { capabilityFromDirectory: true })
for (const name of [
  'pdfmerge', 'pdfsplit', 'img2pdf', 'pdf2img', 'pdfcompress',
  'denoise', 'clearaudio', 'extractaudio', 'scanqr',
  'stripmeta', 'silencecut', 'subtitles',
]) assert.ok(commands.commands.has(name), 'New command missing: ' + name)

const doc = await PDFDocument.create()
doc.addPage([200, 280])
doc.addPage([250, 300])
const pdf = await doc.save()

const merged = await runMediaWorker('pdfmerge', { inputs: [pdf, pdf] })
assert.equal(merged.length, 1)
assert.equal((await PDFDocument.load(merged[0].bytes)).getPageCount(), 4)

const selected = await runMediaWorker('pdfsplit', { input: merged[0].bytes, pages: '2-3' })
assert.equal((await PDFDocument.load(selected[0].bytes)).getPageCount(), 2)

const chunks = await runMediaWorker('pdfsplit', { input: merged[0].bytes, pages: 'every 2' })
assert.equal(chunks.length, 2)
assert.equal((await PDFDocument.load(chunks[0].bytes)).getPageCount(), 2)

const qrPng = await QRCode.toBuffer('https://example.org/night-qr-test', {
  width: 320, margin: 4, type: 'png',
})
assert.equal(await runMediaWorker('scanqr', { input: qrPng }), 'https://example.org/night-qr-test')
const scrubbed = await runMediaWorker('stripmeta', { input: qrPng })
assert.equal(scrubbed.length, 1)
assert.ok(Buffer.from(scrubbed[0].bytes).length > 100)
assert.equal(scrubbed[0].mime, 'image/png')
const generated = await runMediaWorker('img2pdf', { inputs: [qrPng, qrPng] })
assert.equal((await PDFDocument.load(generated[0].bytes)).getPageCount(), 2)

const exists = cmd => spawnSync('sh', ['-c', 'command -v ' + cmd + ' >/dev/null 2>&1']).status === 0
if (exists('pdftoppm')) {
  const images = await runMediaWorker('pdf2img', { input: pdf, pages: '1-2' })
  assert.equal(images.length, 2)
  assert.equal(images[0].mime, 'image/png')
  assert.ok(Buffer.from(images[0].bytes).subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])))
} else console.log('SKIP pdf2img integration: pdftoppm missing')

if (exists('gs')) {
  const output = await runMediaWorker('pdfcompress', { input: pdf })
  assert.equal(output.length, 1)
  assert.ok((await PDFDocument.load(output[0].bytes)).getPageCount() === 2)
} else console.log('SKIP pdfcompress integration: Ghostscript missing')

if (exists('ffmpeg')) {
  const dir = await mkdtemp(join(tmpdir(), 'night-media-selftest-'))
  try {
    const wav = join(dir, 'input.wav'), mp4 = join(dir, 'input.mp4')
    let process = spawnSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y',
      '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
      wav], { timeout: 12000 })
    assert.equal(process.status, 0, String(process.stderr))
    const audio = await readFile(wav)
    assert.ok((await cutAudioSilence(audio, { mode: 'gentle' })).length > 1000)
    assert.ok((await cutAudioSilence(audio, { mode: 'normal' })).length > 1000)
    assert.ok((await cutAudioSilence(audio, { mode: 'aggressive' })).length > 1000)
    const mockModel = join(dir, 'mock-model.bin')
    const mockCli = join(dir, 'mock-whisper.sh')
    await writeFile(mockModel, 'test-only')
    await writeFile(mockCli, '#!/bin/sh\n' +
      'while [ "$#" -gt 0 ]; do\n' +
      '  if [ "$1" = "-of" ]; then shift; out="$1"; fi\n' +
      '  shift\n' +
      'done\n' +
      'printf "1\\n00:00:00,000 --> 00:00:00,900\\nTest caption\\n" > "$out.srt"\n')
    await chmod(mockCli, 0o700)
    const priorBin = process.env.MSCC_WHISPER_BIN
    const priorModel = process.env.MSCC_WHISPER_MODEL
    try {
      process.env.MSCC_WHISPER_BIN = mockCli
      process.env.MSCC_WHISPER_MODEL = mockModel
      assert.match(String(await makeLocalSubtitles(audio)), /Test caption/)
    } finally {
      if (priorBin === undefined) delete process.env.MSCC_WHISPER_BIN
      else process.env.MSCC_WHISPER_BIN = priorBin
      if (priorModel === undefined) delete process.env.MSCC_WHISPER_MODEL
      else process.env.MSCC_WHISPER_MODEL = priorModel
    }
    for (const mode of ['denoise', 'natural', 'auto', 'strong']) {
      assert.ok((await processAudio(audio, { mode })).length > 1000, 'Audio filter failed: ' + mode)
    }
    process = spawnSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y',
      '-f', 'lavfi', '-i', 'color=c=blue:s=160x120:r=10:d=1',
      '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1',
      '-shortest', '-c:v', 'mpeg4', '-c:a', 'aac', mp4], { timeout: 12000 })
    assert.equal(process.status, 0, String(process.stderr))
    assert.ok((await processAudio(await readFile(mp4), { video: true })).length > 1000)
  } finally { await rm(dir, { recursive: true, force: true }) }
} else console.log('SKIP audio integration: FFmpeg missing')

console.log('PASS NIGHT document, voice, metadata and subtitle commands end-to-end')
