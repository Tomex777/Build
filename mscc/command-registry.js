import { readdir } from 'node:fs/promises'
import { relative, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const emptyRegistry = () => ({ commands: new Map(), canonical: [] })

async function commandFiles(rootPath, directoryPath = rootPath) {
  const entries = await readdir(directoryPath, { withFileTypes: true })
  const files = []
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    if (entry.name.startsWith('_')) continue
    const path = directoryPath + sep + entry.name
    if (entry.isDirectory()) files.push(...await commandFiles(rootPath, path))
    else if (entry.isFile() && entry.name.endsWith('.js')) files.push(path)
  }
  return files
}

export async function loadCommands(directoryUrl, {
  cacheBust = '',
  allowMissing = false,
  capabilityFromDirectory = false,
} = {}) {
  const directory = directoryUrl instanceof URL ? directoryUrl : new URL(directoryUrl, import.meta.url)
  const rootPath = fileURLToPath(directory)

  let files
  try {
    files = await commandFiles(rootPath)
  } catch (error) {
    if (allowMissing && error?.code === 'ENOENT') return emptyRegistry()
    throw error
  }

  const commands = new Map()
  for (const file of files) {
    const relativePath = relative(rootPath, file)
    const parts = relativePath.split(sep).filter(Boolean)
    if (capabilityFromDirectory && parts.length < 2) {
      throw new Error(`Public command must live inside a capability folder: ${relativePath}`)
    }

    const url = pathToFileURL(file)
    if (cacheBust) url.searchParams.set('v', String(cacheBust))
    const module = await import(url.href)
    const sourceCommand = module.default
    if (!sourceCommand?.name || typeof sourceCommand.run !== 'function') {
      throw new Error(`Invalid command module: ${relativePath}`)
    }

    const command = capabilityFromDirectory
      ? {
          ...sourceCommand,
          capability: String(parts[0]).trim().toLowerCase(),
          modulePath: relativePath.split(sep).join('/'),
        }
      : sourceCommand

    const keys = [command.name, ...(command.aliases || [])]
      .map(value => String(value).trim().toLowerCase())
      .filter(Boolean)

    for (const key of keys) {
      if (commands.has(key)) throw new Error(`Duplicate MSCC command in namespace: ${key}`)
      commands.set(key, command)
    }
  }

  return { commands, canonical: [...new Set(commands.values())] }
}

function parseCommand(rawText, prefix = '.') {
  const text = String(rawText || '').trim()
  const normalizedPrefix = String(prefix || '.')
  if (!normalizedPrefix || !text.startsWith(normalizedPrefix)) return null

  const body = text.slice(normalizedPrefix.length).trim()
  if (!body) return null
  const [rawName, ...args] = body.split(/\s+/)
  return { name: rawName.toLowerCase(), args }
}

async function runResolvedCommand(registry, parsed, context) {
  const command = registry.commands.get(parsed.name)
  if (!command) return false

  await command.run({
    ...context,
    command,
    args: parsed.args,
    name: parsed.name,
    capability: String(command.capability || 'general').trim().toLowerCase() || 'general',
    commandList: () => registry.canonical,
  })
  return true
}

async function publicCommandAllowed(command, context) {
  if (context.publicCommandsEnabled === false) return false

  if (command.ownerOnly === true && !(context.isSessionOwner === true || context.isSupremeOwner === true)) {
    return false
  }

  if (command.adminOnly === true) {
    const admin = typeof context.isGroupAdmin === 'function'
      ? await context.isGroupAdmin()
      : context.isGroupAdmin === true
    if (!admin) return false
  }

  if (typeof context.shouldExecutePublicCommand === 'function') {
    if (!(await context.shouldExecutePublicCommand(command))) return false
  }

  return true
}

export async function dispatchCommand(registry, rawText, context, { scope = 'private', prefix = '.' } = {}) {
  const parsed = parseCommand(rawText, prefix)
  if (!parsed) return false
  const command = registry.commands.get(parsed.name)
  if (!command) return false

  if (scope === 'private') {
    if (!context.privateControl) return false
  } else if (scope === 'public') {
    if (!(await publicCommandAllowed(command, context))) return false
  } else {
    throw new Error(`Unknown command scope: ${scope}`)
  }

  return runResolvedCommand(registry, parsed, context)
}

export async function dispatchNamespacedCommand({ privateRegistry, publicRegistry, rawText, context }) {
  const privateParsed = parseCommand(rawText, '.')
  if (privateParsed && context.privateControl && privateRegistry?.commands?.has(privateParsed.name)) {
    return runResolvedCommand(privateRegistry, privateParsed, context)
  }

  const publicParsed = parseCommand(rawText, String(context.publicPrefix || '.'))
  if (!publicParsed || !publicRegistry?.commands?.has(publicParsed.name)) return false

  const command = publicRegistry.commands.get(publicParsed.name)
  if (!(await publicCommandAllowed(command, context))) return false
  return runResolvedCommand(publicRegistry, publicParsed, context)
}
