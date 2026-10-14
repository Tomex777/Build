import { Readable } from 'node:stream'
import { randomBytes, randomUUID, timingSafeEqual } from 'node:crypto'

export const MEGA_UPLOAD_LIMIT_BYTES = 2 * 1024 * 1024 * 1024
const ID_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/
const NODE_PATTERN = /^[A-Za-z0-9_-]{6,48}$/
export const MEGA_MEDIA_TYPES = Object.freeze(['images', 'videos', 'audio', 'documents', 'archives', 'stickers'])
const MEDIA_TYPE_SET = new Set([...MEGA_MEDIA_TYPES, 'all'])

export function megaMediaType({ kind = '', mimeType = '', name = '' } = {}) {
  const type = String(kind || '').trim().toLowerCase()
  const mime = String(mimeType || '').split(';')[0].trim().toLowerCase()
  const ext = String(name || '').toLowerCase().split('.').pop()
  if (type === 'sticker') return 'stickers'
  if (type === 'archive' || ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'cbz', 'cbr'].includes(ext) ||
      /^(application\/(zip|x-rar-compressed|x-7z-compressed|x-tar|gzip|x-gzip))$/.test(mime)) return 'archives'
  if (type === 'video' || mime.startsWith('video/') || ['mp4', 'mkv', 'avi', 'webm', 'mov'].includes(ext)) return 'videos'
  if (type === 'audio' || mime.startsWith('audio/') || ['mp3','flac','ogg','wav','m4a','opus'].includes(ext)) return 'audio'
  if (type === 'image' || mime.startsWith('image/') || ['jpg','jpeg','png','webp','gif','heic'].includes(ext)) return 'images'
  return 'documents'
}

export function mediaTypeLabel(types = []) {
  return types.includes('all') ? 'All media' : types.length ? types.map(s => s[0].toUpperCase() + s.slice(1)).join(', ') : 'Unassigned'
}


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
    const rawTypes = item.mediaTypes ?? []
    if (!Array.isArray(rawTypes) || rawTypes.length > MEGA_MEDIA_TYPES.length + 1) {
      throw new Error('Each shared folder requires a list of mediaTypes.')
    }
    const mediaTypes = rawTypes.map(value => String(value || '').trim().toLowerCase())
    if (mediaTypes.some(value => !MEDIA_TYPE_SET.has(value)) || new Set(mediaTypes).size !== mediaTypes.length) {
      throw new Error('Invalid or duplicate MEGA mediaTypes.')
    }
    if (mediaTypes.includes('all') && mediaTypes.length > 1) {
      throw new Error('Use all alone, not together with other mediaTypes.')
    }
    return { id, nodeId, label, uploadEnabled:item.uploadEnabled === true, mediaTypes }
  })
}

export function validateMegaSize(size) {
  const bytes = Number(size)
  if (!Number.isSafeInteger(bytes) || bytes <= 0) {
    throw new Error('The media must have a known, nonzero size to upload to MEGA.')
  }
  if (bytes > MEGA_UPLOAD_LIMIT_BYTES) throw new Error('That file exceeds the 2 GiB streamed-upload safety limit.')
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

/**
 * Pure routing decision. An assigned media category must land in its configured
 * and mounted writable folder, never be silently redirected to someone's other
 * category or the primary account. A category without a share falls back to the
 * primary account. Do not infer donor quota from folder contents.
 */
export function routeMegaTarget({ mediaType, shares, mounts = [] }) {
  const category = MEGA_MEDIA_TYPES.includes(mediaType) ? mediaType : 'documents'
  const assigned = shares.filter(s => s.mediaTypes.includes(category) || s.mediaTypes.includes('all'))
  if (!assigned.length) return { id:'primary', type:'primary', mediaType:category }
  const mountMap = new Map(mounts.filter(m => m?.directory).map(m => [m.nodeId, m]))
  const eligible = assigned.filter(s => s.uploadEnabled && mountMap.has(s.nodeId))
  if (!eligible.length) {
    throw new Error(`No connected writable MEGA folder is available for ${category}. No upload was attempted.`)
  }
  // Balance by visible bytes rather than file count: one movie can be much
  // larger than hundreds of photos. This is NOT donor-account free quota.
  // Restrict accounting to children of the configured upload folder.
  eligible.sort((a,b) => {
    const storedBytes = entry => folderVisibleBytes(mountMap.get(entry.nodeId))
    return storedBytes(a) - storedBytes(b) || a.id.localeCompare(b.id)
  })
  return { id:eligible[0].id, type:'contributed', mediaType:category }
}

function filesIn(folder) {
  return (folder?.children || []).filter(item => !item.directory)
}

/** Byte total for visible immediate children, not account quota or free space. */
function folderVisibleBytes(folder) {
  return filesIn(folder).reduce((sum, item) => {
    const bytes = Number(item.size)
    return sum + (Number.isSafeInteger(bytes) && bytes > 0 ? bytes : 0)
  }, 0)
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
        ...shares.map(({ id, label, uploadEnabled, mediaTypes }) => ({ id, label, type:'contributed', detected:false, uploadEnabled, mediaTypes }))],
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
        { id:'primary', label:config.folder, type:'primary', detected:true, uploadEnabled:true, files:filesIn(folder).length, visibleBytes:folderVisibleBytes(folder) },
        ...shares.map(({ id, label, nodeId, uploadEnabled, mediaTypes }) => {
          const match = resolveMount(client, nodeId)
          return { id, label, type:'contributed', detected:Boolean(match), mediaTypes,
            uploadEnabled:Boolean(match && uploadEnabled),
            files:match ? filesIn(match).length : null,
            visibleBytes:match ? folderVisibleBytes(match) : null,
            quotaBytes:null /* MEGAJS does not expose donor-account quota. */ }
        }),
      ],
      note:'Only the primary account quota is verified. Contributor capacities are not summed.',
    }
  }, options)
}

