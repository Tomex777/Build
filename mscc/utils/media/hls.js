// Generic HLS resolver/downloader adapted from the MIT-licensed pahebatcher design.
// Source reference: https://github.com/smolfiddle/pahebatcher
// Keep provider-specific URL discovery outside this module.

import { createDecipheriv } from 'node:crypto'
import { mkdir, readFile, rename, rm, stat, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { spawn } from 'node:child_process'

function absolute(value, base) {
  return new URL(String(value || ''), base).href
}

function parseAttributeList(value) {
  const out = {}
  const re = /([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))(?:,|$)/gi
  for (const match of String(value || '').matchAll(re)) {
    out[match[1].toUpperCase()] = match[2] ?? match[3] ?? ''
  }
  return out
}

function parseIv(value) {
  if (!value) return null
  const hex = String(value).replace(/^0x/i, '')
  if (!/^[0-9a-f]+$/i.test(hex)) return null
  const padded = hex.padStart(32, '0').slice(-32)
  return Buffer.from(padded, 'hex')
}

export function parseM3u8(content, baseUrl) {
  const lines = String(content || '').split(/\r?\n/)
  const variants = []
  const segments = []
  let key = null
  let mediaSequence = 0
  let nextSequence = 0

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue

    if (line.startsWith('#EXT-X-MEDIA-SEQUENCE:')) {
      const value = Number(line.slice('#EXT-X-MEDIA-SEQUENCE:'.length).trim())
      if (Number.isFinite(value)) {
        mediaSequence = value
        nextSequence = value
      }
      continue
    }

    if (line.startsWith('#EXT-X-STREAM-INF:')) {
      const attrs = parseAttributeList(line.slice('#EXT-X-STREAM-INF:'.length))
      const next = lines.slice(i + 1).find(value => value.trim() && !value.trim().startsWith('#'))?.trim()
      if (next) {
        const resolution = String(attrs.RESOLUTION || '')
        const height = Number(resolution.split('x')[1] || 0)
        variants.push({
          url:absolute(next, baseUrl),
          bandwidth:Number(attrs.BANDWIDTH || 0),
          averageBandwidth:Number(attrs['AVERAGE-BANDWIDTH'] || 0),
          resolution,
          height:Number.isFinite(height) ? height : 0,
          codecs:String(attrs.CODECS || ''),
        })
        while (i + 1 < lines.length) {
          i += 1
          const candidate = lines[i].trim()
          if (candidate && !candidate.startsWith('#')) break
        }
      }
      continue
    }

    if (line.startsWith('#EXT-X-KEY:')) {
      const attrs = parseAttributeList(line.slice('#EXT-X-KEY:'.length))
      if (String(attrs.METHOD || '').toUpperCase() === 'AES-128' && attrs.URI) {
        key = {
          method:'AES-128',
          url:absolute(attrs.URI, baseUrl),
          iv:parseIv(attrs.IV),
        }
      } else {
        key = null
      }
      continue
    }

    if (!line.startsWith('#')) {
      segments.push({
        index:segments.length,
        sequence:nextSequence,
        url:absolute(line, baseUrl),
        key:key ? { ...key } : null,
      })
      nextSequence += 1
    }
  }

  return { variants, segments, mediaSequence }
}

export async function resolveM3u8({
  url,
  fetchText,
  headers = {},
  chooseVariant,
  maxDepth = 4,
}) {
  if (typeof fetchText !== 'function') throw new Error('resolveM3u8 requires fetchText')
  let current = String(url || '')

  for (let depth = 0; depth <= maxDepth; depth++) {
    const content = await fetchText(current, headers)
    const parsed = parseM3u8(content, current)
    if (parsed.segments.length) {
      return { playlistUrl:current, ...parsed }
    }
    if (!parsed.variants.length) throw new Error('HLS manifest contains no playable segments or variants')

    let selected
    if (typeof chooseVariant === 'function') selected = chooseVariant(parsed.variants)
    if (!selected) selected = parsed.variants.at(-1)
    if (!selected?.url) throw new Error('HLS variant selection failed')
    current = selected.url
  }

  throw new Error('HLS master playlist nesting exceeded limit')
}

function segmentPath(root, index) {
  return join(root, String(index).padStart(6, '0') + '.ts')
}

async function exists(path) {
  try {
    const info = await stat(path)
    return info.isFile() && info.size > 0
  } catch {
    return false
  }
}

async function atomicWrite(path, bytes) {
  const tmp = path + '.tmp'
  await writeFile(tmp, bytes)
  await rename(tmp, path)
}

