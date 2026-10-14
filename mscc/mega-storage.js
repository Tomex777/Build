import { Readable } from 'node:stream'

export const MEGA_UPLOAD_LIMIT_BYTES = 512 * 1024 * 1024

export function megaConfig(env = process.env) {
  const email = String(env.MSCC_MEGA_EMAIL || '').trim()
  const password = String(env.MSCC_MEGA_PASSWORD || '')
  const folder = safeMegaName(env.MSCC_MEGA_FOLDER || 'MSCC Uploads', 'MSCC Uploads')
  return { email, password, folder, configured:Boolean(email && password) }
}

export function safeMegaName(value, fallback = 'media.bin') {
  const text = String(value || '')
    .replace(/[\\/\x00-\x1f\x7f]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^\.+$/, '')
    .slice(0, 180)
  return text && text !== '.' && text !== '..' ? text : fallback
}

export function validateMegaSize(size) {
  const bytes = Number(size)
  if (!Number.isSafeInteger(bytes) || bytes <= 0) {
    throw new Error('The media must have a known, nonzero size to upload to MEGA.')
  }
  if (bytes > MEGA_UPLOAD_LIMIT_BYTES) {
    throw new Error('That file exceeds the 512 MiB per-upload safety limit.')
  }
  return bytes
}

export async function withMegaSession(work, { env = process.env, StorageClass } = {}) {
  const config = megaConfig(env)
  if (!config.configured) {
    throw new Error('MEGA is not configured. Set MSCC_MEGA_EMAIL and MSCC_MEGA_PASSWORD in the secure server environment.')
  }
  const Client = StorageClass || (await import('megajs')).Storage
  const client = new Client({ email:config.email, password:config.password, keepalive:false })
  try {
    await client.ready
    return await work(client, config)
  } finally {
    await client.close?.()
  }
}

async function uploadFolder(client, folderName) {
  const existing = client.root.find(folderName)
  if (existing) {
    if (!existing.directory) throw new Error('The configured MEGA folder name is already used by a file.')
    return existing
  }
  return client.root.mkdir(folderName)
}

export async function uploadMegaMedia({ name, size, source }, options = {}) {
  const fileSize = validateMegaSize(size)
  const fileName = safeMegaName(name)
  if (typeof source !== 'function') throw new TypeError('source must be a stream factory.')
  return withMegaSession(async (client, config) => {
    const folder = await uploadFolder(client, config.folder)
    const data = await source()
    if (!(data instanceof Readable) && !(data && typeof data.pipe === 'function')) {
      throw new TypeError('The WhatsApp media download did not return a readable stream.')
    }
    const transfer = folder.upload({ name:fileName, size:fileSize }, data)
    const file = await transfer.complete
    return { name:file.name || fileName, size:fileSize, nodeId:file.nodeId || '' }
  }, options)
}

export async function megaStorageStatus(options = {}) {
  return withMegaSession(async (client, config) => {
    const quota = await client.getAccountInfo()
    return {
      folder:config.folder,
      used:Number(quota.spaceUsed || 0),
      total:Number(quota.spaceTotal || 0),
      type:String(quota.type || ''),
    }
  }, options)
}

export async function megaRecentFiles({ limit = 10 } = {}, options = {}) {
  const capped = Math.min(20, Math.max(1, Math.floor(Number(limit) || 10)))
  return withMegaSession(async (client, config) => {
    const folder = client.root.find(config.folder)
    if (!folder || !folder.directory) return []
    return (folder.children || [])
      .filter(item => !item.directory)
      .sort((a, b) => Number(b.timestamp || 0) - Number(a.timestamp || 0))
      .slice(0, capped)
      .map(item => ({ name:String(item.name || 'Untitled'), size:Number(item.size || 0) }))
  }, options)
}
