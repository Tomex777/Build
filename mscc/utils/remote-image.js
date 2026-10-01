import { lookup as dnsLookup } from 'node:dns'
import http from 'node:http'
import https from 'node:https'
import net from 'node:net'

const MAX_IMAGE_BYTES = 12 * 1024 * 1024
const MAX_REDIRECTS = 5
const REQUEST_TIMEOUT_MS = 20_000

function ipv4Private(address) {
  const parts = address.split('.').map(Number)
  if (parts.length !== 4 || parts.some(n => !Number.isInteger(n) || n < 0 || n > 255)) return true
  const [a,b,c] = parts
  if (a === 0 || a === 10 || a === 127) return true
  if (a === 169 && b === 254) return true
  if (a === 172 && b >= 16 && b <= 31) return true
  if (a === 192 && b === 168) return true
  if (a === 100 && b >= 64 && b <= 127) return true
  if (a === 198 && (b === 18 || b === 19)) return true
  if (a >= 224) return true
  if (a === 192 && b === 0 && c === 0) return true
  if (a === 192 && b === 0 && c === 2) return true
  if (a === 198 && b === 51 && c === 100) return true
  if (a === 203 && b === 0 && c === 113) return true
  return false
}

function ipv6Private(address) {
  const value = String(address || '').toLowerCase()
  if (value === '::' || value === '::1') return true
  if (value.startsWith('fc') || value.startsWith('fd')) return true
  if (/^fe[89ab]/.test(value)) return true
  if (value.startsWith('ff')) return true
  const mapped = value.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/)
  if (mapped) return ipv4Private(mapped[1])
  return false
}

export function isPublicIp(address) {
  const family = net.isIP(String(address || ''))
  if (family === 4) return !ipv4Private(String(address))
  if (family === 6) return !ipv6Private(String(address))
  return false
}

async function validateUrl(urlValue) {
  const url = new URL(String(urlValue || '').trim())
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Only HTTP(S) image URLs are supported.')
  if (url.username || url.password) throw new Error('Authenticated image URLs are not supported.')

  if (net.isIP(url.hostname)) {
    if (!isPublicIp(url.hostname)) throw new Error('Private-network image URLs are not allowed.')
    return url
  }

  const records = await new Promise((resolve, reject) => {
    dnsLookup(url.hostname, { all:true, verbatim:true }, (error, addresses) => {
      if (error) reject(error)
      else resolve(addresses || [])
    })
  })

  if (!records.length || records.some(record => !isPublicIp(record.address))) {
    throw new Error('Private-network image URLs are not allowed.')
  }

  return url
}

function safeLookup(hostname, options, callback) {
  dnsLookup(hostname, { all:true, verbatim:true }, (error, records) => {
    if (error) return callback(error)
    const list = Array.isArray(records) ? records : [records].filter(Boolean)
    if (!list.length || list.some(record => !isPublicIp(record.address))) {
      return callback(new Error('Private-network image URLs are not allowed.'))
    }
    const first = list[0]
    callback(null, first.address, first.family)
  })
}

async function requestImage(urlValue, redirectsLeft) {
  const url = await validateUrl(urlValue)
  const transport = url.protocol === 'https:' ? https : http

  return new Promise((resolve, reject) => {
    const headers = {
      Accept:'image/avif,image/webp,image/apng,image/gif,image/*,*/*;q=0.8',
      'User-Agent':'Mozilla/5.0 (compatible; MSCC/1.0)',
    }
    if (url.hostname.endsWith('pinimg.com')) headers.Referer = 'https://www.pinterest.com/'

    const request = transport.request(url, {
      method:'GET',
      headers,
      lookup:safeLookup,
      timeout:REQUEST_TIMEOUT_MS,
    }, response => {
      const status = Number(response.statusCode || 0)
      const location = response.headers.location

      if (status >= 300 && status < 400 && location) {
        response.resume()
        if (redirectsLeft <= 0) return reject(new Error('Too many image redirects.'))
        return resolve(requestImage(new URL(location, url).href, redirectsLeft - 1))
      }

      if (status < 200 || status >= 300) {
        response.resume()
        return reject(new Error(`Image request returned HTTP ${status}.`))
      }

      const contentType = String(response.headers['content-type'] || '').split(';')[0].trim().toLowerCase()
      if (!contentType.startsWith('image/')) {
        response.resume()
        return reject(new Error('URL did not return an image.'))
      }

      const declared = Number(response.headers['content-length'] || 0)
      if (declared > MAX_IMAGE_BYTES) {
        response.resume()
        return reject(new Error('Image is too large.'))
      }

      const chunks = []
      let total = 0
      response.on('data', chunk => {
        total += chunk.length
        if (total > MAX_IMAGE_BYTES) {
          request.destroy(new Error('Image is too large.'))
          return
        }
        chunks.push(chunk)
      })
      response.once('end', () => resolve({
        buffer:Buffer.concat(chunks),
        contentType,
        url:url.href,
      }))
      response.once('error', reject)
    })

    request.once('timeout', () => request.destroy(new Error('Image download timed out.')))
    request.once('error', reject)
    request.end()
  })
}

export async function downloadImageUrl(url) {
  return requestImage(url, MAX_REDIRECTS)
}
