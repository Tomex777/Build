import assert from 'node:assert/strict'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runFfmpeg, STICKER_CANVAS, STICKER_FIT_FILTER } from './utils/media-conversion.js'

const dir = await mkdtemp(join(tmpdir(), 'mscc-nexia-rounded-'))
const alphaAt = (buffer, x, y, frame = 0) =>
  buffer[((frame * STICKER_CANVAS * STICKER_CANVAS) + y * STICKER_CANVAS + x) * 4 + 3]

function assertRoundedCorners(raw, frames = 1) {
  const frameBytes = STICKER_CANVAS * STICKER_CANVAS * 4
  assert.equal(raw.length, frameBytes * frames, 'Sticker frame must remain exactly 512x512 RGBA')
  for (let f = 0; f < frames; f++) {
    assert.equal(alphaAt(raw, 0, 0, f), 0, 'top-left corner must be transparent')
    assert.equal(alphaAt(raw, 511, 0, f), 0, 'top-right corner must be transparent')
    assert.equal(alphaAt(raw, 0, 511, f), 0, 'bottom-left corner must be transparent')
    assert.equal(alphaAt(raw, 511, 511, f), 0, 'bottom-right corner must be transparent')
    assert.equal(alphaAt(raw, 256, 256, f), 255, 'image center must be opaque')
    assert.equal(alphaAt(raw, 20, 0, f), 255, '20px top corner radius must match Nexia')
    assert.equal(alphaAt(raw, 0, 20, f), 255, '20px left corner radius must match Nexia')
    assert.equal(alphaAt(raw, 6, 6, f), 255, 'corner curvature must match Nexia')
    assert.equal(alphaAt(raw, 0, 0, f), 0, 'do not add a transparent outer border')
  }
}

try {
  // The production static WebP encoder must retain the original Nexia mask.
  for (const [name, width, height] of [
    ['landscape', 640, 360],
    ['portrait', 360, 640],
  ]) {
    const input = join(dir, name + '.ppm')
    const webp = join(dir, name + '.webp')
    const decoded = join(dir, name + '.rgba')
    const header = Buffer.from('P6\\n' + width + ' ' + height + '\\n255\\n', 'ascii')
    const pixels = Buffer.alloc(width * height * 3)
    for (let p = 0; p < pixels.length; p += 3) {
      pixels[p] = 30; pixels[p + 1] = 160; pixels[p + 2] = 250
    }
    await writeFile(input, Buffer.concat([header, pixels]))
    await runFfmpeg([
      '-i', input, '-frames:v', '1', '-vf', STICKER_FIT_FILTER,
      '-c:v', 'libwebp', '-preset', 'picture',
      '-pix_fmt', 'yuva420p', '-quality', '82', '-compression_level', '6', webp,
    ])
    await runFfmpeg([
      '-i', webp, '-frames:v', '1', '-pix_fmt', 'rgba',
      '-f', 'rawvideo', decoded,
    ])
    assertRoundedCorners(await readFile(decoded))
  }

  // Animated conversion shares the same filter, with per-frame alpha.
  const animated = join(dir, 'frames.rgba')
  await runFfmpeg([
    '-f', 'lavfi', '-i', 'color=c=red:size=640x360:rate=2:duration=1',
    '-vf', STICKER_FIT_FILTER + ',fps=2,format=rgba',
    '-frames:v', '2', '-pix_fmt', 'rgba', '-f', 'rawvideo', animated,
  ])
  assertRoundedCorners(await readFile(animated), 2)
  console.log('PASS Nexia original 20px rounded mask on static WebP and animated frames')
} finally {
  await rm(dir, { recursive:true, force:true })
}
