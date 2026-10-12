import { randomUUID } from 'node:crypto'
import { mkdir, open, readFile, realpath, rename, stat, unlink } from 'node:fs/promises'
import { join, resolve, sep } from 'node:path'
import { BufferJSON, initAuthCreds, proto } from '@itsliaaa/baileys'

/**
 * Drop-in, on-disk compatible auth store for Baileys' multi-file format.
 *
 * Baileys' upstream multi-file implementation writes JSON in-place and
 * treats all read errors as a missing key. An interrupted write or damaged
 * creds.json must NOT be interpreted as an unpaired device.
 *
 * This implementation writes a private temporary file, fsyncs its bytes,
 * then atomically replaces the destination. Per-file queues serialize
 * competing writes and deletes within this process.
 */
const queues = new Map()
const safeName = name => String(name).replace(/\//g, '__').replace(/:/g, '-')

async function withFileQueue(path, operation) {
  const previous = queues.get(path) || Promise.resolve()
  const task = previous.catch(() => {}).then(operation)
  const done = task.catch(() => {})
  queues.set(path, done)
  try {
    return await task
  } finally {
    if (queues.get(path) === done) queues.delete(path)
  }
}

async function atomicWrite(path, value) {
  const tmp = `${path}.${process.pid}.${randomUUID()}.tmp`
  let handle
  try {
    handle = await open(tmp, 'wx', 0o600)
    await handle.writeFile(JSON.stringify(value, BufferJSON.replacer))
    await handle.sync()
    await handle.close()
    handle = null
    await rename(tmp, path)
  } finally {
    if (handle) await handle.close().catch(() => {})
    await unlink(tmp).catch(error => {
      if (error.code !== 'ENOENT') throw error
    })
  }
}

export async function useDurableMultiFileAuthState(folder) {
  const dir = resolve(folder)
  const info = await stat(dir).catch(error => {
    if (error.code === 'ENOENT') return null
    throw error
  })
  if (info && !info.isDirectory()) throw new Error('Authentication path is not a directory')
  if (!info) await mkdir(dir, { recursive: true, mode: 0o700 })

  const pathFor = name => join(dir, safeName(name))
  async function readData(name) {
    let raw
    try {
      raw = await readFile(pathFor(name), 'utf8')
    } catch (error) {
      if (error.code === 'ENOENT') return null
      throw error
    }
    try {
      return JSON.parse(raw, BufferJSON.reviver)
    } catch {
      // Don't log or expose sensitive auth content.
      throw new Error(`WhatsApp authentication file ${safeName(name)} is invalid; preserving it for investigation`)
    }
  }
  const writeData = (name, data) => withFileQueue(pathFor(name), () => atomicWrite(pathFor(name), data))
  const removeData = name => withFileQueue(pathFor(name), async () => {
    await unlink(pathFor(name)).catch(error => {
      if (error.code !== 'ENOENT') throw error
    })
  })

  const creds = (await readData('creds.json')) || initAuthCreds()
  return {
    state: {
      creds,
      keys: {
        get: async (type, ids) => {
          const found = {}
          await Promise.all(ids.map(async id => {
            let value = await readData(`${type}-${id}.json`)
            if (type === 'app-state-sync-key' && value) {
              value = proto.Message.AppStateSyncKeyData.fromObject(value)
            }
            found[id] = value
          }))
          return found
        },
        set: async data => {
          const tasks = []
          for (const type in data) {
            for (const id in data[type]) {
              const value = data[type][id]
              const name = `${type}-${id}.json`
              tasks.push(value ? writeData(name, value) : removeData(name))
            }
          }
          await Promise.all(tasks)
        },
      },
    },
    saveCreds: () => writeData('creds.json', creds),
  }
}

/** Detect aliases and nested paths that could make two accounts share auth. */
export async function assertSeparateAuthDirectories(records) {
  const dirs = []
  for (const record of records) {
    const normalized = resolve(record.authDir)
    await mkdir(normalized, { recursive: true, mode: 0o700 })
    const canonical = await realpath(normalized)
    for (const previous of dirs) {
      if (canonical === previous.dir ||
          canonical.startsWith(previous.dir + sep) ||
          previous.dir.startsWith(canonical + sep)) {
        throw new Error(`Accounts ${previous.id} and ${record.id} share or nest authentication directories`)
      }
    }
    dirs.push({ id: record.id, dir: canonical })
  }
}
