import { writeFile, readFile, stat } from 'node:fs/promises'
import { join, isAbsolute } from 'node:path'
import { withMediaFiles, runMediaProcess } from './extended-media.js'
import { runFfmpeg } from './media-conversion.js'

// Wholly local subtitle generation via whisper.cpp. This never sends audio to Azure.
export async function makeLocalSubtitles(bytes, { language = 'auto' } = {}) {
  const selected = String(language).toLowerCase()
  if (!/^(auto|[a-z]{2,3})$/.test(selected)) throw new Error('Use .subtitles or .subtitles en (two-letter language code).')
  const model = String(process.env.MSCC_WHISPER_MODEL || '').trim()
  const binary = String(process.env.MSCC_WHISPER_BIN || 'whisper-cli').trim()
  if (!model || !isAbsolute(model)) {
    throw new Error('Local subtitle model is not installed. Configure MSCC_WHISPER_MODEL with the absolute path to a whisper.cpp GGML .bin model.')
  }
  if (!(await stat(model).catch(() => null))?.isFile()) {
    throw new Error('Local subtitle model file is missing. Check MSCC_WHISPER_MODEL.')
  }
  if (!Buffer.isBuffer(bytes) || bytes.length < 1 || bytes.length > 20 * 1024 * 1024) {
    throw new Error('Audio or video must be below 20 MB.')
  }
  return withMediaFiles('whisper-subtitles', async dir => {
    const input = join(dir, 'source.bin')
    const wav = join(dir, 'speech.wav')
    const prefix = join(dir, 'night-subtitles')
    await writeFile(input, bytes)
    await runFfmpeg([
      '-t', '90', '-i', input, '-vn', '-ar', '16000',
      '-ac', '1', '-c:a', 'pcm_s16le', wav,
    ], { timeoutMs: 60000 })
    await runMediaProcess(binary, [
      '-m', model, '-f', wav, '-l', selected, '-osrt', '-of', prefix,
      '-t', '2',
    ], { timeoutMs: 210000, maxStdout: 512000 })
    const srt = await readFile(prefix + '.srt', 'utf8').catch(() => '')
    if (!srt.trim() || !/-->/.test(srt)) throw new Error('No timestamped speech was detected.')
    const data = Buffer.from(srt, 'utf8')
    if (data.length > 400000) throw new Error('Subtitle file is unexpectedly large.')
    return data
  })
}
