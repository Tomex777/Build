import { readdir } from 'node:fs/promises'

const emptyRegistry = () => ({ commands: new Map(), canonical: [] })

export async function loadCommands(directoryUrl, { cacheBust = '', allowMissing = false } = {}) {
  const directory = directoryUrl instanceof URL ? directoryUrl : new URL(directoryUrl, import.meta.url)
  let entries
  try {
    entries = await readdir(directory, { withFileTypes: true })
  } catch (error) {
    if (allowMissing && error?.code === 'ENOENT') return emptyRegistry()
    throw error
  }

  const names = entries
    .filter(entry => entry.isFile() && entry.name.endsWith('.js') && !entry.name.startsWith('_'))
    .map(entry => entry.name)
    .sort()

  const commands = new Map()
  for (const name of names) {
    const url = new URL(name, directory)
    if (cacheBust) url.searchParams.set('v', String(cacheBust))
    const module = await import(url.href)
    const command = module.default
    if (!command?.name || typeof command.run !== 'function') throw new Error(\`Invalid command module: \${name}\`)
    const keys = [command.name, ...(command.aliases || [])]
      .map(value => String(value).trim().toLowerCase())
      .filter(Boolean)
    for (const key of keys) {
      if (commands.has(key)) throw new Error(\`Duplicate MSCC command in namespace: \${key}\`)
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
    commandList: () => registry.canonical,
  })
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
    if (context.publicCommandsEnabled === false) return false
    if (command.ownerOnly === true && !context.controller) return false
  } else {
    throw new Error(\`Unknown command scope: \${scope}\`)
  }
  return runResolvedCommand(registry, parsed, context)
}

export async function dispatchNamespacedCommand({ privateRegistry, publicRegistry, rawText, context }) {
  const privateParsed = parseCommand(rawText, '.')
  if (privateParsed && context.privateControl && privateRegistry?.commands?.has(privateParsed.name)) {
    return runResolvedCommand(privateRegistry, privateParsed, context)
  }

  if (context.publicCommandsEnabled === false) return false
  const publicParsed = parseCommand(rawText, String(context.publicPrefix || '.'))
  if (!publicParsed || !publicRegistry?.commands?.has(publicParsed.name)) return false
  const command = publicRegistry.commands.get(publicParsed.name)
  if (command.ownerOnly === true && !context.controller) return false
  return runResolvedCommand(publicRegistry, publicParsed, context)
}
