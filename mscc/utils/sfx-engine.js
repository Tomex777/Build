import { mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

// Perceptual identities are intentionally more important than the number of names.
// Avoid synonyms of existing .reverse, .clearaudio, .denoise and .silencecut.
const pct = v => Number(v.toFixed(3))
const pitch = ratio => `asetrate=${pct(48000 * ratio)},aresample=48000,atempo=${pct(1 / ratio)}`
const preset = (family, description, build, tags = []) => Object.freeze({ family, description, build, tags })

export const SFX_PRESETS = Object.freeze({
  deep: preset('Voice', 'Natural lower pitch', s => pitch(1 - 0.32 * s), ['pitch']),
  helium: preset('Voice', 'Bright, high-pitched speech', s => pitch(1 + 0.78 * s), ['pitch']),
  demon: preset('Character', 'Low growl with gritty digital harmonics', s => `${pitch(1 - 0.43 * s)},acrusher=bits=${Math.round(16 - 7 * s)}:mix=${pct(0.2 + 0.54 * s)},bass=g=${pct(5 + 6 * s)}:f=100`, ['pitch', 'crush']),
  boss: preset('Gaming', 'Large, authoritative voice with spacious reflections', s => `${pitch(1 - 0.25 * s)},aecho=0.8:0.65:115|330:0.25|${pct(0.1 + 0.18 * s)},acompressor=threshold=-23dB:ratio=2.8:attack=10:release=170`, ['pitch', 'space']),
  robot: preset('Gaming', 'Machine-like amplitude modulation', s => `highpass=f=110,tremolo=f=${pct(22 + 22 * s)}:d=${pct(0.35 + 0.6 * s)},acompressor=threshold=-24dB:ratio=2.5:attack=4:release=75`, ['machine']),
  alien: preset('Character', 'Unnatural frequency-shifted speech', s => `afreqshift=shift=${Math.round(35 + 170 * s)},aphaser=in_gain=0.55:out_gain=0.75:delay=2.5:decay=0.3:speed=0.55:type=t`, ['frequency-shift']),
  ghost: preset('Character', 'Thin spectral voice with a long, distant tail', s => `highpass=f=${Math.round(650 + 400 * s)},lowpass=f=6300,aecho=0.75:0.7:470|820:0.25|${pct(0.13 + 0.18 * s)}`, ['haunting', 'space']),
  '8bit': preset('Gaming', 'Crunchy retro game audio, downsampled and quantized', s => `aresample=${Math.round(16000 - 10000 * s)},acrusher=bits=${Math.round(11 - 6 * s)}:samples=${Math.round(2 + 11 * s)}:aa=0:mix=${pct(0.48 + 0.5 * s)},aresample=48000`, ['crush']),
  glitch: preset('Gaming', 'Broken, stuttering digital signal', s => `volume=volume='if(lt(mod(t,${pct(0.43 - 0.24 * s)}),${pct(0.04 + 0.055 * s)}),0.03,1)':eval=frame,acrusher=bits=${Math.round(13 - 3 * s)}:mix=${pct(0.16 + 0.31 * s)}`, ['chop']),
  radio: preset('Communication', 'Narrow two-way radio transmission', s => `highpass=f=${Math.round(270 + 230 * s)},lowpass=f=${Math.round(4100 - 1700 * s)},acompressor=threshold=-27dB:ratio=4:attack=5:release=100`, ['comms']),
  megaphone: preset('Communication', 'Aggressively compressed, harsh loudspeaker', s => `highpass=f=280,lowpass=f=5600,asoftclip=type=tanh:threshold=${pct(0.60 - 0.40 * s)},acrusher=bits=12:mix=${pct(0.22 + 0.46 * s)},aecho=0.75:0.55:37:0.22,acompressor=threshold=-35dB:ratio=8:attack=2:release=60:makeup=2`, ['comms']),
  underwater: preset('Environment', 'Heavily muffled, gently wavering underwater sound', s => `lowpass=f=${Math.round(1800 - 1350 * s)},tremolo=f=0.9:d=${pct(0.22 + 0.3 * s)}`, ['muffled']),
  echo: preset('Environment', 'Clear, separated repeating echoes', s => `aecho=0.82:0.68:280|650:${pct(0.15 + 0.3 * s)}|${pct(0.08 + 0.21 * s)}`, ['space']),
  cathedral: preset('Environment', 'Broad, dense multi-reflection ambience', s => `aecho=0.76:0.84:96|174|305|610:0.12|0.15|${pct(0.12 + 0.1 * s)}|${pct(0.12 + 0.15 * s)},lowpass=f=7500`, ['space']),
  chorus: preset('Studio', 'Several subtly detuned overlapping voices', s => `chorus=0.65:0.75:42|65:0.21|0.26:${pct(0.2 + 0.14 * s)}|${pct(0.31 + 0.1 * s)}:1.2|1.7`, ['chorus']),
  flanger: preset('Studio', 'Sweeping metallic comb-filter motion', s => `flanger=delay=4:depth=${pct(1.1 + 7 * s)}:regen=${pct(0.1 + 0.45 * s)}:width=85:speed=0.35`, ['flange']),
  phaser: preset('Studio', 'Swirling phase-shifted speech', s => `aphaser=in_gain=0.45:out_gain=0.8:delay=2.1:decay=${pct(0.22 + 0.5 * s)}:speed=${pct(0.25 + 1.3 * s)}:type=t`, ['phase']),
  vibrato: preset('Studio', 'Pitch wobbles rhythmically', s => `vibrato=f=${pct(4 + 4 * s)}:d=${pct(0.25 + 0.7 * s)}`, ['pitch-mod']),
  tremolo: preset('Studio', 'Volume pulses rhythmically', s => `tremolo=f=${pct(3 + 6 * s)}:d=${pct(0.22 + 0.68 * s)}`, ['amplitude-mod']),
  distortion: preset('Studio', 'Crunchy saturated signal without pitch change', s => `asoftclip=type=tanh:threshold=${pct(0.58 - 0.43 * s)},acrusher=bits=${Math.round(10 - 6 * s)}:mix=${pct(0.48 + 0.48 * s)},acompressor=threshold=-25dB:ratio=2.5:attack=3:release=70`, ['crush']),
  bass: preset('Studio', 'Bass-heavy voice without lowering its pitch', s => `bass=g=${pct(4 + 15 * s)}:f=110:w=0.6,equalizer=f=230:t=q:w=1.2:g=${pct(2 + 4 * s)}`, ['eq']),
  slow: preset('Timing', 'Slow down speech while preserving pitch', s => `atempo=${pct(1 - 0.38 * s)}`, ['tempo']),
  fast: preset('Timing', 'Speed up speech while preserving pitch', s => `atempo=${pct(1 + 0.9 * s)}`, ['tempo']),
})

export const SFX_DEFAULT_INTENSITY = 65
export const SFX_MAX_INPUT_BYTES = 20 * 1024 * 1024
export const SFX_MAX_OUTPUT_BYTES = 19 * 1024 * 1024

const normalized = key => String(key || '').trim().toLowerCase()
export function parseSfxRequest(args = []) {
  if (!args.length || ['list', 'help', '?'].includes(normalized(args[0]))) return { type: 'help' }
  if (args.length > 2) throw new Error('Use .sfx <effect>[+effect] [intensity 0-100].')
  const names = normalized(args[0]).split('+')
  if (!names.length || names.length > 2 || names.some(name => !SFX_PRESETS[name])) {
    throw new Error('Unknown SFX effect. Send .sfx to see the available effects.')
  }
  if (new Set(names).size !== names.length) throw new Error('Choose two different effects, not the same one twice.')
  const raw = args[1]
  const intensity = raw === undefined ? SFX_DEFAULT_INTENSITY : Number(raw)
  if (!Number.isInteger(intensity) || intensity < 0 || intensity > 100 || (raw !== undefined && !/^\d{1,3}$/.test(String(raw)))) {
    throw new Error('Intensity must be a whole number from 0 to 100. Example: .sfx deep 70')
  }
  if (names.length > 1) {
    const tags = new Set(SFX_PRESETS[names[0]].tags)
    if (SFX_PRESETS[names[1]].tags.some(tag => tags.has(tag))) {
      throw new Error('These effects overlap too much. Choose effects with different audio characteristics.')
    }
  }
  return { type: 'process', names, intensity }
}

export function sfxHelp(prefix = '.') {
  const byFamily = new Map()
  for (const [name, preset] of Object.entries(SFX_PRESETS)) {
    if (!byFamily.has(preset.family)) byFamily.set(preset.family, [])
    byFamily.get(preset.family).push(name)
  }
  const lines = [
    '🎛️ *NIGHT SFX — Voice & Audio Transformations*',
    `Reply to an audio or voice note with *${prefix}sfx deep*.`,
    `Intensity: *${prefix}sfx deep 80* (0–100, default ${SFX_DEFAULT_INTENSITY}).`,
    `Combine two different types: *${prefix}sfx deep+echo 70*.`,
    '',
  ]
  for (const [family, names] of byFamily) lines.push(`*${family}:* ${names.join(', ')}`)
  lines.push('', 'Only the first 60 seconds are processed. For backwards audio use .reverse.')
  return lines.join('\n')
}

export function sfxFilter(names, intensity = SFX_DEFAULT_INTENSITY) {
  if (!Array.isArray(names) || !names.length || names.length > 2 || !Number.isInteger(intensity) || intensity < 0 || intensity > 100) {
    throw new Error('Invalid SFX processing request.')
  }
  const request = parseSfxRequest([names.join('+'), String(intensity)])
  const strength = intensity / 100
  const filters = ['aresample=48000', 'aformat=channel_layouts=mono']
  if (strength) for (const name of request.names) filters.push(SFX_PRESETS[name].build(strength))
  // Stable single-pass normalization and true-peak protection, applied once after all effects.
  filters.push('loudnorm=I=-18:TP=-2:LRA=9', 'alimiter=limit=0.95')
  return filters.join(',')
}

/** Render only a bounded clip. runner is injectable to test FFmpeg without dependencies. */
export async function renderSfx(bytes, { names, intensity = SFX_DEFAULT_INTENSITY, runner } = {}) {
  if (!Buffer.isBuffer(bytes) || !bytes.length || bytes.length > SFX_MAX_INPUT_BYTES) {
    throw new Error('Reply to audio smaller than 20 MB.')
  }
  const filter = sfxFilter(names, intensity)
  const run = runner || (await import('./media-conversion.js')).runFfmpeg
  const dir = await mkdtemp(join(tmpdir(), 'night-sfx-'))
  try {
    const input = join(dir, 'voice.bin')
    const output = join(dir, 'voice.ogg')
    await writeFile(input, bytes)
    await run(['-t', '60', '-i', input, '-map', '0:a:0', '-vn', '-af', filter,
      '-ar', '48000', '-ac', '1', '-c:a', 'libopus', '-b:a', '64k', output], { timeoutMs: 90000 })
    const info = await stat(output)
    if (!info.size || info.size > SFX_MAX_OUTPUT_BYTES) throw new Error('SFX output is too large.')
    return readFile(output)
  } finally {
    await rm(dir, { recursive: true, force: true }).catch(() => {})
  }
}
