import { readdir } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

export async function loadCommands(directoryUrl) {
  const directory = directoryUrl instanceof URL ? directoryUrl : new URL(directoryUrl, import.meta.url)
  const names = (await readdir(directory, { withFileTypes: true }))
    .filter(entry => entry.isFile() && entry.name.endsWith('.js') && entry.name !== 'registry.js')
    .map(entry => entry.name)
    .sort()

  const commands = new Map()
  for (const name of names) {
    const url = new URL(name, directory)
    const module = await import(pathToFileURL(url.pathname).href)
    const command = module.default
    if (!command?.name || typeof command.run !== 'function') {
      throw new Error(`Invalid command module: ${name}`)
    }
    const keys = [command.name, ...(command.aliases || [])]
      .map(value => String(value).trim().toLowerCase())
      .filter(Boolean)
    for (const key of keys) {
      if (commands.has(key)) throw new Error(`Duplicate MSCC command: ${key}`)
      commands.set(key, command)
    }
  }

  return {
    commands,
    canonical: [...new Set(commands.values())],
  }
}

export async function dispatchCommand(registry, rawText, context) {
  const text = String(rawText || '').trim()
  if (!text.startsWith('.')) return false

  const body = text.slice(1).trim()
  if (!body) return false
  const [rawName, ...args] = body.split(/\s+/)
  const name = rawName.toLowerCase()
  const command = registry.commands.get(name)
  if (!command) return false
  if (command.ownerOnly !== false && !context.controller) return false

  await command.run({
    ...context,
    command,
    args,
    name,
    commandList: () => registry.canonical,
  })
  return true
}