function segmentIv(segment) {
  if (segment?.key?.iv) return Buffer.from(segment.key.iv)
  const iv = Buffer.alloc(16)
  iv.writeBigUInt64BE(BigInt(segment?.sequence || 0), 8)
  return iv
}

function decryptAes128(bytes, keyBytes, iv) {
  const decipher = createDecipheriv('aes-128-cbc', keyBytes, iv)
  return Buffer.concat([decipher.update(bytes), decipher.final()])
}

export async function downloadHlsSegments({
  segments,
  fetchBytes,
  directory,
  headers = {},
  concurrency = 8,
  onProgress,
}) {
  if (!Array.isArray(segments) || !segments.length) throw new Error('No HLS segments supplied')
  if (typeof fetchBytes !== 'function') throw new Error('downloadHlsSegments requires fetchBytes')

  await mkdir(directory, { recursive:true })
  const keys = new Map()
  let cursor = 0
  let completed = 0
  let bytesWritten = 0
  let resumed = 0

  for (const segment of segments) {
    if (await exists(segmentPath(directory, segment.index))) {
      completed += 1
      resumed += 1
    }
  }

  async function getKey(url) {
    if (!keys.has(url)) keys.set(url, Promise.resolve(fetchBytes(url, headers)).then(Buffer.from))
    const key = await keys.get(url)
    if (key.length !== 16) throw new Error('HLS AES-128 key is not 16 bytes')
    return key
  }

  async function worker() {
    while (true) {
      let segment
      while (cursor < segments.length) {
        const candidate = segments[cursor++]
        if (!(await exists(segmentPath(directory, candidate.index)))) {
          segment = candidate
          break
        }
      }
      if (!segment) return

      let bytes = Buffer.from(await fetchBytes(segment.url, headers))
      if (segment.key?.method === 'AES-128') {
        const keyBytes = await getKey(segment.key.url)
        bytes = decryptAes128(bytes, keyBytes, segmentIv(segment))
      }

      await atomicWrite(segmentPath(directory, segment.index), bytes)
      completed += 1
      bytesWritten += bytes.length
      onProgress?.({
        completed,
        total:segments.length,
        bytesWritten,
        resumed,
        index:segment.index,
      })
    }
  }

  const count = Math.max(1, Math.min(Number(concurrency) || 1, 32))
  await Promise.all(Array.from({ length:count }, () => worker()))

  return {
    directory,
    total:segments.length,
    completed,
    resumed,
    bytesWritten,
    paths:segments.map(segment => segmentPath(directory, segment.index)),
  }
}

async function runFfmpeg(args, { inputBuffers = [] } = {}) {
  return new Promise((resolve, reject) => {
    const proc = spawn('ffmpeg', args, { stdio:['pipe','ignore','pipe'] })
    let stderr = ''
    proc.stderr.on('data', chunk => { stderr += chunk.toString(); if (stderr.length > 12000) stderr = stderr.slice(-12000) })
    proc.on('error', reject)
    proc.on('close', code => resolve({ ok:code === 0, code, stderr }))

    ;(async () => {
      try {
        for (const bytes of inputBuffers) {
          if (!proc.stdin.write(bytes)) {
            await new Promise(resolveDrain => proc.stdin.once('drain', resolveDrain))
          }
        }
      } catch {
      } finally {
        proc.stdin.end()
      }
    })()
  })
}

export async function remuxHlsSegments({
  directory,
  count,
  output,
}) {
  const paths = Array.from({ length:Number(count) || 0 }, (_, index) => segmentPath(directory, index))
  if (!paths.length) throw new Error('No segments to remux')
  for (const path of paths) {
    if (!(await exists(path))) throw new Error('Missing HLS segment: ' + path)
  }

  const buffers = []
  for (const path of paths) buffers.push(await readFile(path))
  const piped = await runFfmpeg(
    ['-y','-i','pipe:0','-c','copy','-movflags','+faststart',output],
    { inputBuffers:buffers },
  )
  if (piped.ok) return { ok:true, method:'pipe', output }

  const listFile = join(directory, 'concat.txt')
  await writeFile(listFile, paths.map(path => "file '" + path.replaceAll("'", "'\\''") + "'").join('\n') + '\n')
  try {
    const fallback = await runFfmpeg([
      '-y','-f','concat','-safe','0','-i',listFile,
      '-c','copy','-movflags','+faststart',output,
    ])
    if (!fallback.ok) {
      const error = new Error('ffmpeg could not remux HLS segments')
      error.stderr = fallback.stderr || piped.stderr
      throw error
    }
    return { ok:true, method:'concat', output }
  } finally {
    await rm(listFile, { force:true }).catch(() => {})
  }
}