/** Read-only discovery for configuring exact allowlisted contributor mount IDs. */
/**
 * One explicitly requested small diagnostic upload to an allowlisted,
 * approved destination. Tests ability to read the bytes back from MSCC's
 * account, NOT whether the donor's MEGA client can decrypt the shared file.
 * The caller must arrange donor-side verification separately.
 */
export async function megaVerifyFolder({ targetId }, options = {}) {
  const env = options.env || process.env
  const id = String(targetId || '').trim().toLowerCase()
  if (!id || id === 'auto') throw new Error('Select a configured MEGA archive to test.')
  if (id !== 'primary' && !megaSharedFolders(env).some(x => x.id === id && x.uploadEnabled)) {
    throw new Error('This MEGA location is not approved for test uploads.')
  }
  return withMegaSession(async (client, config) => {
    const selected = await selectTarget(client, config, env, id, { forUpload:true, createPrimary:id === 'primary' })
    if (!selected.folder) throw new Error('The selected MEGA folder was not found.')
    const bytes = randomBytes(64)
    const name = 'mscc-probe-' + randomUUID() + '.bin'
    let file
    let cleanupOk = false
    try {
      file = await selected.folder.upload({ name, size:bytes.length }, Readable.from([bytes])).complete
      if (!file || typeof file.downloadBuffer !== 'function') {
        throw new Error('MEGA upload did not return a downloadable file.')
      }
      const downloaded = await file.downloadBuffer({ maxConnections:1 })
      if (!Buffer.isBuffer(downloaded) || downloaded.length !== bytes.length ||
        !timingSafeEqual(downloaded, bytes)) {
        throw new Error('Downloaded test data failed the integrity check.')
      }
      return { location:selected.label, success:true, donorVerified:false,
        warning:'MSCC readback passed. The probe is removed after this check. A contributor must still test a separate small upload in their own MEGA app.' }
    } finally {
      if (file && typeof file.delete === 'function') {
        try { await file.delete(true); cleanupOk = true } catch { /* leave test file for manual cleanup */ }
      }
      // Do not claim cleanup success in the response unless reported by API.
      // A failed removal leaves only a 64-byte randomly named probe file.
      if (!cleanupOk && file) {
        process.emitWarning('MEGA readback test file could not be removed; manually check the configured folder.', 'MEGAProbeCleanup')
      }
    }
  }, options)
}

export async function megaAvailableMounts(options = {}) {
  return withMegaSession(async client => (Array.isArray(client.mounts) ? client.mounts : [])
    .filter(node => node && node.directory && node.nodeId &&
      node !== client.root && node !== client.trash && node !== client.inbox)
    .map(node => ({ nodeId:String(node.nodeId), label:String(node.name || 'Shared folder').slice(0,60) }))
    .slice(0,40), options)
}

export async function uploadMegaMedia({ name, size, source, kind = '', mimeType = '' }, options = {}) {
  const fileSize = validateMegaSize(size)
  const fileName = safeMegaName(name)
  if (typeof source !== 'function') throw new TypeError('source must be a stream factory.')
  const env = options.env || process.env
  return withMegaSession(async (client, config) => {
    const mediaType = megaMediaType({ kind, mimeType, name:fileName })
    const target = routeMegaTarget({ mediaType, shares:megaSharedFolders(env), mounts:client.mounts || [] })
    const selected = await selectTarget(client, config, env, target.id, { forUpload:true, createPrimary:true })
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
    return { name:file.name || fileName, size:fileSize, nodeId:file.nodeId || '', targetId:selected.id, label:selected.label, mediaType }
  }, options)
}

export async function megaStorageStatus(options = {}) {
  const status = await megaStorageDashboard(options)
  if (!status.configured) throw new Error('MEGA storage is not configured.')
  return { folder:status.targets[0].label, used:status.used, total:status.total, targets:status.targets }
}

export async function megaRecentFiles({ limit = 10 } = {}, options = {}) {
  const capped = Math.min(20, Math.max(1, Math.floor(Number(limit) || 10)))
  const env = options.env || process.env
  return withMegaSession(async (client, config) => {
    const locations = [await selectTarget(client, config, env, 'primary'), ...megaSharedFolders(env)
      .map(item => ({ folder:resolveMount(client, item.nodeId), label:item.label, id:item.id }))
      .filter(item => item.folder)]
    return locations.flatMap(location => filesIn(location.folder).map(item => ({
      name:String(item.name || 'Untitled'), size:Number(item.size || 0),
      location:location.label, timestamp:Number(item.timestamp || 0),
    })))
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, capped)
      .map(({ timestamp, ...item }) => item)
  }, options)
}
