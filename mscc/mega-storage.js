import { Readable } from 'node:stream'

export const MEGA_UPLOAD_LIMIT_BYTES = 512 * 1024 * 1024
const ID_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/
const NODE_PATTERN = /^[A-Za-z0-9_-]{6,48}$/

export function safeMegaName(value, fallback = 'media.bin') {
  const text = String(value || '')
    .replace(/[\\/\x00-\x1f\x7f]/g, '_')
    .replace(/\s+/g, ' ').trim()
    .replace(/^\.+$/, '').slice(0, 180)
  return text && text !== '.' && text !== '..' ? text : fallback
}

export function megaConfig(env = process.env) {
  const email = String(env.MSCC_MEGA_EMAIL || '').trim()
  const password = String(env.MSCC_MEGA_PASSWORD || '')
  const folder = safeMegaName(env.MSCC_MEGA_FOLDER || 'MSCC Uploads', 'MSCC Uploads')
  return { email, password, folder, configured:Boolean(email && password) }
}

/**
 * Explicit, opt-in share mounts. The contributor retains their credentials.
 * A public folder URL is NOT a writable mount and is never accepted here.
 * Do not infer remote quota from the presence of an incoming share.
 */
export function megaSharedFolders(env = process.env) {
  const raw = String(env.MSCC_MEGA_SHARED_FOLDERS || '[]')
  if (raw.length > 8192) throw new Error('MEGA share configuration is too large.')
  let input
  try { input = JSON.parse(raw) }
  catch { throw new Error('MSCC_MEGA_SHARED_FOLDERS must contain valid JSON.') }
  if (!Array.isArray(input) || input.length > 20) {
    throw new Error('MEGA share configuration must be a list of up to 20 folders.')
  }
  const ids = new Set()
  const nodes = new Set()
  return input.map(item => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) throw new Error('Invalid MEGA folder configuration.')
    const id = String(item.id || '').trim().toLowerCase()
    const nodeId = String(item.nodeId || '').trim()
    const label = String(item.label || id).trim().slice(0, 60)
    if (!ID_PATTERN.test(id) || id === 'primary' || id === 'auto') {
      throw new Error('MEGA folder IDs must be unique short identifiers other than primary/auto.')
    }
    if (!NODE_PATTERN.test(nodeId)) throw new Error('MEGA folder requires an exact valid MEGA node ID.')
    if (!label || ids.has(id) || nodes.has(nodeId)) throw new Error('Duplicate or invalid MEGA folder.')
    ids.add(id)
    nodes.add(nodeId)
    return { id, nodeId, label, uploadEnabled:item.uploadEnabled === true }
  })
}

