import { readdir } from 'node:fs/promises'
import { basename, dirname, extname, join, relative, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { brandedTitle } from './utils/media/branding.js'
export { brandedTitle } from './utils/media/branding.js'

const ID_RE = /^[a-z0-9][a-z0-9._-]{0,63}$/
const SOURCE_POLICY = Object.freeze({
  anime: 'user-choice',
  manga: 'user-choice',
  music: 'managed',
})

const normalizeId = value => {
  const id = String(value || '').trim().toLowerCase()
  if (!ID_RE.test(id)) throw new Error('Source ID must use lowercase letters, numbers, ., _ or -')
  return id
}

async function sourceFiles(rootPath, directoryPath = rootPath) {
  let entries
  try {
    entries = await readdir(directoryPath, { withFileTypes:true })
  } catch (error) {
    if (error?.code === 'ENOENT') return []
    throw error
  }

  const files = []
  for (const entry of entries.sort((a,b) => a.name.localeCompare(b.name))) {
    if (entry.name.startsWith('_')) continue
    const path = join(directoryPath, entry.name)
    if (entry.isDirectory()) files.push(...await sourceFiles(rootPath, path))
    else if (entry.isFile() && entry.name.endsWith('.js')) files.push(path)
  }
  return files
}

export class SourceRegistry {
  constructor({ rootUrl, storage }) {
    this.rootUrl = rootUrl instanceof URL ? rootUrl : new URL(rootUrl, import.meta.url)
    this.storage = storage
    this.sources = new Map()
  }

  async load({ cacheBust = '' } = {}) {
    const rootPath = fileURLToPath(this.rootUrl)
    const files = await sourceFiles(rootPath)
    const next = new Map()

    for (const file of files) {
      const rel = relative(rootPath, file)
      const parts = rel.split(sep).filter(Boolean)
      if (parts.length < 2) throw new Error(`Source must live inside a capability folder: ${rel}`)
      const capability = normalizeId(parts[0])

      const url = pathToFileURL(file)
      if (cacheBust) url.searchParams.set('v', String(cacheBust))
      const module = await import(url.href)
      const source = module.default
      if (!source || typeof source.run !== 'function') throw new Error(`Invalid source module: ${rel}`)

      const id = normalizeId(source.id || basename(file, extname(file)))
      const key = `${capability}:${id}`
      if (next.has(key)) throw new Error(`Duplicate source ${id} for capability ${capability}`)
      next.set(key, {
        ...source,
        id,
        capability,
        name: String(source.name || id).trim() || id,
        description: String(source.description || '').trim(),
        primary: source.primary === true,
        fallbackOrder: Number.isFinite(Number(source.fallbackOrder)) ? Number(source.fallbackOrder) : 1000,
        brandAliases: Array.isArray(source.brandAliases) ? source.brandAliases : [],
        modulePath: rel.split(sep).join('/'),
      })
    }

    this.sources = next
    return this.listAll()
  }

  listAll() {
    return [...this.sources.values()].map(source => ({ ...source }))
  }

  list(capability) {
    const cap = normalizeId(capability)
    return [...this.sources.values()]
      .filter(source => source.capability === cap)
      .sort((a,b) => a.name.localeCompare(b.name))
  }

  get(capability, sourceId) {
    const cap = normalizeId(capability)
    const id = normalizeId(sourceId)
    return this.sources.get(`${cap}:${id}`) || null
  }

  getDefault(userKey, capability) {
    if (!userKey) return ''
    return this.storage?.getSourceDefault(userKey, normalizeId(capability)) || ''
  }

  setDefault(userKey, capability, sourceId) {
    if (!userKey) throw new Error('A stable user identity is required to save a source default')
    const cap = normalizeId(capability)
    const source = this.get(cap, sourceId)
    if (!source) throw new Error(`Unknown ${cap} source: ${sourceId}`)
    this.storage.setSourceDefault(userKey, cap, source.id)
    return source
  }

  clearDefault(userKey, capability) {
    if (!userKey) throw new Error('A stable user identity is required to clear a source default')
    return this.storage.clearSourceDefault(userKey, normalizeId(capability))
  }

  mode(capability) {
    const cap = normalizeId(capability)
    return SOURCE_POLICY[cap] || 'user-choice'
  }

  async execute({ capability, userKey, explicitSource = '', pinnedSource = '', payload = {}, context = {} }) {
    const cap = normalizeId(capability)
    const mode = this.mode(cap)
    const botName = this.storage?.brandForCapability(cap) || 'HEX'
    const available = this.list(cap)
    const runSource = source => source.run({
      ...payload,
      context: {
        ...context,
        botName,
        brandTitle: value => brandedTitle(value, {
          botName,
          sourceName: source.name,
          aliases: [source.id, ...source.brandAliases],
        }),
      },
      source,
    })
    if (!available.length) return { status:'no-sources', capability:cap, sources:[] }

    if (pinnedSource) {
      const source = this.get(cap, pinnedSource)
      if (!source) return { status:'unknown-source', capability:cap, sourceId:String(pinnedSource), sources:available }
      try {
        const result = await runSource(source)
        return { status:'ok', capability:cap, source, result, fallback:false, fallbackFrom:null, pinned:true, managed:mode === 'managed' }
      } catch (error) {
        return { status:'source-error', capability:cap, source, error, pinned:true, managed:mode === 'managed' }
      }
    }

    if (explicitSource && mode === 'managed') {
      return { status:'source-choice-disabled', capability:cap, sources:available }
    }

    if (explicitSource) {
      const source = this.get(cap, explicitSource)
      if (!source) return { status:'unknown-source', capability:cap, sourceId:String(explicitSource), sources:available }
      try {
        const result = await runSource(source)
        return { status:'ok', capability:cap, source, result, fallback:false, fallbackFrom:null }
      } catch (error) {
        return { status:'source-error', capability:cap, source, error }
      }
    }

    if (available.length === 1) {
      const source = available[0]
      try {
        const result = await runSource(source)
        return { status:'ok', capability:cap, source, result, fallback:false, fallbackFrom:null, managed:mode === 'managed' }
      } catch (error) {
        return { status:'source-error', capability:cap, source, error }
      }
    }

    if (mode === 'managed') {
      const ordered = [...available].sort((a,b) => {
        if (a.primary !== b.primary) return a.primary ? -1 : 1
        if (a.fallbackOrder !== b.fallbackOrder) return a.fallbackOrder - b.fallbackOrder
        return a.name.localeCompare(b.name)
      })
      let firstFailure = null
      for (const source of ordered) {
        try {
          const result = await runSource(source)
          return {
            status:'ok', capability:cap, source, result,
            fallback:Boolean(firstFailure),
            fallbackFrom:firstFailure?.source || null,
            managed:true,
          }
        } catch (error) {
          if (!firstFailure) firstFailure = { source, error }
        }
      }
      return { status:'all-failed', capability:cap, sources:available, error:firstFailure?.error || new Error('All sources failed'), managed:true }
    }

    const defaultId = this.getDefault(userKey, cap)
    if (!defaultId) {
      return { status:'choice-required', capability:cap, sources:available }
    }

    const preferred = this.get(cap, defaultId)
    const ordered = [
      ...(preferred ? [preferred] : []),
      ...available.filter(source => source.id !== preferred?.id),
    ]

    let firstFailure = null
    for (const source of ordered) {
      try {
        const result = await runSource(source)
        return {
          status:'ok',
          capability:cap,
          source,
          result,
          fallback:Boolean(firstFailure),
          fallbackFrom:firstFailure?.source || null,
        }
      } catch (error) {
        if (!firstFailure) firstFailure = { source, error }
      }
    }

    return {
      status:'all-failed',
      capability:cap,
      sources:available,
      error:firstFailure?.error || new Error('All sources failed'),
    }
  }
}
