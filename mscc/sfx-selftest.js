import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  SFX_PRESETS, SFX_DEFAULT_INTENSITY, SFX_MAX_INPUT_BYTES,
  parseSfxRequest, sfxFilter, sfxHelp, renderSfx,
} from './utils/sfx-engine.js'

assert.equal(parseSfxRequest([]).type, 'help')
assert.equal(parseSfxRequest(['list']).type, 'help')
assert.deepEqual(parseSfxRequest(['DEEP', '70']), { type: 'process', names: ['deep'], intensity: 70 })
assert.deepEqual(parseSfxRequest(['deep+echo']), { type: 'process', names: ['deep', 'echo'], intensity: SFX_DEFAULT_INTENSITY })
for (const args of [['deep+deep'], ['deep+helium'], ['boss+echo'], ['radio+megaphone'], ['echo+cathedral'], ['deep', '-1'], ['deep', '101'], ['deep', 'NaN'], ['deep', '5.5'], ['notreal'], ['deep+echo+radio'], ['deep', '60', 'extra']]) {
  assert.throws(() => parseSfxRequest(args), undefined, JSON.stringify(args))
}
const names = Object.keys(SFX_PRESETS)
assert.equal(names.length >= 20, true, 'SFX must offer a sizeable, carefully differentiated collection')
assert.equal(new Set(names).size, names.length)
for (const [name, info] of Object.entries(SFX_PRESETS)) {
  assert.ok(info.description && info.family && info.tags.length, `${name}: identity missing`)
  assert.ok(sfxHelp().includes(name), `${name}: missing from help`)
}
// Changes of intensity must not be separate preset names (e.g., helium2, drunk2).
assert.ok(names.every(n => !/\d+$/.test(n) || n === '8bit'))
assert.ok(!names.some(n => ['reverse', 'backwards', 'chipmunk', 'tiny', 'squirrel', 'walkie', 'arcade', 'pixel', 'clearaudio', 'denoise'].includes(n)))
const graphs = names.map(name => sfxFilter([name]))
assert.equal(new Set(graphs).size, names.length, 'Two presets have exactly the same FFmpeg processing graph')
assert.ok(sfxFilter(['deep', 'echo'], 70).includes('aecho='))
assert.equal(sfxFilter(['deep'], 0).includes('asetrate='), false)
assert.throws(() => sfxFilter(['notreal']))
await assert.rejects(renderSfx(Buffer.alloc(SFX_MAX_INPUT_BYTES + 1), { names: ['deep'] }), /20 MB/)

// Signal-processing integration: exercise every preset via actual offline FFmpeg.
const ffmpeg = spawnSync('ffmpeg', ['-version'], { timeout: 6000 })
if (ffmpeg.status === 0) {
  const dir = await mkdtemp(join(tmpdir(), 'night-sfx-selftest-'))
  try {
    const wav = join(dir, 'voice.wav')
    // Harmonically rich, AM-modulated signal resembles speech better than a single tone.
    const signal = 'aevalsrc=0.18*(sin(2*PI*145*t)+0.4*sin(2*PI*470*t)+0.22*sin(2*PI*1030*t))*(0.75+0.2*sin(2*PI*3*t)):s=48000:d=2'
    const job = spawnSync('ffmpeg', ['-hide_banner','-loglevel','error','-y','-f','lavfi','-i',signal,'-c:a','pcm_s16le',wav], { timeout: 15000 })
    assert.equal(job.status, 0, String(job.stderr))
    const sample = await readFile(wav)
    const runner = async (args, { timeoutMs = 90000 } = {}) => {
      const run = spawnSync('ffmpeg', ['-hide_banner','-loglevel','error','-y',...args], { timeout: timeoutMs })
      assert.equal(run.status, 0, String(run.stderr))
    }
    const hashes = new Map()
    for (const name of names) {
      const audio = await renderSfx(sample, { names: [name], runner })
      assert.ok(audio.length > 1500 && audio.length < 2 * 1024 * 1024, `${name}: output size`)
      assert.equal(audio.subarray(0, 4).toString(), 'OggS', `${name}: not a WhatsApp-compatible Ogg audio payload`)
      const hash = createHash('sha256').update(audio).digest('hex')
      assert.ok(!hashes.has(hash), `${name} produced identical bytes to ${hashes.get(hash)}`)
      hashes.set(hash, name)
    }
    const combined = await renderSfx(sample, { names: ['deep', 'echo'], intensity: 78, runner })
    assert.equal(combined.subarray(0, 4).toString(), 'OggS')
    assert.notEqual(createHash('sha256').update(combined).digest('hex'), [...hashes.keys()][0])
    for (const level of [0, 100]) {
      const result = await renderSfx(sample, { names: ['alien'], intensity: level, runner })
      assert.equal(result.subarray(0, 4).toString(), 'OggS')
    }
    console.log(`PASS NIGHT SFX: ${names.length} distinct FFmpeg processing outputs, combined effects, intensity limits and Ogg/Opus delivery`)
  } finally { await rm(dir, { recursive: true, force: true }) }
} else {
  console.log('SKIP NIGHT SFX FFmpeg integration: ffmpeg unavailable (registry and syntax tests passed)')
}