export function validateMegaSize(size) {
  const bytes = Number(size)
  if (!Number.isSafeInteger(bytes) || bytes <= 0) {
    throw new Error('The media must have a known, nonzero size to upload to MEGA.')
  }
  if (bytes > MEGA_UPLOAD_LIMIT_BYTES) throw new Error('That file exceeds the 512 MiB per-upload safety limit.')
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

function resolveMount(client, nodeId) {
  // Restrict share selection to the exact configured node, never fuzzy names.
  const mounts = Array.isArray(client.mounts) ? client.mounts : []
  return mounts.find(node => node && node.nodeId === nodeId && node.directory && node !== client.root && node !== client.trash && node !== client.inbox) || null
}

async function uploadFolder(client, folderName) {
  const existing = client.root.find(folderName)
  if (existing) {
    if (!existing.directory) throw new Error('The configured MEGA folder name is already used by a file.')
    return existing
  }
  return client.root.mkdir(folderName)
}

async function selectTarget(client, config, env, targetId, { forUpload = false, createPrimary = false } = {}) {
  const selected = String(targetId || 'primary').trim().toLowerCase()
  if (selected === 'primary' || selected === 'auto') {
    const folder = createPrimary
      ? await uploadFolder(client, config.folder)
      : client.root.find(config.folder)
    return { folder:folder?.directory ? folder : null, id:'primary', label:config.folder, type:'primary' }
  }
  const entry = megaSharedFolders(env).find(item => item.id === selected)
  if (!entry) throw new Error('Unknown MEGA storage target.')
  if (forUpload && !entry.uploadEnabled) throw new Error('Uploads to this shared folder are not enabled by the administrator.')
  const folder = resolveMount(client, entry.nodeId)
  if (!folder) throw new Error('Contributor folder is not mounted on the primary MEGA account.')
  return { folder, id:entry.id, label:entry.label, type:'contributed' }
}

function filesIn(folder) {
  return (folder?.children || []).filter(item => !item.directory)
}

/** Dashboard reports quota only for the authenticated account. Shares are not added to quota. */
export async function megaStorageDashboard(options = {}) {
  const env = options.env || process.env
  const config = megaConfig(env)
  const shares = megaSharedFolders(env)
  if (!config.configured) {
    return {
      provider:'MEGA', configured:false, used:null, total:null,
      targets:[{ id:'primary', label:config.folder, type:'primary', detected:false, uploadEnabled:false },
        ...shares.map(({ id, label, uploadEnabled }) => ({ id, label, type:'contributed', detected:false, uploadEnabled }))],
      note:'Contributed folders require a MEGA invitation with permission; no quota is assumed.',
    }
  }
  return withMegaSession(async client => {
    const account = await client.getAccountInfo()
    const folder = client.root.find(config.folder)
    return {
      provider:'MEGA', configured:true,
      used:Number(account.spaceUsed || 0), total:Number(account.spaceTotal || 0),
      targets:[
        { id:'primary', label:config.folder, type:'primary', detected:true, uploadEnabled:true, files:filesIn(folder).length },
        ...shares.map(({ id, label, nodeId, uploadEnabled }) => {
          const match = resolveMount(client, nodeId)
          return { id, label, type:'contributed', detected:Boolean(match),
            uploadEnabled:Boolean(match && uploadEnabled),
            files:match ? filesIn(match).length : null,
            quotaBytes:null /* MEGAJS does not expose donor-account quota. */ }
        }),
      ],
      note:'Only the primary account quota is verified. Contributor capacities are not summed.',
    }
  }, options)
}

export async function uploadMegaMedia({ name, size, source, targetId = 'primary' }, options = {}) {
  const fileSize = validateMegaSize(size)
  const fileName = safeMegaName(name)
  if (typeof source !== 'function') throw new TypeError('source must be a stream factory.')
  const env = options.env || process.env
  return withMegaSession(async (client, config) => {
    const selected = await selectTarget(client, config, env, targetId, { forUpload:true, createPrimary:true })
    if (!selected.folder) throw new Error('MEGA folder could not be found.')
    if (selected.type === 'primary') {
      const quota = await client.getAccountInfo()
      const remaining = Number(quota.spaceTotal) - Number(quota.spaceUsed)
      if (Number.isFinite(remaining) && remaining >= 0 && fileSize > remaining) {
        throw new Error('Not enough available space in the primary MEGA account.')
      }
    }
    const data = await source()
    if (!(data instanceof Readable) && !(data && typeof data.pipe === 'function')) {
      throw new TypeError('The WhatsApp media download did not return a readable stream.')
    }
    const transfer = selected.folder.upload({ name:fileName, size:fileSize }, data)
    const file = await transfer.complete
    return { name:file.name || fileName, size:fileSize, nodeId:file.nodeId || '', targetId:selected.id, label:selected.label }
  }, options)
}

export async function megaStorageStatus(options = {}) {
  const status = await megaStorageDashboard(options)
  if (!status.configured) throw new Error('MEGA storage is not configured.')
  return { folder:status.targets[0].label, used:status.used, total:status.total, targets:status.targets }
}

export async function megaRecentFiles({ limit = 10, targetId = 'primary' } = {}, options = {}) {
  const capped = Math.min(20, Math.max(1, Math.floor(Number(limit) || 10)))
  const env = options.env || process.env
  return withMegaSession(async (client, config) => {
    const selected = await selectTarget(client, config, env, targetId)
    return filesIn(selected.folder)
      .sort((a, b) => Number(b.timestamp || 0) - Number(a.timestamp || 0))
      .slice(0, capped)
      .map(item => ({ name:String(item.name || 'Untitled'), size:Number(item.size || 0) }))
  }, options)
}
