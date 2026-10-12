// Live, read-only health matrix for the known KnightBot YouTube video providers.
// Only the public video ID is sent. No media files are downloaded.
import { probeKnightProviders } from '../youtube-knight-providers.js'

const videoId = 'XEuaRjnuHlc'
try {
  const results = await probeKnightProviders(videoId)
  console.log(JSON.stringify({component:'mscc-knight-sources',videoId,results}))
} catch {
  console.log(JSON.stringify({component:'mscc-knight-sources',videoId,
    results:[],status:'probe_failed'}))
}
